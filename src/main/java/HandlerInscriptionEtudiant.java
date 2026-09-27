import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerInscriptionEtudiant implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerInscriptionEtudiant.class.getName());
    private static final Gson GSON = new Gson();
    private static final String UPLOAD_DIR = "data/uploads/photos/";

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

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "🔵 [HandlerInscriptionEtudiant] " + method + " " + path);

        try {
            if (path.startsWith("/inscription/etudiant/api/")) {
                handleApi(exchange);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerInscriptionEtudiant", e);
            String html = UIInscriptionEtudiant.render("Erreur interne: " + e.getMessage(),
                    null, null, null, null, null, null, null, null);
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            InstitutionData institutionData = new InstitutionData(conn);
            List<Institution> institutions = institutionData.readAll();
            LOGGER.info(() -> "🏛️ Institutions chargées: " + institutions.size());

            String institutionId = institutions.isEmpty() ? "" : institutions.get(0).getInstitutionId();
            LOGGER.info(() -> "🔑 Institution ID par défaut: " + institutionId);

            ParametreInscriptionManager paramManager = new ParametreInscriptionManager();
            List<String[]> anneesPeriodes = paramManager.getAnneesPeriodes(institutionId);
            Set<String> anneesSet = new HashSet<>();
            Set<String> periodesSet = new HashSet<>();
            for (String[] ap : anneesPeriodes) {
                anneesSet.add(ap[0]);
                periodesSet.add(ap[1]);
            }
            List<String> annees = new ArrayList<>(anneesSet);
            Collections.sort(annees, Collections.reverseOrder());
            List<String> periodes = new ArrayList<>(periodesSet);
            Collections.sort(periodes);

            String anneeParDefaut = annees.isEmpty() ? "" : annees.get(0);
            String periodeParDefaut = periodes.isEmpty() ? "" : periodes.get(0);

            List<Classe> classes = getClassesAutorisees(institutionId, anneeParDefaut, periodeParDefaut);

            List<Options> options = new ArrayList<>();
            if (!classes.isEmpty()) {
                Classe premiereClasse = classes.get(0);
                String promotion = premiereClasse.getPromotion();
                options = getOptions(institutionId, anneeParDefaut, periodeParDefaut,
                        premiereClasse.getNomClasse(), promotion);
            }

            String html = UIInscriptionEtudiant.render(
                    null,
                    institutions,
                    annees,
                    periodes,
                    classes,
                    institutionId,
                    anneeParDefaut,
                    periodeParDefaut,
                    options
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UIInscriptionEtudiant.render("Erreur base de données.",
                    null, null, null, null, null, null, null, null);
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> params;
        byte[] photoData = null;
        String photoFilename = null;

        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                sendErrorHtml(exchange, "Boundary manquant dans Content-Type");
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            MultipartResult result = parseMultipart(body, boundary);
            params = result.fields;
            photoData = result.photoData;
            photoFilename = result.photoFilename;
        } else {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            params = parseFormData(body);
        }

        // ===== LOG DE TOUS LES PARAMÈTRES =====
        LOGGER.info("🔍 === PARAMÈTRES REÇUS ===");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            LOGGER.info(() -> "   " + entry.getKey() + " = '" + entry.getValue() + "'");
        }

        // ===== EXTRACTION DE TOUS LES CHAMPS =====
        String institutionId = params.get("institutionId");
        String annee = params.get("annee");
        String periode = params.get("periode");
        String classe = params.get("classe");
        String nom = params.get("nom");
        String prenom = params.get("prenom");
        String sexe = params.get("sexe");
        String dateNaissanceStr = params.get("dateNaissance");
        String telephone = params.get("telephone");
        String email = params.get("email");
        String adresse = params.get("adresse");
        String departementNaissance = params.get("departementNaissance");
        String communeNaissance = params.get("communeNaissance");
        String departementResidence = params.get("departementResidence");
        String communeResidence = params.get("communeResidence");
        String nomPere = params.get("nomPere");
        String prenomPere = params.get("prenomPere");
        String nomMere = params.get("nomMere");
        String prenomMere = params.get("prenomMere");
        String telephoneParents = params.get("telephoneParents");
        String residenceParents = params.get("residenceParents");
        String lienParente = params.get("lienParente");
        String typeResponsable = params.get("typeResponsable");
        String nomResponsable = params.get("nomResponsable");
        String prenomResponsable = params.get("prenomResponsable");
        String option = params.get("option");
        String groupeSanguin = params.get("groupeSanguin");
        String observations = params.get("observations");
        String matricule = params.get("matricule");
        String ninu = params.get("ninu");

        // ===== LOG SPÉCIFIQUE POUR NINU =====
        LOGGER.info(() -> "🔍 NINU brut reçu : '" + ninu + "'");

        // ===== VALIDATION DU NINU - OBLIGATOIRE =====
        if (ninu == null || ninu.isBlank()) {
            sendErrorHtml(exchange, "Le NINU (Numéro d'Identification Nationale) est obligatoire. Veuillez le saisir.");
            return;
        }

        String finalNinu = ninu.trim().toUpperCase();
        LOGGER.info(() -> "🔍 NINU après nettoyage : '" + finalNinu + "'");

        if (finalNinu.length() < 5) {
            sendErrorHtml(exchange, "Le NINU doit contenir au moins 5 caractères.");
            return;
        }

        LOGGER.info(() -> "🔍 OPTION reçue : '" + option + "'");
        LOGGER.info(() -> "🔍 OBSERVATIONS reçues : '" + observations + "'");

        // ===== VALIDATION DES CHAMPS OBLIGATOIRES =====
        List<String> champsManquants = new ArrayList<>();
        if (nom == null || nom.isBlank()) champsManquants.add("Nom");
        if (prenom == null || prenom.isBlank()) champsManquants.add("Prénom");
        if (sexe == null || sexe.isBlank()) champsManquants.add("Sexe");
        if (institutionId == null || institutionId.isBlank()) champsManquants.add("Institution");
        if (annee == null || annee.isBlank()) champsManquants.add("Année académique");
        if (periode == null || periode.isBlank()) champsManquants.add("Période");
        if (classe == null || classe.isBlank()) champsManquants.add("Classe");
        if (dateNaissanceStr == null || dateNaissanceStr.isBlank()) champsManquants.add("Date de naissance");
        if (telephone == null || telephone.isBlank()) champsManquants.add("Téléphone");
        if (email == null || email.isBlank()) champsManquants.add("Email");
        if (adresse == null || adresse.isBlank()) champsManquants.add("Adresse");
        if (departementNaissance == null || departementNaissance.isBlank()) champsManquants.add("Département de naissance");
        if (communeNaissance == null || communeNaissance.isBlank()) champsManquants.add("Commune de naissance");
        if (departementResidence == null || departementResidence.isBlank()) champsManquants.add("Département de résidence");
        if (communeResidence == null || communeResidence.isBlank()) champsManquants.add("Commune de résidence");
        if (nomPere == null || nomPere.isBlank()) champsManquants.add("Nom du père");
        if (prenomPere == null || prenomPere.isBlank()) champsManquants.add("Prénom du père");
        if (nomMere == null || nomMere.isBlank()) champsManquants.add("Nom de la mère");
        if (prenomMere == null || prenomMere.isBlank()) champsManquants.add("Prénom de la mère");
        if (telephoneParents == null || telephoneParents.isBlank()) champsManquants.add("Téléphone des parents");
        if (residenceParents == null || residenceParents.isBlank()) champsManquants.add("Résidence des parents");
        if (lienParente == null || lienParente.isBlank()) champsManquants.add("Lien de parenté");
        if (typeResponsable == null || typeResponsable.isBlank()) champsManquants.add("Type de responsable");
        if (nomResponsable == null || nomResponsable.isBlank()) champsManquants.add("Nom du responsable");
        if (prenomResponsable == null || prenomResponsable.isBlank()) champsManquants.add("Prénom du responsable");

        if (!champsManquants.isEmpty()) {
            String errorMsg = "Les champs suivants sont obligatoires : " + String.join(", ", champsManquants);
            sendErrorHtml(exchange, errorMsg);
            return;
        }

        if (photoData == null || photoData.length == 0) {
            sendErrorHtml(exchange, "La photo d'identité est obligatoire.");
            return;
        }

        // ===== CONVERSION DATE =====
        java.sql.Date dateNaissance = null;
        try {
            if (dateNaissanceStr != null && !dateNaissanceStr.isBlank()) {
                dateNaissance = java.sql.Date.valueOf(dateNaissanceStr);
            }
        } catch (IllegalArgumentException e) {
            sendErrorHtml(exchange, "Date de naissance invalide.");
            return;
        }

        Connection conn = null;
        boolean autoCommitOriginal;
        try {
            conn = DatabaseManager.getInstance().getConnection();

            // 1. Vérifier que l'inscription est ouverte
            ParametreInscriptionManager paramManager = new ParametreInscriptionManager();
            List<ParametreInscription> parametres = paramManager.getByInstitutionAnneePeriode(institutionId, annee, periode);
            boolean classeAutorisee = parametres.stream()
                    .anyMatch(p -> p.getClasse().equals(classe) && p.isActif());
            if (!classeAutorisee) {
                sendErrorHtml(exchange, "Les inscriptions ne sont pas ouvertes pour cette classe, année et période.");
                return;
            }

            // 2. Récupérer la promotion à partir du NOM de la classe
            String promotion = null;
            try {
                ClasseData classeData = new ClasseData(conn);   // ✅ connexion partagée
                List<Classe> toutesClasses = classeData.toutLister(institutionId);
                for (Classe c : toutesClasses) {
                    if (c.getNomClasse() != null && c.getNomClasse().equals(classe)) {
                        promotion = c.getPromotion();
                        break;
                    }
                }
                if (promotion == null) {
                    LOGGER.warning(() -> "⚠️ Promotion non trouvée pour la classe " + classe);
                }
            } catch (SQLException e) {
                LOGGER.warning(() -> "⚠️ Impossible de récupérer la promotion pour la classe "
                        + classe + " : " + e.getMessage());
            }

            // 3. Générer un identifiant unique
            String tempNumeroIdentifiant = GenerateurCle.genererIDIndividu(nom, prenom, dateNaissance, sexe, institutionId);
            EtudiantData etudiantData = new EtudiantData(conn);
            if (etudiantData.exists(tempNumeroIdentifiant)) {
                tempNumeroIdentifiant = tempNumeroIdentifiant + "_" + System.currentTimeMillis();
            }

            // ✅ Copie finale pour les lambdas
            final String numeroIdentifiant = tempNumeroIdentifiant;

            // 4. Matricule (si non fourni, en générer un)
            if (matricule == null || matricule.isBlank()) {
                matricule = genererMatricule(nom, prenom, annee);
            }
            String matriculeUnique = matricule;
            int compteur = 0;
            while (etudiantData.existsByMatricule(matriculeUnique)) {
                compteur++;
                matriculeUnique = matricule + "_" + compteur;
            }
            matricule = matriculeUnique;

            // 5. Sauvegarder la photo
            String photoPath = sauvegarderPhoto(photoData, photoFilename, numeroIdentifiant);

            // 6. Vérifier le NINU unique
            if (etudiantData.existsByNinu(finalNinu, institutionId)) {
                sendErrorHtml(exchange, "Ce NINU (" + finalNinu
                        + ") est déjà utilisé par un autre étudiant. Veuillez saisir un NINU valide.");
                return;
            }

            LOGGER.info(() -> "✅ NINU valide et unique : '" + finalNinu + "'");

            // 7. Gestion des autres valeurs
            String finalOption = (option != null && !option.isBlank()) ? option.trim() : "";
            String finalObservations = (observations != null) ? observations : "";
            String finalGroupeSanguin = (groupeSanguin != null) ? groupeSanguin : "";
            String finalTelephoneParents = (telephoneParents != null) ? telephoneParents : "";
            String finalResidenceParents = (residenceParents != null) ? residenceParents : "";
            String finalLienParente = (lienParente != null) ? lienParente : "";
            String finalTypeResponsable = (typeResponsable != null) ? typeResponsable : "";
            String finalNomResponsable = (nomResponsable != null) ? nomResponsable : "";
            String finalPrenomResponsable = (prenomResponsable != null) ? prenomResponsable : "";

            // 8. Construire l'étudiant
            Etudiant etudiant = new Etudiant();
            etudiant.setNumeroIdentifiantEtudiant(numeroIdentifiant);
            etudiant.setNom(nom != null ? nom.trim() : "");
            etudiant.setPrenom(prenom != null ? prenom.trim() : "");
            etudiant.setSexe(sexe != null ? sexe : "");
            etudiant.setDateNaissance(dateNaissance);
            etudiant.setGroupeSanguin(finalGroupeSanguin);
            etudiant.setTelephone(telephone != null ? telephone : "");
            etudiant.setEmail(email != null ? email : "");
            etudiant.setAdresse(adresse != null ? adresse : "");
            etudiant.setClasse(classe != null ? classe : "");
            etudiant.setAnneeAcademique(annee != null ? annee : "");
            etudiant.setPeriode(periode != null ? periode : "");
            etudiant.setPromotion(promotion != null ? promotion : "");
            etudiant.setMatricule(matricule != null ? matricule : "");
            etudiant.setNinu(finalNinu);
            etudiant.setInstitutionId(institutionId != null ? institutionId : "");
            etudiant.setStatut("ACTIF");
            etudiant.setDateInscription(new java.sql.Date(System.currentTimeMillis()));
            etudiant.setDepartementNaissance(departementNaissance != null ? departementNaissance : "");
            etudiant.setCommuneNaissance(communeNaissance != null ? communeNaissance : "");
            etudiant.setDepartementResidence(departementResidence != null ? departementResidence : "");
            etudiant.setCommuneResidence(communeResidence != null ? communeResidence : "");
            etudiant.setNomPere(nomPere != null ? nomPere : "");
            etudiant.setPrenomPere(prenomPere != null ? prenomPere : "");
            etudiant.setNomMere(nomMere != null ? nomMere : "");
            etudiant.setPrenomMere(prenomMere != null ? prenomMere : "");
            etudiant.setTelephoneParents(finalTelephoneParents);
            etudiant.setResidenceParents(finalResidenceParents);
            etudiant.setLienParente(finalLienParente);
            etudiant.setTypeResponsable(finalTypeResponsable);
            etudiant.setNomResponsable(finalNomResponsable);
            etudiant.setPrenomResponsable(finalPrenomResponsable);
            etudiant.setOption(finalOption);
            etudiant.setPhotoPath(photoPath);
            etudiant.setObservations(finalObservations);
            etudiant.setEmpreintePath("");
            etudiant.setVisagePath("");
            etudiant.setBiometrieActive(false);

            // ============================================================
            // 9. ENREGISTREMENT DANS UNE TRANSACTION + SYNC APRÈS COMMIT
            // ============================================================
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                EtudiantManager manager = new EtudiantManager(etudiantData);
                boolean success = manager.enregistrerEtudiant(etudiant);

                if (success) {
                    conn.commit();
                    LOGGER.info(() -> "✅ Inscription étudiant réussie : " + numeroIdentifiant);

                    // ✅ Sync ASYNCHRONE après commit (non bloquante)
                    declencherSyncAsyncApresCommit();

                    String successMsg = "✅ Inscription réussie !<br>"
                            + "Votre identifiant : <strong>" + numeroIdentifiant + "</strong><br>"
                            + "Votre matricule : <strong>" + matricule + "</strong><br>"
                            + "Votre NINU : <strong>" + finalNinu + "</strong><br>"
                            + "Vous pouvez maintenant vous connecter avec votre identifiant.";
                    String html = UIInscriptionEtudiant.render(successMsg,
                            null, null, null, null, null, null, null, null);
                    ResponseUtil.sendHtml(exchange, 200, html);
                } else {
                    conn.rollback();
                    LOGGER.warning("⚠️ Échec enregistrement, rollback");
                    sendErrorHtml(exchange, "❌ Erreur lors de l'inscription en base de données.");
                }

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback inscription étudiant", e);
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendErrorHtml(exchange, "Erreur base de données: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur", e);
            sendErrorHtml(exchange, "Erreur: " + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    // ==================== API ====================
    private void handleApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQueryParams(query);
        String path = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "🔵 [API] " + path + " avec params: " + params);

        if (path.endsWith("/annees-periodes")) {
            handleAnneesPeriodes(exchange, params);
            return;
        }
        if (path.endsWith("/classes")) {
            handleClasses(exchange, params);
            return;
        }
        if (path.endsWith("/options")) {
            handleOptions(exchange, params);
            return;
        }
        sendJsonError(exchange, "API inconnue");
    }

    private void handleAnneesPeriodes(HttpExchange exchange, Map<String, String> params) throws IOException {
        String institutionId = params.get("institutionId");
        if (institutionId == null || institutionId.isBlank()) {
            sendJsonError(exchange, "Institution manquante");
            return;
        }
        try {
            ParametreInscriptionManager manager = new ParametreInscriptionManager();
            List<String[]> anneesPeriodes = manager.getAnneesPeriodes(institutionId);
            Set<String> anneesSet = new HashSet<>();
            Set<String> periodesSet = new HashSet<>();
            for (String[] ap : anneesPeriodes) {
                anneesSet.add(ap[0]);
                periodesSet.add(ap[1]);
            }
            List<String> annees = new ArrayList<>(anneesSet);
            Collections.sort(annees, Collections.reverseOrder());
            List<String> periodes = new ArrayList<>(periodesSet);
            Collections.sort(periodes);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("annees", annees);
            result.put("periodes", periodes);
            sendJson(exchange, result);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    private void handleClasses(HttpExchange exchange, Map<String, String> params) throws IOException {
        String institutionId = params.get("institutionId");
        String annee = params.get("annee");
        String periode = params.get("periode");
        if (institutionId == null || annee == null || periode == null) {
            sendJsonError(exchange, "Paramètres manquants: institutionId, annee, periode");
            return;
        }
        try {
            List<Classe> classes = getClassesAutorisees(institutionId, annee, periode);
            List<Map<String, String>> result = new ArrayList<>();
            for (Classe c : classes) {
                Map<String, String> map = new HashMap<>();
                map.put("nomClasse", c.getNomClasse());
                map.put("codeClasse", c.getCodeClasse());
                map.put("promotion", c.getPromotion());
                result.add(map);
            }
            Map<String, Object> json = new HashMap<>();
            json.put("success", true);
            json.put("data", result);
            sendJson(exchange, json);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    private void handleOptions(HttpExchange exchange, Map<String, String> params) throws IOException {
        String institutionId = params.get("institutionId");
        String annee = params.get("annee");
        String periode = params.get("periode");
        String classe = params.get("classe");
        String promotion = params.get("promotion");

        if (institutionId == null || annee == null || periode == null || classe == null) {
            sendJsonError(exchange, "Paramètres manquants: institutionId, annee, periode, classe");
            return;
        }
        try {
            List<Options> options = getOptions(institutionId, annee, periode, classe, promotion);
            List<Map<String, String>> result = new ArrayList<>();
            for (Options opt : options) {
                Map<String, String> map = new HashMap<>();
                map.put("option", opt.getOption());
                result.add(map);
            }
            Map<String, Object> json = new HashMap<>();
            json.put("success", true);
            json.put("data", result);
            sendJson(exchange, json);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            sendJsonError(exchange, "Erreur base de données");
        }
    }

    // ==================== SYNCHRONISATION ====================
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
        } catch (SQLException ignored) {}
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private List<Classe> getClassesAutorisees(String institutionId, String annee, String periode) throws SQLException {
        ParametreInscriptionManager manager = new ParametreInscriptionManager();
        List<ParametreInscription> params = manager.getByInstitutionAnneePeriode(institutionId, annee, periode);
        List<String> classesAutorisees = new ArrayList<>();
        for (ParametreInscription p : params) {
            if (p.isActif()) {
                classesAutorisees.add(p.getClasse());
            }
        }
        if (classesAutorisees.isEmpty()) return new ArrayList<>();
        ClasseData classeData = new ClasseData();
        List<Classe> toutesClasses = classeData.toutLister(institutionId);
        List<Classe> result = new ArrayList<>();
        for (Classe c : toutesClasses) {
            if (classesAutorisees.contains(c.getNomClasse())) {
                result.add(c);
            }
        }
        return result;
    }

      private List<Options> getOptions(String institutionId, String annee, String periode,
                                       String classe, String promotion) throws SQLException {
        List<Options> filtered = new ArrayList<>();

        // ✅ Filtrage SQL direct (au lieu de readAll + filtrage Java)
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM options WHERE institution_id = ? AND classe = ? "
            + "AND annee_academique = ? AND periode = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(classe);
        params.add(annee);
        params.add(periode);

        if (promotion != null && !promotion.isBlank()) {
            sql.append(" AND (promotion = ? OR promotion IS NULL)");
            params.add(promotion);
        }

        sql.append(" ORDER BY `option` ASC");

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Options opt = new Options();
                        opt.setId(rs.getInt("id"));
                        opt.setInstitutionId(rs.getString("institution_id"));
                        opt.setOption(rs.getString("option"));
                        opt.setClasse(rs.getString("classe"));
                        opt.setAnneeAcademique(rs.getString("annee_academique"));
                        opt.setPeriode(rs.getString("periode"));
                        opt.setPromotion(rs.getString("promotion"));
                        filtered.add(opt);
                    }
                }
            }
        } finally {
            if (conn != null) {
                try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
            }
        }

        LOGGER.info(() -> "🔍 Options trouvées pour " + classe
                + " (annee=" + annee + ", periode=" + periode + ") : " + filtered.size());
        return filtered;
    }
    
    private String genererMatricule(String nom, String prenom, String annee) {
        String nomPart = nom.length() >= 3 ? nom.substring(0, 3).toUpperCase() : nom.toUpperCase();
        String prenomPart = prenom.length() >= 3 ? prenom.substring(0, 3).toUpperCase() : prenom.toUpperCase();
        String anneePart = annee.replace("-", "").substring(0, 4);
        return "M-" + nomPart + prenomPart + anneePart + System.currentTimeMillis() % 10000;
    }

    private String sauvegarderPhoto(byte[] data, String filename, String etudiantId) throws IOException {
        if (filename == null || filename.isBlank()) {
            filename = "photo.jpg";
        }
        String ext = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = filename.substring(dotIndex);
        }
        String newFilename = etudiantId + "_" + System.currentTimeMillis() + ext;
        Path target = Paths.get(UPLOAD_DIR, newFilename);
        Files.createDirectories(target.getParent());
        Files.write(target, data);

        String storedPath = "/data/uploads/photos/" + newFilename;
        LOGGER.info(() -> "📸 Photo sauvegardée: " + target.toAbsolutePath()
                + " (stockée: " + storedPath + ")");
        return storedPath;
    }

    private String extractBoundary(String contentType) {
        Pattern pattern = Pattern.compile("boundary=([^;]+)");
        Matcher matcher = pattern.matcher(contentType);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private MultipartResult parseMultipart(byte[] body, String boundary) {
        Map<String, String> fields = new HashMap<>();
        byte[] photoData = null;
        String photoFilename = null;

        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] endBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.ISO_8859_1);

        int start = 0;
        while (start < body.length) {
            int partStart = indexOf(body, boundaryBytes, start);
            if (partStart == -1) break;
            partStart += boundaryBytes.length;
            if (body[partStart] == '\r' && body[partStart + 1] == '\n') {
                partStart += 2;
            } else if (body[partStart] == '\n') {
                partStart += 1;
            }

            int partEnd = indexOf(body, boundaryBytes, partStart);
            if (partEnd == -1) partEnd = body.length;
            if (partEnd < body.length && startsWith(body, endBoundaryBytes, partEnd)) break;

            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), partStart);
            if (headerEnd == -1) {
                start = partEnd;
                continue;
            }

            byte[] headerBytes = new byte[headerEnd - partStart];
            System.arraycopy(body, partStart, headerBytes, 0, headerBytes.length);
            String headers = new String(headerBytes, StandardCharsets.ISO_8859_1);

            int contentStart = headerEnd + 4;
            int contentEnd = partEnd;
            if (contentEnd > contentStart + 1 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            } else if (contentEnd > contentStart && body[contentEnd - 1] == '\n') {
                contentEnd -= 1;
            }

            String name = null;
            String filename = null;
            String[] headerLines = headers.split("\r\n");
            for (String line : headerLines) {
                if (line.startsWith("Content-Disposition:")) {
                    Pattern pName = Pattern.compile("name=\"([^\"]+)\"");
                    Matcher mName = pName.matcher(line);
                    if (mName.find()) name = mName.group(1);
                    Pattern pFilename = Pattern.compile("filename=\"([^\"]+)\"");
                    Matcher mFilename = pFilename.matcher(line);
                    if (mFilename.find()) filename = mFilename.group(1);
                }
            }

            if (name != null) {
                if (filename != null) {
                    photoFilename = filename;
                    int length = contentEnd - contentStart;
                    if (length > 0) {
                        photoData = new byte[length];
                        System.arraycopy(body, contentStart, photoData, 0, length);
                    }
                } else {
                    byte[] valueBytes = new byte[contentEnd - contentStart];
                    System.arraycopy(body, contentStart, valueBytes, 0, valueBytes.length);
                    String value = new String(valueBytes, StandardCharsets.UTF_8);
                    fields.put(name, value.trim());
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
        Map<String, String> fields;
        byte[] photoData;
        String photoFilename;

        MultipartResult(Map<String, String> fields, byte[] photoData, String photoFilename) {
            this.fields = fields;
            this.photoData = photoData;
            this.photoFilename = photoFilename;
        }
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    private void sendErrorHtml(HttpExchange exchange, String errorMsg) throws IOException {
        String html = UIInscriptionEtudiant.render(errorMsg,
                null, null, null, null, null, null, null, null);
        ResponseUtil.sendHtml(exchange, 400, html);
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> data) throws IOException {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonError(HttpExchange exchange, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, error);
    }
}