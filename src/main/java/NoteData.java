import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO pour la table `notes`.
 *
 * RÈGLES DE SÉCURITÉ :
 *  1. create() : vérifie le doublon ET insère sur la MÊME connexion.
 *  2. update() : ne modifie JAMAIS code_cours, periode, annee, classe.
 *     Pour changer de période, il faut delete() + create().
 *  3. trouverNoteParContexte() : 6 critères (institution + code_cours +
 *     numero_etudiant + periode + annee + classe).
 *  4. Toutes les lectures filtrent par institution_id.
 */
public class NoteData {

    private static final Logger LOGGER = Logger.getLogger(NoteData.class.getName());
    private static final String TABLE = MigrationManager.TABLE_NOTES;

    /** Timeout SQL général. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** Timeout écriture outbox. */
    private static final int OUTBOX_TIMEOUT_SECONDS = 10;

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDateTime>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.LocalDate.class,
                    (com.google.gson.JsonSerializer<java.time.LocalDate>)
                            (src, type, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .create();

    private final Connection connection;

    public NoteData() { this.connection = null; }
    public NoteData(Connection connection) { this.connection = connection; }

    // ============================================================
    // HELPERS
    // ============================================================
    private DatabaseManager db() { return DatabaseManager.getInstance(); }

    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        return db().getConnection();
    }

    private boolean shouldClose(Connection conn) { return conn != connection; }

    private void closeIfNeeded(Connection conn) {
        if (shouldClose(conn) && conn != null) {
            try { if (!conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        }
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

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); }
        catch (SQLException e) { return false; }
    }

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

    // ============================================================
    // RECHERCHE PAR CONTEXTE COMPLET (6 critères)
    // ============================================================

    /**
     * Recherche une note par contexte COMPLET :
     *   institution + code_cours + numero_etudiant + periode + annee + classe.
     *
     * Ouvre sa propre connexion si aucune n'est fournie.
     */
    public Note trouverNoteParContexte(String codeCours, String numeroEtudiant,
                                         String institutionId, String periode,
                                         String annee, String classe) {
        if (isBlank(codeCours) || isBlank(numeroEtudiant) || isBlank(institutionId)
                || isBlank(periode) || isBlank(annee) || isBlank(classe)) {
            LOGGER.warning("trouverNoteParContexte : paramètres null ou vides");
            return null;
        }

        Connection conn = null;
        try {
            conn = getConnection();
            return trouverNoteParContexteAvecConn(conn, codeCours, numeroEtudiant,
                    institutionId, periode, annee, classe);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur trouverNoteParContexte", e);
            return null;
        } finally {
            closeIfNeeded(conn);
        }
    }

    /**
     * ✅ Variante interne : utilise une connexion fournie.
     *    Indispensable pour partager la connexion entre SELECT et INSERT.
     */
    private Note trouverNoteParContexteAvecConn(Connection conn, String codeCours,
                                                 String numeroEtudiant, String institutionId,
                                                 String periode, String annee, String classe) {
        if (conn == null) return null;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ?"
                + "   AND code_cours = ?"
                + "   AND numero_etudiant = ?"
                + "   AND periode = ?"
                + "   AND annee_academique = ?"
                + "   AND classe = ?"
                + " LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, codeCours);
            ps.setString(3, numeroEtudiant);
            ps.setString(4, periode);
            ps.setString(5, annee);
            ps.setString(6, classe);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapNote(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur trouverNoteParContexteAvecConn", e);
        }
        return null;
    }

    // ============================================================
    // CREATE — vérification + insertion sur la MÊME connexion
    // ============================================================
    public boolean create(Note note) {
        if (note == null) throw new IllegalArgumentException("Note nulle");

        if (note.getId() == null || note.getId().isBlank()) {
            note.setId(UUID.randomUUID().toString());
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour créer la note");
            return false;
        }

        boolean inserted = false;
        String usedSource = null;

        try {
            usedSource = getSource(conn);

            // ✅ FIX : vérification ET insertion sur la MÊME connexion
            Note existing = trouverNoteParContexteAvecConn(conn,
                    note.getCodeCours(),
                    note.getNumeroIdentifiantEtudiant(),
                    note.getInstitutionId(),
                    note.getPeriode(),
                    note.getAnneeAcademique(),
                    note.getClasse());

            if (existing != null) {
                LOGGER.warning(() -> "⚠️ Une note existe déjà pour "
                        + note.getCodeCours() + " / " + note.getNumeroIdentifiantEtudiant()
                        + " / " + note.getPeriode() + " — insertion annulée");
                return false;
            }

            inserted = createSansSync(conn, note, usedSource);
            if (inserted) declencherSyncSiHorsTransaction(conn);

        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec création note sur " + srcErr
                            + " : " + note.getCodeCours());
        } finally {
            closeIfNeeded(conn);
        }

