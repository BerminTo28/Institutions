// HandlerEtudiantAttestation.java — Version intégrale corrigée

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantAttestation implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantAttestation.class.getName());
    private static final String ATTESTATIONS_DIR = "attestations";

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // Récupération de la session
            String numeroEtudiant = getNumeroEtudiantFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            // Vérification de la session
            if (numeroEtudiant == null || numeroEtudiant.isBlank()
                    || institutionId == null || institutionId.isBlank()) {
                redirectToLogin(exchange);
                return;
            }

            // Vérification des droits
            if (!verifierDroitAttestations(numeroEtudiant, institutionId)) {
                redirectToDashboard(exchange, "acces_refuse_attestations");
                return;
            }

            // Récupération de l'étudiant
            Etudiant etudiant = getEtudiant(numeroEtudiant, institutionId);
            if (etudiant == null) {
                sendErrorPage(exchange, 404, "Étudiant introuvable.");
                return;
            }

            // Traitement selon la méthode HTTP
            if ("GET".equalsIgnoreCase(method)) {
                if (path.endsWith("/telecharger")) {
                    handleTelecharger(exchange, numeroEtudiant, institutionId);
                } else {
                    handleGet(exchange, etudiant, institutionId);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, etudiant, institutionId);
            } else {
                sendErrorPage(exchange, 405, "Méthode non autorisée");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerEtudiantAttestation", e);
            try {
                sendErrorPage(exchange, 500, "Erreur interne: " + e.getMessage());
            } catch (IOException ignored) {
                // Le client est peut-être déjà déconnecté
            }
        }
    }

    // ============================================================
    // GESTION DES REQUÊTES GET
    // ============================================================
    private void handleGet(HttpExchange exchange, Etudiant etudiant, String institutionId) throws IOException {
        try {
            // Récupération des attestations de l'étudiant
            List<Attestation> attestations = AttestationDAO.getAllWithFilters(
                    institutionId,
                    etudiant.getNumeroIdentifiantEtudiant(),
                    null, null, null, null
            );

            // Récupération de l'institution (via DAO fiable)
            Institution institution = AttestationDAO.getInstitution(institutionId);

            // Option de l'étudiant — accès sûr (getter peut être absent)
            String optionEtudiant = safeGetOption(etudiant);

            // Génération de la page
            String html = UIAttestationEtudiant.render(
                    etudiant,
                    institution,
                    attestations,
                    optionEtudiant,
                    null,
                    null
            );

            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur chargement page attestations étudiant", e);
            sendErrorPage(exchange, 500, "Erreur lors du chargement des données.");
        }
    }

    // ============================================================
    // GESTION DES REQUÊTES POST
    // ============================================================
    private void handlePost(HttpExchange exchange, Etudiant etudiant, String institutionId) throws IOException {
        try {
            // Lecture des paramètres du formulaire
            Map<String, String> params = parseFormData(exchange);

            String type = params.get("type");
            String reference = params.get("reference");
            String numero = params.get("numero");
            String dateStr = params.get("date");
            String option = params.get("option");

            // Validation
            if (type == null || type.isBlank()) {
                sendErrorPage(exchange, 400, "Veuillez sélectionner un type d'attestation.");
                return;
            }

            // Génération automatique des champs vides
            if (reference == null || reference.isBlank()) {
                reference = "ATT-" + System.currentTimeMillis();
            }
            if (numero == null || numero.isBlank()) {
                numero = AttestationDAO.genererNumero(institutionId);
            }

            Date date = parseDate(dateStr);

            // Option : paramètre > getter étudiant > ""
            String optionEtudiant = (option != null && !option.isBlank())
                    ? option
                    : safeGetOption(etudiant);

            // Récupération de l'institution
            Institution institution = AttestationDAO.getInstitution(institutionId);
            if (institution == null) {
                sendErrorPage(exchange, 404, "Institution non trouvée.");
                return;
            }

            // Classe et année académique sûres
            String classe = safeGetClasse(etudiant);
            String anneeAcademique = safeGetAnneeAcademique(etudiant);

            // Génération du PDF
            String fileName = genererPDF(
                    etudiant,
                    institution,
                    type,
                    reference,
                    numero,
                    date,
                    optionEtudiant,
                    classe,
                    anneeAcademique
            );

            // Sauvegarde en base de données
            Attestation att = creerAttestation(
                    etudiant,
                    institutionId,
                    type,
                    reference,
                    numero,
                    date,
                    optionEtudiant,
                    fileName
            );

            boolean saved = AttestationDAO.save(att);
            if (!saved) {
                sendErrorPage(exchange, 500, "Erreur lors de l'enregistrement de l'attestation.");
                return;
            }

            // Nom complet sûr
            String nomComplet = ((etudiant.getNom() != null ? etudiant.getNom() : "")
                    + " "
                    + (etudiant.getPrenom() != null ? etudiant.getPrenom() : "")).trim();

            LOGGER.info(() -> "📄 Attestation générée pour " + nomComplet + " : " + fileName);

            // Réponse avec message de succès
            String html = UIAttestationEtudiant.renderMessage(
                    "Attestation générée avec succès !",
                    "success"
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (DocumentException e) {
            LOGGER.log(Level.SEVERE, "Erreur génération PDF", e);
            sendErrorPage(exchange, 500, "Erreur lors de la génération PDF : " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur IO génération attestation", e);
            sendErrorPage(exchange, 500, "Erreur lors de la génération : " + e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue génération attestation", e);
            sendErrorPage(exchange, 500, "Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // GESTION DU TÉLÉCHARGEMENT
    // ============================================================
    private void handleTelecharger(HttpExchange exchange, String numeroEtudiant, String institutionId) throws IOException {
        try {
            Map<String, String> params = parseQueryParams(exchange);
            String id = params.get("id");   // ✅ UUID String

            if (id == null || id.isBlank()) {
                sendErrorPage(exchange, 400, "ID manquant");
                return;
            }

            // Récupération de l'attestation (id = UUID String)
            Attestation att = AttestationDAO.getById(id, institutionId);

            // Vérification des droits
            if (att == null || !numeroEtudiant.equals(att.getEtudiantId())) {
                sendErrorPage(exchange, 403, "Attestation non trouvée ou non autorisée");
                return;
            }

            // Recherche du fichier PDF
            Path pdfPath = trouverFichierPDF(att);

            if (pdfPath == null || !Files.exists(pdfPath)) {
                sendErrorPage(exchange, 404, "Fichier PDF non trouvé");
                return;
            }

            // Envoi du fichier
            envoyerPDF(exchange, pdfPath);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur téléchargement", e);
            sendErrorPage(exchange, 500, "Erreur lors du téléchargement: " + e.getMessage());
        }
    }

    // ============================================================
    // GÉNÉRATION DU PDF
    // ============================================================
    private String genererPDF(Etudiant etudiant, Institution institution,
                              String type, String reference, String numero,
                              Date dateEmission, String option, String classe,
                              String anneeAcademique) throws IOException, DocumentException {

        // 1. Création du générateur
        AttestationGenerator generator = new AttestationGenerator(
                etudiant, institution, type, reference, numero,
                dateEmission, option, classe, anneeAcademique
        );

        // 2. Génération de l'image AWT
        BufferedImage image = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 595, 842);
        generator.drawDocument(g2);
        g2.dispose();

        // 3. Conversion en PDF
        String fileName = "attestation_" + System.currentTimeMillis() + ".pdf";
        File dir = new File(ATTESTATIONS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Impossible de créer le répertoire : " + ATTESTATIONS_DIR);
        }
        File output = new File(dir, fileName);

        Document document = new Document(PageSize.A4, 30, 30, 25, 25);
        try (FileOutputStream fos = new FileOutputStream(output)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);
            document.open();

            PdfContentByte cb = writer.getDirectContent();
            PdfTemplate template = cb.createTemplate(PageSize.A4.getWidth(), PageSize.A4.getHeight());
            Graphics2D pdfG2 = new PdfGraphics2D(template, PageSize.A4.getWidth(), PageSize.A4.getHeight());
            pdfG2.drawImage(image, 0, 0, null);
            pdfG2.dispose();
            cb.addTemplate(template, 0, 0);

            document.close();
        }
        return fileName;
    }

    // ============================================================
    // CRÉATION DE L'OBJET ATTESTATION
    // ============================================================
    private Attestation creerAttestation(Etudiant etudiant, String institutionId,
                                         String type, String reference, String numero,
                                         Date date, String option, String fileName) {
        Attestation att = new Attestation();
        att.setInstitutionId(institutionId);
        att.setEtudiantId(etudiant.getNumeroIdentifiantEtudiant());
        att.setType(type);
        att.setReference(reference);
        att.setNumero(numero);
        att.setDateEmission(date);
        att.setClasse(safeGetClasse(etudiant));
        att.setAnneeAcademique(safeGetAnneeAcademique(etudiant));

        // Période — accès sûr
        try {
            att.setPeriode(etudiant.getPeriode());
        } catch (Exception ignored) {
            // getPeriode() peut ne pas exister
        }

        att.setOption(option);
        // ⚠️ Le nom du fichier PDF est stocké dans contenuHTML.
        //    Idéalement, renommer ce champ en `fichier_pdf` dans la table + le bean + le DAO.
        att.setContenuHTML(fileName);
        return att;
    }

    // ============================================================
    // RÉCUPÉRATION ÉTUDIANT
    // ============================================================
    private Etudiant getEtudiant(String numeroIdentifiant, String institutionId) {
        // 1) Essai via AttestationDAO (plus fiable)
        try {
            Etudiant e = AttestationDAO.getEtudiant(numeroIdentifiant, institutionId);
            if (e != null) return e;
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "AttestationDAO.getEtudiant a échoué, fallback EtudiantData", ex);
        }

        // 2) Fallback via EtudiantData
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData etudiantData = new EtudiantData(conn);
            return etudiantData.read(numeroIdentifiant, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération étudiant", e);
            return null;
        }
    }

    // ============================================================
    // VÉRIFICATION DES DROITS
    // ============================================================
    private boolean verifierDroitAttestations(String etudiantId, String institutionId) {
        // ⚠️ Adapter le nom de la table/colonne selon votre schéma réel
        String sql = "SELECT attestations FROM acces WHERE numero_identifiant = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etudiantId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("attestations");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits attestations", e);
        }
        return false;
    }

    // ============================================================
    // PARSING DATE
    // ============================================================
    private Date parseDate(String dateStr) {
        Date date = new Date();
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                date = java.sql.Date.valueOf(dateStr);
            } catch (IllegalArgumentException ignored) {
                // Garder la date courante
            }
        }
        return date;
    }

    // ============================================================
    // RECHERCHE FICHIER PDF
    // ============================================================
    private Path trouverFichierPDF(Attestation att) {
        // 1) Essayer avec le nom stocké
        String fileName = att.getContenuHTML();
        if (fileName != null && !fileName.isBlank()) {
            Path pdfPath = Paths.get(ATTESTATIONS_DIR, fileName);
            if (Files.exists(pdfPath)) {
                return pdfPath;
            }
        }

        // 2) Recherche par numéro
        try {
            Path dir = Paths.get(ATTESTATIONS_DIR);
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    var found = stream
                            .filter(p -> p.getFileName().toString().contains(att.getNumero()))
                            .findFirst();
                    if (found.isPresent()) {
                        return found.get();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "Erreur recherche fichier PDF: " + e.getMessage());
        }

        return null;
    }

    // ============================================================
    // ENVOI DU PDF
    // ============================================================
    private void envoyerPDF(HttpExchange exchange, Path pdfPath) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + pdfPath.getFileName().toString() + "\"");
        exchange.sendResponseHeaders(200, Files.size(pdfPath));

        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(pdfPath, os);
            os.flush();
        }
    }

    // ============================================================
    // REDIRECTIONS ET ERREURS
    // ============================================================
    private void redirectToLogin(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/connexion");
        exchange.sendResponseHeaders(302, -1);
    }

    private void redirectToDashboard(HttpExchange exchange, String error) throws IOException {
        exchange.getResponseHeaders().set("Location", "/etudiant/dashboard?error=" + error);
        exchange.sendResponseHeaders(302, -1);
    }

    private void sendErrorPage(HttpExchange exchange, int statusCode, String message) throws IOException {
        String html = UIAttestationEtudiant.renderError(message);
        ResponseUtil.sendHtml(exchange, statusCode, html);
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getNumeroEtudiantFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        return session != null ? (String) session.getAttribute("userId") : null;
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

            String sessionId = null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    sessionId = parts[1];
                    break;
                }
            }

            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.warning(() -> "Impossible de récupérer la session: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // PARSING DES PARAMÈTRES
    // ============================================================
    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String pair : query.split("&")) {
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

    private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = new HashMap<>();
        if (body.isBlank()) return params;

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

    // ============================================================
    // HELPERS D'ACCÈS SÛRS SUR L'ÉTUDIANT
    // ============================================================
    private String safeGetOption(Etudiant etudiant) {
        try {
            Object v = etudiant.getClass().getMethod("getOption").invoke(etudiant);
            return v != null ? v.toString() : "";
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            return "";
        }
    }

    private String safeGetClasse(Etudiant etudiant) {
        try {
            Object v = etudiant.getClass().getMethod("getClasse").invoke(etudiant);
            return v != null ? v.toString() : "";
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            return "";
        }
    }

    private String safeGetAnneeAcademique(Etudiant etudiant) {
        try {
            Object v = etudiant.getClass().getMethod("getAnneeAcademique").invoke(etudiant);
            return v != null ? v.toString() : "";
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            return "";
        }
    }
}