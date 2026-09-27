import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class GenerateurCle {

    private static final AtomicInteger compteurOrdre = new AtomicInteger(100001);
    private static final Random random = new Random();

    public static String genererIDIndividu(String nom, String prenom, Date dateNaissance, String sexe, String institutionId) {
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("L'institutionId est obligatoire pour générer un ID.");
        }
        String prefixe = construirePrefixe(nom, prenom, dateNaissance, sexe);
        String suffixe = genererSuffixeUnique(prefixe, institutionId.trim());
        return prefixe + suffixe;
    }

    private static String construirePrefixe(String nom, String prenom, java.util.Date dateNaissance, String sexe) {
        // 1. Sécurité sur le Nom (3 lettres)
        String partNom = "XXX";
        if (nom != null && !nom.trim().isEmpty()) {
            String n = nom.trim().toUpperCase();
            partNom = n.substring(0, Math.min(3, n.length()));
            while (partNom.length() < 3) partNom += "X";
        }

        // 2. Première lettre du prénom
        String partPrenom = "X";
        if (prenom != null && !prenom.trim().isEmpty()) {
            partPrenom = prenom.trim().toUpperCase().substring(0, 1);
        }

        // 3. Date de naissance
        String partDate;
        if (dateNaissance != null) {
            partDate = new SimpleDateFormat("yyyyMMdd").format(dateNaissance);
        } else {
            partDate = new SimpleDateFormat("yyyy").format(new java.util.Date()) + "0101";
        }

        // 4. Sexe : 'G' pour masculin, 'F' pour féminin
        String partSexe = "G";
        if (sexe != null && !sexe.trim().isEmpty()) {
            String s = sexe.trim().toUpperCase();
            if (s.startsWith("F")) partSexe = "F";
            else if (s.startsWith("M") || s.startsWith("G")) partSexe = "G";
        }

        return partNom + partPrenom + partDate + partSexe;
    }

    private static String genererSuffixeUnique(String prefixe, String institutionId) {
        String suffixe;
        int maxAttempts = 100;
        int attempts = 0;
        do {
            int nombre = 1 + random.nextInt(9_999_999); // 1 à 9 999 999
            suffixe = String.format("%07d", nombre);
            attempts++;
            if (attempts > maxAttempts) {
                // Sécurité : en cas d'échec, on utilise un timestamp
                long timestamp = System.currentTimeMillis() % 10_000_000;
                suffixe = String.format("%07d", timestamp);
                break;
            }
        } while (suffixeExiste(prefixe, suffixe, institutionId));
        return suffixe;
    }

    /**
     * Vérifie si un identifiant complet (préfixe + suffixe) existe déjà pour cette institution.
     */
    private static boolean suffixeExiste(String prefixe, String suffixe, String institutionId) {
        String idComplet = prefixe + suffixe;
        String sql = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_ETUDIANTS
                + " WHERE numero_identifiant = ? AND institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idComplet);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
        }
        return false;
    }

    // ==================== AUTRES MÉTHODES (inchangées) ====================

    public static String genererIDAdmin() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(alphabet.charAt(random.nextInt(26)));
        }
        for (int i = 0; i < 5; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    public static String genererCodeCours(String nomMatiere) {
    String prefixe = (nomMatiere != null && !nomMatiere.isEmpty())
            ? nomMatiere.substring(0, Math.min(5, nomMatiere.length())).toUpperCase()
            : "XXXXX";
    while (prefixe.length() < 5)
        prefixe += "X";
    
    // Ajout d'un timestamp pour garantir l'unicité
    String timestamp = String.valueOf(System.currentTimeMillis() % 100000);
    return prefixe + String.format("%05d", Integer.valueOf(timestamp));
}

// ============================================================
// GÉNÉRATION ID PROFESSEUR
// ============================================================
/**
 * ✅ Génère un identifiant professeur unique au format :
 *    NOM(3) + PRENOM(1) + yyyyMMdd + Sexe + Suffixe(7)
 *    Ex : CLEG20000903G5245040
 *
 * Vérifie l'unicité dans la table `professeurs`.
 */
public static String genererIDProfesseur(String nom, String prenom,
                                           Date dateNaissance, String sexe,
                                           String institutionId) {
    if (institutionId == null || institutionId.trim().isEmpty()) {
        throw new IllegalArgumentException("L'institutionId est obligatoire.");
    }

    String prefixe = construirePrefixe(nom, prenom, dateNaissance, sexe);
    String suffixe = genererSuffixeUniqueProfesseur(prefixe, institutionId.trim());
    return prefixe + suffixe;
}

/**
 * ✅ Génère un suffixe unique pour un professeur (7 chiffres).
 *    Vérifie dans la table `professeurs`.
 */
private static String genererSuffixeUniqueProfesseur(String prefixe, String institutionId) {
    String suffixe;
    int maxAttempts = 100;
    int attempts = 0;

    do {
        int nombre = 1 + random.nextInt(9_999_999);
        suffixe = String.format("%07d", nombre);
        attempts++;

        if (attempts > maxAttempts) {
            long timestamp = System.currentTimeMillis() % 10_000_000;
            suffixe = String.format("%07d", timestamp);
            break;
        }
    } while (suffixeExisteProfesseur(prefixe, suffixe, institutionId));

    return suffixe;
}

/**
 * ✅ Vérifie si l'ID complet existe déjà dans `professeurs`.
 */
private static boolean suffixeExisteProfesseur(String prefixe, String suffixe,
                                                 String institutionId) {
    String idComplet = prefixe + suffixe;
    String sql = "SELECT COUNT(*) FROM " + MigrationManager.TABLE_PROFESSEURS
            + " WHERE numero_identifiant_professeur = ? AND institution_id = ?";

    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, idComplet);
        ps.setString(2, institutionId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1) > 0;
        }
    } catch (SQLException e) {
        // silent
    }
    return false;
}

    public static String genererCodeMatiere(String nom, String prenom, Date dateNaissance, String promotion) {
        String base = (nom + prenom + new SimpleDateFormat("yyyyMMdd").format(dateNaissance) + promotion)
                .replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.length() > 15)
            base = base.substring(0, 15);
        return base + String.format("%05d", random.nextInt(100000));
    }

    public static String genererNumeroSequentiel() {
        return String.valueOf(compteurOrdre.getAndIncrement());
    }

    public static String genererMatricule(String annee, String codeClasse) {
        String anneePart = (annee != null && annee.length() >= 2) ? annee.substring(2, 4) : "00";
        String seq = String.format("%04d", compteurOrdre.getAndIncrement() % 10000);
        return anneePart + "-" + codeClasse + "-" + seq;
    }

    public static String genererCodeClasse(String nomClasse, int niveau, String anneeAcademique) {
    // 1. Extraire un préfixe propre du nom de la classe (ex: "Licence Informatique" -> "LICIN")
    String base = "CLS";
    if (nomClasse != null && !nomClasse.trim().isEmpty()) {
        base = nomClasse.trim().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (base.length() > 5) {
            base = base.substring(0, 5);
        }
    }
    
    // 2. Extraire l'année courte (ex: "2025-2026" -> "2526" ou les 4 premiers caractères)
    String anneePart = "0000";
    if (anneeAcademique != null && !anneeAcademique.trim().isEmpty()) {
        anneePart = anneeAcademique.replaceAll("[^0-9]", "");
        if (anneePart.length() >= 4) {
            anneePart = anneePart.substring(0, 4);
        }
    }

    // 3. Ajouter un suffixe aléatoire court ou séquentiel pour garantir l'unicité
    String randomSuffix = String.format("%03d", random.nextInt(1000));

    return base + "-N" + niveau + "-" + anneePart + "-" + randomSuffix;
}

    public static String genererCodeSession(String anneeAcademique, String typeSession) {
        String type = (typeSession != null && !typeSession.isEmpty())
                ? typeSession.substring(0, Math.min(3, typeSession.length())).toUpperCase()
                : "SES";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String suffixe = timestamp.length() > 8 ? timestamp.substring(timestamp.length() - 8) : timestamp;
        return anneeAcademique + "_" + type + "_" + suffixe;
    }

    public static String genererMotDePasseTemporaire() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++)
            sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    public static String genererReferencePaiement() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String suffixe = String.format("%04d", random.nextInt(10000));
        return "PAI" + timestamp + suffixe;
    }
}