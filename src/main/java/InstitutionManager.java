import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service métier pour la gestion des institutions.
 *
 * <p><b>RÔLE</b> : couche d'orchestration entre l'UI et {@link InstitutionData}.
 * Elle applique les validations métier, gère les transactions et expose une
 * API claire pour les handlers HTTP.</p>
 *
 * <p><b>ISOLATION</b> : la table {@code institutions} est globale (pas de filtre
 * {@code institution_id}). Le filtrage par institution s'applique aux AUTRES
 * tables métier.</p>
 */
public class InstitutionManager {

    private static final Logger LOGGER = Logger.getLogger(InstitutionManager.class.getName());

    private final InstitutionData institutionDAO;

    public InstitutionManager(InstitutionData institutionDAO) {
        if (institutionDAO == null) {
            throw new IllegalArgumentException("institutionDAO ne peut pas être null.");
        }
        this.institutionDAO = institutionDAO;
    }

    // =========================================================
    // CRÉATION
    // =========================================================

    /**
     * Crée une institution (sans admin).
     *
     * @param institution l'institution à créer (non null, avec institutionId)
     * @return true si créée, false sinon
     * @throws IllegalArgumentException si l'institution est invalide
     */
    public boolean ajouterInstitution(Institution institution) {
        validerInstitution(institution);

        final String institutionId = institution.getInstitutionId();

        try {
            boolean ok = institutionDAO.create(institution);
            if (ok) {
                LOGGER.info(() -> "✅ Institution créée : " + institutionId);
            } else {
                LOGGER.warning(() -> "⚠️ Échec création institution : " + institutionId);
            }
            return ok;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "❌ Erreur SQL création institution : " + institutionId, e);
            return false;
        }
    }

    /**
     * Crée une institution ET son administrateur (transactionnel).
     *
     * @param institution l'institution à créer (non null, avec institutionId)
     * @return true si créée, false sinon
     * @throws IllegalArgumentException si l'institution est invalide
     */
    public boolean ajouterInstitutionAvecAdmin(Institution institution) {
        validerInstitution(institution);

        final String institutionId = institution.getInstitutionId();

        try {
            boolean ok = institutionDAO.createWithAdminPermissions(institution);
            if (ok) {
                LOGGER.info(() -> "✅ Institution + admin créés : " + institutionId);
            } else {
                LOGGER.warning(() -> "⚠️ Échec création institution + admin : "
                        + institutionId);
            }
            return ok;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "❌ Erreur SQL création institution + admin : " + institutionId, e);
            return false;
        }
    }

    // =========================================================
    // LECTURE
    // =========================================================

    /**
     * Recherche une institution par son identifiant.
     *
     * @param institutionId identifiant (non null, non vide)
     * @return l'institution ou null si introuvable
     */
    public Institution trouverParInstitutionId(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("trouverParInstitutionId() : institutionId nul/vide");
            return null;
        }
        return institutionDAO.readByInstitutionId(institutionId);
    }

    /**
     * Liste toutes les institutions (LOCAL + REMOTE fusionnées).
     * La table {@code institutions} est globale : pas de filtre.
     */
    public List<Institution> listerToutesInstitutions() {
        return institutionDAO.readAll();
    }

    /**
     * Liste paginée des institutions.
     */
    public List<Institution> listerInstitutions(int limit, int offset) {
        if (limit <= 0) {
            LOGGER.warning("listerInstitutions() : limit <= 0 → limit=100 par défaut");
            limit = 100;
        }
        if (offset < 0) {
            LOGGER.warning("listerInstitutions() : offset < 0 → offset=0");
            offset = 0;
        }
        return institutionDAO.readAll(limit, offset);
    }

    // =========================================================
    // MISE À JOUR
    // =========================================================

    /**
     * Met à jour une institution.
     *
     * @param institution l'institution à mettre à jour
     * @return true si mise à jour, false sinon
     * @throws IllegalArgumentException si l'institution est invalide
     */
    public boolean mettreAJourInstitution(Institution institution) {
        validerInstitution(institution);

        final String institutionId = institution.getInstitutionId();

        try {
            boolean ok = institutionDAO.update(institution);
            if (ok) {
                LOGGER.info(() -> "✅ Institution mise à jour : " + institutionId);
            } else {
                LOGGER.warning(() -> "⚠️ Échec mise à jour institution : " + institutionId);
            }
            return ok;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "❌ Erreur SQL mise à jour institution : " + institutionId, e);
            return false;
        }
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================

    /**
     * Supprime une institution.
     *
     * @param institutionId identifiant (non null, non vide)
     * @return true si supprimée, false sinon
     * @throws IllegalArgumentException si l'identifiant est invalide
     */
    public boolean supprimerInstitution(String institutionId) throws SQLException {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution_id ne peut pas être vide.");
        }

        boolean ok = institutionDAO.delete(institutionId);
        if (ok) {
            LOGGER.info(() -> "✅ Institution supprimée : " + institutionId);
        } else {
            LOGGER.warning(() -> "⚠️ Échec suppression institution : " + institutionId);
        }
        return ok;
    }

    // =========================================================
    // COMPTAGE
    // =========================================================

    public int compterInstitutions() {
        return institutionDAO.count();
    }

    // =========================================================
    // VÉRIFICATIONS D'UNICITÉ
    // =========================================================

    public boolean emailExiste(String email) {
        if (email == null || email.isBlank()) return false;
        return institutionDAO.emailExists(email);
    }

    public boolean sigleExiste(String sigle) {
        if (sigle == null || sigle.isBlank()) return false;
        return institutionDAO.sigleExists(sigle);
    }

    // =========================================================
    // VALIDATION PRIVÉE
    // =========================================================

    /**
     * ✅ Valide une institution avant de la passer au DAO.
     *
     * @throws IllegalArgumentException si l'institution est invalide
     */
    private void validerInstitution(Institution institution) {
        Objects.requireNonNull(institution, "L'institution ne peut pas être null.");

        if (institution.getInstitutionId() == null
                || institution.getInstitutionId().isBlank()) {
            throw new IllegalArgumentException(
                    "L'institution doit avoir un institution_id.");
        }

        if (institution.getNomInstitution() == null
                || institution.getNomInstitution().isBlank()) {
            throw new IllegalArgumentException(
                    "L'institution doit avoir un nom_institution.");
        }
    }
}