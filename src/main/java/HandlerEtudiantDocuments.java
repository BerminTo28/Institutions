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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerEtudiantDocuments implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEtudiantDocuments.class.getName());
    private static final Gson GSON = new Gson();

    /** ✅ Répertoire racine des documents (anti path traversal). */
    private static final String DOCUMENTS_ROOT = "./data/documents/";

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            String etudiantId = getEtudiantIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

            if (etudiantId == null || institutionId == null) {
                LOGGER.fine("⛔ Session invalide → /connexion");
                redirect(exchange, "/connexion");
                return;
            }

            // ✅ Vérification des droits READ sur DOCUMENTS
            if (!verifierDroitDocuments(etudiantId, institutionId)) {
                LOGGER.warning(() -> "⛔ Accès refusé aux documents pour " + etudiantId);
                redirect(exchange, "/etudiant/dashboard?error=acces_refuse_documents");
                return;
            }

            // ✅ Routage
            if ("GET".equalsIgnoreCase(method)) {

                // Page HTML
                if (path.equals("/etudiant/documents") || path.equals("/etudiant/documents/")) {
                    handlePage(exchange, etudiantId, institutionId);
                    return;
                }

                // API : liste
                if (path.contains("/api/list")) {
                    handleListApi(exchange, etudiantId, institutionId);
                    return;
                }

                // API : filtres
                if (path.contains("/api/matieres")) {
                    handleMatieresApi(exchange, etudiantId, institutionId);
                    return;
                }
                if (path.contains("/api/periodes")) {
                    handlePeriodesApi(exchange, etudiantId, institutionId);
                    return;
                }
                if (path.contains("/api/annees")) {
                    handleAnneesApi(exchange, etudiantId, institutionId);
                    return;
                }

                // Téléchargement
                if (path.contains("/api/download") || path.contains("/download")) {
                    handleDownload(exchange, etudiantId, institutionId);
                    return;
                }

                sendJsonError(exchange, 404, "Route non trouvée");
                return;
            }

            sendJsonError(exchange, 405, "Méthode non autorisée");

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O HandlerEtudiantDocuments", e);
            safeSendError(exchange, 500, "Erreur interne");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue HandlerEtudiantDocuments", e);
            safeSendError(exchange, 500, "Erreur interne");
        }
    }

    // ============================================================
    // VÉRIFICATION DES DROITS
    // ============================================================
    private boolean verifierDroitDocuments(String etudiantId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(etudiantId, institutionId);

            if (acces == null) {
                LOGGER.warning(() -> "❌ Aucun AccesEtudiant pour " + etudiantId
                        + " / " + institutionId);
                return false;
            }

            LOGGER.info(() -> "📋 AccesEtudiant : documents=" + acces.isDocuments()
                    + " notes=" + acces.isNotes()
                    + " bulletin=" + acces.isBulletin());

            if (!acces.isDocuments()) {
                LOGGER.warning(() -> "🚫 Droit documents = false pour " + etudiantId);
                return false;
            }
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification droits", e);
            return false;
        }
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    private void handlePage(HttpExchange exchange, String etudiantId,
                             String institutionId) throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String matiere = params.getOrDefault("matiere", "");
        String periode = params.getOrDefault("periode", "");
        String annee = params.getOrDefault("annee", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // ① Charger l'étudiant
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(etudiantId, institutionId);

            if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
                LOGGER.warning(() -> "⛔ Étudiant introuvable : " + etudiantId);
                String html = UIEtudiantDocuments.renderError("Étudiant introuvable.");
                ResponseUtil.sendHtml(exchange, 404, html);
                return;
            }

            String classeEtudiant = etudiant.getClasse() != null ? etudiant.getClasse() : "";
            String periodeEtudiant = etudiant.getPeriode() != null ? etudiant.getPeriode() : "";
            String anneeEtudiant = etudiant.getAnneeAcademique() != null
                    ? etudiant.getAnneeAcademique() : "";
            String nomComplet = etudiant.getNomComplet();

            // ② Matières de l'étudiant
            List<String> matieresEtudiant = getMatieresDeLEtudiant(
                    conn, institutionId, etudiantId, classeEtudiant, anneeEtudiant);

            LOGGER.info(() -> "📚 Étudiant " + etudiantId
                    + " | classe='" + classeEtudiant + "'"
                            + " | période='" + periodeEtudiant + "'"
                                    + " | année='" + anneeEtudiant + "'"
                                            + " | matières=" + matieresEtudiant);

            // ③ Vérifier que le filtre matière est autorisé
            String matiereEffective = matiere;
            if (!matiere.isEmpty() && !matieresEtudiant.contains(matiere)) {
                LOGGER.warning(() -> "⛔ Filtre matière non autorisé : " + matiere);
                matiereEffective = "";
            }

            // ④ Charger les documents accessibles (STRICT)
            DocumentManager docManager = new DocumentManager(conn);
            List<DocumentProfesseur> documents = docManager.listerDocumentsPourEtudiantStrict(
                    institutionId,
                    classeEtudiant,
                    periodeEtudiant,
                    anneeEtudiant,
                    matieresEtudiant,
                    matiereEffective.isEmpty() ? null : matiereEffective,
                    periode.isEmpty() ? null : periode,
                    annee.isEmpty() ? null : annee
            );

            // ⑤ Listes pour les filtres
            List<AnneeAcademique> annees = new AnneeAcademiqueData(conn)
                    .listByInstitution(institutionId);
            List<Periode> periodes = new PeriodeData(conn)
                    .listByInstitution(institutionId);

            // ⑥ Rendu HTML
            String html = UIEtudiantDocuments.render(
                    etudiantId,
                    institutionId,
                    nomComplet,
                    classeEtudiant,
                    annees,
                    periodes,
                    matieresEtudiant,
                    documents,
                    matiere,
                    periode,
                    annee,
                    null,
                    null
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL (handlePage)", e);
            safeSendHtml(exchange, 500,
                    UIEtudiantDocuments.renderError("Erreur base de données."));
        } catch (DocumentManager.DocumentException e) {
            LOGGER.log(Level.SEVERE, "Erreur Document", e);
            safeSendHtml(exchange, 500,
                    UIEtudiantDocuments.renderError("Erreur: " + e.getMessage()));
        }
    }

    // ============================================================
    // API : LISTE
    // ============================================================
    private void handleListApi(HttpExchange exchange, String etudiantId,
                                String institutionId) throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String matiere = params.getOrDefault("matiere", "");
        String periode = params.getOrDefault("periode", "");
        String annee = params.getOrDefault("annee", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(etudiantId, institutionId);

            if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
                sendJsonError(exchange, 404, "Étudiant introuvable");
                return;
            }

            String classeEtudiant = etudiant.getClasse() != null ? etudiant.getClasse() : "";
            String periodeEtudiant = etudiant.getPeriode() != null ? etudiant.getPeriode() : "";
            String anneeEtudiant = etudiant.getAnneeAcademique() != null
                    ? etudiant.getAnneeAcademique() : "";

            List<String> matieresEtudiant = getMatieresDeLEtudiant(
                    conn, institutionId, etudiantId, classeEtudiant, anneeEtudiant);

            // Vérifier que le filtre matière est autorisé
            String matiereEffective = matiere;
            if (!matiere.isEmpty() && !matieresEtudiant.contains(matiere)) {
                LOGGER.warning(() -> "⛔ Filtre matière non autorisé : " + matiere);
                matiereEffective = "";
            }

            DocumentManager docManager = new DocumentManager(conn);
            List<DocumentProfesseur> documents = docManager.listerDocumentsPourEtudiantStrict(
                    institutionId,
                    classeEtudiant,
                    periodeEtudiant,
                    anneeEtudiant,
                    matieresEtudiant,
                    matiereEffective.isEmpty() ? null : matiereEffective,
                    periode.isEmpty() ? null : periode,
                    annee.isEmpty() ? null : annee
            );

            sendJsonSuccess(exchange, documents);

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine("Client déconnecté (list API)");
                return;
            }
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur API list", e);
            safeSendJsonError(exchange, 500, e.getMessage());
        }
    }

    // ============================================================
    // API : FILTRES (matières, périodes, années)
    // ============================================================
    private void handleMatieresApi(HttpExchange exchange, String etudiantId,
                                     String institutionId) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(etudiantId, institutionId);

            if (etudiant == null) {
                sendJsonError(exchange, 404, "Étudiant introuvable");
                return;
            }

            String classe = etudiant.getClasse() != null ? etudiant.getClasse() : "";
            String annee = etudiant.getAnneeAcademique() != null
                    ? etudiant.getAnneeAcademique() : "";

            List<String> matieres = getMatieresDeLEtudiant(
                    conn, institutionId, etudiantId, classe, annee);

            sendJsonSuccess(exchange, matieres);

        } catch (SQLException e) {
            safeSendJsonError(exchange, 500, e.getMessage());
        }
    }

   /**
 * ✅ Périodes accessibles à l'étudiant.
 *    - Filtre sur l'année académique de l'étudiant.
 *    - Si l'étudiant a une période spécifique, la mettre en premier.
 */
