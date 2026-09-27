import java.util.List;

/**
 * Génère le SQL de création de toutes les tables.
 *
 * ✅ ARCHITECTURE MULTI-INSTITUTIONS STRICTE + UUID :
 *   - PK composite (institution_id, id) pour les tables avec id local
 *   - `id VARCHAR(36)` (UUID) au lieu de AUTO_INCREMENT
 *   - UUID généré par l'application → PK stable entre LOCAL et REMOTE
 *   - FK composites (institution_id, xxx) → (institution_id, yyy)
 */
public class TableSchemaSQL {

    public record IndexDef(String name, List<String> columns) {}

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String getCreateTableSQL(String tableName) {
        return switch (tableName) {
            case MigrationManager.TABLE_INSTITUTIONS              -> createInstitutionTable();
            case MigrationManager.TABLE_ANNEES_ACADEMIQUES        -> createAnneeAcademiqueTable();
            case MigrationManager.TABLE_PERIODES                  -> createPeriodeTable();
            case MigrationManager.TABLE_ASSISTANCE                -> createAssistanceTable();
            case MigrationManager.TABLE_ACCES_ADMIN               -> createAccesAdminTable();
            case MigrationManager.TABLE_MODULES                   -> createModulesTable();
            case MigrationManager.TABLE_PROMOTIONS                -> createPromotionTable();
            case MigrationManager.TABLE_PARAMETRES_INSCRIPTION    -> createParametresInscriptionTable();
            case MigrationManager.TABLE_CLASSES                   -> createClasseTable();
            case MigrationManager.TABLE_OPTIONS                   -> createOptionsTable();
            case MigrationManager.TABLE_ETUDIANTS                 -> createEtudiantTable();
            case MigrationManager.TABLE_PROFESSEURS               -> createProfesseurTable();
            case MigrationManager.TABLE_COURS_LIENS               -> createCoursLiensTable();
            case MigrationManager.TABLE_MATIERES                  -> createMatiereTable();
            case MigrationManager.TABLE_ABSENCES                  -> createAbsenceTable();
            case MigrationManager.TABLE_ACCES                     -> createAccesTable();
            case MigrationManager.TABLE_MESSAGES                  -> createMessagesTable();
            case MigrationManager.TABLE_ATTESTATIONS              -> createAttestationTable();
            case MigrationManager.TABLE_ACCES_PROFESSEUR          -> createAccesProfesseurTable();
            case MigrationManager.TABLE_PROFESSEUR_MATIERES       -> createProfesseurMatieresTable();
            case MigrationManager.TABLE_NOTES                     -> createNoteTable();
            case MigrationManager.TABLE_EXAMENS                   -> createExamenTable();
            case MigrationManager.TABLE_QUIZ                  -> createQuizTable();
            case MigrationManager.TABLE_QUIZ_SECTION          -> createQuizSectionTable();
            case MigrationManager.TABLE_QUIZ_QUESTION         -> createQuizQuestionTable();
            case MigrationManager.TABLE_QUIZ_OPTION           -> createQuizOptionTable();
            case MigrationManager.TABLE_QUIZ_TENTATIVE        -> createQuizTentativeTable();
            case MigrationManager.TABLE_QUIZ_REPONSE_ETUDIANT -> createQuizReponseEtudiantTable();
            case MigrationManager.TABLE_DOCUMENTS_PROFESSEUR      -> createDocumentsProfesseurTable();
            case MigrationManager.TABLE_EVENEMENTS                -> createEvenementTable();
            case MigrationManager.TABLE_FRAIS_SCOLAIRES           -> createFraisScolairesTable();
            case MigrationManager.TABLE_MODALITES_PAIEMENT        -> createModalitesPaiementTable();
            case MigrationManager.TABLE_PROGRAMMES                -> createProgrammesTable();
            case MigrationManager.TABLE_PAIEMENTS_ETUDIANTS       -> createPaiementsEtudiantsTable();
            case MigrationManager.TABLE_RECETTES_EXTERNES         -> createRecettesExternesTable();
            case MigrationManager.TABLE_DEPENSES                  -> createDepensesTable();
            case MigrationManager.TABLE_EMPLOYES                  -> createEmployesTable();
            case MigrationManager.TABLE_BUDGET_CLASSE             -> createBudgetClasseTable();
            default -> null;
        };
    }

    public static List<IndexDef> getRequiredIndexes(String tableName) {
        return List.of(); // Les index sont créés par MigrationManager
    }

