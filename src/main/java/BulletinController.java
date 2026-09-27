import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BulletinController {

    private final String institutionId;
    private final Connection connection;
    private final EtudiantData etudiantData;
    private final BulletinService bulletinService;
    private final BulletinPDFBuilder pdfBuilder;
    private final BulletinGenerator generator;

    public BulletinController(String institutionId, Connection connection) {
        this.institutionId = institutionId;
        this.connection = connection;
        this.etudiantData = new EtudiantData(connection);
        this.bulletinService = new BulletinService(institutionId, connection);
        this.pdfBuilder = new BulletinPDFBuilder();
        this.generator = new BulletinGenerator(institutionId, connection);
    }

    public BulletinController(String institutionId) throws SQLException {
        this(institutionId, DatabaseManager.getInstance().getConnection());
    }

    private Institution getInstitution() {
        return generator.getInstitutionInfo();
    }

    /**
     * Mapping complet d'un étudiant depuis un ResultSet
     */
    private Etudiant mapResultSetToEtudiant(ResultSet rs) throws SQLException {
        Etudiant e = new Etudiant();
        
        // Identifiants et informations personnelles
        e.setNumeroIdentifiantEtudiant(rs.getString("numero_identifiant"));
        e.setNom(rs.getString("nom"));
        e.setPrenom(rs.getString("prenom"));
        e.setSexe(rs.getString("sexe"));
        e.setDateNaissance(rs.getDate("date_naissance"));
        e.setGroupeSanguin(rs.getString("groupe_sanguin"));
        
        // Coordonnées
        e.setTelephone(rs.getString("telephone"));
        e.setEmail(rs.getString("email"));
        e.setAdresse(rs.getString("adresse"));
        
        // Informations académiques
        e.setClasse(rs.getString("classe"));
        e.setAnneeAcademique(rs.getString("annee_academique"));
        e.setPromotion(rs.getString("promotion"));
        e.setMatricule(rs.getString("matricule"));
        e.setNinu(rs.getString("ninu"));
        e.setInstitutionId(rs.getString("institution_id"));
        e.setStatut(rs.getString("statut"));
        e.setDateInscription(rs.getDate("date_inscription"));
        
        // Lieu de naissance
        e.setDepartementNaissance(rs.getString("departement_naissance"));
        e.setCommuneNaissance(rs.getString("commune_naissance"));
        
        // Informations parents
        e.setNomPere(rs.getString("nom_pere"));
        e.setPrenomPere(rs.getString("prenom_pere"));
        e.setNomMere(rs.getString("nom_mere"));
        e.setPrenomMere(rs.getString("prenom_mere"));
        e.setTelephoneParents(rs.getString("telephone_parents"));
        e.setResidenceParents(rs.getString("residence_parents"));
        e.setLienParente(rs.getString("lien_parente"));
        
        // Responsable légal
        e.setTypeResponsable(rs.getString("type_responsable"));
        e.setNomResponsable(rs.getString("nom_responsable"));
        e.setPrenomResponsable(rs.getString("prenom_responsable"));
        
        // Fichiers et biométrie
        e.setPhotoPath(rs.getString("photo_path"));
        e.setObservations(rs.getString("observations"));
        e.setEmpreintePath(rs.getString("empreinte_path"));
        e.setVisagePath(rs.getString("visage_path"));
        e.setBiometrieActive(rs.getBoolean("biometrie_active"));
        
        return e;
    }

    private List<Etudiant> findByClasse(String classe, String annee) {
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
        }
        return result;
    }

    // --- Méthodes publiques ---

    public boolean genererBulletinsPourClasse(String classe, String annee, String session) {
        List<Etudiant> etudiants = findByClasse(classe, annee);
        if (etudiants.isEmpty()) {
            System.out.println("Aucun étudiant trouvé pour la classe: " + classe + ", année: " + annee);
            return false;
        }

        Institution institution = getInstitution();
        if (institution == null) {
            System.out.println("Institution non trouvée pour ID: " + institutionId);
            return false;
        }

        Map<String, Integer> rangs = bulletinService.calculerRangsParClasse(classe, annee, session);
        File dossierSortie = getDownloadsFolder();

        int succes = 0;
        for (Etudiant e : etudiants) {
            BulletinData data = bulletinService.buildBulletinData(e, annee, session, rangs);
            data.setInstitution(institution);
            File pdf = pdfBuilder.build(data, dossierSortie);
            if (pdf != null && pdf.exists()) {
                succes++;
            }
        }
        System.out.println("Génération : " + succes + "/" + etudiants.size());
        if (succes > 0) {
            try { 
                Desktop.getDesktop().open(dossierSortie); 
            } catch (IOException ignored) {
                System.out.println("Impossible d'ouvrir le dossier: " + dossierSortie.getAbsolutePath());
            }
        }
        return succes > 0;
    }

    public boolean genererBulletinIndividuel(String numeroEtudiant, String annee, String session) {
        Etudiant e = etudiantData.read(numeroEtudiant,institutionId);
        if (e == null) {
            System.out.println("Étudiant non trouvé: " + numeroEtudiant);
            return false;
        }

        Institution institution = getInstitution();
        if (institution == null) {
            System.out.println("Institution non trouvée pour ID: " + institutionId);
            return false;
        }

        BulletinData data = bulletinService.buildBulletinData(e, annee, session, null);
        data.setInstitution(institution);
        File dossier = getDownloadsFolder();
        File pdf = pdfBuilder.build(data, dossier);
        if (pdf != null) {
            try { 
                Desktop.getDesktop().open(dossier); 
            } catch (IOException ignored) {
                System.out.println("Impossible d'ouvrir le dossier: " + dossier.getAbsolutePath());
            }
            return true;
        }
        return false;
    }

    public BulletinData getBulletinDataForPreview(String numeroEtudiant, String annee, String session) {
        Etudiant e = etudiantData.read(numeroEtudiant,institutionId);
        if (e == null) return null;
        Institution institution = getInstitution();
        if (institution == null) return null;
        BulletinData data = bulletinService.buildBulletinData(e, annee, session, null);
        data.setInstitution(institution);
        return data;
    }

    public BulletinData getBulletinDataMultiSession(String idEtudiant, String annee, String[] sessions,
                                                    String classe, String numero, String reference) {
        Etudiant e = etudiantData.read(idEtudiant,institutionId);
        if (e == null) {
            System.out.println("Étudiant non trouvé: " + idEtudiant);
            return null;
        }
        Institution institution = getInstitution();
        if (institution == null) {
            System.out.println("Institution non trouvée pour ID: " + institutionId);
            return null;
        }

        List<BulletinData> datas = new ArrayList<>();
        for (String session : sessions) {
            Map<String, Integer> rangs = bulletinService.calculerRangsParClasse(classe, annee, session);
            BulletinData data = bulletinService.buildBulletinData(e, annee, session, rangs);
            data.setInstitution(institution);
            datas.add(data);
        }

        List<BulletinReleveMatiere> mergedMatieres = pdfBuilder.mergeMatieresBySession(datas, Arrays.asList(sessions));
        BulletinData mergedData = new BulletinData();
        mergedData.setEtudiant(e);
        mergedData.setInstitution(institution);
        mergedData.setAnnee(annee);
        mergedData.setSession(String.join(", ", sessions));
        mergedData.setReleves(mergedMatieres);
        mergedData.setNumero(numero);
        mergedData.setReference(reference);

        double moyenneAnnuelle = bulletinService.calculerMoyenneAnnuelle(e, annee, classe);
        mergedData.setMoyenneGenerale(moyenneAnnuelle);
        mergedData.setDecision(moyenneAnnuelle >= 50 ? "ADMIS" : "ÉCHEC");
        mergedData.setDecisionColorHex(moyenneAnnuelle >= 50 ? "#008000" : "#FF0000");
        return mergedData;
    }

   public String genererBulletinMultiSessionPDF(String idEtudiant, String annee, String[] sessionsArray,
                                             String classe, String numero, String reference) {
    Etudiant e = etudiantData.read(idEtudiant,institutionId);
    if (e == null) {
        System.out.println("Étudiant non trouvé: " + idEtudiant);
        return null;
    }
    Institution institution = getInstitution();
    if (institution == null) {
        System.out.println("Institution non trouvée pour ID: " + institutionId);
        return null;
    }

    // ✅ Dédoublonner les sessions
    Set<String> uniqueSessions = new LinkedHashSet<>(Arrays.asList(sessionsArray));
    if (uniqueSessions.isEmpty()) {
        return null;
    }
    List<String> sessions = new ArrayList<>(uniqueSessions);

    List<BulletinData> datas = new ArrayList<>();
    List<Integer> rangsParSession = new ArrayList<>(); // <-- NOUVEAU

    for (String session : sessions) {
        Map<String, Integer> rangs = bulletinService.calculerRangsParClasse(classe, annee, session);
        BulletinData data = bulletinService.buildBulletinData(e, annee, session, rangs);
        data.setInstitution(institution);
        if (numero != null && !numero.isEmpty()) data.setNumero(numero);
        if (reference != null && !reference.isEmpty()) data.setReference(reference);
        datas.add(data);
        // Récupérer le rang de l'étudiant pour cette session
        Integer rang = rangs.get(idEtudiant);
        rangsParSession.add(rang != null ? rang : 0);
    }

    double moyenneAnnuelle = bulletinService.calculerMoyenneAnnuelle(e, annee, classe);
    File dossier = getDownloadsFolder();
    // ✅ Passage de la liste des rangs par session
    File pdf = pdfBuilder.buildMultiSession(datas, dossier, e, institution, moyenneAnnuelle, rangsParSession);
    if (pdf != null && pdf.exists()) {
        System.out.println("📄 Bulletin multi-session généré: " + pdf.getAbsolutePath());
        return pdf.getName();
    }
    return null;
}

    public boolean genererFichierBulletinPDF(String idEtudiant, String annee, String session,
                                             String classe, String dossierDestination) {
        Etudiant e = etudiantData.read(idEtudiant,institutionId);
        if (e == null) {
            System.out.println("Étudiant non trouvé: " + idEtudiant);
            return false;
        }
        Institution institution = getInstitution();
        if (institution == null) {
            System.out.println("Institution non trouvée pour ID: " + institutionId);
            return false;
        }

        File dossier = (dossierDestination != null && !dossierDestination.trim().isEmpty())
                ? new File(dossierDestination) : getDownloadsFolder();
        if (!dossier.exists()) {
            dossier.mkdirs();
        }

        Map<String, Integer> rangs = bulletinService.calculerRangsParClasse(e.getClasse(), annee, session);
        BulletinData data = bulletinService.buildBulletinData(e, annee, session, rangs);
        data.setInstitution(institution);

        File pdf = pdfBuilder.build(data, dossier);
        if (pdf != null && pdf.exists()) {
            System.out.println("📄 Bulletin : " + pdf.getAbsolutePath());
            return true;
        }
        return false;
    }

    private File getDownloadsFolder() {
        String home = System.getProperty("user.home");
        File dl = new File(home, "Downloads");
        if (!dl.exists()) {
            dl = new File(home, "Téléchargements");
        }
        if (!dl.exists()) {
            dl = new File(".");
        }
        return dl;
    }
}