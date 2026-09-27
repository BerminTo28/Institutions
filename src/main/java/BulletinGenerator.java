import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BulletinGenerator {

    public static final DecimalFormat DF = new DecimalFormat("0.00");
    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    private final String institutionId;
    private final Connection connection;
    private static final Logger LOGGER = Logger.getLogger(BulletinGenerator.class.getName());

    // ==================== CONSTRUCTEURS ====================

    public BulletinGenerator(String institutionId, Connection connection) {
        this.institutionId = institutionId;
        this.connection = connection;
    }

    public BulletinGenerator(String institutionId) throws SQLException {
        this(institutionId, DatabaseManager.getInstance().getConnection());
    }

    public String getInstitutionId() {
        return institutionId;
    }

    // ==================== RÉCUPÉRATION DE L'INSTITUTION ====================

    public Institution getInstitutionInfo() {
        if (institutionId == null) return null;
        String sql = "SELECT * FROM " + MigrationManager.TABLE_INSTITUTIONS + " WHERE institution_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapInstitution(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération institution: " + e.getMessage(), e);
        }
        return null;
    }

    private Institution mapInstitution(ResultSet rs) throws SQLException {
        Institution institution = new Institution();
        institution.setInstitutionId(rs.getString("institution_id"));
        institution.setNomInstitution(rs.getString("nom_institution"));
        institution.setSigleInstitution(rs.getString("sigle_institution"));
        institution.setDeviseInstitution(rs.getString("devise_institution"));
        institution.setTypeInstitution(rs.getString("type_institution"));
        institution.setNiveauInstitution(rs.getString("niveau_institution"));
        institution.setCategorieInstitution(rs.getString("categorie_institution"));
        institution.setLogoInstitution(rs.getString("logo_institution"));
        institution.setQRCodeInstitution(rs.getString("qrcode_institution"));
        institution.setAdresseInstitution(rs.getString("adresse_institution"));
        institution.setCodePostalInstitution(rs.getString("code_postal_institution"));
        institution.setCommuneInstitution(rs.getString("commune_institution"));
        institution.setDepartementInstitution(rs.getString("departement_institution"));
        institution.setPaysInstitution(rs.getString("pays_institution"));
        institution.setMailPrimaire(rs.getString("mail_primaire"));
        institution.setMailSecondaire(rs.getString("mail_secondaire"));
        institution.setTelephonePrimaireInstitution(rs.getString("telephone_primaire"));
        institution.setTelephoneSecondaireInstitution(rs.getString("telephone_secondaire"));
        institution.setSiteWebInstitution(rs.getString("site_web_institution"));
        institution.setSmtpPasswordInstitution(rs.getString("smtp_password"));
        institution.setResponsableInstitution(rs.getString("responsable_institution"));
        institution.setPosteResponsableInstitution(rs.getString("poste_responsable"));
        institution.setMailResponsableInstitution(rs.getString("mail_responsable"));
        institution.setTelephoneResponsableInstitution(rs.getString("telephone_responsable"));
        institution.setMoyenneDePassageInstitution(rs.getString("moyenne_passage"));
        institution.setSystemeEducatifInstitution(rs.getString("systeme_educatif"));
        institution.setStatutInstitution(rs.getString("statut_institution"));
        institution.setDateCreationInstitution(rs.getString("date_creation"));
        return institution;
    }

    // ==================== RÉCUPÉRATION DES ÉTUDIANTS ====================

    public List<Etudiant> getEtudiants(String classe, String annee) {
        if (institutionId == null || classe == null || annee == null) return Collections.emptyList();
        return getEtudiantsParClasseAnnee(classe, annee);
    }

    private List<Etudiant> getEtudiantsParClasseAnnee(String classe, String annee) {
        List<Etudiant> result = new ArrayList<>();
        String sql = "SELECT * FROM " + MigrationManager.TABLE_ETUDIANTS +
                     " WHERE classe = ? AND annee_academique = ? AND institution_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, classe);
            ps.setString(2, annee);
            ps.setString(3, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToEtudiant(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération étudiants: " + e.getMessage(), e);
        }
        return result;
    }

    private Etudiant mapResultSetToEtudiant(ResultSet rs) throws SQLException {
        Etudiant e = new Etudiant();
        e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
        e.setNom(rs.getString("nom"));
        e.setPrenom(rs.getString("prenom"));
        e.setSexe(rs.getString("sexe"));
        e.setDateNaissance(rs.getDate("date_naissance"));
        e.setGroupeSanguin(rs.getString("groupe_sanguin"));
        e.setTelephone(rs.getString("telephone"));
        e.setEmail(rs.getString("email"));
        e.setAdresse(rs.getString("adresse"));
        e.setClasse(rs.getString("classe"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setPromotion(rs.getString("promotion"));
        e.setMatricule(rs.getString("matricule"));
        e.setNinu(rs.getString("ninu"));
        e.setInstitutionId(rs.getString("institution_id"));
        e.setStatut(rs.getString("statut"));
        e.setDateInscription(rs.getDate("date_inscription"));
        e.setDepartementNaissance(rs.getString("departement_naissance"));
        e.setCommuneNaissance(rs.getString("commune_naissance"));
        e.setNomPere(rs.getString("nom_pere"));
        e.setPrenomPere(rs.getString("prenom_pere"));
        e.setNomMere(rs.getString("nom_mere"));
        e.setPrenomMere(rs.getString("prenom_mere"));
        e.setTelephoneParents(rs.getString("telephone_parents"));
        e.setResidenceParents(rs.getString("residence_parents"));
        e.setLienParente(rs.getString("lien_parente"));
        e.setTypeResponsable(rs.getString("type_responsable"));
        e.setNomResponsable(rs.getString("nom_responsable"));
        e.setPrenomResponsable(rs.getString("prenom_responsable"));
        e.setPhotoPath(rs.getString("photo_path"));
        e.setObservations(rs.getString("observations"));
        e.setEmpreintePath(rs.getString("empreinte_path"));
        e.setVisagePath(rs.getString("visage_path"));
        e.setBiometrieActive(rs.getBoolean("biometrie_active"));
        return e;
    }

    // ==================== RÉCUPÉRATION DES NOTES ====================

    public List<BulletinReleveMatiere> getNotesEtudiant(String idEtudiant, String annee, String session, String classe) {
        if (institutionId == null || idEtudiant == null) return Collections.emptyList();

        final String classeEffective;
        if (classe != null && !classe.trim().isEmpty()) {
            classeEffective = classe;
        } else {
            String c = getClasseEtudiant(idEtudiant);
            if (c == null || c.isEmpty()) {
                LOGGER.warning(() -> "Impossible de déterminer la classe pour l'étudiant " + idEtudiant);
                return Collections.emptyList();
            }
            classeEffective = c;
        }

        final String anneeEffective;
        if (annee != null && !annee.trim().isEmpty()) {
            anneeEffective = annee;
        } else {
            String a = getAnneeActive();
            if (a == null) {
                LOGGER.warning("Aucune année active trouvée");
                return Collections.emptyList();
            }
            anneeEffective = a;
        }

        List<Matiere> matieresClasse = getMatieresParClasse(classeEffective);
        if (matieresClasse.isEmpty()) {
            LOGGER.warning(() -> "Aucune matière trouvée dans la configuration pour la classe " + classeEffective);
            return getNotesEtudiantDepuisNotesSeules(idEtudiant, anneeEffective, session, classeEffective);
        }

        Map<String, Double> notesEtudiantMap = new HashMap<>();
        String sqlNotes = """
            SELECT matiere_nom, code_cours, note_value 
            FROM notes
            WHERE numero_etudiant = ? 
              AND annee_academique = ? 
              AND periode = ? 
              AND institution_id = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sqlNotes)) {
            ps.setString(1, idEtudiant);
            ps.setString(2, anneeEffective);
            ps.setString(3, session);
            ps.setString(4, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nomMat = rs.getString("matiere_nom");
                    String codeCours = rs.getString("code_cours");
                    double val = rs.getDouble("note_value");
                    if (codeCours != null && !codeCours.trim().isEmpty()) {
                        notesEtudiantMap.put(codeCours.trim(), val);
                    }
                    if (nomMat != null && !nomMat.trim().isEmpty()) {
                        notesEtudiantMap.put(nomMat.trim(), val);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération notes pour fusion: " + e.getMessage(), e);
        }

        Map<String, BulletinReleveMatiere> mapResultat = new LinkedHashMap<>();
        for (Matiere mat : matieresClasse) {
            String key = (mat.getCodeCours() != null && !mat.getCodeCours().trim().isEmpty())
                         ? mat.getCodeCours().trim()
                         : mat.getNomMatiere().trim();

            BulletinReleveMatiere rm = new BulletinReleveMatiere(
                anneeEffective,
                key,
                mat.getNomMatiere().trim(),
                mat.getCoefficient() > 0 ? mat.getCoefficient() : 1.0,
                mat.getNoteMaximale() > 0 ? mat.getNoteMaximale() : 10.0
            );

            double noteTrouvee = -1.0;
            if (mat.getCodeCours() != null && notesEtudiantMap.containsKey(mat.getCodeCours().trim())) {
                noteTrouvee = notesEtudiantMap.get(mat.getCodeCours().trim());
            } else if (notesEtudiantMap.containsKey(mat.getNomMatiere().trim())) {
                noteTrouvee = notesEtudiantMap.get(mat.getNomMatiere().trim());
            }
            rm.ajouterNote(session, noteTrouvee);
            mapResultat.put(key, rm);
        }
        return new ArrayList<>(mapResultat.values());
    }

    private List<BulletinReleveMatiere> getNotesEtudiantDepuisNotesSeules(String idEtudiant, String annee, String session, String classe) {
        System.out.println("Bulletin de la classe "+classe);
        List<BulletinReleveMatiere> result = new ArrayList<>();
        String sql = """
            SELECT matiere_nom, code_cours, coefficient, note_maximale, note_value 
            FROM notes
            WHERE numero_etudiant = ? 
              AND annee_academique = ? 
              AND periode = ? 
              AND institution_id = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idEtudiant);
            ps.setString(2, annee);
            ps.setString(3, session);
            ps.setString(4, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nomMat = rs.getString("matiere_nom");
                    String codeCours = rs.getString("code_cours");
                    double coef = rs.getDouble("coefficient");
                    double noteMax = rs.getDouble("note_maximale");
                    double val = rs.getDouble("note_value");

                    String key = (codeCours != null && !codeCours.trim().isEmpty()) ? codeCours : nomMat;
                    BulletinReleveMatiere rm = new BulletinReleveMatiere(
                        annee,
                        key,
                        nomMat,
                        coef > 0 ? coef : 1.0,
                        noteMax > 0 ? noteMax : 10.0
                    );
                    rm.ajouterNote(session, val);
                    result.add(rm);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération notes seules: " + e.getMessage(), e);
        }
        return result;
    }

    // ==================== RÉCUPÉRATION DES MATIÈRES ====================

    public List<Matiere> getMatieresParClasse(String classe) {
        List<Matiere> result = new ArrayList<>();
        String sql = "SELECT * FROM " + MigrationManager.TABLE_MATIERES +
                     " WHERE classe_matiere = ? AND institution_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, classe);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMatiere(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération matières: " + e.getMessage(), e);
        }
        return result;
    }

    private Matiere mapMatiere(ResultSet rs) throws SQLException {
        return new Matiere(
            rs.getString("institution_id"),
            rs.getString("nom_matiere"),
            rs.getDouble("coefficient"),
            rs.getDouble("note_passage"),
            rs.getDouble("note_maximale"),
            rs.getDouble("note_minimale"),
            rs.getString("classe_matiere"),
            rs.getInt("nombre_credits"),
            rs.getString("duree_cours"),
            rs.getString("periode"),
            rs.getString("statut"),
            rs.getString("code_cours"),
            rs.getString("annee_academique"),
            rs.getString("source")
        );
    }

    // ==================== ANNÉES ACADÉMIQUES ====================

    public String getAnneeActive() {
        String sql = "SELECT annee FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES +
                     " WHERE institution_id = ? ORDER BY annee DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("annee");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération année active: " + e.getMessage(), e);
        }
        return null;
    }

    public List<String> getAnneesAcademiques() {
        List<String> annees = new ArrayList<>();
        String sql = "SELECT annee_academique FROM " + MigrationManager.TABLE_ANNEES_ACADEMIQUES +
                     " WHERE institution_id = ? ORDER BY annee_academique DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    annees.add(rs.getString("annee_academique"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération années académiques: " + e.getMessage(), e);
        }
        return annees;
    }

    // ==================== PÉRIODES ====================

    public List<String> getPeriodesForYear(String annee, String classe) {
        if (institutionId == null || annee == null) return Collections.emptyList();
        List<String> sessions = new ArrayList<>();
        String sql = "SELECT periode FROM " + MigrationManager.TABLE_PERIODES +
                     " WHERE institution_id = ? AND annee_academique = ? ORDER BY periode";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(rs.getString("periode"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération périodes: " + e.getMessage(), e);
        }
        if (sessions.isEmpty()) {
            sessions.add("S1");
            sessions.add("S2");
        }
        return sessions;
    }

    // ==================== UTILITAIRES ====================

    private String getClasseEtudiant(String idEtudiant) {
        if (idEtudiant == null || institutionId == null) return null;
        String sql = "SELECT classe FROM " + MigrationManager.TABLE_ETUDIANTS +
                     " WHERE numero_identifiant = ? AND institution_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idEtudiant);
            ps.setString(2, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("classe");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur récupération classe étudiant: " + e.getMessage(), e);
        }
        return null;
    }

    // ==================== CALCULS ====================

    public static double calculerMoyenneGenerale(List<BulletinReleveMatiere> notes, String session) {
        if (notes == null || notes.isEmpty()) return 0.0;
        double totalObtenu = 0.0;
        double totalMaximum = 0.0;
        for (BulletinReleveMatiere rm : notes) {
            double note = rm.getNoteBrute(session);
            double noteEffective = (note < 0) ? 0.0 : note;
            totalObtenu += noteEffective * rm.getCoefficient();
            totalMaximum += rm.getNoteMaximale() * rm.getCoefficient();
        }
        if (totalMaximum <= 0) return 0.0;
        return (totalObtenu / totalMaximum) * 10.0;
    }

    public double calculerMoyenneAnnuelle(String idEtudiant, String annee, String classe, List<String> sessions) {
        if (institutionId == null || idEtudiant == null || sessions == null || sessions.isEmpty()) {
            return 0.0;
        }

        final String classeEffective;
        if (classe != null && !classe.trim().isEmpty()) {
            classeEffective = classe;
        } else {
            String c = getClasseEtudiant(idEtudiant);
            if (c == null || c.isEmpty()) {
                LOGGER.warning(() -> "Impossible de déterminer la classe pour l'étudiant " + idEtudiant);
                return 0.0;
            }
            classeEffective = c;
        }

        final String anneeEffective;
        if (annee != null && !annee.trim().isEmpty()) {
            anneeEffective = annee;
        } else {
            String a = getAnneeActive();
            if (a == null) return 0.0;
            anneeEffective = a;
        }

        double sommeMoyennes = 0.0;
        int nbSessions = sessions.size();
        for (String session : sessions) {
            if (session == null || session.trim().isEmpty()) continue;
            List<BulletinReleveMatiere> notes = getNotesEtudiant(idEtudiant, anneeEffective, session, classeEffective);
            double moyenneSession = calculerMoyenneGenerale(notes, session);
            sommeMoyennes += moyenneSession;
        }
        if (nbSessions == 0) return 0.0;
        return sommeMoyennes / nbSessions;
    }

    public double getMoyenneEtudiant(String idEtudiant, String annee, String session, String classe) {
        List<BulletinReleveMatiere> notes = getNotesEtudiant(idEtudiant, annee, session, classe);
        return calculerMoyenneGenerale(notes, session);
    }

    // ==================== STATISTIQUES CLASSE (CORRIGÉ AVEC DENSE_RANK) ====================

    public StatsClasse calculerStatsClasse(String classe, String annee, String session) {
        StatsClasse stats = new StatsClasse();
        List<Etudiant> etudiants = getEtudiants(classe, annee);
        stats.effectif = etudiants.size();
        if (stats.effectif == 0) {
            LOGGER.info(() -> "⚠️ Aucun étudiant pour la classe " + classe + " année " + annee);
            return stats;
        }

        // 1. Calculer la moyenne de chaque étudiant
        Map<String, Double> moyennes = new HashMap<>();
        for (Etudiant e : etudiants) {
            List<BulletinReleveMatiere> notes = getNotesEtudiant(e.getNumeroIdentifiantEtudiant(), annee, session, classe);
            double moyenne = calculerMoyenneGenerale(notes, session);
            moyennes.put(e.getNumeroIdentifiantEtudiant(), moyenne);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> "📊 Étudiant " + e.getNomComplet() + " (" + e.getNumeroIdentifiantEtudiant() + ") moyenne = " + DF.format(moyenne));
            }
        }

        // 2. Récupérer les valeurs distinctes de moyennes triées par ordre décroissant
        Set<Double> valeursDistinctes = new TreeSet<>(Collections.reverseOrder());
        valeursDistinctes.addAll(moyennes.values());

        // 3. Attribuer le rang dense à chaque étudiant
        for (Map.Entry<String, Double> entry : moyennes.entrySet()) {
            String id = entry.getKey();
            double moyenne = entry.getValue();
            int rang = 1;
            for (Double val : valeursDistinctes) {
                if (val > moyenne) {
                    rang++;
                } else {
                    break; // les valeurs sont triées décroissantes, on peut s'arrêter
                }
            }
            stats.rangs.put(id, rang);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "\ud83c\udfc5 \u00c9tudiant {0} rang {1} (moy = {2})", new Object[]{id, rang, DF.format(moyenne)});
            }
        }

        // 4. Moyenne de la classe
        double sum = 0.0;
        for (double m : moyennes.values()) sum += m;
        stats.moyenneClasse = (stats.effectif > 0) ? sum / stats.effectif : 0.0;

        LOGGER.info(() -> "📊 Stats classe " + classe + " session " + session +
                " : effectif=" + stats.effectif +
                ", moyenneClasse=" + DF.format(stats.moyenneClasse) +
                ", rangs=" + stats.rangs.size());
        return stats;
    }

    // ==================== CLASSE INTERNE ====================

    public static class StatsClasse {
        public int effectif = 0;
        public double moyenneClasse = 0.0;
        public Map<String, Integer> rangs = new HashMap<>();
    }
}