        if (!inserted) return false;

        enregistrerDansOutbox(note, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Note créée sur " + src + " + outbox : " + note.getCodeCours());
        return true;
    }

    private boolean createSansSync(Connection conn, Note note, String source) {
        String sql = """
            INSERT INTO notes (
                institution_id, id,
                numero_etudiant, nom_etudiant, prenom_etudiant, annee_academique,
                matiere_nom, code_cours, classe,
                numero_enseignant, nom_enseignant, prenom_enseignant,
                note_value, note_sur, note_base, coefficient,
                periode, note_minimale, note_maximale, note_passage, rang_etudiant,
                est_validee, is_verified, verified_by,
                source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

                int i = 1;
                ps.setString(i++, note.getInstitutionId());
                ps.setString(i++, note.getId());
                ps.setString(i++, note.getNumeroIdentifiantEtudiant());
                ps.setString(i++, note.getEtudiantNom());
                ps.setString(i++, note.getEtudiantPrenom());
                ps.setString(i++, note.getAnneeAcademique());
                ps.setString(i++, note.getMatiereNom());
                ps.setString(i++, note.getCodeCours());
                ps.setString(i++, note.getClasse());
                ps.setString(i++, note.getNumeroIdentifiantEnseignant());
                ps.setString(i++, note.getEnseignantNom());
                ps.setString(i++, note.getEnseignantPrenom());
                ps.setObject(i++, note.getNoteValue());
                ps.setObject(i++, note.getNoteSur());
                ps.setObject(i++, note.getNoteBase());
                ps.setObject(i++, note.getCoefficient());
                ps.setString(i++, note.getPeriode());
                ps.setObject(i++, note.getNoteMinimale());
                ps.setObject(i++, note.getNoteMaximale());
                ps.setObject(i++, note.getNoteDePassage());
                ps.setObject(i++, note.getRangEtudiant());
                ps.setBoolean(i++, note.getEstValidee());
                ps.setBoolean(i++, note.getIsVerified());
                ps.setString(i++, note.getVerifiedBy());
                ps.setString(i, source != null ? source : getSource(c));

                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur création note", e);
            return false;
        }
    }

    // ============================================================
    // UPDATE — periode/code_cours/annee/classe IMMUABLES
    // ============================================================
    public boolean update(Note note) {
        if (note == null) throw new IllegalArgumentException("Note nulle");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour update note");
            return false;
        }

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = updateSansSync(conn, note, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec update note sur " + srcErr
                            + " : " + note.getCodeCours());
        } finally {
            closeIfNeeded(conn);
        }

        if (!updated) return false;

        enregistrerDansOutbox(note, "UPDATE");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Note mise à jour sur " + src
                + " + outbox : " + note.getCodeCours());
        return true;
    }

    private boolean updateSansSync(Connection conn, Note note, String source) {
        boolean useId = note.getId() != null && !note.getId().isBlank();

        String sql;
        if (useId) {
            sql = """
                UPDATE notes SET
                    note_value=?, note_base=?, coefficient=?,
                    note_minimale=?, note_maximale=?, note_passage=?, rang_etudiant=?,
                    numero_enseignant=?, nom_enseignant=?, prenom_enseignant=?,
                    est_validee=?, is_verified=?, verified_by=?,
                    source=?
                WHERE institution_id=?
                  AND id=?
                  AND code_cours=?
                  AND periode=?
                  AND annee_academique=?
                  AND classe=?
                """;
        } else {
            sql = """
                UPDATE notes SET
                    note_value=?, note_base=?, coefficient=?,
                    note_minimale=?, note_maximale=?, note_passage=?, rang_etudiant=?,
                    numero_enseignant=?, nom_enseignant=?, prenom_enseignant=?,
                    est_validee=?, is_verified=?, verified_by=?,
                    source=?
                WHERE institution_id=?
                  AND code_cours=?
                  AND numero_etudiant=?
                  AND periode=?
                  AND annee_academique=?
                  AND classe=?
                """;
        }

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

                int i = 1;
                ps.setObject(i++, note.getNoteValue());
                ps.setObject(i++, note.getNoteBase());
                ps.setObject(i++, note.getCoefficient());
                // ✅ La période n'est PAS dans le SET
                ps.setObject(i++, note.getNoteMinimale());
                ps.setObject(i++, note.getNoteMaximale());
                ps.setObject(i++, note.getNoteDePassage());
                ps.setObject(i++, note.getRangEtudiant());
                ps.setString(i++, note.getNumeroIdentifiantEnseignant());
                ps.setString(i++, note.getEnseignantNom());
                ps.setString(i++, note.getEnseignantPrenom());
                ps.setBoolean(i++, note.getEstValidee());
                ps.setBoolean(i++, note.getIsVerified());
                ps.setString(i++, note.getVerifiedBy());
                ps.setString(i++, source != null ? source : getSource(c));

                if (useId) {
                    ps.setString(i++, note.getInstitutionId());
                    ps.setString(i++, note.getId());
                    ps.setString(i++, note.getCodeCours());
                    ps.setString(i++, note.getPeriode());
                    ps.setString(i++, note.getAnneeAcademique());
                    ps.setString(i, note.getClasse());
                } else {
                    ps.setString(i++, note.getInstitutionId());
                    ps.setString(i++, note.getCodeCours());
                    ps.setString(i++, note.getNumeroIdentifiantEtudiant());
                    ps.setString(i++, note.getPeriode());
                    ps.setString(i++, note.getAnneeAcademique());
                    ps.setString(i, note.getClasse());
                }

                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur update note", e);
            return false;
        }
    }

    // ============================================================
    // VALIDATION / REJET
    // ============================================================
    public boolean validerNote(String noteId, String verifiedBy) {
        if (isBlank(noteId)) return false;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = validerNoteSansSync(conn, noteId, verifiedBy, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec validerNote sur " + srcErr + " : " + noteId);
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) enregistrerValidationOutbox(noteId, true, verifiedBy);

        return updated;
    }

    private boolean validerNoteSansSync(Connection conn, String noteId,
                                          String verifiedBy, String source) {
        String sql = "UPDATE " + TABLE
                + " SET est_validee = TRUE, is_verified = TRUE, verified_by = ?, source = ? "
                + "WHERE id = ?";

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, verifiedBy);
                ps.setString(2, source != null ? source : getSource(c));
                ps.setString(3, noteId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur validerNote", e);
            return false;
        }
    }

    public boolean rejeterNote(String noteId) {
        if (isBlank(noteId)) return false;

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean updated = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            updated = rejeterNoteSansSync(conn, noteId, usedSource);
            if (updated) declencherSyncSiHorsTransaction(conn);
        } catch (RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec rejeterNote sur " + srcErr + " : " + noteId);
        } finally {
            closeIfNeeded(conn);
        }

        if (updated) enregistrerValidationOutbox(noteId, false, null);

        return updated;
    }

    private boolean rejeterNoteSansSync(Connection conn, String noteId, String source) {
        String sql = "UPDATE " + TABLE
                + " SET est_validee = FALSE, is_verified = FALSE, verified_by = NULL, source = ? "
                + "WHERE id = ?";

        Connection c = (conn != null) ? conn : null;
        try {
            if (c == null) c = getConnection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, source != null ? source : getSource(c));
                ps.setString(2, noteId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur rejeterNote", e);
            return false;
        }
    }

    // ============================================================
    // DELETE PAR CONTEXTE COMPLET (6 critères)
    // ============================================================

    /**
     * ✅ Suppression ciblée sur les 6 critères.
     *    Utilisez CETTE méthode pour supprimer une note précise.
     */
    public boolean deleteWithContexte(String codeCours, String numeroEtudiant,
                                        String institutionId, String periode,
                                        String annee, String classe) {
        if (isBlank(codeCours) || isBlank(numeroEtudiant) || isBlank(institutionId)
                || isBlank(periode) || isBlank(annee) || isBlank(classe)) {
            LOGGER.warning("deleteWithContexte() : paramètres null ou vides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) return false;

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerAvecContexteDansBase(conn, codeCours, numeroEtudiant,
                    institutionId, periode, annee, classe, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec deleteWithContexte sur " + srcErr
                            + " : " + codeCours + "/" + numeroEtudiant);
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) return false;

        enregistrerDeleteDansOutbox(codeCours, numeroEtudiant, institutionId, usedSource);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Note supprimée (contexte complet) sur " + src
                + " + outbox : " + codeCours);
        return true;
    }

    private boolean supprimerAvecContexteDansBase(Connection conn, String codeCours,
                                                    String numeroEtudiant, String institutionId,
                                                    String periode, String annee,
                                                    String classe, String source)
            throws SQLException {
        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            String pkValue = null;
            String selectSql = "SELECT id FROM " + TABLE
                    + " WHERE institution_id = ? AND code_cours = ? AND numero_etudiant = ?"
                    + "   AND periode = ? AND annee_academique = ? AND classe = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, numeroEtudiant);
                ps.setString(4, periode);
                ps.setString(5, annee);
                ps.setString(6, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) pkValue = rs.getString("id");
                }
            }
            if (pkValue == null) {
                conn.rollback();
                return false;
            }

            String compositePk = institutionId + "|" + pkValue;
            DeleteTracker.enregistrerSuppression(conn, TABLE, compositePk, source);

            String deleteSql = "DELETE FROM " + TABLE
                    + " WHERE institution_id = ? AND code_cours = ? AND numero_etudiant = ?"
                    + "   AND periode = ? AND annee_academique = ? AND classe = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, numeroEtudiant);
                ps.setString(4, periode);
                ps.setString(5, annee);
                ps.setString(6, classe);
                rows = ps.executeUpdate();
            }

            conn.commit();
            return rows > 0;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
        }
    }

    // ============================================================
    // LECTURES
    // ============================================================
    public List<Note> getNotesNonValidees(String institutionId, String codeCours,
                                           String periode, String classe, String annee) {
        List<Note> notes = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM " + TABLE
            + " WHERE institution_id = ? AND (est_validee IS NULL OR est_validee = FALSE)"
        );
        List<Object> params = new ArrayList<>();
        params.add(institutionId);

        if (!isBlank(codeCours)) { sql.append(" AND code_cours = ?"); params.add(codeCours); }
        if (!isBlank(periode))   { sql.append(" AND periode = ?");    params.add(periode); }
        if (!isBlank(classe))    { sql.append(" AND classe = ?");     params.add(classe); }
        if (!isBlank(annee))     { sql.append(" AND annee_academique = ?"); params.add(annee); }
        sql.append(" ORDER BY classe, nom_etudiant, prenom_etudiant");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur getNotesNonValidees", e);
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<Note> readAllWithFilters(String institutionId, String codeCours,
                                          String periode, String classe) {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND code_cours = ? AND periode = ? AND classe = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, periode);
                ps.setString(4, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAllWithFilters", e);
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<Note> readAllWithPeriodeAndCours(String institutionId, String codeCours,
                                                   String periode, String annee) {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND code_cours = ? AND periode = ? AND annee_academique = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, periode);
                ps.setString(4, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAllWithPeriodeAndCours", e);
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public Note read(String codeCours, String numeroEtudiant, String institutionId) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id=? AND code_cours=? AND numero_etudiant=?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, numeroEtudiant);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapNote(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture note", e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    /**
     * @deprecated Utiliser {@link #trouverNoteParContexte} à la place.
     */
    @Deprecated
    public Note readWithPeriode(String codeCours, String numeroEtudiant,
                                  String institutionId, String periode) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id=? AND code_cours=? AND numero_etudiant=? AND periode=?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, codeCours);
                ps.setString(3, numeroEtudiant);
                ps.setString(4, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapNote(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lecture note avec periode", e);
        } finally {
            closeIfNeeded(conn);
        }
        return null;
    }

    public List<Note> readAll(String institutionId) {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAll notes", e);
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<Note> readAllWithPeriode(String institutionId, String periode) {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE + " WHERE institution_id = ? AND periode = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur readAllWithPeriode", e);
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<Note> getNotesByEtudiant(String numeroEtudiant, String institutionId,
                                           String annee, String periode) throws SQLException {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND numero_etudiant = ? "
                + "AND annee_academique = ? AND periode = ? AND est_validee = TRUE";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroEtudiant);
                ps.setString(3, annee);
                ps.setString(4, periode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<String> getAnneesByEtudiant(String numeroEtudiant, String institutionId)
            throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + TABLE
                + " WHERE institution_id = ? AND numero_etudiant = ? ORDER BY annee_academique DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroEtudiant);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString("annee_academique"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return annees;
    }

    public List<String> getPeriodesByEtudiant(String numeroEtudiant, String institutionId,
                                                String annee) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sql = "SELECT DISTINCT periode FROM " + TABLE
                + " WHERE institution_id = ? AND numero_etudiant = ? AND annee_academique = ? ORDER BY periode";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroEtudiant);
                ps.setString(3, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) periodes.add(rs.getString("periode"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return periodes;
    }

    public List<Note> getAllValidatedNotesByEtudiant(String numeroEtudiant,
                                                       String institutionId) throws SQLException {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE institution_id = ? AND numero_etudiant = ? AND est_validee = TRUE";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroEtudiant);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    public List<Note> getNotesWithFiltersForStudent(String etudiantId, String institutionId,
                                                      String annee, String periode,
                                                      String classe) throws SQLException {
        List<Note> notes = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM " + TABLE + " WHERE institution_id = ? AND numero_etudiant = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(etudiantId);
        if (!isBlank(annee))   { sql.append(" AND annee_academique = ?"); params.add(annee); }
        if (!isBlank(periode)) { sql.append(" AND periode = ?");          params.add(periode); }
        if (!isBlank(classe))  { sql.append(" AND classe = ?");           params.add(classe); }
        sql.append(" ORDER BY annee_academique DESC, periode, matiere_nom");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(mapNote(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return notes;
    }

    // ============================================================
    // MAPPING
    // ============================================================
    private Note mapNote(ResultSet rs) throws SQLException {
        Note note = new Note();
        try { note.setId(rs.getString("id")); } catch (SQLException ignored) {}

        note.setInstitutionId(rs.getString("institution_id"));
        note.setNumeroIdentifiantEtudiant(rs.getString("numero_etudiant"));
        note.setEtudiantNom(rs.getString("nom_etudiant"));
        note.setEtudiantPrenom(rs.getString("prenom_etudiant"));
        note.setAnneeAcademique(rs.getString("annee_academique"));
        note.setMatiereNom(rs.getString("matiere_nom"));
        note.setCodeCours(rs.getString("code_cours"));
        note.setClasse(rs.getString("classe"));
        note.setNumeroIdentifiantEnseignant(rs.getString("numero_enseignant"));
        note.setEnseignantNom(rs.getString("nom_enseignant"));
        note.setEnseignantPrenom(rs.getString("prenom_enseignant"));
        note.setNoteValue(rs.getString("note_value"));
        note.setNoteSur(rs.getString("note_sur"));
        note.setNoteBase(rs.getString("note_base"));
        note.setCoefficient(rs.getString("coefficient"));
        note.setPeriode(rs.getString("periode"));
        note.setNoteMinimale(rs.getString("note_minimale"));
        note.setNoteMaximale(rs.getString("note_maximale"));
        note.setNoteDePassage(rs.getString("note_passage"));
        note.setRangEtudiant(rs.getString("rang_etudiant"));
        note.setIsVerified(rs.getBoolean("is_verified"));

        // ✅ FIX : utiliser la méthode correcte (convention JavaBean)
        note.setVerifiedBy(rs.getString("verified_by"));

        note.setEstValidee(rs.getBoolean("est_validee"));
        try { note.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        try {
            Timestamp ts = rs.getTimestamp("last_modified");
            if (ts != null) note.setLastModified(ts.toLocalDateTime());
        } catch (SQLException ignored) {}

        return note;
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    private void enregistrerDansOutbox(Note note, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, operation);
            ps.setString(3, note.getInstitutionId() + "|" + note.getId());
            ps.setString(4, toJson(note));
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox " + operation + " : " + note.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer dans l'outbox : " + note.getId());
        }
    }

    private void enregistrerValidationOutbox(String noteId, boolean validee, String verifiedBy) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'UPDATE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, noteId);
            ps.setString(3, "{\"id\":\"" + escapeJson(noteId) + "\","
                    + "\"validee\":" + validee + ","
                    + "\"verifiedBy\":\"" + escapeJson(verifiedBy) + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox validation : " + noteId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer validation outbox : " + noteId);
        }
    }

    private void enregistrerDeleteDansOutbox(String codeCours, String numeroEtudiant,
                                                String institutionId, String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, TABLE);
            ps.setString(2, institutionId + "|" + codeCours + "|" + numeroEtudiant);
            ps.setString(3, "{\"codeCours\":\"" + escapeJson(codeCours) + "\","
                    + "\"numeroEtudiant\":\"" + escapeJson(numeroEtudiant) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();

            LOGGER.fine(() -> "📥 Outbox DELETE : " + codeCours + "/" + numeroEtudiant);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Impossible d'enregistrer le DELETE dans l'outbox");
        }
    }

    private String toJson(Note note) {
        try {
            return GSON.toJson(note);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Impossible de sérialiser la note en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ============================================================
    // SYNCHRONISATION
    // ============================================================
    private void declencherSyncSiHorsTransaction(Connection conn) {
        try {
            if (conn != null && conn.getAutoCommit()) {
                db().declencherSyncImmediateAsync();
                LOGGER.fine("⚡ Sync asynchrone déclenchée (hors transaction).");
            } else {
                LOGGER.fine("ℹ️ Sync différée : transaction en cours (autoCommit=false).");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Impossible de déclencher la sync", e);
        }
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}