    public static List<String> getPrimaryKeyColumnsForTrigger(String tableName) {
        if (tableName == null) return List.of();
        return switch (tableName) {
            case MigrationManager.TABLE_INSTITUTIONS         -> List.of("institution_id");
            case MigrationManager.TABLE_ACCES_ADMIN           -> List.of("institution_id", "utilisateur_id");
            case MigrationManager.TABLE_ANNEES_ACADEMIQUES    -> List.of("institution_id", "annee_academique");
            case MigrationManager.TABLE_PERIODES              -> List.of("institution_id", "annee_academique", "periode");
            case MigrationManager.TABLE_PROMOTIONS            -> List.of("institution_id", "annee_academique", "promotion");
            case MigrationManager.TABLE_CLASSES               -> List.of("institution_id", "code_classe");
            case MigrationManager.TABLE_MODULES               -> List.of("institution_id", "id");
            case MigrationManager.TABLE_ETUDIANTS             -> List.of("institution_id", "numero_identifiant");
            case MigrationManager.TABLE_ACCES                 -> List.of("institution_id", "numero_identifiant");
            case MigrationManager.TABLE_ACCES_PROFESSEUR      -> List.of("institution_id", "numero_identifiant");
            case MigrationManager.TABLE_OPTIONS               -> List.of("institution_id", "option", "classe", "annee_academique");
            case MigrationManager.TABLE_PROFESSEURS           -> List.of("institution_id", "numero_identifiant_professeur");
            case MigrationManager.TABLE_MATIERES              -> List.of("institution_id", "code_cours");
            case MigrationManager.TABLE_COURS_LIENS           -> List.of("institution_id", "id");
            case MigrationManager.TABLE_PROFESSEUR_MATIERES   -> List.of("institution_id", "id");
            case MigrationManager.TABLE_NOTES                 -> List.of("institution_id", "id");
            case MigrationManager.TABLE_ABSENCES              -> List.of("institution_id", "id");
            case MigrationManager.TABLE_MESSAGES              -> List.of("institution_id", "id");
            case MigrationManager.TABLE_ATTESTATIONS          -> List.of("institution_id", "id");
            case MigrationManager.TABLE_PROGRAMMES            -> List.of("institution_id", "id");
            case MigrationManager.TABLE_DOCUMENTS_PROFESSEUR  -> List.of("institution_id", "id");
            case MigrationManager.TABLE_EVENEMENTS            -> List.of("institution_id", "id");
            case MigrationManager.TABLE_FRAIS_SCOLAIRES       -> List.of("institution_id", "id");
            case MigrationManager.TABLE_MODALITES_PAIEMENT    -> List.of("institution_id", "id");
            case MigrationManager.TABLE_EXAMENS               -> List.of("institution_id", "id");
            case MigrationManager.TABLE_PAIEMENTS_ETUDIANTS   -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ                  -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ_SECTION          -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ_QUESTION         -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ_OPTION           -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ_TENTATIVE        -> List.of("institution_id", "id");
            case MigrationManager.TABLE_QUIZ_REPONSE_ETUDIANT -> List.of("institution_id", "id");
            case MigrationManager.TABLE_RECETTES_EXTERNES     -> List.of("institution_id", "id");
            case MigrationManager.TABLE_DEPENSES              -> List.of("institution_id", "id");
            case MigrationManager.TABLE_EMPLOYES              -> List.of("institution_id", "id");
            case MigrationManager.TABLE_BUDGET_CLASSE         -> List.of("institution_id", "id");
            case MigrationManager.TABLE_ASSISTANCE            -> List.of("institution_id", "id");
            case MigrationManager.TABLE_PARAMETRES_INSCRIPTION -> List.of("institution_id", "id");
            default -> List.of();
        };
    }

