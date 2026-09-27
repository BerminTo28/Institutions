import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerExamenAdmin implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerExamenAdmin.class.getName());

    // ============================================================
    // CONSTANTES
    // ============================================================

    /** ✅ Liste blanche des statuts valides. */
    private static final List<String> STATUTS_VALIDES =
            List.of("PREVU", "EN_COURS", "TERMINE", "ANNULE");

    /** ✅ Répertoire racine des fichiers uploadés (anti path traversal). */
    private static final String UPLOAD_ROOT = "uploads/examens";

    /** ✅ Rôles considérés comme admin. */
    private static final Set<String> ROLES_ADMIN =
            Set.of("ADMINISTRATEUR", "ADMIN");

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // ============================================================
        // 1. SESSION
        // ============================================================
        HttpSession session = resolveSession(exchange);
        if (session == null) {
            LOGGER.fine("⛔ Session invalide → /login");
            redirect(exchange, "/login?error=Session+expirée");
            return;
        }

        String institutionId = readAttributeAsString(session, "institutionId");
        if (institutionId == null) {
            institutionId = readAttributeAsString(session, "institution_id");
        }
        String utilisateurId = readAttributeAsString(session, "userId");
        if (utilisateurId == null) {
            utilisateurId = readAttributeAsString(session, "username");
        }
        String role = readAttributeAsString(session, "role");

        if (institutionId == null || utilisateurId == null) {
            LOGGER.fine("⛔ Session incomplète → /login");
            redirect(exchange, "/login?error=Session+expirée");
            return;
        }

        // ============================================================
        // 2. VÉRIFICATION D'APPARTENANCE À L'INSTITUTION
        // ============================================================
        if (!utilisateurAppartientAInstitution(utilisateurId, institutionId)) {
            LOGGER.warning("⛔ Utilisateur " + utilisateurId
                    + " ∉ institution " + institutionId);
            redirect(exchange, "/dashboard?error=Accès+non+autorisé");
            return;
        }

        // ============================================================
        // 3. RÔLE
        // ============================================================
        final boolean isAdmin = role != null && ROLES_ADMIN.contains(role);

        // ============================================================
        // 4. 🔒 BLOQUER LES POST/PUT/DELETE NON-ADMIN
        // P
        if (!isAdmin && isWriteMethod(method)) {
            LOGGER.warning("⛔ Écriture refusée : utilisateur non-admin "
                    + utilisateurId + " → " + method + " " + path);
            ResponseUtil.sendError(exchange, 403,
                    "Seuls les administrateurs peuvent modifier les examens.");
            return;
        }

        // ============================================================
        // 5. PERMISSIONS
        // ============================================================
        AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
        if (acces == null
                || !acces.hasPermission(AccesAdmin.Module.EXAMEN, AccesAdmin.Action.READ)) {
            LOGGER.warning("⛔ Pas de READ sur EXAMEN : " + utilisateurId);
            redirect(exchange, "/dashboard?error=Accès+non+autorisé");
            return;
        }

        // ============================================================
        // 6. ROUTAGE
        // ============================================================
        try {
            if (path.startsWith("/admin/examens/download/")) {
                handleDownload(exchange, institutionId, utilisateurId, isAdmin);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, institutionId, utilisateurId, isAdmin, acces);
                return;
            }

            ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL HandlerExamenAdmin", e);
            String html = UIExamenAdmin.renderError(
                    "Erreur base de données : " + e.getMessage());
            safeSendHtml(exchange, 500, html);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur I/O HandlerExamenAdmin", e);
            String html = UIExamenAdmin.renderError(
                    "Erreur serveur : " + e.getMessage());
            safeSendHtml(exchange, 500, html);
        }
    }

    // ============================================================
    // GET — LISTE FILTRÉE
    // ============================================================
    private void handleGet(HttpExchange exchange, String institutionId,
                            String utilisateurId, boolean isAdmin,
                            AccesAdmin acces) throws IOException, SQLException {

        // ① Extraire et normaliser les filtres
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String codeCours = normaliserFiltre(params.get("codeCours"));
        String classe = normaliserFiltre(params.get("classe"));
        String periode = normaliserFiltre(params.get("periode"));
        String annee = normaliserFiltre(params.get("annee"));
        String statut = normaliserStatut(params.get("statut"));

        LOGGER.info("🔍 Filtres examens (" + (isAdmin ? "ADMIN" : "PROF") + ") : "
                + "codeCours='" + codeCours + "' "
                + "classe='" + classe + "' "
                + "periode='" + periode + "' "
                + "annee='" + annee + "' "
                + "statut='" + statut + "'");

        // ② Charger les examens SELON LE RÔLE
        List<Examen> examens;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ExamenManager manager = new ExamenManager(conn);

            if (isAdmin) {
                examens = manager.getExamensByInstitution(
                        institutionId, codeCours, classe, periode, annee, statut);
            } else {
                examens = manager.getExamensByProfesseurFiltres(
                        utilisateurId, institutionId,
                        codeCours, classe, periode, annee, statut);
            }
        }

        // ③ Enrichir les noms de matières
        enrichirNomsMatieres(examens, institutionId);

        // ④ Charger les listes pour les dropdowns (List<String> pour l'UI)
        List<String> codesCours = chargerCodesCours(institutionId);
        List<String> classes    = chargerNomsClasses(institutionId);
        List<String> periodes   = chargerNomsPeriodes(institutionId, annee);
        List<String> annees     = chargerNomsAnnees(institutionId);

        // ⑤ Permissions
        boolean canCreate = acces.hasPermission(
                AccesAdmin.Module.EXAMEN, AccesAdmin.Action.CREATE);
        boolean canUpdate = acces.hasPermission(
                AccesAdmin.Module.EXAMEN, AccesAdmin.Action.UPDATE);
        boolean canDelete = acces.hasPermission(
                AccesAdmin.Module.EXAMEN, AccesAdmin.Action.DELETE);

        // ⑥ Rendu — ordre EXACT de UIExamenAdmin.render
        String html = UIExamenAdmin.render(
                institutionId,
                examens,
                codesCours,
                classes,
                periodes,
                annees,
                STATUTS_VALIDES,
                codeCours,
                classe,
                periode,
                annee,
                statut,
                null,
                null,
                canCreate,
                canUpdate,
                canDelete
        );
        ResponseUtil.sendHtml(exchange, 200, html);

        LOGGER.fine(() -> "✅ " + examens.size() + " examen(s) pour "
                + (isAdmin ? "ADMIN " : "PROF ") + utilisateurId);
    }

    // ============================================================
    // TÉLÉCHARGEMENT
    // ============================================================
    private void handleDownload(HttpExchange exchange, String institutionId,
                                  String utilisateurId, boolean isAdmin)
            throws IOException, SQLException {

        String path = exchange.getRequestURI().getPath();
        String id = path.substring("/admin/examens/download/".length());

        if (id.isBlank()) {
            ResponseUtil.sendHtml(exchange, 400, "ID d'examen manquant.");
            return;
        }

        LOGGER.info(() -> "📥 Téléchargement : " + id + " ("
                + (isAdmin ? "ADMIN" : "PROF") + " " + utilisateurId + ")");

        // ✅ Charger l'examen SELON LE RÔLE
        Examen examen;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ExamenManager manager = new ExamenManager(conn);

            if (isAdmin) {
                examen = manager.getExamenByIdForAdmin(id, institutionId);
            } else {
                examen = manager.getExamenByIdForProfesseur(id, institutionId, utilisateurId);
            }
        }

        if (examen == null) {
            LOGGER.warning(() -> "❌ Examen introuvable : " + id
                    + " (inst=" + institutionId + " user=" + utilisateurId
                    + " admin=" + isAdmin + ")");
            ResponseUtil.sendHtml(exchange, 404, "Examen introuvable.");
            return;
        }

        if (examen.getFichierChemin() == null || examen.getFichierChemin().isBlank()) {
            ResponseUtil.sendHtml(exchange, 404, "Aucun fichier pour cet examen.");
            return;
        }

        // ✅ Anti path traversal
        File file = localiserFichierSecurise(examen.getFichierChemin());
        if (file == null || !file.exists() || !file.isFile()) {
            LOGGER.warning(() -> "Fichier physique introuvable : " + examen.getFichierChemin());
            ResponseUtil.sendHtml(exchange, 404,
                    "Le fichier physique est introuvable sur le serveur.");
            return;
        }

        String mimeType = (examen.getFichierType() != null
                && !examen.getFichierType().isBlank())
                ? examen.getFichierType()
                : "application/octet-stream";

        String nomFichier = (examen.getFichierNom() != null
                && !examen.getFichierNom().isBlank())
                ? examen.getFichierNom()
                : file.getName();

        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + nomFichier.replace("\"", "") + "\"");
        exchange.sendResponseHeaders(200, file.length());

        try (OutputStream os = exchange.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }

        LOGGER.info(() -> "✅ Téléchargement réussi : " + nomFichier);
    }

    // ============================================================
    // ✅ CHARGEMENT DES LISTES (List<String> pour l'UI)
    // ============================================================

    /**
     * ✅ Codes cours distincts de l'institution.
     */
    private List<String> chargerCodesCours(String institutionId) {
        try {
            return new MatiereData(institutionId).readAll().stream()
                    .map(Matiere::getCodeCours)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur chargement codes cours", e);
            return List.of();
        }
    }

    /**
     * ✅ Noms des classes de l'institution.
     */
    private List<String> chargerNomsClasses(String institutionId) {
        try {
            return new ClasseData().toutLister(institutionId).stream()
                    .map(Classe::getNomClasse)
                    .filter(nom -> nom != null && !nom.isBlank())
                    .distinct()
                    .toList();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement classes", e);
            return List.of();
        }
    }

    /**
     * ✅ Noms des périodes (filtrées par année si fournie).
     */
    private List<String> chargerNomsPeriodes(String institutionId, String annee) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PeriodeData periodeData = new PeriodeData(conn);

            List<Periode> periodes = (annee != null && !annee.isBlank())
                    ? periodeData.listByInstitutionAndAnnee(institutionId, annee)
                    : periodeData.listByInstitution(institutionId);

            return periodes.stream()
                    .map(Periode::getPeriode)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct()
                    .toList();

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement périodes", e);
            return List.of();
        }
    }

    /**
     * ✅ Noms des années académiques.
     */
    private List<String> chargerNomsAnnees(String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            return new AnneeAcademiqueData(conn).listByInstitution(institutionId).stream()
                    .map(AnneeAcademique::getAnneeAcademique)
                    .filter(a -> a != null && !a.isBlank())
                    .distinct()
                    .toList();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur chargement années", e);
            return List.of();
        }
    }

    // ============================================================
    // ENRICHISSEMENT DES NOMS DE MATIÈRES
    // ============================================================
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
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "Impossible d'enrichir les noms de matières", ex);
        }
    }

    // ============================================================
    // SÉCURITÉ FICHIERS
    // ============================================================
    private File localiserFichierSecurise(String cheminStocke) {
        if (cheminStocke == null || cheminStocke.isBlank()) return null;

        try {
            Path root = Paths.get(UPLOAD_ROOT).toAbsolutePath().normalize();
            Path cible = Paths.get(cheminStocke).toAbsolutePath().normalize();

            if (!cible.startsWith(root)) {
                LOGGER.warning(() -> "⛔ Tentative d'accès hors UPLOAD_ROOT : " + cheminStocke);
                return null;
            }

            File file = cible.toFile();
            if (file.exists() && file.isFile()) return file;

            // Fallback : chemin relatif depuis user.dir
            Path userDir = Paths.get(System.getProperty("user.dir"))
                    .toAbsolutePath().normalize();
            Path relatif = userDir.resolve(cheminStocke).normalize();
            if (relatif.startsWith(root) && relatif.toFile().exists()) {
                return relatif.toFile();
            }

            return null;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur localisation fichier", e);
            return null;
        }
    }

    // ============================================================
    // PERMISSIONS / SESSION
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

    private boolean utilisateurAppartientAInstitution(String utilisateurId,
                                                        String institutionId) {
        if (utilisateurId == null || institutionId == null) return false;

        String sql = "SELECT COUNT(*) FROM acces_admin "
                   + "WHERE utilisateur_id = ? AND institution_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, utilisateurId);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur vérification appartenance", e);
            return false;
        }
    }

    private HttpSession resolveSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;

            for (String cookie : cookieHeader.split(";")) {
                String trimmed = cookie.trim();
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;

                String name = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();

                if ("SESSION_ID".equals(name) && !value.isEmpty()) {
                    return SessionManager.getSession(value);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur résolution session", e);
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

    // ============================================================
    // UTILITAIRES
    // ============================================================

    /** ✅ Détecte les méthodes d'écriture. */
    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "DELETE".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);
    }

    private String normaliserFiltre(String valeur) {
        if (valeur == null) return "";
        String v = valeur.trim();
        if (v.isEmpty()
                || v.equalsIgnoreCase("toutes")
                || v.equalsIgnoreCase("tous")
                || v.equalsIgnoreCase("all")
                || v.equals("-")) {
            return "";
        }
        return v;
    }

    private String normaliserStatut(String valeur) {
        if (valeur == null || valeur.isBlank()) return "";
        String v = valeur.trim().toUpperCase();
        if (v.equals("TOUTES") || v.equals("TOUS") || v.equals("ALL")) return "";
        return STATUTS_VALIDES.contains(v) ? v : "";
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;

        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq <= 0) {
                try {
                    map.put(URLDecoder.decode(param, StandardCharsets.UTF_8), "");
                } catch (Exception ignored) {}
                continue;
            }
            try {
                String key = URLDecoder.decode(param.substring(0, eq),
                        StandardCharsets.UTF_8);
                String value = URLDecoder.decode(param.substring(eq + 1),
                        StandardCharsets.UTF_8);
                map.put(key, value);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private void safeSendHtml(HttpExchange exchange, int status, String html) {
        try {
            if (exchange.getResponseCode() != -1) return;
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Impossible d'envoyer la réponse HTML", e);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}