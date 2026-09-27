import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerExamenProfesseur implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerExamenProfesseur.class.getName());

    // ============================================================
    // CONSTANTES
    // ============================================================

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String UPLOAD_DIR = "uploads/examens/";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;   // 20 Mo

    /** ✅ Extensions autorisées (whitelist). */
    private static final List<String> EXTENSIONS_AUTORISEES = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".ppt", ".pptx", ".txt", ".png", ".jpg", ".jpeg"
    );

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // ① SESSION
            HttpSession session = resolveSession(exchange);
            if (session == null) {
                LOGGER.fine("⛔ Session invalide → /connexion");
                redirect(exchange, "/connexion");
                return;
            }

            String professeurId = readAttributeAsString(session, "userId");
            if (professeurId == null) {
                professeurId = readAttributeAsString(session, "username");
            }
            String institutionId = readAttributeAsString(session, "institutionId");
            if (institutionId == null) {
                institutionId = readAttributeAsString(session, "institution_id");
            }

            if (professeurId == null || institutionId == null) {
                LOGGER.fine("⛔ Session incomplète → /connexion");
                redirect(exchange, "/connexion");
                return;
            }

            // ② VÉRIFICATION D'APPARTENANCE À L'INSTITUTION
            if (!professeurAppartientAInstitution(professeurId, institutionId)) {
                LOGGER.warning("⛔ Prof " + professeurId + " ∉ " + institutionId);
                ResponseUtil.sendError(exchange, 403,
                        "Vous n'appartenez pas à cette institution.");
                return;
            }

            // ③ ROUTAGE
            if (path.startsWith("/professeur/examens/download/")) {
                handleDownload(exchange, institutionId, professeurId);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, professeurId, institutionId);
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, professeurId, institutionId);
                return;
            }

            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur I/O HandlerExamenProfesseur", e);
            safeSendError(exchange, 500, "Erreur interne : " + e.getMessage());
        }
    }

    // ============================================================
    // TÉLÉCHARGEMENT (uniquement ses propres examens)
    // ============================================================
    private void handleDownload(HttpExchange exchange, String institutionId,
                                  String professeurId) throws IOException {

        String path = exchange.getRequestURI().getPath();
        String id = path.substring(path.lastIndexOf("/") + 1);

        if (id.isBlank()) {
            ResponseUtil.sendError(exchange, 400, "ID manquant");
            return;
        }

        LOGGER.info("📥 Téléchargement examen : " + id + " par " + professeurId);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ExamenManager manager = new ExamenManager(conn);

            // ✅ Filtre STRICT : id + institution + professeur
            Examen examen = manager.getExamenByIdForProfesseur(id, institutionId, professeurId);

            if (examen == null) {
                LOGGER.warning("❌ Examen introuvable : " + id
                        + " (prof=" + professeurId + ")");
                ResponseUtil.sendError(exchange, 404, "Examen introuvable ou non autorisé.");
                return;
            }

            if (examen.getFichierChemin() == null || examen.getFichierChemin().isBlank()) {
                LOGGER.warning("❌ Aucun fichier pour l'examen : " + id);
                ResponseUtil.sendError(exchange, 404, "Aucun fichier associé à cet examen.");
                return;
            }

            // ✅ Anti path traversal
            File file = localiserFichierSecurise(examen.getFichierChemin());
            if (file == null || !file.exists() || !file.isFile()) {
                LOGGER.warning("❌ Fichier physique introuvable : "
                        + examen.getFichierChemin());
                ResponseUtil.sendError(exchange, 404,
                        "Le fichier est introuvable sur le serveur.");
                return;
            }

            // ✅ Téléchargement
            byte[] data = Files.readAllBytes(file.toPath());

            String nomFichier = (examen.getFichierNom() != null
                    && !examen.getFichierNom().isBlank())
                    ? examen.getFichierNom().replace("\"", "")
                    : file.getName();

            String mimeType = (examen.getFichierType() != null
                    && !examen.getFichierType().isBlank())
                    ? examen.getFichierType()
                    : getMimeType(nomFichier);

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + nomFichier + "\"");
            exchange.sendResponseHeaders(200, data.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
                os.flush();
            }

            LOGGER.info("✅ Téléchargement réussi : " + nomFichier);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL téléchargement", e);
            ResponseUtil.sendError(exchange, 500, "Erreur serveur");
        }
    }

    // ============================================================
    // GET — LISTE / ÉDITION / SUPPRESSION / AJOUT
    // ============================================================
    private void handleGet(HttpExchange exchange, String professeurId,
                            String institutionId) throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String codeCours = params.getOrDefault("codeCours", "");
        String classe = params.getOrDefault("classe", "");
        String periode = params.getOrDefault("periode", "");
        String annee = params.getOrDefault("annee", "");
        String action = params.getOrDefault("action", "");
        String idStr = params.getOrDefault("id", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ExamenManager manager = new ExamenManager(conn);

            // ============================================================
            // SUPPRESSION
            // ============================================================
            if ("delete".equals(action) && !idStr.isBlank()) {
                Examen examen = manager.getExamenByIdForProfesseur(
                        idStr, institutionId, professeurId);

                if (examen == null) {
                    LOGGER.warning("⛔ Suppression refusée : examen " + idStr
                            + " ∉ prof " + professeurId);
                    handleGetWithMessage(exchange, professeurId, institutionId,
                            "error", "Examen introuvable ou non autorisé.");
                    return;
                }

                boolean deleted = manager.supprimerExamen(idStr, institutionId, professeurId);

                // Supprimer le fichier physique seulement si le delete BD a réussi
                if (deleted && examen.getFichierChemin() != null) {
                    File f = localiserFichierSecurise(examen.getFichierChemin());
                    if (f != null && f.exists() && f.delete()) {
                        LOGGER.fine("🗑️ Fichier supprimé : " + examen.getFichierChemin());
                    }
                }

                String msg = deleted
                        ? "Examen supprimé avec succès."
                        : "Erreur lors de la suppression.";
                handleGetWithMessage(exchange, professeurId, institutionId,
                        deleted ? "success" : "error", msg);
                return;
            }

            // ============================================================
            // ÉDITION
            // ============================================================
            if ("edit".equals(action) && !idStr.isBlank()) {
                Examen examen = manager.getExamenByIdForProfesseur(
                        idStr, institutionId, professeurId);

                if (examen == null) {
                    String html = UIExamenProfesseur.renderError(
                            "Examen introuvable ou non autorisé.");
                    ResponseUtil.sendHtml(exchange, 404, html);
                    return;
                }
                renderFormSafely(exchange, conn, professeurId, institutionId,
                        examen, null, null);
                return;
            }

            // ============================================================
            // AJOUT
            // ============================================================
            if ("add".equals(action)) {
                renderFormSafely(exchange, conn, professeurId, institutionId,
                        null, null, null);
                return;
            }

            // ============================================================
            // LISTE
            // ============================================================
            List<Examen> examens;
            if (!codeCours.isBlank() || !classe.isBlank()
                    || !periode.isBlank() || !annee.isBlank()) {
                examens = manager.getExamensByProfesseurFiltres(
                        professeurId, institutionId,
                        codeCours, classe, periode, annee, "");
            } else {
                examens = manager.getExamensByProfesseur(professeurId, institutionId);
            }

            enrichirNomsMatieres(examens, institutionId);

            // ✅ Listes filtrées
            List<AnneeAcademique> annees = chargerAnneesPourProfesseur(
                    conn, professeurId, institutionId);
            List<Periode> periodes = chargerPeriodes(conn, institutionId, annee);
            List<Classe> classes = chargerClassesPourProfesseur(
                    conn, professeurId, institutionId);
            List<Professeur.Creneau> creneaux = new ProfesseurData(conn)
                    .getCreneauxParProfesseur(professeurId, institutionId);

            String html = UIExamenProfesseur.renderListe(professeurId, creneaux, annees,
                    periodes, classes, examens, codeCours, classe, periode, annee,
                    institutionId, null, null);
            ResponseUtil.sendHtml(exchange, 200, html);

            LOGGER.fine(() -> "✅ " + examens.size() + " examen(s) pour " + professeurId);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur GET", e);
            String html = UIExamenProfesseur.renderError("Erreur : " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ============================================================
    // ✅ CHARGEMENT DES LISTES FILTRÉES
    // ============================================================

    /**
     * ✅ Années académiques filtrées par les créneaux du professeur.
     */
    private List<AnneeAcademique> chargerAnneesPourProfesseur(Connection conn,
                                                                String professeurId,
                                                                String institutionId)
            throws SQLException {

        AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
        List<AnneeAcademique> toutes = anneeData.listByInstitution(institutionId);

        List<Professeur.Creneau> creneaux = new ProfesseurData(conn)
                .getCreneauxParProfesseur(professeurId, institutionId);

        if (creneaux == null || creneaux.isEmpty()) {
            return toutes;
        }

        Set<String> anneesDuProf = new HashSet<>();
        for (Professeur.Creneau c : creneaux) {
            if (c.getAnneeAcademique() != null && !c.getAnneeAcademique().isBlank()) {
                anneesDuProf.add(c.getAnneeAcademique());
            }
        }

        if (anneesDuProf.isEmpty()) return toutes;

        List<AnneeAcademique> filtrees = new ArrayList<>();
        for (AnneeAcademique a : toutes) {
            if (anneesDuProf.contains(a.getAnneeAcademique())) {
                filtrees.add(a);
            }
        }
        return filtrees;
    }

    /**
     * ✅ Périodes filtrées par année académique.
     */
    private List<Periode> chargerPeriodes(Connection conn, String institutionId,
                                            String annee) throws SQLException {
        PeriodeData periodeData = new PeriodeData(conn);
        if (annee != null && !annee.isBlank()) {
            return periodeData.listByInstitutionAndAnnee(institutionId, annee);
        }
        return periodeData.listByInstitution(institutionId);
    }

    /**
     * ✅ Classes filtrées par les créneaux du professeur.
     */
    private List<Classe> chargerClassesPourProfesseur(Connection conn,
                                                        String professeurId,
                                                        String institutionId)
            throws SQLException {

        List<Professeur.Creneau> creneaux = new ProfesseurData(conn)
                .getCreneauxParProfesseur(professeurId, institutionId);

        Set<String> nomsClasses = new LinkedHashSet<>();
        for (Professeur.Creneau c : creneaux) {
            if (c.getClasse() != null && !c.getClasse().isBlank()) {
                nomsClasses.add(c.getClasse());
            }
        }

        List<Classe> toutes = new ClasseData().toutLister(institutionId);
        List<Classe> filtrees = new ArrayList<>();
        for (Classe c : toutes) {
            if (nomsClasses.contains(c.getNomClasse())) {
                filtrees.add(c);
            }
        }
        return filtrees;
    }

    // ============================================================
    // RENDU FORMULAIRE
    // ============================================================
    private void renderFormSafely(HttpExchange exchange, Connection conn,
                                   String professeurId, String institutionId,
                                   Examen examen, String msgType, String msg)
            throws SQLException, IOException {

        String anneeCourante = (examen != null) ? examen.getAnneeAcademique() : null;

        List<AnneeAcademique> annees = chargerAnneesPourProfesseur(
                conn, professeurId, institutionId);
        List<Periode> periodes = chargerPeriodes(conn, institutionId, anneeCourante);
        List<Classe> classes = chargerClassesPourProfesseur(
                conn, professeurId, institutionId);
        List<Professeur.Creneau> creneaux = new ProfesseurData(conn)
                .getCreneauxParProfesseur(professeurId, institutionId);

        String html = UIExamenProfesseur.renderForm(professeurId, creneaux, annees,
                periodes, classes, examen, institutionId, msgType, msg);
        ResponseUtil.sendHtml(exchange, 200, html);
    }

    private void handleGetWithMessage(HttpExchange exchange, String professeurId,
                                       String institutionId,
                                       String msgType, String msg)
            throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ExamenManager manager = new ExamenManager(conn);
            List<Examen> examens = manager.getExamensByProfesseur(professeurId, institutionId);
            enrichirNomsMatieres(examens, institutionId);

            List<AnneeAcademique> annees = chargerAnneesPourProfesseur(
                    conn, professeurId, institutionId);
            List<Periode> periodes = chargerPeriodes(conn, institutionId, null);
            List<Classe> classes = chargerClassesPourProfesseur(
                    conn, professeurId, institutionId);
            List<Professeur.Creneau> creneaux = new ProfesseurData(conn)
                    .getCreneauxParProfesseur(professeurId, institutionId);

            String html = UIExamenProfesseur.renderListe(professeurId, creneaux, annees,
                    periodes, classes, examens, "", "", "", "",
                    institutionId, msgType, msg);
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur handleGetWithMessage", e);
            String html = UIExamenProfesseur.renderError("Erreur : " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    private void enrichirNomsMatieres(List<Examen> examens, String institutionId) {
        if (examens == null || examens.isEmpty()) return;
        try {
            MatiereData matiereData = new MatiereData(institutionId);
            List<Matiere> matieres = matiereData.readAll();

            Map<String, String> nomsParCode = new HashMap<>();
            for (Matiere m : matieres) {
                if (m.getCodeCours() != null) {
                    nomsParCode.put(m.getCodeCours(), m.getNomMatiere());
                }
            }
            for (Examen e : examens) {
                String nom = nomsParCode.get(e.getCodeCours());
                if (nom != null) e.setNomMatiere(nom);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible d'enrichir les noms", e);
        }
    }

    // ============================================================
    // POST
    // ============================================================
    private void handlePost(HttpExchange exchange, String professeurId,
                              String institutionId) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            handleMultipart(exchange, professeurId, institutionId);
        } else {
            handleFormData(exchange, professeurId, institutionId);
        }
    }

    // ============================================================
    // MULTIPART
    // ============================================================
    private void handleMultipart(HttpExchange exchange, String professeurId,
                                   String institutionId) throws IOException {

        Map<String, String> params = new HashMap<>();
        String fileName = null;
        byte[] fileData = null;

        try {
            String boundaryHeader = exchange.getRequestHeaders().getFirst("Content-Type");
            String boundary = extractBoundary(boundaryHeader);
            byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

            InputStream is = exchange.getRequestBody();
            byte[] bodyBytes = is.readAllBytes();

            List<byte[]> parts = splitBytes(bodyBytes, boundaryBytes);

            for (byte[] part : parts) {
                if (part.length == 0) continue;

                int headerEndIndex = indexOf(part, new byte[]{'\r', '\n', '\r', '\n'}, 0);
                if (headerEndIndex == -1) continue;

                String headers = new String(part, 0, headerEndIndex, StandardCharsets.UTF_8);
                int contentStartIndex = headerEndIndex + 4;

                int contentEndIndex = part.length;
                if (contentEndIndex >= 2
                        && part[contentEndIndex - 2] == '\r'
                        && part[contentEndIndex - 1] == '\n') {
                    contentEndIndex -= 2;
                }

                if (!headers.contains("Content-Disposition: form-data; name=\"")) continue;

                int nameStart = headers.indexOf("name=\"") + 6;
                int nameEnd = headers.indexOf("\"", nameStart);
                if (nameStart < 6 || nameEnd < 0) continue;
                String name = headers.substring(nameStart, nameEnd);

                if (headers.contains("filename=\"")) {
                    int fnStart = headers.indexOf("filename=\"") + 10;
                    int fnEnd = headers.indexOf("\"", fnStart);
                    if (fnStart > 9 && fnEnd > fnStart) {
                        fileName = headers.substring(fnStart, fnEnd);
                        if (contentEndIndex > contentStartIndex) {
                            fileData = new byte[contentEndIndex - contentStartIndex];
                            System.arraycopy(part, contentStartIndex, fileData, 0, fileData.length);
                        }
                    }
                } else {
                    if (contentEndIndex > contentStartIndex) {
                        String value = new String(part, contentStartIndex,
                                contentEndIndex - contentStartIndex, StandardCharsets.UTF_8);
                        params.put(name, value);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur parsing multipart", e);
            String html = UIExamenProfesseur.renderError(
                    "Erreur lors de l'upload du fichier.");
            ResponseUtil.sendHtml(exchange, 400, html);
            return;
        }

        String action = params.get("action");
        if (!"ajouter".equals(action) && !"modifier".equals(action)) {
            String html = UIExamenProfesseur.renderError("Action inconnue : " + action);
            ResponseUtil.sendHtml(exchange, 400, html);
            return;
        }

        processExam(exchange, professeurId, institutionId, params, fileName, fileData, action);
    }

    private void handleFormData(HttpExchange exchange, String professeurId,
                                  String institutionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if (!"ajouter".equals(action) && !"modifier".equals(action)) {
            String html = UIExamenProfesseur.renderError("Action inconnue.");
            ResponseUtil.sendHtml(exchange, 400, html);
            return;
        }

        processExam(exchange, professeurId, institutionId, params, null, null, action);
    }

    // ============================================================
    // TRAITEMENT PRINCIPAL
    // ============================================================
    private void processExam(HttpExchange exchange, String professeurId,
                              String institutionId, Map<String, String> params,
                              String fileName, byte[] fileData, String action)
            throws IOException {

        String codeCours = params.get("codeCours");
        String classe = params.get("classe");
        String periode = params.get("periode");
        String annee = params.get("annee_academique");
        String titre = params.get("titre");
        String description = params.get("description");
        String dateStr = params.get("date_examen");
        String heureDebutStr = params.get("heure_debut");
        String heureFinStr = params.get("heure_fin");
        String dureeStr = params.get("duree_minutes");
        String salle = params.get("salle");
        String coefficientStr = params.get("coefficient");
        String noteMaxStr = params.get("note_maximale");
        String statut = params.get("statut");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // ============================================================
            // 🔒 SÉCURITÉ 1 : ANNÉE ACADÉMIQUE
            // ============================================================
            if (annee == null || annee.isBlank()) {
                renderFormSafely(exchange, conn, professeurId, institutionId, null,
                        "error", "Année académique obligatoire.");
                return;
            }

            AnneeAcademique anneeObj = new AnneeAcademiqueData(conn)
                    .readByInstitutionAndAnnee(institutionId, annee);
            if (anneeObj == null) {
                LOGGER.warning(() -> "⛔ Année non autorisée : " + annee
                        + " pour " + institutionId);
                renderFormSafely(exchange, conn, professeurId, institutionId, null,
                        "error", "Année académique invalide pour votre institution.");
                return;
            }

            // ============================================================
            // 🔒 SÉCURITÉ 2 : PÉRIODE
            // ============================================================
            if (periode == null || periode.isBlank()) {
                renderFormSafely(exchange, conn, professeurId, institutionId, null,
                        "error", "Période obligatoire.");
                return;
            }

            Periode periodeObj = new PeriodeData(conn)
                    .readByCleComposite(institutionId, annee, periode);
            if (periodeObj == null) {
                LOGGER.warning(() -> "⛔ Période non autorisée : " + periode
                        + " (" + annee + "/" + institutionId + ")");
                renderFormSafely(exchange, conn, professeurId, institutionId, null,
                        "error", "Période invalide pour cette année académique.");
                return;
            }

            // ============================================================
            // 🔒 SÉCURITÉ 3 : CLASSE AUTORISÉE POUR LE PROFESSEUR
            // ============================================================
            List<Professeur.Creneau> creneauxProf = new ProfesseurData(conn)
                    .getCreneauxParProfesseur(professeurId, institutionId);
            boolean classeAutorisee = creneauxProf.stream()
                    .anyMatch(c -> c.getClasse() != null
                            && c.getClasse().equalsIgnoreCase(classe));

            if (!classeAutorisee) {
                LOGGER.warning(() -> "⛔ Classe non autorisée : " + classe
                        + " pour " + professeurId);
                Examen examenErreur = null;
                String idStr = params.get("id");
                if (idStr != null && !idStr.isBlank()) {
                    examenErreur = new ExamenManager(conn)
                            .getExamenByIdForProfesseur(idStr, institutionId, professeurId);
                }
                renderFormSafely(exchange, conn, professeurId, institutionId, examenErreur,
                        "error",
                        "Action interdite : cette classe ne vous appartient pas.");
                return;
            }

            // ============================================================
            // 🔒 SÉCURITÉ 4 : FICHIER (taille + extension)
            // ============================================================
            if (fileData != null && fileName != null && !fileName.isBlank()) {
                if (fileData.length > MAX_FILE_SIZE) {
                    LOGGER.warning(() -> "⛔ Fichier trop gros : " + fileData.length);
                    renderFormSafely(exchange, conn, professeurId, institutionId, null,
                            "error", "Fichier trop volumineux (max 20 Mo).");
                    return;
                }
                if (!extensionAutorisee(fileName)) {
                    LOGGER.warning(() -> "⛔ Extension non autorisée : " + fileName);
                    renderFormSafely(exchange, conn, professeurId, institutionId, null,
                            "error", "Type de fichier non autorisé. Formats acceptés : "
                                    + String.join(", ", EXTENSIONS_AUTORISEES));
                    return;
                }
            }

            // ============================================================
            // Construction de l'objet Examen
            // ============================================================
            Examen examen = new Examen();
            examen.setInstitutionId(institutionId);
            examen.setProfesseurId(professeurId);
            examen.setCodeCours(codeCours);
            examen.setClasse(classe);
            examen.setPeriode(periode);
            examen.setAnneeAcademique(annee);
            examen.setTitre(titre);
            examen.setDescription(description);
            examen.setSalle(salle);
            examen.setStatut(statut != null ? statut : "PREVU");

            try {
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);

                if (dateStr != null && !dateStr.isBlank()) {
                    examen.setDateExamen(sdf.parse(dateStr));
                } else {
                    throw new ParseException("Date manquante", 0);
                }
                if (heureDebutStr != null && !heureDebutStr.isBlank()) {
                    examen.setHeureDebut(Time.valueOf(
                            heureDebutStr + (heureDebutStr.length() == 5 ? ":00" : "")));
                }
                if (heureFinStr != null && !heureFinStr.isBlank()) {
                    examen.setHeureFin(Time.valueOf(
                            heureFinStr + (heureFinStr.length() == 5 ? ":00" : "")));
                }
                if (dureeStr != null && !dureeStr.isBlank()) {
                    examen.setDureeMinutes(Integer.parseInt(dureeStr));
                }
                examen.setCoefficient(coefficientStr != null && !coefficientStr.isBlank()
                        ? Double.parseDouble(coefficientStr) : 1.0);
                examen.setNoteMaximale(noteMaxStr != null && !noteMaxStr.isBlank()
                        ? Double.parseDouble(noteMaxStr) : 20.0);
            } catch (ParseException | IllegalArgumentException e) {
                LOGGER.warning(() -> "Erreur format date/heure : " + e.getMessage());
                renderFormSafely(exchange, conn, professeurId, institutionId, null,
                        "error", "Erreur de format dans les dates ou heures.");
                return;
            }

            // ============================================================
            // Gestion du fichier joint
            // ============================================================
            String ancienChemin = null;

            if (fileData != null && fileName != null && !fileName.isBlank()) {
                try {
                    File uploadDir = new File(UPLOAD_DIR);
                    if (!uploadDir.exists()) uploadDir.mkdirs();

                    String extension = "";
                    int i = fileName.lastIndexOf('.');
                    if (i > 0) extension = fileName.substring(i);

                    String uniqueName = UUID.randomUUID().toString() + extension;
                    String filePath = UPLOAD_DIR + uniqueName;

                    File destFile = new File(filePath);
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        fos.write(fileData);
                    }

                    examen.setFichierNom(fileName);
                    examen.setFichierChemin(filePath);
                    examen.setFichierType(getMimeType(fileName));
                    examen.setTaille(fileData.length);

                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Erreur sauvegarde fichier", e);
                    renderFormSafely(exchange, conn, professeurId, institutionId, null,
                            "error", "Erreur lors de la sauvegarde du fichier.");
                    return;
                }
            }

            // ============================================================
            // INSERT / UPDATE
            // ============================================================
            ExamenManager manager = new ExamenManager(conn);
            boolean ok;

            if ("ajouter".equals(action)) {
                ok = manager.ajouterExamen(examen);
            } else {
                String idStr = params.get("id");
                if (idStr == null || idStr.isBlank()) {
                    throw new IllegalArgumentException("ID manquant pour la modification");
                }
                examen.setId(idStr);

                Examen old = manager.getExamenByIdForProfesseur(
                        idStr, institutionId, professeurId);
                if (old == null) {
                    LOGGER.warning(() -> "⛔ Modification refusée : examen " + idStr
                            + " ∉ prof " + professeurId);
                    renderFormSafely(exchange, conn, professeurId, institutionId, null,
                            "error", "Examen introuvable ou non autorisé.");
                    return;
                }

                if (fileData != null && fileName != null && !fileName.isBlank()) {
                    ancienChemin = old.getFichierChemin();
                }

                ok = manager.modifierExamen(examen);

                if (ok && ancienChemin != null) {
                    File oldFile = localiserFichierSecurise(ancienChemin);
                    if (oldFile != null && oldFile.exists()) {
                        oldFile.delete();
                    }
                }
            }

            String msg = ok
                    ? "Examen " + ("ajouter".equals(action) ? "ajouté" : "modifié")
                        + " avec succès."
                    : "Erreur lors de l'opération.";
            String msgType = ok ? "success" : "error";
            handleGetWithMessage(exchange, professeurId, institutionId, msgType, msg);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur processExam", e);
            handleGetWithMessage(exchange, professeurId, institutionId, "error",
                    "Erreur interne : " + e.getMessage());
        }
    }

    // ============================================================
    // HELPERS MULTIPART
    // ============================================================
    private String extractBoundary(String contentType) {
        if (contentType == null) return "boundary";
        int idx = contentType.indexOf("boundary=");
        if (idx >= 0) {
            return contentType.substring(idx + 9).trim();
        }
        return "boundary";
    }

    private List<byte[]> splitBytes(byte[] source, byte[] delimiter) {
        List<byte[]> parts = new ArrayList<>();
        int index;
        int prev = 0;
        while ((index = indexOf(source, delimiter, prev)) != -1) {
            if (prev != 0) {
                int length = index - prev;
                byte[] part = new byte[length];
                System.arraycopy(source, prev, part, 0, length);
                parts.add(part);
            }
            prev = index + delimiter.length;
        }
        return parts;
    }

    private int indexOf(byte[] outer, byte[] inner, int start) {
        for (int i = start; i <= outer.length - inner.length; i++) {
            boolean found = true;
            for (int j = 0; j < inner.length; j++) {
                if (outer[i + j] != inner[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    // ============================================================
    // SESSION
    // ============================================================
    private HttpSession resolveSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur résolution session", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;

            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();

            if ("SESSION_ID".equals(name) && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String readAttributeAsString(HttpSession session, String name) {
        if (session == null) return null;
        Object value = session.getAttribute(name);
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    private boolean professeurAppartientAInstitution(String professeurId,
                                                       String institutionId) {
        if (professeurId == null || institutionId == null) return false;

        String sql = "SELECT COUNT(*) FROM acces_admin "
                   + "WHERE utilisateur_id = ? AND institution_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, professeurId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur vérification appartenance", e);
            return false;
        }
    }

    // ============================================================
    // SÉCURITÉ FICHIERS
    // ============================================================
    private File localiserFichierSecurise(String cheminStocke) {
        if (cheminStocke == null || cheminStocke.isBlank()) return null;

        try {
            Path root = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            Path cible = Paths.get(cheminStocke).toAbsolutePath().normalize();

            if (!cible.startsWith(root)) {
                LOGGER.warning(() -> "⛔ Tentative d'accès hors UPLOAD_DIR : " + cheminStocke);
                return null;
            }

            File file = cible.toFile();
            return (file.exists() && file.isFile()) ? file : null;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur localisation fichier", e);
            return null;
        }
    }

    private boolean extensionAutorisee(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : EXTENSIONS_AUTORISEES) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            ResponseUtil.sendError(exchange, status, message);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Impossible d'envoyer l'erreur", e);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;

            try {
                String key = URLDecoder.decode(pair.substring(0, eq),
                        StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1),
                        StandardCharsets.UTF_8);
                params.put(key, value);
            } catch (Exception ignored) {}
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;

        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;

            try {
                String key = URLDecoder.decode(pair.substring(0, eq),
                        StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1),
                        StandardCharsets.UTF_8);
                params.put(key, value);
            } catch (Exception ignored) {}
        }
        return params;
    }
}