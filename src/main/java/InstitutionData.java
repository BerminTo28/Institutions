import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/**
 * Accès aux données de la table {@code institutions}.
 *
 * <p><b>RÔLE</b> : la table {@code institutions} est la <b>table racine</b> du modèle.
 * Elle contient la liste de TOUTES les institutions connues, locales et distantes.
 * C'est la SEULE table qui est synchronisée GLOBALEMENT (sans filtre
 * {@code institution_id}), afin de permettre la découverte inter-machines.</p>
 *
 * <p><b>ISOLATION</b> : pour toutes les autres tables, le filtre
 * {@code institution_id} s'applique (voir SyncService).</p>
 *
 * <p><b>ROBUSTESSE</b> :
 * <ul>
 *   <li>Écriture sur LOCAL prioritaire (fallback REMOTE)</li>
 *   <li>Enregistrement systématique dans l'outbox (LOCAL + REMOTE)</li>
 *   <li>Fusion intelligente LOCAL + REMOTE (par {@code last_modified})</li>
 *   <li>Auto-réparation : sync forcée si divergence détectée</li>
 *   <li>Timeouts SQL sur toutes les requêtes</li>
 *   <li>Gestion propre des transactions</li>
 * </ul>
 */
public class InstitutionData {

    private static final Logger LOGGER = Logger.getLogger(InstitutionData.class.getName());

    private static final String TABLE = MigrationManager.TABLE_INSTITUTIONS;

    /** Timeout SQL général — 60s pour supporter les verrous concurrents. */
    private static final int QUERY_TIMEOUT_SECONDS = 60;

