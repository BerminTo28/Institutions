import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiant implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiant.class.getName());
    private static final Gson GSON = new Gson();
    private static final String UPLOAD_DIR = "data/uploads/photos/";

    /** Verrou anti-double-POST : clé = sessionId|path */
    private static final Set<String> POSTS_EN_COURS = ConcurrentHashMap.newKeySet();

    static {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                LOGGER.info(() -> "📁 Dossier upload créé : " + uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Impossible de créer le dossier d'upload", e);
        }
    }

    // =========================================================
    // POINT D'ENTRÉE
    // =========================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        final String path = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "🚀 HandlerEtudiant - Méthode: " + method + ", Path: " + path);

        final String sessionIdBrut = extractSessionId(exchange.getRequestHeaders().getFirst("Cookie"));
        final String sessionId = (sessionIdBrut != null) ? sessionIdBrut : "ANON";
        final String verrouKey = sessionId + "|" + path;

        final boolean isPost = "POST".equalsIgnoreCase(method);

        if (isPost) {
            if (!POSTS_EN_COURS.add(verrouKey)) {
                LOGGER.warning(() -> "⛔ POST déjà en cours pour " + verrouKey + " → rejeté (429)");
                try {
                    exchange.getResponseHeaders().set("Retry-After", "1");
                    exchange.sendResponseHeaders(429, -1);
                } catch (IOException ignored) { /* rien */ }
                return;
            }
        }

        try {
            final String institutionId = getInstitutionIdFromSession(exchange);
            final String utilisateurId = getUtilisateurIdFromSession(exchange);

            if (institutionId == null || institutionId.isBlank()
                    || utilisateurId == null || utilisateurId.isBlank()) {
                LOGGER.warning("⛔ Session invalide - Redirection vers login");
                safeRedirect(exchange, "/login?error=Session+expirée");
                return;
            }

            final AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                LOGGER.warning(() -> "⛔ Permissions non trouvées pour " + utilisateurId);
                safeRedirect(exchange, "/dashboard?error=Permissions+non+trouvées");
                return;
            }

            if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.READ)) {
                LOGGER.warning("⛔ Accès refusé - Pas de READ sur ETUDIANTS");
                safeRedirect(exchange, "/dashboard?error=Accès+non+autorisé");
                return;
            }

            if (path.startsWith("/admin/etudiants/api/")) {
                handleApi(exchange, institutionId, acces);
                return;
            }

            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId, acces);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, institutionId, acces);
            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST, HEAD, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur contrainte SQL", e);
            safeSendError(exchange, 400, "Contrainte violée: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "⚠️ Requête invalide", e);
            safeSendError(exchange, 400, "Requête invalide: " + e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "⚠️ État invalide", e);
            safeSendError(exchange, 409, "Conflit: " + e.getMessage());
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue dans HandlerEtudiant", e);
            safeSendError(exchange, 500, "Erreur interne: " + e.getMessage());
        } finally {
            if (isPost) {
                POSTS_EN_COURS.remove(verrouKey);
            }
        }
    }

    // =========================================================
    // GET
    // =========================================================
    private void handleGet(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException {
        final String query = exchange.getRequestURI().getQuery();
        final Map<String, String> params = parseQueryParams(query);
        final String action = params.get("action");

        LOGGER.info(() -> "📋 GET - action: " + action + ", institutionId: " + institutionId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            if ("nouveau".equalsIgnoreCase(action)) {
                if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.CREATE)) {
                    redirectWithError(exchange, "Permission CREATE refusée pour les étudiants");
                    return;
                }
                final List<String> annees = getAnneesDisponibles(conn, institutionId);
                final List<String> periodes = getPeriodesDisponibles(conn, institutionId);
                final List<String> classes = getClassesDisponibles(conn, institutionId);
                final List<String> promotions = getPromotionsDisponibles(conn, institutionId);
                final String formHtml = UIFormulaireEtudiant.rendreFormulaire(
                        null, institutionId, annees, periodes, classes, promotions, List.of(), "", ""
                );
                ResponseUtil.sendHtml(exchange, 200, formHtml);
                return;
            }

            if ("edit".equalsIgnoreCase(action)) {
                if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.UPDATE)) {
                    redirectWithError(exchange, "Permission UPDATE refusée pour les étudiants");
                    return;
                }
                final String id = params.get("id");
                if (id == null || id.isBlank()) {
                    redirectWithError(exchange, "ID manquant pour la modification");
                    return;
                }
                final EtudiantData etudiantData = new EtudiantData(conn);
                // ✅ Nouvelle signature : read(id, institutionId)
                final Etudiant etudiant = etudiantData.read(id, institutionId);
                if (etudiant == null) {
                    redirectWithError(exchange, "Étudiant introuvable");
                    return;
                }
                final List<String> annees = getAnneesDisponibles(conn, institutionId);
                final List<String> periodes = getPeriodesDisponibles(conn, institutionId);
                final List<String> classes = getClassesDisponibles(conn, institutionId);
                final List<String> promotions = getPromotionsDisponibles(conn, institutionId);
                List<Options> options = new ArrayList<>();
                if (etudiant.getClasse() != null && !etudiant.getClasse().isBlank()
                        && etudiant.getAnneeAcademique() != null && !etudiant.getAnneeAcademique().isBlank()
                        && etudiant.getPeriode() != null && !etudiant.getPeriode().isBlank()) {
                    options = getOptions(conn, institutionId, etudiant.getAnneeAcademique(),
                            etudiant.getPeriode(), etudiant.getClasse(), etudiant.getPromotion());
                }
                final String formHtml = UIFormulaireEtudiant.rendreFormulaire(
                        etudiant, institutionId, annees, periodes, classes, promotions, options, "", ""
                );
                ResponseUtil.sendHtml(exchange, 200, formHtml);
                return;
            }

            // Liste
            final String classeFiltre = params.getOrDefault("classe", "");
            final String anneeFiltre = params.getOrDefault("anneeAcademique", "");
            final String periodeFiltre = params.getOrDefault("periode", "");
            final String motCle = params.getOrDefault("q", "");
            final String successMsg = params.getOrDefault("success", "");
            final String errorMsg = params.getOrDefault("error", "");

            final EtudiantData etudiantData = new EtudiantData(conn);
            final List<Etudiant> etudiants;
            if (!motCle.isBlank()) {
                etudiants = etudiantData.search(institutionId, motCle);
            } else {
                etudiants = etudiantData.readWithFilters(institutionId, classeFiltre,
                        anneeFiltre, periodeFiltre);
            }

            final List<String> annees = getAnneesDisponibles(conn, institutionId);
            final List<String> periodes = getPeriodesDisponibles(conn, institutionId);
            final List<String> classes = getClassesDisponibles(conn, institutionId);
            final List<String> promotions = getPromotionsDisponibles(conn, institutionId);

            final boolean canCreate = acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.CREATE);
            final boolean canUpdate = acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.UPDATE);
            final boolean canDelete = acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.DELETE);

            final String html = UIEtudiant.rendrePage(
                    etudiants, motCle, institutionId, successMsg, errorMsg,
                    annees, periodes, promotions, classes,
                    anneeFiltre, periodeFiltre, classeFiltre,
                    canCreate, canUpdate, canDelete
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL dans handleGet", e);
            ResponseUtil.sendError(exchange, 500, "Erreur base de données: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "⚠️ Paramètres invalides dans handleGet", e);
            redirectWithError(exchange, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue dans handleGet", e);
            ResponseUtil.sendError(exchange, 500, "Erreur interne: " + e.getMessage());
        }
    }

    // =========================================================
    // POST
    // =========================================================
    private void handlePost(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException {
        LOGGER.info("📝 === DÉBUT handlePost ===");

        try {
            final String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            LOGGER.info(() -> "📋 Content-Type: " + contentType);

            Map<String, String> paramsTemp;
            byte[] photoDataTemp = null;
            String photoFilenameTemp = null;

            if (contentType != null && contentType.startsWith("multipart/form-data")) {
                final String boundary = extractBoundary(contentType);
                if (boundary == null) {
                    redirectWithError(exchange, "Boundary manquant");
                    return;
                }
                final byte[] body = exchange.getRequestBody().readAllBytes();
                final MultipartResult result = parseMultipart(body, boundary);
                paramsTemp = result.fields;
                photoDataTemp = result.photoData;
                photoFilenameTemp = result.photoFilename;
            } else {
                final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                paramsTemp = parseFormData(body);
            }

            final Map<String, String> params = paramsTemp;
            final byte[] photoData = photoDataTemp;
            final String photoFilename = photoFilenameTemp;

            LOGGER.info("🔍 === PARAMÈTRES REÇUS ===");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                LOGGER.info(() -> "   " + entry.getKey() + " = '" + entry.getValue() + "'");
            }
            LOGGER.info(() -> "   [photoData] = " + (photoData != null ? photoData.length + " octets" : "absent"));
            LOGGER.info(() -> "   [photoFilename] = " + photoFilename);

            final String action = params.get("action");
            if (action == null || action.isBlank()) {
                redirectWithError(exchange, "Action non spécifiée");
                return;
            }

            switch (action) {
                case "delete" -> {
                    if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.DELETE)) {
                        redirectWithError(exchange, "Permission DELETE refusée");
                        return;
                    }
                    handleDeleteAction(exchange, params, institutionId);
                }

                case "add" -> {
                    if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.CREATE)) {
                        redirectWithError(exchange, "Permission CREATE refusée");
                        return;
                    }
                    handleAddOrUpdateAction(exchange, params, photoData, photoFilename, institutionId);
                }

                case "update" -> {
                    if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.UPDATE)) {
                        redirectWithError(exchange, "Permission UPDATE refusée");
                        return;
                    }
                    handleAddOrUpdateAction(exchange, params, photoData, photoFilename, institutionId);
                }

                default -> redirectWithError(exchange, "Action inconnue : " + action);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur dans handlePost", e);
            if (exchange.getResponseCode() == -1) {
                try {
                    redirectWithError(exchange, "Erreur serveur: " + e.getMessage());
                } catch (IOException ignored) {
                    LOGGER.log(Level.SEVERE, "Impossible d'envoyer la réponse d'erreur", ignored);
                }
            }
        } finally {
            LOGGER.info("📝 === FIN handlePost ===");
        }
    }

    // =========================================================
    // DELETE (formulaire)
    // =========================================================
    private void handleDeleteAction(HttpExchange exchange, Map<String, String> params,
                                     String institutionId) throws IOException {
        final String id = params.get("id");
        if (id == null || id.isBlank()) {
            redirectWithError(exchange, "ID manquant pour la suppression");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            final boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                final EtudiantData etudiantData = new EtudiantData(conn);
                // ✅ Nouvelle signature : read(id, institutionId)
                final Etudiant etudiant = etudiantData.read(id, institutionId);
                if (etudiant == null) {
                    conn.rollback();
                    LOGGER.warning(() -> "⛔ Étudiant " + id
                            + " introuvable dans " + institutionId);
                    redirectWithError(exchange, "Étudiant introuvable dans cette institution");
                    return;
                }

                final EtudiantManager manager = new EtudiantManager(etudiantData);
                // ✅ Nouvelle signature : supprimerEtudiant(id, institutionId)
                final boolean ok = manager.supprimerEtudiant(id, institutionId);
                if (!ok) {
                    conn.rollback();
                    redirectWithError(exchange, "Échec suppression étudiant");
                    return;
                }

                mettreAJourNombreEtudiants(conn,
                        etudiant.getInstitutionId(),
                        etudiant.getClasse(),
                        etudiant.getAnneeAcademique(),
                        etudiant.getPeriode());

                conn.commit();
                LOGGER.info(() -> "✅ Suppression étudiant " + id
                        + " (institution " + institutionId + ")");
                declencherSyncAsyncApresCommit();
                redirectWithMessage(exchange, "success");

            } catch (IllegalArgumentException e) {
                conn.rollback();
                LOGGER.log(Level.WARNING, "⚠️ Rollback suppression (validation)", e);
                redirectWithError(exchange, e.getMessage());
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression étudiant", e);
                redirectWithError(exchange, "Erreur base de données: " + e.getMessage());
            } catch (IOException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression (inattendue)", e);
                redirectWithError(exchange, "Erreur inattendue: " + e.getMessage());
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) { /* rien */ }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion suppression", e);
            redirectWithError(exchange, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================
    // ADD / UPDATE (formulaire)
    // =========================================================
    private void handleAddOrUpdateAction(HttpExchange exchange,
                                          final Map<String, String> params,
                                          final byte[] photoData,
                                          final String photoFilename,
                                          final String institutionId) throws IOException {

        final String action = params.get("action");
        final boolean isUpdate = "update".equalsIgnoreCase(action) && notBlank(params.get("numId"));

        LOGGER.info(() -> "🔍 action=" + action + ", isUpdate=" + isUpdate
                + ", numId='" + params.get("numId") + "'");

        final Etudiant etudiant;
        if (isUpdate) {
            etudiant = chargerEtudiantExistant(params.get("numId"), institutionId);
            if (etudiant == null) {
                redirectWithError(exchange, "Étudiant introuvable pour modification");
                return;
            }
        } else {
            etudiant = new Etudiant();
            final String generatedId = genererIdRobuste(params, institutionId);
            etudiant.setNumeroIdentifiantEtudiant(generatedId);
            LOGGER.info(() -> "🔑 ID généré : '" + generatedId + "'");
        }

        etudiant.setInstitutionId(institutionId);
        etudiant.setNom(params.get("nom"));
        etudiant.setPrenom(params.get("prenom"));
        etudiant.setSexe(params.get("sexe"));
        etudiant.setGroupeSanguin(params.get("groupeSanguin"));
        etudiant.setTelephone(params.get("telephone"));
        etudiant.setEmail(params.get("email"));
        etudiant.setAdresse(params.get("adresse"));
        etudiant.setClasse(params.get("classe"));
        etudiant.setAnneeAcademique(params.get("anneeAcademique"));
        etudiant.setPeriode(params.get("periode"));
        etudiant.setPromotion(params.get("promotion"));
        etudiant.setNinu(params.get("ninu"));
        etudiant.setDepartementNaissance(params.get("departementNaissance"));
        etudiant.setCommuneNaissance(params.get("communeNaissance"));
        etudiant.setDepartementResidence(params.get("departementResidence"));
        etudiant.setCommuneResidence(params.get("communeResidence"));
        etudiant.setNomPere(params.get("nomPere"));
        etudiant.setPrenomPere(params.get("prenomPere"));
        etudiant.setNomMere(params.get("nomMere"));
        etudiant.setPrenomMere(params.get("prenomMere"));
        etudiant.setTelephoneParents(params.get("telephoneParents"));
        etudiant.setResidenceParents(params.get("residenceParents"));
        etudiant.setLienParente(params.get("lienParente"));
        etudiant.setTypeResponsable(params.get("typeResponsable"));
        etudiant.setNomResponsable(params.get("nomResponsable"));
        etudiant.setPrenomResponsable(params.get("prenomResponsable"));
        etudiant.setOption(params.get("option"));
        etudiant.setObservations(params.get("observations"));
        etudiant.setBiometrieActive("true".equalsIgnoreCase(params.get("biometrieActive")));

        etudiant.setDateNaissance(parseDateOuNull(params.get("dateNaissance")));
        etudiant.setDateInscription(parseDateOuNull(params.get("dateInscription")));

        final String matricule = params.get("matricule");
        if (notBlank(matricule)) {
            etudiant.setMatricule(matricule.trim());
        } else if (!isUpdate) {
            etudiant.setMatricule(genererMatricule(etudiant.getNom(), etudiant.getPrenom(),
                    etudiant.getAnneeAcademique()));
        }

        if (!notBlank(etudiant.getStatut())) {
            etudiant.setStatut("ACTIF");
        }

        try {
            String photoPath = params.get("photoPathActuelle");
            if (photoData != null && photoData.length > 0) {
                photoPath = sauvegarderPhoto(photoData, photoFilename,
                        etudiant.getNumeroIdentifiantEtudiant() != null
                                ? etudiant.getNumeroIdentifiantEtudiant()
                                : "tmp-" + System.currentTimeMillis());
                LOGGER.log(Level.INFO, "📸 Photo sauvegardée : {0}", photoPath);
            } else {
                final String photoBase64 = params.get("photoBase64");
                if (notBlank(photoBase64) && !notBlank(photoPath)) {
                    photoPath = sauvegarderPhotoBase64(photoBase64,
                            etudiant.getNumeroIdentifiantEtudiant() != null
                                    ? etudiant.getNumeroIdentifiantEtudiant()
                                    : "tmp-" + System.currentTimeMillis());
                    LOGGER.log(Level.INFO, "📸 Photo base64 sauvegardée : {0}", photoPath);
                }
            }
            etudiant.setPhotoPath(photoPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur sauvegarde photo", e);
            redirectWithError(exchange, "Erreur sauvegarde photo: " + e.getMessage());
            return;
        }

        LOGGER.info(() -> "🔍 AVANT MANAGER : id='" + etudiant.getNumeroIdentifiantEtudiant()
                + "', nom='" + etudiant.getNom()
                + "', prenom='" + etudiant.getPrenom()
                + "', dateNaissance=" + etudiant.getDateNaissance()
                + "', photoPath='" + etudiant.getPhotoPath()
                + "', institution='" + etudiant.getInstitutionId() + "'");

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            final boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                final EtudiantData dao = new EtudiantData(conn);
                final EtudiantManager manager = new EtudiantManager(dao);

                // ✅ ÉtudiantManager ne lance plus SQLException sur create/update
                final boolean success;
                if (isUpdate) {
                    success = manager.modifierEtudiant(etudiant);
                } else {
                    success = manager.enregistrerEtudiant(etudiant);
                }

                if (!success) {
                    conn.rollback();
                    redirectWithError(exchange, "Erreur lors de l'enregistrement");
                    return;
                }

                mettreAJourNombreEtudiants(conn, etudiant.getInstitutionId(),
                        etudiant.getClasse(), etudiant.getAnneeAcademique(),
                        etudiant.getPeriode());

                conn.commit();
                LOGGER.info(() -> "✅ Étudiant " + etudiant.getNumeroIdentifiantEtudiant()
                        + " enregistré (transaction)");
                declencherSyncAsyncApresCommit();
                redirectWithMessage(exchange, "success");

            } catch (IllegalArgumentException e) {
                conn.rollback();
                LOGGER.warning(() -> "⛔ Validation métier : " + e.getMessage());
                redirectWithError(exchange, e.getMessage());

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Erreur SQL", e);
                redirectWithError(exchange, "Erreur base de données: " + e.getMessage());

            } catch (IOException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Erreur inattendue", e);
                redirectWithError(exchange, "Erreur inattendue: " + e.getMessage());

            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) { /* rien */ }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion", e);
            redirectWithError(exchange, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================
    // GÉNÉRATION D'ID ROBUSTE
    // =========================================================
    private String genererIdRobuste(Map<String, String> params, String institutionId) {
        final String nom = params.get("nom");
        final String prenom = params.get("prenom");
        final java.sql.Date dateNaissance = parseDateOuNull(params.get("dateNaissance"));
        final String sexe = params.get("sexe");

        String generatedId = null;
        try {
            generatedId = GenerateurCle.genererIDIndividu(nom, prenom, dateNaissance, sexe, institutionId);
            LOGGER.log(Level.INFO, "🔑 ID via GenerateurCle : ''{0}''", generatedId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ GenerateurCle a échoué", e);
        }

        if (generatedId == null || generatedId.isBlank()) {
            generatedId = "ETU-" + System.currentTimeMillis();
            LOGGER.log(Level.INFO, "🔑 ID fallback : ''{0}''", generatedId);
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final EtudiantData dao = new EtudiantData(conn);
            // ✅ Nouvelle signature : existsWithInstitution
            if (dao.existsWithInstitution(generatedId, institutionId)) {
                generatedId = generatedId + "_" + System.currentTimeMillis();
                LOGGER.log(Level.INFO, "🔑 ID ajusté (doublon) : ''{0}''", generatedId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "⚠️ Vérification unicité ID échouée", e);
        }

        return generatedId;
    }

    // ---------- Helpers ----------
    private Etudiant chargerEtudiantExistant(String id, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // ✅ Nouvelle signature : read(id, institutionId)
            return new EtudiantData(conn).read(id, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur chargement étudiant", e);
            return null;
        }
    }

    private java.sql.Date parseDateOuNull(String s) {
        if (!notBlank(s)) return null;
        try {
            return java.sql.Date.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // =========================================================
    // API
    // =========================================================
    private void handleApi(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException, SQLException {
        final String path = exchange.getRequestURI().getPath();
        final String query = exchange.getRequestURI().getQuery();
        final Map<String, String> params = parseQueryParams(query);

        LOGGER.info(() -> "📡 API appelée: " + path + " avec params: " + params);

        try {
            if (path.endsWith("/liste")) {
                handleListeApi(exchange, institutionId);
                return;
            }
            if (path.endsWith("/delete")) {
                if (!acces.hasPermission(AccesAdmin.Module.ETUDIANTS, AccesAdmin.Action.DELETE)) {
                    sendJsonError(exchange, "Permission DELETE refusée");
                    return;
                }
                handleDeleteApi(exchange, params, institutionId);
                return;
            }
            if (path.endsWith("/genererId")) {
                handleGenererIdApi(exchange);
                return;
            }
            if (path.endsWith("/options")) {
                handleApiOptions(exchange, params, institutionId);
                return;
            }
            if (path.endsWith("/verifierCapacite")) {
                handleVerifierCapaciteApi(exchange, params, institutionId);
                return;
            }
            sendJsonError(exchange, "API inconnue");

        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "⚠️ Paramètres invalides API", e);
            sendJsonError(exchange, e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue API", e);
            sendJsonError(exchange, "Erreur interne: " + e.getMessage());
        }
    }

    private void handleListeApi(HttpExchange exchange, String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final EtudiantData etudiantData = new EtudiantData(conn);
            final List<Etudiant> etudiants = etudiantData.readAll(institutionId);
            final Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", etudiants);
            sendJson(exchange, result);
        } catch (SQLException e) {
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    private void handleDeleteApi(HttpExchange exchange, Map<String, String> params,
                                  String institutionId) throws IOException {
        final String id = params.get("id");
        if (id == null || id.isBlank()) {
            sendJsonError(exchange, "ID requis");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            final boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                final EtudiantData etudiantData = new EtudiantData(conn);
                // ✅ Nouvelle signature : read(id, institutionId)
                final Etudiant etudiant = etudiantData.read(id, institutionId);
                if (etudiant == null) {
                    conn.rollback();
                    LOGGER.warning(() -> "⛔ Étudiant " + id
                            + " introuvable dans " + institutionId);
                    sendJsonError(exchange, "Étudiant introuvable dans cette institution");
                    return;
                }

                final EtudiantManager manager = new EtudiantManager(etudiantData);
                // ✅ Nouvelle signature : supprimerEtudiant(id, institutionId)
                final boolean ok = manager.supprimerEtudiant(id, institutionId);
                if (!ok) {
                    conn.rollback();
                    sendJsonError(exchange, "Échec suppression étudiant");
                    return;
                }

                mettreAJourNombreEtudiants(conn,
                        etudiant.getInstitutionId(),
                        etudiant.getClasse(),
                        etudiant.getAnneeAcademique(),
                        etudiant.getPeriode());

                conn.commit();
                LOGGER.info(() -> "✅ Suppression API étudiant " + id
                        + " (institution " + institutionId + ")");
                declencherSyncAsyncApresCommit();

                final Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                sendJson(exchange, result);

            } catch (IllegalArgumentException e) {
                conn.rollback();
                LOGGER.log(Level.WARNING, "⚠️ Rollback suppression API (validation)", e);
                sendJsonError(exchange, e.getMessage());
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression API", e);
                sendJsonError(exchange, "Erreur base de données");
            } catch (IOException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback suppression API (inattendue)", e);
                sendJsonError(exchange, "Erreur inattendue: " + e.getMessage());
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) { /* rien */ }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion suppression API", e);
            sendJsonError(exchange, "Erreur base de données");
        } finally {
            closeQuietly(conn);
        }
    }

    private void handleGenererIdApi(HttpExchange exchange) throws IOException {
        final Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", "ETU-" + System.currentTimeMillis());
        sendJson(exchange, result);
    }

    private void handleApiOptions(HttpExchange exchange, Map<String, String> params,
                                   String institutionId) throws IOException {
        final String annee = params.get("annee");
        final String periode = params.get("periode");
        final String classe = params.get("classe");
        final String promotion = params.get("promotion");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final List<Options> options = getOptions(conn, institutionId, annee, periode, classe, promotion);
            final List<Map<String, String>> result = new ArrayList<>();
            for (Options opt : options) {
                final Map<String, String> map = new HashMap<>();
                map.put("option", opt.getOption());
                result.add(map);
            }
            final Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            sendJson(exchange, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL options", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    private void handleVerifierCapaciteApi(HttpExchange exchange, Map<String, String> params,
                                            String institutionId) throws IOException {
        final String classe = params.get("classe");
        final String anneeAcademique = params.get("anneeAcademique");
        final String periode = params.get("periode");

        if (classe == null || classe.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()
                || periode == null || periode.isBlank()) {
            sendJsonError(exchange, "Classe, année académique et période sont requis");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final String codeClasse = getCodeClasseFromNom(conn, classe, institutionId);
            final Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            if (codeClasse == null) {
                result.put("classeExistante", false);
                result.put("message", "La classe n'existe pas dans cette institution");
                sendJson(exchange, result);
                return;
            }

            final ClasseData classeData = new ClasseData();
            // ✅ getCapaciteClasse ne lance plus SQLException
            final int capacite = classeData.getCapaciteClasse(codeClasse, institutionId);
            final int nombreActuel = compterEtudiantsDansClasse(conn, institutionId, classe,
                    anneeAcademique, periode);

            result.put("classeExistante", true);
            result.put("codeClasse", codeClasse);
            result.put("capacite", capacite);
            result.put("nombreActuel", nombreActuel);
            result.put("placesDisponibles", Math.max(0, capacite - nombreActuel));
            result.put("estPleine", nombreActuel >= capacite);

            sendJson(exchange, result);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL vérification capacité", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    private void declencherSyncAsyncApresCommit() {
        try {
            DatabaseManager.getInstance().declencherSyncImmediateAsync();
            LOGGER.fine("⚡ Sync asynchrone déclenchée après commit.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Impossible de déclencher la sync async", e);
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException ignored) { /* rien */ }
    }

    // =========================================================
    // SESSION
    // =========================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            final AccesAdminData dao = new AccesAdminData(conn);
            // ✅ loadByUtilisateurId ne lance plus SQLException
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur connexion chargerPermissions", e);
            return new AccesAdmin();
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            final String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            final String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            final HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur getInstitutionIdFromSession: " + e.getMessage());
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        try {
            final String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            final String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            final HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (userId == null || userId.isBlank()) {
                userId = (String) session.getAttribute("username");
            }
            return userId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur getUtilisateurIdFromSession: " + e.getMessage());
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            final String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    // =========================================================
    // UPLOAD PHOTOS
    // =========================================================
    private String sauvegarderPhoto(byte[] data, String filename, String etudiantId)
            throws IOException {
        final String nomFichier = (filename == null || filename.isBlank()) ? "photo.jpg" : filename;
        String ext = "";
        final int dot = nomFichier.lastIndexOf('.');
        if (dot > 0) {
            ext = nomFichier.substring(dot);
        }
        final String newName = etudiantId + "_" + System.currentTimeMillis() + ext;

        // ✅ Utilise UPLOAD_DIR (constante) au lieu d'un chemin en dur
        final Path target = Paths.get(UPLOAD_DIR, newName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);

        return "/data/uploads/photos/" + newName;
    }

    private String sauvegarderPhotoBase64(String base64, String etudiantId) throws IOException {
        final String[] parts = base64.split(",");
        final byte[] data;
        String ext = ".jpg";
        if (parts.length == 2) {
            final String header = parts[0];
            if (header.contains("png")) ext = ".png";
            else if (header.contains("gif")) ext = ".gif";
            else if (header.contains("webp")) ext = ".webp";
            data = Base64.getDecoder().decode(parts[1]);
        } else {
            data = Base64.getDecoder().decode(base64);
        }
        final String newName = etudiantId + "_" + System.currentTimeMillis() + ext;
        final Path target = Paths.get(UPLOAD_DIR, newName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return "/data/uploads/photos/" + newName;
    }

    // =========================================================
    // MULTIPART
    // =========================================================
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        final Pattern p = Pattern.compile("boundary=([^;]+)");
        final Matcher m = p.matcher(contentType);
        return m.find() ? m.group(1) : null;
    }

    private MultipartResult parseMultipart(byte[] body, String boundary) {
        final Map<String, String> fields = new HashMap<>();
        byte[] photoData = null;
        String photoFilename = null;

        final byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        final byte[] endBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.ISO_8859_1);

        int start = 0;
        while (start < body.length) {
            int partStart = indexOf(body, boundaryBytes, start);
            if (partStart == -1) break;
            partStart += boundaryBytes.length;
            if (partStart + 1 < body.length && body[partStart] == '\r' && body[partStart + 1] == '\n') {
                partStart += 2;
            } else if (partStart < body.length && body[partStart] == '\n') {
                partStart += 1;
            }

            int partEnd = indexOf(body, boundaryBytes, partStart);
            if (partEnd == -1) partEnd = body.length;
            if (partEnd < body.length && startsWith(body, endBoundaryBytes, partEnd)) break;

            final int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), partStart);
            if (headerEnd == -1) {
                start = partEnd;
                continue;
            }

            final byte[] headerBytes = new byte[headerEnd - partStart];
            System.arraycopy(body, partStart, headerBytes, 0, headerBytes.length);
            final String headers = new String(headerBytes, StandardCharsets.ISO_8859_1);

            int contentStart = headerEnd + 4;
            int contentEnd = partEnd;
            if (contentEnd > contentStart + 1 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            } else if (contentEnd > contentStart && body[contentEnd - 1] == '\n') {
                contentEnd -= 1;
            }

            String name = null;
            String filename = null;
            for (String line : headers.split("\r\n")) {
                if (line.startsWith("Content-Disposition:")) {
                    final Matcher mName = Pattern.compile("name=\"([^\"]+)\"").matcher(line);
                    if (mName.find()) name = mName.group(1);
                    final Matcher mFilename = Pattern.compile("filename=\"([^\"]+)\"").matcher(line);
                    if (mFilename.find()) filename = mFilename.group(1);
                }
            }

            if (name != null) {
                if (filename != null && !filename.isBlank()) {
                    photoFilename = filename;
                    final int length = contentEnd - contentStart;
                    if (length > 0) {
                        photoData = new byte[length];
                        System.arraycopy(body, contentStart, photoData, 0, length);
                    }
                } else {
                    final byte[] valueBytes = new byte[contentEnd - contentStart];
                    System.arraycopy(body, contentStart, valueBytes, 0, valueBytes.length);
                    fields.put(name, new String(valueBytes, StandardCharsets.UTF_8).trim());
                }
            }

            start = partEnd;
        }

        return new MultipartResult(fields, photoData, photoFilename);
    }

    private int indexOf(byte[] array, byte[] target, int start) {
        outer:
        for (int i = start; i <= array.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private boolean startsWith(byte[] array, byte[] prefix, int offset) {
        if (offset + prefix.length > array.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (array[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private static class MultipartResult {
        final Map<String, String> fields;
        final byte[] photoData;
        final String photoFilename;

        MultipartResult(Map<String, String> fields, byte[] photoData, String photoFilename) {
            this.fields = fields;
            this.photoData = photoData;
            this.photoFilename = photoFilename;
        }
    }

    // =========================================================
    // PARSING FORM
    // =========================================================
    private Map<String, String> parseQueryParams(String query) {
        final Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            final String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) { /* rien */ }
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        final Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            final int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    final String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    final String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) { /* rien */ }
            }
        }
        return params;
    }

    // =========================================================
    // RÉPONSES HTTP
    // =========================================================
    private void redirectWithMessage(HttpExchange exchange, String status) throws IOException {
        if (exchange.getResponseCode() != -1) {
            LOGGER.fine(() -> "Réponse déjà committée, redirection ignorée");
            return;
        }
        final String redirect = "/admin/etudiants?" + status + "=1";
        exchange.getResponseHeaders().set("Location", redirect);
        exchange.sendResponseHeaders(302, -1);
    }

    private void redirectWithError(HttpExchange exchange, String error) throws IOException {
        if (exchange.getResponseCode() != -1) {
            LOGGER.fine(() -> "Réponse déjà committée, redirection d'erreur ignorée");
            return;
        }
        final String redirect = "/admin/etudiants?error="
                + URLEncoder.encode(error != null ? error : "", StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Location", redirect);
        exchange.sendResponseHeaders(302, -1);
    }

    private void safeRedirect(HttpExchange exchange, String location) {
        try {
            if (exchange.getResponseCode() != -1) return;
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Impossible de rediriger vers " + location, e);
        }
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        final String json = GSON.toJson(data);
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, String message) throws IOException {
        final Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, error);
    }

    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            ResponseUtil.sendError(exchange, status, message);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Impossible d'envoyer la réponse d'erreur HTTP", e);
        }
    }

    // =========================================================
    // DONNÉES DE RÉFÉRENCE
    // =========================================================
    private List<String> getAnneesDisponibles(Connection conn, String institutionId)
            throws SQLException {
        final List<String> annees = new ArrayList<>();
        final String sql = "SELECT DISTINCT annee_academique FROM annees_academiques "
                + "WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) annees.add(rs.getString(1));
            }
        }
        if (annees.isEmpty()) {
            final String sql2 = "SELECT DISTINCT annee_academique FROM etudiants "
                    + "WHERE institution_id = ? ORDER BY annee_academique DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql2)) {
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString(1));
                }
            }
        }
        if (annees.isEmpty()) annees.add("2024-2025");
        return annees;
    }

    private List<String> getPeriodesDisponibles(Connection conn, String institutionId)
            throws SQLException {
        final List<String> periodes = new ArrayList<>();
        final String sql = "SELECT DISTINCT periode FROM periodes "
                + "WHERE institution_id = ? ORDER BY periode";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) periodes.add(rs.getString(1));
            }
        }
        if (periodes.isEmpty()) periodes.add("None");
        return periodes;
    }

    private List<String> getClassesDisponibles(Connection conn, String institutionId)
            throws SQLException {
        final List<String> classes = new ArrayList<>();
        final String sql = "SELECT DISTINCT nom_classe FROM classes "
                + "WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) classes.add(rs.getString(1));
            }
        }
        if (classes.isEmpty()) {
            classes.addAll(List.of("6ème", "5ème", "4ème", "3ème", "2nde", "1ère", "Terminale"));
        }
        return classes;
    }

    private List<String> getPromotionsDisponibles(Connection conn, String institutionId)
            throws SQLException {
        final List<String> promos = new ArrayList<>();
        final String sql = "SELECT DISTINCT promotion FROM classes "
                + "WHERE institution_id = ? AND promotion IS NOT NULL ORDER BY promotion";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final String p = rs.getString(1);
                    if (p != null && !p.isBlank()) promos.add(p);
                }
            }
        }
        if (promos.isEmpty()) promos.addAll(List.of("A", "B", "C", "D"));
        return promos;
    }

    private List<Options> getOptions(Connection conn, String institutionId, String annee,
                                      String periode, String classe, String promotion)
            throws SQLException {
        final OptionData optionData = new OptionData(conn);
        final List<Options> toutes = optionData.readAll(institutionId);
        final List<Options> filtered = new ArrayList<>();
        for (Options opt : toutes) {
            if (isMatching(opt.getAnneeAcademique(), annee)
                    && isMatching(opt.getPeriode(), periode)
                    && isMatching(opt.getClasse(), classe)
                    && (promotion == null || promotion.isBlank()
                        || isMatching(opt.getPromotion(), promotion))) {
                filtered.add(opt);
            }
        }
        return filtered;
    }

    private boolean isMatching(String dbValue, String filterValue) {
        if (dbValue == null && filterValue == null) return true;
        if (dbValue == null || filterValue == null) return false;
        return normalize(dbValue).equals(normalize(filterValue));
    }

    private String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase().trim()
                .replace("é", "e").replace("è", "e").replace("ê", "e")
                .replace("à", "a").replace("â", "a")
                .replace("î", "i").replace("ï", "i")
                .replace("ô", "o").replace("ö", "o")
                .replace("ç", "c")
                .replaceAll("\\s+", " ");
    }

    private String genererMatricule(String nom, String prenom, String annee) {
        final String nomSafe = (nom == null) ? "XXX" : nom;
        final String prenomSafe = (prenom == null) ? "XXX" : prenom;
        final String anneeSafe = (annee == null) ? "2024-2025" : annee;
        final String nomPart = nomSafe.length() >= 3 ? nomSafe.substring(0, 3).toUpperCase() : nomSafe.toUpperCase();
        final String prenomPart = prenomSafe.length() >= 3 ? prenomSafe.substring(0, 3).toUpperCase() : prenomSafe.toUpperCase();
        String anneePart = anneeSafe.replace("-", "");
        anneePart = anneePart.length() >= 4 ? anneePart.substring(0, 4) : anneePart;
        return "M-" + nomPart + prenomPart + anneePart;
    }

    // =========================================================
    // CAPACITÉ
    // =========================================================
    private String getCodeClasseFromNom(Connection conn, String nomClasse, String institutionId)
            throws SQLException {
        final String sql = "SELECT code_classe FROM classes "
                + "WHERE nom_classe = ? AND institution_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nomClasse);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("code_classe");
            }
        }
        return null;
    }

    private int compterEtudiantsDansClasse(Connection conn, String institutionId, String nomClasse,
                                            String anneeAcademique, String periode)
            throws SQLException {
        final String sql = "SELECT COUNT(*) FROM etudiants "
                + "WHERE institution_id = ? AND classe = ? "
                + "AND annee_academique = ? AND periode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, nomClasse);
            ps.setString(3, anneeAcademique);
            ps.setString(4, periode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private void mettreAJourNombreEtudiants(Connection conn, String institutionId, String nomClasse,
                                             String anneeAcademique, String periode)
            throws SQLException {
        if (nomClasse == null || nomClasse.isBlank()) return;
        final String codeClasse = getCodeClasseFromNom(conn, nomClasse, institutionId);
        if (codeClasse == null) return;
        final int nbEtudiants = compterEtudiantsDansClasse(conn, institutionId, nomClasse,
                anneeAcademique, periode);
        final String sqlUpdate = "UPDATE classes SET nombre_etudiants = ? "
                + "WHERE code_classe = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setInt(1, nbEtudiants);
            ps.setString(2, codeClasse);
            ps.setString(3, institutionId);
            ps.executeUpdate();
        }
    }
}