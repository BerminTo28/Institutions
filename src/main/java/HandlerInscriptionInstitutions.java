import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.google.zxing.WriterException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour l'inscription d'une nouvelle institution.
 *
 * <p><b>SÉCURITÉ</b> :
 * <ul>
 *   <li>Limite de taille du body (10 Mo)</li>
 *   <li>Limite du nombre de parties multipart (anti-DoS)</li>
 *   <li>Whitelist stricte des extensions d'images en entrée</li>
 *   <li>Conversion forcée en PNG (pas d'écriture brute)</li>
 *   <li>Validation stricte de l'email (avec TLD)</li>
 *   <li>Validation du mot de passe (longueur + complexité)</li>
 *   <li>Anti-XSS sur les messages d'erreur</li>
 *   <li>Cookie de session HttpOnly + SameSite + Secure si HTTPS</li>
 *   <li>Nettoyage des fichiers orphelins en cas d'échec BD</li>
 * </ul>
 *
 * <p><b>ISOLATION PAR INSTITUTION</b> :
 * <ul>
 *   <li>Vérifie qu'aucune institution n'a le même sigle/email</li>
 *   <li>Génère un {@code institution_id} unique</li>
 *   <li>Création transactionnelle institution + admin</li>
 * </ul>
 *
 * <p><b>FORMAT DES LOGOS</b> : tous les logos sont convertis en PNG. Les
 * extensions acceptées en entrée sont : {@code .png, .jpg, .jpeg, .gif,
 * .webp, .bmp}. Le fichier écrit est TOUJOURS {@code .png}.</p>
 */
public class HandlerInscriptionInstitutions implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerInscriptionInstitutions.class.getName());

    // ============================================================
    // CONSTANTES DE STOCKAGE
    // ============================================================
    private static final String PHOTOS_DIR = "data/photos/";
    private static final String PHOTOS_URL = "/data/photos/";
    private static final String QR_DIR     = "data/qr_codes/";
    private static final String QR_URL     = "/data/qr_codes/";

    // ============================================================
    // LIMITES DE SÉCURITÉ
    // ============================================================
    private static final long TAILLE_MAX_LOGO           = 5L * 1024 * 1024;   // 5 Mo
    private static final long TAILLE_MAX_REQUETE        = 10L * 1024 * 1024;  // 10 Mo
    private static final int  MAX_PARTIES_MULTIPART     = 100;                // anti-DoS
    private static final int  LONGUEUR_MIN_MOT_DE_PASSE = 8;
    private static final int  LONGUEUR_MAX_MOT_DE_PASSE = 128;

    // ============================================================
    // WHITELISTS
    // ============================================================
    /** Extensions acceptées en entrée (le fichier écrit sera toujours .png). */
    private static final Set<String> EXTENSIONS_AUTORISEES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"
    );

    /** Moyennes de passage autorisées. */
    private static final Set<String> MOYENNES_AUTORISEES = Set.of(
            "10", "10.00", "100", "100.00"
    );

    // ============================================================
    // PATTERNS
    // ============================================================
    private static final Pattern PATTERN_EMAIL =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PATTERN_BOUNDARY =
            Pattern.compile("boundary=([^;]+)");
    private static final Pattern PATTERN_NAME =
            Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern PATTERN_FILENAME =
            Pattern.compile("filename=\"([^\"]+)\"");
    private static final Pattern PATTERN_EXTENSION =
            Pattern.compile("\\.[a-z0-9]{2,5}");

    // ============================================================
    // CRÉATION DES DOSSIERS (idempotente)
    // ============================================================
    static {
        try {
            Files.createDirectories(Paths.get(PHOTOS_DIR));
            Files.createDirectories(Paths.get(QR_DIR));
            LOGGER.info("📁 Dossiers prêts : " + PHOTOS_DIR + ", " + QR_DIR);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Impossible de créer les dossiers de stockage", e);
        }
    }

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        final String path   = exchange.getRequestURI().getPath();

        LOGGER.info(() -> "🔵 [HandlerInscriptionInstitutions] " + method + " " + path);

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerInscriptionInstitutions", e);
            safeRenderError(exchange, 500,
                    "Une erreur interne est survenue. Contactez l'administrateur.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue", e);
            safeRenderError(exchange, 500,
                    "Une erreur interne est survenue. Contactez l'administrateur.");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        LOGGER.info("📄 Affichage du formulaire d'inscription");
        String html = UIInscriptionInstitutions.render(null, null);
        ResponseUtil.sendHtml(exchange, 200, html);
    }

    // ============================================================
    // POST — TRAITEMENT DU FORMULAIRE
    // ============================================================
    private void handlePost(HttpExchange exchange) throws IOException {
        LOGGER.info("📨 Réception d'une demande POST pour l'inscription institution");

        // 1) Content-Type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            safeRenderError(exchange, 400,
                    "Le formulaire doit être soumis en multipart/form-data.");
            return;
        }

        // 2) Content-Length préventif
        if (!verifierTailleRequete(exchange)) {
            safeRenderError(exchange, 413,
                    "Requête trop volumineuse (max "
                    + (TAILLE_MAX_REQUETE / 1024 / 1024) + " Mo).");
            return;
        }

        // 3) Lecture limitée
        byte[] bodyBytes = lireBodyAvecLimite(exchange, TAILLE_MAX_REQUETE);
        if (bodyBytes == null) {
            safeRenderError(exchange, 413,
                    "Requête trop volumineuse (max "
                    + (TAILLE_MAX_REQUETE / 1024 / 1024) + " Mo).");
            return;
        }

        // 4) Parsing multipart
        MultipartParser.Result result;
        try {
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                throw new IOException("Boundary introuvable dans Content-Type");
            }
            result = MultipartParser.parse(bodyBytes, boundary, MAX_PARTIES_MULTIPART);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erreur parsing multipart", e);
            safeRenderError(exchange, 400,
                    "Le formulaire n'a pas pu être traité. Vérifiez le fichier envoyé.");
            return;
        }

        Map<String, String> params = result.fields;
        byte[] logoData         = result.fileData;
        String originalFileName = result.fileName;

        // 5) Extraction des champs obligatoires
        String nomInstitution        = params.get("nomInstitution");
        String sigleInstitution      = params.get("sigleInstitution");
        String mailPrimaire          = params.get("mailPrimaire");
        String motDePasseInstitution = params.get("motDePasseInstitution");
        String moyenneInstitution    = params.get("moyenneDePassageInstitution");

        // 6) Validations métier
        String erreurValidation = validerChampsObligatoires(
                nomInstitution, sigleInstitution, mailPrimaire, motDePasseInstitution);
        if (erreurValidation != null) {
            safeRenderError(exchange, 400, erreurValidation);
            return;
        }

        if (!PATTERN_EMAIL.matcher(mailPrimaire).matches()) {
            safeRenderError(exchange, 400, "Email principal invalide.");
            return;
        }

        String erreurMdp = validerMotDePasse(motDePasseInstitution);
        if (erreurMdp != null) {
            safeRenderError(exchange, 400, erreurMdp);
            return;
        }

        if (isBlank(moyenneInstitution)
                || !MOYENNES_AUTORISEES.contains(moyenneInstitution.trim())) {
            safeRenderError(exchange, 400, "La moyenne de base doit être 10 ou 100.");
            return;
        }
        String moyNormalisee = moyenneInstitution.trim().startsWith("10")
                ? "10.00" : "100.00";

        if (logoData != null && logoData.length > TAILLE_MAX_LOGO) {
            safeRenderError(exchange, 400,
                    "Le logo ne doit pas dépasser "
                    + (TAILLE_MAX_LOGO / 1024 / 1024) + " Mo.");
            return;
        }

        // 7) Création
        final String institutionId = genererInstitutionId();
        String logoPath = null;
        String qrPath   = null;

        try {
            // 8) Vérifications d'unicité (sigle + email) — AVANT toute écriture
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                InstitutionData institutionData = new InstitutionData(conn);
                InstitutionManager manager = new InstitutionManager(institutionData);

                if (manager.sigleExiste(sigleInstitution)) {
                    safeRenderError(exchange, 400,
                            "Ce sigle est déjà utilisé par une autre institution.");
                    return;
                }
                if (manager.emailExiste(mailPrimaire)) {
                    safeRenderError(exchange, 400,
                            "Cet email est déjà utilisé par une autre institution.");
                    return;
                }
            }

            // 9) Sauvegarde du logo (après vérification d'unicité)
            logoPath = sauvegarderLogo(logoData, originalFileName, institutionId);
            qrPath   = genererQRCode(institutionId, nomInstitution, sigleInstitution);

            // 10) Construction de l'institution
            Institution institution = construireInstitution(
                    params, institutionId, logoPath, qrPath, moyNormalisee,
                    mailPrimaire, motDePasseInstitution);

            // 11) Création transactionnelle (institution + admin)
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                InstitutionData institutionData = new InstitutionData(conn);
                InstitutionManager manager = new InstitutionManager(institutionData);

                boolean success = manager.ajouterInstitutionAvecAdmin(institution);
                if (!success) {
                    LOGGER.severe(() -> "❌ Échec création institution " + institutionId);
                    nettoyerFichiersOrphelins(logoPath, qrPath);
                    safeRenderError(exchange, 500,
                            "Erreur lors de l'enregistrement. Contactez l'administrateur.");
                    return;
                }
            }

            LOGGER.info(() -> "✅ Institution créée : " + institutionId);

            // 12) Session + redirection
            creerSessionEtRediriger(exchange, institutionId, mailPrimaire, nomInstitution);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur SQL lors de la création d'institution", e);
            nettoyerFichiersOrphelins(logoPath, qrPath);
            safeRenderError(exchange, 500,
                    "Une erreur est survenue lors de l'enregistrement. "
                    + "Contactez l'administrateur.");
        }
    }

    // ============================================================
    // VALIDATIONS
    // ============================================================
    private String validerChampsObligatoires(String nom, String sigle,
                                              String mail, String mdp) {
        if (isBlank(nom))    return "Le nom de l'institution est obligatoire.";
        if (isBlank(sigle))  return "Le sigle est obligatoire.";
        if (isBlank(mail))   return "L'email principal est obligatoire.";
        if (isBlank(mdp))    return "Le mot de passe est obligatoire.";
        return null;
    }

    /**
     * ✅ Valide le mot de passe : longueur minimale + complexité.
     */
    private String validerMotDePasse(String motDePasse) {
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            return "Le mot de passe doit contenir au moins "
                    + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.";
        }
        if (motDePasse.length() > LONGUEUR_MAX_MOT_DE_PASSE) {
            return "Le mot de passe ne doit pas dépasser "
                    + LONGUEUR_MAX_MOT_DE_PASSE + " caractères.";
        }
        boolean aMajuscule = motDePasse.chars().anyMatch(Character::isUpperCase);
        boolean aMinuscule = motDePasse.chars().anyMatch(Character::isLowerCase);
        boolean aChiffre   = motDePasse.chars().anyMatch(Character::isDigit);

        if (!aMajuscule || !aMinuscule || !aChiffre) {
            return "Le mot de passe doit contenir au moins une majuscule, "
                    + "une minuscule et un chiffre.";
        }
        return null;
    }

    private boolean verifierTailleRequete(HttpExchange exchange) {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength == null) return true;
        try {
            return Long.parseLong(contentLength) <= TAILLE_MAX_REQUETE;
        } catch (NumberFormatException ignored) {
            return true;    // la lecture limitée se chargera du contrôle
        }
    }

    private String genererInstitutionId() {
        return "INST_" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();
    }

    /**
     * ✅ Construit l'entité Institution à partir des paramètres du formulaire.
     */
    private Institution construireInstitution(Map<String, String> params,
                                                String institutionId,
                                                String logoPath,
                                                String qrPath,
                                                String moyNormalisee,
                                                String mailPrimaire,
                                                String motDePasse) {
        Institution institution = new Institution();
        institution.setInstitutionId(institutionId);

        // Identification
        institution.setNomInstitution(params.get("nomInstitution"));
        institution.setSigleInstitution(params.get("sigleInstitution"));
        institution.setDeviseInstitution(params.get("deviseInstitution"));
        institution.setTypeInstitution(params.get("typeInstitution"));
        institution.setNiveauInstitution(params.get("niveauInstitution"));
        institution.setCategorieInstitution(params.get("categorieInstitution"));

        // Médias
        institution.setLogoInstitution(logoPath);
        institution.setQRCodeInstitution(qrPath);

        // Coordonnées
        institution.setAdresseInstitution(params.get("adresseInstitution"));
        institution.setCodePostalInstitution(params.get("codePostalInstitution"));
        institution.setCommuneInstitution(params.get("communeInstitution"));
        institution.setDepartementInstitution(params.get("departementInstitution"));
        institution.setPaysInstitution(
                !isBlank(params.get("paysInstitution"))
                        ? params.get("paysInstitution")
                        : "Haïti");

        // Contacts
        institution.setMailPrimaire(mailPrimaire);
        institution.setMailSecondaire(params.get("mailSecondaire"));
        institution.setTelephonePrimaireInstitution(params.get("telephonePrimaireInstitution"));
        institution.setTelephoneSecondaireInstitution(params.get("telephoneSecondaireInstitution"));
        institution.setSiteWebInstitution(params.get("siteWebInstitution"));
        institution.setSmtpPasswordInstitution(params.get("smtpPasswordInstitution"));

        // Responsable
        institution.setResponsableInstitution(params.get("responsableInstitution"));
        institution.setPosteResponsableInstitution(params.get("posteResponsableInstitution"));
        institution.setMailResponsableInstitution(params.get("mailResponsableInstitution"));
        institution.setTelephoneResponsableInstitution(params.get("telephoneResponsableInstitution"));

        // Système éducatif
        institution.setMoyenneDePassageInstitution(moyNormalisee);
        institution.setSystemeEducatifInstitution(params.get("systemeEducatifInstitution"));

        // Statut
        institution.setStatutInstitution(
                !isBlank(params.get("statutInstitution"))
                        ? params.get("statutInstitution")
                        : "ACTIF");
        institution.setStatutValidation("PENDING");

        // Date
        institution.setDateCreationInstitution(
                !isBlank(params.get("dateCreationInstitution"))
                        ? params.get("dateCreationInstitution")
                        : LocalDate.now().toString());

        // Sécurité (transitoire)
        institution.setMotDePasseInstitution(motDePasse);

        return institution;
    }

    // ============================================================
    // LECTURE DU BODY AVEC LIMITE
    // ============================================================
    private byte[] lireBodyAvecLimite(HttpExchange exchange, long limite)
            throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int lus;
            while ((lus = is.read(buffer)) != -1) {
                total += lus;
                if (total > limite) return null;
                baos.write(buffer, 0, lus);
            }
            return baos.toByteArray();
        }
    }

    // ============================================================
    // SAUVEGARDE DU LOGO (CONVERSION PNG FORCÉE)
    // ============================================================
    /**
     * ✅ Sauvegarde le logo uploadé en le convertissant TOUJOURS en PNG.
     *
     * <p><b>Étapes</b> :
     * <ol>
     *   <li><b>Validation de l'extension</b> uploadée (whitelist stricte)
     *       — via {@link #extraireExtensionSure(String)}</li>
     *   <li>Décodage via ImageIO</li>
     *   <li>Conversion en PNG (fond transparent)</li>
     *   <li>Écriture sur disque avec extension {@code .png}</li>
     * </ol>
     *
     * <p><b>Sécurité</b> : un fichier non décodable (ex : {@code .exe}
     * renommé) est <b>refusé</b> — aucun fichier brut n'est écrit.</p>
     *
     * @return le chemin URL du logo ({@code /data/photos/logo_*.png}),
     *         ou {@code null} si aucun logo fourni / invalide
     */
    private String sauvegarderLogo(byte[] logoData, String originalFileName,
                                     String institutionId) {
        if (logoData == null || logoData.length == 0 || originalFileName == null) {
            return null;
        }

        // ✅ 1) Validation de l'extension (AVANT tout décodage — économie CPU + sécurité)
        final String extension = extraireExtensionSure(originalFileName);
        if (extension == null) {
            LOGGER.warning(() -> "⛔ Logo rejeté (extension invalide ou non autorisée) : "
                    + originalFileName);
            return null;
        }

        LOGGER.fine(() -> "🔍 Logo : " + originalFileName
                + " (extension validée : " + extension + ")");

        // ✅ 2) Décodage
        BufferedImage sourceImage;
        try (InputStream bais = new ByteArrayInputStream(logoData)) {
            sourceImage = ImageIO.read(bais);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "❌ Image non décodable, logo ignoré", e);
            return null;
        }

        if (sourceImage == null) {
            LOGGER.warning(() -> "⚠️ Format image non décodable, logo ignoré : "
                    + originalFileName + " (extension=" + extension + ")");
            return null;
        }

        // ✅ 3) Conversion en PNG (fond transparent)
        BufferedImage pngImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = pngImage.createGraphics();
        try {
            g2d.drawImage(sourceImage, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        // ✅ 4) Écriture PNG (extension TOUJOURS .png)
        final String pngFileName = "logo_" + institutionId + "_"
                + System.currentTimeMillis() + ".png";
        final File pngFile = new File(PHOTOS_DIR, pngFileName);
        pngFile.getParentFile().mkdirs();

        try {
            boolean written = ImageIO.write(pngImage, "png", pngFile);
            if (!written) {
                LOGGER.warning(() -> "⚠️ Échec écriture PNG : " + pngFile);
                return null;
            }
            LOGGER.info(() -> "✅ Logo converti en PNG : " + pngFile.getAbsolutePath());
            return PHOTOS_URL + pngFileName;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur écriture PNG", e);
            return null;
        }
    }

    /**
     * ✅ Extrait et VALIDE l'extension d'un nom de fichier.
     *
     * <p><b>Whitelist stricte</b> : {@code .png, .jpg, .jpeg, .gif, .webp, .bmp}.
     * Le fichier écrit sera de toute façon converti en {@code .png}.</p>
     *
     * @param originalFileName nom du fichier uploadé (ex : {@code logo.jpg})
     * @return l'extension valide (avec le point), ou {@code null} si non autorisée
     */
    private String extraireExtensionSure(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            LOGGER.fine("⛔ Nom de fichier null/vide");
            return null;
        }

        int dot = originalFileName.lastIndexOf('.');
        if (dot <= 0 || dot == originalFileName.length() - 1) {
            LOGGER.warning(() -> "⛔ Extension manquante : " + originalFileName);
            return null;
        }

        String candidate = originalFileName.substring(dot).toLowerCase();

        // Format valide : .xxx (2 à 5 caractères alphanumériques)
        if (!PATTERN_EXTENSION.matcher(candidate).matches()) {
            LOGGER.warning(() -> "⛔ Extension invalide : " + candidate);
            return null;
        }

        // Whitelist stricte
        if (!EXTENSIONS_AUTORISEES.contains(candidate)) {
            LOGGER.warning(() -> "⛔ Extension non autorisée : " + candidate);
            return null;
        }

        return candidate;
    }

    private void ecrireOctets(File dest, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
            fos.flush();
        }
    }

    // ============================================================
    // GÉNÉRATION DU QR CODE
    // ============================================================
    private String genererQRCode(String institutionId, String nom, String sigle) {
        try {
            String qrText = "INST:" + institutionId
                    + "|Nom:" + nom
                    + "|Sigle:" + sigle;
            byte[] qrBytes = QRCodeGenerator.generateQRCodeImage(qrText, 200, 200);

            String qrFileName = "qr_" + institutionId + ".png";
            File qrFile = new File(QR_DIR, qrFileName);
            qrFile.getParentFile().mkdirs();
            ecrireOctets(qrFile, qrBytes);

            LOGGER.info(() -> "✅ QR Code généré : " + qrFile.getAbsolutePath());
            return QR_URL + qrFileName;

        } catch (WriterException | IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur génération QR Code", e);
            return null;
        }
    }

    // ============================================================
    // NETTOYAGE EN CAS D'ÉCHEC
    // ============================================================
    private void nettoyerFichiersOrphelins(String logoPath, String qrPath) {
        supprimerFichier(logoPath, PHOTOS_URL, PHOTOS_DIR);
        supprimerFichier(qrPath, QR_URL, QR_DIR);
    }

    private void supprimerFichier(String urlPath, String urlPrefix, String dir) {
        if (urlPath == null || !urlPath.startsWith(urlPrefix)) return;
        String fileName = urlPath.substring(urlPrefix.length());
        Path file = Paths.get(dir, fileName);
        try {
            Files.deleteIfExists(file);
            LOGGER.fine(() -> "🧹 Fichier orphelin supprimé : " + file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Impossible de supprimer " + file, e);
        }
    }

    // ============================================================
    // SESSION + REDIRECTION
    // ============================================================
    private void creerSessionEtRediriger(HttpExchange exchange,
                                           String institutionId,
                                           String mailPrimaire,
                                           String nomInstitution) throws IOException {
        String sessionId = SessionManager.createSession();
        HttpSession session = SessionManager.getSession(sessionId);
        session.setAttribute("userId", mailPrimaire);
        session.setAttribute("role", "ADMINISTRATEUR");
        session.setAttribute("institutionId", institutionId);
        session.setAttribute("institution_id", institutionId);
        session.setAttribute("email", mailPrimaire);
        session.setAttribute("displayName", nomInstitution);
        session.setAttribute("nomInstitution", nomInstitution);

        exchange.getResponseHeaders().set("Set-Cookie",
                buildSessionCookie(exchange, sessionId));
        exchange.getResponseHeaders().set("Location", "/dashboard");
        exchange.sendResponseHeaders(302, -1);
    }

    private String buildSessionCookie(HttpExchange exchange, String sessionId) {
        boolean https = isHttps(exchange);
        StringBuilder cookie = new StringBuilder();
        cookie.append("SESSION_ID=").append(sessionId);
        cookie.append("; Path=/");
        cookie.append("; HttpOnly");
        cookie.append("; SameSite=Lax");
        if (https) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    private boolean isHttps(HttpExchange exchange) {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        if (proto != null && proto.equalsIgnoreCase("https")) return true;
        String scheme = exchange.getRequestURI().getScheme();
        return "https".equalsIgnoreCase(scheme);
    }

    // ============================================================
    // RENDU D'ERREUR SÛR
    // ============================================================
    private void safeRenderError(HttpExchange exchange, int status, String message) {
        try {
            String html = UIInscriptionInstitutions.render(echapperHtml(message), null);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur envoi page erreur", e);
            try {
                ResponseUtil.sendError(exchange, status, message);
            } catch (IOException ignored) {}
        }
    }

    private String echapperHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ============================================================
    // UTILITAIRES MULTIPART
    // ============================================================
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        Matcher m = PATTERN_BOUNDARY.matcher(contentType);
        return m.find() ? m.group(1).trim().replace("\"", "") : null;
    }

    // ============================================================
    // PARSER MULTIPART ROBUSTE
    // ============================================================
    private static final class MultipartParser {

        static final class Result {
            final Map<String, String> fields = new HashMap<>();
            byte[] fileData;
            String fileName;
        }

        static Result parse(byte[] body, String boundary, int maxParties)
                throws IOException {
            Result result = new Result();
            byte[] delimiter    = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            byte[] endDelimiter = ("--" + boundary + "--").getBytes(StandardCharsets.ISO_8859_1);
            byte[] headerSep    = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

            int pos = 0;
            int bodyLen = body.length;
            int nbParties = 0;

            while (pos < bodyLen) {
                if (++nbParties > maxParties) {
                    throw new IOException("Trop de parties multipart (max "
                            + maxParties + ")");
                }

                int delimStart = indexOf(body, delimiter, pos);
                if (delimStart == -1) break;

                if (startsWith(body, endDelimiter, delimStart)) break;

                int partStart = delimStart + delimiter.length;
                if (partStart + 1 < bodyLen
                        && body[partStart] == '\r' && body[partStart + 1] == '\n') {
                    partStart += 2;
                }

                int partEnd = indexOf(body, delimiter, partStart);
                if (partEnd == -1) partEnd = bodyLen;

                int contentEnd = partEnd;
                if (contentEnd >= 2
                        && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                    contentEnd -= 2;
                }

                int headerEnd = indexOf(body, headerSep, partStart);
                if (headerEnd == -1 || headerEnd > contentEnd) {
                    pos = partEnd;
                    continue;
                }

                String headers = new String(body, partStart,
                        headerEnd - partStart, StandardCharsets.ISO_8859_1);
                int contentStart = headerEnd + 4;

                String name = null;
                String filename = null;
                for (String line : headers.split("\r\n")) {
                    if (line.toLowerCase().startsWith("content-disposition:")) {
                        Matcher mn = PATTERN_NAME.matcher(line);
                        if (mn.find()) name = mn.group(1);
                        Matcher mf = PATTERN_FILENAME.matcher(line);
                        if (mf.find()) filename = mf.group(1);
                    }
                }

                if (name == null) {
                    pos = partEnd;
                    continue;
                }

                if (filename != null && !filename.isEmpty()) {
                    int length = contentEnd - contentStart;
                    if (length > 0) {
                        result.fileData = new byte[length];
                        System.arraycopy(body, contentStart, result.fileData, 0, length);
                        result.fileName = filename;
                    }
                } else {
                    String value = new String(body, contentStart,
                            contentEnd - contentStart, StandardCharsets.UTF_8);
                    result.fields.put(name, value);
                }

                pos = partEnd;
            }

            return result;
        }

        private static int indexOf(byte[] haystack, byte[] needle, int from) {
            if (needle.length == 0) return from;
            outer:
            for (int i = from; i <= haystack.length - needle.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) continue outer;
                }
                return i;
            }
            return -1;
        }

        private static boolean startsWith(byte[] haystack, byte[] needle, int pos) {
            if (pos + needle.length > haystack.length) return false;
            for (int i = 0; i < needle.length; i++) {
                if (haystack[pos + i] != needle[i]) return false;
            }
            return true;
        }
    }
}