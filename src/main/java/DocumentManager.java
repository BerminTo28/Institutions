import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class DocumentManager {

    private final DocumentData documentData;

    public DocumentManager(Connection connection) {
        this.documentData = new DocumentData(connection);
    }

    /**
 * ✅ Version STRICTE : filtre sur institution + classe + période + année + matières.
 *
 * @param institutionId     institution de l'étudiant
 * @param classeEtudiant    classe de l'étudiant
 * @param periodeEtudiant   période de l'étudiant
 * @param anneeEtudiant     année académique de l'étudiant
 * @param matieresEtudiant  matières (codes cours) accessibles à l'étudiant
 * @param matiereFiltre     filtre optionnel sur la matière (parmi matieresEtudiant)
 * @param periodeFiltre     filtre optionnel sur la période
 * @param anneeFiltre       filtre optionnel sur l'année
 */
public List<DocumentProfesseur> listerDocumentsPourEtudiantStrict(
        String institutionId,
        String classeEtudiant,
        String periodeEtudiant,
        String anneeEtudiant,
        List<String> matieresEtudiant,
        String matiereFiltre,
        String periodeFiltre,
        String anneeFiltre) throws DocumentException {

    try {
        return documentData.getDocumentsForStudentStrict(
                institutionId,
                classeEtudiant,
                periodeEtudiant,
                anneeEtudiant,
                matieresEtudiant,
                matiereFiltre,
                periodeFiltre,
                anneeFiltre);
    } catch (SQLException e) {
        throw new DocumentException(
                "Erreur lors du listage strict des documents : " + e.getMessage(), e);
    }
}
    // =========================================================
    // AJOUT
    // =========================================================
    public boolean ajouterDocument(DocumentProfesseur document) throws DocumentException, SQLException {
        validerDocument(document, true);
        return documentData.create(document);
    }

    // =========================================================
    // MODIFICATION (id String UUID)
    // =========================================================
    public boolean modifierDocument(DocumentProfesseur document) throws DocumentException {
        // ✅ id String (UUID) : vérifier null/blank au lieu de <= 0
        if (document.getId() == null || document.getId().isBlank()) {
            throw new DocumentException("L'ID du document est requis pour la mise à jour.");
        }
        validerDocument(document, false);
        try {
            DocumentProfesseur ancien = documentData.getById(
                    document.getId(),
                    document.getInstitutionId(),
                    document.getProfesseurId());
            if (ancien == null) {
                throw new DocumentException("Document introuvable.");
            }
            if (document.getFichierNom() == null || document.getFichierNom().isBlank()) {
                document.setFichierNom(ancien.getFichierNom());
                document.setFichierChemin(ancien.getFichierChemin());
                document.setUrl(ancien.getUrl());
                document.setTaille(ancien.getTaille());
            }
            return documentData.update(document);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors de la modification du document : " + e.getMessage(), e);
        }
    }

    // =========================================================
    // SUPPRESSION (id String UUID)
    // =========================================================
    public boolean supprimerDocument(String id, String institutionId, String professeurId) throws DocumentException {
        // ✅ id String (UUID)
        if (id == null || id.isBlank()) {
            throw new DocumentException("L'ID du document est requis pour la suppression.");
        }
        try {
            DocumentProfesseur doc = documentData.getById(id, institutionId, professeurId);
            if (doc == null) return false;
            supprimerFichierPhysique(doc.getFichierChemin());
            return documentData.delete(id, institutionId, professeurId);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors de la suppression du document : " + e.getMessage(), e);
        }
    }

    // =========================================================
    // LECTURE PAR ID (id String UUID)
    // =========================================================
    public DocumentProfesseur obtenirDocument(String id, String institutionId, String professeurId)
            throws DocumentException {
        if (id == null || id.isBlank()) return null;
        try {
            return documentData.getById(id, institutionId, professeurId);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors de la récupération du document : " + e.getMessage(), e);
        }
    }

    // =========================================================
    // ✅ CORRECTION : obtenirDocumentParIdEtInstitution en String
    // =========================================================
    public DocumentProfesseur obtenirDocumentParIdEtInstitution(String id, String institutionId)
            throws DocumentException {
        if (id == null || id.isBlank()) return null;
        try {
            return documentData.getByIdAndInstitution(id, institutionId);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors de la récupération du document : " + e.getMessage(), e);
        }
    }

    // =========================================================
    // LISTES
    // =========================================================
    public List<DocumentProfesseur> listerDocuments(String institutionId, String professeurId)
            throws DocumentException {
        try {
            return documentData.getAll(institutionId, professeurId);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors du listage des documents : " + e.getMessage(), e);
        }
    }

    public List<DocumentProfesseur> listerDocumentsAvecFiltres(String institutionId, String professeurId,
                                                               String matiere, String classe, String periode)
            throws DocumentException {
        try {
            return documentData.getByFilters(institutionId, professeurId, matiere, classe, periode);
        } catch (SQLException e) {
            throw new DocumentException("Erreur lors du listage filtré des documents : " + e.getMessage(), e);
        }
    }

    public List<DocumentProfesseur> listerDocumentsPourEtudiant(String institutionId, String classeEtudiant,
                                                                 String matiere, String periode,
                                                                 String anneeAcademique)
            throws DocumentException {
        try {
            return documentData.getDocumentsForStudent(
                    institutionId, classeEtudiant, matiere, periode, anneeAcademique);
        } catch (SQLException e) {
            throw new DocumentException(
                    "Erreur lors du listage des documents pour l'étudiant : " + e.getMessage(), e);
        }
    }

    // =========================================================
    // MÉTHODES PRIVÉES
    // =========================================================
    private void validerDocument(DocumentProfesseur doc, boolean isInsert) throws DocumentException {
        if (doc.getInstitutionId() == null || doc.getInstitutionId().isBlank()) {
            throw new DocumentException("L'ID de l'institution est obligatoire.");
        }
        if (doc.getProfesseurId() == null || doc.getProfesseurId().isBlank()) {
            throw new DocumentException("L'ID du professeur est obligatoire.");
        }
        if (doc.getTitre() == null || doc.getTitre().isBlank()) {
            throw new DocumentException("Le titre du document est obligatoire.");
        }
        if (doc.getMatiere() == null || doc.getMatiere().isBlank()) {
            throw new DocumentException("La matière est obligatoire.");
        }
        if (doc.getClasse() == null || doc.getClasse().isBlank()) {
            throw new DocumentException("La classe est obligatoire.");
        }
        if (isInsert) {
            if (doc.getFichierNom() == null || doc.getFichierNom().isBlank()) {
                throw new DocumentException("Le nom du fichier est obligatoire.");
            }
            if (doc.getFichierChemin() == null || doc.getFichierChemin().isBlank()) {
                throw new DocumentException("Le chemin du fichier est obligatoire.");
            }
        }
    }

    private void supprimerFichierPhysique(String chemin) {
        if (chemin != null && !chemin.isBlank()) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(chemin);
                java.nio.file.Files.deleteIfExists(path);
            } catch (IOException e) {
                // log silencieux
            }
        }
    }

    // =========================================================
    // EXCEPTION INTERNE
    // =========================================================
    public static class DocumentException extends Exception {
        public DocumentException(String message) { super(message); }
        public DocumentException(String message, Throwable cause) { super(message, cause); }
    }
}