    /** Timeout écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    /** Nombre max d'institutions avant avertissement. */
    private static final int SEUIL_ALERTE_INSTITUTIONS = 100;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>)
                            (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .create();

    private static final String COLONNES =
            "institution_id, nom_institution, sigle_institution, devise_institution, type_institution, " +
            "niveau_institution, categorie_institution, logo_institution, qrcode_institution, " +
            "adresse_institution, code_postal_institution, commune_institution, departement_institution, " +
            "pays_institution, mail_primaire, mail_secondaire, telephone_primaire, telephone_secondaire, " +
            "site_web_institution, smtp_password, responsable_institution, poste_responsable, " +
            "mail_responsable, telephone_responsable, moyenne_passage, systeme_educatif, " +
            "statut_institution, statut_validation, date_creation, last_modified, source";

    private final Connection connection;

    public InstitutionData() {
        this.connection = null;
    }

    public InstitutionData(Connection connection) {
        this.connection = connection;
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

    private boolean shouldClose(Connection conn) {
        return conn != connection;
    }

    private void closeIfNeeded(Connection conn) {
        if (shouldClose(conn) && conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
    }

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
    }

    private String getSource(Connection conn) {
        if (conn == null) return "LOCAL";
        try {
            String url = conn.getMetaData().getURL();
            String localUrl  = db().getDataSource().getSecondaryUrl();
            String remoteUrl = db().getDataSource().getPrimaryUrl();

            if (localUrl != null && localUrl.equals(url))  return "LOCAL";
            if (remoteUrl != null && remoteUrl.equals(url)) return "REMOTE";
        } catch (SQLException e) {
            LOGGER.fine("Source indéterminée, fallback LOCAL");
        }
        return "LOCAL";
    }

    /**
     * ✅ Retourne une connexion d'écriture en priorité LOCAL, puis REMOTE.
     */
    private Connection getPreferredWriteConnection() {
        DatabaseManager m = db();

        try {
            if (m.isLocalConfigured() && m.isLocalHealthy()) {
                Connection c = m.getLocalConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LOCAL indisponible pour écriture", e);
        }

        try {
            if (m.isRemoteConfigured() && m.isRemoteHealthy()
                    && m.isMigrationDistanteTerminee()) {
                Connection c = m.getRemoteConnection();
                if (c != null && !c.isClosed() && isConnectionAlive(c)) return c;
                closeIfNeeded(c);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "REMOTE indisponible pour écriture", e);
        }

        try {
            Connection c = m.getLocalConnection();
            if (c != null && !c.isClosed()) {
                LOGGER.warning("⚠️ Aucune base saine — tentative directe sur LOCAL");
                return c;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'obtenir une connexion d'écriture", e);
        }

        return null;
    }

    // =========================================================
    // CREATE
    // =========================================================
    public boolean create(Institution institution) throws SQLException {
        if (institution == null || institution.getInstitutionId() == null) {
            throw new IllegalArgumentException("Institution invalide.");
        }

        final String institutionId = institution.getInstitutionId();
        LOGGER.info(() -> "📥 Création institution : " + institutionId);

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer institution "
                    + institutionId);
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = creerSansSync(conn, institution, usedSource);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création institution sur " + srcErr
                            + " : " + institutionId);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            // ✅ Enregistrement outbox + sync
            enregistrerDansOutbox(institution, "INSERT", usedSource);
            forcerSynchronisationImmediate(TABLE);
            final String src = usedSource;
            LOGGER.info(() -> "✅ Institution créée sur " + src
                    + " + outbox : " + institutionId);
        }
        return ok;
    }

    private boolean creerSansSync(Connection conn, Institution institution, String source) {
        String sql = "INSERT INTO " + TABLE + " (" +
                "institution_id, nom_institution, sigle_institution, devise_institution, type_institution, " +
                "niveau_institution, categorie_institution, logo_institution, qrcode_institution, " +
                "adresse_institution, code_postal_institution, commune_institution, departement_institution, " +
                "pays_institution, mail_primaire, mail_secondaire, telephone_primaire, telephone_secondaire, " +
                "site_web_institution, smtp_password, responsable_institution, poste_responsable, " +
                "mail_responsable, telephone_responsable, moyenne_passage, systeme_educatif, " +
                "statut_institution, statut_validation, date_creation, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institution.getInstitutionId());
            ps.setString(2, institution.getNomInstitution());
            ps.setString(3, institution.getSigleInstitution());
            ps.setString(4, institution.getDeviseInstitution());
            ps.setString(5, institution.getTypeInstitution());
            ps.setString(6, institution.getNiveauInstitution());
            ps.setString(7, institution.getCategorieInstitution());
            ps.setString(8, institution.getLogoInstitution());
            ps.setString(9, institution.getQRCodeInstitution());
            ps.setString(10, institution.getAdresseInstitution());
            ps.setString(11, institution.getCodePostalInstitution());
            ps.setString(12, institution.getCommuneInstitution());
            ps.setString(13, institution.getDepartementInstitution());
            ps.setString(14, institution.getPaysInstitution());
            ps.setString(15, institution.getMailPrimaire());
            ps.setString(16, institution.getMailSecondaire());
            ps.setString(17, institution.getTelephonePrimaireInstitution());
            ps.setString(18, institution.getTelephoneSecondaireInstitution());
            ps.setString(19, institution.getSiteWebInstitution());
            ps.setString(20, institution.getSmtpPasswordInstitution());
            ps.setString(21, institution.getResponsableInstitution());
            ps.setString(22, institution.getPosteResponsableInstitution());
            ps.setString(23, institution.getMailResponsableInstitution());
            ps.setString(24, institution.getTelephoneResponsableInstitution());
            ps.setString(25, institution.getMoyenneDePassageInstitution());
            ps.setString(26, institution.getSystemeEducatifInstitution());
            ps.setString(27, institution.getStatutInstitution());
            ps.setString(28, institution.getStatutValidation());
            ps.setString(29, institution.getDateCreationInstitution());
            ps.setString(30, source != null ? source : getSource(conn));

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur insertion institution "
                    + institution.getInstitutionId() + " sur " + source, e);
            return false;
        }
    }

    // =========================================================
    // CREATE AVEC ADMIN — transactionnel LOCAL
    // =========================================================
    public boolean createWithAdminPermissions(Institution institution) throws SQLException {
        if (institution == null || institution.getInstitutionId() == null) {
            throw new IllegalArgumentException("Institution invalide.");
        }

        final String institutionId = institution.getInstitutionId();
        LOGGER.info(() -> "📥 Création institution + admin : " + institutionId);

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = createInstitutionAndAdminOnConnection(conn, institution, usedSource);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création institution+admin sur " + srcErr);
        } finally {
            closeIfNeeded(conn);
        }

        if (ok) {
            enregistrerDansOutbox(institution, "INSERT", usedSource);
            forcerSynchronisationImmediate(TABLE + "+acces_admin");
            final String src = usedSource;
            LOGGER.info(() -> "✅ Institution + admin créés sur " + src
                    + " + outbox : " + institutionId);
        }
        return ok;
    }

  private boolean createInstitutionAndAdminOnConnection(Connection conn,
                                                        Institution institution,
                                                        String source) {
    boolean autoCommitOriginal = true;
    try {
        autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);

        // ① Institution
        if (!creerSansSync(conn, institution, source)) {
            conn.rollback();
            LOGGER.severe(() -> "❌ Rollback : échec création institution "
                    + institution.getInstitutionId());
            return false;
        }

        // ② ✅ Identifiant admin (résolu sans réassignation)
        final String adminId = resolveAdminId(institution);

        if (adminId == null || adminId.isBlank()) {
            conn.rollback();
            LOGGER.warning(() -> "⚠️ Rollback : aucun email admin pour "
                    + institution.getInstitutionId());
            return false;
        }

        // ③ Mot de passe
        final String motDePasse = institution.getMotDePasseInstitution();
        if (motDePasse == null || motDePasse.isBlank()) {
            conn.rollback();
            LOGGER.warning(() -> "⚠️ Rollback : mot de passe admin manquant pour " + adminId);
            return false;
        }

        // ④ Nom / prénom (résolus sans réassignation)
        final String nomAdmin    = resolveNomAdmin(institution);
        final String prenomAdmin = resolvePrenomAdmin(institution);

        // ⑤ AccesAdmin
        AccesAdmin acces = new AccesAdmin();
        acces.grantAll();

        // ⑥ Utilisateur
        Utilisateur utilisateur = new Utilisateur(
                adminId, nomAdmin, prenomAdmin, adminId, 5,
                acces, Collections.emptyList());

        // ⑦ Persister acces_admin sur la MÊME connexion
        UtilisateurData userDao = new UtilisateurData(conn);
        boolean userCreated = userDao.create(conn, utilisateur, motDePasse,
                institution.getInstitutionId());

        if (!userCreated) {
            conn.rollback();
            LOGGER.warning(() -> "⚠️ Rollback : échec création admin " + adminId);
            return false;
        }

        conn.commit();

        final String instIdFinal = institution.getInstitutionId();
        LOGGER.info(() -> "✅ Institution + admin créés sur " + source
                + " : " + instIdFinal + " / " + adminId);
        return true;

    } catch (SQLException e) {
        try { conn.rollback(); } catch (SQLException ignored) {}
        LOGGER.log(Level.SEVERE, "❌ Erreur création institution+admin", e);
        return false;
    } finally {
        try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
    }
}

