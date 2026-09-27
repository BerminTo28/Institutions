import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {
    private final DatabaseManager dbManager;
    
    public AuthService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public AuthResult authenticate(String identifiant, String complement, String role) {
        AuthResult result = new AuthResult();

        try (Connection conn = dbManager.getConnection()) {

            if (!"AUTO".equals(role)) {
                return authenticateWithRole(conn, identifiant, complement, role);
            }

            // Mode AUTO
            String[] roles = {"ETUDIANT", "PROFESSEUR", "ADMINISTRATEUR"};
            for (String r : roles) {
                AuthResult test = authenticateWithRole(conn, identifiant, complement, r);
                if (test.isSuccess()) {
                    return test;
                }
            }

            result.setSuccess(false);
            result.setMessage("Aucun compte trouvé avec ces identifiants");

        } catch (SQLException e) {
            result.setSuccess(false);
            result.setMessage("Erreur de base de données: " + e.getMessage());
        }

        return result;
    }

    private AuthResult authenticateWithRole(Connection conn, String identifiant, String complement, String role) {
        return switch (role) {
            case "ETUDIANT" -> authenticateEtudiant(conn, identifiant, complement);
            case "PROFESSEUR" -> authenticateProfesseur(conn, identifiant, complement);
            case "ADMINISTRATEUR" -> authenticateAdmin(conn, identifiant, complement);
            default -> {
                AuthResult res = new AuthResult();
                res.setSuccess(false);
                res.setMessage("Rôle inconnu: " + role);
                yield res;
            }
        };
    }

    private AuthResult authenticateEtudiant(Connection conn, String identifiant, String complement) {
        AuthResult result = new AuthResult();
        String sql = "SELECT numero_identifiant, mot_de_passe, ninu, matricule, nom, prenom " +
                    "FROM etudiants WHERE numero_identifiant = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifiant);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String motDePasseBDD = rs.getString("mot_de_passe");
                    String ninu = rs.getString("ninu");
                    String matricule = rs.getString("matricule");

                    boolean isPasswordValid = motDePasseBDD != null && motDePasseBDD.equals(complement);
                    boolean isSecondaryValid = (ninu != null && ninu.equals(complement)) || 
                                               (matricule != null && matricule.equals(complement));

                    if (isPasswordValid || isSecondaryValid) {
                        result.setSuccess(true);
                        result.setRole("ETUDIANT");
                        result.setUserId(rs.getString("numero_identifiant"));
                        result.setRedirect("/etudiant/dashboard");
                        result.setDisplayName(rs.getString("prenom") + " " + rs.getString("nom"));
                        result.setMessage("Connexion réussie");
                    } else {
                        result.setSuccess(false);
                        result.setMessage("Identifiants invalides pour l'étudiant");
                    }
                } else {
                    result.setSuccess(false);
                    result.setMessage("Étudiant non trouvé");
                }
            }
        } catch (SQLException e) {
            result.setSuccess(false);
            result.setMessage("Erreur authentification étudiant: " + e.getMessage());
        }

        return result;
    }

    private AuthResult authenticateProfesseur(Connection conn, String identifiant, String complement) {
        AuthResult result = new AuthResult();
        String sql = "SELECT numero_identifiant_professeur, mot_de_passe, email, matricule, nom, prenom " +
                    "FROM professeurs WHERE numero_identifiant_professeur = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifiant);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String motDePasseBDD = rs.getString("mot_de_passe");
                    String email = rs.getString("email");
                    String matricule = rs.getString("matricule");

                    boolean isPasswordValid = motDePasseBDD != null && motDePasseBDD.equals(complement);
                    boolean isSecondaryValid = (email != null && email.equals(complement)) || 
                                               (matricule != null && matricule.equals(complement));

                    if (isPasswordValid || isSecondaryValid) {
                        result.setSuccess(true);
                        result.setRole("PROFESSEUR");
                        result.setUserId(rs.getString("numero_identifiant_professeur"));
                        result.setRedirect("/professeur/dashboard");
                        result.setDisplayName(rs.getString("prenom") + " " + rs.getString("nom"));
                        result.setEmail(rs.getString("email"));
                        result.setMessage("Connexion réussie");
                    } else {
                        result.setSuccess(false);
                        result.setMessage("Identifiants invalides pour le professeur");
                    }
                } else {
                    result.setSuccess(false);
                    result.setMessage("Professeur non trouvé");
                }
            }
        } catch (SQLException e) {
            result.setSuccess(false);
            result.setMessage("Erreur authentification professeur: " + e.getMessage());
        }

        return result;
    }

    private AuthResult authenticateAdmin(Connection conn, String identifiant, String complement) {
        AuthResult result = new AuthResult();
        String sql = "SELECT id, mot_de_passe, telephone_primaire, nom_institution, mail_primaire as email " +
                    "FROM institutions WHERE mail_primaire = ? OR mail_secondaire = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            ps.setString(2, identifiant);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String motDePasseBDD = rs.getString("mot_de_passe");
                    String telephone = rs.getString("telephone_primaire");

                    boolean isPasswordValid = motDePasseBDD != null && motDePasseBDD.equals(complement);
                    boolean isSecondaryValid = telephone != null && telephone.equals(complement);

                    if (isPasswordValid || isSecondaryValid) {
                        result.setSuccess(true);
                        result.setRole("ADMINISTRATEUR");
                        result.setUserId(rs.getString("id"));
                        result.setRedirect("/admin/dashboard");
                        result.setDisplayName(rs.getString("nom_institution"));
                        result.setEmail(rs.getString("email"));
                        result.setInstitutionId(rs.getString("id"));
                        result.setMessage("Connexion réussie");
                    } else {
                        result.setSuccess(false);
                        result.setMessage("Mot de passe invalide pour l'administrateur");
                    }
                } else {
                    result.setSuccess(false);
                    result.setMessage("Administrateur non trouvé");
                }
            }
        } catch (SQLException e) {
            result.setSuccess(false);
            result.setMessage("Erreur authentification administrateur: " + e.getMessage());
        }

        return result;
    }
}