private void handlePeriodesApi(HttpExchange exchange, String etudiantId,
                                 String institutionId) throws IOException {
    try (Connection conn = DatabaseManager.getInstance().getConnection()) {

        // ① Charger l'étudiant
        Etudiant etudiant = new EtudiantData(conn).read(etudiantId, institutionId);
        if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
            sendJsonError(exchange, 404, "Étudiant introuvable");
            return;
        }

        String anneeEtudiant = etudiant.getAnneeAcademique();

        // ② Charger les périodes de l'année de l'étudiant
        PeriodeData periodeData = new PeriodeData(conn);
        List<Periode> periodes;

        if (anneeEtudiant != null && !anneeEtudiant.isBlank()) {
            periodes = periodeData.listByInstitutionAndAnnee(institutionId, anneeEtudiant);
        } else {
            periodes = periodeData.listByInstitution(institutionId);
        }

        LOGGER.fine(() -> "📋 " + periodes.size() + " période(s) pour "
                + etudiantId + " (année=" + anneeEtudiant + ")");

        sendJsonSuccess(exchange, periodes);

    } catch (SQLException e) {
        LOGGER.log(Level.WARNING, "Erreur handlePeriodesApi", e);
        safeSendJsonError(exchange, 500, e.getMessage());
    }
}

   /**
 * ✅ Années académiques accessibles à l'étudiant.
 *    - Si l'étudiant a une année académique, on ne retourne QUE cette année.
 *    - Sinon, on retourne toutes les années de l'institution.
 */
