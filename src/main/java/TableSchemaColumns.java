    import java.util.ArrayList;
    import java.util.Arrays;
    import java.util.List;

    public class TableSchemaColumns {

        // ============================================================
        // POINT D'ENTRÉE
        // ============================================================

        public static TableSchemaProvider.TableDefinition getTableDefinition(String tableName) {
            return switch (tableName) {
                case MigrationManager.TABLE_INSTITUTIONS -> getInstitutionTableDef();
                case MigrationManager.TABLE_ANNEES_ACADEMIQUES -> getAnneeAcademiqueTableDef();
                case MigrationManager.TABLE_PERIODES -> getPeriodeTableDef();
                case MigrationManager.TABLE_PROMOTIONS -> getPromotionTableDef();
                case MigrationManager.TABLE_CLASSES -> getClasseTableDef();
                case MigrationManager.TABLE_ETUDIANTS -> getEtudiantTableDef();
                case MigrationManager.TABLE_ACCES_ADMIN -> getAccesAdminTableDef();
                case MigrationManager.TABLE_QUIZ                  -> getQuizTableDef();
                case MigrationManager.TABLE_QUIZ_SECTION          -> getQuizSectionTableDef();
                case MigrationManager.TABLE_QUIZ_QUESTION         -> getQuizQuestionTableDef();
                case MigrationManager.TABLE_QUIZ_OPTION           -> getQuizOptionTableDef();
                case MigrationManager.TABLE_QUIZ_TENTATIVE        -> getQuizTentativeTableDef();
                case MigrationManager.TABLE_QUIZ_REPONSE_ETUDIANT -> getQuizReponseEtudiantTableDef();
                case MigrationManager.TABLE_MODULES -> getModulesTableDef();
                case MigrationManager.TABLE_PROFESSEURS -> getProfesseurTableDef();
                case MigrationManager.TABLE_COURS_LIENS -> getCoursLiensTableDef();
                case MigrationManager.TABLE_ACCES_PROFESSEUR -> getAccesProfesseurTableDef();
                case MigrationManager.TABLE_MATIERES -> getMatiereTableDef();
                case MigrationManager.TABLE_ABSENCES -> getAbsenceTableDef();
                case MigrationManager.TABLE_ACCES -> getAccesTableDef();
                case MigrationManager.TABLE_OPTIONS -> getOptionsTableDef();
                case MigrationManager.TABLE_ASSISTANCE -> getAssistanceTableDef();
                case MigrationManager.TABLE_DOCUMENTS_PROFESSEUR -> getDocumentsProfesseurTableDef();
                case MigrationManager.TABLE_ATTESTATIONS -> getAttestationTableDef();
                case MigrationManager.TABLE_PROFESSEUR_MATIERES -> getProfesseurMatieresTableDef();
                case MigrationManager.TABLE_PARAMETRES_INSCRIPTION -> getParametresInscriptionTableDef();
                case MigrationManager.TABLE_NOTES -> getNoteTableDef();
                case MigrationManager.TABLE_MESSAGES -> getMessagesTableDef();
                case MigrationManager.TABLE_PROGRAMMES -> getProgrammesTableDef();
                case MigrationManager.TABLE_EVENEMENTS -> getEvenementTableDef();
                case MigrationManager.TABLE_FRAIS_SCOLAIRES -> getFraisScolairesTableDef();
                case MigrationManager.TABLE_EXAMENS -> getExamenTableDef();
                case MigrationManager.TABLE_MODALITES_PAIEMENT -> getModalitesPaiementTableDef();
                case MigrationManager.TABLE_PAIEMENTS_ETUDIANTS -> getPaiementsEtudiantsTableDef();
                case MigrationManager.TABLE_RECETTES_EXTERNES -> getRecettesExternesTableDef();
                case MigrationManager.TABLE_DEPENSES -> getDepensesTableDef();
                case MigrationManager.TABLE_EMPLOYES -> getEmployesTableDef();
                case MigrationManager.TABLE_BUDGET_CLASSE -> getBudgetClasseTableDef();
                default -> null;
            };
        }

        // ============================================================
        // HELPERS INTERNES
        // ============================================================

        private static TableSchemaProvider.ColumnDefinition col(String name, String type,
                                                                String defaultValue, boolean nullable) {
            return new TableSchemaProvider.ColumnDefinition(name, type, defaultValue, nullable);
        }

        private static TableSchemaProvider.TableDefinition def(TableSchemaProvider.ColumnDefinition... columns) {
            return new TableSchemaProvider.TableDefinition(Arrays.asList(columns));
        }

        // ============================================================
        // DÉFINITIONS PAR TABLE (alignées sur TableSchemaSQL)
        // ============================================================

        /**
         * institutions — PK simple (institution_id).
         * C'est la racine du modèle multi-institutions.
         */
       private static TableSchemaProvider.TableDefinition getInstitutionTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("nom_institution", "VARCHAR(255)", null, true),
        col("sigle_institution", "VARCHAR(100)", null, true),
        col("devise_institution", "VARCHAR(255)", null, true),
        col("type_institution", "VARCHAR(100)", null, true),
        col("niveau_institution", "VARCHAR(100)", null, true),
        col("categorie_institution", "VARCHAR(100)", null, true),
        col("logo_institution", "VARCHAR(255)", null, true),
        col("qrcode_institution", "LONGTEXT", null, true),
        col("adresse_institution", "VARCHAR(255)", null, true),
        col("code_postal_institution", "VARCHAR(50)", null, true),
        col("commune_institution", "VARCHAR(100)", null, true),
        col("departement_institution", "VARCHAR(100)", null, true),
        col("pays_institution", "VARCHAR(100)", null, true),
        col("mail_primaire", "VARCHAR(255)", null, true),
        col("mail_secondaire", "VARCHAR(255)", null, true),
        col("telephone_primaire", "VARCHAR(50)", null, true),
        col("telephone_secondaire", "VARCHAR(50)", null, true),
        col("site_web_institution", "VARCHAR(255)", null, true),
        col("smtp_password", "VARCHAR(255)", null, true),
        col("responsable_institution", "VARCHAR(255)", null, true),
        col("poste_responsable", "VARCHAR(100)", null, true),
        col("mail_responsable", "VARCHAR(255)", null, true),
        col("telephone_responsable", "VARCHAR(50)", null, true),
        col("moyenne_passage", "VARCHAR(50)", null, true),
        col("systeme_educatif", "VARCHAR(100)", null, true),
        col("statut_institution", "VARCHAR(50)", null, true),
        col("date_creation", "VARCHAR(50)", null, true),
        col("statut_validation", "VARCHAR(20)", "'PENDING'", true),   // ✅ AJOUTÉ
        col("last_modified", "TIMESTAMP",
            "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}
        /**
         * annees_academiques — PK (institution_id, annee_academique).
         * Pas de colonne `id`.
         */
        private static TableSchemaProvider.TableDefinition getAnneeAcademiqueTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("date_debut", "DATE", null, false),
                col("date_fin", "DATE", null, false),
                col("est_active", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * periodes — PK (institution_id, annee_academique, periode).
         */
        private static TableSchemaProvider.TableDefinition getPeriodeTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("periode", "VARCHAR(100)", null, false),
                col("date_debut", "DATE", null, true),
                col("date_fin", "DATE", null, true),
                col("est_active", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * promotions — PK (institution_id, annee_academique, promotion).
         */
        private static TableSchemaProvider.TableDefinition getPromotionTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("promotion", "VARCHAR(100)", null, false),
                col("date_debut", "DATE", null, false),
                col("date_fin", "DATE", null, false),
                col("est_active", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * classes — PK (institution_id, code_classe).
         */
        private static TableSchemaProvider.TableDefinition getClasseTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("code_classe", "VARCHAR(50)", null, false),
                col("nom_classe", "VARCHAR(255)", "''", false) ,
                col("niveau_classe", "INT", "1", true),
                col("capacite_classe", "INT", "30", true),
                col("nombre_etudiants", "INT", "0", true),
                col("nombre_matieres", "INT", "0", true),
                col("periode", "VARCHAR(50)", null, true),
                col("annee_academique", "VARCHAR(20)", null, true),
                col("promotion", "VARCHAR(100)", null, true),
                col("statut", "VARCHAR(20)", "'ACTIF'", true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("moyenne_passage", "DECIMAL(5,2)", "10.00", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * etudiants — PK (institution_id, numero_identifiant).
         * Pas de colonne `id`.
         */
        private static TableSchemaProvider.TableDefinition getEtudiantTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("nom", "VARCHAR(255)", null, false),
                col("prenom", "VARCHAR(255)", null, true),
                col("sexe", "VARCHAR(20)", null, false),
                col("date_naissance", "DATE", null, true),
                col("groupe_sanguin", "VARCHAR(10)", null, true),
                col("telephone", "VARCHAR(50)", null, true),
                col("email", "VARCHAR(255)", null, true),
                col("adresse", "TEXT", null, true),
                col("classe", "VARCHAR(100)", null, true),
                col("annee_academique", "VARCHAR(50)", null, true),
                col("periode", "VARCHAR(20)", null, false),
                col("promotion", "VARCHAR(100)", null, true),
                col("`option`", "VARCHAR(100)", null, true),
                col("matricule", "VARCHAR(100)", null, true),
                col("ninu", "VARCHAR(100)", null, true),
                col("statut", "VARCHAR(50)", "'ACTIF'", true),
                col("date_inscription", "DATE", null, true),
                col("departement_naissance", "VARCHAR(100)", null, true),
                col("commune_naissance", "VARCHAR(100)", null, true),
                col("departement_residence", "VARCHAR(100)", null, true),
                col("commune_residence", "VARCHAR(100)", null, true),
                col("nom_pere", "VARCHAR(255)", null, true),
                col("prenom_pere", "VARCHAR(255)", null, true),
                col("nom_mere", "VARCHAR(255)", null, true),
                col("prenom_mere", "VARCHAR(255)", null, true),
                col("telephone_parents", "VARCHAR(50)", null, true),
                col("residence_parents", "VARCHAR(255)", null, true),
                col("lien_parente", "VARCHAR(100)", null, true),
                col("type_responsable", "VARCHAR(100)", null, true),
                col("nom_responsable", "VARCHAR(255)", null, true),
                col("prenom_responsable", "VARCHAR(255)", null, true),
                col("photo_path", "VARCHAR(500)", null, true),
                col("observations", "TEXT", null, true),
                col("empreinte_path", "VARCHAR(500)", null, true),
                col("visage_path", "VARCHAR(500)", null, true),
                col("biometrie_active", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * acces_admin — PK (institution_id, utilisateur_id).
         */
        private static TableSchemaProvider.TableDefinition getAccesAdminTableDef() {
            List<TableSchemaProvider.ColumnDefinition> columns = new ArrayList<>();

            columns.add(col("institution_id", "VARCHAR(50)", null, false));
            columns.add(col("utilisateur_id", "VARCHAR(50)", null, false));
            columns.add(col("nom", "VARCHAR(255)", null, false));
            columns.add(col("prenom", "VARCHAR(255)", null, false));
            columns.add(col("email", "VARCHAR(255)", null, false));
            columns.add(col("mot_de_passe", "VARCHAR(255)", null, false));
            columns.add(col("niveau", "INT", "1", true));

            // Colonnes permissions par module
            for (AccesAdmin.Module module : AccesAdmin.Module.values()) {
                String colName = module.name().toLowerCase() + "_perms";
                columns.add(col(colName, "INT", "0", true));
            }

            columns.add(col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true));
            columns.add(col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true));
            columns.add(col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true));
            columns.add(col("source", "VARCHAR(10)", "'LOCAL'", true));

            return new TableSchemaProvider.TableDefinition(columns);
        }

        /**
         * modules — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getModulesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("nom_module", "VARCHAR(255)", null, false),
                col("icone", "VARCHAR(255)", null, true),
                col("lien", "VARCHAR(255)", null, true),
                col("description", "TEXT", null, true),
                col("ordre", "INT", "0", true),
                col("permission_key", "VARCHAR(100)", null, true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * professeurs — PK (institution_id, numero_identifiant_professeur).
         */
        private static TableSchemaProvider.TableDefinition getProfesseurTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("numero_identifiant_professeur", "VARCHAR(50)", null, false),
                col("nom", "VARCHAR(255)", null, false),
                col("prenom", "VARCHAR(255)", null, true),
                col("sexe", "VARCHAR(20)", null, false),
                col("date_naissance", "DATE", null, true),
                col("groupe_sanguin", "VARCHAR(10)", null, true),
                col("departement_naissance", "VARCHAR(100)", null, true),
                col("commune_naissance", "VARCHAR(100)", null, true),
                col("telephone", "VARCHAR(50)", null, true),
                col("email", "VARCHAR(255)", null, true),
                col("adresse", "TEXT", null, true),
                col("pays_habitation", "VARCHAR(100)", null, true),
                col("departement_habitation", "VARCHAR(100)", null, true),
                col("commune_habitation", "VARCHAR(100)", null, true),
                col("matricule", "VARCHAR(100)", null, true),
                col("ninu", "VARCHAR(100)", null, true),
                col("diplome", "VARCHAR(255)", null, true),
                col("specialite", "VARCHAR(255)", null, true),
                col("date_embauche", "DATE", null, true),
                col("salaire", "DECIMAL(12,2)", "0.00", true),
                col("statut", "VARCHAR(50)", "'ACTIF'", true),
                col("photo_path", "VARCHAR(500)", null, true),
                col("observations", "TEXT", null, true),
                col("situation_matrimoniale", "VARCHAR(100)", null, true),
                col("empreinte_path", "VARCHAR(500)", null, true),
                col("visage_path", "VARCHAR(500)", null, true),
                col("biometrie_active", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * cours_liens — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getCoursLiensTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("titre", "VARCHAR(255)", null, false),
                col("description", "TEXT", null, true),
                col("url", "VARCHAR(500)", null, false),
                col("date_debut", "DATETIME", null, false),
                col("date_fin", "DATETIME", null, false),
                col("professeur_id", "VARCHAR(50)", null, false),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * acces_professeur — PK (institution_id, numero_identifiant).
         */
        private static TableSchemaProvider.TableDefinition getAccesProfesseurTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("profil", "BOOLEAN", "TRUE", true),
                col("cours", "BOOLEAN", "TRUE", true),
                col("notes", "BOOLEAN", "TRUE", true),
                col("edt", "BOOLEAN", "TRUE", true),
                col("absences", "BOOLEAN", "TRUE", true),
                col("documents", "BOOLEAN", "TRUE", true),
                col("messages", "BOOLEAN", "TRUE", true),
                col("parametres", "BOOLEAN", "TRUE", true),
                col("programmes", "BOOLEAN", "TRUE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * matieres — PK (institution_id, code_cours).
         */
        private static TableSchemaProvider.TableDefinition getMatiereTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("code_cours", "VARCHAR(100)", null, false),
                col("nom_matiere", "VARCHAR(255)", null, false),
                col("coefficient", "DECIMAL(5,2)", "1.00", true),
                col("note_passage", "DECIMAL(5,2)", null, true),
                col("note_minimale", "DECIMAL(5,2)", null, true),
                col("note_maximale", "DECIMAL(5,2)", null, true),
                col("classe_matiere", "VARCHAR(100)", null, true),
                col("periode", "VARCHAR(100)", null, true),
                col("statut", "VARCHAR(50)", "'ACTIF'", true),
                col("annee_academique", "VARCHAR(50)", null, true),
                col("nombre_credits", "INT", null, true),
                col("duree_cours", "VARCHAR(100)", null, true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * absences — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getAbsenceTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("date_absence", "TIMESTAMP", null, false),
                col("justifiee", "BOOLEAN", "FALSE", true),
                col("motif", "TEXT", null, true),
                col("code_cours", "VARCHAR(100)", null, true),
                col("periode", "VARCHAR(50)", null, true),
                col("promotion", "VARCHAR(100)", null, true),
                col("annee_academique", "VARCHAR(50)", null, true),
                col("classe", "VARCHAR(100)", null, true),
                col("personne_type", "VARCHAR(20)", null, false),
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("created_by", "VARCHAR(50)", null, true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * acces — PK (institution_id, numero_identifiant).
         */
        private static TableSchemaProvider.TableDefinition getAccesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("connexion", "BOOLEAN", "TRUE", true),
                col("profil", "BOOLEAN", "TRUE", true),
                col("notes", "BOOLEAN", "TRUE", true),
                col("bulletin", "BOOLEAN", "TRUE", true),
                col("paiement", "BOOLEAN", "TRUE", true),
                col("messages", "BOOLEAN", "TRUE", true),
                col("documents", "BOOLEAN", "TRUE", true),
                col("edt", "BOOLEAN", "TRUE", true),
                col("attestations", "BOOLEAN", "TRUE", true),
                col("absences", "BOOLEAN", "TRUE", true),
                col("assistance", "BOOLEAN", "TRUE", true),
                col("statistiques", "BOOLEAN", "TRUE", true),
                col("activites", "BOOLEAN", "TRUE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * options — PK (institution_id, option, classe, annee_academique).
         */
        private static TableSchemaProvider.TableDefinition getOptionsTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("`option`", "VARCHAR(255)", null, false),
                col("classe", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("periode", "VARCHAR(50)", null, true),
                col("promotion", "VARCHAR(100)", null, true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * assistance — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getAssistanceTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("etudiant_id", "VARCHAR(50)", null, false),
                col("type_demande", "VARCHAR(100)", null, false),
                col("sujet", "VARCHAR(255)", null, false),
                col("message", "TEXT", null, false),
                col("statut", "VARCHAR(20)", "'EN_ATTENTE'", true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("fichier_joint", "VARCHAR(255)", null, true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * documents_professeur — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getDocumentsProfesseurTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("professeur_id", "VARCHAR(50)", null, false),
                col("titre", "VARCHAR(255)", null, false),
                col("description", "TEXT", null, true),
                col("fichier_nom", "VARCHAR(255)", null, true),
                col("fichier_chemin", "VARCHAR(500)", null, true),
                col("url", "VARCHAR(500)", null, true),
                col("type", "VARCHAR(50)", null, true),
                col("matiere", "VARCHAR(100)", null, false),
                col("classe", "VARCHAR(100)", null, false),
                col("periode", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("est_public", "BOOLEAN", "FALSE", true),
                col("taille", "BIGINT", "0", true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * attestations — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getAttestationTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("type", "VARCHAR(100)", null, false),
                col("date_emission", "DATE", null, false),
                col("contenu_html", "TEXT", null, true),
                col("reference", "VARCHAR(100)", null, true),
                col("numero", "VARCHAR(50)", null, true),
                col("classe", "VARCHAR(100)", null, true),
                col("annee_academique", "VARCHAR(50)", null, true),
                col("session", "VARCHAR(50)", null, true),
                col("`option`", "VARCHAR(100)", null, true),
                col("periode", "VARCHAR(50)", null, true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * professeur_matieres — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getProfesseurMatieresTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("code_cours", "VARCHAR(100)", null, false),
                col("nom_matiere", "VARCHAR(200)", null, true),
                col("classe", "VARCHAR(100)", null, false),
                col("coefficient", "DECIMAL(5,2)", "1.00", true),
                col("jour", "VARCHAR(20)", null, false),
                col("heure_debut", "TIME", null, false),
                col("heure_fin", "TIME", null, true),
                col("duree_minutes", "INT", "0", true),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("periode", "VARCHAR(50)", null, true),
                col("date_affectation", "DATE", null, true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * parametres_inscription — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getParametresInscriptionTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("annee_academique", "VARCHAR(20)", null, false),
                col("periode", "VARCHAR(50)", null, false),
                col("promotion", "VARCHAR(100)", null, false),
                col("classe", "VARCHAR(100)", null, false),
                col("actif", "BOOLEAN", "FALSE", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * notes — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getNoteTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("numero_etudiant", "VARCHAR(50)", null, false),
                col("nom_etudiant", "VARCHAR(255)", null, true),
                col("prenom_etudiant", "VARCHAR(255)", null, true),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("matiere_nom", "VARCHAR(255)", null, true),
                col("code_cours", "VARCHAR(100)", null, false),
                col("classe", "VARCHAR(100)", null, true),
                col("numero_enseignant", "VARCHAR(50)", null, true),
                col("nom_enseignant", "VARCHAR(255)", null, true),
                col("prenom_enseignant", "VARCHAR(255)", null, true),
                col("note_value", "DECIMAL(5,2)", null, true),
                col("note_sur", "DECIMAL(5,2)", "100.00", true),
                col("note_base", "DECIMAL(5,2)", null, true),
                col("coefficient", "DECIMAL(5,2)", "1.00", true),
                col("note_minimale", "DECIMAL(5,2)", null, true),
                col("note_maximale", "DECIMAL(5,2)", null, true),
                col("note_passage", "DECIMAL(5,2)", null, true),
                col("rang_etudiant", "INT", null, true),
                col("periode", "VARCHAR(50)", null, false),
                col("est_validee", "BOOLEAN", "FALSE", true),
                col("is_verified", "BOOLEAN", "FALSE", true),
                col("verified_by", "VARCHAR(255)", null, true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * messages — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getMessagesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("expediteur_id", "VARCHAR(50)", null, false),
                col("expediteur_type", "ENUM", "'PROFESSEUR','ETUDIANT','ADMIN'", false),
                col("destinataire_id", "VARCHAR(50)", null, false),
                col("destinataire_type", "ENUM", "'PROFESSEUR','ETUDIANT','ADMIN'", false),
                col("sujet", "VARCHAR(255)", null, false),
                col("contenu", "TEXT", null, false),
                col("lu", "BOOLEAN", "FALSE", true),
                col("date_envoi", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("reponse_a", "VARCHAR(36)", null, true),              // ✅ UUID (FK vers messages.id)
                col("est_supprime_expediteur", "BOOLEAN", "FALSE", true),
                col("est_supprime_destinataire", "BOOLEAN", "FALSE", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * programmes — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getProgrammesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("professeur_id", "VARCHAR(50)", null, false),
                col("code_cours", "VARCHAR(100)", null, false),
                col("matiere", "VARCHAR(100)", null, false),
                col("classe", "VARCHAR(100)", null, false),
                col("periode", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("chapitre", "VARCHAR(255)", null, false),
                col("description", "TEXT", null, true),
                col("titre_chapitre", "TEXT", null, true),
                col("date_debut", "DATE", null, true),
                col("date_fin", "DATE", null, true),
                col("est_vu", "BOOLEAN", "FALSE", true),
                col("duree_prevue", "INT", "0", true),
                col("duree_reelle", "INT", "0", true),
                col("cotation", "INT", "1", true),
                col("est_termine", "BOOLEAN", "FALSE", true),
                col("ordre", "INT", "0", true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * evenements — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getEvenementTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("titre", "VARCHAR(255)", null, false),
                col("description", "TEXT", null, true),
                col("date_debut", "TIMESTAMP", null, false),
                col("date_fin", "TIMESTAMP", null, true),
                col("lieu", "VARCHAR(255)", null, true),
                col("classe", "VARCHAR(50)", null, true),
                col("annee_academique", "VARCHAR(20)", null, true),
                col("periode", "VARCHAR(20)", null, true),
                col("etudiants_emails", "TEXT", null, true),
                col("professeurs_emails", "TEXT", null, true),
                col("statut", "VARCHAR(50)", "'À venir'", true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * frais_scolaires — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getFraisScolairesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("classe", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("montant_total", "DECIMAL(12,2)", null, false),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * examens — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getExamenTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("professeur_id", "VARCHAR(50)", null, false),
                col("code_cours", "VARCHAR(100)", null, false),
                col("classe", "VARCHAR(100)", null, false),
                col("periode", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(50)", null, false),
                col("titre", "VARCHAR(255)", null, false),
                col("description", "TEXT", null, true),
                col("date_examen", "DATE", null, false),
                col("heure_debut", "TIME", null, true),
                col("heure_fin", "TIME", null, true),
                col("duree_minutes", "INT", "0", true),
                col("salle", "VARCHAR(100)", null, true),
                col("coefficient", "DECIMAL(5,2)", "1.00", true),
                col("note_maximale", "DECIMAL(5,2)", "20.00", true),
                col("statut", "VARCHAR(20)", "'PREVU'", true),
                col("fichier_nom", "VARCHAR(255)", null, true),
                col("fichier_chemin", "VARCHAR(500)", null, true),
                col("fichier_type", "VARCHAR(50)", null, true),
                col("created_at", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("updated_at", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * modalites_paiement — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getModalitesPaiementTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("classe", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("versement_num", "INT", null, false),
                col("montant", "DECIMAL(12,2)", null, false),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * paiements_etudiants — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getPaiementsEtudiantsTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("numero_identifiant", "VARCHAR(50)", null, false),
                col("montant", "DECIMAL(12,2)", null, false),
                col("moyen_paiement", "VARCHAR(50)", null, false),
                col("reference", "VARCHAR(100)", null, true),
                col("date_paiement", "DATETIME", "CURRENT_TIMESTAMP", false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("statut", "VARCHAR(20)", "'VALIDÉ'", true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        /**
         * recettes_externes — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getRecettesExternesTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("categorie", "VARCHAR(100)", null, false),
                col("date_recette", "DATE", null, false),
                col("montant", "DECIMAL(12,2)", null, false),
                col("donateur", "VARCHAR(255)", null, true),
                col("receveur", "VARCHAR(255)", null, true),
                col("motif", "TEXT", null, true),
                col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }

        // ============================================================
// QUIZ
// ============================================================

/**
 * quiz — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 */
private static TableSchemaProvider.TableDefinition getQuizTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("professeur_id", "VARCHAR(50)", null, false),
        col("code_cours", "VARCHAR(100)", null, true),
        col("classe", "VARCHAR(100)", null, true),
        col("periode", "VARCHAR(100)", null, true),
        col("annee_academique", "VARCHAR(50)", null, true),
        col("titre", "VARCHAR(255)", null, false),
        col("description", "TEXT", null, true),
        col("date_debut", "DATETIME", null, true),
        col("date_expiration", "DATETIME", null, false),          // ⏰ obligatoire
        col("duree_minutes", "INT", "30", true),
        col("note_totale", "DECIMAL(10,2)", "0.00", true),
        col("statut", "VARCHAR(20)", "'BROUILLON'", true),        // BROUILLON/PUBLIE/ARCHIVE
        col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
        col("date_modification", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

/**
 * quiz_section — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 * FK → quiz(institution_id, id) ON DELETE CASCADE
 */
private static TableSchemaProvider.TableDefinition getQuizSectionTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("quiz_id", "VARCHAR(36)", null, false),
        col("titre", "VARCHAR(255)", null, false),
        col("description", "TEXT", null, true),
        col("ordre", "INT", "0", true),
        col("cote_section", "DECIMAL(10,2)", "0.00", true),       // somme des questions
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

/**
 * quiz_question — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 * FK → quiz_section(institution_id, id) ON DELETE CASCADE
 */
private static TableSchemaProvider.TableDefinition getQuizQuestionTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("section_id", "VARCHAR(36)", null, false),
        col("enonce", "TEXT", null, false),
        col("type", "VARCHAR(20)", "'SIMPLE'", false),            // SIMPLE / MULTIPLE
        col("cote", "DECIMAL(10,2)", "1.00", true),
        col("ordre", "INT", "0", true),
        col("explication", "TEXT", null, true),
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

/**
 * quiz_option — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 * FK → quiz_question(institution_id, id) ON DELETE CASCADE
 */
private static TableSchemaProvider.TableDefinition getQuizOptionTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("question_id", "VARCHAR(36)", null, false),
        col("texte", "TEXT", null, false),
        col("est_correcte", "BOOLEAN", "FALSE", true),
        col("ordre", "INT", "0", true),
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

/**
 * quiz_tentative — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 * FK → quiz(institution_id, id) ON DELETE CASCADE
 * UNIQUE KEY (institution_id, quiz_id, etudiant_id)
 */
private static TableSchemaProvider.TableDefinition getQuizTentativeTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("quiz_id", "VARCHAR(36)", null, false),
        col("etudiant_id", "VARCHAR(50)", null, false),
        col("date_debut", "DATETIME", "CURRENT_TIMESTAMP", true),
        col("date_fin", "DATETIME", null, true),
        col("cote_obtenue", "DECIMAL(10,2)", "0.00", true),
        col("statut", "VARCHAR(20)", "'EN_COURS'", true),         // EN_COURS/TERMINEE/EXPIREE
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

/**
 * quiz_reponse_etudiant — PK (institution_id, id).
 * ✅ id = VARCHAR(36) (UUID)
 * FK → quiz_tentative(institution_id, id) ON DELETE CASCADE
 */
private static TableSchemaProvider.TableDefinition getQuizReponseEtudiantTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
        col("tentative_id", "VARCHAR(36)", null, false),
        col("question_id", "VARCHAR(36)", null, false),
        col("option_id", "VARCHAR(36)", null, false),
        col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

        /**
         * depenses — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
       private static TableSchemaProvider.TableDefinition getDepensesTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),
        col("annee_academique", "VARCHAR(20)", null, true),
        col("mois", "VARCHAR(20)", null, true),
        col("classes", "TEXT", null, true),
        col("nom_institution", "VARCHAR(255)", null, true),
        col("nom_depense", "VARCHAR(255)", null, true),
        col("type_depense", "VARCHAR(100)", null, true),
        col("description_depense", "TEXT", null, true),
        col("responsable_depense", "VARCHAR(255)", null, true),
        col("mode_paiement", "VARCHAR(50)", null, true),
        col("numero_facture", "VARCHAR(100)", null, true),
        col("ninu", "VARCHAR(100)", null, true),
        col("matricule", "VARCHAR(100)", null, true),
        col("justification_depense", "TEXT", null, true),
        col("montant", "DECIMAL(12,2)", "0.00", false),
        col("montant_total_depenses", "DECIMAL(12,2)", "0.00", true),
        col("date_depense", "DATE", null, false),
        col("article", "VARCHAR(255)", null, true),
        col("fournisseur", "VARCHAR(255)", null, true),
        col("motif", "TEXT", null, true),
        col("executant", "VARCHAR(255)", null, true),
        col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
        col("date_modification", "TIMESTAMP",
            "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("last_modified", "TIMESTAMP",
            "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}
        /**
         * employes — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
       private static TableSchemaProvider.TableDefinition getEmployesTableDef() {
    return def(
        col("institution_id", "VARCHAR(50)", null, false),
        col("id", "VARCHAR(36)", null, false),
        col("employe_id", "INT", null, false),
        col("annee_academique", "VARCHAR(20)", null, true),   // ✅ AJOUTÉ
        col("mois", "VARCHAR(20)", null, true),               // ✅ AJOUTÉ
        col("classes", "TEXT", null, true),                   // ✅ AJOUTÉ
        col("nom", "VARCHAR(255)", null, false),
        col("poste", "VARCHAR(255)", null, true),
        col("salaire_base", "DECIMAL(12,2)", null, false),
        col("deductions", "DECIMAL(12,2)", "0.00", true),
        col("date_embauche", "DATE", null, true),
        col("date_creation", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
        col("date_modification", "TIMESTAMP",
            "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("last_modified", "TIMESTAMP",
            "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
        col("source", "VARCHAR(10)", "'LOCAL'", true)
    );
}

        /**
         * budget_classe — PK (institution_id, id).
         * ✅ id = VARCHAR(36) (UUID)
         */
        private static TableSchemaProvider.TableDefinition getBudgetClasseTableDef() {
            return def(
                col("institution_id", "VARCHAR(50)", null, false),
                col("id", "VARCHAR(36)", null, false),                    // ✅ UUID
                col("classe", "VARCHAR(100)", null, false),
                col("annee_academique", "VARCHAR(20)", null, false),
                col("total_recettes", "DECIMAL(12,2)", "0.00", true),
                col("total_depenses", "DECIMAL(12,2)", "0.00", true),
                col("solde", "DECIMAL(12,2)", "0.00", true),
                col("date_calcul", "TIMESTAMP", "CURRENT_TIMESTAMP", true),
                col("last_modified", "TIMESTAMP", "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", true),
                col("source", "VARCHAR(10)", "'LOCAL'", true)
            );
        }
    }