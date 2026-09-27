import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UIExamenProfesseur {

    private static final String DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231e40af'/%3E%3Ctext x='75' y='95' font-size='60' text-anchor='middle' fill='white' font-family='sans-serif'%3E📝%3C/text%3E%3C/svg%3E";

    public static String renderListe(String professeurId,
                                     List<Professeur.Creneau> creneaux,
                                     List<AnneeAcademique> annees,
                                     List<Periode> periodes,
                                     List<Classe> classes,
                                     List<Examen> examens,
                                     String codeCoursSel,
                                     String classeSel,
                                     String periodeSel,
                                     String anneeSel,
                                     String institutionId,
                                     String msgType,
                                     String msg) {

        List<Professeur.Creneau> safeCreneaux = creneaux != null ? creneaux : Collections.emptyList();
        List<AnneeAcademique> safeAnnees = annees != null ? annees : Collections.emptyList();
        List<Periode> safePeriodes = periodes != null ? periodes : Collections.emptyList();
        List<Classe> safeClasses = classes != null ? classes : Collections.emptyList();

        List<String> codesCours = safeCreneaux.stream()
                .map(Professeur.Creneau::getCodeCours)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomClasses = safeClasses.stream()
                .map(Classe::getNomClasse)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomPeriodes = safePeriodes.stream()
                .map(Periode::getPeriode)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomAnnees = safeAnnees.stream()
                .map(AnneeAcademique::getAnneeAcademique)
                .distinct()
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Gestion des Examens - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader(professeurId));

        if (msg != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(msg);
            sb.append("</div>");
        }

        sb.append(renderFilters(codesCours, nomClasses, nomPeriodes, nomAnnees,
                codeCoursSel, classeSel, periodeSel, anneeSel));

        sb.append("<div style=\"text-align:right; margin-bottom:15px;\">");
        sb.append("<a href=\"/professeur/examens?action=add\" class=\"btn btn-success\"><i class=\"fas fa-plus\"></i> Nouvel Examen</a>");
        sb.append("</div>");

        if (examens == null || examens.isEmpty()) {
            sb.append("<div class=\"empty-state\"><i class=\"fas fa-file-alt\"></i><p>Aucun examen trouvé.</p></div>");
        } else {
            sb.append("<div class=\"table-wrapper\"><table>");
            sb.append("<thead><tr><th>Titre</th><th>Matière</th><th>Classe</th><th>Date</th><th>Heure</th><th>Salle</th><th>Coeff</th><th>Statut</th><th>Fichier</th><th>Actions</th></tr></thead><tbody>");
            for (Examen e : examens) {
                String fichierDisplay = "-";
                if (e.getFichierNom() != null && !e.getFichierNom().isBlank()) {
                    fichierDisplay = "<a href=\"/professeur/examens/download/" + e.getId() + "\" target=\"_blank\"><i class=\"fas fa-file\"></i> " + echapperHtml(e.getFichierNom()) + "</a>";
                }
                String statutBadge = getStatutBadge(e.getStatut());
                String heure = "";
                if (e.getHeureDebut() != null) {
                    heure = e.getHeureDebut().toString().substring(0, 5);
                    if (e.getHeureFin() != null) {
                        heure += " - " + e.getHeureFin().toString().substring(0, 5);
                    }
                }
                sb.append("<tr>");
                sb.append("<td><strong>").append(echapperHtml(e.getTitre())).append("</strong></td>");
                sb.append("<td>").append(echapperHtml(e.getNomMatiere() != null ? e.getNomMatiere() : e.getCodeCours())).append("</td>");
                sb.append("<td>").append(echapperHtml(e.getClasse())).append("</td>");
                sb.append("<td>").append(e.getDateExamen() != null ? e.getDateExamen().toString() : "").append("</td>");
                sb.append("<td>").append(heure).append("</td>");
                sb.append("<td>").append(echapperHtml(e.getSalle())).append("</td>");
                sb.append("<td>").append(e.getCoefficient()).append("</td>");
                sb.append("<td>").append(statutBadge).append("</td>");
                sb.append("<td>").append(fichierDisplay).append("</td>");
                sb.append("<td>");
                sb.append("<a href=\"/professeur/examens?action=edit&id=").append(e.getId()).append("\" class=\"btn btn-primary\"><i class=\"fas fa-edit\"></i></a> ");
                sb.append("<a href=\"/professeur/examens?action=delete&id=").append(e.getId()).append("\" class=\"btn btn-danger\" onclick=\"return confirm('Supprimer cet examen ?')\"><i class=\"fas fa-trash\"></i></a>");
                sb.append("</td></tr>");
            }
            sb.append("</tbody></table></div>");
        }

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    public static String renderForm(String professeurId,
                                    List<Professeur.Creneau> creneaux,
                                    List<AnneeAcademique> annees,
                                    List<Periode> periodes,
                                    List<Classe> classes,
                                    Examen examen,
                                    String institutionId,
                                    String msgType,
                                    String msg) {

        List<Professeur.Creneau> safeCreneaux = creneaux != null ? creneaux : Collections.emptyList();
        List<AnneeAcademique> safeAnnees = annees != null ? annees : Collections.emptyList();
        List<Periode> safePeriodes = periodes != null ? periodes : Collections.emptyList();
        List<Classe> safeClasses = classes != null ? classes : Collections.emptyList();

        boolean isEdit = examen != null && examen.getId() != null && !examen.getId().isBlank();
        String action = isEdit ? "modifier" : "ajouter";
        String title = isEdit ? "Modifier l'examen" : "Ajouter un examen";

        List<String> codesCours = safeCreneaux.stream()
                .map(Professeur.Creneau::getCodeCours)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomClasses = safeClasses.stream()
                .map(Classe::getNomClasse)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomPeriodes = safePeriodes.stream()
                .map(Periode::getPeriode)
                .distinct()
                .collect(Collectors.toList());
        List<String> nomAnnees = safeAnnees.stream()
                .map(AnneeAcademique::getAnneeAcademique)
                .distinct()
                .collect(Collectors.toList());

        String id = "";
        String codeCours = "";
        String classe = "";
        String periode = "";
        String annee = "";
        String titre = "";
        String description = "";
        String dateExamen = "";
        String heureDebut = "";
        String heureFin = "";
        String duree = "";
        String salle = "";
        String coefficient = "1.0";
        String noteMax = "20.0";
        String statut = "PREVU";
        String fichierNom = "";
        String fichierChemin = "";
        String fichierType = "";

        if (isEdit && examen != null) {
            id = String.valueOf(examen.getId());
            codeCours = examen.getCodeCours() != null ? examen.getCodeCours() : "";
            classe = examen.getClasse() != null ? examen.getClasse() : "";
            periode = examen.getPeriode() != null ? examen.getPeriode() : "";
            annee = examen.getAnneeAcademique() != null ? examen.getAnneeAcademique() : "";
            titre = examen.getTitre() != null ? examen.getTitre() : "";
            description = examen.getDescription() != null ? examen.getDescription() : "";
            
            if (examen.getDateExamen() != null) {
                dateExamen = new java.text.SimpleDateFormat("yyyy-MM-dd").format(examen.getDateExamen());
            }
            if (examen.getHeureDebut() != null) {
                String hDebutStr = examen.getHeureDebut().toString();
                heureDebut = hDebutStr.length() >= 5 ? hDebutStr.substring(0, 5) : hDebutStr;
            }
            if (examen.getHeureFin() != null) {
                String hFinStr = examen.getHeureFin().toString();
                heureFin = hFinStr.length() >= 5 ? hFinStr.substring(0, 5) : hFinStr;
            }
            
            duree = String.valueOf(examen.getDureeMinutes());
            salle = examen.getSalle() != null ? examen.getSalle() : "";
            coefficient = String.valueOf(examen.getCoefficient());
            noteMax = String.valueOf(examen.getNoteMaximale());
            
            if (examen.getStatut() != null) {
                statut = examen.getStatut();
            }
            
            fichierNom = examen.getFichierNom() != null ? examen.getFichierNom() : "";
            fichierChemin = examen.getFichierChemin() != null ? examen.getFichierChemin() : "";
            fichierType = examen.getFichierType() != null ? examen.getFichierType() : "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(title).append(" - Professeur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        sb.append(renderHeader(professeurId));

        if (msg != null && msgType != null) {
            sb.append("<div class=\"message ").append(msgType).append("\">");
            sb.append("<i class=\"fas fa-").append(msgType.equals("success") ? "check-circle" : "exclamation-triangle").append("\"></i> ");
            sb.append(msg);
            sb.append("</div>");
        }

        sb.append("<div class=\"form-container\">");
        sb.append("<h2>").append(title).append("</h2>");
        sb.append("<form method=\"POST\" action=\"/professeur/examens\" enctype=\"multipart/form-data\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"").append(action).append("\">");
        sb.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");

        // Champs côte-à-cote pour optimiser l'espace et éviter un formulaire trop long
        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Code cours</label><select name=\"codeCours\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String c : codesCours) {
            String selected = c.equals(codeCours) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(selected).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Classe</label><select name=\"classe\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String c : nomClasses) {
            String selected = c.equals(classe) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(selected).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String p : nomPeriodes) {
            String selected = p.equals(periode) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(selected).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Année académique</label><select name=\"annee_academique\" required>");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String a : nomAnnees) {
            String selected = a.equals(annee) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(selected).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\"><label>Titre</label><input type=\"text\" name=\"titre\" value=\"").append(echapperHtml(titre)).append("\" required></div>");

        sb.append("<div class=\"form-group\"><label>Description</label><textarea name=\"description\" rows=\"3\">").append(echapperHtml(description)).append("</textarea></div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Date de l'examen</label><input type=\"date\" name=\"date_examen\" value=\"").append(dateExamen).append("\" required></div>");
        sb.append("<div class=\"form-group\"><label>Heure début</label><input type=\"time\" name=\"heure_debut\" value=\"").append(heureDebut).append("\"></div>");
        sb.append("<div class=\"form-group\"><label>Heure fin</label><input type=\"time\" name=\"heure_fin\" value=\"").append(heureFin).append("\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Durée (minutes)</label><input type=\"number\" name=\"duree_minutes\" value=\"").append(duree).append("\" min=\"0\"></div>");
        sb.append("<div class=\"form-group\"><label>Salle</label><input type=\"text\" name=\"salle\" value=\"").append(echapperHtml(salle)).append("\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-row\">");
        sb.append("<div class=\"form-group\"><label>Coefficient</label><input type=\"number\" name=\"coefficient\" value=\"").append(coefficient).append("\" step=\"0.1\" min=\"0.1\"></div>");
        sb.append("<div class=\"form-group\"><label>Note maximale</label><input type=\"number\" name=\"note_maximale\" value=\"").append(noteMax).append("\" step=\"0.5\" min=\"0\"></div>");
        sb.append("</div>");

        sb.append("<div class=\"form-group\"><label>Statut</label><select name=\"statut\">");
        String[] statuts = {"PREVU", "TERMINE", "ANNULE"};
        for (String s : statuts) {
            String selected = s.equals(statut) ? "selected" : "";
            sb.append("<option value=\"").append(s).append("\" ").append(selected).append(">").append(s).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\">");
        sb.append("<label>Fichier joint (Word, Excel, PDF, etc.)</label>");
        sb.append("<input type=\"file\" name=\"fichier\" accept=\".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.jpg,.png\">");
        if (fichierNom != null && !fichierNom.isBlank()) {
            sb.append("<p style=\"font-size:12px;color:#64748b;margin-top:5px;\">Fichier actuel: <a href=\"/professeur/examens/download/").append(id).append("\" target=\"_blank\">").append(echapperHtml(fichierNom)).append("</a></p>");
            sb.append("<input type=\"hidden\" name=\"fichier_actuel\" value=\"").append(echapperHtml(fichierNom)).append("\">");
            sb.append("<input type=\"hidden\" name=\"fichier_chemin_actuel\" value=\"").append(echapperHtml(fichierChemin)).append("\">");
            sb.append("<input type=\"hidden\" name=\"fichier_type_actuel\" value=\"").append(echapperHtml(fichierType)).append("\">");
        }
        sb.append("</div>");

        sb.append("<div class=\"form-actions\">");
        sb.append("<a href=\"/professeur/examens\" class=\"btn btn-secondary\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("<button type=\"submit\" class=\"btn btn-success\"><i class=\"fas fa-save\"></i> Enregistrer</button>");
        sb.append("</div>");
        sb.append("</form>");
        sb.append("</div>");

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy</div>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Erreur</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Plus Jakarta Sans',sans-serif; }");
        sb.append("body { background:#f4f6f9; display:flex; justify-content:center; align-items:center; height:100vh; }");
        sb.append(".error-container { background:white; padding:40px; border-radius:16px; text-align:center; max-width:500px; }");
        sb.append(".error-container i { font-size:64px; color:#dc2626; margin-bottom:20px; }");
        sb.append(".error-container h1 { color:#1e293b; margin-bottom:10px; }");
        sb.append(".error-container p { color:#64748b; }");
        sb.append(".error-container .btn { display:inline-block; margin-top:20px; padding:10px 24px; background:#1e40af; color:white; text-decoration:none; border-radius:8px; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"error-container\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(echapperHtml(message)).append("</p>");
        sb.append("<a href=\"/professeur/examens\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== STYLES ====================
    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        body { font-family:'Plus Jakarta Sans',sans-serif; background:#f0f4f8; min-height:100vh; padding:20px; display:flex; justify-content:center; align-items:flex-start; }
        
        /* Élargissement dynamique maximal sur n'importe quel appareil */
        .container { width: 95%; max-width: 1600px; margin: 0 auto; }

        .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:20px 25px; border-radius:16px; display:flex; justify-content:space-between; align-items:center; margin-bottom:25px; }
        .header h1 { font-size:22px; font-weight:700; }
        .header h1 i { margin-right:10px; }
        .header-actions { display:flex; gap:15px; }
        .header-actions a { color:#94a3b8; text-decoration:none; font-size:14px; transition:0.2s; padding:4px 12px; border-radius:20px; }
        .header-actions a:hover { color:white; background:rgba(255,255,255,0.1); }
        .header-avatar { width:50px; height:50px; border-radius:50%; border:2px solid #3b82f6; object-fit:cover; }

        .filters { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e2e8f0; display:flex; gap:15px; flex-wrap:wrap; align-items:end; }
        .filters .form-group { flex:1; min-width:150px; }
        .filters label { display:block; font-weight:600; color:#475569; margin-bottom:4px; font-size:13px; }
        .filters select { width:100%; padding:8px 12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; background:white; }
        .filters .btn-filter { background:#1e40af; color:white; border:none; padding:8px 20px; border-radius:8px; cursor:pointer; font-weight:600; }

        .table-wrapper { background:white; border-radius:12px; padding:20px; overflow-x:auto; border:1px solid #e2e8f0; width: 100%; }
        table { width:100%; border-collapse:collapse; font-size:14px; }
        th, td { padding:10px 12px; text-align:left; border-bottom:1px solid #e2e8f0; }
        th { background:#f8fafc; font-weight:600; color:#475569; }
        .btn { padding:6px 14px; border:none; border-radius:6px; cursor:pointer; font-size:13px; font-weight:600; text-decoration:none; display:inline-flex; align-items:center; gap:6px; transition:0.2s; }
        .btn-primary { background:#1e40af; color:white; }
        .btn-primary:hover { background:#1e3a8a; }
        .btn-danger { background:#dc2626; color:white; }
        .btn-danger:hover { background:#b91c1c; }
        .btn-success { background:#16a34a; color:white; }
        .btn-success:hover { background:#15803d; }
        .btn-secondary { background:#94a3b8; color:white; }
        .btn-secondary:hover { background:#64748b; }

        .badge { padding:3px 12px; border-radius:12px; font-size:12px; font-weight:600; }
        .badge-prevu { background:#fef3c7; color:#d97706; }
        .badge-termine { background:#d1fae5; color:#16a34a; }
        .badge-annule { background:#fee2e2; color:#dc2626; }

        .form-container { background:white; border-radius:12px; padding:30px; border:1px solid #e2e8f0; width: 100%; margin:0 auto; }
        .form-container h2 { margin-bottom:20px; color:#1e293b; }
        .form-group { margin-bottom:15px; }
        .form-group label { display:block; font-weight:600; color:#475569; margin-bottom:5px; }
        .form-group input, .form-group select, .form-group textarea { width:100%; padding:10px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; }
        .form-row { display:flex; gap:15px; }
        .form-row .form-group { flex:1; }
        .form-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:20px; }

        .empty-state { text-align:center; padding:40px; background:white; border-radius:12px; border:1px solid #e2e8f0; width: 100%; }
        .empty-state i { font-size:48px; color:#94a3b8; margin-bottom:12px; }
        .empty-state p { color:#64748b; font-size:16px; }
        .footer { margin-top:30px; text-align:center; color:#94a3b8; font-size:13px; }
        .message { padding:12px 18px; border-radius:8px; margin-bottom:20px; width: 100%; }
        .message.success { background:#d1fae5; color:#16a34a; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#dc2626; border-left:4px solid #dc2626; }
        @media (max-width:768px) { .filters { flex-direction:column; } .form-row { flex-direction:column; } .container { width: 100%; padding: 10px; } }
        """;
    }

    private static String renderHeader(String professeurId) {
        System.out.println("Bienvenue Prof :"+professeurId);
        return "<div class=\"header\">" +
                "<div style=\"display:flex;align-items:center;gap:15px;\">" +
                "<img src=\"" + DEFAULT_AVATAR + "\" alt=\"Avatar\" class=\"header-avatar\">" +
                "<h1><i class=\"fas fa-file-alt\"></i> Gestion des Examens</h1>" +
                "</div>" +
                "<div class=\"header-actions\">" +
                "<a href=\"/dashboard/professeur\"><i class=\"fas fa-home\"></i> Dashboard</a>" +
                "<a href=\"/professeur/profil\"><i class=\"fas fa-user\"></i> Profil</a>" +
                "<a href=\"/logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>" +
                "</div>" +
                "</div>";
    }

    private static String renderFilters(List<String> codesCours, List<String> classes,
                                        List<String> periodes, List<String> annees,
                                        String codeCoursSel, String classeSel,
                                        String periodeSel, String anneeSel) {
        StringBuilder sb = new StringBuilder("<form method=\"GET\" action=\"/professeur/examens\" class=\"filters\">");
        sb.append("<div class=\"form-group\"><label>Année</label><select name=\"annee\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String a : annees) {
            String sel = a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(sel).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Période</label><select name=\"periode\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String p : periodes) {
            String sel = p.equals(periodeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(p)).append("\" ").append(sel).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Classe</label><select name=\"classe\">");
        sb.append("<option value=\"\">Toutes</option>");
        for (String c : classes) {
            String sel = c.equals(classeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(sel).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div class=\"form-group\"><label>Cours</label><select name=\"codeCours\">");
        sb.append("<option value=\"\">Tous</option>");
        for (String c : codesCours) {
            String sel = c.equals(codeCoursSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(c)).append("\" ").append(sel).append(">").append(echapperHtml(c)).append("</option>");
        }
        sb.append("</select></div>");

        sb.append("<div><button type=\"submit\" class=\"btn-filter\"><i class=\"fas fa-filter\"></i> Filtrer</button></div>");
        sb.append("</form>");
        return sb.toString();
    }

    private static String getStatutBadge(String statut) {
        if (statut == null) statut = "PREVU";
        String cls = switch (statut) {
            case "TERMINE" -> "badge-termine";
            case "ANNULE" -> "badge-annule";
            default -> "badge-prevu";
        };
        return "<span class=\"badge " + cls + "\">" + statut + "</span>";
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