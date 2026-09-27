import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseur implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerProfesseur.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        LOGGER.log(Level.INFO, "🚀 PROFESSEUR HANDLER - Requête reçue: {0} {1}",
                new Object[]{exchange.getRequestMethod(), path});

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // ① Session
        String institutionId = getInstitutionIdFromSession(exchange);
        String utilisateurId = getUtilisateurIdFromSession(exchange);

        if (institutionId == null || institutionId.isBlank()
                || utilisateurId == null || utilisateurId.isBlank()) {
            redirect(exchange, "/login?error=Session+expirée");
            return;
        }

        // ② Permissions
        AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
        if (acces == null) {
            redirect(exchange, "/dashboard?error=Permissions+non+trouvées");
            return;
        }
        if (!acces.hasPermission(AccesAdmin.Module.PROFESSEUR, AccesAdmin.Action.READ)) {
            redirect(exchange, "/dashboard?error=Accès+non+autorisé+au+module+PROFESSEUR");
            return;
        }

        // ③ API génération ID
        if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/genererId")) {
            handleGenererId(exchange, institutionId);
            return;
        }

        // ④ POST = actions
        if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange, institutionId, acces);
            return;
        }

        // ⑤ GET = affichage
        handleGet(exchange, institutionId, acces);
    }

    // ============================================================
    // GET — Affichage de la liste
    // ============================================================
    private void handleGet(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException {

        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String motCle = params.getOrDefault("q", "");

        List<Professeur> professeurs = new ArrayList<>();
        List<Matiere> matieres = new ArrayList<>();
        String messageErreur = "";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurManager manager = new ProfesseurManager(conn);
            professeurs = manager.listerTous(institutionId);

            if (motCle != null && !motCle.isBlank()) {
                String lower = motCle.toLowerCase();
                professeurs = professeurs.stream()
                        .filter(p -> (p.getNom() != null
                                        && p.getNom().toLowerCase().contains(lower))
                                || (p.getPrenom() != null
                                        && p.getPrenom().toLowerCase().contains(lower))
                                || (p.getNumeroIdentifiantProfesseur() != null
                                        && p.getNumeroIdentifiantProfesseur()
                                                .toLowerCase().contains(lower)))
                        .toList();
            }

            // Charger les créneaux de chaque professeur
            for (Professeur p : professeurs) {
                List<Professeur.Creneau> creneaux = manager.obtenirCreneaux(
                        p.getNumeroIdentifiantProfesseur(), institutionId);
                p.setCreneaux(creneaux);
            }

            MatiereManager matiereManager = new MatiereManager(institutionId);
            matieres = matiereManager.listerToutesMatieres();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur chargement: {0}", e.getMessage());
            messageErreur = "Erreur de base de données: " + e.getMessage();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue: {0}", e.getMessage());
            messageErreur = "Erreur: " + e.getMessage();
        }

        boolean canCreate = acces.hasPermission(
                AccesAdmin.Module.PROFESSEUR, AccesAdmin.Action.CREATE);
        boolean canUpdate = acces.hasPermission(
                AccesAdmin.Module.PROFESSEUR, AccesAdmin.Action.UPDATE);
        boolean canDelete = acces.hasPermission(
                AccesAdmin.Module.PROFESSEUR, AccesAdmin.Action.DELETE);

        String html = UIProfesseur.rendrePage(
                professeurs, matieres, motCle, institutionId, messageErreur,
                canCreate, canUpdate, canDelete);
        sendResponse(exchange, 200, html);
    }

    // ============================================================
    // POST — Dispatch par action
    // ============================================================
    private void handlePost(HttpExchange exchange, String institutionId, AccesAdmin acces)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, String> params = parseQueryParams(body);
        LOGGER.log(Level.INFO, "📝 POST reçu: {0}", params);

        String action = params.get("action");
        if (action == null || action.isBlank()) {
            redirect(exchange, "/admin/professeurs?error=Action non spécifiée");
            return;
        }

        switch (action) {
            case "delete" -> {
                if (!acces.hasPermission(AccesAdmin.Module.PROFESSEUR,
                        AccesAdmin.Action.DELETE)) {
                    redirect(exchange, "/admin/professeurs?error=Permission+DELETE+refusée");
                    return;
                }
                handleDelete(exchange, params, institutionId);
            }
            case "add" -> {
                if (!acces.hasPermission(AccesAdmin.Module.PROFESSEUR,
                        AccesAdmin.Action.CREATE)) {
                    redirect(exchange, "/admin/professeurs?error=Permission+CREATE+refusée");
                    return;
                }
                handleSave(exchange, params, institutionId, action);
            }
            case "update" -> {
                if (!acces.hasPermission(AccesAdmin.Module.PROFESSEUR,
                        AccesAdmin.Action.UPDATE)) {
                    redirect(exchange, "/admin/professeurs?error=Permission+UPDATE+refusée");
                    return;
                }
                handleSave(exchange, params, institutionId, action);
            }
            default -> redirect(exchange, "/admin/professeurs?error=Action inconnue");
        }
    }

    // ============================================================
    // SUPPRESSION
    // ============================================================
    private void handleDelete(HttpExchange exchange, Map<String, String> params,
                                String institutionId) throws IOException {
        String id = params.get("id");
        if (id == null || id.isBlank()) {
            redirect(exchange, "/admin/professeurs?error=Identifiant manquant");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurManager manager = new ProfesseurManager(conn);
            boolean deleted = manager.supprimer(id.trim(), institutionId);
            if (!deleted) {
                redirect(exchange, "/admin/professeurs?error=Échec suppression");
                return;
            }
            LOGGER.log(Level.INFO, "🗑️ Professeur supprimé: {0}", id);
            redirect(exchange, "/admin/professeurs?success=Supprimé avec succès");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur suppression: {0}", e.getMessage());
            redirect(exchange, "/admin/professeurs?error=" + e.getMessage());
        }
    }

    // ============================================================
    // SAUVEGARDE (AJOUT / MODIFICATION)
    // ============================================================
    private void handleSave(HttpExchange exchange, Map<String, String> params,
                             String institutionId, String action) throws IOException {

        // ========== 1. Récupération des champs ==========
        String numeroId = params.get("numeroIdentifiantProfesseur");
        String nom = params.get("nom");
        String prenom = params.get("prenom");
        String email = params.get("email");
        String telephone = params.get("telephone");
        String specialite = params.get("specialite");
        String diplome = params.get("diplome");
        String statut = params.getOrDefault("statut", "ACTIF");
        String sexe = params.get("sexe");
        String dateNaissanceStr = params.get("dateNaissance");
        String groupeSanguin = params.get("groupeSanguin");
        String departementNaissance = params.get("departementNaissance");
        String communeNaissance = params.get("communeNaissance");
        String adresse = params.get("adresse");
        String paysHabitation = params.get("paysHabitation");
        String departementHabitation = params.get("departementHabitation");
        String communeHabitation = params.get("communeHabitation");
        String matricule = params.get("matricule");
        String ninu = params.get("ninu");
        String dateEmbauche = params.get("dateEmbauche");
        String salaireStr = params.get("salaire");
        String observations = params.get("observations");
        String situationMatrimoniale = params.get("situationMatrimoniale");
        String biometrieActiveStr = params.get("biometrieActive");
        String photoBase64 = params.get("photoBase64");
        String photoPathActuelle = params.get("photoPathActuelle");

        // ========== 2. Validation ==========
        if (nom == null || nom.isBlank()) {
            redirect(exchange, "/admin/professeurs?error=Le nom est obligatoire");
            return;
        }

        // ========== 3. Vérifications d'unicité ==========
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // NINU
            if (ninu != null && !ninu.isBlank()) {
                String sql = "SELECT COUNT(*) FROM professeurs "
                        + "WHERE ninu = ? AND institution_id = ?";
                if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                    sql += " AND numero_identifiant_professeur != ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ninu.trim());
                    ps.setString(2, institutionId);
                    if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                        ps.setString(3, numeroId.trim());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            redirect(exchange,
                                    "/admin/professeurs?error=Ce NINU est déjà utilisé");
                            return;
                        }
                    }
                }
            }

            // Email
            if (email != null && !email.isBlank()) {
                String sql = "SELECT COUNT(*) FROM professeurs "
                        + "WHERE email = ? AND institution_id = ?";
                if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                    sql += " AND numero_identifiant_professeur != ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email.trim());
                    ps.setString(2, institutionId);
                    if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                        ps.setString(3, numeroId.trim());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            redirect(exchange,
                                    "/admin/professeurs?error=Cet email est déjà utilisé");
                            return;
                        }
                    }
                }
            }

            // Matricule
            if (matricule != null && !matricule.isBlank()) {
                String sql = "SELECT COUNT(*) FROM professeurs "
                        + "WHERE matricule = ? AND institution_id = ?";
                if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                    sql += " AND numero_identifiant_professeur != ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, matricule.trim());
                    ps.setString(2, institutionId);
                    if ("update".equals(action) && numeroId != null && !numeroId.isBlank()) {
                        ps.setString(3, numeroId.trim());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            redirect(exchange,
                                    "/admin/professeurs?error=Ce matricule est déjà utilisé");
                            return;
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur vérification unicité: {0}", e.getMessage());
        }

        // ========== 4. Date de naissance ==========
        java.sql.Date dateNaissance = null;
        try {
            if (dateNaissanceStr != null && !dateNaissanceStr.isBlank()) {
                dateNaissance = java.sql.Date.valueOf(dateNaissanceStr);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "⚠️ Date de naissance invalide: {0}", dateNaissanceStr);
        }

        // ========== 5. ✅ Génération ID via GenerateurCle.genererIDProfesseur ==========
        if (numeroId == null || numeroId.isBlank()) {
            numeroId = GenerateurCle.genererIDProfesseur(
                    nom,
                    prenom != null ? prenom : "X",
                    dateNaissance,
                    sexe,
                    institutionId
            );
            LOGGER.log(Level.INFO, "🔑 Identifiant professeur généré: {0}", numeroId);
        }

        // ========== 6. Construction de l'objet Professeur ==========
        Professeur prof = new Professeur();
        prof.setNumeroIdentifiantProfesseur(numeroId.trim());
        prof.setNom(nom.trim());
        prof.setPrenom(prenom != null ? prenom.trim() : "");
        prof.setEmail(email != null ? email.trim() : "");
        prof.setTelephone(telephone != null ? telephone.trim() : "");
        prof.setSpecialite(specialite != null ? specialite.trim() : "");
        prof.setDiplome(diplome != null ? diplome.trim() : "");
        prof.setStatut(statut);
        prof.setInstitutionId(institutionId);
        prof.setSexe(sexe);
        prof.setGroupeSanguin(groupeSanguin);
        prof.setDepartementNaissance(departementNaissance);
        prof.setCommuneNaissance(communeNaissance);
        prof.setAdresse(adresse);
        prof.setPaysHabitation(paysHabitation);
        prof.setDepartementHabitation(departementHabitation);
        prof.setCommuneHabitation(communeHabitation);
        prof.setMatricule(matricule);
        prof.setNinu(ninu);
        prof.setObservations(observations);
        prof.setSituationMatrimoniale(situationMatrimoniale);

        // ========== 7. Photo ==========
        String newPhotoPath = traiterEtSauvegarderPhoto(photoBase64,
                prof.getNumeroIdentifiantProfesseur());
        if (newPhotoPath != null) {
            prof.setPhotoPath(newPhotoPath);
        } else if (photoPathActuelle != null && !photoPathActuelle.isBlank()) {
            prof.setPhotoPath(photoPathActuelle);
        }

        // ========== 8. Salaire ==========
        try {
            if (salaireStr != null && !salaireStr.isBlank()) {
                prof.setSalaire(Double.parseDouble(salaireStr));
            }
        } catch (NumberFormatException ignored) {}

        prof.setBiometrieActive("true".equalsIgnoreCase(biometrieActiveStr));

        try {
            if (dateNaissance != null) {
                prof.setDateNaissance(dateNaissance);
            }
            if (dateEmbauche != null && !dateEmbauche.isBlank()) {
                prof.setDateEmbauche(java.sql.Date.valueOf(dateEmbauche));
            }
        } catch (IllegalArgumentException ignored) {}

        // ========== 9. Créneaux ==========
        List<Professeur.Creneau> creneaux = new ArrayList<>();
        Set<Integer> indices = new TreeSet<>();
        for (String key : params.keySet()) {
            if (key.startsWith("creneau_code_")
                    || key.startsWith("creneau_matiere_")
                    || key.startsWith("creneau_classe_")
                    || key.startsWith("creneau_jour_")
                    || key.startsWith("creneau_debut_")
                    || key.startsWith("creneau_fin_")
                    || key.startsWith("creneau_coefficient_")
                    || key.startsWith("creneau_annee_")
                    || key.startsWith("creneau_periode_")) {
                try {
                    String suffix = key.substring(key.lastIndexOf("_") + 1);
                    indices.add(Integer.valueOf(suffix));
                } catch (NumberFormatException ignored) {}
            }
        }

        for (int index : indices) {
            String code = params.get("creneau_code_" + index);
            if (code == null || code.isBlank()) continue;

            Professeur.Creneau creneau = new Professeur.Creneau();
            creneau.setCodeCours(code.trim());
            creneau.setNomMatiere(params.get("creneau_matiere_" + index));

            String classe = params.get("creneau_classe_" + index);
            if (classe == null || classe.isBlank()) {
                classe = getClasseMatiere(code.trim(), institutionId);
            }
            creneau.setClasse(classe);
            creneau.setJourSemaine(params.get("creneau_jour_" + index));
            creneau.setHeureDebut(params.get("creneau_debut_" + index));
            creneau.setHeureFin(params.get("creneau_fin_" + index));

            String coeffStr = params.get("creneau_coefficient_" + index);
            try {
                creneau.setCoefficient(coeffStr != null && !coeffStr.isBlank()
                        ? Double.valueOf(coeffStr) : 1.0);
            } catch (NumberFormatException e) {
                creneau.setCoefficient(1.0);
            }

            String annee = params.get("creneau_annee_" + index);
            if (annee == null || annee.isBlank()) {
                LOGGER.warning(() -> "⚠️ Créneau " + index + " : année académique manquante");
                redirect(exchange,
                        "/admin/professeurs?error=Année+académique+manquante+pour+un+créneau");
                return;
            }
            creneau.setAnneeAcademique(annee.trim());

            String periode = params.get("creneau_periode_" + index);
            if (periode == null || periode.isBlank()) {
                LOGGER.warning(() -> "⚠️ Créneau " + index + " : période manquante");
                redirect(exchange,
                        "/admin/professeurs?error=Période+manquante+pour+un+créneau");
                return;
            }
            creneau.setPeriode(periode.trim());

            creneau.setDateAffectation(new Date());
            creneaux.add(creneau);
        }
        prof.setCreneaux(creneaux);
        LOGGER.log(Level.INFO, "📊 Total créneaux récupérés: {0}", creneaux.size());

        // ========== 10. Enregistrement ==========
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ProfesseurManager manager = new ProfesseurManager(conn);

            // Vérifications existant / inexistant
            Professeur existant = manager.obtenir(numeroId.trim(), institutionId);

            if ("update".equals(action)) {
                if (existant == null) {
                    redirect(exchange, "/admin/professeurs?error=Professeur inexistant");
                    return;
                }
            } else if ("add".equals(action)) {
                if (existant != null) {
                    redirect(exchange,
                            "/admin/professeurs?error=Identifiant déjà existant");
                    return;
                }
            }

            // ✅ Sauvegarde : la méthode crée automatiquement l'accès professeur
            boolean success = manager.sauvegarder(prof);
            if (!success) {
                throw new SQLException("Échec de l'enregistrement du professeur.");
            }

            // ✅ Vérification finale : l'accès a bien été créé
            verifierAccesProfesseur(conn, numeroId.trim(), institutionId);

            LOGGER.log(Level.INFO,
                    "✅ Professeur enregistré: {0} avec {1} créneaux",
                    new Object[]{numeroId, creneaux.size()});
            redirect(exchange, "/admin/professeurs?success=Opération réussie");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur SQL: {0}", e.getMessage());
            redirect(exchange, "/admin/professeurs?error=" + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur inattendue: {0}", e.getMessage());
            redirect(exchange, "/admin/professeurs?error=" + e.getMessage());
        }
    }

    // ============================================================
    // ✅ VÉRIFICATION DE L'ACCÈS PROFESSEUR
    // ============================================================
    /**
     * Vérifie que la ligne `acces_professeur` existe.
     * Si elle manque, la crée en secours.
     */
    private void verifierAccesProfesseur(Connection conn, String numeroId,
                                           String institutionId) {
        if (conn == null || numeroId == null || numeroId.isBlank()) return;

        String checkSql = "SELECT 1 FROM " + MigrationManager.TABLE_ACCES_PROFESSEUR
                + " WHERE institution_id = ? AND numero_identifiant = ?";

        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, institutionId);
            check.setString(2, numeroId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    LOGGER.fine(() -> "✅ Acces professeur confirmé : " + numeroId);
                    return;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur vérif acces_professeur", e);
        }

        // Fallback : créer manuellement
        String insertSql = "INSERT INTO " + MigrationManager.TABLE_ACCES_PROFESSEUR
                + " (institution_id, numero_identifiant, profil, cours, notes, edt, "
                + "absences, documents, messages, parametres, programmes, source) "
                + "VALUES (?, ?, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 'LOCAL')";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, institutionId);
            ps.setString(2, numeroId);
            ps.executeUpdate();
            LOGGER.info(() -> "✅ Acces professeur créé en fallback : " + numeroId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, () -> "⚠️ Impossible de créer l'accès professeur en fallback : "
                    + e.getMessage());
        }
    }

    // ============================================================
    // API GÉNÉRATION IDENTIFIANT
    // ============================================================
    private void handleGenererId(HttpExchange exchange, String institutionId)
            throws IOException {
        try {
            // ✅ Utilisation de genererIDProfesseur (au lieu de genererIDIndividu)
            String newId = GenerateurCle.genererIDProfesseur(
                    "PROF",
                    "DEF",
                    new java.util.Date(),
                    "G",
                    institutionId
            );
            LOGGER.log(Level.INFO, "🔑 Identifiant professeur généré via API: {0}", newId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", newId);
            sendJson(exchange, response);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur génération ID: {0}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            sendJson(exchange, response);
        }
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
    // SESSION
    // ============================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
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
                String[] parts = cookie.trim().split("=", 2);
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
    // PHOTO
    // ============================================================
    private String traiterEtSauvegarderPhoto(String base64Image, String numeroId) {
        if (base64Image == null || base64Image.isBlank()) return null;

        try {
            String[] parts = base64Image.split(",");
            String imageString = parts.length > 1 ? parts[1] : parts[0];
            byte[] imageBytes = Base64.getDecoder().decode(imageString);
            String extension = detectImageFormat(imageBytes);

            String uploadDir = "data/uploads/photos_professeurs/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String fileName = numeroId + "_" + System.currentTimeMillis() + "." + extension;
            Path path = Paths.get(uploadDir + fileName);
            Files.write(path, imageBytes);

            return "uploads/photos_professeurs/" + fileName;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "❌ Erreur lors de la sauvegarde de la photo: {0}", e.getMessage());
            return null;
        }
    }

    private String detectImageFormat(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 8) return "jpg";

        // JPEG
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8) return "jpg";

        // PNG
        if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50
                && imageBytes[2] == (byte) 0x4E && imageBytes[3] == (byte) 0x47) return "png";

        // GIF
        if (imageBytes[0] == (byte) 0x47 && imageBytes[1] == (byte) 0x49
                && imageBytes[2] == (byte) 0x46 && imageBytes[3] == (byte) 0x38) return "gif";

        // WEBP
        if (imageBytes.length >= 12
                && imageBytes[0] == (byte) 0x52 && imageBytes[1] == (byte) 0x49
                && imageBytes[2] == (byte) 0x46 && imageBytes[3] == (byte) 0x46
                && imageBytes[8] == (byte) 0x57 && imageBytes[9] == (byte) 0x45
                && imageBytes[10] == (byte) 0x42 && imageBytes[11] == (byte) 0x50) return "webp";

        // BMP
        if (imageBytes[0] == (byte) 0x42 && imageBytes[1] == (byte) 0x4D) return "bmp";

        // SVG
        try {
            String start = new String(imageBytes, 0, Math.min(imageBytes.length, 100),
                    StandardCharsets.UTF_8);
            if (start.contains("<svg") || start.contains("<?xml")) return "svg";
        } catch (Exception ignored) {}

        return "jpg";
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private String getClasseMatiere(String codeCours, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT classe_matiere FROM matieres "
                    + "WHERE code_cours = ? AND institution_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codeCours);
                ps.setString(2, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String classe = rs.getString("classe_matiere");
                        if (classe != null && !classe.isBlank()) {
                            return classe;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                    "Impossible de récupérer la classe pour " + codeCours, e);
        }
        return "";
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data)
            throws IOException {
        String json = mapToJson(data);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (count > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String string) {
                sb.append("\"").append(escapeJson(string)).append("\"");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("null");
            }
            count++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        try {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx),
                            StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1),
                            StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur parsing: {0}", e.getMessage());
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html)
            throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }
}