    // ============================================================
    // TABLE INSTITUTIONS (racine — PK simple)
    // ============================================================
   private static String createInstitutionTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_INSTITUTIONS + " (" +
            "institution_id VARCHAR(50) PRIMARY KEY," +
            "nom_institution VARCHAR(255)," +
            "sigle_institution VARCHAR(100)," +
            "devise_institution VARCHAR(255)," +
            "type_institution VARCHAR(100)," +
            "niveau_institution VARCHAR(100)," +
            "categorie_institution VARCHAR(100)," +
            "logo_institution VARCHAR(255)," +
            "qrcode_institution LONGTEXT," +
            "adresse_institution VARCHAR(255)," +
            "code_postal_institution VARCHAR(50)," +
            "commune_institution VARCHAR(100)," +
            "departement_institution VARCHAR(100)," +
            "pays_institution VARCHAR(100)," +
            "mail_primaire VARCHAR(255)," +
            "mail_secondaire VARCHAR(255)," +
            "telephone_primaire VARCHAR(50)," +
            "telephone_secondaire VARCHAR(50)," +
            "site_web_institution VARCHAR(255)," +
            "smtp_password VARCHAR(255)," +
            "responsable_institution VARCHAR(255)," +
            "poste_responsable VARCHAR(100)," +
            "mail_responsable VARCHAR(255)," +
            "telephone_responsable VARCHAR(50)," +
            "moyenne_passage VARCHAR(50)," +
            "systeme_educatif VARCHAR(100)," +
            "statut_institution VARCHAR(50)," +
            "date_creation VARCHAR(50)," +
            "statut_validation VARCHAR(20) DEFAULT 'PENDING'," +          // ✅ AJOUTÉ
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}
    private static String createAnneeAcademiqueTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ANNEES_ACADEMIQUES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "date_debut DATE NOT NULL," +
                "date_fin DATE NOT NULL," +
                "est_active BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, annee_academique)," +
                "CONSTRAINT fk_annees_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createPeriodeTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PERIODES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "periode VARCHAR(100) NOT NULL," +
                "date_debut DATE," +
                "date_fin DATE," +
                "est_active BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, annee_academique, periode)," +
                "CONSTRAINT fk_periodes_annee FOREIGN KEY (institution_id, annee_academique) " +
                "  REFERENCES " + MigrationManager.TABLE_ANNEES_ACADEMIQUES + "(institution_id, annee_academique) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createPromotionTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PROMOTIONS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "promotion VARCHAR(100) NOT NULL," +
                "date_debut DATE NOT NULL," +
                "date_fin DATE NOT NULL," +
                "est_active BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, annee_academique, promotion)," +
                "CONSTRAINT fk_promotions_annee FOREIGN KEY (institution_id, annee_academique) " +
                "  REFERENCES " + MigrationManager.TABLE_ANNEES_ACADEMIQUES + "(institution_id, annee_academique) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createClasseTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_CLASSES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "code_classe VARCHAR(50) NOT NULL," +
                "nom_classe VARCHAR(255) NOT NULL," +
                "niveau_classe INT DEFAULT 1," +
                "capacite_classe INT DEFAULT 30," +
                "nombre_etudiants INT DEFAULT 0," +
                "nombre_matieres INT DEFAULT 0," +
                "periode VARCHAR(50)," +
                "annee_academique VARCHAR(20)," +
                "promotion VARCHAR(100)," +
                "statut VARCHAR(20) DEFAULT 'ACTIF'," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "moyenne_passage DECIMAL(5,2) DEFAULT 10.00," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, code_classe)," +
                "CONSTRAINT fk_classes_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createOptionsTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_OPTIONS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "`option` VARCHAR(255) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "periode VARCHAR(50)," +
                "promotion VARCHAR(100)," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, `option`, classe, annee_academique)," +
                "CONSTRAINT fk_options_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createEtudiantTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ETUDIANTS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "nom VARCHAR(255) NOT NULL," +
                "prenom VARCHAR(255)," +
                "sexe VARCHAR(20) NOT NULL," +
                "date_naissance DATE," +
                "groupe_sanguin VARCHAR(10)," +
                "telephone VARCHAR(50)," +
                "email VARCHAR(255)," +
                "adresse TEXT," +
                "classe VARCHAR(100)," +
                "annee_academique VARCHAR(50)," +
                "periode VARCHAR(20) NOT NULL," +
                "promotion VARCHAR(100)," +
                "`option` VARCHAR(100)," +
                "matricule VARCHAR(100)," +
                "ninu VARCHAR(100)," +
                "statut VARCHAR(50) DEFAULT 'ACTIF'," +
                "date_inscription DATE," +
                "departement_naissance VARCHAR(100)," +
                "commune_naissance VARCHAR(100)," +
                "departement_residence VARCHAR(100)," +
                "commune_residence VARCHAR(100)," +
                "nom_pere VARCHAR(255)," +
                "prenom_pere VARCHAR(255)," +
                "nom_mere VARCHAR(255)," +
                "prenom_mere VARCHAR(255)," +
                "telephone_parents VARCHAR(50)," +
                "residence_parents VARCHAR(255)," +
                "lien_parente VARCHAR(100)," +
                "type_responsable VARCHAR(100)," +
                "nom_responsable VARCHAR(255)," +
                "prenom_responsable VARCHAR(255)," +
                "photo_path VARCHAR(500)," +
                "observations TEXT," +
                "empreinte_path VARCHAR(500)," +
                "visage_path VARCHAR(500)," +
                "biometrie_active BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, numero_identifiant)," +
                "UNIQUE KEY unique_matricule_institution (institution_id, matricule)," +
                "UNIQUE KEY unique_ninu_institution (institution_id, ninu)," +
                "CONSTRAINT fk_etudiants_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createProfesseurTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PROFESSEURS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "numero_identifiant_professeur VARCHAR(50) NOT NULL," +
                "nom VARCHAR(255) NOT NULL," +
                "prenom VARCHAR(255)," +
                "sexe VARCHAR(20) NOT NULL," +
                "date_naissance DATE," +
                "groupe_sanguin VARCHAR(10)," +
                "departement_naissance VARCHAR(100)," +
                "commune_naissance VARCHAR(100)," +
                "telephone VARCHAR(50)," +
                "email VARCHAR(255)," +
                "adresse TEXT," +
                "pays_habitation VARCHAR(100)," +
                "departement_habitation VARCHAR(100)," +
                "commune_habitation VARCHAR(100)," +
                "matricule VARCHAR(100)," +
                "ninu VARCHAR(100)," +
                "diplome VARCHAR(255)," +
                "specialite VARCHAR(255)," +
                "date_embauche DATE," +
                "salaire DECIMAL(12,2) DEFAULT 0.00," +
                "statut VARCHAR(50) DEFAULT 'ACTIF'," +
                "photo_path VARCHAR(500)," +
                "observations TEXT," +
                "situation_matrimoniale VARCHAR(100)," +
                "empreinte_path VARCHAR(500)," +
                "visage_path VARCHAR(500)," +
                "biometrie_active BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, numero_identifiant_professeur)," +
                "UNIQUE KEY unique_email_prof_institution (institution_id, email)," +
                "UNIQUE KEY unique_matricule_prof_institution (institution_id, matricule)," +
                "UNIQUE KEY unique_ninu_prof_institution (institution_id, ninu)," +
                "CONSTRAINT fk_professeurs_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createMatiereTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_MATIERES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "code_cours VARCHAR(100) NOT NULL," +
                "nom_matiere VARCHAR(255) NOT NULL," +
                "coefficient DECIMAL(5,2) DEFAULT 1.00," +
                "note_passage DECIMAL(5,2)," +
                "note_minimale DECIMAL(5,2)," +
                "note_maximale DECIMAL(5,2)," +
                "classe_matiere VARCHAR(100)," +
                "periode VARCHAR(100)," +
                "statut VARCHAR(50) DEFAULT 'ACTIF'," +
                "annee_academique VARCHAR(50)," +
                "nombre_credits INT," +
                "duree_cours VARCHAR(100)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, code_cours)," +
                "CONSTRAINT fk_matieres_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    // ============================================================
    // TABLES AVEC id UUID (VARCHAR(36))
    // ============================================================

    private static String createAccesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ACCES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "connexion BOOLEAN DEFAULT TRUE," +
                "profil BOOLEAN DEFAULT TRUE," +
                "notes BOOLEAN DEFAULT TRUE," +
                "bulletin BOOLEAN DEFAULT TRUE," +
                "paiement BOOLEAN DEFAULT TRUE," +
                "messages BOOLEAN DEFAULT TRUE," +
                "documents BOOLEAN DEFAULT TRUE," +
                "edt BOOLEAN DEFAULT TRUE," +
                "attestations BOOLEAN DEFAULT TRUE," +
                "absences BOOLEAN DEFAULT TRUE," +
                "assistance BOOLEAN DEFAULT TRUE," +
                "statistiques BOOLEAN DEFAULT TRUE," +
                "activites BOOLEAN DEFAULT TRUE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, numero_identifiant)," +
                "CONSTRAINT fk_acces_etudiant FOREIGN KEY (institution_id, numero_identifiant) " +
                "  REFERENCES " + MigrationManager.TABLE_ETUDIANTS + "(institution_id, numero_identifiant) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createAccesProfesseurTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ACCES_PROFESSEUR + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "profil BOOLEAN DEFAULT TRUE," +
                "cours BOOLEAN DEFAULT TRUE," +
                "notes BOOLEAN DEFAULT TRUE," +
                "edt BOOLEAN DEFAULT TRUE," +
                "absences BOOLEAN DEFAULT TRUE," +
                "documents BOOLEAN DEFAULT TRUE," +
                "messages BOOLEAN DEFAULT TRUE," +
                "parametres BOOLEAN DEFAULT TRUE," +
                "programmes BOOLEAN DEFAULT TRUE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, numero_identifiant)," +
                "CONSTRAINT fk_acces_professeur FOREIGN KEY (institution_id, numero_identifiant) " +
                "  REFERENCES " + MigrationManager.TABLE_PROFESSEURS + "(institution_id, numero_identifiant_professeur) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createAccesAdminTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(MigrationManager.TABLE_ACCES_ADMIN).append(" (");
        sb.append("institution_id VARCHAR(50) NOT NULL,");
        sb.append("utilisateur_id VARCHAR(50) NOT NULL,");
        sb.append("nom VARCHAR(255) NOT NULL,");
        sb.append("prenom VARCHAR(255) NOT NULL,");
        sb.append("email VARCHAR(255) NOT NULL,");
        sb.append("mot_de_passe VARCHAR(255) NOT NULL,");

        for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
            String colName = module.name().toLowerCase() + "_perms";
            sb.append(colName).append(" INT DEFAULT 0,");
        }

        sb.append("niveau INT NOT NULL DEFAULT 1,");
        sb.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,");
        sb.append("updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,");
        sb.append("last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,");
        sb.append("source VARCHAR(10) DEFAULT 'LOCAL',");
        sb.append("PRIMARY KEY (institution_id, utilisateur_id),");
        sb.append("CONSTRAINT fk_acces_admin_institution FOREIGN KEY (institution_id) ");
        sb.append("  REFERENCES ").append(MigrationManager.TABLE_INSTITUTIONS);
        sb.append("(institution_id) ON DELETE CASCADE ON UPDATE CASCADE");
        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        return sb.toString();
    }

    private static String createModulesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_MODULES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "nom_module VARCHAR(255) NOT NULL," +
                "icone VARCHAR(255)," +
                "lien VARCHAR(255)," +
                "description TEXT," +
                "ordre INT DEFAULT 0," +
                "permission_key VARCHAR(100) DEFAULT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "CONSTRAINT fk_modules_institution FOREIGN KEY (institution_id) " +
                "  REFERENCES " + MigrationManager.TABLE_INSTITUTIONS + "(institution_id) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createCoursLiensTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_COURS_LIENS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "titre VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "url VARCHAR(500) NOT NULL," +
                "date_debut DATETIME NOT NULL," +
                "date_fin DATETIME NOT NULL," +
                "professeur_id VARCHAR(50) NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "CONSTRAINT fk_cours_liens_professeur FOREIGN KEY (institution_id, professeur_id) " +
                "  REFERENCES " + MigrationManager.TABLE_PROFESSEURS + "(institution_id, numero_identifiant_professeur) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createProfesseurMatieresTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PROFESSEUR_MATIERES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "code_cours VARCHAR(100) NOT NULL," +
                "nom_matiere VARCHAR(200)," +
                "classe VARCHAR(100) NOT NULL," +
                "coefficient DECIMAL(5,2) DEFAULT 1.00," +
                "jour VARCHAR(20) NOT NULL," +
                "heure_debut TIME NOT NULL," +
                "heure_fin TIME," +
                "duree_minutes INT DEFAULT 0," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "periode VARCHAR(50)," +
                "date_affectation DATE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_creneau (institution_id, numero_identifiant, code_cours, classe, jour, heure_debut, annee_academique, periode)," +
                "CONSTRAINT fk_prof_matieres_professeur FOREIGN KEY (institution_id, numero_identifiant) " +
                "  REFERENCES " + MigrationManager.TABLE_PROFESSEURS + "(institution_id, numero_identifiant_professeur) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE," +
                "CONSTRAINT fk_prof_matieres_matiere FOREIGN KEY (institution_id, code_cours) " +
                "  REFERENCES " + MigrationManager.TABLE_MATIERES + "(institution_id, code_cours) " +
                "  ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createNoteTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_NOTES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "numero_etudiant VARCHAR(50) NOT NULL," +
                "nom_etudiant VARCHAR(255)," +
                "prenom_etudiant VARCHAR(255)," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "matiere_nom VARCHAR(255)," +
                "code_cours VARCHAR(100) NOT NULL," +
                "classe VARCHAR(100)," +
                "numero_enseignant VARCHAR(50)," +
                "nom_enseignant VARCHAR(255)," +
                "prenom_enseignant VARCHAR(255)," +
                "note_value DECIMAL(5,2)," +
                "note_sur DECIMAL(5,2) DEFAULT 100.00," +
                "note_base DECIMAL(5,2)," +
                "coefficient DECIMAL(5,2) DEFAULT 1.00," +
                "note_minimale DECIMAL(5,2)," +
                "note_maximale DECIMAL(5,2)," +
                "note_passage DECIMAL(5,2)," +
                "rang_etudiant INT," +
                "periode VARCHAR(50) NOT NULL," +
                "est_validee BOOLEAN DEFAULT FALSE," +
                "is_verified BOOLEAN DEFAULT FALSE," +
                "verified_by VARCHAR(255) DEFAULT NULL," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_note (institution_id, numero_etudiant, code_cours, periode, annee_academique)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createAbsenceTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ABSENCES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "date_absence TIMESTAMP NOT NULL," +
                "justifiee BOOLEAN DEFAULT FALSE," +
                "motif TEXT," +
                "code_cours VARCHAR(100)," +
                "periode VARCHAR(50)," +
                "promotion VARCHAR(100)," +
                "annee_academique VARCHAR(50)," +
                "classe VARCHAR(100)," +
                "personne_type VARCHAR(20) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "created_by VARCHAR(50)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createMessagesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_MESSAGES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "expediteur_id VARCHAR(50) NOT NULL," +
                "expediteur_type ENUM('PROFESSEUR', 'ETUDIANT', 'ADMIN') NOT NULL," +
                "destinataire_id VARCHAR(50) NOT NULL," +
                "destinataire_type ENUM('PROFESSEUR', 'ETUDIANT', 'ADMIN') NOT NULL," +
                "sujet VARCHAR(255) NOT NULL," +
                "contenu TEXT NOT NULL," +
                "lu BOOLEAN DEFAULT FALSE," +
                "date_envoi TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "reponse_a VARCHAR(36) NULL," +
                "est_supprime_expediteur BOOLEAN DEFAULT FALSE," +
                "est_supprime_destinataire BOOLEAN DEFAULT FALSE," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createAttestationTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ATTESTATIONS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "type VARCHAR(100) NOT NULL," +
                "date_emission DATE NOT NULL," +
                "contenu_html TEXT," +
                "reference VARCHAR(100)," +
                "numero VARCHAR(50)," +
                "classe VARCHAR(100)," +
                "annee_academique VARCHAR(50)," +
                "session VARCHAR(50)," +
                "`option` VARCHAR(100)," +
                "periode VARCHAR(50)," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createProgrammesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PROGRAMMES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "professeur_id VARCHAR(50) NOT NULL," +
                "code_cours VARCHAR(100) NOT NULL," +
                "matiere VARCHAR(100) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "periode VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "chapitre VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "titre_chapitre TEXT," +
                "date_debut DATE," +
                "date_fin DATE," +
                "est_vu BOOLEAN DEFAULT FALSE," +
                "duree_prevue INT DEFAULT 0," +
                "duree_reelle INT DEFAULT 0," +
                "cotation INT DEFAULT 1," +
                "est_termine BOOLEAN DEFAULT FALSE," +
                "ordre INT DEFAULT 0," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createDocumentsProfesseurTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_DOCUMENTS_PROFESSEUR + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "professeur_id VARCHAR(50) NOT NULL," +
                "titre VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "fichier_nom VARCHAR(255)," +
                "fichier_chemin VARCHAR(500)," +
                "url VARCHAR(500)," +
                "type VARCHAR(50)," +
                "matiere VARCHAR(100) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "periode VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "est_public BOOLEAN DEFAULT FALSE," +
                "taille BIGINT DEFAULT 0," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createExamenTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_EXAMENS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "professeur_id VARCHAR(50) NOT NULL," +
                "code_cours VARCHAR(100) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "periode VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(50) NOT NULL," +
                "titre VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "date_examen DATE NOT NULL," +
                "heure_debut TIME," +
                "heure_fin TIME," +
                "duree_minutes INT DEFAULT 0," +
                "salle VARCHAR(100)," +
                "coefficient DECIMAL(5,2) DEFAULT 1.00," +
                "note_maximale DECIMAL(5,2) DEFAULT 20.00," +
                "statut VARCHAR(20) DEFAULT 'PREVU'," +
                "fichier_nom VARCHAR(255)," +
                "fichier_chemin VARCHAR(500)," +
                "fichier_type VARCHAR(50)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createEvenementTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_EVENEMENTS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "titre VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "date_debut TIMESTAMP NOT NULL," +
                "date_fin TIMESTAMP NULL," +
                "lieu VARCHAR(255)," +
                "classe VARCHAR(50)," +
                "annee_academique VARCHAR(20)," +
                "periode VARCHAR(20)," +
                "etudiants_emails TEXT," +
                "professeurs_emails TEXT," +
                "statut VARCHAR(50) DEFAULT 'À venir'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createFraisScolairesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_FRAIS_SCOLAIRES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "montant_total DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_frais (institution_id, classe, annee_academique)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createModalitesPaiementTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_MODALITES_PAIEMENT + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "versement_num INT NOT NULL," +
                "montant DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_modalite (institution_id, classe, annee_academique, versement_num)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createPaiementsEtudiantsTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PAIEMENTS_ETUDIANTS + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "numero_identifiant VARCHAR(50) NOT NULL," +
                "montant DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
                "moyen_paiement VARCHAR(50) NOT NULL," +
                "reference VARCHAR(100)," +
                "date_paiement DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "statut VARCHAR(20) DEFAULT 'VALIDÉ'," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createRecettesExternesTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_RECETTES_EXTERNES + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "categorie VARCHAR(100) NOT NULL," +
                "date_recette DATE NOT NULL," +
                "montant DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
                "donateur VARCHAR(255)," +
                "receveur VARCHAR(255)," +
                "motif TEXT," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

   private static String createDepensesTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_DEPENSES + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "annee_academique VARCHAR(20)," +
            "mois VARCHAR(20)," +
            "classes TEXT," +
            "executant VARCHAR(255)," +
            "motif TEXT," +
            "nom_depense VARCHAR(255)," +
            "type_depense VARCHAR(100)," +
            "description_depense TEXT," +
            "responsable_depense VARCHAR(255)," +
            "mode_paiement VARCHAR(50)," +
            "numero_facture VARCHAR(100)," +
            "ninu VARCHAR(100)," +
            "matricule VARCHAR(100)," +
            "justification_depense TEXT," +
            "montant DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
            "montant_total_depenses DECIMAL(12,2) DEFAULT 0.00," +
            "date_depense DATE," +
            "article VARCHAR(255)," +
            "fournisseur VARCHAR(255)," +
            "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}
  private static String createEmployesTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_EMPLOYES + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "employe_id INT NOT NULL," +
            "annee_academique VARCHAR(20)," +
            "mois VARCHAR(20)," +
            "classes TEXT," +
            "nom VARCHAR(255) NOT NULL," +
            "poste VARCHAR(255)," +
            "salaire_base DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
            "deductions DECIMAL(12,2) DEFAULT 0.00," +
            "date_embauche DATE," +
            "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}
    private static String createBudgetClasseTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_BUDGET_CLASSE + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "total_recettes DECIMAL(12,2) DEFAULT 0.00," +
                "total_depenses DECIMAL(12,2) DEFAULT 0.00," +
                "solde DECIMAL(12,2) DEFAULT 0.00," +
                "date_calcul TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_budget (institution_id, classe, annee_academique)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    private static String createAssistanceTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_ASSISTANCE + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "etudiant_id VARCHAR(50) NOT NULL," +
                "type_demande VARCHAR(100) NOT NULL," +
                "sujet VARCHAR(255) NOT NULL," +
                "message TEXT NOT NULL," +
                "statut VARCHAR(20) DEFAULT 'EN_ATTENTE'," +
                "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "fichier_joint VARCHAR(255)," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }
