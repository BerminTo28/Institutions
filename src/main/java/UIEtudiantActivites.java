import java.text.SimpleDateFormat;
import java.util.List;

public class UIEtudiantActivites {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public static String render(List<Evenement> activites,
                                List<String> anneesDisponibles,
                                List<String> periodesDisponibles,
                                List<String> classesDisponibles,
                                String anneeSel,
                                String periodeSel,
                                String classeSel,
                                String numeroEtudiant,
                                String emailEtudiant,
                                String classeEtudiant) {

        if (activites == null) activites = List.of();
        if (anneesDisponibles == null) anneesDisponibles = List.of();
        if (periodesDisponibles == null) periodesDisponibles = List.of();
        if (classesDisponibles == null) classesDisponibles = List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang='fr'>");
        sb.append("<head>");
        sb.append("<meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>Mes Activités</title>");
        sb.append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'>");
        sb.append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>");
        sb.append("""
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
            body { padding: 20px; }
            .container { max-width: 1400px; margin: 0 auto; }
            .header { 
                display: flex; 
                justify-content: space-between; 
                align-items: center; 
                margin-bottom: 25px; 
                flex-wrap: wrap; 
                gap: 15px; 
                background: white; 
                padding: 20px 24px; 
                border-radius: 16px; 
                border: 1px solid #DCE1E6; 
                box-shadow: 0 2px 8px rgba(0,0,0,0.06); 
            }
            .header h1 { 
                font-size: 24px; 
                color: #1e293b; 
                display: flex; 
                align-items: center; 
                gap: 12px; 
            }
            .header h1 i { color: #2563EB; }
            .header .user-info { 
                color: #64748b; 
                font-size: 14px; 
                display: flex;
                flex-direction: column;
                align-items: flex-end;
                gap: 4px;
            }
            .header .user-info .classe-badge {
                background: #EFF6FF;
                color: #2563EB;
                padding: 4px 12px;
                border-radius: 12px;
                font-weight: 600;
                font-size: 13px;
            }
            .filters { 
                background: white; 
                padding: 16px 20px; 
                border-radius: 12px; 
                margin-bottom: 20px; 
                border: 1px solid #DCE1E6; 
                display: flex; 
                flex-wrap: wrap; 
                gap: 16px; 
                align-items: end; 
            }
            .filters .form-group { 
                display: flex; 
                flex-direction: column; 
                flex: 1 1 150px; 
                min-width: 120px; 
            }
            .filters .form-group label { 
                font-size: 13px; 
                font-weight: 600; 
                color: #475569; 
                margin-bottom: 4px; 
            }
            .filters .form-group select { 
                padding: 8px 12px; 
                border: 1px solid #DCE1E6; 
                border-radius: 8px; 
                font-size: 14px; 
                background: white; 
            }
            .filters .form-group select:focus {
                outline: none;
                border-color: #2563EB;
                box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
            }
            .filters .btn-group {
                display: flex;
                gap: 8px;
                flex-wrap: wrap;
            }
            .filters .btn-filter { 
                padding: 8px 20px; 
                background: #2563EB; 
                color: white; 
                border: none; 
                border-radius: 8px; 
                font-weight: 600; 
                cursor: pointer; 
                transition: 0.2s; 
                text-decoration: none;
                display: inline-flex;
                align-items: center;
                gap: 6px;
            }
            .filters .btn-filter:hover { background: #1D4ED8; }
            .filters .btn-reset { 
                padding: 8px 20px; 
                background: #E2E8F0; 
                color: #475569; 
                border: none; 
                border-radius: 8px; 
                font-weight: 600; 
                cursor: pointer; 
                transition: 0.2s; 
                text-decoration: none;
                display: inline-flex;
                align-items: center;
                gap: 6px;
            }
            .filters .btn-reset:hover { background: #CBD5E1; }
            .card { 
                background: white; 
                border-radius: 16px; 
                padding: 24px; 
                margin-bottom: 20px; 
                box-shadow: 0 2px 8px rgba(0,0,0,0.06); 
                border: 1px solid #DCE1E6; 
            }
            .card-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 16px;
                flex-wrap: wrap;
                gap: 10px;
            }
            .card-header h2 {
                font-size: 18px;
                color: #1E293B;
                font-weight: 600;
            }
            .card-header .count {
                font-size: 14px;
                color: #64748B;
                background: #F1F5F9;
                padding: 4px 12px;
                border-radius: 12px;
            }
            .table-responsive { overflow-x: auto; }
            table { width: 100%; border-collapse: collapse; }
            th { 
                background: #EFF6FF; 
                padding: 14px 16px; 
                text-align: left; 
                font-weight: 600; 
                border-bottom: 1px solid #DCE1E6; 
                font-size: 13px; 
                color: #1E293B; 
                white-space: nowrap;
            }
            td { 
                padding: 12px 16px; 
                border-bottom: 1px solid #E2E8F0; 
                font-size: 14px; 
                vertical-align: middle; 
            }
            tr:hover td { background: #FAFBFB; }
            .badge { 
                display: inline-block; 
                padding: 4px 12px; 
                border-radius: 12px; 
                font-size: 12px; 
                font-weight: 500; 
            }
            .badge-avenir { background: #DBEAFE; color: #1E40AF; }
            .badge-encours { background: #FEF3C7; color: #92400E; }
            .badge-termine { background: #D1FAE5; color: #065F46; }
            .badge-annule { background: #FEE2E2; color: #991B1B; }
            .badge-generale { background: #E0E7FF; color: #4338CA; }
            .badge-classe { background: #FCE7F3; color: #9D174D; }
            .badge-participant { 
                display: inline-block; 
                padding: 2px 10px; 
                border-radius: 10px; 
                font-size: 11px; 
                font-weight: 500;
                margin: 2px 4px 2px 0;
            }
            .badge-etudiant { background: #DBEAFE; color: #1E40AF; }
            .badge-professeur { background: #FEF3C7; color: #92400E; }
            .empty-state { 
                text-align: center; 
                padding: 60px 20px; 
            }
            .empty-state i { 
                font-size: 56px; 
                color: #CBD5E1; 
                margin-bottom: 16px; 
            }
            .empty-state h3 {
                color: #475569;
                font-size: 20px;
                margin-bottom: 8px;
            }
            .empty-state p { 
                color: #94A3B8; 
                font-size: 16px; 
            }
            .event-title {
                font-weight: 600;
                color: #0F172A;
            }
            .event-classe {
                font-size: 12px;
                color: #64748B;
            }
            @media (max-width: 768px) {
                body { padding: 10px; }
                .header { flex-direction: column; align-items: flex-start; }
                .header .user-info { align-items: flex-start; }
                .filters { flex-direction: column; align-items: stretch; }
                .filters .form-group { flex: 1 1 auto; }
                .filters .btn-group { flex-wrap: wrap; }
                th, td { padding: 8px 10px; font-size: 12px; }
                .card { padding: 16px; }
            }
            @media (max-width: 480px) {
                .header h1 { font-size: 20px; }
                .table-responsive { font-size: 12px; }
                th, td { padding: 6px 8px; }
                .badge { font-size: 10px; padding: 2px 8px; }
            }
        </style>
        """);
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class='container'>");

        // En-tête
        sb.append("<div class='header'>");
        sb.append("<h1><i class='fa-solid fa-calendar-days'></i> Mes Activités</h1>");
        sb.append("<div class='user-info'>");
        sb.append("<span><i class='fa-solid fa-user'></i> ").append(echapperHtml(numeroEtudiant)).append("</span>");
        if (emailEtudiant != null && !emailEtudiant.isBlank()) {
            sb.append("<span style='font-size:12px;color:#94A3B8;'><i class='fa-solid fa-envelope'></i> ").append(echapperHtml(emailEtudiant)).append("</span>");
        }
        if (classeEtudiant != null && !classeEtudiant.isBlank()) {
            sb.append("<span class='classe-badge'><i class='fa-solid fa-users'></i> ").append(echapperHtml(classeEtudiant)).append("</span>");
        }
        sb.append("</div>");
        sb.append("</div>");

        // Filtres
        sb.append("<form method='GET' action='/etudiant/activites' class='filters'>");
        
        // Filtre Année
        sb.append("<div class='form-group'>");
        sb.append("<label for='annee'><i class='fa-solid fa-calendar'></i> Année académique</label>");
        sb.append("<select name='annee' id='annee'>");
        sb.append("<option value=''>Toutes</option>");
        for (String a : anneesDisponibles) {
            String selected = a != null && a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value='").append(echapperHtml(a)).append("' ").append(selected).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        // Filtre Période
        sb.append("<div class='form-group'>");
        sb.append("<label for='periode'><i class='fa-solid fa-clock'></i> Période</label>");
        sb.append("<select name='periode' id='periode'>");
        sb.append("<option value=''>Toutes</option>");
        for (String p : periodesDisponibles) {
            String selected = p != null && p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value='").append(echapperHtml(p)).append("' ").append(selected).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        // Filtre Classe (optionnel - permet de filtrer mais affiche aussi les événements sans classe)
        sb.append("<div class='form-group'>");
        sb.append("<label for='classe'><i class='fa-solid fa-users'></i> Classe</label>");
        sb.append("<select name='classe' id='classe'>");
        sb.append("<option value=''>Toutes</option>");
        sb.append("<option value='__aucune__' ").append("__aucune__".equals(classeSel) ? "selected" : "").append(">Sans classe</option>");
        for (String c : classesDisponibles) {
            String selected = c != null && c.equals(classeSel) ? "selected" : "";
            sb.append("<option value='").append(echapperHtml(c)).append("' ").append(selected).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");

        // Boutons
        sb.append("<div class='btn-group'>");
        sb.append("<button type='submit' class='btn-filter'><i class='fa-solid fa-filter'></i> Filtrer</button>");
        sb.append("<a href='/etudiant/activites' class='btn-reset'><i class='fa-solid fa-undo'></i> Réinitialiser</a>");
        sb.append("</div>");
        sb.append("</form>");

        // Tableau des activités
        sb.append("<div class='card'>");
        sb.append("<div class='card-header'>");
        sb.append("<h2><i class='fa-solid fa-list'></i> Liste des activités</h2>");
        sb.append("<span class='count'>").append(activites.size()).append(" activité(s)</span>");
        sb.append("</div>");
        sb.append("<div class='table-responsive'>");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th>Titre</th>");
        sb.append("<th>Description</th>");
        sb.append("<th>Début</th>");
        sb.append("<th>Fin</th>");
        sb.append("<th>Lieu</th>");
        sb.append("<th>Année</th>");
        sb.append("<th>Période</th>");
        sb.append("<th>Classe</th>");
        sb.append("<th>Participants</th>");
        sb.append("<th>Statut</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        if (activites.isEmpty()) {
            sb.append("""
                <tr>
                    <td colspan='10'>
                        <div class='empty-state'>
                            <i class='fa-regular fa-calendar-circle-exclamation'></i>
                            <h3>Aucune activité trouvée</h3>
                            <p>Vous n'êtes invité à aucune activité pour le moment.</p>
                        </div>
                    </td>
                </tr>
            """);
        } else {
            for (Evenement e : activites) {
                sb.append("<tr>");
                sb.append("<td><span class='event-title'>").append(echapperHtml(e.getTitre())).append("</span></td>");
                sb.append("<td>").append(echapperHtml(truncate(e.getDescription(), 60))).append("</td>");
                sb.append("<td>").append(e.getDateDebut() != null ? DATE_FORMAT.format(e.getDateDebut()) : "").append("</td>");
                sb.append("<td>").append(e.getDateFin() != null ? DATE_FORMAT.format(e.getDateFin()) : "").append("</td>");
                sb.append("<td>").append(echapperHtml(e.getLieu() != null ? e.getLieu() : "-")).append("</td>");
                sb.append("<td>").append(echapperHtml(e.getAnneeAcademique() != null ? e.getAnneeAcademique() : "-")).append("</td>");
                sb.append("<td>").append(echapperHtml(e.getPeriode() != null ? e.getPeriode() : "-")).append("</td>");
                
                // Affichage de la classe avec badge spécial si générale
                String classeDisplay = e.getClasse();
                if (classeDisplay == null || classeDisplay.isBlank()) {
                    sb.append("<td><span class='badge badge-generale'><i class='fa-solid fa-globe'></i> Générale</span></td>");
                } else {
                    sb.append("<td><span class='badge badge-classe'>").append(echapperHtml(classeDisplay)).append("</span></td>");
                }
                
                // Participants
                sb.append("<td>");
                if (e.getEtudiantsEmails() != null && !e.getEtudiantsEmails().isEmpty()) {
                    for (String email : e.getEtudiantsEmails()) {
                        if (email != null && !email.isBlank()) {
                            sb.append("<span class='badge-participant badge-etudiant'><i class='fa-solid fa-user-graduate'></i> ").append(echapperHtml(email)).append("</span> ");
                        }
                    }
                }
                if (e.getProfesseursEmails() != null && !e.getProfesseursEmails().isEmpty()) {
                    for (String email : e.getProfesseursEmails()) {
                        if (email != null && !email.isBlank()) {
                            sb.append("<span class='badge-participant badge-professeur'><i class='fa-solid fa-chalkboard-user'></i> ").append(echapperHtml(email)).append("</span> ");
                        }
                    }
                }
                if ((e.getEtudiantsEmails() == null || e.getEtudiantsEmails().isEmpty()) && 
                    (e.getProfesseursEmails() == null || e.getProfesseursEmails().isEmpty())) {
                    sb.append("<span style='color:#94A3B8;font-size:12px;'>Aucun participant</span>");
                }
                sb.append("</td>");
                
                sb.append("<td>").append(getBadgeStatut(e.getStatut())).append("</td>");
                sb.append("</tr>");
            }
        }

        sb.append("</tbody></table>");
        sb.append("</div></div>");

        // Pied de page avec lien de retour
        sb.append("""
            <div style='text-align:center;padding:16px 0;'>
                <a href='/' style='color:#64748B;text-decoration:none;font-size:14px;'>
                    <i class='fa-solid fa-arrow-left'></i> Retour à l'accueil
                </a>
            </div>
        """);

        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ==================== UTILITAIRES ====================

    private static String getBadgeStatut(String statut) {
        if (statut == null) statut = "À venir";
        String badgeClass;
        badgeClass = switch (statut) {
            case "Terminé" -> "badge-termine";
            case "Annulé" -> "badge-annule";
            case "En cours" -> "badge-encours";
            default -> "badge-avenir";
        };
        return "<span class='badge " + badgeClass + "'>" + echapperHtml(statut) + "</span>";
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}