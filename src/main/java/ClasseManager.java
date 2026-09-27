import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ClasseManager {

    private static final Logger LOGGER = Logger.getLogger(ClasseManager.class.getName());
    private final ClasseData classeData;

    public ClasseManager() {
        this.classeData = new ClasseData();
    }

    // =========================================================
    // NOMBRE D'ÉTUDIANTS RÉEL (calculé depuis la table etudiants)
    // =========================================================

    /**
     * ✅ Compte les étudiants réellement inscrits dans une classe
     *    pour une année et une période données.
     *    Ce n'est PAS le champ `nombre_etudiants` de la table classes
     *    (qui est un compteur dénormalisé pouvant être désynchronisé).
     */
    public int getNombreEtudiantsActuel(String institutionId, String nomClasse,
                                         String anneeAcademique, String periode) throws SQLException {
        if (institutionId == null || institutionId.isBlank()
                || nomClasse == null || nomClasse.isBlank()) {
            return 0;
        }

        String sql = """
            SELECT COUNT(*) FROM etudiants
            WHERE institution_id = ?
              AND classe = ?
              AND annee_academique = ?
              AND periode = ?
        """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setQueryTimeout(30);
            ps.setString(1, institutionId);
            ps.setString(2, nomClasse);
            ps.setString(3, anneeAcademique);
            ps.setString(4, periode);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(() -> "❌ Erreur comptage étudiants pour " + nomClasse
                    + " (" + anneeAcademique + ", " + periode + ") : " + e.getMessage());
            throw e;
        }
        return 0;
    }

    // =========================================================
    // RECHERCHE FILTRÉE (déléguée à ClasseData)
    // =========================================================

    public List<Classe> obtenirClassesFiltrees(String institutionId, String motCle,
                                                String annee, String periode,
                                                String nomClasse) throws Exception {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        return classeData.obtenirClassesFiltrees(institutionId, motCle, annee, periode, nomClasse);
    }

    // =========================================================
    // CRÉATION
    // =========================================================

    /**
     * Enregistrer une nouvelle classe avec validation dynamique de la moyenne.
     */
    public boolean enregistrerClasse(Classe classe) throws Exception {
        validerClasse(classe);

        // ✅ Vérification de doublon par institution
        Classe existant = classeData.trouverParCode(classe.getCodeClasse(), classe.getInstitutionId());
        if (existant != null) {
            LOGGER.warning(() -> "⚠️ Une classe avec le code '" + classe.getCodeClasse()
                    + "' existe déjà dans cette institution.");
            throw new IllegalStateException("Une classe avec le code '"
                    + classe.getCodeClasse() + "' existe déjà dans cette institution.");
        }

        // ✅ Valeurs par défaut
        if (classe.getDateCreation() == null) {
            classe.setDateCreation(new Date());
        }
        if (classe.getStatut() == null || classe.getStatut().isBlank()) {
            classe.setStatut("ACTIF");
        }

        LOGGER.info(() -> "📝 Enregistrement d'une nouvelle classe: " + classe.getCodeClasse());
        return classeData.ajouter(classe);
    }

    // =========================================================
    // MODIFICATION
    // =========================================================

    /**
     * Mettre à jour une classe avec validation dynamique de la moyenne.
     */
    public boolean modifierClasse(Classe classe) throws Exception {
        validerClasse(classe);

        Classe existant = classeData.trouverParCode(classe.getCodeClasse(), classe.getInstitutionId());
        if (existant == null) {
            LOGGER.warning(() -> "⚠️ La classe avec le code " + classe.getCodeClasse()
                    + " n'existe pas dans cette institution.");
            throw new IllegalArgumentException("La classe avec le code "
                    + classe.getCodeClasse() + " n'existe pas dans cette institution.");
        }

        LOGGER.info(() -> "🔄 Modification de la classe: " + classe.getCodeClasse());
        return classeData.modifier(classe);
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================

    public boolean supprimerClasse(String codeClasse, String institutionId) throws Exception {
        if (codeClasse == null || codeClasse.isBlank()) {
            throw new IllegalArgumentException("Le code classe ne peut pas être vide.");
        }
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }

        Classe existant = classeData.trouverParCode(codeClasse, institutionId);
        if (existant == null) {
            LOGGER.warning(() -> "⚠️ La classe avec le code " + codeClasse
                    + " n'existe pas dans cette institution.");
            throw new IllegalArgumentException("La classe avec le code "
                    + codeClasse + " n'existe pas dans cette institution.");
        }

        LOGGER.info(() -> "🗑️ Suppression de la classe: " + codeClasse);
        return classeData.supprimer(codeClasse, institutionId);
    }

    // =========================================================
    // LECTURE
    // =========================================================

    public Classe obtenirClasseParCode(String codeClasse, String institutionId) throws Exception {
        if (codeClasse == null || codeClasse.isBlank()) {
            throw new IllegalArgumentException("Le code classe ne peut pas être vide.");
        }
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        return classeData.trouverParCode(codeClasse, institutionId);
    }

    public List<Classe> obtenirToutesLesClasses(String institutionId) throws Exception {
        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        LOGGER.info(() -> "📚 Chargement de toutes les classes pour l'institution: " + institutionId);
        return classeData.toutLister(institutionId);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    /**
     * Valide dynamiquement les attributs d'une classe selon la BDD.
     */
    private void validerClasse(Classe classe) throws Exception {
        if (classe == null) {
            throw new IllegalArgumentException("La classe ne peut pas être nulle.");
        }
        if (classe.getInstitutionId() == null || classe.getInstitutionId().isBlank()) {
            throw new IllegalArgumentException("L'institution est obligatoire.");
        }
        if (classe.getCodeClasse() == null || classe.getCodeClasse().isBlank()) {
            throw new IllegalArgumentException("Le code de la classe est obligatoire.");
        }
        if (classe.getNomClasse() == null || classe.getNomClasse().isBlank()) {
            throw new IllegalArgumentException("Le nom de la classe est obligatoire.");
        }
        if (classe.getCapaciteClasse() <= 0) {
            throw new IllegalArgumentException("La capacité de la classe doit être supérieure à 0.");
        }

        // 🔄 Récupération dynamique du plafond de la moyenne
        double maxMoyenne = obtenirMoyennePassageMaxDynamique(classe.getInstitutionId());

        if (classe.getMoyennePassage() < 0.0 || classe.getMoyennePassage() > maxMoyenne) {
            throw new IllegalArgumentException("La moyenne de passage doit être comprise entre 0 et "
                    + maxMoyenne + ".");
        }
    }

    // =========================================================
    // MOYENNE DE PASSAGE MAX
    // =========================================================

    /**
     * Extrait dynamiquement la moyenne de passage maximale configurée
     * pour l'institution. Fallback sur 20.0 en cas d'erreur.
     *
     * ✅ Parsing robuste : gère "10", "10.5", "10,5", "10/20",
     *    "Moyenne: 10/20", "10.5/20", etc.
     */
    private double obtenirMoyennePassageMaxDynamique(String institutionId) {
        double moyenneMax = 20.0;   // Valeur par défaut de sécurité
        if (institutionId == null || institutionId.isBlank()) {
            return moyenneMax;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            InstitutionData instData = new InstitutionData(conn);
            Institution inst = instData.readByIdOrInstitutionId(institutionId);

            if (inst != null && inst.getMoyenneDePassageInstitution() != null) {
                String rawVal = inst.getMoyenneDePassageInstitution().trim();
                if (!rawVal.isBlank()) {
                    Double parsed = parseMoyenneRobuste(rawVal);
                    if (parsed != null && parsed > 0) {
                        moyenneMax = parsed;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "⚠️ Impossible de lire la moyenne max pour "
                    + institutionId + " (" + e.getMessage() + "), utilisation de 20.0");
        }
        return moyenneMax;
    }

    /**
     * ✅ Parsing robuste d'une valeur de moyenne.
     *
     * Cas gérés :
     *   "10"          → 10.0
     *   "10.5"        → 10.5
     *   "10,5"        → 10.5
     *   "10/20"       → 10.0   (prend le numérateur)
     *   "10.5/20"     → 10.5
     *   "Moyenne: 10" → 10.0
     *   "10 sur 20"   → 10.0
     *
     * @return la valeur parsée, ou null si impossible.
     */
    private Double parseMoyenneRobuste(String rawVal) {
        if (rawVal == null || rawVal.isBlank()) return null;

        // 1. Remplacer la virgule par un point
        String normalized = rawVal.replace(",", ".");

        // 2. Si la valeur contient un "/", prendre la partie avant le "/"
        int slashIdx = normalized.indexOf('/');
        if (slashIdx > 0) {
            normalized = normalized.substring(0, slashIdx);
        }

        // 3. Extraire le premier nombre décimal trouvé (regex)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)")
                .matcher(normalized);

        if (m.find()) {
            try {
                return Double.valueOf(m.group(1));
            } catch (NumberFormatException e) {
                LOGGER.warning(() -> "⚠️ Valeur non parsable : " + rawVal);
            }
        }
        return null;
    }
}