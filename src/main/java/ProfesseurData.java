import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfesseurData {

    private static final Logger LOGGER = Logger.getLogger(ProfesseurData.class.getName());

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

    /** Connexion partagée optionnelle (null = mode autonome). */
    private final Connection connection;

    public ProfesseurData() { this.connection = null; }
    public ProfesseurData(Connection connection) { this.connection = connection; }

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

    private boolean isConnectionAlive(Connection conn) {
        if (conn == null) return false;
        try { return conn.isValid(5); } catch (SQLException e) { return false; }
    }

    /**
     * ✅ Détermine la source (LOCAL/REMOTE) par comparaison d'URL JDBC.
     */
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
     * ✅ Retourne une connexion d'écriture : LOCAL en priorité, REMOTE en secours.
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

    // ============================================================
    // SAUVEGARDE — écriture LOCAL uniquement + outbox
    // ============================================================
    public boolean sauvegarder(Professeur prof) {
        if (prof == null) throw new IllegalArgumentException("Professeur null");

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour sauvegarder "
                    + prof.getNumeroIdentifiantProfesseur());
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = sauvegarderDansBase(conn, prof, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec sauvegarde professeur sur " + srcErr
                            + " : " + prof.getNumeroIdentifiantProfesseur());
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) return false;

        enregistrerDansOutbox(prof, "INSERT");

        final String src = usedSource;
        LOGGER.info(() -> "✅ Professeur sauvegardé sur " + src
                + " + outbox : " + prof.getNumeroIdentifiantProfesseur());
        return true;
    }

    /**
     * Sauvegarde transactionnelle : professeur + accès + créneaux.
     */
    private boolean sauvegarderDansBase(Connection conn, Professeur prof, String source)
            throws SQLException {

        if (conn == null) {
            LOGGER.warning("sauvegarderDansBase() : connexion nulle");
            return false;
        }

        boolean autoCommitOriginal = true;
        try {
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // ① Insérer ou mettre à jour le professeur
            Professeur existant = trouverParId(prof.getNumeroIdentifiantProfesseur(),
                    prof.getInstitutionId(), conn);
            boolean success = (existant != null)
                    ? mettreAJour(prof, conn, source)
                    : inserer(prof, conn, source);

            if (!success) {
                conn.rollback();
                LOGGER.warning(() -> "⚠️ Rollback : échec sauvegarde professeur "
                        + prof.getNumeroIdentifiantProfesseur());
                return false;
            }

            // ①bis. Créer la ligne d'accès si elle n'existe pas
            creerAccesProfesseurParDefaut(conn, prof, source);

            // ② Tombstoner les créneaux AVANT de les supprimer
            List<String> idsCreneaux = lireIdsCreneaux(
                    prof.getNumeroIdentifiantProfesseur(), prof.getInstitutionId(), conn);

            for (String idCreneau : idsCreneaux) {
                String compositePk = prof.getInstitutionId() + "|" + idCreneau;
                DeleteTracker.enregistrerSuppression(
                        conn, MigrationManager.TABLE_PROFESSEUR_MATIERES, compositePk, source);
            }

            // ③ Supprimer les créneaux existants
            supprimerTousCreneaux(prof.getNumeroIdentifiantProfesseur(),
                    prof.getInstitutionId(), conn);

            // ④ Réinsérer les nouveaux créneaux
            if (prof.getCreneaux() != null && !prof.getCreneaux().isEmpty()) {
                int inserted = 0;
                for (Professeur.Creneau creneau : prof.getCreneaux()) {
                    if (insererCreneau(prof, creneau, conn, source)) inserted++;
                }
                final int ins = inserted;
                final int total = prof.getCreneaux().size();
                LOGGER.fine(() -> "✅ " + ins + "/" + total + " créneaux insérés");
            }

            conn.commit();

            final String nomComplet = prof.getNomComplet();
            LOGGER.info(() -> "✅ Sauvegarde complète réussie pour: " + nomComplet);
            return true;

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            LOGGER.log(Level.SEVERE, "❌ Erreur sauvegarde, rollback", e);
            throw e;
        } finally {
            try { conn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
        }
    }

    // ============================================================
    // CRÉATION DE L'ACCÈS PROFESSEUR PAR DÉFAUT
    // ============================================================
    private void creerAccesProfesseurParDefaut(Connection conn, Professeur prof,
                                                 String source) {
        if (conn == null || prof == null) return;

        String checkSql = "SELECT 1 FROM " + MigrationManager.TABLE_ACCES_PROFESSEUR
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            check.setString(1, prof.getInstitutionId());
            check.setString(2, prof.getNumeroIdentifiantProfesseur());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    LOGGER.fine(() -> "✅ Accès professeur déjà présent : "
                            + prof.getNumeroIdentifiantProfesseur());
                    return;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur vérification acces_professeur", e);
            return;
        }

        String sql = "INSERT INTO " + MigrationManager.TABLE_ACCES_PROFESSEUR
                + " (institution_id, numero_identifiant, profil, cours, notes, edt, "
                + "absences, documents, messages, parametres, programmes, source) "
                + "VALUES (?, ?, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, prof.getInstitutionId());
            ps.setString(2, prof.getNumeroIdentifiantProfesseur());
            ps.setString(3, source != null ? source : "LOCAL");
            ps.executeUpdate();
            LOGGER.info(() -> "✅ Accès professeur par défaut créé : "
                    + prof.getNumeroIdentifiantProfesseur());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, () -> "⚠️ Impossible de créer l'accès professeur : " + e.getMessage());
        }
    }

    // ============================================================
    // OUTBOX
    // ============================================================
    private void enregistrerDansOutbox(Professeur prof, String operation) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, ?, ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, MigrationManager.TABLE_PROFESSEURS);
            ps.setString(2, operation);
            ps.setString(3, prof.getInstitutionId() + "|"
                    + prof.getNumeroIdentifiantProfesseur());
            ps.setString(4, toJson(prof));
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Professeur " + prof.getNumeroIdentifiantProfesseur()
                    + " → outbox (" + operation + ")");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Impossible d'enregistrer dans l'outbox", e);
        }
    }

    private void enregistrerDeleteDansOutbox(String numeroIdentifiant, String institutionId,
                                                String source) {
        String sql = """
            INSERT INTO sync_outbox
              (table_name, operation, primary_key, row_data, target_source, statut)
            VALUES (?, 'DELETE', ?, ?, 'REMOTE', 'PENDING')
        """;
        try (Connection conn = db().getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(OUTBOX_TIMEOUT_SECONDS);
            ps.setString(1, MigrationManager.TABLE_PROFESSEURS);
            ps.setString(2, institutionId + "|" + numeroIdentifiant);
            ps.setString(3, "{\"numeroIdentifiantProfesseur\":\""
                    + escapeJson(numeroIdentifiant) + "\","
                    + "\"institutionId\":\"" + escapeJson(institutionId) + "\","
                    + "\"source\":\"" + escapeJson(source) + "\"}");
            ps.executeUpdate();
            LOGGER.fine(() -> "📥 Suppression professeur " + numeroIdentifiant + " → outbox");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Impossible d'enregistrer le DELETE dans l'outbox", e);
        }
    }

    private String toJson(Professeur p) {
        try {
            return GSON.toJson(p);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e,
                    () -> "Impossible de sérialiser le professeur en JSON");
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * ✅ Lit les ids UUID des créneaux (String).
     */
    private List<String> lireIdsCreneaux(String numeroIdentifiant, String institutionId,
                                           Connection conn) throws SQLException {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT id FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, numeroIdentifiant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    if (id != null) ids.add(id);
                }
            }
        }
        return ids;
    }

    // ============================================================
    // INSERT / UPDATE (professeurs)
    // ============================================================
    private boolean inserer(Professeur prof, Connection conn, String source)
            throws SQLException {
        String sql = """
            INSERT INTO professeurs (
                institution_id, numero_identifiant_professeur,
                nom, prenom, sexe, date_naissance, groupe_sanguin,
                departement_naissance, commune_naissance, telephone, email, adresse, pays_habitation,
                departement_habitation, commune_habitation, matricule, ninu, diplome,
                specialite, date_embauche, salaire, statut, photo_path, observations,
                situation_matrimoniale, empreinte_path, visage_path, biometrie_active,
                source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            remplirPreparedStatement(ps, prof);
            ps.setString(29, source);
            return ps.executeUpdate() > 0;
        }
    }

    private boolean mettreAJour(Professeur prof, Connection conn, String source)
            throws SQLException {
        String sql = """
            UPDATE professeurs SET
                nom = ?, prenom = ?, sexe = ?, date_naissance = ?, groupe_sanguin = ?,
                departement_naissance = ?, commune_naissance = ?, telephone = ?, email = ?,
                adresse = ?, pays_habitation = ?, departement_habitation = ?, commune_habitation = ?,
                matricule = ?, ninu = ?, diplome = ?, specialite = ?, date_embauche = ?,
                salaire = ?, statut = ?, photo_path = ?, observations = ?, situation_matrimoniale = ?,
                empreinte_path = ?, visage_path = ?, biometrie_active = ?, source = ?
            WHERE institution_id = ? AND numero_identifiant_professeur = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, prof.getNom());
            ps.setString(2, prof.getPrenom());
            ps.setString(3, prof.getSexe());
            ps.setDate(4, prof.getDateNaissance() != null
                    ? new java.sql.Date(prof.getDateNaissance().getTime()) : null);
            ps.setString(5, prof.getGroupeSanguin());
            ps.setString(6, prof.getDepartementNaissance());
            ps.setString(7, prof.getCommuneNaissance());
            ps.setString(8, prof.getTelephone());
            ps.setString(9, prof.getEmail());
            ps.setString(10, prof.getAdresse());
            ps.setString(11, prof.getPaysHabitation());
            ps.setString(12, prof.getDepartementHabitation());
            ps.setString(13, prof.getCommuneHabitation());
            ps.setString(14, prof.getMatricule());
            ps.setString(15, prof.getNinu());
            ps.setString(16, prof.getDiplome());
            ps.setString(17, prof.getSpecialite());
            ps.setDate(18, prof.getDateEmbauche() != null
                    ? new java.sql.Date(prof.getDateEmbauche().getTime()) : null);
            ps.setDouble(19, prof.getSalaire());
            ps.setString(20, prof.getStatut());
            ps.setString(21, prof.getPhotoPath());
            ps.setString(22, prof.getObservations());
            ps.setString(23, prof.getSituationMatrimoniale());
            ps.setString(24, prof.getEmpreintePath());
            ps.setString(25, prof.getVisagePath());
            ps.setBoolean(26, prof.isBiometrieActive());
            ps.setString(27, source);
            ps.setString(28, prof.getInstitutionId());
            ps.setString(29, prof.getNumeroIdentifiantProfesseur());
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // SUPPRESSION
    // ============================================================
    public boolean supprimer(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("supprimer() : paramètres null/vides");
            return false;
        }

        Connection conn = getPreferredWriteConnection();
        if (conn == null) {
            LOGGER.severe(() -> "❌ Aucune connexion disponible pour supprimer "
                    + numeroIdentifiant);
            return false;
        }

        boolean ok = false;
        String usedSource = null;
        try {
            usedSource = getSource(conn);
            ok = supprimerDansBase(conn, numeroIdentifiant, institutionId, usedSource);
            if (ok) declencherSyncSiHorsTransaction(conn);
        } catch (SQLException | RuntimeException e) {
            final String srcErr = usedSource;
            LOGGER.log(Level.SEVERE, e,
                    () -> "❌ Échec suppression professeur sur " + srcErr
                            + " : " + numeroIdentifiant);
        } finally {
            closeIfNeeded(conn);
        }

        if (!ok) return false;

        enregistrerDeleteDansOutbox(numeroIdentifiant, institutionId, usedSource);

        final String src = usedSource;
        LOGGER.info(() -> "✅ Professeur supprimé sur " + src
                + " + outbox : " + numeroIdentifiant);
        return true;
    }

    private boolean supprimerDansBase(Connection conn, String numeroIdentifiant,
                                       String institutionId, String source) throws SQLException {

        if (conn == null) {
            LOGGER.warning("supprimerDansBase() : connexion nulle");
            return false;
        }

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // ① Tombstones des créneaux
            List<String> idsCreneaux = lireIdsCreneaux(numeroIdentifiant, institutionId, conn);
            for (String idCreneau : idsCreneaux) {
                String compositePk = institutionId + "|" + idCreneau;
                DeleteTracker.enregistrerSuppression(
                        conn, MigrationManager.TABLE_PROFESSEUR_MATIERES, compositePk, source);
            }

            // ② Tombstone du professeur
            String compositeProf = institutionId + "|" + numeroIdentifiant;
            DeleteTracker.enregistrerSuppression(
                    conn, MigrationManager.TABLE_PROFESSEURS, compositeProf, source);

            // ③ Suppression acces_professeur (FK)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + MigrationManager.TABLE_ACCES_PROFESSEUR
                    + " WHERE institution_id = ? AND numero_identifiant = ?")) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroIdentifiant);
                ps.executeUpdate();
            }

            // ④ Suppression des créneaux (FK)
            supprimerTousCreneaux(numeroIdentifiant, institutionId, conn);

            // ⑤ Suppression du professeur
            String sql = "DELETE FROM professeurs "
                    + "WHERE institution_id = ? AND numero_identifiant_professeur = ?";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroIdentifiant);
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
    // CRÉNEAUX
    // ============================================================
    private boolean insererCreneau(Professeur prof, Professeur.Creneau creneau,
                                     Connection conn, String source) throws SQLException {
        if (creneau.getAnneeAcademique() == null || creneau.getAnneeAcademique().isBlank()) {
            throw new SQLException("Année académique manquante pour le créneau "
                    + creneau.getCodeCours());
        }
        if (creneau.getPeriode() == null || creneau.getPeriode().isBlank()) {
            throw new SQLException("Période manquante pour le créneau "
                    + creneau.getCodeCours());
        }

        String creneauId = UUID.randomUUID().toString();

        String sql = """
            INSERT INTO professeur_matieres 
            (institution_id, id, numero_identifiant, code_cours, nom_matiere, classe, coefficient, 
             jour, heure_debut, heure_fin, duree_minutes, annee_academique, periode, date_affectation,
             source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int i = 1;
            ps.setString(i++, prof.getInstitutionId());
            ps.setString(i++, creneauId);
            ps.setString(i++, prof.getNumeroIdentifiantProfesseur());
            ps.setString(i++, creneau.getCodeCours());
            ps.setString(i++, creneau.getNomMatiere());
            ps.setString(i++, creneau.getClasse());
            Double coefficient = creneau.getCoefficient();
            ps.setDouble(i++, coefficient != null ? coefficient : 1.0);
            ps.setString(i++, creneau.getJourSemaine());
            ps.setString(i++, creneau.getHeureDebut());
            ps.setString(i++, creneau.getHeureFin());
            ps.setInt(i++, creneau.getDureeMinutes());
            ps.setString(i++, creneau.getAnneeAcademique());
            ps.setString(i++, creneau.getPeriode());
            ps.setDate(i++, creneau.getDateAffectation() != null
                    ? new java.sql.Date(creneau.getDateAffectation().getTime())
                    : new java.sql.Date(System.currentTimeMillis()));
            ps.setString(i, source);
            return ps.executeUpdate() > 0;
        }
    }

    private void supprimerTousCreneaux(String numeroIdentifiant, String institutionId,
                                         Connection conn) throws SQLException {
        String sql = "DELETE FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE institution_id = ? AND numero_identifiant = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, numeroIdentifiant);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                final int d = deleted;
                LOGGER.fine(() -> "🗑️ " + d + " créneaux supprimés pour " + numeroIdentifiant);
            }
        }
    }

    public List<Professeur.Creneau> getCreneauxParProfesseur(String numeroIdentifiant,
                                                               String institutionId) throws SQLException {
        List<Professeur.Creneau> list = new ArrayList<>();
        String sql = """
            SELECT id, code_cours, nom_matiere, classe, coefficient, 
                   date_affectation, jour, heure_debut, heure_fin, duree_minutes, 
                   annee_academique, periode, source
            FROM professeur_matieres 
            WHERE institution_id = ? AND numero_identifiant = ?
            ORDER BY jour, heure_debut, code_cours
            """;
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroIdentifiant);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(extraireCreneau(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return list;
    }

    // ============================================================
    // LECTURES
    // ============================================================
    public Professeur trouverParId(String numeroIdentifiant, String institutionId)
            throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            return trouverParId(numeroIdentifiant, institutionId, conn);
        } finally {
            closeIfNeeded(conn);
        }
    }

    private Professeur trouverParId(String numeroIdentifiant, String institutionId,
                                     Connection conn) throws SQLException {
        String sql = "SELECT * FROM professeurs "
                + "WHERE institution_id = ? AND numero_identifiant_professeur = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, institutionId);
            ps.setString(2, numeroIdentifiant);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return extraireProfesseur(rs);
            }
        }
        return null;
    }

    public List<Professeur> listerParInstitution(String institutionId) throws SQLException {
        List<Professeur> liste = new ArrayList<>();
        String sql = "SELECT * FROM professeurs "
                + "WHERE institution_id = ? ORDER BY nom, prenom";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) liste.add(extraireProfesseur(rs));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return liste;
    }

    // ============================================================
    // CRÉNEAUX PAR CLASSE
    // ============================================================
    public List<Map<String, Object>> getCreneauxParClasse(String institutionId, String classe,
                                                          String annee, String periode,
                                                          String jour) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT pm.code_cours, pm.nom_matiere, pm.classe, pm.coefficient,
                   pm.jour, pm.heure_debut, pm.heure_fin, pm.duree_minutes,
                   pm.annee_academique, pm.periode,
                   p.nom AS professeur_nom, p.prenom AS professeur_prenom,
                   m.note_minimale, m.note_maximale, m.note_passage
            FROM professeur_matieres pm
            LEFT JOIN professeurs p 
                ON pm.institution_id = p.institution_id 
               AND pm.numero_identifiant = p.numero_identifiant_professeur
            LEFT JOIN matieres m 
                ON pm.institution_id = m.institution_id 
               AND pm.code_cours = m.code_cours
            WHERE pm.institution_id = ? AND pm.classe = ?
        """);
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(classe);

        if (annee != null && !annee.isEmpty()) {
            sql.append(" AND pm.annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND pm.periode = ?"); params.add(periode);
        }
        if (jour != null && !jour.isEmpty()) {
            sql.append(" AND pm.jour = ?"); params.add(jour);
        }
        sql.append(" ORDER BY pm.jour, pm.heure_debut");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("codeCours", rs.getString("code_cours"));
                        row.put("nomMatiere", rs.getString("nom_matiere"));
                        row.put("classe", rs.getString("classe"));
                        row.put("coefficient", rs.getDouble("coefficient"));
                        row.put("jour", rs.getString("jour"));
                        row.put("heureDebut", rs.getString("heure_debut"));
                        row.put("heureFin", rs.getString("heure_fin"));
                        row.put("dureeMinutes", rs.getInt("duree_minutes"));
                        row.put("anneeAcademique", rs.getString("annee_academique"));
                        row.put("periode", rs.getString("periode"));
                        row.put("professeurNom", rs.getString("professeur_nom"));
                        row.put("professeurPrenom", rs.getString("professeur_prenom"));
                        row.put("noteMinimale", rs.getDouble("note_minimale"));
                        row.put("noteMaximale", rs.getDouble("note_maximale"));
                        row.put("notePassage", rs.getDouble("note_passage"));
                        result.add(row);
                    }
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return result;
    }

    public List<String> getAnneesByClasse(String institutionId, String classe) throws SQLException {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT DISTINCT annee_academique FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE institution_id = ? AND classe = ? ORDER BY annee_academique DESC";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) annees.add(rs.getString("annee_academique"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return annees;
    }

    public List<String> getPeriodesByClasse(String institutionId, String classe,
                                              String annee) throws SQLException {
        List<String> periodes = new ArrayList<>();
        String sqlPeriodes = "SELECT periode FROM " + MigrationManager.TABLE_PERIODES
                + " WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        Set<String> periodesDefinies = new HashSet<>();

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlPeriodes)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) periodesDefinies.add(rs.getString("periode"));
                }
            }
            if (periodesDefinies.isEmpty()) return periodes;

            String sqlUtilisees = "SELECT DISTINCT periode FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                    + " WHERE institution_id = ? AND classe = ? AND annee_academique = ? "
                    + "AND periode IS NOT NULL AND periode != ''";
            Set<String> periodesUtilisees = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlUtilisees)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, classe);
                ps.setString(3, annee);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) periodesUtilisees.add(rs.getString("periode"));
                }
            }
            for (String p : periodesDefinies) {
                if (periodesUtilisees.contains(p)) periodes.add(p);
            }
            Collections.sort(periodes);
        } finally {
            closeIfNeeded(conn);
        }
        return periodes;
    }

    public List<String> getJoursByClasse(String institutionId, String classe,
                                           String annee, String periode) throws SQLException {
        List<String> jours = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT jour FROM "
                + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE institution_id = ? AND classe = ?");
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(classe);
        if (annee != null && !annee.isEmpty()) {
            sql.append(" AND annee_academique = ?"); params.add(annee);
        }
        if (periode != null && !periode.isEmpty()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        sql.append(" ORDER BY jour");

        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) jours.add(rs.getString("jour"));
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return jours;
    }

    // ============================================================
    // VÉRIFICATIONS
    // ============================================================
    public boolean verifierCreneauExiste(String numeroIdentifiant, String institutionId,
                                           String codeCours, String classe,
                                           String periode, String anneeAcademique)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
            + " WHERE institution_id = ? AND numero_identifiant = ? AND code_cours = ? AND classe = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(institutionId);
        params.add(numeroIdentifiant);
        params.add(codeCours);
        params.add(classe);
        if (periode != null && !periode.isBlank()) {
            sql.append(" AND periode = ?"); params.add(periode);
        }
        if (anneeAcademique != null && !anneeAcademique.isBlank()) {
            sql.append(" AND annee_academique = ?"); params.add(anneeAcademique);
        }
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    public boolean verifierConflitHoraire(String numeroIdentifiant, String institutionId,
                                            String jour, String heureDebut, String heureFin,
                                            String codeCours, String classe)
            throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM professeur_matieres 
            WHERE institution_id = ? AND numero_identifiant = ? AND jour = ? 
            AND ((heure_debut <= ? AND heure_fin > ?) OR (heure_debut < ? AND heure_fin >= ?) OR (heure_debut >= ? AND heure_fin <= ?))
            AND code_cours != ? AND classe != ?
            """;
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, institutionId);
                ps.setString(2, numeroIdentifiant);
                ps.setString(3, jour);
                ps.setString(4, heureDebut);
                ps.setString(5, heureDebut);
                ps.setString(6, heureFin);
                ps.setString(7, heureFin);
                ps.setString(8, heureDebut);
                ps.setString(9, heureFin);
                ps.setString(10, codeCours);
                ps.setString(11, classe);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1) > 0;
                }
            }
        } finally {
            closeIfNeeded(conn);
        }
        return false;
    }

    // ============================================================
    // MAPPING
    // ============================================================
    private void remplirPreparedStatement(PreparedStatement ps, Professeur prof)
            throws SQLException {
        ps.setString(1, prof.getInstitutionId());
        ps.setString(2, prof.getNumeroIdentifiantProfesseur());
        ps.setString(3, prof.getNom());
        ps.setString(4, prof.getPrenom());
        ps.setString(5, prof.getSexe());
        ps.setDate(6, prof.getDateNaissance() != null
                ? new java.sql.Date(prof.getDateNaissance().getTime()) : null);
        ps.setString(7, prof.getGroupeSanguin());
        ps.setString(8, prof.getDepartementNaissance());
        ps.setString(9, prof.getCommuneNaissance());
        ps.setString(10, prof.getTelephone());
        ps.setString(11, prof.getEmail());
        ps.setString(12, prof.getAdresse());
        ps.setString(13, prof.getPaysHabitation());
        ps.setString(14, prof.getDepartementHabitation());
        ps.setString(15, prof.getCommuneHabitation());
        ps.setString(16, prof.getMatricule());
        ps.setString(17, prof.getNinu());
        ps.setString(18, prof.getDiplome());
        ps.setString(19, prof.getSpecialite());
        ps.setDate(20, prof.getDateEmbauche() != null
                ? new java.sql.Date(prof.getDateEmbauche().getTime()) : null);
        ps.setDouble(21, prof.getSalaire());
        ps.setString(22, prof.getStatut());
        ps.setString(23, prof.getPhotoPath());
        ps.setString(24, prof.getObservations());
        ps.setString(25, prof.getSituationMatrimoniale());
        ps.setString(26, prof.getEmpreintePath());
        ps.setString(27, prof.getVisagePath());
        ps.setBoolean(28, prof.isBiometrieActive());
    }

    private Professeur extraireProfesseur(ResultSet rs) throws SQLException {
        Professeur prof = new Professeur();
        prof.setNumeroIdentifiantProfesseur(rs.getString("numero_identifiant_professeur"));
        prof.setNom(rs.getString("nom"));
        prof.setPrenom(rs.getString("prenom"));
        prof.setSexe(rs.getString("sexe"));
        prof.setDateNaissance(rs.getDate("date_naissance"));
        prof.setGroupeSanguin(rs.getString("groupe_sanguin"));
        prof.setDepartementNaissance(rs.getString("departement_naissance"));
        prof.setCommuneNaissance(rs.getString("commune_naissance"));
        prof.setTelephone(rs.getString("telephone"));
        prof.setEmail(rs.getString("email"));
        prof.setAdresse(rs.getString("adresse"));
        prof.setPaysHabitation(rs.getString("pays_habitation"));
        prof.setDepartementHabitation(rs.getString("departement_habitation"));
        prof.setCommuneHabitation(rs.getString("commune_habitation"));
        prof.setMatricule(rs.getString("matricule"));
        prof.setNinu(rs.getString("ninu"));
        prof.setInstitutionId(rs.getString("institution_id"));
        prof.setDiplome(rs.getString("diplome"));
        prof.setSpecialite(rs.getString("specialite"));
        prof.setDateEmbauche(rs.getDate("date_embauche"));
        prof.setSalaire(rs.getDouble("salaire"));
        prof.setStatut(rs.getString("statut"));
        prof.setPhotoPath(rs.getString("photo_path"));
        prof.setObservations(rs.getString("observations"));
        prof.setSituationMatrimoniale(rs.getString("situation_matrimoniale"));
        prof.setEmpreintePath(rs.getString("empreinte_path"));
        prof.setVisagePath(rs.getString("visage_path"));
        prof.setBiometrieActive(rs.getBoolean("biometrie_active"));
        try { prof.setSource(rs.getString("source")); } catch (SQLException ignored) {}

        try {
            Timestamp ts = rs.getTimestamp("last_modified");
            if (ts != null) prof.setLastModified(ts.toLocalDateTime());
        } catch (SQLException ignored) {}

        return prof;
    }

    private Professeur.Creneau extraireCreneau(ResultSet rs) throws SQLException {
        Professeur.Creneau creneau = new Professeur.Creneau();

        try { creneau.setId(rs.getString("id")); } catch (SQLException ignored) {}

        creneau.setCodeCours(rs.getString("code_cours"));
        creneau.setNomMatiere(rs.getString("nom_matiere"));
        creneau.setClasse(rs.getString("classe"));
        creneau.setCoefficient(rs.getDouble("coefficient"));
        creneau.setJourSemaine(rs.getString("jour"));
        creneau.setHeureDebut(rs.getString("heure_debut"));
        creneau.setHeureFin(rs.getString("heure_fin"));
        creneau.setDureeMinutes(rs.getInt("duree_minutes"));
        creneau.setAnneeAcademique(rs.getString("annee_academique"));
        creneau.setPeriode(rs.getString("periode"));
        Timestamp ts = rs.getTimestamp("date_affectation");
        if (ts != null) creneau.setDateAffectation(new java.util.Date(ts.getTime()));
        try { creneau.setSource(rs.getString("source")); } catch (SQLException ignored) {}
        return creneau;
    }

    // ============================================================
    // SYNC
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
}