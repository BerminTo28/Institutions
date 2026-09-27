import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class PromotionManager {

    private static final Logger LOGGER = Logger.getLogger(PromotionManager.class.getName());

    // ============================================================
    // 1. ENREGISTRER UNE PROMOTION (CRÉATION OU MODIFICATION)
    // ============================================================
    /**
     * ✅ Pas de getId() — on détecte la création/modification via la clé composite.
     * - Si la promotion existe déjà (via institutionId + annee + promotion) → UPDATE.
     * - Sinon → INSERT.
     */
    public boolean enregistrerPromotion(Promotion promotion) throws IllegalArgumentException, SQLException {
        validerPromotion(promotion);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);

            // Vérifier si la promotion existe déjà (clé composite)
            Optional<Promotion> existante = dao.trouverParCle(
                    promotion.getInstitutionId(),
                    promotion.getAnneeAcademique(),
                    promotion.getPromotion());

            if (existante.isPresent()) {
                LOGGER.info(() -> "Mise à jour de la promotion : "
                        + promotion.getAnneeAcademique() + " / " + promotion.getPromotion());
                return dao.mettreAJour(promotion);
            } else {
                LOGGER.info(() -> "Création d'une nouvelle promotion : "
                        + promotion.getAnneeAcademique() + " / " + promotion.getPromotion());
                return dao.créer(promotion);
            }
        }
    }

    // ============================================================
    // 2. LISTER TOUTES LES PROMOTIONS D'UNE INSTITUTION
    // ============================================================
    public List<Promotion> obtenirToutesLesPromotions(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            return new ArrayList<>();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.listerParInstitution(institutionId);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur récupération promotions pour "
                    + institutionId + " : " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ============================================================
    // 3. LISTER LES PROMOTIONS PAR ANNÉE
    // ============================================================
    public List<Promotion> obtenirPromotionsParAnnee(String institutionId, String anneeAcademique) {
        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()) {
            return new ArrayList<>();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.listerParInstitutionEtAnnee(institutionId, anneeAcademique);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur filtrage promotions par année : " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ============================================================
    // 4. OBTENIR UNE PROMOTION PAR CLÉ COMPOSITE
    // ============================================================
    /**
     * ✅ Plus de int id — clé composite.
     */
    public Optional<Promotion> obtenirParCle(String institutionId,
                                               String anneeAcademique,
                                               String promotion) {
        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()
                || promotion == null || promotion.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.trouverParCle(institutionId, anneeAcademique, promotion);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur recherche promotion "
                    + anneeAcademique + "/" + promotion + " : " + e.getMessage());
            return Optional.empty();
        }
    }

    // ============================================================
    // 5. OBTENIR LA PROMOTION ACTIVE
    // ============================================================
    public Optional<Promotion> obtenirPromotionActive(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.trouverActiveParInstitution(institutionId);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur recherche promotion active pour "
                    + institutionId + " : " + e.getMessage());
            return Optional.empty();
        }
    }

    // ============================================================
    // 6. ACTIVER UNE PROMOTION
    // ============================================================
    /**
     * ✅ Clé composite au lieu de int id.
     */
    public boolean activerPromotion(String institutionId,
                                      String anneeAcademique,
                                      String promotion) {
        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()
                || promotion == null || promotion.isBlank()) {
            return false;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.definirCommeActive(institutionId, anneeAcademique, promotion);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur activation promotion "
                    + anneeAcademique + "/" + promotion + " : " + e.getMessage());
            return false;
        }
    }

    // ============================================================
    // 7. SUPPRIMER UNE PROMOTION
    // ============================================================
    /**
     * ✅ Clé composite au lieu de int id.
     */
    public boolean supprimerPromotion(String institutionId,
                                        String anneeAcademique,
                                        String promotion) throws SQLException {
        if (institutionId == null || institutionId.isBlank()
                || anneeAcademique == null || anneeAcademique.isBlank()
                || promotion == null || promotion.isBlank()) {
            return false;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.supprimer(institutionId, anneeAcademique, promotion);
        }
    }

    // ============================================================
    // 8. MISE À JOUR AVEC CHANGEMENT DE CLÉ COMPOSITE
    // ============================================================
    /**
     * ✅ Si l'utilisateur change l'année ou le nom de la promotion,
     *    la clé composite change → DELETE + INSERT.
     */
    public boolean mettreAJourAvecChangementDeCle(String institutionId,
                                                    String ancienneAnnee,
                                                    String anciennePromotion,
                                                    Promotion nouvelle) throws SQLException {
        validerPromotion(nouvelle);
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PromotionData dao = new PromotionData(conn);
            return dao.mettreAJourAvecChangementDeCle(
                    institutionId, ancienneAnnee, anciennePromotion, nouvelle);
        }
    }

    // ============================================================
    // 9. RÈGLES DE VALIDATION MÉTIER
    // ============================================================
    private void validerPromotion(Promotion p) throws IllegalArgumentException {
        if (p == null) {
            throw new IllegalArgumentException("L'objet Promotion ne peut pas être nul.");
        }
        if (p.getInstitutionId() == null || p.getInstitutionId().isBlank()) {
            throw new IllegalArgumentException("L'identifiant de l'institution est obligatoire.");
        }
        if (p.getAnneeAcademique() == null || p.getAnneeAcademique().isBlank()) {
            throw new IllegalArgumentException("L'année académique est obligatoire.");
        }
        if (p.getPromotion() == null || p.getPromotion().isBlank()) {
            throw new IllegalArgumentException("Le nom/libellé de la promotion est obligatoire.");
        }
        if (p.getDateDebut() == null) {
            throw new IllegalArgumentException("La date de début de la promotion est obligatoire.");
        }
        if (p.getDateFin() == null) {
            throw new IllegalArgumentException("La date de fin de la promotion est obligatoire.");
        }
        if (p.getDateFin().isBefore(p.getDateDebut())) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }
    }
}