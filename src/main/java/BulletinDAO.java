import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour récupérer les notes brutes d'un étudiant.
 * Adapté au schéma réel : table notes avec code_cours, valeur, etc.
 */
public class BulletinDAO {
    private final String institutionId;

    public BulletinDAO(String institutionId) {
        this.institutionId = institutionId;
    }

    /**
     * Récupère la liste des notes d'un étudiant pour une année, session et classe données.
     *
     * @param numeroEtudiant identifiant de l'étudiant (numero_identifiant)
     * @param annee          année académique (ex: "2024-2025")
     * @param session        session (ex: "1", "2", "Rattrapage")
     * @param classe         classe de l'étudiant
     * @return liste de NoteRaw (intitulé, coefficient, note max, note obtenue)
     */
    public List<NoteRaw> getNotesEtudiant(String numeroEtudiant, String annee, String session, String classe) {
        List<NoteRaw> notes = new ArrayList<>();
        String sql = "SELECT m.nom_matiere AS intitule, m.coefficient, m.note_maximale, n.valeur AS note_obtenue " +
                "FROM " + MigrationManager.TABLE_NOTES + " n " +
                "JOIN " + MigrationManager.TABLE_MATIERES + " m " +
                "  ON n.code_cours = m.code_cours AND n.institution_id = m.institution_id " +
                "WHERE n.numero_identifiant = ? " +
                "  AND n.annee_academique = ? " +
                "  AND n.session = ? " +
                "  AND n.classe = ? " +
                "  AND n.institution_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroEtudiant);
            ps.setString(2, annee);
            ps.setString(3, session);
            ps.setString(4, classe);
            ps.setString(5, institutionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                NoteRaw nr = new NoteRaw();
                nr.intitule = rs.getString("intitule");
                nr.coefficient = rs.getDouble("coefficient");
                nr.noteMaximale = rs.getDouble("note_maximale");
                nr.noteObtenue = rs.getDouble("note_obtenue");
                notes.add(nr);
            }
        } catch (SQLException e) {
        }
        return notes;
    }

    /**
     * Classe interne pour transporter une note brute.
     */
    public static class NoteRaw {
        public String intitule;
        public double coefficient;
        public double noteMaximale;
        public double noteObtenue;
    }
}