// ============================================================
// TABLES QUIZ
// ============================================================

private static String createQuizTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "professeur_id VARCHAR(50) NOT NULL," +
            "code_cours VARCHAR(100)," +
            "classe VARCHAR(100)," +
            "periode VARCHAR(100)," +
            "annee_academique VARCHAR(50)," +
            "titre VARCHAR(255) NOT NULL," +
            "description TEXT," +
            "date_debut DATETIME," +
            "date_expiration DATETIME NOT NULL," +
            "duree_minutes INT DEFAULT 30," +
            "note_totale DECIMAL(10,2) DEFAULT 0.00," +
            "statut VARCHAR(20) DEFAULT 'BROUILLON'," +
            "date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "CONSTRAINT fk_quiz_professeur FOREIGN KEY (institution_id, professeur_id) " +
            "  REFERENCES " + MigrationManager.TABLE_PROFESSEURS + "(institution_id, numero_identifiant_professeur) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE," +
            "INDEX idx_quiz_prof (institution_id, professeur_id)," +
            "INDEX idx_quiz_exp (institution_id, date_expiration, statut)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}

private static String createQuizSectionTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ_SECTION + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "quiz_id VARCHAR(36) NOT NULL," +
            "titre VARCHAR(255) NOT NULL," +
            "description TEXT," +
            "ordre INT DEFAULT 0," +
            "cote_section DECIMAL(10,2) DEFAULT 0.00," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "CONSTRAINT fk_quiz_section_quiz FOREIGN KEY (institution_id, quiz_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}

