import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExamenManager {

    private static final Logger LOGGER = Logger.getLogger(ExamenManager.class.getName());

    private final ExamenData examenData;

    public ExamenManager(Connection connection) {
        this.examenData = new ExamenData(connection);
    }

    // ============================================================
    // CREATE
    // ============================================================
    public boolean ajouterExamen(Examen examen) {
        if (examen == null) {
            LOGGER.warning("ajouterExamen : examen nul");
            return false;
        }
        boolean ok = examenData.create(examen);
        if (ok) {
            LOGGER.info(() -> "✅ Examen ajouté : " + examen.getId());
        } else {
            LOGGER.warning("⚠️ Échec ajout examen");
        }
        return ok;
    }

    // ============================================================
    // UPDATE
    // ============================================================
    public boolean modifierExamen(Examen examen) {
        if (examen == null || examen.getId() == null || examen.getId().isBlank()) {
            LOGGER.warning("modifierExamen : examen ou id nul");
            return false;
        }
        boolean ok = examenData.update(examen);
        if (ok) {
            LOGGER.info(() -> "✅ Examen modifié : " + examen.getId());
        } else {
            LOGGER.warning(() -> "⚠️ Échec modification examen : " + examen.getId());
        }
        return ok;
    }

    // ============================================================
    // DELETE
    // ============================================================
    public boolean supprimerExamen(String id, String institutionId, String professeurId) {
        if (id == null || id.isBlank()
                || institutionId == null || institutionId.isBlank()
                || professeurId == null || professeurId.isBlank()) {
            LOGGER.warning("supprimerExamen : paramètres null ou vides");
            return false;
        }
        boolean ok = examenData.delete(id, institutionId, professeurId);
        if (ok) {
            LOGGER.info(() -> "✅ Examen supprimé : " + id);
        } else {
            LOGGER.warning(() -> "⚠️ Échec suppression examen : " + id);
        }
        return ok;
    }

    // ============================================================
    // READ — par professeur (tous ses examens)
    // ============================================================
    public List<Examen> getExamensByProfesseur(String professeurId, String institutionId) {
        if (professeurId == null || professeurId.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return List.of();
        }
        try {
            return examenData.getByProfesseur(professeurId, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération examens professeur", e);
            return List.of();
        }
    }

    // ============================================================
    // ✅ NOUVEAU : READ — par professeur AVEC filtres
    // ============================================================
    /**
     * ✅ Liste les examens d'un PROFESSEUR avec filtres.
     *    <p>Utilise {@code getByFiltres} de {@link ExamenData}, qui filtre
     *    strictement sur {@code professeur_id} ET {@code institution_id}.</p>
     *
     * @param professeurId   identifiant du professeur
     * @param institutionId  identifiant de l'institution
     * @param codeCours      filtre code cours (null/"" pour ignorer)
     * @param classe         filtre classe (null/"" pour ignorer)
     * @param periode        filtre période (null/"" pour ignorer)
     * @param annee          filtre année académique (null/"" pour ignorer)
     * @param statut         filtre statut (non utilisé par getByFiltres,
     *                       conservé pour cohérence de signature)
     */
    public List<Examen> getExamensByProfesseurFiltres(String professeurId,
                                                        String institutionId,
                                                        String codeCours,
                                                        String classe,
                                                        String periode,
                                                        String annee,
                                                        String statut) {
        if (professeurId == null || professeurId.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return List.of();
        }
        try {
            // ✅ Appel à getByFiltres (filtre strict professeur_id + institution_id)
            return examenData.getByFiltres(professeurId, institutionId,
                    codeCours, classe, periode, annee);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur récupération examens professeur filtrés", e);
            return List.of();
        }
    }

    // ============================================================
    // READ — par filtres (professeur)
    // ============================================================
    public List<Examen> getExamensByFiltres(String professeurId, String institutionId,
                                            String codeCours, String classe,
                                            String periode, String annee) {
        if (professeurId == null || institutionId == null) {
            return List.of();
        }
        try {
            return examenData.getByFiltres(professeurId, institutionId,
                    codeCours, classe, periode, annee);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération examens filtrés", e);
            return List.of();
        }
    }

    // ============================================================
    // READ — par UUID (PROFESSEUR — filtre strict)
    // ============================================================
    public Examen getExamenById(String id, String institutionId, String professeurId) {
        if (id == null || id.isBlank()
                || institutionId == null || institutionId.isBlank()
                || professeurId == null || professeurId.isBlank()) {
            return null;
        }
        try {
            return examenData.getByIdForProfesseur(id, institutionId, professeurId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération examen par ID " + id, e);
            return null;
        }
    }

    // ============================================================
    // ✅ NOUVEAU : READ — par UUID (PROFESSEUR) — alias
    // ============================================================
    /**
     * ✅ Récupère un examen par UUID en filtrant strictement sur le professeur.
     *    <p>Utilisé par les professeurs pour accéder à leurs propres examens.</p>
     */
    public Examen getExamenByIdForProfesseur(String id, String institutionId,
                                               String professeurId) {
        if (id == null || id.isBlank()
                || institutionId == null || institutionId.isBlank()
                || professeurId == null || professeurId.isBlank()) {
            return null;
        }
        try {
            return examenData.getByIdForProfesseur(id, institutionId, professeurId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur récupération examen professeur par ID " + id, e);
            return null;
        }
    }

    // ============================================================
    // READ — par UUID (ADMIN — sans filtre professeur)
    // ============================================================
    /**
     * ✅ Récupère un examen par UUID pour un ADMINISTRATEUR.
     *    <p>Filtre uniquement sur {@code institution_id}.
     *    Le handler DOIT vérifier le rôle avant d'appeler cette méthode.</p>
     */
    public Examen getExamenByIdForAdmin(String id, String institutionId) {
        if (id == null || id.isBlank()
                || institutionId == null || institutionId.isBlank()) {
            return null;
        }
        try {
            return examenData.getByIdForAdmin(id, institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Erreur récupération examen admin par ID " + id, e);
            return null;
        }
    }

    // ============================================================
    // READ — par institution (ADMIN)
    // ============================================================
    public List<Examen> getExamensByInstitution(String institutionId, String codeCours,
                                                 String classe, String periode,
                                                 String annee, String statut) {
        if (institutionId == null || institutionId.isBlank()) {
            return List.of();
        }
        try {
            return examenData.getAllByInstitution(institutionId, codeCours, classe,
                    periode, annee, statut);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération examens par institution", e);
            return List.of();
        }
    }

    // ============================================================
    // READ — par classe (étudiants)
    // ============================================================
    public List<Examen> getExamensByClasse(String institutionId, String classe,
                                             String annee, String periode) {
        if (institutionId == null || institutionId.isBlank()
                || classe == null || classe.isBlank()) {
            return List.of();
        }
        try {
            return examenData.getByClasse(institutionId, classe, annee, periode);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération examens par classe", e);
            return List.of();
        }
    }
}