// ============================================================
// HELPERS — Résolution sans réassignation
// ============================================================

/**
 * ✅ Résout l'identifiant admin (mailPrimaire, sinon mailResponsable).
 *    Aucune réassignation → la valeur retournée peut être `final`.
 */
private String resolveAdminId(Institution institution) {
    String mail = institution.getMailPrimaire();
    if (mail != null && !mail.isBlank()) return mail;

    String responsable = institution.getMailResponsableInstitution();
    if (responsable != null && !responsable.isBlank()) return responsable;

    return null;
}

/**
 * ✅ Résout le nom de l'admin (responsable, sinon défaut).
 */
private String resolveNomAdmin(Institution institution) {
    String nom = institution.getResponsableInstitution();
    return (nom != null && !nom.isBlank()) ? nom : "Administrateur";
}


private String resolvePrenomAdmin(Institution institution) {
    String prenom = institution.getPosteResponsableInstitution();
    return (prenom != null && !prenom.isBlank()) ? prenom : "Institution";
}

    // =========================================================
    // READ ALL — Fusion LOCAL + REMOTE
    // =========================================================
    /**
     * ✅ Lit TOUTES les institutions (LOCAL + REMOTE) et fusionne.
     *    <p>La table {@code institutions} est <b>globale</b> : pas de filtre
     *    {@code institution_id}. Une institution locale doit pouvoir voir
     *    toutes les autres institutions (pour la découverte inter-machines).</p>
     */
    public List<Institution> readAll() {
        Map<String, Institution> byId = new HashMap<>();
        Set<String> sourcesUtilisees = new HashSet<>();

        // 1) LOCAL
        try (Connection localConn = db().getLocalConnection()) {
            List<Institution> localInstitutions = readAllFromConnection(localConn);
            for (Institution inst : localInstitutions) {
                if (inst.getInstitutionId() != null) {
                    byId.put(inst.getInstitutionId(), inst);
                }
            }
            sourcesUtilisees.add("LOCAL");
            final int n = localInstitutions.size();
            LOGGER.fine(() -> "📊 readAll() — LOCAL : " + n + " institutions");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "⚠️ Lecture LOCAL échouée dans readAll()", e);
        }

        // 2) REMOTE — ne remplace que si plus récent
        try (Connection remoteConn = db().getRemoteConnection()) {
            List<Institution> remoteInstitutions = readAllFromConnection(remoteConn);
            int replaced = 0, added = 0;
            for (Institution inst : remoteInstitutions) {
                if (inst.getInstitutionId() == null) continue;
                Institution existing = byId.get(inst.getInstitutionId());
                if (existing == null) {
                    byId.put(inst.getInstitutionId(), inst);
                    added++;
                } else if (isMoreRecent(inst, existing)) {
                    byId.put(inst.getInstitutionId(), inst);
                    replaced++;
                }
            }
            sourcesUtilisees.add("REMOTE");
            final int a = added, r = replaced;
            LOGGER.fine(() -> "📊 readAll() — REMOTE : " + remoteInstitutions.size()
                    + " institutions (" + a + " ajoutées, " + r + " plus récentes)");
        } catch (SQLException e) {
            LOGGER.fine(() -> "Lecture REMOTE non disponible : " + e.getMessage());
        }

        List<Institution> result = new ArrayList<>(byId.values());
        result.sort(Comparator.comparing(
                Institution::getNomInstitution,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        final int total = result.size();
        LOGGER.fine(() -> "✅ readAll() — Fusion finale : " + total
                + " institutions (sources=" + sourcesUtilisees + ")");

        // ✅ Alerte si trop d'institutions
        if (total > SEUIL_ALERTE_INSTITUTIONS) {
            LOGGER.warning(() -> "⚠️ " + total + " institutions — vérifier la sync");
        }

        return result;
    }

    /**
     * ✅ Retourne uniquement les IDs des institutions.
     *    Utilisé pour le diagnostic de divergence.
     */
    public Set<String> readAllIds() {
        Set<String> ids = new HashSet<>();
        String sql = "SELECT institution_id FROM " + TABLE;

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(1);
                        if (id != null) ids.add(id);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture IDs institutions", e);
        } finally {
            closeIfNeeded(conn);
        }
        return ids;
    }

    private boolean isMoreRecent(Institution a, Institution b) {
        LocalDateTime ta = a.getLastModified();
        LocalDateTime tb = b.getLastModified();
        if (ta == null) return false;
        if (tb == null) return true;
        return ta.isAfter(tb);
    }

    private List<Institution> readAllFromConnection(Connection conn) throws SQLException {
        List<Institution> institutions = new ArrayList<>();
        if (conn == null || conn.isClosed()) return institutions;

        String sql = "SELECT " + COLONNES + " FROM " + TABLE + " ORDER BY nom_institution";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    institutions.add(mapInstitution(rs));
                }
            }
        }
        return institutions;
    }

    // =========================================================
    // READ ALL PAGINÉ
    // =========================================================
    public List<Institution> readAll(int limit, int offset) {
        List<Institution> institutions = new ArrayList<>();
        String sql = "SELECT " + COLONNES + " FROM " + TABLE
                + " ORDER BY nom_institution LIMIT ? OFFSET ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        institutions.add(mapInstitution(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture toutes institutions (paginée)", e);
        } finally {
            closeIfNeeded(conn);
        }
        return institutions;
    }

    // =========================================================
    // LECTURE PAR ID
    // =========================================================
    public Institution readByInstitutionId(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) return null;

        String sql = "SELECT " + COLONNES + " FROM " + TABLE + " WHERE institution_id = ?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapInstitution(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture institution par institution_id", e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public Institution readByIdOrInstitutionId(String institutionId) throws SQLException {
        return readByInstitutionId(institutionId);
    }

    // ❌ readByInternalId() SUPPRIMÉ — la table n'a pas de colonne `id`

    // =========================================================
    // UPDATE
    // =========================================================
    public boolean update(Institution institution) throws SQLException {
        if (institution == null || institution.getInstitutionId() == null) {
            throw new IllegalArgumentException("Institution invalide.");
        }

        final String institutionId = institution.getInstitutionId();
        LOGGER.info(() -> "📥 Mise à jour institution : " + institutionId);

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, institution, usedSource);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update institution sur " + srcErr
                            + " : " + institutionId);
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) {
            enregistrerDansOutbox(institution, "UPDATE", usedSource);
            forcerSynchronisationImmediate(TABLE);
            final String src = usedSource;
            LOGGER.info(() -> "✅ Institution mise à jour sur " + src
                    + " + outbox : " + institutionId);
        }
        return updated;
    }

    private boolean updateSansSync(Connection conn, Institution institution, String source) {
        String sql = "UPDATE " + TABLE + " SET " +
                "nom_institution=?, sigle_institution=?, devise_institution=?, type_institution=?, " +
                "niveau_institution=?, categorie_institution=?, logo_institution=?, qrcode_institution=?, " +
                "adresse_institution=?, code_postal_institution=?, commune_institution=?, departement_institution=?, " +
                "pays_institution=?, mail_primaire=?, mail_secondaire=?, telephone_primaire=?, telephone_secondaire=?, " +
                "site_web_institution=?, smtp_password=?, responsable_institution=?, poste_responsable=?, " +
                "mail_responsable=?, telephone_responsable=?, moyenne_passage=?, systeme_educatif=?, " +
                "statut_institution=?, statut_validation=?, date_creation=?, source=? " +
                "WHERE institution_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institution.getNomInstitution());
            ps.setString(2, institution.getSigleInstitution());
            ps.setString(3, institution.getDeviseInstitution());
            ps.setString(4, institution.getTypeInstitution());
            ps.setString(5, institution.getNiveauInstitution());
            ps.setString(6, institution.getCategorieInstitution());
            ps.setString(7, institution.getLogoInstitution());
            ps.setString(8, institution.getQRCodeInstitution());
            ps.setString(9, institution.getAdresseInstitution());
            ps.setString(10, institution.getCodePostalInstitution());
            ps.setString(11, institution.getCommuneInstitution());
            ps.setString(12, institution.getDepartementInstitution());
            ps.setString(13, institution.getPaysInstitution());
            ps.setString(14, institution.getMailPrimaire());
            ps.setString(15, institution.getMailSecondaire());
            ps.setString(16, institution.getTelephonePrimaireInstitution());
            ps.setString(17, institution.getTelephoneSecondaireInstitution());
            ps.setString(18, institution.getSiteWebInstitution());
            ps.setString(19, institution.getSmtpPasswordInstitution());
            ps.setString(20, institution.getResponsableInstitution());
            ps.setString(21, institution.getPosteResponsableInstitution());
            ps.setString(22, institution.getMailResponsableInstitution());
            ps.setString(23, institution.getTelephoneResponsableInstitution());
            ps.setString(24, institution.getMoyenneDePassageInstitution());
            ps.setString(25, institution.getSystemeEducatifInstitution());
            ps.setString(26, institution.getStatutInstitution());
            ps.setString(27, institution.getStatutValidation());
            ps.setString(28, institution.getDateCreationInstitution());
            ps.setString(29, source != null ? source : getSource(conn));
            ps.setString(30, institution.getInstitutionId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur update institution sur " + source, e);
            return false;
        }
    }

    // =========================================================
    // DELETE
    // =========================================================
    public boolean delete(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId invalide.");
        }

        LOGGER.info(() -> "📥 Suppression institution : " + institutionId);

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean autoCommitOriginal;
        String usedSource;
        try {
            usedSource = getSource(conn);
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                boolean ok = supprimerDansBase(conn, institutionId, usedSource);
                conn.commit();

                if (ok) {
                    enregistrerDeleteDansOutbox(institutionId, usedSource);
                    forcerSynchronisationImmediate(TABLE);
                    LOGGER.info(() -> "✅ Institution supprimée : "
                            + institutionId + " + outbox");
                }
                return ok;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Erreur delete institution " + institutionId, e);
                return false;
            } finally {
                try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }

        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec delete institution : " + institutionId);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    private boolean supprimerDansBase(Connection conn, String institutionId, String source)
            throws SQLException {
        DeleteTracker.enregistrerSuppression(conn, TABLE, institutionId, source);

        String sql = "DELETE FROM " + TABLE + " WHERE institution_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================
    // OUTBOX
    // =========================================================
    /**
     * ✅ Enregistre une opération dans l'outbox pour synchronisation.
     *
     * @param operation INSERT, UPDATE ou DELETE
     * @param source    LOCAL ou REMOTE (source de l'écriture)
     */
    private void enregistrerDansOutbox(Institution institution, String operation, String source) {
        final String targetSource = "REMOTE".equals(source) ? "LOCAL" : "REMOTE";

        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, ?, 'PENDING')
        """;

        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, operation);
            ps.setString(3, institution.getInstitutionId());
            ps.setString(4, toJson(institution));
            ps.setString(5, targetSource);
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox " + operation + " : "
                    + institution.getInstitutionId() + " → " + targetSource);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : "
                            + institution.getInstitutionId());
        }
    }

    /**
     * ✅ Enregistre un DELETE dans l'outbox.
     *    Le {@code row_data} est NULL (pas de données à propager).
     */
    private void enregistrerDeleteDansOutbox(String institutionId, String source) {
        final String targetSource = "REMOTE".equals(source) ? "LOCAL" : "REMOTE";

        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, NULL, ?, 'PENDING')
        """;

        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, institutionId);
            ps.setString(3, targetSource);
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Outbox DELETE : " + institutionId + " → " + targetSource);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Institution institution) {
        try {
            return GSON.toJson(institution);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser l'institution en JSON");
            return "{}";
        }
    }

    // =========================================================
    // MAPPING
    // =========================================================
    private Institution mapInstitution(ResultSet rs) throws SQLException {
        Institution institution = new Institution();
        institution.setInstitutionId(rs.getString("institution_id"));
        institution.setNomInstitution(rs.getString("nom_institution"));
        institution.setSigleInstitution(rs.getString("sigle_institution"));
        institution.setDeviseInstitution(rs.getString("devise_institution"));
        institution.setTypeInstitution(rs.getString("type_institution"));
        institution.setNiveauInstitution(rs.getString("niveau_institution"));
        institution.setCategorieInstitution(rs.getString("categorie_institution"));
        institution.setLogoInstitution(rs.getString("logo_institution"));
        institution.setQRCodeInstitution(rs.getString("qrcode_institution"));
        institution.setAdresseInstitution(rs.getString("adresse_institution"));
        institution.setCodePostalInstitution(rs.getString("code_postal_institution"));
        institution.setCommuneInstitution(rs.getString("commune_institution"));
        institution.setDepartementInstitution(rs.getString("departement_institution"));
        institution.setPaysInstitution(rs.getString("pays_institution"));
        institution.setMailPrimaire(rs.getString("mail_primaire"));
        institution.setMailSecondaire(rs.getString("mail_secondaire"));
        institution.setTelephonePrimaireInstitution(rs.getString("telephone_primaire"));
        institution.setTelephoneSecondaireInstitution(rs.getString("telephone_secondaire"));
        institution.setSiteWebInstitution(rs.getString("site_web_institution"));
        institution.setSmtpPasswordInstitution(rs.getString("smtp_password"));
        institution.setResponsableInstitution(rs.getString("responsable_institution"));
        institution.setPosteResponsableInstitution(rs.getString("poste_responsable"));
        institution.setMailResponsableInstitution(rs.getString("mail_responsable"));
        institution.setTelephoneResponsableInstitution(rs.getString("telephone_responsable"));
        institution.setMoyenneDePassageInstitution(rs.getString("moyenne_passage"));
        institution.setSystemeEducatifInstitution(rs.getString("systeme_educatif"));
        institution.setStatutInstitution(rs.getString("statut_institution"));
        institution.setStatutValidation(rs.getString("statut_validation"));
        institution.setDateCreationInstitution(rs.getString("date_creation"));

        try { institution.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        try {
            Timestamp ts = rs.getTimestamp("last_modified");
            if (ts != null) institution.setLastModified(ts.toLocalDateTime());
        } catch (SQLException ignored) {}

        return institution;
    }

    // =========================================================
    // COUNT
    // =========================================================
    public int count() {
        String sql = "SELECT COUNT(*) FROM " + TABLE;

        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur count institutions", e);
        } finally {
            closeIfNeeded(conn);
        }
        return 0;
    }

    // =========================================================
    // VÉRIFICATIONS D'UNICITÉ
    // =========================================================
    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) return false;

        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE mail_primaire=? OR mail_secondaire=?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, email);
                ps.setString(2, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification email : " + email, e);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    public boolean sigleExists(String sigle) {
        if (sigle == null || sigle.isBlank()) return false;

        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE sigle_institution=?";

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, sigle);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur vérification sigle : " + sigle, e);
            return false;
        } finally {
            closeIfNeeded(conn);
        }
    }

    // =========================================================
    // SYNCHRONISATION
    // =========================================================
    private void forcerSynchronisationImmediate(String nomTable) {
        try {
            db().declencherSyncImmediateAsync();
            LOGGER.fine(() -> "⚡ Sync asynchrone déclenchée pour : " + nomTable);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "⚠️ Impossible de déclencher la synchronisation pour " + nomTable, e);
        }
    }
}