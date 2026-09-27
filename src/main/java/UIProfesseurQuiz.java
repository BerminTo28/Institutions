import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class UIProfesseurQuiz {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    // ============================================================
    // 1. LISTE DES QUIZ DU PROFESSEUR
    // ============================================================
    public static String renderListe(String professeurId, List<Quiz> quizList,
                                      String msgType, String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(entete("Mes Quiz"));
        sb.append("<div class=\"container\">");

        // Message succès / erreur
        if (msg != null && !msg.isEmpty()) {
            String cls = "success".equals(msgType) ? "success" : "error";
            String icone = "success".equals(msgType) ? "check-circle" : "exclamation-triangle";
            sb.append("<div class=\"message ").append(cls).append("\">")
              .append("<i class=\"fas fa-").append(icone).append("\"></i> ")
              .append(esc(msg))
              .append("</div>");
        }

        // En-tête
        sb.append("<div class=\"page-header\">");
        sb.append("  <div>");
        sb.append("    <h2><i class=\"fas fa-clipboard-list\"></i> Mes Quiz</h2>");
        sb.append("    <p>Créez et gérez vos évaluations en ligne.</p>");
        sb.append("  </div>");
        sb.append("  <a href=\"/professeur/quiz?action=nouveau\" class=\"btn-create\">");
        sb.append("    <i class=\"fas fa-plus\"></i> Nouveau quiz");
        sb.append("  </a>");
        sb.append("</div>");

        // Liste ou vide
        if (quizList == null || quizList.isEmpty()) {
            sb.append("<div class=\"empty-state\">");
            sb.append("  <i class=\"fas fa-clipboard\"></i>");
            sb.append("  <h3>Aucun quiz créé</h3>");
            sb.append("  <p>Commencez par créer votre premier quiz pour vos étudiants.</p>");
            sb.append("  <a href=\"/professeur/quiz?action=nouveau\" class=\"btn-primary\">");
            sb.append("    <i class=\"fas fa-plus\"></i> Créer un quiz");
            sb.append("  </a>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"quiz-grid\">");
            for (Quiz q : quizList) {
                sb.append(renderCarteProfesseur(q));
            }
            sb.append("</div>");
        }

        sb.append("</div>");
        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // 2. FORMULAIRE (délègue à UIFormulaireQuiz)
    // ============================================================
    /**
     * ✅ Rendu du formulaire via UIFormulaireQuiz, avec l'en-tête
     *    généré dynamiquement par DocumentPrivateHeader / DocumentPublicHeader
     *    selon la visibilité du quiz.
     *
     * @param professeurId  identifiant du professeur
     * @param codesCours    liste des codes cours autorisés
     * @param classes       liste des classes autorisées
     * @param periodes      liste des périodes autorisées
     * @param annees        liste des années autorisées
     * @param quiz          null = création, non-null = modification
     * @param institution   institution courante (pour l'en-tête)
     * @param visibilite    "PRIVE" ou "PUBLIC"
     * @param msgType       "success" / "error" / null
     * @param msg           message éventuel
     */
    public static String renderFormulaire(String professeurId,
                                            List<String> codesCours,
                                            List<String> classes,
                                            List<String> periodes,
                                            List<String> annees,
                                            Quiz quiz,
                                            Institution institution,
                                            String visibilite,
                                            String msgType,
                                            String msg) {

        // ✅ Délégation complète à UIFormulaireQuiz
        return UIFormulaireQuiz.render(
                professeurId,
                codesCours,
                classes,
                periodes,
                annees,
                quiz,
                institution,
                visibilite,
                msgType,
                msg
        );
    }

    // ============================================================
    // 3. DÉTAIL D'UN QUIZ
    // ============================================================
    public static String renderDetail(Quiz quiz) {
        StringBuilder sb = new StringBuilder();
        sb.append(entete("Détail du quiz"));
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append("<div class=\"page-header\">");
        sb.append("  <div>");
        sb.append("    <h2><i class=\"fas fa-clipboard-check\"></i> ")
          .append(esc(quiz.getTitre())).append("</h2>");
        if (quiz.getDescription() != null && !quiz.getDescription().isBlank()) {
            sb.append("    <p>").append(esc(quiz.getDescription())).append("</p>");
        }
        sb.append("  </div>");
        sb.append("  <a href=\"/professeur/quiz\" class=\"btn-back\">");
        sb.append("    <i class=\"fas fa-arrow-left\"></i> Retour");
        sb.append("  </a>");
        sb.append("</div>");

        // Infos globales
        boolean expire = quiz.isExpire();
        boolean publie = "PUBLIE".equalsIgnoreCase(quiz.getStatut());

        sb.append("<div class=\"detail-info\">");
        sb.append("  <div class=\"info-item\"><i class=\"fas fa-star\"></i> ")
          .append(String.format("%.2f", quiz.getNoteTotale())).append(" pts</div>");
        sb.append("  <div class=\"info-item\"><i class=\"fas fa-clock\"></i> ")
          .append(quiz.getDureeMinutes()).append(" min</div>");
        sb.append("  <div class=\"info-item\"><i class=\"fas fa-hourglass-end\"></i> Expire le ")
          .append(formatDate(quiz.getDateExpiration())).append("</div>");

        // Badge visibilité
        sb.append("  <div class=\"info-item\">");
        if (publie) {
            sb.append("<span class=\"badge badge-published\">")
              .append("<i class=\"fas fa-globe\"></i> Public</span>");
        } else {
            sb.append("<span class=\"badge badge-draft\">")
              .append("<i class=\"fas fa-lock\"></i> Privé</span>");
        }
        sb.append("  </div>");

        // Badge expiration
        if (expire) {
            sb.append("  <div class=\"info-item\">")
              .append("<span class=\"badge badge-expired\">Expiré</span></div>");
        }
        sb.append("</div>");

        // Sections
        if (quiz.getSections() != null) {
            int sIdx = 1;
            for (QuizSection section : quiz.getSections()) {
                sb.append("<div class=\"section-detail\">");
                sb.append("  <div class=\"section-detail-header\">");
                sb.append("    <h3><span class=\"section-num\">").append(sIdx).append("</span> ")
                  .append(esc(section.getTitre())).append("</h3>");
                sb.append("    <span class=\"section-cote\">")
                  .append(String.format("%.1f", section.getCoteSection())).append(" pts</span>");
                sb.append("  </div>");

                if (section.getQuestions() != null) {
                    int qIdx = 1;
                    for (QuizQuestion q : section.getQuestions()) {
                        sb.append("<div class=\"question-detail\">");
                        sb.append("  <div class=\"question-detail-header\">");
                        sb.append("    <span class=\"q-num\">Q").append(sIdx).append(".")
                          .append(qIdx).append("</span>");
                        sb.append("    <span class=\"q-type\">")
                          .append(q.getType() == QuizQuestion.Type.MULTIPLE
                                  ? "Choix multiple" : "Choix unique").append("</span>");
                        sb.append("    <span class=\"q-cote\">")
                          .append(String.format("%.1f", q.getCote())).append(" pt</span>");
                        sb.append("  </div>");
                        sb.append("  <div class=\"q-enonce\">").append(esc(q.getEnonce())).append("</div>");

                        if (q.getOptions() != null) {
                            sb.append("<div class=\"q-options\">");
                            for (QuizOption opt : q.getOptions()) {
                                sb.append("<div class=\"q-option")
                                  .append(opt.isEstCorrecte() ? " correct" : "").append("\">");
                                sb.append("<i class=\"fas fa-")
                                  .append(opt.isEstCorrecte() ? "check-circle" : "circle")
                                  .append("\"></i> ");
                                sb.append(esc(opt.getTexte()));
                                sb.append("</div>");
                            }
                            sb.append("</div>");
                        }

                        sb.append("</div>");
                        qIdx++;
                    }
                }

                sb.append("</div>");
                sIdx++;
            }
        }

        // Actions
        sb.append("<div class=\"detail-actions\">");
        sb.append("  <a href=\"/professeur/quiz?action=editer&id=").append(esc(quiz.getId()))
          .append("\" class=\"btn-primary\">")
          .append("<i class=\"fas fa-pen\"></i> Modifier</a>");

        // Publier / Dépublier
        if (publie) {
            sb.append("  <a href=\"/professeur/quiz?action=depublier&id=").append(esc(quiz.getId()))
              .append("\" class=\"btn-secondary\">")
              .append("<i class=\"fas fa-eye-slash\"></i> Dépublier</a>");
        } else {
            sb.append("  <a href=\"/professeur/quiz?action=publier&id=").append(esc(quiz.getId()))
              .append("\" class=\"btn-success\">")
              .append("<i class=\"fas fa-rocket\"></i> Publier</a>");
        }

        sb.append("  <a href=\"/professeur/quiz?action=delete&id=").append(esc(quiz.getId()))
          .append("\" class=\"btn-danger\" onclick=\"return confirm('Supprimer ce quiz ?');\">")
          .append("<i class=\"fas fa-trash\"></i> Supprimer</a>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // 4. PAGE D'ERREUR
    // ============================================================
    public static String renderError(String message) {
        return "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\">"
             + "<title>Erreur</title>"
             + "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">"
             + "<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">"
             + "<style>"
             + "*{margin:0;padding:0;box-sizing:border-box;}"
             + "body{font-family:'Plus Jakarta Sans',sans-serif;background:linear-gradient(135deg,#f0f4f8,#d9e2ec);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;}"
             + ".error-box{background:white;border-radius:20px;padding:50px 40px;text-align:center;max-width:500px;box-shadow:0 20px 40px -20px rgba(0,0,0,0.15);}"
             + ".error-box i{font-size:64px;color:#ef4444;margin-bottom:20px;}"
             + ".error-box h1{color:#1e293b;margin-bottom:12px;font-size:22px;}"
             + ".error-box p{color:#64748b;margin-bottom:25px;line-height:1.6;}"
             + ".error-box a{display:inline-flex;align-items:center;gap:8px;padding:12px 24px;background:#3b82f6;color:white;text-decoration:none;border-radius:10px;font-weight:600;}"
             + "</style></head><body>"
             + "<div class=\"error-box\">"
             + "<i class=\"fas fa-exclamation-triangle\"></i>"
             + "<h1>Erreur</h1>"
             + "<p>" + esc(message) + "</p>"
             + "<a href=\"/professeur/quiz\"><i class=\"fas fa-arrow-left\"></i> Retour aux quiz</a>"
             + "</div></body></html>";
    }

    // ============================================================
    // COMPOSANTS INTERNES — LISTE
    // ============================================================

    private static String renderCarteProfesseur(Quiz q) {
        boolean expire = q.isExpire();
        boolean publie = "PUBLIE".equalsIgnoreCase(q.getStatut());
        int nbSections = q.getSections() != null ? q.getSections().size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"quiz-card").append(expire ? " expired" : "").append("\">");

        // Header avec icône et badge visibilité
        sb.append("  <div class=\"quiz-card-header\">");
        sb.append("    <div class=\"quiz-card-icon\"><i class=\"fas fa-clipboard-list\"></i></div>");
        if (publie) {
            sb.append("    <span class=\"badge badge-published\">")
              .append("<i class=\"fas fa-globe\"></i> Public</span>");
        } else {
            sb.append("    <span class=\"badge badge-draft\">")
              .append("<i class=\"fas fa-lock\"></i> Privé</span>");
        }
        sb.append("  </div>");

        // Titre
        sb.append("  <h3>").append(esc(q.getTitre())).append("</h3>");

        // Meta
        sb.append("  <div class=\"quiz-card-meta\">");
        if (q.getCodeCours() != null && !q.getCodeCours().isBlank()) {
            sb.append("<span><i class=\"fas fa-book\"></i> ")
              .append(esc(q.getCodeCours())).append("</span>");
        }
        if (q.getClasse() != null && !q.getClasse().isBlank()) {
            sb.append("<span><i class=\"fas fa-users\"></i> ")
              .append(esc(q.getClasse())).append("</span>");
        }
        sb.append("  </div>");

        // Stats
        sb.append("  <div class=\"quiz-card-stats\">");
        sb.append("    <div class=\"stat\"><i class=\"fas fa-layer-group\"></i>")
          .append("<span><strong>").append(nbSections)
          .append("</strong> sections</span></div>");
        sb.append("    <div class=\"stat\"><i class=\"fas fa-star\"></i>")
          .append("<span><strong>").append(String.format("%.1f", q.getNoteTotale()))
          .append("</strong> pts</span></div>");
        sb.append("    <div class=\"stat\"><i class=\"fas fa-clock\"></i>")
          .append("<span><strong>").append(q.getDureeMinutes())
          .append("</strong> min</span></div>");
        sb.append("  </div>");

        // Deadline
        sb.append("  <div class=\"quiz-card-deadline\">")
          .append("<i class=\"fas fa-hourglass-end\"></i> ")
          .append(expire ? "Expiré le " : "Expire le ")
          .append(formatDate(q.getDateExpiration()))
          .append("  </div>");

        // Actions
        sb.append("  <div class=\"quiz-card-actions\">");
        sb.append("    <a href=\"/professeur/quiz?id=").append(esc(q.getId()))
          .append("\" class=\"btn-icon\" title=\"Voir\">")
          .append("<i class=\"fas fa-eye\"></i></a>");
        sb.append("    <a href=\"/professeur/quiz?action=editer&id=").append(esc(q.getId()))
          .append("\" class=\"btn-icon\" title=\"Modifier\">")
          .append("<i class=\"fas fa-pen\"></i></a>");

        // Publier / Dépublier
        if (publie) {
            sb.append("    <a href=\"/professeur/quiz?action=depublier&id=").append(esc(q.getId()))
              .append("\" class=\"btn-icon\" title=\"Dépublier\">")
              .append("<i class=\"fas fa-eye-slash\"></i></a>");
        } else {
            sb.append("    <a href=\"/professeur/quiz?action=publier&id=").append(esc(q.getId()))
              .append("\" class=\"btn-icon btn-icon-publish\" title=\"Publier\">")
              .append("<i class=\"fas fa-rocket\"></i></a>");
        }

        sb.append("    <a href=\"/professeur/quiz?action=delete&id=").append(esc(q.getId()))
          .append("\" class=\"btn-icon btn-icon-delete\" title=\"Supprimer\" ")
          .append("onclick=\"return confirm('Supprimer ce quiz ?');\">")
          .append("<i class=\"fas fa-trash\"></i></a>");
        sb.append("  </div>");

        sb.append("</div>");
        return sb.toString();
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String entete(String titre) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"fr\"><head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(esc(titre)).append("</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>").append(styles()).append("</style>");
        sb.append("</head><body>");
        sb.append("<div class=\"main-wrapper\">");
        sb.append(renderHeader());
        return sb.toString();
    }

    private static String pied() {
        return "</div>"
             + "<div class=\"footer\">© " + java.time.Year.now().getValue()
             + " M-TECH Academy - Tous droits réservés</div>"
             + "</body></html>";
    }

    private static String renderHeader() {
        return """
        <div class="app-header">
            <a href="/professeur/dashboard" class="logo">
                <i class="fas fa-chalkboard-user"></i>
                <span>Espace Professeur</span>
            </a>
            <nav class="nav-links">
                <a href="/professeur/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/professeur/quiz" class="active"><i class="fas fa-clipboard-list"></i> Quiz</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </nav>
        </div>
        """;
    }

    private static String styles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        html, body { width:100%; min-height:100vh; background:linear-gradient(135deg,#f0f4f8,#d9e2ec); font-family:'Plus Jakarta Sans',sans-serif; color:#0f172a; }
        body { padding:0; display:flex; flex-direction:column; }
        .main-wrapper { min-height:100vh; display:flex; flex-direction:column; }

        .app-header { background:linear-gradient(135deg,#1e293b,#0f172a); padding:14px 40px; display:flex; justify-content:space-between; align-items:center; color:white; }
        .app-header .logo { display:flex; align-items:center; gap:10px; color:white; text-decoration:none; font-weight:700; font-size:16px; }
        .app-header .logo i { color:#3b82f6; font-size:22px; }
        .nav-links { display:flex; gap:8px; }
        .nav-links a { color:#94a3b8; text-decoration:none; padding:8px 14px; border-radius:8px; font-size:13px; font-weight:500; display:flex; align-items:center; gap:6px; }
        .nav-links a:hover, .nav-links a.active { background:rgba(255,255,255,0.1); color:white; }

        .container { max-width:1200px; margin:0 auto; padding:30px 40px; width:100%; flex:1; }

        .page-header { display:flex; justify-content:space-between; align-items:center; gap:20px; margin-bottom:30px; flex-wrap:wrap; }
        .page-header h2 { font-size:26px; font-weight:800; display:flex; align-items:center; gap:12px; }
        .page-header h2 i { color:#3b82f6; }
        .page-header p { color:#64748b; margin-top:6px; font-size:14px; }

        .message { padding:14px 18px; border-radius:10px; margin-bottom:20px; display:flex; align-items:center; gap:10px; font-size:14px; }
        .message.success { background:#d1fae5; color:#065f46; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#991b1b; border-left:4px solid #dc2626; }

        .btn-create, .btn-primary, .btn-secondary, .btn-success, .btn-danger, .btn-back {
            display:inline-flex; align-items:center; gap:8px; padding:10px 20px; border-radius:10px;
            font-weight:600; font-size:14px; text-decoration:none; border:none; cursor:pointer;
            transition:0.2s; font-family:inherit;
        }
        .btn-create, .btn-primary { background:#3b82f6; color:white; }
        .btn-create:hover, .btn-primary:hover { background:#2563eb; }
        .btn-secondary { background:#f1f5f9; color:#334155; }
        .btn-secondary:hover { background:#e2e8f0; }
        .btn-success { background:#16a34a; color:white; }
        .btn-success:hover { background:#15803d; }
        .btn-danger { background:#fee2e2; color:#991b1b; }
        .btn-danger:hover { background:#fecaca; }
        .btn-back { background:#f1f5f9; color:#334155; }
        .btn-back:hover { background:#e2e8f0; }

        .quiz-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:20px; }
        .quiz-card { background:white; border-radius:16px; padding:24px; border:1px solid #e2e8f0; display:flex; flex-direction:column; gap:14px; transition:0.2s; }
        .quiz-card:hover { transform:translateY(-3px); box-shadow:0 12px 28px -8px rgba(59,130,246,0.18); }
        .quiz-card.expired { opacity:0.7; }
        .quiz-card-header { display:flex; justify-content:space-between; align-items:flex-start; }
        .quiz-card-icon { width:48px; height:48px; border-radius:12px; background:linear-gradient(135deg,#3b82f6,#2563eb); color:white; display:flex; align-items:center; justify-content:center; font-size:20px; }
        .badge { font-size:11px; padding:4px 10px; border-radius:20px; font-weight:600; text-transform:uppercase; letter-spacing:0.4px; display:inline-flex; align-items:center; gap:4px; }
        .badge-active, .badge-published { background:#d1fae5; color:#065f46; }
        .badge-expired { background:#fee2e2; color:#991b1b; }
        .badge-draft { background:#fef3c7; color:#92400e; }
        .quiz-card h3 { font-size:17px; font-weight:700; line-height:1.3; }
        .quiz-card-meta { display:flex; gap:12px; flex-wrap:wrap; font-size:12px; color:#64748b; }
        .quiz-card-meta span { display:inline-flex; align-items:center; gap:5px; }
        .quiz-card-stats { display:flex; gap:8px; padding:12px 0; border-top:1px solid #f1f5f9; border-bottom:1px solid #f1f5f9; }
        .quiz-card-stats .stat { flex:1; text-align:center; font-size:12px; color:#64748b; display:flex; flex-direction:column; gap:4px; }
        .quiz-card-stats .stat i { color:#3b82f6; font-size:14px; }
        .quiz-card-stats .stat strong { color:#0f172a; font-size:14px; display:block; }
        .quiz-card-deadline { font-size:12px; color:#f59e0b; display:flex; align-items:center; gap:6px; }
        .quiz-card-actions { margin-top:auto; display:flex; gap:8px; }
        .btn-icon { width:36px; height:36px; border-radius:8px; background:#f1f5f9; color:#475569; display:flex; align-items:center; justify-content:center; text-decoration:none; transition:0.2s; font-size:13px; }
        .btn-icon:hover { background:#e2e8f0; color:#0f172a; }
        .btn-icon-publish { background:#dbeafe; color:#1e40af; }
        .btn-icon-publish:hover { background:#bfdbfe; }
        .btn-icon-delete { background:#fee2e2; color:#991b1b; }
        .btn-icon-delete:hover { background:#fecaca; }

        .empty-state { text-align:center; padding:80px 20px; background:white; border-radius:16px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:64px; color:#cbd5e1; margin-bottom:16px; }
        .empty-state h3 { font-size:18px; color:#334155; margin-bottom:8px; }
        .empty-state p { color:#64748b; margin-bottom:20px; }

        /* --- Détail --- */
        .detail-info { display:flex; gap:20px; flex-wrap:wrap; padding:20px 24px; background:white; border-radius:14px; border:1px solid #e2e8f0; margin-bottom:24px; }
        .info-item { display:flex; align-items:center; gap:8px; font-size:14px; color:#334155; }
        .info-item i { color:#3b82f6; }
        .section-detail { background:white; border-radius:14px; padding:24px; margin-bottom:16px; border:1px solid #e2e8f0; }
        .section-detail-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; padding-bottom:12px; border-bottom:1px solid #f1f5f9; }
        .section-detail-header h3 { font-size:16px; font-weight:700; display:flex; align-items:center; gap:10px; }
        .section-num { background:#3b82f6; color:white; width:26px; height:26px; border-radius:50%; display:inline-flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; }
        .section-cote { background:#eff6ff; color:#1e40af; padding:6px 14px; border-radius:20px; font-size:13px; font-weight:700; }
        .question-detail { padding:16px 0; border-bottom:1px dashed #e2e8f0; }
        .question-detail:last-child { border-bottom:none; }
        .question-detail-header { display:flex; gap:10px; align-items:center; margin-bottom:10px; flex-wrap:wrap; }
        .q-num { background:#f1f5f9; color:#475569; padding:3px 10px; border-radius:6px; font-size:12px; font-weight:700; }
        .q-type { background:#ede9fe; color:#6d28d9; padding:3px 10px; border-radius:6px; font-size:11px; font-weight:600; }
        .q-cote { margin-left:auto; background:#fef3c7; color:#92400e; padding:3px 10px; border-radius:6px; font-size:12px; font-weight:700; }
        .q-enonce { font-size:14px; margin-bottom:12px; color:#0f172a; line-height:1.5; }
        .q-options { display:flex; flex-direction:column; gap:6px; }
        .q-option { padding:8px 14px; border-radius:8px; background:#f8fafc; font-size:13px; display:flex; align-items:center; gap:8px; color:#334155; }
        .q-option.correct { background:#d1fae5; color:#065f46; font-weight:600; }
        .q-option.correct i { color:#16a34a; }
        .detail-actions { display:flex; gap:12px; margin-top:24px; flex-wrap:wrap; }

        .footer { text-align:center; color:#94a3b8; font-size:13px; padding:30px 20px 20px; }

        @media (max-width:768px) {
            .app-header { padding:12px 20px; flex-direction:column; gap:10px; }
            .container { padding:20px; }
            .page-header { flex-direction:column; align-items:stretch; }
            .detail-actions { flex-direction:column; }
        }
        """;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String formatDate(java.util.Date date) {
        if (date == null) return "—";
        LocalDateTime ldt = date.toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        return DATE_FMT.format(ldt);
    }
}