private void handleAnneesApi(HttpExchange exchange, String etudiantId,
                               String institutionId) throws IOException {
    try (Connection conn = DatabaseManager.getInstance().getConnection()) {

        // ① Charger l'étudiant
        Etudiant etudiant = new EtudiantData(conn).read(etudiantId, institutionId);
        if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
            sendJsonError(exchange, 404, "Étudiant introuvable");
            return;
        }

        String anneeEtudiant = etudiant.getAnneeAcademique();

        List<AnneeAcademique> annees;

        if (anneeEtudiant != null && !anneeEtudiant.isBlank()) {
            // ✅ Ne retourner que l'année de l'étudiant
            AnneeAcademique annee = new AnneeAcademiqueData(conn)
                    .readByInstitutionAndAnnee(institutionId, anneeEtudiant);

            annees = (annee != null)
                    ? List.of(annee)
                    : new ArrayList<>();
        } else {
            annees = new AnneeAcademiqueData(conn).listByInstitution(institutionId);
        }

        LOGGER.fine(() -> "📋 " + annees.size() + " année(s) pour "
                + etudiantId + " (année étudiant=" + anneeEtudiant + ")");

        sendJsonSuccess(exchange, annees);

    } catch (SQLException e) {
        LOGGER.log(Level.WARNING, "Erreur handleAnneesApi", e);
        safeSendJsonError(exchange, 500, e.getMessage());
    }
}

    // ============================================================
    // TÉLÉCHARGEMENT (STRICT)
    // ============================================================
    private void handleDownload(HttpExchange exchange, String etudiantId,
                                 String institutionId) throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String id = params.get("id");

        if (id == null || id.isBlank()) {
            sendJsonError(exchange, 400, "ID manquant");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // ① Charger l'étudiant
            EtudiantData etudiantData = new EtudiantData(conn);
            Etudiant etudiant = etudiantData.read(etudiantId, institutionId);

            if (etudiant == null || !institutionId.equals(etudiant.getInstitutionId())) {
                sendJsonError(exchange, 403, "Accès non autorisé");
                return;
            }

            // ② Charger le document (institution + id)
            DocumentManager docManager = new DocumentManager(conn);
            DocumentProfesseur doc = docManager.obtenirDocumentParIdEtInstitution(id, institutionId);

            if (doc == null) {
                sendJsonError(exchange, 404, "Document introuvable");
                return;
            }

            // ③ Vérifier les droits d'accès
            AccesEtudiantData accesData = new AccesEtudiantData(conn);
            AccesEtudiant acces = accesData.getByEtudiant(etudiantId, institutionId);
            if (acces == null || !acces.isDocuments()) {
                sendJsonError(exchange, 403, "Accès non autorisé");
                return;
            }

            // ============================================================
            // ④ VÉRIFICATIONS STRICTES
            // ============================================================

            String classeEtudiant = etudiant.getClasse();
            String periodeEtudiant = etudiant.getPeriode();
            String anneeEtudiant = etudiant.getAnneeAcademique();

            // Classe
            String docClasse = doc.getClasse();
            boolean classeMatch = docClasse != null && classeEtudiant != null
                    && docClasse.equalsIgnoreCase(classeEtudiant);

            if (!doc.isEstPublic() && !classeMatch) {
                LOGGER.warning(() -> "⛔ Refus classe : doc='" + docClasse
                        + "' étudiant='" + classeEtudiant + "'");
                sendJsonError(exchange, 403, "Ce document n'est pas accessible.");
                return;
            }

            // Année académique
            String docAnnee = doc.getAnneeAcademique();
            if (docAnnee != null && !docAnnee.isBlank()
                    && anneeEtudiant != null && !anneeEtudiant.isBlank()
                    && !docAnnee.equals(anneeEtudiant)) {
                LOGGER.warning(() -> "⛔ Refus année : doc='" + docAnnee
                        + "' étudiant='" + anneeEtudiant + "'");
                sendJsonError(exchange, 403,
                        "Ce document n'est pas accessible pour votre année.");
                return;
            }

            // Période
            String docPeriode = doc.getPeriode();
            if (docPeriode != null && !docPeriode.isBlank()
                    && periodeEtudiant != null && !periodeEtudiant.isBlank()
                    && !docPeriode.equals(periodeEtudiant)) {
                LOGGER.warning(() -> "⛔ Refus période : doc='" + docPeriode
                        + "' étudiant='" + periodeEtudiant + "'");
                sendJsonError(exchange, 403,
                        "Ce document n'est pas accessible pour votre période.");
                return;
            }

            // Matière
            List<String> matieresEtudiant = getMatieresDeLEtudiant(
                    conn, institutionId, etudiantId, classeEtudiant, anneeEtudiant);

            String docMatiere = doc.getMatiere();
            boolean matiereMatch = docMatiere != null
                    && matieresEtudiant.contains(docMatiere);

            if (!matiereMatch) {
                LOGGER.warning(() -> "⛔ Refus matière : doc='" + docMatiere
                        + "' étudiant=" + matieresEtudiant);
                sendJsonError(exchange, 403, "Ce document ne concerne pas vos cours.");
                return;
            }

            // ============================================================
            // ⑤ Toutes les vérifications OK → servir
            // ============================================================
            LOGGER.info(() -> "✅ Document " + id + " autorisé pour " + etudiantId);

            // Lien externe
            if (doc.getUrl() != null && !doc.getUrl().isBlank()) {
                redirect(exchange, doc.getUrl());
                return;
            }

            // Fichier physique
            String filePath = doc.getFichierChemin();
            if (filePath == null || filePath.isBlank()) {
                sendJsonError(exchange, 404, "Aucun fichier associé");
                return;
            }

            // Anti path traversal
            Path path = localiserFichierSecurise(filePath);
            if (path == null || !Files.exists(path)) {
                LOGGER.warning(() -> "⛔ Fichier introuvable/inaccessible : " + filePath);
                sendJsonError(exchange, 404, "Fichier introuvable sur le serveur");
                return;
            }

            // Envoi
            byte[] data = Files.readAllBytes(path);
            String nomFichier = doc.getFichierNom() != null
                    ? doc.getFichierNom().replace("\"", "")
                    : path.getFileName().toString();

            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + nomFichier + "\"");
            exchange.sendResponseHeaders(200, data.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
                os.flush();
            }

            LOGGER.info(() -> "✅ Téléchargement réussi : " + nomFichier);

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine("Client déconnecté (download)");
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O téléchargement", e);
            safeSendJsonError(exchange, 500, "Erreur interne");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur téléchargement", e);
            safeSendJsonError(exchange, 500, "Erreur interne : " + e.getMessage());
        }
    }

    // ============================================================
    // MATIÈRES DE L'ÉTUDIANT
    // ============================================================
    /**
     * ✅ Récupère les matières (codes cours) accessibles à l'étudiant.
     *    Union de :
     *    - Table `matieres` (classe_matiere = classe étudiant)
     *    - Table `professeur_matieres` (classe étudiant)
     *    - Table `notes` (l'étudiant a une note)
     */
  /**
 * ✅ Récupère les matières (codes cours) accessibles à l'étudiant.
 *    Union de :
 *    - Table `matieres` (classe_matiere = classe étudiant + annee_academique)
 *    - Table `professeur_matieres` (classe étudiant + annee_academique)
 *    - Table `notes` (l'étudiant a une note + annee_academique)
 *
 * @param institutionId     institution de l'étudiant
 * @param etudiantId        identifiant de l'étudiant
 * @param classe            classe de l'étudiant
 * @param anneeAcademique   année académique de l'étudiant (filtre)
 */
