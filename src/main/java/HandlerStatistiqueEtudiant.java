import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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

public class HandlerStatistiqueEtudiant implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerStatistiqueEtudiant.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            final String etudiantId = getEtudiantIdFromSession(exchange);
            final String institutionId = getInstitutionIdFromSession(exchange);

            if (etudiantId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // ✅ Vérification des droits via AccesEtudiantData
            boolean aDroitStats = verifierDroitStatistiques(etudiantId, institutionId);
            if (!aDroitStats) {
                LOGGER.warning(() -> "🚫 Accès statistiques refusé pour " + etudiantId);
                exchange.getResponseHeaders().set("Location",
                        "/etudiant/dashboard?error=acces_refuse_statistiques");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            // ---------- Paramètres de filtre ----------
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            final String annee = params.getOrDefault("annee", "");
            final String periode = params.getOrDefault("periode", "");

            // ✅ Classe : récupérée depuis la base (pas depuis le client)
            String classeEtudiant = getClasseEtudiant(etudiantId, institutionId);
            if (classeEtudiant == null) {
                safeSendError(exchange, 404, "Étudiant introuvable.");
                return;
            }
            final String classe = classeEtudiant;

            LOGGER.info(() -> "📊 Stats étudiant=" + etudiantId
                    + " | classe=" + classe
                    + " | annee=" + annee
                    + " | periode=" + periode);

            // ---------- Récupération des notes filtrées ----------
            final List<Note> notes = getNotesFiltrees(
                    etudiantId, institutionId, annee, periode, classe);
            LOGGER.info(() -> "📝 Notes récupérées : " + notes.size());

            // ---------- Récupération des matières (avec coefficient + bornes) ----------
            final Map<String, Matiere> matieresMap = getMatieresMap(institutionId);
            LOGGER.info(() -> "📚 Matières chargées : " + matieresMap.size());

            // ---------- Calcul des moyennes + coefficients par matière ----------
            Map<String, Double> moyennes = new LinkedHashMap<>();
            Map<String, Double> coefficients = new LinkedHashMap<>();

            calculerMoyennesEtCoefficients(notes, matieresMap, moyennes, coefficients);

            LOGGER.info(() -> "📈 Moyennes calculées : " + moyennes.size()
                    + " | coefficients : " + coefficients.size());

            // ---------- Listes des filtres (depuis les référentiels) ----------
            final List<String> anneesDisponibles = getAnneesDisponibles(institutionId);
            final List<String> periodesDisponibles = getPeriodesDisponibles(institutionId, annee);

            LOGGER.info(() -> "📅 Années=" + anneesDisponibles
                    + " | Périodes=" + periodesDisponibles);

            // ---------- Rendu HTML ----------
            final String html = UIStatistiqueEtudiant.render(
                    moyennes,
                    coefficients,
                    anneesDisponibles,
                    periodesDisponibles,
                    classe,
                    annee,
                    periode
            );

            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerStatistiqueEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerStatistiqueEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ============================================================
    // VÉRIFICATION DES DROITS
    // ============================================================
    private boolean verifierDroitStatistiques(String etudiantId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(etudiantId, institutionId);
            LOGGER.info(() -> "🔍 Vérif acces statistiques pour " + etudiantId
                    + " | acces=" + (acces == null ? "NULL" : "trouvé")
                    + (acces != null ? " | statistiques=" + acces.isStatistiques() : ""));
            return acces != null && acces.isStatistiques();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits statistiques", e);
            return false;
        }
    }

    // ============================================================
    // CLASSE DE L'ÉTUDIANT
    // ============================================================
    private String getClasseEtudiant(String etudiantId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData dao = new EtudiantData(conn);
            Etudiant e = dao.read(etudiantId,institutionId);
            if (e == null || !institutionId.equals(e.getInstitutionId())) return null;
            return e.getClasse();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération classe étudiant", e);
            return null;
        }
    }

    // ============================================================
    // NOTES FILTRÉES
    // ============================================================
    private List<Note> getNotesFiltrees(String etudiantId, String institutionId,
                                         String annee, String periode, String classe) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            NoteData noteData = new NoteData(conn);
            return noteData.getNotesWithFiltersForStudent(
                    etudiantId, institutionId, annee, periode, classe);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération notes", e);
            return new ArrayList<>();
        }
    }

    // ============================================================
    // MATIÈRES
    // ============================================================
    private Map<String, Matiere> getMatieresMap(String institutionId) {
        Map<String, Matiere> map = new LinkedHashMap<>();
        MatiereData matiereData = new MatiereData(institutionId);
        List<Matiere> matieres = matiereData.readAll();
        for (Matiere m : matieres) {
            if (m.getCodeCours() != null) {
                map.put(m.getCodeCours(), m);
            }
        }
        return map;
    }

    // ============================================================
    // CALCUL DES MOYENNES + COEFFICIENTS
    // ============================================================
    /**
     * ✅ Calcule les moyennes normalisées sur 20 par matière
     *    ET remplit la map des coefficients associés.
     */
   private void calculerMoyennesEtCoefficients(List<Note> notes,
                                              Map<String, Matiere> matieresMap,
                                              Map<String, Double> moyennes,
                                              Map<String, Double> coefficients) {

    Map<String, List<Double>> notesParMatiere = new LinkedHashMap<>();
    Map<String, Double> coeffParMatiere = new LinkedHashMap<>();

    for (Note note : notes) {
        String codeCours = note.getCodeCours();
        if (codeCours == null || codeCours.isBlank()) continue;

        Matiere matiere = matieresMap.get(codeCours);
        if (matiere == null) continue;

        String noteValueStr = note.getNoteValue();
        if (noteValueStr == null || noteValueStr.isBlank()) continue;

        double noteValue;
        try {
            noteValue = Double.parseDouble(noteValueStr);
        } catch (NumberFormatException e) {
            continue;
        }

        // ============================================================
        // ✅ NORMALISATION CORRECTE
        //    On utilise l'échelle DE LA NOTE (note_sur ou note_base),
        //    PAS les bornes pédagogiques de la matière.
        // ============================================================

        // 1) Chercher "note_sur" en priorité
        double noteSur = 0.0;
        String noteSurStr = note.getNoteSur();
        if (noteSurStr != null && !noteSurStr.isBlank()) {
            try { noteSur = Double.parseDouble(noteSurStr); }
            catch (NumberFormatException ignored) {}
        }

        // 2) Fallback sur "note_base" si note_sur est absent
        if (noteSur <= 0) {
            String noteBaseStr = note.getNoteBase();
            if (noteBaseStr != null && !noteBaseStr.isBlank()) {
                try { noteSur = Double.parseDouble(noteBaseStr); }
                catch (NumberFormatException ignored) {}
            }
        }

        // 3) Dernier recours : 20 par défaut
        if (noteSur <= 0) {
            noteSur = 100.0;
        }

        // ✅ Normalisation : ramener la note sur 20
        double noteNormalisee = (noteValue / noteSur) * 100.0;
        noteNormalisee = Math.max(0, Math.min(100, noteNormalisee));

        String nomMatiere = matiere.getNomMatiere();
        if (nomMatiere == null || nomMatiere.isBlank()) continue;

        notesParMatiere.computeIfAbsent(nomMatiere, k -> new ArrayList<>())
                .add(noteNormalisee);

        // ✅ Coefficient
        double coeff = matiere.getCoefficient();
        if (coeff <= 0) coeff = 1.0;
        coeffParMatiere.put(nomMatiere, coeff);
    }

    // ✅ Moyenne par matière
    for (Map.Entry<String, List<Double>> entry : notesParMatiere.entrySet()) {
        String nomMatiere = entry.getKey();
        List<Double> valeurs = entry.getValue();

        double somme = 0.0;
        for (double v : valeurs) somme += v;
        double moyenne = somme / valeurs.size();
        double moyenneArrondie = Math.round(moyenne * 100.0) / 100.0;

        moyennes.put(nomMatiere, moyenneArrondie);
        coefficients.put(nomMatiere, coeffParMatiere.getOrDefault(nomMatiere, 1.0));
    }
}
    // ============================================================
    // FILTRES : ANNÉES & PÉRIODES (depuis les référentiels)
    // ============================================================
    private List<String> getAnneesDisponibles(String institutionId) {
        List<String> annees = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            annees = anneeData.listByInstitution(institutionId).stream()
                    .map(AnneeAcademique::getAnneeAcademique)
                    .toList();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération années", e);
        }
        return annees;
    }

    private List<String> getPeriodesDisponibles(String institutionId, String annee) {
        List<String> periodes = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PeriodeData periodeData = new PeriodeData(conn);
            periodes = periodeData.listByInstitution(institutionId).stream()
                    .filter(p -> annee == null || annee.isBlank()
                            || annee.equals(p.getAnneeAcademique()))
                    .map(Periode::getPeriode)
                    .toList();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération périodes", e);
        }
        return periodes;
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getEtudiantIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        return session == null ? null : (String) session.getAttribute("userId");
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }

    private HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur récupération session", e);
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

    // ============================================================
    // PARSING
    // ============================================================
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                try {
                    String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {
                    params.put(parts[0], parts[1]);
                }
            }
        }
        return params;
    }

    // ============================================================
    // SEND HELPERS
    // ============================================================
    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            String html = "<html><body><h1>Erreur " + status + "</h1><p>"
                    + escapeHtml(message) + "</p></body></html>";
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer la page d'erreur", e);
            }
        }
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
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