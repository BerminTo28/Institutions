import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParametresAcces {

    private static final Logger LOGGER = Logger.getLogger(ParametresAcces.class.getName());

    // ==================== RÉCUPÉRATION DES LISTES ====================

    public List<AnneeAcademique> getAnnees(String institutionId) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AnneeAcademiqueData data = new AnneeAcademiqueData(conn);
            return data.listByInstitution(institutionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur chargement années: {0}", e.getMessage());
            throw new Exception("Impossible de charger les années académiques.");
        }
    }

    public List<Periode> getPeriodes(String institutionId, String annee) throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            PeriodeData data = new PeriodeData(conn);
            return data.listByInstitutionAndAnnee(institutionId, annee);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur chargement périodes: " + e.getMessage());
            throw new Exception("Impossible de charger les périodes.");
        }
    }

    public List<Classe> getClasses(String institutionId, String annee, String periode) throws Exception {
        // ✅ Correction : utiliser la connexion partagée
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ClasseData classeData = new ClasseData(conn);
            return classeData.listByInstitutionAnneePeriode(institutionId, annee, periode);
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur chargement classes: " + e.getMessage());
            throw new Exception("Impossible de charger les classes.");
        }
    }

    // ==================== ÉTUDIANTS AVEC DROITS ====================

    public List<EtudiantAccesDTO> getEtudiantsWithAcces(String institutionId, String annee,
                                                          String periode, String classe) throws Exception {
        List<EtudiantAccesDTO> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            EtudiantData etudiantData = new EtudiantData(conn);
            List<Etudiant> etudiants = etudiantData.readWithFilters(institutionId, classe, annee, periode);

            AccesEtudiantData localAccesData = new AccesEtudiantData(conn);
            List<AccesEtudiant> allAcces = localAccesData.getAllByInstitution(institutionId);

            Map<String, AccesEtudiant> accesMap = new HashMap<>();
            for (AccesEtudiant a : allAcces) {
                accesMap.put(a.getNumeroIdentifiant(), a);
            }

            for (Etudiant e : etudiants) {
                AccesEtudiant acces = accesMap.get(e.getNumeroIdentifiantEtudiant());
                if (acces == null) {
                    acces = createDefaultAcces(e.getNumeroIdentifiantEtudiant(), institutionId);
                }
                result.add(new EtudiantAccesDTO(e, acces));
            }
            return result;
        } catch (SQLException e) {
            LOGGER.severe(() -> "Erreur chargement étudiants: " + e.getMessage());
            throw new Exception("Impossible de charger les étudiants.");
        }
    }

    private AccesEtudiant createDefaultAcces(String numeroIdentifiant, String institutionId) {
        AccesEtudiant acces = new AccesEtudiant();
        acces.setNumeroIdentifiant(numeroIdentifiant);
        acces.setInstitutionId(institutionId);
        acces.setConnexion(true);
        acces.setProfil(true);
        acces.setNotes(true);
        acces.setBulletin(true);
        acces.setPaiement(true);
        acces.setMessages(true);
        acces.setDocuments(true);
        acces.setEdt(true);
        acces.setAttestations(true);
        acces.setAbsences(true);
        acces.setAssistance(true);
        acces.setStatistiques(true);
        acces.setActivites(true);
        acces.setSource("LOCAL");
        return acces;
    }

    // ==================== SAUVEGARDE EN MASSE (avec transaction + sync) ====================

    /**
     * Sauvegarde en masse des droits d'accès dans une SEULE transaction.
     * Toutes les opérations réussissent ou échouent ensemble.
     * Une sync asynchrone est déclenchée après le commit.
     */
    public void updateAccesBulk(List<AccesEtudiant> accesList) throws Exception {
        if (accesList == null || accesList.isEmpty()) {
            LOGGER.fine("Aucun droit d'accès à sauvegarder.");
            return;
        }

        Connection conn = null;
        boolean autoCommitOriginal ;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            AccesEtudiantData localData = new AccesEtudiantData(conn);

            try {
                int inserts = 0, updates = 0;
                for (AccesEtudiant acces : accesList) {
                    AccesEtudiant existing = localData.getByEtudiant(
                            acces.getNumeroIdentifiant(), acces.getInstitutionId());
                    if (existing == null) {
                        localData.insert(acces);
                        inserts++;
                    } else {
                        localData.update(acces);
                        updates++;
                    }
                }

                conn.commit();
                final int ins = inserts, upd = updates;
                LOGGER.info(() -> "✅ Droits d'accès enregistrés (transaction) : "
                        + ins + " insertions, " + upd + " mises à jour");

                // ✅ Sync ASYNCHRONE après commit
                declencherSyncAsyncApresCommit();

            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "❌ Rollback mise à jour droits d'accès", e);
                throw new Exception("Impossible de sauvegarder les droits d'accès.");
            } finally {
                restoreAutoCommit(conn, autoCommitOriginal);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur connexion pour updateAccesBulk", e);
            throw new Exception("Impossible d'obtenir une connexion.");
        } finally {
            closeQuietly(conn);
        }
    }

    // ============================================================
    // SYNCHRONISATION ASYNCHRONE
    // ============================================================

    private void declencherSyncAsyncApresCommit() {
        try {
            DatabaseManager.getInstance().declencherSyncImmediateAsync();
            LOGGER.fine("⚡ Sync asynchrone déclenchée après commit.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Impossible de déclencher la sync async", e);
        }
    }

    // ============================================================
    // GESTION PROPRE DES CONNEXIONS
    // ============================================================

    private void restoreAutoCommit(Connection conn, boolean autoCommitOriginal) {
        if (conn == null) return;
        try {
            if (conn.getAutoCommit() != autoCommitOriginal) {
                conn.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException ignored) {}
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException ignored) {}
    }

    // ============================================================
    // DTO interne
    // ============================================================
    public static class EtudiantAccesDTO {
        public final Etudiant etudiant;
        public final AccesEtudiant acces;

        public EtudiantAccesDTO(Etudiant etudiant, AccesEtudiant acces) {
            this.etudiant = etudiant;
            this.acces = acces;
        }
    }
}