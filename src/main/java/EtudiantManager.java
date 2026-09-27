import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class EtudiantManager {

    private static final Logger LOGGER = Logger.getLogger(EtudiantManager.class.getName());

    private final EtudiantData etudiantDAO;

    public EtudiantManager(EtudiantData etudiantDAO) {
        if (etudiantDAO == null) {
            throw new IllegalArgumentException("EtudiantData (DAO) ne peut pas être nul.");
        }
        this.etudiantDAO = etudiantDAO;
    }

    // =========================================================
    // VALIDATION MÉTIER
    // =========================================================

    private void validerEtudiantComplet(Etudiant e) {
        if (e == null) {
            throw new IllegalArgumentException("L'étudiant ne peut pas être nul.");
        }

        ValidateurEtudiant v = new ValidateurEtudiant()
            // ----- Identité -----
            .requireTextMin("numeroIdentifiant", e.getNumeroIdentifiantEtudiant(), 3)
            .requireTextMin("nom", e.getNom(), 2)
            .requireTextMin("prenom", e.getPrenom(), 2)
            .requireSexe("sexe", e.getSexe())
            .requireTrue("dateNaissance",
                    e.getDateNaissance() != null,
                    "Champ obligatoire manquant")
            .requireTrue("dateNaissancePassee",
                    estDansLePasse(e.getDateNaissance()),
                    "La date de naissance doit être dans le passé")
            .requireGroupeSanguin("groupeSanguin", e.getGroupeSanguin())

            // ----- Coordonnées -----
            .requireTelephone("telephone", e.getTelephone())
            .requireEmail("email", e.getEmail())
            .requireTextMin("adresse", e.getAdresse(), 3)

            // ----- Académique -----
            .requireText("classe", e.getClasse())
            .requireText("anneeAcademique", e.getAnneeAcademique())
            .requireText("periode", e.getPeriode())
            .requireNinu("ninu", e.getNinu())

            // ----- Institution -----
            .requireText("institutionId", e.getInstitutionId())

            // ----- Origine -----
            .requireDepartement("departementNaissance", e.getDepartementNaissance())
            .requireCommune("communeNaissance", e.getCommuneNaissance(), e.getDepartementNaissance())
            .requireDepartement("departementResidence", e.getDepartementResidence())
            .requireCommune("communeResidence", e.getCommuneResidence(), e.getDepartementResidence())

            // ----- Filiation -----
            .requireTextMin("nomPere", e.getNomPere(), 2)
            .requireTextMin("prenomPere", e.getPrenomPere(), 2)
            .requireTextMin("nomMere", e.getNomMere(), 2)
            .requireTextMin("prenomMere", e.getPrenomMere(), 2)
            .requireTelephone("telephoneParents", e.getTelephoneParents())
            .requireTextMin("residenceParents", e.getResidenceParents(), 3)
            .requireText("lienParente", e.getLienParente())
            .requireText("typeResponsable", e.getTypeResponsable())
            .requireTextMin("nomResponsable", e.getNomResponsable(), 2)
            .requireTextMin("prenomResponsable", e.getPrenomResponsable(), 2)

            // ----- Photo (obligatoire) -----
            .requireText("photoPath", e.getPhotoPath());

        v.throwIfInvalid();
    }

    private boolean estDansLePasse(java.util.Date date) {
        if (date == null) return true;

        java.time.LocalDate localDate;
        if (date instanceof java.sql.Date sqlDate) {
            localDate = sqlDate.toLocalDate();
        } else {
            localDate = date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        }
        return !localDate.isAfter(java.time.LocalDate.now());
    }

    private void validerChampNonVide(String valeur, String nomChamp) {
        if (valeur == null || valeur.trim().isEmpty()) {
            throw new IllegalArgumentException(nomChamp + " est obligatoire.");
        }
    }

    // =========================================================
    // CRÉATION
    // =========================================================

    public boolean enregistrerEtudiant(Etudiant etudiant) {
    validerEtudiantComplet(etudiant);
    validerChampNonVide(etudiant.getInstitutionId(),
            "L'identifiant de l'institution");

    if (etudiantDAO.existsWithInstitution(
            etudiant.getNumeroIdentifiantEtudiant(),
            etudiant.getInstitutionId())) {
        String msg = "Un étudiant avec cet identifiant existe déjà dans cette institution : "
                + etudiant.getNumeroIdentifiantEtudiant();
        LOGGER.warning(() -> msg);
        throw new IllegalArgumentException(msg);
    }

    // ✅ Catch de SQLException
    try {
        return etudiantDAO.create(etudiant);
    } catch (java.sql.SQLException e) {
        LOGGER.log(java.util.logging.Level.SEVERE,
                "❌ Erreur SQL création étudiant : "
                        + etudiant.getNumeroIdentifiantEtudiant(), e);
        return false;
    }
}

    // =========================================================
    // MODIFICATION
    // =========================================================

    public boolean modifierEtudiant(Etudiant etudiant) throws SQLException {
        validerEtudiantComplet(etudiant);
        validerChampNonVide(etudiant.getInstitutionId(),
                "L'identifiant de l'institution");

        if (!etudiantDAO.existsWithInstitution(
                etudiant.getNumeroIdentifiantEtudiant(),
                etudiant.getInstitutionId())) {
            String msg = "Impossible de modifier : l'étudiant n'existe pas dans cette institution.";
            LOGGER.warning(() -> msg);
            throw new IllegalArgumentException(msg);
        }

        // ✅ Retour direct (update ne lance plus SQLException)
        return etudiantDAO.update(etudiant);
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================

    public boolean supprimerEtudiant(String numeroIdentifiant, String institutionId) {
        validerChampNonVide(numeroIdentifiant, "L'identifiant de l'étudiant");
        validerChampNonVide(institutionId, "L'identifiant de l'institution");

        if (!etudiantDAO.existsWithInstitution(numeroIdentifiant, institutionId)) {
            String msg = "Impossible de supprimer : l'étudiant n'existe pas dans cette institution.";
            LOGGER.warning(() -> msg);
            throw new IllegalArgumentException(msg);
        }
        return etudiantDAO.delete(numeroIdentifiant, institutionId);
    }

    // =========================================================
    // LECTURE
    // =========================================================

    public Etudiant obtenirEtudiantParId(String numeroIdentifiant, String institutionId) {
        validerChampNonVide(numeroIdentifiant, "L'identifiant de l'étudiant");
        validerChampNonVide(institutionId, "L'identifiant de l'institution");

        return etudiantDAO.read(numeroIdentifiant, institutionId);
    }

    public List<Etudiant> obtenirTousLesEtudiants(String institutionId) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        return etudiantDAO.readAll(institutionId);
    }

    public List<Etudiant> obtenirEtudiantsAvecFiltres(String institutionId, String classe,
                                                       String anneeAcademique, String periode) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        return etudiantDAO.readWithFilters(institutionId, classe, anneeAcademique, periode);
    }

    public List<Etudiant> obtenirEtudiantsParClasse(String institutionId, String classe) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        validerChampNonVide(classe, "La classe");
        return etudiantDAO.readWithFilters(institutionId, classe, null, null);
    }

    public List<Etudiant> obtenirEtudiantsParAnneeAcademique(String institutionId,
                                                              String anneeAcademique) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        validerChampNonVide(anneeAcademique, "L'année académique");
        return etudiantDAO.readWithFilters(institutionId, null, anneeAcademique, null);
    }

    public List<Etudiant> obtenirEtudiantsParPeriode(String institutionId, String periode) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        validerChampNonVide(periode, "La période");
        return etudiantDAO.readWithFilters(institutionId, null, null, periode);
    }

    public List<Etudiant> obtenirEtudiantsParInstitution(String institutionId) {
        return obtenirTousLesEtudiants(institutionId);
    }

    // =========================================================
    // COMPTAGE
    // =========================================================

    public int compterEtudiants(String institutionId) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        return etudiantDAO.count(institutionId);
    }

    public int compterEtudiantsParClasse(String institutionId, String classe) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        validerChampNonVide(classe, "La classe");
        return etudiantDAO.countByClasse(institutionId, classe);
    }

    // =========================================================
    // RECHERCHE
    // =========================================================

    public List<Etudiant> rechercherEtudiants(String institutionId, String keyword) {
        validerChampNonVide(institutionId, "L'identifiant de l'institution");
        if (keyword == null || keyword.trim().isEmpty()) {
            return obtenirTousLesEtudiants(institutionId);
        }
        return etudiantDAO.search(institutionId, keyword);
    }

    // =========================================================
    // EXISTENCE
    // =========================================================

    public boolean etudiantExiste(String numeroIdentifiant, String institutionId) {
        if (numeroIdentifiant == null || numeroIdentifiant.trim().isEmpty()) return false;
        if (institutionId == null || institutionId.trim().isEmpty()) return false;
        return etudiantDAO.existsWithInstitution(numeroIdentifiant, institutionId);
    }
}