private static String createQuizQuestionTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ_QUESTION + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "section_id VARCHAR(36) NOT NULL," +
            "enonce TEXT NOT NULL," +
            "type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE'," +
            "cote DECIMAL(10,2) DEFAULT 1.00," +
            "ordre INT DEFAULT 0," +
            "explication TEXT," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "CONSTRAINT fk_quiz_question_section FOREIGN KEY (institution_id, section_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ_SECTION + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}

private static String createQuizOptionTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ_OPTION + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "question_id VARCHAR(36) NOT NULL," +
            "texte TEXT NOT NULL," +
            "est_correcte BOOLEAN DEFAULT FALSE," +
            "ordre INT DEFAULT 0," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "CONSTRAINT fk_quiz_option_question FOREIGN KEY (institution_id, question_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ_QUESTION + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}

private static String createQuizTentativeTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ_TENTATIVE + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "quiz_id VARCHAR(36) NOT NULL," +
            "etudiant_id VARCHAR(50) NOT NULL," +
            "date_debut DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "date_fin DATETIME," +
            "cote_obtenue DECIMAL(10,2) DEFAULT 0.00," +
            "statut VARCHAR(20) DEFAULT 'EN_COURS'," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "UNIQUE KEY uk_quiz_etudiant (institution_id, quiz_id, etudiant_id)," +
            "CONSTRAINT fk_quiz_tentative_quiz FOREIGN KEY (institution_id, quiz_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE," +
            "CONSTRAINT fk_quiz_tentative_etudiant FOREIGN KEY (institution_id, etudiant_id) " +
            "  REFERENCES " + MigrationManager.TABLE_ETUDIANTS + "(institution_id, numero_identifiant) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}

