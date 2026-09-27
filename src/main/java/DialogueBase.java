import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.mindrot.jbcrypt.BCrypt;

public class DialogueBase {

    private static final Logger LOGGER = Logger.getLogger(DialogueBase.class.getName());

    private final Connection connection;

    public DialogueBase(Connection connection) {
        this.connection = connection;
    }

    // =========================================================
    // AUTHENTIFICATION PROFESSEUR — Multi-base
    // =========================================================
    /**
     * Authentifie un professeur et retourne son institution.
     * Vérifie sur : connexion fournie → LOCAL → REMOTE.
     *
     * @param codeCours        code du cours enseigné
     * @param numeroIdentifiant identifiant du professeur
     * @return l'institution_id si trouvé, null sinon
     */
    public String authentifierProfesseurEtObtenirInstitution(String codeCours,
                                                               String numeroIdentifiant)
            throws SQLException {

        if (codeCours == null || codeCours.isBlank()
                || numeroIdentifiant == null || numeroIdentifiant.isBlank()) {
            LOGGER.warning("authentifierProfesseurEtObtenirInstitution() : paramètres invalides");
            return null;
        }

        // 1) Connexion fournie
        String institutionId = authentifierProfesseurSurConnexion(
                connection, codeCours, numeroIdentifiant);
        if (institutionId != null) {
            LOGGER.fine(() -> "✅ Auth prof réussie (connexion fournie) : " + numeroIdentifiant);
            return institutionId;
        }

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            institutionId = authentifierProfesseurSurConnexion(
                    localConn, codeCours, numeroIdentifiant);
            if (institutionId != null) {
                LOGGER.info(() -> "✅ Auth prof réussie sur LOCAL : " + numeroIdentifiant);
                return institutionId;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            institutionId = authentifierProfesseurSurConnexion(
                    remoteConn, codeCours, numeroIdentifiant);
            if (institutionId != null) {
                LOGGER.info(() -> "✅ Auth prof réussie sur REMOTE : " + numeroIdentifiant);
                return institutionId;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        LOGGER.warning(() -> "❌ Auth prof échouée : " + numeroIdentifiant
                + " (cours=" + codeCours + ")");
        return null;
    }

    private String authentifierProfesseurSurConnexion(Connection conn, String codeCours,
                                                        String numeroIdentifiant) {
        if (conn == null) return null;

        // ✅ Essaie 2 combinaisons : (code_cours, numero) et (numero, code_cours)
        //    au cas où l'utilisateur aurait inversé les champs.
        String sql = "SELECT institution_id FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE code_cours = ? AND numero_identifiant = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codeCours);
            stmt.setString(2, numeroIdentifiant);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("institution_id");
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur auth prof (format direct) : " + e.getMessage());
        }

        // Tentative inversée
        String sqlInverse = "SELECT institution_id FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                + " WHERE code_cours = ? AND numero_identifiant = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sqlInverse)) {
            stmt.setString(1, numeroIdentifiant);
            stmt.setString(2, codeCours);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("institution_id");
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur auth prof (format inversé) : " + e.getMessage());
        }

