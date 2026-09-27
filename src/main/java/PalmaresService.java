import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PalmaresService {

    private final PalmaresDAO dao;
    private final BulletinGenerator bulletinGenerator;
    private final String institutionId;
    private final Connection connection;

    public PalmaresService(Connection connection, String institutionId) {
        this.connection = connection;
        this.dao = new PalmaresDAO(connection);
        this.bulletinGenerator = new BulletinGenerator(institutionId, connection);
        this.institutionId = institutionId;
    }

    /**
     * Récupère le palmarès détaillé pour une année, une session (période) et une classe donnés.
     * N'affiche que les matières de la période sélectionnée.
     */
    public List<PalmaresEntry> getPalmaresDetail(String annee, String session, String classe) throws SQLException {
        // 1. Récupérer tous les étudiants de la classe pour cette année et cette période
        List<PalmaresEntry> entries = dao.getEtudiantsDeLaClasse(institutionId, annee, session, classe);

        // 2. Récupérer toutes les matières de la classe pour cette période
        MatiereData matiereData = new MatiereData(institutionId);
        List<Matiere> matieresPeriode = matiereData.rechercherAvecFiltres(null, annee, session, classe);
        // Si aucune matière trouvée, la liste sera vide

        for (PalmaresEntry entry : entries) {
            // 3. Récupérer les notes de l'étudiant (toutes périodes confondues si BulletinGenerator ne filtre pas)
            List<BulletinReleveMatiere> notesBrutes = bulletinGenerator.getNotesEtudiant(
                    entry.getId(), annee, session, classe);

            // 4. Construire une map nom_matiere -> note pour les notes brutes
            Map<String, Double> noteMap = new HashMap<>();
            for (BulletinReleveMatiere rm : notesBrutes) {
                String key = rm.getIntituleMatiere();
                noteMap.put(key, rm.getNoteBrute(session));
            }

            // 5. Construire la liste des MatiereNote à partir des matières de la période
            List<PalmaresEntry.MatiereNote> matiereNotes = new ArrayList<>();
            List<BulletinReleveMatiere> relevesPourMoyenne = new ArrayList<>();

            for (Matiere m : matieresPeriode) {
                String nomMatiere = m.getNomMatiere();
                Double note = noteMap.getOrDefault(nomMatiere, -1.0);

                PalmaresEntry.MatiereNote mn = new PalmaresEntry.MatiereNote();
                mn.setNom(nomMatiere);
                mn.setNote(note);
                mn.setNoteSur(m.getNoteMaximale());
                mn.setCoefficient(m.getCoefficient());
                matiereNotes.add(mn);

                // Créer un BulletinReleveMatiere pour le calcul de la moyenne sur la période
                BulletinReleveMatiere rm = new BulletinReleveMatiere(
                        annee,
                        m.getCodeCours(),
                        nomMatiere,
                        m.getCoefficient(),
                        m.getNoteMaximale()
                );
                rm.ajouterNote(session, note);
                relevesPourMoyenne.add(rm);
            }

            entry.setMatieres(matiereNotes);

            // 6. Calculer la moyenne sur la période
            double moyenne = BulletinGenerator.calculerMoyenneGenerale(relevesPourMoyenne, session);
            entry.setMoyenne(moyenne);

            // Mention
            if (matiereNotes.isEmpty()) {
                entry.setMention("Non évalué");
            } else {
                entry.setMention(moyenne >= 5 ? "Admis(e)" : "Maintenu(e)");
            }
        }

        // 7. Tri par moyenne décroissante
        entries.sort((a, b) -> Double.compare(b.getMoyenne(), a.getMoyenne()));

        // 8. Attribution des rangs (ex-aequo)
        int rank ;
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0 && Math.abs(entries.get(i).getMoyenne() - entries.get(i - 1).getMoyenne()) < 0.0001) {
                entries.get(i).setRang(entries.get(i - 1).getRang());
            } else {
                rank = i + 1;
                entries.get(i).setRang(rank);
            }
        }

        return entries;
    }

    // ========== Méthodes pour les filtres ==========

    public Institution getInstitution() {
        return bulletinGenerator.getInstitutionInfo();
    }

    public List<String> getAnneesAcademiques() {
        return bulletinGenerator.getAnneesAcademiques();
    }

    public List<String> getSessions(String annee) {
        return bulletinGenerator.getPeriodesForYear(annee, null);
    }

    public List<String> getClasses() {
        String sql = "SELECT DISTINCT nom_classe FROM classes WHERE institution_id = ? ORDER BY nom_classe";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> classes = new ArrayList<>();
                while (rs.next()) {
                    classes.add(rs.getString("nom_classe"));
                }
                return classes;
            }
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }
}