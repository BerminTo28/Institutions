import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerStatistiques implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerStatistiques.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        String institutionId = StatistiquesUtils.getInstitutionId(exchange);
        if (institutionId == null || institutionId.isBlank()) {
            if (path.contains("/api/")) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("error", "Session expirée");
                StatistiquesUtils.sendJson(exchange, resp, 401);
            } else {
                exchange.getResponseHeaders().set("Location", "/login?error=Session+expirée");
                exchange.sendResponseHeaders(302, -1);
                exchange.getResponseBody().close();
            }
            return;
        }

        try {
            if (path.endsWith("/api/filtres")) {
                sendSuccess(exchange, StatistiquesService.getFiltres(institutionId));
                return;
            }

            if (path.endsWith("/api/dashboard")) {
                Map<String, String> p = StatistiquesUtils.parseQuery(query);
                String annee   = p.getOrDefault("annee", "Toutes années");
                String periode = p.getOrDefault("periode", "Toutes periodes");
                String classe  = p.getOrDefault("classe", "Toutes classes");
                String matiere = p.getOrDefault("matiere", "Toutes matières");

                Map<String, Object> data = new HashMap<>();
                data.put("kpis",        StatistiquesService.getKPIs(institutionId, annee, periode, classe, matiere));
                data.put("effectifs",   StatistiquesService.getEffectifsClasses(institutionId, classe));
                data.put("moyennes",    StatistiquesService.getMoyennesClasses(institutionId, annee, periode, classe, matiere));
                data.put("evolution",   StatistiquesService.getEvolution(institutionId, periode, classe, matiere));
                data.put("repartition", StatistiquesService.getRepartitionPromo(institutionId, classe));
                sendSuccess(exchange, data);
                return;
            }

            if (path.endsWith("/api/classes")) {
                Map<String, String> p = StatistiquesUtils.parseQuery(query);
                List<Object[]> rows = StatistiquesData.getStatsClasse(institutionId,
                        p.getOrDefault("annee", "Toutes années"),
                        p.getOrDefault("periode", "Toutes periodes"),
                        p.getOrDefault("classe", "Toutes classes"),
                        p.getOrDefault("matiere", "Toutes matières"));
                Map<String, Object> data = new HashMap<>();
                data.put("stats", rows);
                sendSuccess(exchange, data);
                return;
            }

            if (path.endsWith("/api/analyse")) {
                Map<String, String> p = StatistiquesUtils.parseQuery(query);
                String annee   = p.getOrDefault("annee", "Toutes années");
                String periode = p.getOrDefault("periode", "Toutes periodes");
                String classe  = p.getOrDefault("classe", "Toutes classes");
                String matiere = p.getOrDefault("matiere", "Toutes matières");

                Map<String, Object> kpis = StatistiquesService.getKPIs(institutionId, annee, periode, classe, matiere);
                Map<String, Object> data = new HashMap<>();
                data.put("mediane",   kpis.getOrDefault("mediane", 0.0));
                data.put("ecartType", kpis.getOrDefault("ecartType", 0.0));
                data.put("iqr",       kpis.getOrDefault("iqr", 0.0));

                double moy = ((Number) kpis.getOrDefault("moyenneGenerale", 0.0)).doubleValue();
                double ec  = ((Number) kpis.getOrDefault("ecartType", 0.0)).doubleValue();
                data.put("cv", moy != 0 ? (ec / moy) * 100.0 : 0.0);
                data.put("evolution", StatistiquesService.getEvolution(institutionId, periode, classe, matiere));
                sendSuccess(exchange, data);
                return;
            }

            if (path.endsWith("/api/genre")) {
                Map<String, String> p = StatistiquesUtils.parseQuery(query);
                String classe = p.getOrDefault("classe", "Toutes classes");
                sendSuccess(exchange, getGenreParClasse(institutionId, classe));
                return;
            }

            String html = UIStatistiques.rendrePage(institutionId);
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans HandlerStatistiques", e);
            sendError(exchange, "Erreur base de données : " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerStatistiques", e);
            sendError(exchange, "Erreur interne : " + e.getMessage());
        }
    }

    // ====================================================================
    // GENRE PAR CLASSE — Logique métier corrigée
    // ====================================================================
    private static Map<String, Object> getGenreParClasse(String institutionId, String classeFilter) {
        List<String> classes = new ArrayList<>();
        List<Integer> garcons = new ArrayList<>();
        List<Integer> filles = new ArrayList<>();
        List<Integer> autres = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT e.classe, e.sexe, COUNT(*) AS total " +
                "FROM " + Constantes.TABLE_ETUDIANTS + " e " +
                "WHERE e.institution_id = ? AND e.classe IS NOT NULL AND e.classe != ''");

        boolean filterClasse = classeFilter != null
                && !classeFilter.isBlank()
                && !classeFilter.equals("Toutes classes")
                && !classeFilter.equals("Toutes les classes");

        if (filterClasse) {
            sql.append(" AND e.classe = ?");
        }
        sql.append(" GROUP BY e.classe, e.sexe ORDER BY e.classe");

        Map<String, int[]> parClasse = new LinkedHashMap<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setString(1, institutionId);
            if (filterClasse) ps.setString(2, classeFilter);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String classe = rs.getString("classe");
                    String sexeRaw = rs.getString("sexe");
                    int total = rs.getInt("total");

                    parClasse.putIfAbsent(classe, new int[]{0, 0, 0});
                    int[] c = parClasse.get(classe);

                    String sexe = normaliserSexe(sexeRaw);

                    switch (sexe) {
                        case "M" -> c[0] += total;
                        case "F" -> c[1] += total;
                        default -> {
                            c[2] += total;
                            LOGGER.log(Level.WARNING, "Valeur de sexe non reconnue ou vide: ''{0}'' pour la classe ''{1}''", 
                                    new Object[]{sexeRaw, classe});
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans getGenreParClasse pour " + institutionId, e);
        }

        for (Map.Entry<String, int[]> entry : parClasse.entrySet()) {
            classes.add(entry.getKey());
            garcons.add(entry.getValue()[0]);
            filles.add(entry.getValue()[1]);
            autres.add(entry.getValue()[2]);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("classes", classes);
        data.put("garcons", garcons);
        data.put("filles", filles);
        data.put("autres", autres);
        return data;
    }

    // ====================================================================
    // ✅ NORMALISATION DU SEXE — Cartographie exhaustive
    // ====================================================================
    private static String normaliserSexe(String sexeRaw) {
        if (sexeRaw == null || sexeRaw.isBlank()) return "";

        String s = sexeRaw.trim().toLowerCase(java.util.Locale.ROOT);
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        if (s.equals("m") || s.equals("masculin") || s.equals("male")
                || s.equals("homme") || s.equals("h") || s.equals("1")
                || s.equals("garcon") || s.equals("g")) {
            return "M";
        }
        if (s.equals("f") || s.equals("feminin") || s.equals("female")
                || s.equals("femme") || s.equals("2")
                || s.equals("fille")) {
            return "F";
        }
        return "";
    }

    // ====================================================================
    // RÉPONSES JSON
    // ====================================================================
    private static void sendSuccess(HttpExchange exchange, Map<String, Object> data) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        if (data != null) resp.putAll(data);
        StatistiquesUtils.sendJson(exchange, resp);
    }

    private static void sendError(HttpExchange exchange, String message) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        StatistiquesUtils.sendJson(exchange, resp, 500);
    }
}