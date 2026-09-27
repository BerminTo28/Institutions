import java.util.List;
import java.util.Map;

public class UICommunicationHelper {

    public static String renderFiltres(String annee, String session, String classe, List<String> annees, List<String> sessions, List<String> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"filters\">");
        sb.append("<div class=\"filter-group\">");
        sb.append("<label>Année</label>");
        sb.append("<select name=\"annee\" id=\"anneeSelect\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String a : annees) {
            String selected = a.equals(annee) ? "selected" : "";
            sb.append("<option value=\"").append(a).append("\" ").append(selected).append(">").append(a).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"filter-group\">");
        sb.append("<label>Session</label>");
        sb.append("<select name=\"session\" id=\"sessionSelect\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String s : sessions) {
            String selected = s.equals(session) ? "selected" : "";
            sb.append("<option value=\"").append(s).append("\" ").append(selected).append(">").append(s).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<div class=\"filter-group\">");
        sb.append("<label>Classe</label>");
        sb.append("<select name=\"classe\" id=\"classeSelect\">");
        sb.append("<option value=\"\">Sélectionner</option>");
        for (String c : classes) {
            String selected = c.equals(classe) ? "selected" : "";
            sb.append("<option value=\"").append(c).append("\" ").append(selected).append(">").append(c).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("<button id=\"btnCharger\" class=\"btn btn-primary\"><i class=\"fas fa-search\"></i> Charger</button>");
        sb.append("</div>");
        return sb.toString();
    }

    public static String renderTableauEtudiants(List<Map<String, String>> etudiants) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-container\">");
        sb.append("<table><thead><tr>");
        sb.append("<th><input type=\"checkbox\" id=\"checkAll\"></th>");
        sb.append("<th>ID</th><th>Nom</th><th>Prénom</th><th>Classe</th><th>Email</th><th>Téléphone</th>");
        sb.append("</tr></thead><tbody>");
        if (etudiants == null || etudiants.isEmpty()) {
            sb.append("<tr><td colspan=\"7\" class=\"empty-state\">Aucun étudiant trouvé</td></tr>");
        } else {
            for (Map<String, String> e : etudiants) {
                sb.append("<tr>");
                sb.append("<td><input type=\"checkbox\" class=\"etudiant-check\" value=\"").append(e.get("id")).append("\"></td>");
                sb.append("<td>").append(e.get("id")).append("</td>");
                sb.append("<td>").append(e.get("nom")).append("</td>");
                sb.append("<td>").append(e.get("prenom")).append("</td>");
                sb.append("<td>").append(e.get("classe")).append("</td>");
                sb.append("<td>").append(e.get("email")).append("</td>");
                sb.append("<td>").append(e.get("telephone")).append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");
        sb.append("</div>");
        return sb.toString();
    }
}