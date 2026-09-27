import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class MatiereManager {

    private static final Logger LOGGER = Logger.getLogger(MatiereManager.class.getName());
    private final MatiereData matiereData;
    private final String institutionId;

    /**
     * Constructeur par défaut : mode autonome.
     * Chaque méthode de {@link MatiereData} ouvre sa propre connexion.
     */
    public MatiereManager(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        this.institutionId = institutionId;
        this.matiereData = new MatiereData(institutionId);
    }

    /**
     * Constructeur avec connexion partagée : mode transactionnel.
     * Toutes les opérations de {@link MatiereData} utilisent cette connexion.
     */
    public MatiereManager(String institutionId, Connection connection) {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        if (connection == null) {
            throw new IllegalArgumentException("La connexion ne peut pas être nulle.");
        }
        try {
            if (connection.isClosed()) {
                throw new IllegalArgumentException("La connexion est déjà fermée.");
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("Connexion invalide", e);
        }
        this.institutionId = institutionId;
        this.matiereData = new MatiereData(institutionId, connection);
    }

    // ============================================================
    // CRÉATION
    // ============================================================

    public boolean ajouterMatiere(Matiere matiere) throws Exception {
        if (matiere == null) {
            throw new IllegalArgumentException("La matière ne peut pas être nulle.");
        }

        // ✅ Forcer l'institution_id du manager
        if (matiere.getInstitutionId() == null || matiere.getInstitutionId().isBlank()) {
            matiere.setInstitutionId(this.institutionId);
        } else if (!this.institutionId.equals(matiere.getInstitutionId())) {
            throw new IllegalArgumentException(
                "L'institution de la matière (" + matiere.getInstitutionId()
                + ") ne correspond pas à celle du manager (" + this.institutionId + ").");
        }

        // ✅ Validation des champs obligatoires
        if (matiere.getCodeCours() == null || matiere.getCodeCours().isBlank()) {
            throw new IllegalArgumentException("Le code du cours est obligatoire.");
        }
        if (matiere.getNomMatiere() == null || matiere.getNomMatiere().isBlank()) {
            throw new IllegalArgumentException("Le nom de la matière est obligatoire.");
        }
        if (matiere.getCoefficient() <= 0) {
            throw new IllegalArgumentException("Le coefficient doit être supérieur à 0.");
        }

        // ✅ Validation des notes
        validerNotes(matiere);

        LOGGER.info(() -> String.format(
                "📝 Validation de la matière: %s (notes: min=%.2f, max=%.2f, passage=%.2f)",
                matiere.getCodeCours(), matiere.getNoteMinimale(),
                matiere.getNoteMaximale(), matiere.getNotePassage()));

        // ✅ Vérification d'existence
        Matiere existant = matiereData.trouverMatiereParCodeCours(matiere.getCodeCours());
        if (existant != null) {
            LOGGER.warning(() -> String.format(
                    "⚠️ Tentative d'ajout d'une matière existante: %s", matiere.getCodeCours()));
            throw new IllegalStateException("Une matière avec le code '"
                    + matiere.getCodeCours() + "' existe déjà.");
        }

        if (matiere.getStatut() == null || matiere.getStatut().isBlank()) {
            matiere.setStatut("ACTIF");
        }

        boolean result = matiereData.create(matiere);
        if (result) {
            LOGGER.info(() -> String.format(
                    "✅ Matière ajoutée avec succès: %s", matiere.getCodeCours()));
        } else {
            LOGGER.severe(() -> String.format(
                    "❌ Échec de l'ajout de la matière: %s", matiere.getCodeCours()));
        }
        return result;
    }

    // ============================================================
    // LECTURE
    // ============================================================

    public Matiere trouverParCodeCours(String codeCours) {
        if (codeCours == null || codeCours.trim().isEmpty()) {
            LOGGER.warning("⚠️ Tentative de recherche avec un code cours null ou vide");
            return null;
        }

        LOGGER.fine(() -> String.format(
                "🔍 Recherche de la matière par code: %s", codeCours));
        Matiere result = matiereData.trouverMatiereParCodeCours(codeCours);

        if (result == null) {
            LOGGER.fine(() -> String.format(
                    "⚠️ Aucune matière trouvée avec le code: %s", codeCours));
        }
        return result;
    }

    /**
     * ✅ Vérifie si une matière existe déjà par son code.
     */
    public boolean existeParCode(String codeCours) {
        if (codeCours == null || codeCours.isBlank()) return false;
        return matiereData.trouverMatiereParCodeCours(codeCours) != null;
    }

    // ============================================================
    // MISE À JOUR
    // ============================================================

    public boolean mettreAJourMatiere(Matiere matiere) throws Exception {
        if (matiere == null) {
            throw new IllegalArgumentException("La matière ne peut pas être nulle.");
        }
        if (matiere.getCodeCours() == null || matiere.getCodeCours().isBlank()) {
            throw new IllegalArgumentException("Le code du cours est obligatoire pour la mise à jour.");
        }

        // ✅ Forcer / valider l'institution_id
        if (matiere.getInstitutionId() == null || matiere.getInstitutionId().isBlank()) {
            matiere.setInstitutionId(this.institutionId);
        } else if (!this.institutionId.equals(matiere.getInstitutionId())) {
            throw new IllegalArgumentException(
                "L'institution de la matière (" + matiere.getInstitutionId()
                + ") ne correspond pas à celle du manager (" + this.institutionId + ").");
        }

        // ✅ Validation des notes
        validerNotes(matiere);

        LOGGER.info(() -> String.format(
                "🔄 Mise à jour de la matière: %s", matiere.getCodeCours()));

        Matiere existant = matiereData.trouverMatiereParCodeCours(matiere.getCodeCours());
        if (existant == null) {
            LOGGER.warning(() -> String.format(
                    "⚠️ Tentative de mise à jour d'une matière inexistante: %s",
                    matiere.getCodeCours()));
            throw new IllegalArgumentException("La matière avec le code "
                    + matiere.getCodeCours() + " n'existe pas.");
        }

        boolean result = matiereData.update(matiere);
        if (result) {
            LOGGER.info(() -> String.format(
                    "✅ Matière mise à jour avec succès: %s", matiere.getCodeCours()));
        } else {
            LOGGER.severe(() -> String.format(
                    "❌ Échec de la mise à jour de la matière: %s", matiere.getCodeCours()));
        }
        return result;
    }

    // ============================================================
    // SUPPRESSION
    // ============================================================

    public boolean supprimerMatiere(String codeCours) throws Exception {
        if (codeCours == null || codeCours.isBlank()) {
            throw new IllegalArgumentException("Le code du cours ne peut pas être vide.");
        }

        LOGGER.info(() -> String.format("🗑️ Suppression de la matière: %s", codeCours));

        Matiere existant = matiereData.trouverMatiereParCodeCours(codeCours);
        if (existant == null) {
            LOGGER.warning(() -> String.format(
                    "⚠️ Tentative de suppression d'une matière inexistante: %s", codeCours));
            throw new IllegalArgumentException("La matière avec le code "
                    + codeCours + " n'existe pas.");
        }

        boolean result = matiereData.delete(codeCours);
        if (result) {
            LOGGER.info(() -> String.format(
                    "✅ Matière supprimée avec succès: %s", codeCours));
        } else {
            LOGGER.severe(() -> String.format(
                    "❌ Échec de la suppression de la matière: %s", codeCours));
        }
        return result;
    }

    // ============================================================
    // LISTAGE
    // ============================================================

    public List<Matiere> listerToutesMatieres() {
        LOGGER.fine("📚 Récupération de toutes les matières");
        List<Matiere> result = matiereData.readAll();
        LOGGER.info(() -> String.format("📚 %d matières récupérées", result.size()));
        return result;
    }

    public List<Matiere> listerMatieresParClasse(String classeMatiere) {
        LOGGER.fine(() -> String.format(
                "📚 Récupération des matières pour la classe: %s", classeMatiere));
        List<Matiere> result = matiereData.trouverToutesMatieresParClasse(classeMatiere);
        LOGGER.info(() -> String.format(
                "📚 %d matières trouvées pour la classe %s", result.size(), classeMatiere));
        return result;
    }

    public List<Matiere> listerMatieresParPeriode(String periode) {
        LOGGER.fine(() -> String.format(
                "📚 Récupération des matières pour la période: %s", periode));
        List<Matiere> result = matiereData.readByPeriode(periode);
        LOGGER.info(() -> String.format(
                "📚 %d matières trouvées pour la période %s", result.size(), periode));
        return result;
    }

    public List<Matiere> listerMatieresParStatut(String statut) {
        LOGGER.fine(() -> String.format(
                "📚 Récupération des matières pour le statut: %s", statut));
        List<Matiere> result = matiereData.readByStatut(statut);
        LOGGER.info(() -> String.format(
                "📚 %d matières trouvées pour le statut %s", result.size(), statut));
        return result;
    }

    public int compterMatieres() {
        int count = matiereData.count();
        LOGGER.info(() -> String.format("📊 Nombre total de matières: %d", count));
        return count;
    }

    // ============================================================
    // RECHERCHE
    // ============================================================

    public List<Matiere> rechercherMatieres(String keyword) {
        LOGGER.info(() -> String.format(
                "🔍 Recherche de matières avec le mot-clé: %s", keyword));
        if (keyword == null || keyword.trim().isEmpty()) {
            LOGGER.fine("🔍 Mot-clé vide, récupération de toutes les matières");
            return listerToutesMatieres();
        }
        List<Matiere> result = matiereData.search(keyword);
        LOGGER.info(() -> String.format(
                "🔍 %d matières trouvées pour la recherche: %s", result.size(), keyword));
        return result;
    }

    public List<Matiere> rechercherAvecFiltres(String motCle, String annee,
                                                String periode, String classe) {
        LOGGER.info(() -> String.format(
                "🔍 Recherche avec filtres - motCle: %s, année: %s, période: %s, classe: %s",
                motCle, annee, periode, classe));

        List<Matiere> result = matiereData.rechercherAvecFiltres(motCle, annee, periode, classe);

        LOGGER.info(() -> String.format(
                "🔍 %d matières trouvées avec les filtres appliqués", result.size()));
        return result;
    }

    // ============================================================
    // VALIDATION UTILITAIRE
    // ============================================================

    /**
     * ✅ Valide les notes : min < max et min <= passage <= max.
     */
    private void validerNotes(Matiere matiere) {
        if (matiere.getNoteMinimale() >= matiere.getNoteMaximale()) {
            throw new IllegalArgumentException(
                    "La note minimale doit être inférieure à la note maximale.");
        }
        if (matiere.getNotePassage() < matiere.getNoteMinimale() ||
            matiere.getNotePassage() > matiere.getNoteMaximale()) {
            throw new IllegalArgumentException(String.format(
                    "La note de passage (%.2f) doit être comprise entre %.2f et %.2f.",
                    matiere.getNotePassage(),
                    matiere.getNoteMinimale(),
                    matiere.getNoteMaximale()));
        }
    }
}