private List<String> getMatieresDeLEtudiant(Connection conn,
                                              String institutionId,
                                              String etudiantId,
                                              String classe,
                                              String anneeAcademique)
        throws SQLException {

    if (institutionId == null || institutionId.isBlank()
            || classe == null || classe.isBlank()) {
        LOGGER.warning("getMatieresDeLEtudiant : institutionId ou classe manquant");
        return new ArrayList<>();
    }

    Set<String> matieres = new LinkedHashSet<>();
    boolean filtrerAnnee = (anneeAcademique != null && !anneeAcademique.isBlank());

    // ============================================================
    // ① Table `matieres`
    // ============================================================
    StringBuilder sqlMatieres = new StringBuilder(
            "SELECT DISTINCT code_cours FROM " + MigrationManager.TABLE_MATIERES
            + " WHERE institution_id = ?"
            + "   AND LOWER(classe_matiere) = LOWER(?)");
    if (filtrerAnnee) {
        sqlMatieres.append(" AND annee_academique = ?");
    }

    try (PreparedStatement ps = conn.prepareStatement(sqlMatieres.toString())) {
        int idx = 1;
        ps.setString(idx++, institutionId);
        ps.setString(idx++, classe);
        if (filtrerAnnee) {
            ps.setString(idx, anneeAcademique);
        }
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code_cours");
                if (code != null && !code.isBlank()) matieres.add(code);
            }
        }
    }

    // ============================================================
    // ② Table `professeur_matieres`
    // ============================================================
    StringBuilder sqlProfs = new StringBuilder(
            "SELECT DISTINCT code_cours FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
            + " WHERE institution_id = ?"
            + "   AND LOWER(classe) = LOWER(?)");
    if (filtrerAnnee) {
        sqlProfs.append(" AND annee_academique = ?");
    }

    try (PreparedStatement ps = conn.prepareStatement(sqlProfs.toString())) {
        int idx = 1;
        ps.setString(idx++, institutionId);
        ps.setString(idx++, classe);
        if (filtrerAnnee) {
            ps.setString(idx, anneeAcademique);
        }
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code_cours");
                if (code != null && !code.isBlank()) matieres.add(code);
            }
        }
    }

    // ============================================================
    // ③ Table `notes` (l'étudiant a une note)
    // ============================================================
    StringBuilder sqlNotes = new StringBuilder(
            "SELECT DISTINCT code_cours FROM " + MigrationManager.TABLE_NOTES
            + " WHERE institution_id = ?"
            + "   AND numero_etudiant = ?");
    if (filtrerAnnee) {
        sqlNotes.append(" AND annee_academique = ?");
    }

    try (PreparedStatement ps = conn.prepareStatement(sqlNotes.toString())) {
        int idx = 1;
        ps.setString(idx++, institutionId);
        ps.setString(idx++, etudiantId);
        if (filtrerAnnee) {
            ps.setString(idx, anneeAcademique);
        }
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code_cours");
                if (code != null && !code.isBlank()) matieres.add(code);
            }
        }
    }

    final int total = matieres.size();
    LOGGER.fine(() -> "📚 " + total + " matière(s) pour " + etudiantId
            + " (classe=" + classe
            + (filtrerAnnee ? ", année=" + anneeAcademique : ", toutes années") + ")");

    return new ArrayList<>(matieres);
}
    // ============================================================
    // SÉCURITÉ FICHIERS
    // ============================================================
    /**
     * ✅ Localise un fichier de manière sécurisée (anti path traversal).
     */
    private Path localiserFichierSecurise(String cheminStocke) {
        if (cheminStocke == null || cheminStocke.isBlank()) return null;

        try {
            Path root = Paths.get(DOCUMENTS_ROOT).toAbsolutePath().normalize();
            Path cible = Paths.get(cheminStocke).toAbsolutePath().normalize();

            if (!cible.startsWith(root)) {
                LOGGER.warning(() -> "⛔ Tentative d'accès hors " + DOCUMENTS_ROOT
                        + " : " + cheminStocke);
                return null;
            }

            return cible;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur localisation fichier", e);
            return null;
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getEtudiantIdFromSession(HttpExchange exchange) {
        HttpSession session = resolveSession(exchange);
        if (session == null) return null;

        String userId = readAttributeAsString(session, "userId");
        if (userId == null) userId = readAttributeAsString(session, "user_id");
        if (userId == null) userId = readAttributeAsString(session, "username");
        if (userId == null) userId = readAttributeAsString(session, "email");
        return userId;
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        HttpSession session = resolveSession(exchange);
        if (session == null) return null;

        String instId = readAttributeAsString(session, "institutionId");
        if (instId == null) instId = readAttributeAsString(session, "institution_id");
        return instId;
    }

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

    // ============================================================
    // JSON / HTTP HELPERS
    // ============================================================
    private void sendJsonError(HttpExchange exchange, int status, String message)
            throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        String json = GSON.toJson(error);
        ResponseUtil.sendJson(exchange, status, json);
    }

    private void sendJsonSuccess(HttpExchange exchange, Object data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        String json = GSON.toJson(response);
        ResponseUtil.sendJson(exchange, 200, json);
    }

    private void safeSendJsonError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            sendJsonError(exchange, status, message);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Impossible d'envoyer l'erreur JSON", e);
        }
    }

    private void safeSendHtml(HttpExchange exchange, int status, String html) {
        try {
            if (exchange.getResponseCode() != -1) return;
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Impossible d'envoyer le HTML", e);
        }
    }

    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            ResponseUtil.sendError(exchange, status, message);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Impossible d'envoyer l'erreur", e);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    // ============================================================
    // PARSING
    // ============================================================
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

    // ============================================================
    // DÉTECTION ERREUR RÉSEAU
    // ============================================================
    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null && e.getCause() != null) {
            msg = e.getCause().getMessage();
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