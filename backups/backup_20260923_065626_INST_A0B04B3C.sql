-- Sauvegarde générée le 2026-09-23T06:56:26.710697500
-- Institution : INST_A0B04B3C

SET FOREIGN_KEY_CHECKS=0;

-- Table : institutions
DELETE FROM `institutions` WHERE institution_id = 'INST_A0B04B3C';
INSERT INTO `institutions` (`institution_id`, `nom_institution`, `sigle_institution`, `devise_institution`, `type_institution`, `niveau_institution`, `categorie_institution`, `logo_institution`, `qrcode_institution`, `adresse_institution`, `code_postal_institution`, `commune_institution`, `departement_institution`, `pays_institution`, `mail_primaire`, `mail_secondaire`, `telephone_primaire`, `telephone_secondaire`, `site_web_institution`, `smtp_password`, `responsable_institution`, `poste_responsable`, `mail_responsable`, `telephone_responsable`, `moyenne_passage`, `systeme_educatif`, `statut_institution`, `date_creation`, `statut_validation`, `last_modified`, `source`) VALUES ('INST_A0B04B3C', 'INSTITUTION MIXTE MINGOT TECHNOLOGIES', 'IMMTECH', 'Win or Die', 'École Supérieure', 'Supérieur', 'Confessionnelle', '/data/photos/logo_INST_A0B04B3C_1790135038704.png', '/data/qr_codes/qr_INST_A0B04B3C.png', '3,Rue Neptune, Delmas 65', '6110', 'Port-au-Prince', 'Ouest', 'Haïti', 'mingotroberto3@gmail.com', 'mingotroberto@gmail.com', '38225375', '37811375', '', '', 'Roberto Mingot', 'Directeur General', 'mingotroberto4@gmail.com', '38225375', '10.00', 'Avance', 'ACTIF', '2026-09-22', 'PENDING', '2026-09-22 19:43:59.0', 'LOCAL');

-- Table : acces_admin
DELETE FROM `acces_admin` WHERE institution_id = 'INST_A0B04B3C';
INSERT INTO `acces_admin` (`institution_id`, `utilisateur_id`, `nom`, `prenom`, `email`, `mot_de_passe`, `classe_perms`, `parametre_perms`, `matiere_perms`, `utilisateur_perms`, `note_perms`, `bulletin_perms`, `economie_perms`, `attestation_perms`, `emploi_du_temps_perms`, `evenements_perms`, `etudiants_perms`, `palmares_perms`, `absence_perms`, `examen_perms`, `professeur_perms`, `parametres_perms`, `communication_perms`, `statistiques_perms`, `programmes_perms`, `documents_perms`, `messages_perms`, `assistance_perms`, `modules_perms`, `promotions_perms`, `options_perms`, `periodes_perms`, `annees_academiques_perms`, `niveau`, `created_at`, `updated_at`, `last_modified`, `source`) VALUES ('INST_A0B04B3C', 'mingotroberto3@gmail.com', 'Roberto Mingot', 'Directeur General', 'mingotroberto3@gmail.com', '$2a$12$4A1MlcZjnL61SWxL7ecvNeRN7Ra1RhntljjZpRzdE/Pms4EoG9Zwm', 15, 0, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 5, '2026-09-22 19:43:59.0', '2026-09-22 19:43:59.0', '2026-09-22 19:43:59.0', 'LOCAL');

-- Table : annees_academiques
DELETE FROM `annees_academiques` WHERE institution_id = 'INST_A0B04B3C';

-- Table : periodes
DELETE FROM `periodes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : classes
DELETE FROM `classes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : modules
DELETE FROM `modules` WHERE institution_id = 'INST_A0B04B3C';

-- Table : promotions
DELETE FROM `promotions` WHERE institution_id = 'INST_A0B04B3C';

-- Table : etudiants
DELETE FROM `etudiants` WHERE institution_id = 'INST_A0B04B3C';

-- Table : acces
DELETE FROM `acces` WHERE institution_id = 'INST_A0B04B3C';

-- Table : options
DELETE FROM `options` WHERE institution_id = 'INST_A0B04B3C';

-- Table : assistance
DELETE FROM `assistance` WHERE institution_id = 'INST_A0B04B3C';

-- Table : parametres_inscription
DELETE FROM `parametres_inscription` WHERE institution_id = 'INST_A0B04B3C';

-- Table : professeurs
DELETE FROM `professeurs` WHERE institution_id = 'INST_A0B04B3C';

-- Table : cours_liens
DELETE FROM `cours_liens` WHERE institution_id = 'INST_A0B04B3C';

-- Table : acces_professeur
DELETE FROM `acces_professeur` WHERE institution_id = 'INST_A0B04B3C';

-- Table : matieres
DELETE FROM `matieres` WHERE institution_id = 'INST_A0B04B3C';

-- Table : notes
DELETE FROM `notes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : absences
DELETE FROM `absences` WHERE institution_id = 'INST_A0B04B3C';

-- Table : messages
DELETE FROM `messages` WHERE institution_id = 'INST_A0B04B3C';

-- Table : attestations
DELETE FROM `attestations` WHERE institution_id = 'INST_A0B04B3C';

-- Table : professeur_matieres
DELETE FROM `professeur_matieres` WHERE institution_id = 'INST_A0B04B3C';

-- Table : programmes
DELETE FROM `programmes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : documents_professeur
DELETE FROM `documents_professeur` WHERE institution_id = 'INST_A0B04B3C';

-- Table : evenements
DELETE FROM `evenements` WHERE institution_id = 'INST_A0B04B3C';

-- Table : frais_scolaires
DELETE FROM `frais_scolaires` WHERE institution_id = 'INST_A0B04B3C';

-- Table : modalites_paiement
DELETE FROM `modalites_paiement` WHERE institution_id = 'INST_A0B04B3C';

-- Table : examens
DELETE FROM `examens` WHERE institution_id = 'INST_A0B04B3C';

-- Table : paiements_etudiants
DELETE FROM `paiements_etudiants` WHERE institution_id = 'INST_A0B04B3C';

-- Table : recettes_externes
DELETE FROM `recettes_externes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : depenses
DELETE FROM `depenses` WHERE institution_id = 'INST_A0B04B3C';

-- Table : employes
DELETE FROM `employes` WHERE institution_id = 'INST_A0B04B3C';

-- Table : budget_classe
DELETE FROM `budget_classe` WHERE institution_id = 'INST_A0B04B3C';

SET FOREIGN_KEY_CHECKS=1;