private static String createQuizReponseEtudiantTable() {
    return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_QUIZ_REPONSE_ETUDIANT + " (" +
            "institution_id VARCHAR(50) NOT NULL," +
            "id VARCHAR(36) NOT NULL," +
            "tentative_id VARCHAR(36) NOT NULL," +
            "question_id VARCHAR(36) NOT NULL," +
            "option_id VARCHAR(36) NOT NULL," +
            "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "source VARCHAR(10) DEFAULT 'LOCAL'," +
            "PRIMARY KEY (institution_id, id)," +
            "CONSTRAINT fk_quiz_reponse_tentative FOREIGN KEY (institution_id, tentative_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ_TENTATIVE + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE," +
            "CONSTRAINT fk_quiz_reponse_question FOREIGN KEY (institution_id, question_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ_QUESTION + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE," +
            "CONSTRAINT fk_quiz_reponse_option FOREIGN KEY (institution_id, option_id) " +
            "  REFERENCES " + MigrationManager.TABLE_QUIZ_OPTION + "(institution_id, id) " +
            "  ON DELETE CASCADE ON UPDATE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}
    private static String createParametresInscriptionTable() {
        return "CREATE TABLE IF NOT EXISTS " + MigrationManager.TABLE_PARAMETRES_INSCRIPTION + " (" +
                "institution_id VARCHAR(50) NOT NULL," +
                "id VARCHAR(36) NOT NULL," +
                "annee_academique VARCHAR(20) NOT NULL," +
                "periode VARCHAR(50) NOT NULL," +
                "promotion VARCHAR(100) NOT NULL," +
                "classe VARCHAR(100) NOT NULL," +
                "actif BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "source VARCHAR(10) DEFAULT 'LOCAL'," +
                "PRIMARY KEY (institution_id, id)," +
                "UNIQUE KEY unique_inscription (institution_id, annee_academique, periode, classe)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    public static String createDeletedRowsTable() {
        return "CREATE TABLE IF NOT EXISTS deleted_rows ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "table_name VARCHAR(100) NOT NULL,"
            + "primary_key_value VARCHAR(255) NOT NULL,"
            + "deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
            + "source VARCHAR(10) DEFAULT 'LOCAL',"
            + "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "INDEX idx_deleted_at (deleted_at),"
            + "INDEX idx_table_pk (table_name, primary_key_value)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }
}