        return null;
    }

    // =========================================================
    // AUTHENTIFICATION ÉTUDIANT — Flux « email d'abord »
    // =========================================================
    /**
     * Authentifie un étudiant par son numéro identifiant et son email.
     * Vérifie :
     *   ① L'email existe → institution
     *   ② Le couple (email, numero_identifiant) correspond
     *   ③ `acces.connexion = true`
     */
    public boolean authentifierEtudiant(String numeroIdentifiant, String email) throws SQLException {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || email == null || email.isBlank()) {
            LOGGER.warning("authentifierEtudiant() : paramètres invalides");
            return false;
        }

        String emailNorm = email.trim();

        // ① Email → institution
        String institutionId = trouverInstitutionParEmail(emailNorm);
        if (institutionId == null) {
            LOGGER.warning(() -> "❌ Email inconnu : " + emailNorm);
            return false;
        }
        LOGGER.fine(() -> "🔍 Email trouvé, institution=" + institutionId);

        // ② Couple (email, numero_identifiant)
        if (!verifierEmailEtIdentifiant(emailNorm, numeroIdentifiant)) {
            LOGGER.warning(() -> "❌ Couple email/identifiant invalide : "
                    + emailNorm + " / " + numeroIdentifiant);
            return false;
        }

        // ③ Droit `acces.connexion`
        if (!verifierDroitConnexion(numeroIdentifiant, institutionId)) {
            LOGGER.warning(() -> "🚫 Connexion refusée (acces.connexion=false ou absent) : "
                    + numeroIdentifiant);
            return false;
        }

        LOGGER.info(() -> "✅ Auth étudiant autorisée : " + numeroIdentifiant
                + " (institution=" + institutionId + ")");
        return true;
    }

    /**
     * Étape 1 — Vérifie que l'email existe et retourne l'institution.
     */
    public String trouverInstitutionParEmail(String email) throws SQLException {
        if (email == null || email.isBlank()) return null;
        String emailNorm = email.trim();

        // 1) Connexion fournie
        String inst = chercherInstitutionParEmail(connection, emailNorm);
        if (inst != null) return inst;

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            inst = chercherInstitutionParEmail(localConn, emailNorm);
            if (inst != null) return inst;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            inst = chercherInstitutionParEmail(remoteConn, emailNorm);
            if (inst != null) return inst;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        return null;
    }

    private String chercherInstitutionParEmail(Connection conn, String email) {
        if (conn == null) return null;
        String sql = "SELECT institution_id FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("institution_id");
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur recherche email : " + e.getMessage());
        }
        return null;
    }

    /**
     * Étape 2 — Vérifie que le couple (email, numero_identifiant) correspond.
     */
    public boolean verifierEmailEtIdentifiant(String email, String numeroIdentifiant)
            throws SQLException {
        if (email == null || email.isBlank()
                || numeroIdentifiant == null || numeroIdentifiant.isBlank()) {
            return false;
        }
        String emailNorm = email.trim();
        String idNorm = numeroIdentifiant.trim();

        // 1) Connexion fournie
        Boolean okFournie = verifierCoupleSurConnexion(connection, emailNorm, idNorm);
        if (okFournie != null) return okFournie;

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Boolean okLocal = verifierCoupleSurConnexion(localConn, emailNorm, idNorm);
            if (okLocal != null) return okLocal;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            Boolean okRemote = verifierCoupleSurConnexion(remoteConn, emailNorm, idNorm);
            if (okRemote != null) return okRemote;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        return false;
    }

    private Boolean verifierCoupleSurConnexion(Connection conn, String email,
                                                 String numeroIdentifiant) {
        if (conn == null) return null;
        String sql = "SELECT 1 FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE LOWER(email) = LOWER(?) AND numero_identifiant = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, numeroIdentifiant);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Boolean.TRUE : Boolean.FALSE;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur vérif couple : " + e.getMessage());
        }
        return null;
    }

    /**
     * Vérifie `acces.connexion = true` — fail-closed.
     */
    private boolean verifierDroitConnexion(String numeroIdentifiant, String institutionId) {
        LOGGER.fine(() -> "🔍 verifierDroitConnexion pour " + numeroIdentifiant
                + " | institution=" + institutionId);

        // 1) Connexion fournie
        Boolean resultFournie = lireDroitConnexionSurConnexion(
                connection, numeroIdentifiant, institutionId);
        if (resultFournie != null) return resultFournie;

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Boolean resultLocal = lireDroitConnexionSurConnexion(
                    localConn, numeroIdentifiant, institutionId);
            if (resultLocal != null) return resultLocal;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            Boolean resultRemote = lireDroitConnexionSurConnexion(
                    remoteConn, numeroIdentifiant, institutionId);
            if (resultRemote != null) return resultRemote;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        LOGGER.warning(() -> "⚠️ Aucun enregistrement acces pour " + numeroIdentifiant
                + " (institution=" + institutionId + ") → refus par défaut");
        return false;
    }

    private Boolean lireDroitConnexionSurConnexion(Connection conn, String numeroIdentifiant,
                                                     String institutionId) {
        if (conn == null) return null;
        String sql = "SELECT connexion FROM acces "
                   + "WHERE numero_identifiant = ? AND institution_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroIdentifiant);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean("connexion");
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur lecture droit connexion : " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // DÉTECTION DE RÔLE (helpers publics pour LoginHandler)
    // =========================================================

    /**
     * ✅ Vérifie si l'identifiant correspond à un professeur.
     *    Multi-base : connexion fournie → LOCAL → REMOTE.
     *    Cherche dans : professeur_matieres (code_cours / numero_identifiant)
     *                   et professeurs (numero_identifiant_professeur / email)
     */
    public boolean estProfesseur(String identifiant) {
        if (identifiant == null || identifiant.isBlank()) return false;

        // 1) Connexion fournie
        Boolean resultFournie = estProfesseurSurConnexion(connection, identifiant);
        if (resultFournie != null) return resultFournie;

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Boolean resultLocal = estProfesseurSurConnexion(localConn, identifiant);
            if (resultLocal != null) return resultLocal;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            Boolean resultRemote = estProfesseurSurConnexion(remoteConn, identifiant);
            if (resultRemote != null) return resultRemote;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        return false;
    }

    private Boolean estProfesseurSurConnexion(Connection conn, String identifiant) {
        if (conn == null) return null;
        try {
            // a) code_cours dans professeur_matieres
            if (existeDansTable(conn,
                    "SELECT 1 FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                    + " WHERE code_cours = ?", identifiant)) {
                return Boolean.TRUE;
            }

            // b) numero_identifiant dans professeur_matieres
            if (existeDansTable(conn,
                    "SELECT 1 FROM " + MigrationManager.TABLE_PROFESSEUR_MATIERES
                    + " WHERE numero_identifiant = ?", identifiant)) {
                return Boolean.TRUE;
            }

            // c) numero_identifiant_professeur dans professeurs
            if (existeDansTable(conn,
                    "SELECT 1 FROM " + MigrationManager.TABLE_PROFESSEURS
                    + " WHERE numero_identifiant_professeur = ?", identifiant)) {
                return Boolean.TRUE;
            }

            // d) email dans professeurs
            String sqlEmail = "SELECT 1 FROM " + MigrationManager.TABLE_PROFESSEURS
                    + " WHERE LOWER(email) = LOWER(?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlEmail)) {
                ps.setString(1, identifiant);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Boolean.TRUE;
                }
            }

            return Boolean.FALSE;
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur détection professeur : " + e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Vérifie si l'identifiant correspond à un étudiant.
     */
    public boolean estEtudiant(String identifiant) {
        if (identifiant == null || identifiant.isBlank()) return false;

        // 1) Connexion fournie
        Boolean resultFournie = estEtudiantSurConnexion(connection, identifiant);
        if (resultFournie != null) return resultFournie;

        // 2) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Boolean resultLocal = estEtudiantSurConnexion(localConn, identifiant);
            if (resultLocal != null) return resultLocal;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            Boolean resultRemote = estEtudiantSurConnexion(remoteConn, identifiant);
            if (resultRemote != null) return resultRemote;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        return false;
    }

    private Boolean estEtudiantSurConnexion(Connection conn, String identifiant) {
        if (conn == null) return null;
        String sql = "SELECT 1 FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE numero_identifiant = ? OR LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            ps.setString(2, identifiant);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Boolean.TRUE : Boolean.FALSE;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur détection étudiant : " + e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Vérifie si l'identifiant correspond à un compte admin.
     */
    public boolean estAdmin(String identifiant) {
        if (identifiant == null || identifiant.isBlank()) return false;

        Boolean resultFournie = estAdminSurConnexion(connection, identifiant);
        if (resultFournie != null) return resultFournie;

        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Boolean resultLocal = estAdminSurConnexion(localConn, identifiant);
            if (resultLocal != null) return resultLocal;
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            Boolean resultRemote = estAdminSurConnexion(remoteConn, identifiant);
            if (resultRemote != null) return resultRemote;
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        return false;
    }

    private Boolean estAdminSurConnexion(Connection conn, String identifiant) {
        if (conn == null) return null;
        String sql = "SELECT 1 FROM " + MigrationManager.TABLE_ACCES_ADMIN
                + " WHERE LOWER(email) = LOWER(?) OR utilisateur_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            ps.setString(2, identifiant);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Boolean.TRUE : Boolean.FALSE;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "Erreur détection admin : " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // UTILITAIRE INTERNE
    // =========================================================
    private boolean existeDansTable(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // =========================================================
    // AUTHENTIFICATION ADMIN — Multi-base
    // =========================================================
    public Institution authentifierAdminEtObtenir(String email, String motDePasseClair)
            throws SQLException {
        LOGGER.info(() -> "🔍 Tentative auth admin : " + email);

        // 1) LOCAL
        try (Connection localConn = DatabaseManager.getInstance().getLocalConnection()) {
            Institution institution = authentifierAdminSurConnexion(localConn, email, motDePasseClair);
            if (institution != null) {
                LOGGER.info(() -> "✅ Auth admin réussie sur LOCAL : " + email);
                return institution;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "LOCAL non disponible : " + e.getMessage());
        }

        // 2) Connexion fournie
        Institution institution = authentifierAdminSurConnexion(connection, email, motDePasseClair);
        if (institution != null) {
            LOGGER.info(() -> "✅ Auth admin réussie (connexion fournie) : " + email);
            return institution;
        }

        // 3) REMOTE
        try (Connection remoteConn = DatabaseManager.getInstance().getRemoteConnection()) {
            institution = authentifierAdminSurConnexion(remoteConn, email, motDePasseClair);
            if (institution != null) {
                LOGGER.info(() -> "✅ Auth admin réussie sur REMOTE : " + email);
                return institution;
            }
        } catch (SQLException e) {
            LOGGER.fine(() -> "REMOTE non disponible : " + e.getMessage());
        }

        LOGGER.warning(() -> "❌ Auth admin échouée pour : " + email);
        return null;
    }

    private Institution authentifierAdminSurConnexion(Connection conn, String email,
                                                        String motDePasseClair) throws SQLException {
        if (conn == null || conn.isClosed()) return null;

        String sql = "SELECT institution_id, mot_de_passe FROM " + MigrationManager.TABLE_ACCES_ADMIN
                + " WHERE LOWER(email) = LOWER(?) OR utilisateur_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String institutionId = rs.getString("institution_id");
                String hashStocke = rs.getString("mot_de_passe");

                if (institutionId == null || hashStocke == null) {
                    LOGGER.warning(() -> "⚠️ institution_id ou hash null pour : " + email);
                    return null;
                }
                if (!BCrypt.checkpw(motDePasseClair, hashStocke)) {
                    LOGGER.warning(() -> "⚠️ Mot de passe incorrect pour : " + email);
                    return null;
                }

                InstitutionData instData = new InstitutionData(conn);
                return instData.readByIdOrInstitutionId(institutionId);
            }
        }
    }

    // =========================================================
    // RACCOURCIS
    // =========================================================
    public boolean authentifierAdmin(String identifiant, String motDePasse) throws SQLException {
        return authentifierAdminEtObtenir(identifiant, motDePasse) != null;
    }
}