import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEvenement implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEvenement.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "📅 EVENEMENT HANDLER - " + method + " " + path);

        String institutionId = null;
        String utilisateurId;

        try {
            institutionId = getInstitutionIdFromSession(exchange);
            utilisateurId = getUtilisateurIdFromSession(exchange);

            if (institutionId == null || institutionId.isBlank()
                    || utilisateurId == null || utilisateurId.isBlank()) {
                LOGGER.warning("⛔ Session invalide - Redirection vers login");
                exchange.getResponseHeaders().set("Location", "/login?error=Session+expirée");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                LOGGER.warning(() -> "⛔ Permissions non trouvées pour " + utilisateurId);
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Permissions+non+trouvées");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if (!acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.READ)) {
                LOGGER.warning("⛔ Accès refusé - Pas de READ sur EVENEMENTS");
                exchange.getResponseHeaders().set("Location", "/dashboard?error=Accès+non+autorisé");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            EvenementManager manager = new EvenementManager(institutionId);

            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, manager, institutionId, acces);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, manager, institutionId, acces);
                return;
            }

            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            if (path.startsWith("/admin/evenements/api/")) {
                sendJsonError(exchange, e.getMessage());
            } else {
                String instId = (institutionId != null && !institutionId.isBlank()) ? institutionId : "ERR";
                String html = UIEvenement.rendrePage(
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        "Erreur: " + e.getMessage(),
                        "",
                        instId,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        "",
                        "",
                        "",
                        false, false, false
                );
                sendResponse(exchange, 500, html);
            }
        }
    }

    // ============================================================
    // GET
    // ============================================================
    private void handleGet(HttpExchange exchange, EvenementManager manager,
                            String institutionId, AccesAdmin acces) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String error = params.getOrDefault("error", "");
        String success = params.getOrDefault("success", "");
        String anneeFiltre = params.getOrDefault("annee", "");
        String periodeFiltre = params.getOrDefault("periode", "");
        String classeFiltre = params.getOrDefault("classe", "");

        List<Evenement> evenements;
        if (!anneeFiltre.isBlank() || !periodeFiltre.isBlank() || !classeFiltre.isBlank()) {
            evenements = manager.listerFiltres(anneeFiltre, periodeFiltre, classeFiltre);
        } else {
            evenements = manager.listerTous();
        }

        List<String> anneesDisponibles = manager.getAnneesDisponibles();
        List<String> periodesDisponibles = manager.getPeriodesDisponibles();
        List<String> classesDisponibles = manager.getClassesDisponibles();

        List<Object[]> etudiants = manager.obtenirEtudiantsActifs();
        List<Object[]> professeurs = manager.obtenirProfesseursActifs();

        boolean canCreate = acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.CREATE);
        boolean canUpdate = acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.UPDATE);
        boolean canDelete = acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.DELETE);

        String html = UIEvenement.rendrePage(
                evenements,
                etudiants,
                professeurs,
                error,
                success,
                institutionId,
                anneesDisponibles,
                periodesDisponibles,
                classesDisponibles,
                anneeFiltre,
                periodeFiltre,
                classeFiltre,
                canCreate,
                canUpdate,
                canDelete
        );

        sendResponse(exchange, 200, html);
    }

    // ============================================================
    // POST
    // ============================================================
    private void handlePost(HttpExchange exchange, EvenementManager manager,
                             String institutionId, AccesAdmin acces) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseQueryParams(body);
        LOGGER.info(() -> "📝 POST reçu: " + params);

        String action = params.get("action");
        if (action == null || action.isBlank()) {
            redirect(exchange, "/admin/evenements?error=Action non spécifiée");
            return;
        }

        // --- SUPPRESSION ---
        if ("delete".equals(action)) {
            if (!acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.DELETE)) {
                redirect(exchange, "/admin/evenements?error=Permission DELETE refusée");
                return;
            }
            // ✅ id String (UUID)
            String idStr = params.get("id");
            if (idStr == null || idStr.isBlank()) {
                redirect(exchange, "/admin/evenements?error=ID manquant");
                return;
            }
            boolean deleted = manager.supprimer(idStr);      // ✅ String
            redirect(exchange, deleted
                    ? "/admin/evenements?success=Supprimé"
                    : "/admin/evenements?error=Échec suppression");
            return;
        }

        // --- AJOUT / MODIFICATION ---
        boolean isAdd = "add".equals(action);
        boolean isUpdate = "update".equals(action);

        if (isAdd && !acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.CREATE)) {
            redirect(exchange, "/admin/evenements?error=Permission CREATE refusée");
            return;
        }
        if (isUpdate && !acces.hasPermission(AccesAdmin.Module.EVENEMENTS, AccesAdmin.Action.UPDATE)) {
            redirect(exchange, "/admin/evenements?error=Permission UPDATE refusée");
            return;
        }

        String idStr = params.get("id");                     // ✅ String UUID
        String titre = params.get("titre");
        String description = params.get("description");
        String dateDebutStr = params.get("dateDebut");
        String dateFinStr = params.get("dateFin");
        String lieu = params.get("lieu");
        String statut = params.get("statut");
        String anneeAcademique = params.get("anneeAcademique");
        String periode = params.get("periode");
        String classe = params.get("classe");

        if (titre == null || titre.isBlank() || dateDebutStr == null || dateFinStr == null) {
            redirect(exchange, "/admin/evenements?error=Titre, date début et date fin sont obligatoires");
            return;
        }

        Timestamp dateDebut = parseTimestamp(dateDebutStr);
        Timestamp dateFin = parseTimestamp(dateFinStr);
        if (dateDebut == null || dateFin == null) {
            redirect(exchange, "/admin/evenements?error=Format de date invalide");
            return;
        }

        // Récupération des étudiants participants
        List<String> etudiants = new ArrayList<>();
        for (String pair : body.split("&")) {
            if (pair.startsWith("participants_etudiants=")) {
                try {
                    String value = URLDecoder.decode(
                            pair.substring("participants_etudiants=".length()),
                            StandardCharsets.UTF_8);
                    if (value != null && !value.isBlank()) {
                        etudiants.add(value.trim());
                    }
                } catch (Exception e) {
                    LOGGER.warning(() -> "Erreur décodage étudiant: " + e.getMessage());
                }
            }
        }

        // Récupération des professeurs participants
        List<String> professeurs = new ArrayList<>();
        for (String pair : body.split("&")) {
            if (pair.startsWith("participants_professeurs=")) {
                try {
                    String value = URLDecoder.decode(
                            pair.substring("participants_professeurs=".length()),
                            StandardCharsets.UTF_8);
                    if (value != null && !value.isBlank()) {
                        professeurs.add(value.trim());
                    }
                } catch (Exception e) {
                    LOGGER.warning(() -> "Erreur décodage professeur: " + e.getMessage());
                }
            }
        }

        LOGGER.info(() -> "👥 Étudiants extraits: " + etudiants);
        LOGGER.info(() -> "👥 Professeurs extraits: " + professeurs);

        Evenement evenement = new Evenement();
        evenement.setTitre(titre);
        evenement.setDescription(description);
        evenement.setDateDebut(dateDebut);
        evenement.setDateFin(dateFin);
        evenement.setLieu(lieu);
        evenement.setStatut(statut != null ? statut : "À venir");
        evenement.setInstitutionId(institutionId);
        evenement.setAnneeAcademique(anneeAcademique != null && !anneeAcademique.isBlank() ? anneeAcademique : null);
        evenement.setPeriode(periode != null && !periode.isBlank() ? periode : null);
        evenement.setClasse(classe != null && !classe.isBlank() ? classe : null);
        evenement.setEtudiantsEmails(etudiants);
        evenement.setProfesseursEmails(professeurs);

        // ✅ id String (UUID) — plus de parseInt
        if (idStr != null && !idStr.isBlank() && !"0".equals(idStr)) {
            evenement.setId(idStr);                          // ✅ UUID
        }

        boolean saved = manager.sauvegarder(evenement);
        redirect(exchange, saved
                ? "/admin/evenements?success=Opération réussie"
                : "/admin/evenements?error=Échec de l'enregistrement");
    }

    // ============================================================
    // CHARGEMENT DES PERMISSIONS
    // ============================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

    // ============================================================
    // RÉCUPÉRATION SESSION
    // ============================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("username");
            }
            return userId;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private Timestamp parseTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
                dateStr += ":00";
            }
            dateStr = dateStr.replace("T", " ");
            return Timestamp.valueOf(dateStr);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Erreur parsing date: {0} - {1}",
                    new Object[]{dateStr, e.getMessage()});
            return null;
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        try {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } else if (idx == -1 && !pair.isBlank()) {
                    params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur parsing query: " + e.getMessage());
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    private void sendJsonError(HttpExchange exchange, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        String json = new com.google.gson.Gson().toJson(error);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(500, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}