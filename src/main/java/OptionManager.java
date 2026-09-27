import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OptionManager {

    private static final Logger LOGGER = Logger.getLogger(OptionManager.class.getName());

    private final String institutionId;
    private final Connection connection;

    public OptionManager(String institutionId, Connection connection) {
        this.institutionId = institutionId;
        this.connection = connection;
    }

    // ============================================================
    // CRUD avec throws SQLException
    // ============================================================

    public void ajouterOption(Options option) throws SQLException {
        if (option.getInstitutionId() == null) {
            option.setInstitutionId(institutionId);
        }
        OptionData data = new OptionData(connection);
        data.create(option);
        LOGGER.info(() -> "Option ajoutée : " + option.getOption() + " - " + option.getClasse());
    }

    /**
     * ✅ Recherche par clé composite.
     */
    public Options recupererOption(String option, String classe, String anneeAcademique)
            throws SQLException {
        OptionData data = new OptionData(connection);
        return data.read(institutionId, option, classe, anneeAcademique);
    }

    // ✅ recupererOptionParId SUPPRIMÉ (plus de colonne id)

    public List<Options> recupererToutesLesOptions() throws SQLException {
        OptionData data = new OptionData(connection);
        return data.readAll(institutionId);
    }

    /**
     * ✅ Mise à jour simple (clé composite inchangée).
     * Modifie uniquement `periode` et `promotion`.
     */
    public void mettreAJourOption(Options option) throws SQLException {
        if (option.getInstitutionId() == null) {
            option.setInstitutionId(institutionId);
        }
        OptionData data = new OptionData(connection);
        data.update(option);
        LOGGER.info(() -> "Option mise à jour : " + option.getOption() + " - " + option.getClasse());
    }

    /**
     * ✅ Mise à jour avec changement de clé composite.
     * Utilisé si l'option, la classe ou l'année change.
     */
    public void mettreAJourOptionAvecChangementDeCle(String ancienneOption,
                                                       String ancienneClasse,
                                                       String ancienneAnnee,
                                                       Options nouvelle) throws SQLException {
        if (nouvelle.getInstitutionId() == null) {
            nouvelle.setInstitutionId(institutionId);
        }
        OptionData data = new OptionData(connection);
        data.updateAvecChangementDeCle(
                institutionId, ancienneOption, ancienneClasse, ancienneAnnee, nouvelle);
        LOGGER.info(() -> "Option mise à jour (clé changée) : "
                + ancienneOption + " → " + nouvelle.getOption());
    }

    public void supprimerOption(String option, String classe, String anneeAcademique)
            throws SQLException {
        OptionData data = new OptionData(connection);
        data.delete(institutionId, option, classe, anneeAcademique);
        LOGGER.info(() -> "Option supprimée : " + option + " - " + classe
                + " (" + anneeAcademique + ")");
    }

    // ✅ supprimerOptionParId SUPPRIMÉ (plus de colonne id)

    // ============================================================
    // MÉTHODE MÉTIER : obtenir toutes les options distinctes (pour filtres)
    // ============================================================
    public List<Options> getOptionsFiltrees(String annee, String periode, String classe)
            throws SQLException {
        OptionData data = new OptionData(connection);
        List<Options> all = data.readAll(institutionId);
        return all.stream()
                .filter(o -> (annee == null || annee.isBlank()
                        || annee.equals(o.getAnneeAcademique())))
                .filter(o -> (periode == null || periode.isBlank()
                        || periode.equals(o.getPeriode())))
                .filter(o -> (classe == null || classe.isBlank()
                        || classe.equals(o.getClasse())))
                .toList();
    }

    // ============================================================
    // TRANSACTION : ajout multiple avec rollback en cas d'erreur
    // ============================================================
    public void ajouterOptionsEnLot(List<Options> options) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            OptionData data = new OptionData(connection);
            for (Options opt : options) {
                if (opt.getInstitutionId() == null) {
                    opt.setInstitutionId(institutionId);
                }
                data.create(opt);
            }
            connection.commit();
            LOGGER.info(() -> options.size() + " options ajoutées en lot");
        } catch (SQLException e) {
            connection.rollback();
            LOGGER.log(Level.SEVERE, "Échec de l'ajout en lot", e);
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}