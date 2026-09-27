import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class DepensesManager {

    private final DepensesData depensesData;
    private final String institutionId;

    public DepensesManager(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("institutionId requis");
        }
        this.institutionId = institutionId;
        this.depensesData = new DepensesData(institutionId);
    }

    // =========================================================
    // AJOUT
    // =========================================================
    /** Ajoute une dépense (objet complet). */
    public void ajouterDepense(Depenses depense) throws SQLException {
        if (depense == null) throw new IllegalArgumentException("Depenses null");
        if (depense.getInstitutionId() == null || depense.getInstitutionId().isBlank()) {
            depense.setInstitutionId(institutionId);
        }
        depensesData.ajouterDepense(depense);
    }

    /** Overload historique — construit un Depenses minimal. */
    public void ajouterDepense(String executant, String motif, double montant,
                                Date date, String article,
                                String fournisseur) throws SQLException {
        Depenses d = new Depenses();
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        ajouterDepense(d);
    }

    // =========================================================
    // LECTURE
    // =========================================================
    /** Liste typée — renvoie des objets Depenses. */
    public List<Depenses> listerDepenses() throws SQLException {
        return depensesData.listerDepenses();
    }

    /** Récupère une dépense par id. */
    public Depenses lireDepense(String id) throws SQLException {
        return depensesData.lireDepenseParId(id);
    }

    /** Total des dépenses de l'institution. */
    public double getTotalDepenses() throws SQLException {
        return depensesData.getTotalDepenses();
    }

    // =========================================================
    // MISE À JOUR
    // =========================================================
    /** Met à jour une dépense (objet complet). */
    public void mettreAJourDepense(Depenses depense) throws SQLException {
        if (depense == null || depense.getId() == null || depense.getId().isBlank()) {
            throw new IllegalArgumentException("id requis");
        }
        if (depense.getInstitutionId() == null || depense.getInstitutionId().isBlank()) {
            depense.setInstitutionId(institutionId);
        }
        depensesData.mettreAJourDepense(depense);
    }

    /** Overload historique — construit un Depenses minimal. */
    public void mettreAJourDepense(String id, String executant, String motif,
                                     double montant, Date date,
                                     String article, String fournisseur)
            throws SQLException {
        Depenses d = new Depenses();
        d.setId(id);
        d.setInstitutionId(institutionId);
        d.setResponsableDepense(executant);
        d.setMotif(motif);
        d.setMontantDepense(montant);
        d.setDateDepense(date);
        d.setNomDepense(article);
        d.setFournisseur(fournisseur);
        mettreAJourDepense(d);
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================
    public void supprimerDepense(String id) throws SQLException {
        if (id == null || id.isBlank()) return;
        depensesData.supprimerDepense(id);
    }

    public void supprimerDepense(Depenses depense) throws SQLException {
        if (depense != null && depense.getId() != null && !depense.getId().isBlank()) {
            supprimerDepense(depense.getId());
        }
    }

    // =========================================================
    // ACCESSEURS
    // =========================================================
    public String getInstitutionId() {
        return institutionId;
    }
}