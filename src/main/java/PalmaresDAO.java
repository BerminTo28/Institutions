import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PalmaresDAO {
    private final Connection connection;

    public PalmaresDAO(Connection connection) {
        this.connection = connection;
    }

    public List<PalmaresEntry> getEtudiantsDeLaClasse(String institutionId, String annee, String periode, String classe) throws SQLException {
        List<PalmaresEntry> list = new ArrayList<>();
        String sql = "SELECT numero_identifiant, nom, prenom, matricule FROM etudiants " +
                     "WHERE institution_id = ? AND classe = ? AND annee_academique = ? AND periode = ? " +
                     "ORDER BY nom, prenom";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, classe);
            ps.setString(3, annee);
            ps.setString(4, periode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PalmaresEntry entry = new PalmaresEntry();
                    entry.setId(rs.getString("numero_identifiant"));
                    entry.setNom(rs.getString("nom"));
                    entry.setPrenom(rs.getString("prenom"));
                    entry.setMatricule(rs.getString("matricule"));
                    list.add(entry);
                }
            }
        }
        return list;
    }
}