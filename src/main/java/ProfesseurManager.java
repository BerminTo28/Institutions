import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ProfesseurManager {

    private static final Logger LOGGER = Logger.getLogger(ProfesseurManager.class.getName());

    private final ProfesseurData professeurData;

    /**
     * ✅ Constructeur 1 : mode transactionnel (connexion partagée).
     */
    public ProfesseurManager(java.sql.Connection connection) {
        this.professeurData = new ProfesseurData(connection);
    }

    /**
     * ✅ Constructeur 2 : mode autonome.
     *    Chaque opération ouvre sa propre connexion.
     */
    public ProfesseurManager() {
        this.professeurData = new ProfesseurData();
    }

    // ============================================================
    // SAUVEGARDE
    // ============================================================

    /**
     * ✅ Sauvegarde un professeur avec tous ses créneaux en une transaction.
     */
    public boolean sauvegarder(Professeur professeur) {
        if (professeur == null) {
            LOGGER.warning("sauvegarder() : professeur nul");
            return false;
        }
        if (!professeur.estValide()) {
            LOGGER.warning(() -> "sauvegarder() : données invalides pour "
                    + professeur.getNumeroIdentifiantProfesseur());
            return false;
        }
        if (professeur.getInstitutionId() == null || professeur.getInstitutionId().isBlank()) {
            LOGGER.warning("sauvegarder() : institution_id manquant");
            return false;
        }

        boolean ok = professeurData.sauvegarder(professeur);
        if (ok) {
            LOGGER.info(() -> "✅ Professeur sauvegardé : "
                    + professeur.getNumeroIdentifiantProfesseur());
        } else {
            LOGGER.warning(() -> "⚠️ Échec sauvegarde professeur : "
                    + professeur.getNumeroIdentifiantProfesseur());
        }
        return ok;
    }

    // ============================================================
    // RECHERCHE
    // ============================================================

    public Professeur obtenir(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()) {
            LOGGER.warning("obtenir() : numeroIdentifiant nul/vide");
            return null;
        }
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("obtenir() : institutionId nul/vide");
            return null;
        }

        try {
            return professeurData.trouverParId(numeroIdentifiant, institutionId);
        } catch (java.sql.SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "❌ Erreur recherche professeur "
                    + numeroIdentifiant, e);
            return null;
        }
    }

    public List<Professeur> listerTous(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("listerTous() : institutionId nul/vide");
            return Collections.emptyList();
        }

        try {
            return professeurData.listerParInstitution(institutionId);
        } catch (java.sql.SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "❌ Erreur listing professeurs pour "
                    + institutionId, e);
            return Collections.emptyList();
        }
    }

    public List<Professeur.Creneau> obtenirCreneaux(String numeroIdentifiant,
                                                      String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("obtenirCreneaux() : paramètres nul/vides");
            return Collections.emptyList();
        }

        try {
            return professeurData.getCreneauxParProfesseur(numeroIdentifiant, institutionId);
        } catch (java.sql.SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "❌ Erreur récupération créneaux pour "
                    + numeroIdentifiant, e);
            return Collections.emptyList();
        }
    }

    // ============================================================
    // SUPPRESSION
    // ============================================================

    public boolean supprimer(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            LOGGER.warning("supprimer() : paramètres nul/vides");
            return false;
        }

        boolean ok = professeurData.supprimer(numeroIdentifiant, institutionId);
        if (ok) {
            LOGGER.info(() -> "✅ Professeur supprimé : " + numeroIdentifiant);
        } else {
            LOGGER.warning(() -> "⚠️ Échec suppression professeur : " + numeroIdentifiant);
        }
        return ok;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================

    public boolean existe(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return false;
        }

        try {
            return professeurData.trouverParId(numeroIdentifiant, institutionId) != null;
        } catch (java.sql.SQLException e) {
            LOGGER.log(java.util.logging.Level.WARNING,
                    "⚠️ Erreur vérification existence professeur " + numeroIdentifiant, e);
            return false;
        }
    }
}