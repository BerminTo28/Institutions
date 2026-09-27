import java.sql.Connection;
import java.sql.SQLException;

public class DialogueConnexion {

    private final DialogueBase dialogueBase;

    public DialogueConnexion(Connection connection) {
        this.dialogueBase = new DialogueBase(connection);
    }

    public enum Role {
        PROFESSEUR, ETUDIANT, ADMIN
    }

    public boolean tenterConnexion(Role role, String champ1, String champ2) {
        if (champ1 == null || champ1.isBlank() || champ2 == null || champ2.isBlank()) {
            return false;
        }
        try {
            return switch (role) {
                case PROFESSEUR -> {
                    String instId = dialogueBase.authentifierProfesseurEtObtenirInstitution(champ1.trim(), champ2.trim());
                    yield instId != null;
                }
                case ETUDIANT -> dialogueBase.authentifierEtudiant(champ1.trim(), champ2.trim());
                case ADMIN -> dialogueBase.authentifierAdmin(champ1.trim(), champ2.trim());
                default -> false;
            };
        } catch (SQLException e) {
            System.err.println("[ERREUR BDD] " + e.getMessage());
            return false;
        }
    }

    /**
     * Pour le professeur uniquement : retourne l'institutionId si authentification réussie.
     */
    public String tenterConnexionProfesseurAvecInstitution(String codeCours, String numeroIdentifiant) {
        if (codeCours == null || codeCours.isBlank() || numeroIdentifiant == null || numeroIdentifiant.isBlank()) {
            return null;
        }
        try {
            return dialogueBase.authentifierProfesseurEtObtenirInstitution(codeCours.trim(), numeroIdentifiant.trim());
        } catch (SQLException e) {
            System.err.println("[ERREUR BDD] " + e.getMessage());
            return null;
        }
    }

    public Institution connecterAdmin(String identifiant, String motDePasse) {
        if (identifiant == null || identifiant.isBlank() || motDePasse == null || motDePasse.isBlank()) {
            return null;
        }
        try {
            return dialogueBase.authentifierAdminEtObtenir(identifiant.trim(), motDePasse.trim());
        } catch (SQLException e) {
            System.err.println("[ERREUR BDD] " + e.getMessage());
            return null;
        }
    }}