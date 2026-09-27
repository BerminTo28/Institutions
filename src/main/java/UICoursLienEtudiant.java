import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Interface utilisateur pour l'affichage des liens de cours par l'étudiant
 */
public class UICoursLienEtudiant {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Affiche la liste des liens de cours pour l'étudiant
     */
    public static String renderListe(List<CoursLien> liens, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes cours en ligne</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-video\"></i> Mes cours en ligne</h1>");
        sb.append("<p class=\"subtitle\">Rejoignez vos cours à distance</p>");
        sb.append("</div>");

        if (message != null && !message.isEmpty()) {
            sb.append("<div class=\"message info\"><i class=\"fas fa-info-circle\"></i> ").append(echapperHtml(message)).append("</div>");
        }

        if (liens == null || liens.isEmpty()) {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-video-slash\"></i>");
            sb.append("<p>Aucun cours en ligne prévu pour le moment.</p>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"cards-grid\">");
            for (CoursLien lien : liens) {
                boolean estEnCours = lien.getDateDebut() != null && lien.getDateDebut().isBefore(LocalDateTime.now()) &&
                                     (lien.getDateFin() == null || lien.getDateFin().isAfter(LocalDateTime.now()));
                boolean estFini = lien.getDateFin() != null && lien.getDateFin().isBefore(LocalDateTime.now());

                sb.append("<div class=\"card-lien\">");
                sb.append("<div class=\"card-header\">");
                sb.append("<h2>").append(echapperHtml(lien.getTitre())).append("</h2>");
                if (estEnCours) {
                    sb.append("<span class=\"badge en-cours\"><i class=\"fas fa-circle\"></i> En cours</span>");
                } else if (estFini) {
                    sb.append("<span class=\"badge termine\"><i class=\"fas fa-check-circle\"></i> Terminé</span>");
                } else {
                    sb.append("<span class=\"badge a-venir\"><i class=\"fas fa-clock\"></i> À venir</span>");
                }
                sb.append("</div>");
                sb.append("<div class=\"card-body\">");
                if (lien.getDescription() != null && !lien.getDescription().isEmpty()) {
                    sb.append("<p class=\"description\">").append(echapperHtml(lien.getDescription())).append("</p>");
                }
                sb.append("<div class=\"info-row\">");
                sb.append("<i class=\"fas fa-calendar-alt\"></i> ").append(lien.getDateDebut() != null ? lien.getDateDebut().format(DATE_FORMATTER) : "").append(" - ").append(lien.getDateFin() != null ? lien.getDateFin().format(DATE_FORMATTER) : "");
                sb.append("</div>");
                if (!estFini) {
                    sb.append("<a href=\"").append(echapperHtml(lien.getUrl())).append("\" target=\"_blank\" class=\"btn-join\"><i class=\"fas fa-sign-in-alt\"></i> Rejoindre le cours</a>");
                } else {
                    sb.append("<span class=\"btn-disabled\"><i class=\"fas fa-lock\"></i> Cours terminé</span>");
                }
                sb.append("</div>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        sb.append("<div class=\"footer\">");
        sb.append("<a href=\"/etudiant/dashboard\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES ====================

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; padding:20px; min-height:100vh; }
        .container { max-width:900px; margin:0 auto; }
        .header { text-align:center; margin-bottom:25px; }
        .header h1 { font-size:28px; color:#0f172a; }
        .header h1 i { color:#3b82f6; margin-right:10px; }
        .subtitle { color:#64748b; font-size:16px; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; background:#dbeafe; color:#1e40af; border-left:4px solid #1e40af; }
        .cards-grid { display:grid; grid-template-columns:1fr; gap:20px; }
        .card-lien { background:white; border-radius:12px; padding:20px; box-shadow:0 2px 10px rgba(0,0,0,0.05); border-left:4px solid #3b82f6; transition:0.2s; }
        .card-lien:hover { transform:translateY(-2px); box-shadow:0 8px 25px rgba(0,0,0,0.08); }
        .card-header { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px; margin-bottom:10px; }
        .card-header h2 { font-size:20px; color:#0f172a; margin:0; }
        .badge { display:inline-block; padding:4px 14px; border-radius:20px; font-size:12px; font-weight:600; }
        .badge.en-cours { background:#dbeafe; color:#1e40af; }
        .badge.en-cours i { color:#3b82f6; margin-right:4px; }
        .badge.a-venir { background:#fef3c7; color:#b45309; }
        .badge.a-venir i { color:#b45309; margin-right:4px; }
        .badge.termine { background:#d1fae5; color:#16a34a; }
        .badge.termine i { color:#16a34a; margin-right:4px; }
        .description { color:#475569; margin:10px 0; }
        .info-row { color:#64748b; font-size:14px; margin:8px 0; }
        .info-row i { width:20px; color:#3b82f6; }
        .btn-join { display:inline-block; margin-top:12px; padding:10px 24px; background:#1e40af; color:white; border-radius:8px; text-decoration:none; font-weight:600; transition:0.2s; border:none; cursor:pointer; }
        .btn-join:hover { background:#1e3a8a; transform:translateY(-2px); }
        .btn-join i { margin-right:8px; }
        .btn-disabled { display:inline-block; margin-top:12px; padding:10px 24px; background:#e2e8f0; color:#94a3b8; border-radius:8px; font-weight:600; cursor:not-allowed; }
        .btn-disabled i { margin-right:8px; }
        .empty-state { text-align:center; padding:60px 20px; background:white; border-radius:12px; }
        .empty-state i { font-size:64px; color:#cbd5e1; margin-bottom:15px; }
        .footer { margin-top:30px; text-align:center; }
        .footer a { color:#64748b; text-decoration:none; }
        .footer a:hover { color:#1e293b; }
        @media (min-width:768px) { .cards-grid { grid-template-columns:1fr 1fr; } }
        """;
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