import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProgrammeProfesseur implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerProgrammeProfesseur.class.getName());

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, professeurId, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, professeurId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerProgrammeProfesseur", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerProgrammeProfesseur", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String codeCours = params.getOrDefault("codeCours", "");
        String classe = params.getOrDefault("classe", "");
        String periode = params.getOrDefault("periode", "");
        String annee = params.getOrDefault("annee", "");
        String action = params.getOrDefault("action", "");
        String idStr = params.getOrDefault("id", "");
        String vue = params.getOrDefault("vue", "liste");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProgrammeManager manager = new ProgrammeManager(conn);
            List<Programme> programmes;

            // --- Suppression ---
            if ("delete".equals(action) && !idStr.isBlank()) {
                // ✅ id est un UUID String — plus de parseInt
                boolean deleted = manager.supprimerProgramme(idStr, institutionId, professeurId);
                String msg = deleted
                        ? "Chapitre supprimé avec succès."
                        : "Erreur lors de la suppression.";
                String msgType = deleted ? "success" : "error";
                handleGetWithMessage(exchange, professeurId, institutionId, msgType, msg);
                return;
            }

            // --- Liste ---
            if (!codeCours.isBlank() || !classe.isBlank()
                    || !periode.isBlank() || !annee.isBlank()) {
                programmes = manager.getProgrammesByFiltres(
                        professeurId, institutionId, codeCours, classe, periode, annee);
            } else {
                programmes = manager.getProgrammesByProfesseur(professeurId, institutionId);
            }

            // --- Créneaux du professeur ---
            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux =
                    profData.getCreneauxParProfesseur(professeurId, institutionId);

            List<String> codesCoursProf = creneaux.stream()
                    .map(Professeur.Creneau::getCodeCours)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> classesProf = creneaux.stream()
                    .map(Professeur.Creneau::getClasse)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> periodesProf = creneaux.stream()
                    .map(Professeur.Creneau::getPeriode)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> anneesProf = creneaux.stream()
                    .map(Professeur.Creneau::getAnneeAcademique)
                    .filter(a -> a != null && !a.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            boolean peutAjouter = !creneaux.isEmpty();

            String html = UIProfesseurProgramme.render(
                    professeurId,
                    codesCoursProf,
                    classesProf,
                    periodesProf,
                    anneesProf,
                    peutAjouter,
                    programmes,
                    codeCours,
                    classe,
                    periode,
                    annee,
                    vue,
                    null,
                    null
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans handleGet", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        }
    }

    private void handleGetWithMessage(HttpExchange exchange, String professeurId,
                                       String institutionId, String msgType, String msg)
            throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProgrammeManager manager = new ProgrammeManager(conn);
            List<Programme> programmes =
                    manager.getProgrammesByProfesseur(professeurId, institutionId);

            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux =
                    profData.getCreneauxParProfesseur(professeurId, institutionId);

            List<String> codesCoursProf = creneaux.stream()
                    .map(Professeur.Creneau::getCodeCours)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> classesProf = creneaux.stream()
                    .map(Professeur.Creneau::getClasse)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> periodesProf = creneaux.stream()
                    .map(Professeur.Creneau::getPeriode)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> anneesProf = creneaux.stream()
                    .map(Professeur.Creneau::getAnneeAcademique)
                    .filter(a -> a != null && !a.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            boolean peutAjouter = !creneaux.isEmpty();

            String html = UIProfesseurProgramme.render(
                    professeurId,
                    codesCoursProf,
                    classesProf,
                    periodesProf,
                    anneesProf,
                    peutAjouter,
                    programmes,
                    "", "", "", "",
                    "liste",
                    msgType,
                    msg
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL dans handleGetWithMessage", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if (!"ajouter".equals(action) && !"modifier".equals(action)) {
            safeSendError(exchange, 400, "Action inconnue.");
            return;
        }

        String codeCours = params.get("codeCours");
        String classe = params.get("classe");
        String periode = params.get("periode");
        String annee = params.get("annee_academique");
        String titre = params.get("titre");

        if (codeCours == null || codeCours.isBlank()
                || classe == null || classe.isBlank()
                || periode == null || periode.isBlank()
                || annee == null || annee.isBlank()
                || titre == null || titre.isBlank()) {
            safeSendError(exchange, 400,
                    "Tous les champs obligatoires doivent être remplis.");
            return;
        }

        // --- Vérification des droits ---
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux =
                    profData.getCreneauxParProfesseur(professeurId, institutionId);

            boolean autorise = creneaux.stream().anyMatch(c ->
                    codeCours.equals(c.getCodeCours())
                    && classe.equals(c.getClasse())
                    && periode.equals(c.getPeriode())
                    && annee.equals(c.getAnneeAcademique())
            );

            if (!autorise) {
                safeSendError(exchange, 403,
                        "Vous n'êtes pas autorisé à enseigner ce cours "
                        + "pour cette classe/période/année.");
                return;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits", e);
            safeSendError(exchange, 500,
                    "Erreur lors de la vérification des droits.");
            return;
        }

        // --- Construction du programme ---
        Programme prog = new Programme();
        prog.setInstitutionId(institutionId);
        prog.setProfesseurId(professeurId);
        prog.setCodeCours(codeCours.trim());
        prog.setClasse(classe.trim());
        prog.setPeriode(periode.trim());
        prog.setAnneeAcademique(annee.trim());
        prog.setTitreChapitre(titre.trim());
        prog.setDescription(params.getOrDefault("description", "").trim());
        prog.setEstTermine("on".equals(params.get("est_termine")));

        try {
            String dateDebutStr = params.get("date_debut");
            if (dateDebutStr != null && !dateDebutStr.isBlank()) {
                prog.setDateDebut(dateFormat.parse(dateDebutStr));
            }
            String dateFinStr = params.get("date_fin");
            if (dateFinStr != null && !dateFinStr.isBlank()) {
                prog.setDateFin(dateFormat.parse(dateFinStr));
            }
            String cotationStr = params.get("cotation");
            prog.setCotation(cotationStr != null && !cotationStr.isBlank()
                    ? Integer.parseInt(cotationStr) : 0);
        } catch (ParseException e) {
            safeSendError(exchange, 400,
                    "Format de date invalide (utilisez YYYY-MM-DD).");
            return;
        } catch (NumberFormatException e) {
            safeSendError(exchange, 400, "Cotation invalide.");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProgrammeManager manager = new ProgrammeManager(conn);
            boolean ok;

            if ("ajouter".equals(action)) {
                ok = manager.ajouterProgramme(prog);
            } else {
                // ✅ id est un UUID String — plus de parseInt
                String idStr = params.get("id");
                if (idStr == null || idStr.isBlank()) {
                    safeSendError(exchange, 400,
                            "ID manquant pour la modification.");
                    return;
                }
                prog.setId(idStr);
                ok = manager.modifierProgramme(prog);
            }

            String msg = ok
                    ? "Chapitre " + ("ajouter".equals(action) ? "ajouté" : "modifié")
                      + " avec succès."
                    : "Erreur lors de l'opération.";
            String msgType = ok ? "success" : "error";
            handleGetWithMessage(exchange, professeurId, institutionId, msgType, msg);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur sauvegarde programme", e);
            handleGetWithMessage(exchange, professeurId, institutionId,
                    "error", "Erreur: " + e.getMessage());
        }
    }

    // ==================== SESSION ====================
    private String getProfesseurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID professeur", e);
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'institutionId", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // ==================== PARSING ====================
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    // ==================== SEND HELPERS ====================
    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            String html = UIProfesseurProgramme.renderError(message);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer la page d'erreur", e);
            }
        }
    }

    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null) msg = cause.getMessage();
        }
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("aborted")
            || lower.contains("broken pipe")
            || lower.contains("connection reset")
            || lower.contains("insufficient bytes")
            || lower.contains("connection closed")
            || lower.contains("software in your host machine");
    }
}