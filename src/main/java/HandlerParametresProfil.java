import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerParametresProfil implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerParametresProfil.class.getName());
    
    // ✅ CONSTANTES UNIFORMEES AVEC LES AUTRES HANDLERS
    private static final String PHOTOS_DIR = "data/photos/";
    private static final String PHOTOS_URL_PATH = "/data/photos/";

    static {
        File dir = new File(PHOTOS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            LOGGER.info("📁 Dossier créé: " + PHOTOS_DIR);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        LOGGER.info(() -> "🚀 ParametresProfil - " + method + " " + path + (query != null ? "?" + query : ""));

        String sessionId = getSessionId(exchange);
        if (sessionId == null || !isParametresAuthenticated(sessionId)) {
            redirect(exchange, "/admin/parametres");
            return;
        }

        String institutionId = getInstitutionIdFromSession(sessionId);
        if (institutionId == null) {
            redirect(exchange, "/admin/parametres");
            return;
        }

        if ("GET".equals(method)) {
            Institution institution = getInstitutionById(institutionId);
            if (institution == null) {
                redirect(exchange, "/admin/parametres");
                return;
            }
            String html = UIParametresProfil.rendreFormulaire(institution, null, null);
            sendResponse(exchange, 200, html);
            return;
        }

        if ("POST".equals(method)) {
            handlePost(exchange, institutionId);
            return;
        }

        sendResponse(exchange, 405, "<h1>405 Method Not Allowed</h1>");
    }

    private void handlePost(HttpExchange exchange, String institutionId) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> params = new HashMap<>();
        String logoPath = null;

        Institution oldInst = getInstitutionById(institutionId);

        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            String boundary = extractBoundary(contentType);
            if (boundary != null) {
                Map<String, byte[]> fichiers = new HashMap<>();
                parseMultipart(exchange, boundary, params, fichiers);
                
                if (fichiers.containsKey("logoFile")) {
                    byte[] fileData = fichiers.get("logoFile");
                    if (fileData != null && fileData.length > 0) {
                        String extension = detecterExtension(fileData);
                        String nomFichier = "logo_" + institutionId + "_" + System.currentTimeMillis() + extension;
                        String chemin = PHOTOS_DIR + nomFichier;
                        
                        try (FileOutputStream fos = new FileOutputStream(chemin)) {
                            fos.write(fileData);
                            fos.flush();
                        }
                        // ✅ Chemin avec slash pour l'URL (uniformisé)
                        logoPath = PHOTOS_URL_PATH + nomFichier;
                        LOGGER.log(Level.INFO, "✅ Logo uploadé: {0}", logoPath);
                    }
                }
            }
        } else {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            params = parseFormData(body);
        }

        String action = params.get("action");
        if (!"update".equals(action)) {
            redirect(exchange, "/admin/parametres/profil");
            return;
        }

        // ============================================================
        // CONSTRUCTION DE L'OBJET INSTITUTION (SANS MOT DE PASSE)
        // ============================================================
        Institution inst = new Institution();
        inst.setInstitutionId(institutionId);
        inst.setNomInstitution(params.getOrDefault("nomInstitution", oldInst != null ? oldInst.getNomInstitution() : ""));
        inst.setSigleInstitution(params.getOrDefault("sigleInstitution", oldInst != null ? oldInst.getSigleInstitution() : ""));
        inst.setDeviseInstitution(params.getOrDefault("deviseInstitution", oldInst != null ? oldInst.getDeviseInstitution() : ""));
        inst.setTypeInstitution(params.getOrDefault("typeInstitution", oldInst != null ? oldInst.getTypeInstitution() : ""));
        inst.setNiveauInstitution(params.getOrDefault("niveauInstitution", oldInst != null ? oldInst.getNiveauInstitution() : ""));
        inst.setCategorieInstitution(params.getOrDefault("categorieInstitution", oldInst != null ? oldInst.getCategorieInstitution() : ""));
        
        // ✅ Gestion du logo : priorité au nouveau logo uploadé
        if (logoPath != null && !logoPath.isBlank()) {
            inst.setLogoInstitution(logoPath);
            LOGGER.log(Level.INFO, "✅ Nouveau logo défini: {0}", logoPath);
        } else if (oldInst != null && oldInst.getLogoInstitution() != null && !oldInst.getLogoInstitution().isBlank()) {
            // Normaliser le chemin du logo existant
            String existingLogo = normaliserCheminLogo(oldInst.getLogoInstitution());
            inst.setLogoInstitution(existingLogo);
            LOGGER.log(Level.INFO, "✅ Logo existant conservé: {0}", existingLogo);
        }
        
        inst.setQRCodeInstitution(params.getOrDefault("qrcodeInstitution", oldInst != null ? oldInst.getQRCodeInstitution() : ""));
        inst.setAdresseInstitution(params.getOrDefault("adresseInstitution", oldInst != null ? oldInst.getAdresseInstitution() : ""));
        inst.setCodePostalInstitution(params.getOrDefault("codePostalInstitution", oldInst != null ? oldInst.getCodePostalInstitution() : ""));
        inst.setCommuneInstitution(params.getOrDefault("communeInstitution", oldInst != null ? oldInst.getCommuneInstitution() : ""));
        inst.setDepartementInstitution(params.getOrDefault("departementInstitution", oldInst != null ? oldInst.getDepartementInstitution() : ""));
        inst.setPaysInstitution(params.getOrDefault("paysInstitution", oldInst != null ? oldInst.getPaysInstitution() : ""));
        inst.setMailPrimaire(params.getOrDefault("mailPrimaire", oldInst != null ? oldInst.getMailPrimaire() : ""));
        inst.setMailSecondaire(params.getOrDefault("mailSecondaire", oldInst != null ? oldInst.getMailSecondaire() : ""));
        inst.setTelephonePrimaireInstitution(params.getOrDefault("telephonePrimaireInstitution", oldInst != null ? oldInst.getTelephonePrimaireInstitution() : ""));
        inst.setTelephoneSecondaireInstitution(params.getOrDefault("telephoneSecondaireInstitution", oldInst != null ? oldInst.getTelephoneSecondaireInstitution() : ""));
        inst.setSiteWebInstitution(params.getOrDefault("siteWebInstitution", oldInst != null ? oldInst.getSiteWebInstitution() : ""));
        inst.setSmtpPasswordInstitution(params.getOrDefault("smtpPasswordInstitution", oldInst != null ? oldInst.getSmtpPasswordInstitution() : ""));
        inst.setResponsableInstitution(params.getOrDefault("responsableInstitution", oldInst != null ? oldInst.getResponsableInstitution() : ""));
        inst.setPosteResponsableInstitution(params.getOrDefault("posteResponsableInstitution", oldInst != null ? oldInst.getPosteResponsableInstitution() : ""));
        inst.setMailResponsableInstitution(params.getOrDefault("mailResponsableInstitution", oldInst != null ? oldInst.getMailResponsableInstitution() : ""));
        inst.setTelephoneResponsableInstitution(params.getOrDefault("telephoneResponsableInstitution", oldInst != null ? oldInst.getTelephoneResponsableInstitution() : ""));
        inst.setMoyenneDePassageInstitution(params.getOrDefault("moyenneDePassageInstitution", oldInst != null ? oldInst.getMoyenneDePassageInstitution() : ""));
        inst.setSystemeEducatifInstitution(params.getOrDefault("systemeEducatifInstitution", oldInst != null ? oldInst.getSystemeEducatifInstitution() : ""));
        inst.setStatutInstitution(params.getOrDefault("statutInstitution", oldInst != null ? oldInst.getStatutInstitution() : ""));
        inst.setDateCreationInstitution(params.getOrDefault("dateCreationInstitution", oldInst != null ? oldInst.getDateCreationInstitution() : ""));
        
        // ⚠️ Le mot de passe n'est PAS récupéré ni stocké dans la table institutions
        // inst.setMotDePasseInstitution(...) est volontairement omis

        // Validation
        if (inst.getNomInstitution() == null || inst.getNomInstitution().isBlank()) {
            String html = UIParametresProfil.rendreFormulaire(inst, "Le nom de l'institution est obligatoire", null);
            sendResponse(exchange, 200, html);
            return;
        }
        if (inst.getMailPrimaire() == null || inst.getMailPrimaire().isBlank()) {
            String html = UIParametresProfil.rendreFormulaire(inst, "L'email principal est obligatoire", null);
            sendResponse(exchange, 200, html);
            return;
        }

        // ✅ Mise à jour en base (sans mot de passe)
        boolean success = updateInstitution(inst);
        if (success) {
            LOGGER.log(Level.INFO, "✅ Institution mise à jour avec succès: {0}", institutionId);
            String html = UIParametresProfil.rendreFormulaire(inst, null, "Informations mises à jour avec succès !");
            sendResponse(exchange, 200, html);
        } else {
            LOGGER.log(Level.WARNING, "❌ Échec de la mise à jour pour: {0}", institutionId);
            String html = UIParametresProfil.rendreFormulaire(inst, "Erreur lors de la mise à jour. Veuillez réessayer.", null);
            sendResponse(exchange, 200, html);
        }
    }

    // ============================================================
    // UPDATE (sans mot_de_passe)
    // ============================================================
    private boolean updateInstitution(Institution inst) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE institutions SET " +
                 "nom_institution=?, sigle_institution=?, devise_institution=?, type_institution=?, " +
                 "niveau_institution=?, categorie_institution=?, logo_institution=?, qrcode_institution=?, " +
                 "adresse_institution=?, code_postal_institution=?, commune_institution=?, departement_institution=?, " +
                 "pays_institution=?, mail_primaire=?, mail_secondaire=?, telephone_primaire=?, telephone_secondaire=?, " +
                 "site_web_institution=?, smtp_password=?, responsable_institution=?, poste_responsable=?, " +
                 "mail_responsable=?, telephone_responsable=?, moyenne_passage=?, systeme_educatif=?, " +
                 "statut_institution=?, date_creation=? WHERE institution_id=?")) {

            ps.setString(1, inst.getNomInstitution());
            ps.setString(2, inst.getSigleInstitution());
            ps.setString(3, inst.getDeviseInstitution());
            ps.setString(4, inst.getTypeInstitution());
            ps.setString(5, inst.getNiveauInstitution());
            ps.setString(6, inst.getCategorieInstitution());
            ps.setString(7, inst.getLogoInstitution());
            ps.setString(8, inst.getQRCodeInstitution());
            ps.setString(9, inst.getAdresseInstitution());
            ps.setString(10, inst.getCodePostalInstitution());
            ps.setString(11, inst.getCommuneInstitution());
            ps.setString(12, inst.getDepartementInstitution());
            ps.setString(13, inst.getPaysInstitution());
            ps.setString(14, inst.getMailPrimaire());
            ps.setString(15, inst.getMailSecondaire());
            ps.setString(16, inst.getTelephonePrimaireInstitution());
            ps.setString(17, inst.getTelephoneSecondaireInstitution());
            ps.setString(18, inst.getSiteWebInstitution());
            ps.setString(19, inst.getSmtpPasswordInstitution());
            ps.setString(20, inst.getResponsableInstitution());
            ps.setString(21, inst.getPosteResponsableInstitution());
            ps.setString(22, inst.getMailResponsableInstitution());
            ps.setString(23, inst.getTelephoneResponsableInstitution());
            ps.setString(24, inst.getMoyenneDePassageInstitution());
            ps.setString(25, inst.getSystemeEducatifInstitution());
            ps.setString(26, inst.getStatutInstitution());
            ps.setString(27, inst.getDateCreationInstitution());
            ps.setString(28, inst.getInstitutionId());

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur updateInstitution: {0}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // LECTURE (inchangée)
    // ============================================================
    private Institution getInstitutionById(String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM institutions WHERE institution_id = ?")) {
            ps.setString(1, institutionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Institution inst = new Institution();
                inst.setInstitutionId(rs.getString("institution_id"));
                inst.setNomInstitution(rs.getString("nom_institution"));
                inst.setSigleInstitution(rs.getString("sigle_institution"));
                inst.setDeviseInstitution(rs.getString("devise_institution"));
                inst.setTypeInstitution(rs.getString("type_institution"));
                inst.setNiveauInstitution(rs.getString("niveau_institution"));
                inst.setCategorieInstitution(rs.getString("categorie_institution"));
                
                // ✅ Normaliser le chemin du logo lors de la lecture
                String logo = rs.getString("logo_institution");
                inst.setLogoInstitution(normaliserCheminLogo(logo));
                
                inst.setQRCodeInstitution(rs.getString("qrcode_institution"));
                inst.setAdresseInstitution(rs.getString("adresse_institution"));
                inst.setCodePostalInstitution(rs.getString("code_postal_institution"));
                inst.setCommuneInstitution(rs.getString("commune_institution"));
                inst.setDepartementInstitution(rs.getString("departement_institution"));
                inst.setPaysInstitution(rs.getString("pays_institution"));
                inst.setMailPrimaire(rs.getString("mail_primaire"));
                inst.setMailSecondaire(rs.getString("mail_secondaire"));
                inst.setTelephonePrimaireInstitution(rs.getString("telephone_primaire"));
                inst.setTelephoneSecondaireInstitution(rs.getString("telephone_secondaire"));
                inst.setSiteWebInstitution(rs.getString("site_web_institution"));
                inst.setSmtpPasswordInstitution(rs.getString("smtp_password"));
                inst.setResponsableInstitution(rs.getString("responsable_institution"));
                inst.setPosteResponsableInstitution(rs.getString("poste_responsable"));
                inst.setMailResponsableInstitution(rs.getString("mail_responsable"));
                inst.setTelephoneResponsableInstitution(rs.getString("telephone_responsable"));
                inst.setMoyenneDePassageInstitution(rs.getString("moyenne_passage"));
                inst.setSystemeEducatifInstitution(rs.getString("systeme_educatif"));
                inst.setStatutInstitution(rs.getString("statut_institution"));
                inst.setDateCreationInstitution(rs.getString("date_creation"));
                // Le mot de passe n'est pas lu (et ne sera pas utilisé)
                // inst.setMotDePasseInstitution(rs.getString("mot_de_passe"));
                return inst;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur getInstitutionById: {0}", e.getMessage());
        }
        return null;
    }

    // ============================================================
    // NORMALISATION DU CHEMIN DU LOGO
    // ============================================================
    private String normaliserCheminLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }

        String normalized = logoPath.trim().replace("\\", "/");

        // Si c'est déjà une URL complète ou un data URI, on le garde
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("data:")) {
            return normalized;
        }

        // Supprimer les doubles slashes
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }

        // Si le chemin commence par "data/photos/" (sans slash), on ajoute le slash
        if (normalized.startsWith(PHOTOS_DIR)) {
            normalized = "/" + normalized;
        }

        // Si le chemin ne commence pas par "/data/photos/", on le normalise
        if (!normalized.startsWith(PHOTOS_URL_PATH)) {
            // Extraire le nom du fichier si le chemin contient un dossier
            String fileName = normalized.substring(normalized.lastIndexOf("/") + 1);
            if (!fileName.isBlank() && fileName.startsWith("logo_")) {
                normalized = PHOTOS_URL_PATH + fileName;
            } else {
                // Si le chemin ne correspond à rien, on le retourne tel quel
                // mais ça devrait être un chemin valide
                return normalized;
            }
        }

        return normalized;
    }

    // ============================================================
    // PARSING MULTIPART
    // ============================================================
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length());
            }
        }
        return null;
    }

    private void parseMultipart(HttpExchange exchange, String boundary, 
                                Map<String, String> params, Map<String, byte[]> fichiers) throws IOException {
        InputStream is = exchange.getRequestBody();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        byte[] data = baos.toByteArray();
        String body = new String(data, StandardCharsets.ISO_8859_1);

        String boundaryLine = "--" + boundary;
        String[] parts = body.split(boundaryLine);

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || part.equals("--")) continue;

            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd == -1) {
                headerEnd = part.indexOf("\n\n");
            }
            if (headerEnd == -1) continue;

            String headers = part.substring(0, headerEnd);
            String content = part.substring(headerEnd + 4);

            if (content.endsWith("--")) {
                content = content.substring(0, content.length() - 2);
            }
            content = content.trim();

            String name = null;
            String filename = null;
            String[] headerLines = headers.split("\r\n");
            for (String headerLine : headerLines) {
                if (headerLine.startsWith("Content-Disposition:")) {
                    String[] attrs = headerLine.split(";");
                    for (String attr : attrs) {
                        attr = attr.trim();
                        if (attr.startsWith("name=")) {
                            name = attr.substring("name=".length()).replace("\"", "");
                        } else if (attr.startsWith("filename=")) {
                            filename = attr.substring("filename=".length()).replace("\"", "");
                        }
                    }
                }
            }

            if (name == null) continue;

            if (filename != null && !filename.isEmpty()) {
                byte[] fileData = content.getBytes(StandardCharsets.ISO_8859_1);
                int i = fileData.length - 1;
                while (i >= 0 && (fileData[i] == '\r' || fileData[i] == '\n')) {
                    i--;
                }
                byte[] cleanData = new byte[i + 1];
                System.arraycopy(fileData, 0, cleanData, 0, i + 1);
                fichiers.put(name, cleanData);
                LOGGER.log(Level.INFO, "📎 Fichier reçu: {0} ({1} octets) - {2}", new Object[]{name, cleanData.length, filename});
            } else {
                params.put(name, content);
            }
        }
    }

    private String detecterExtension(byte[] data) {
        if (data.length < 4) return ".png";
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) return ".jpg";
        if (data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47) return ".png";
        if (data[0] == (byte)0x47 && data[1] == (byte)0x49 && data[2] == (byte)0x46) return ".gif";
        return ".png";
    }

    // ============================================================
    // GESTION DE SESSION
    // ============================================================
    private String getSessionId(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                    return parts[1];
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur getSessionId: {0}", e.getMessage());
        }
        return null;
    }

    private boolean isParametresAuthenticated(String sessionId) {
        if (sessionId == null) return false;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return false;
            Boolean flag = (Boolean) session.getAttribute("parametresAuthenticated");
            return flag != null && flag;
        } catch (Exception e) {
            return false;
        }
    }

    private String getInstitutionIdFromSession(String sessionId) {
        if (sessionId == null) return null;
        try {
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("institutionId");
        } catch (Exception e) {
            return null;
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
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
                } catch (Exception e) {
                    // ignore
                }
            }
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
}