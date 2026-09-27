import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class UIEtudiantQuiz {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    // ============================================================
    // 1. LISTE DES QUIZ DISPONIBLES
    // ============================================================
    public static String renderListe(String etudiantId, List<Quiz> quizList,
                                      String msgType, String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(entete("Mes Quiz"));

        // Message succès / erreur
        if (msg != null && !msg.isEmpty()) {
            String cls = "success".equals(msgType) ? "success" : "error";
            sb.append("<div class=\"message ").append(cls).append("\">")
              .append("<i class=\"fas fa-")
              .append("success".equals(msgType) ? "check-circle" : "exclamation-triangle")
              .append("\"></i> ").append(esc(msg)).append("</div>");
        }

        sb.append("<div class=\"container\">");

        // --- En-tête de page ---
        sb.append("<div class=\"page-header\">")
          .append("<h2><i class=\"fas fa-clipboard-question\"></i> Mes Quiz</h2>")
          .append("<p>Retrouvez ici tous les quiz accessibles pour votre classe.</p>")
          .append("</div>");

        // --- Liste ---
        if (quizList == null || quizList.isEmpty()) {
            sb.append("<div class=\"empty-state\">")
              .append("<i class=\"fas fa-inbox\"></i>")
              .append("<h3>Aucun quiz disponible</h3>")
              .append("<p>Il n'y a aucun quiz accessible pour le moment.</p>")
              .append("</div>");
        } else {
            sb.append("<div class=\"quiz-grid\">");
            for (Quiz q : quizList) {
                sb.append(renderCarteQuiz(q, null));
            }
            sb.append("</div>");
        }

        sb.append("</div>"); // .container
        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // 2. PASSATION D'UN QUIZ
    // ============================================================
    public static String renderQuiz(Quiz quiz, QuizTentative tentative) {
        StringBuilder sb = new StringBuilder();
        sb.append(entete("Quiz : " + esc(quiz.getTitre())));

        // Calcul du temps restant (pour le timer JS)
        long secondesRestantes = calculerSecondesRestantes(quiz.getDateExpiration());

        sb.append("<div class=\"container\">");

        // --- En-tête du quiz ---
        sb.append("<div class=\"quiz-header\">");
        sb.append("  <div class=\"quiz-header-info\">");
        sb.append("    <h1><i class=\"fas fa-clipboard-question\"></i> ")
          .append(esc(quiz.getTitre())).append("</h1>");
        if (quiz.getDescription() != null && !quiz.getDescription().isBlank()) {
            sb.append("<p class=\"quiz-desc\">").append(esc(quiz.getDescription())).append("</p>");
        }
        sb.append("  </div>");
        sb.append("  <div class=\"quiz-header-meta\">");
        sb.append("    <div class=\"meta-item\">")
          .append("<i class=\"fas fa-star\"></i> ")
          .append(String.format("%.2f", quiz.getNoteTotale())).append(" pts")
          .append("</div>");
        sb.append("    <div class=\"meta-item\">")
          .append("<i class=\"fas fa-clock\"></i> ")
          .append(quiz.getDureeMinutes()).append(" min")
          .append("</div>");
        sb.append("    <div class=\"meta-item\">")
          .append("<i class=\"fas fa-hourglass-end\"></i> Expire le ")
          .append(formatDate(quiz.getDateExpiration()))
          .append("</div>");
        sb.append("  </div>");
        sb.append("</div>");

        // --- Bandeau de compte à rebours ---
        sb.append("<div class=\"countdown-bar\" id=\"countdownBar\">");
        sb.append("  <i class=\"fas fa-hourglass-half\"></i>");
        sb.append("  <span>Temps restant : </span>");
        sb.append("  <strong id=\"countdownValue\">").append(formatDuree(secondesRestantes)).append("</strong>");
        sb.append("</div>");

        // --- Formulaire ---
        sb.append("<form method=\"POST\" action=\"/etudiant/quiz\" id=\"quizForm\">");
        sb.append("  <input type=\"hidden\" name=\"action\" value=\"soumettre\">");
        sb.append("  <input type=\"hidden\" name=\"quizId\" value=\"").append(esc(quiz.getId())).append("\">");

        // --- Sections ---
        int sectionIndex = 0;
        for (QuizSection section : quiz.getSections()) {
            sb.append(renderSection(section, sectionIndex));
            sectionIndex++;
        }

        // --- Bouton de soumission ---
        sb.append("<div class=\"submit-bar\">");
        sb.append("  <p class=\"submit-warning\">")
          .append("<i class=\"fas fa-exclamation-triangle\"></i> ")
          .append("Une fois soumis, vous ne pourrez plus modifier vos réponses.")
          .append("</p>");
        sb.append("  <button type=\"submit\" class=\"btn-submit\" id=\"btnSubmit\">")
          .append("<i class=\"fas fa-paper-plane\"></i> Soumettre mes réponses")
          .append("</button>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>"); // .container

        // --- Script : timer + confirmation ---
        sb.append("<script>");
        sb.append("const expirationTs = ").append(
                quiz.getDateExpiration() != null
                        ? quiz.getDateExpiration().getTime()
                        : System.currentTimeMillis())
                .append(";");
        sb.append("const countdownEl = document.getElementById('countdownValue');");
        sb.append("const countdownBar = document.getElementById('countdownBar');");
        sb.append("function updateCountdown() {");
        sb.append("  const now = Date.now();");
        sb.append("  const diff = Math.max(0, Math.floor((expirationTs - now) / 1000));");
        sb.append("  if (diff <= 0) {");
        sb.append("    countdownEl.textContent = 'Quiz expiré';");
        sb.append("    countdownBar.classList.add('expired');");
        sb.append("    document.getElementById('btnSubmit').disabled = true;");
        sb.append("    return;");
        sb.append("  }");
        sb.append("  const h = Math.floor(diff / 3600);");
        sb.append("  const m = Math.floor((diff % 3600) / 60);");
        sb.append("  const s = diff % 60;");
        sb.append("  const pad = (v) => String(v).padStart(2, '0');");
        sb.append("  countdownEl.textContent = (h > 0 ? h + 'h ' : '') + pad(m) + 'm ' + pad(s) + 's';");
        sb.append("  if (diff < 300) countdownBar.classList.add('urgent');");
        sb.append("}");
        sb.append("updateCountdown();");
        sb.append("setInterval(updateCountdown, 1000);");

        // Confirmation avant soumission
        sb.append("document.getElementById('quizForm').addEventListener('submit', function(e) {");
        sb.append("  if (!confirm('Voulez-vous vraiment soumettre vos réponses ?')) {");
        sb.append("    e.preventDefault();");
        sb.append("  }");
        sb.append("});");
        sb.append("</script>");

        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // 3. RÉSULTAT APRÈS SOUMISSION
    // ============================================================
    public static String renderResultat(Quiz quiz, double cote, QuizTentative tentative) {
        StringBuilder sb = new StringBuilder();
        sb.append(entete("Résultat du quiz"));

        double noteTotal = quiz.getNoteTotale();
        double pourcentage = noteTotal > 0 ? (cote / noteTotal) * 100 : 0;
        boolean reussi = pourcentage >= 50;
        String classe = reussi ? "success" : "fail";
        String icone = reussi ? "fa-trophy" : "fa-face-frown";

        sb.append("<div class=\"container\">");

        sb.append("<div class=\"resultat-card ").append(classe).append("\">");
        sb.append("  <i class=\"fas ").append(icone).append(" resultat-icon\"></i>");
        sb.append("  <h1>").append(reussi ? "Félicitations !" : "Peut mieux faire").append("</h1>");
        sb.append("  <p class=\"resultat-quiz-titre\">").append(esc(quiz.getTitre())).append("</p>");

        sb.append("  <div class=\"resultat-cote\">");
        sb.append("    <span class=\"cote-value\">").append(String.format("%.2f", cote)).append("</span>");
        sb.append("    <span class=\"cote-total\">/ ").append(String.format("%.2f", noteTotal)).append("</span>");
        sb.append("  </div>");

        sb.append("  <div class=\"resultat-pourcentage\">");
        sb.append("    <div class=\"progress-bar\">");
        sb.append("      <div class=\"progress-fill\" style=\"width:")
          .append(String.format("%.0f", Math.min(100, pourcentage))).append("%\"></div>");
        sb.append("    </div>");
        sb.append("    <span>").append(String.format("%.1f", pourcentage)).append("%</span>");
        sb.append("  </div>");

        if (tentative != null && tentative.getDateFin() != null) {
            sb.append("  <p class=\"resultat-date\">");
            sb.append("    <i class=\"fas fa-calendar-check\"></i> Soumis le ")
              .append(formatDate(tentative.getDateFin()))
              .append("</p>");
        }

        sb.append("  <div class=\"resultat-actions\">");
        sb.append("    <a href=\"/etudiant/quiz\" class=\"btn-primary\">");
        sb.append("      <i class=\"fas fa-arrow-left\"></i> Retour à la liste");
        sb.append("    </a>");
        sb.append("  </div>");
        sb.append("</div>");

        sb.append("</div>"); // .container
        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // 4. PAGE D'ERREUR
    // ============================================================
    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\"><head><meta charset=\"UTF-8\">");
        sb.append("<title>Erreur</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box;}");
        sb.append("body{font-family:'Plus Jakarta Sans',sans-serif;background:linear-gradient(135deg,#f0f4f8,#d9e2ec);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;}");
        sb.append(".error-box{background:white;border-radius:20px;padding:50px 40px;text-align:center;max-width:500px;box-shadow:0 20px 40px -20px rgba(0,0,0,0.15);}");
        sb.append(".error-box i{font-size:64px;color:#ef4444;margin-bottom:20px;}");
        sb.append(".error-box h1{color:#1e293b;margin-bottom:12px;font-size:22px;}");
        sb.append(".error-box p{color:#64748b;margin-bottom:25px;line-height:1.6;}");
        sb.append(".error-box a{display:inline-flex;align-items:center;gap:8px;padding:12px 24px;background:#3b82f6;color:white;text-decoration:none;border-radius:10px;font-weight:600;}");
        sb.append(".error-box a:hover{background:#2563eb;}");
        sb.append("</style></head><body>");
        sb.append("<div class=\"error-box\">");
        sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
        sb.append("<h1>Erreur</h1>");
        sb.append("<p>").append(esc(message)).append("</p>");
        sb.append("<a href=\"/etudiant/quiz\"><i class=\"fas fa-arrow-left\"></i> Retour aux quiz</a>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ============================================================
    // COMPOSANTS INTERNES
    // ============================================================

    private static String renderCarteQuiz(Quiz quiz, QuizTentative tentative) {
        boolean expire = quiz.isExpire();
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"quiz-card").append(expire ? " quiz-card-expired" : "").append("\">");
        sb.append("  <div class=\"quiz-card-header\">");
        sb.append("    <div class=\"quiz-card-icon\">")
          .append("<i class=\"fas fa-file-circle-question\"></i>")
          .append("</div>");
        if (expire) {
            sb.append("<span class=\"badge badge-expired\">Expiré</span>");
        } else {
            sb.append("<span class=\"badge badge-active\">Disponible</span>");
        }
        sb.append("  </div>");

        sb.append("  <h3 class=\"quiz-card-title\">").append(esc(quiz.getTitre())).append("</h3>");

        if (quiz.getDescription() != null && !quiz.getDescription().isBlank()) {
            sb.append("  <p class=\"quiz-card-desc\">").append(esc(quiz.getDescription())).append("</p>");
        }

        sb.append("  <div class=\"quiz-card-meta\">");
        if (quiz.getCodeCours() != null) {
            sb.append("    <span><i class=\"fas fa-book\"></i> ")
              .append(esc(quiz.getCodeCours())).append("</span>");
        }
        if (quiz.getPeriode() != null) {
            sb.append("    <span><i class=\"fas fa-calendar\"></i> ")
              .append(esc(quiz.getPeriode())).append("</span>");
        }
        sb.append("  </div>");

        sb.append("  <div class=\"quiz-card-stats\">");
        sb.append("    <div class=\"stat\">");
        sb.append("      <i class=\"fas fa-list-ol\"></i>");
        sb.append("      <span><strong>")
          .append(quiz.getSections() != null ? quiz.getSections().size() : 0)
          .append("</strong> section(s)</span>");
        sb.append("    </div>");
        sb.append("    <div class=\"stat\">");
        sb.append("      <i class=\"fas fa-star\"></i>");
        sb.append("      <span><strong>").append(String.format("%.1f", quiz.getNoteTotale()))
          .append("</strong> points</span>");
        sb.append("    </div>");
        sb.append("    <div class=\"stat\">");
        sb.append("      <i class=\"fas fa-clock\"></i>");
        sb.append("      <span><strong>").append(quiz.getDureeMinutes())
          .append("</strong> min</span>");
        sb.append("    </div>");
        sb.append("  </div>");

        sb.append("  <div class=\"quiz-card-deadline\">");
        sb.append("    <i class=\"fas fa-hourglass-end\"></i> ");
        if (expire) {
            sb.append("Expiré le ").append(formatDate(quiz.getDateExpiration()));
        } else {
            sb.append("Expire le ").append(formatDate(quiz.getDateExpiration()));
        }
        sb.append("  </div>");

        sb.append("  <div class=\"quiz-card-actions\">");
        if (tentative != null && "TERMINEE".equalsIgnoreCase(tentative.getStatut())) {
            sb.append("<a href=\"/etudiant/quiz?action=resultat&id=")
              .append(esc(quiz.getId())).append("\" class=\"btn-secondary\">")
              .append("<i class=\"fas fa-eye\"></i> Voir le résultat</a>");
        } else if (expire) {
            sb.append("<button class=\"btn-disabled\" disabled>")
              .append("<i class=\"fas fa-lock\"></i> Expiré</button>");
        } else {
            sb.append("<a href=\"/etudiant/quiz?id=").append(esc(quiz.getId()))
              .append("\" class=\"btn-primary\">")
              .append("<i class=\"fas fa-play\"></i> Commencer</a>");
        }
        sb.append("  </div>");

        sb.append("</div>");
        return sb.toString();
    }

    private static String renderSection(QuizSection section, int sectionIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section-card\" data-section=\"").append(sectionIndex).append("\">");

        // En-tête de section
        sb.append("  <div class=\"section-header\">");
        sb.append("    <div class=\"section-number\">").append(sectionIndex + 1).append("</div>");
        sb.append("    <div>");
        sb.append("      <h2>").append(esc(section.getTitre())).append("</h2>");
        if (section.getDescription() != null && !section.getDescription().isBlank()) {
            sb.append("      <p>").append(esc(section.getDescription())).append("</p>");
        }
        sb.append("    </div>");
        sb.append("    <div class=\"section-score\">");
        sb.append("      <i class=\"fas fa-star\"></i> ");
        sb.append(String.format("%.1f", section.getCoteSection())).append(" pts");
        sb.append("    </div>");
        sb.append("  </div>");

        // Questions
        int qIndex = 0;
        for (QuizQuestion q : section.getQuestions()) {
            sb.append(renderQuestion(q, sectionIndex, qIndex));
            qIndex++;
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String renderQuestion(QuizQuestion q, int sectionIndex, int questionIndex) {
        boolean multiple = q.getType() == QuizQuestion.Type.MULTIPLE;
        String inputType = multiple ? "checkbox" : "radio";
        String typeLabel = multiple ? "Choix multiple" : "Choix unique";
        String typeIcone = multiple ? "fa-list-check" : "fa-circle-dot";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"question-card\" data-q=\"").append(q.getId()).append("\">");

        // En-tête
        sb.append("  <div class=\"question-header\">");
        sb.append("    <div class=\"question-num\">Q")
          .append(sectionIndex + 1).append(".").append(questionIndex + 1).append("</div>");
        sb.append("    <div class=\"question-type\">");
        sb.append("      <i class=\"fas ").append(typeIcone).append("\"></i> ").append(typeLabel);
        sb.append("    </div>");
        sb.append("    <div class=\"question-cote\">");
        sb.append("      <i class=\"fas fa-star\"></i> ");
        sb.append(String.format("%.2f", q.getCote())).append(" pt");
        if (q.getCote() > 1) sb.append("s");
        sb.append("    </div>");
        sb.append("  </div>");

        // Énoncé
        sb.append("  <div class=\"question-enonce\">").append(esc(q.getEnonce())).append("</div>");

        // Options
        sb.append("  <div class=\"question-options\">");
        int optIndex = 0;
        for (QuizOption opt : q.getOptions()) {
            String optId = "q_" + q.getId() + "_opt_" + optIndex;
            sb.append("    <label class=\"option-item\" for=\"").append(optId).append("\">");
            sb.append("      <input type=\"").append(inputType)
              .append("\" name=\"q_").append(esc(q.getId())).append("\"")
              .append(" id=\"").append(optId).append("\"")
              .append(" value=\"").append(esc(opt.getId())).append("\">");
            sb.append("      <span class=\"option-check\"></span>");
            sb.append("      <span class=\"option-text\">").append(esc(opt.getTexte())).append("</span>");
            sb.append("    </label>");
            optIndex++;
        }
        sb.append("  </div>");

        sb.append("</div>");
        return sb.toString();
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String entete(String titre) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\"><head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(esc(titre)).append("</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(styles());
        sb.append("</style></head><body>");
        sb.append("<div class=\"main-wrapper\">");
        sb.append(renderHeader());
        return sb.toString();
    }

    private static String pied() {
        return "</div>"  // .main-wrapper
             + "<div class=\"footer\">© " + java.time.Year.now().getValue()
             + " M-TECH Academy - Tous droits réservés</div>"
             + "</body></html>";
    }

    private static String renderHeader() {
        return """
        <div class="app-header">
            <a href="/etudiant/dashboard" class="logo">
                <i class="fas fa-graduation-cap"></i>
                <span>Espace Étudiant</span>
            </a>
            <nav class="nav-links">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/quiz" class="active"><i class="fas fa-clipboard-question"></i> Quiz</a>
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

        /* --- Header --- */
        .app-header { background:linear-gradient(135deg,#1e293b,#0f172a); padding:14px 40px; display:flex; justify-content:space-between; align-items:center; color:white; box-shadow:0 2px 12px rgba(0,0,0,0.05); }
        .app-header .logo { display:flex; align-items:center; gap:10px; color:white; text-decoration:none; font-weight:700; font-size:16px; }
        .app-header .logo i { color:#3b82f6; font-size:22px; }
        .nav-links { display:flex; gap:8px; }
        .nav-links a { color:#94a3b8; text-decoration:none; padding:8px 14px; border-radius:8px; font-size:13px; font-weight:500; transition:0.2s; display:flex; align-items:center; gap:6px; }
        .nav-links a:hover, .nav-links a.active { background:rgba(255,255,255,0.1); color:white; }

        /* --- Container --- */
        .container { max-width:1200px; margin:0 auto; padding:30px 40px; width:100%; flex:1; }

        .page-header { margin-bottom:30px; }
        .page-header h2 { font-size:26px; font-weight:800; color:#0f172a; display:flex; align-items:center; gap:12px; }
        .page-header h2 i { color:#3b82f6; }
        .page-header p { color:#64748b; margin-top:6px; font-size:14px; }

        /* --- Messages --- */
        .message { padding:14px 18px; border-radius:10px; margin-bottom:20px; display:flex; align-items:center; gap:10px; font-size:14px; }
        .message.success { background:#d1fae5; color:#065f46; border-left:4px solid #16a34a; }
        .message.error { background:#fee2e2; color:#991b1b; border-left:4px solid #dc2626; }

        /* --- Grille des quiz --- */
        .quiz-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:20px; }
        .quiz-card { background:white; border-radius:16px; padding:24px; border:1px solid #e2e8f0; transition:0.25s; display:flex; flex-direction:column; gap:14px; }
        .quiz-card:hover { transform:translateY(-3px); box-shadow:0 12px 28px -8px rgba(59,130,246,0.18); border-color:#93c5fd; }
        .quiz-card-expired { opacity:0.7; }
        .quiz-card-header { display:flex; justify-content:space-between; align-items:flex-start; }
        .quiz-card-icon { width:48px; height:48px; border-radius:12px; background:linear-gradient(135deg,#3b82f6,#2563eb); color:white; display:flex; align-items:center; justify-content:center; font-size:20px; }
        .badge { font-size:11px; padding:4px 10px; border-radius:20px; font-weight:600; text-transform:uppercase; letter-spacing:0.4px; }
        .badge-active { background:#d1fae5; color:#065f46; }
        .badge-expired { background:#fee2e2; color:#991b1b; }
        .quiz-card-title { font-size:17px; font-weight:700; color:#0f172a; line-height:1.3; }
        .quiz-card-desc { color:#64748b; font-size:13px; line-height:1.5; }
        .quiz-card-meta { display:flex; gap:12px; flex-wrap:wrap; font-size:12px; color:#64748b; }
        .quiz-card-meta span { display:inline-flex; align-items:center; gap:5px; }
        .quiz-card-meta i { color:#94a3b8; }
        .quiz-card-stats { display:flex; gap:8px; padding:12px 0; border-top:1px solid #f1f5f9; border-bottom:1px solid #f1f5f9; }
        .quiz-card-stats .stat { flex:1; text-align:center; font-size:12px; color:#64748b; display:flex; flex-direction:column; gap:4px; }
        .quiz-card-stats .stat i { color:#3b82f6; font-size:14px; }
        .quiz-card-stats .stat strong { color:#0f172a; font-size:14px; display:block; }
        .quiz-card-deadline { font-size:12px; color:#f59e0b; display:flex; align-items:center; gap:6px; }
        .quiz-card-actions { margin-top:auto; }
        .quiz-card-actions a, .quiz-card-actions button { display:flex; align-items:center; justify-content:center; gap:8px; padding:12px 20px; border-radius:10px; font-weight:600; font-size:14px; text-decoration:none; border:none; cursor:pointer; transition:0.2s; width:100%; }
        .btn-primary { background:#3b82f6; color:white; }
        .btn-primary:hover { background:#2563eb; }
        .btn-secondary { background:#f1f5f9; color:#334155; }
        .btn-secondary:hover { background:#e2e8f0; }
        .btn-disabled { background:#f1f5f9; color:#94a3b8; cursor:not-allowed; }

        /* --- Empty state --- */
        .empty-state { text-align:center; padding:80px 20px; background:white; border-radius:16px; border:1px solid #e2e8f0; }
        .empty-state i { font-size:64px; color:#cbd5e1; margin-bottom:16px; }
        .empty-state h3 { font-size:18px; color:#334155; margin-bottom:8px; }
        .empty-state p { color:#64748b; }

        /* --- Quiz header (passation) --- */
        .quiz-header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; border-radius:16px; padding:28px 32px; margin-bottom:20px; display:flex; justify-content:space-between; gap:20px; flex-wrap:wrap; align-items:center; }
        .quiz-header-info h1 { font-size:22px; font-weight:800; display:flex; align-items:center; gap:10px; margin-bottom:6px; }
        .quiz-header-info h1 i { color:#3b82f6; }
        .quiz-desc { color:#94a3b8; font-size:14px; }
        .quiz-header-meta { display:flex; gap:18px; flex-wrap:wrap; }
        .meta-item { display:flex; align-items:center; gap:6px; font-size:13px; color:#cbd5e1; }
        .meta-item i { color:#3b82f6; }

        /* --- Countdown --- */
        .countdown-bar { background:#fef3c7; color:#92400e; padding:14px 20px; border-radius:12px; margin-bottom:24px; display:flex; align-items:center; gap:10px; font-size:14px; border-left:4px solid #f59e0b; transition:0.3s; }
        .countdown-bar.urgent { background:#fee2e2; color:#991b1b; border-left-color:#dc2626; animation:pulse 1s infinite; }
        .countdown-bar.expired { background:#f1f5f9; color:#64748b; border-left-color:#94a3b8; }
        .countdown-bar strong { font-family:'Courier New',monospace; font-size:16px; }
        @keyframes pulse { 0%,100%{opacity:1;} 50%{opacity:0.7;} }

        /* --- Section --- */
        .section-card { background:white; border-radius:16px; padding:28px 32px; margin-bottom:24px; border:1px solid #e2e8f0; }
        .section-header { display:flex; gap:16px; align-items:flex-start; padding-bottom:18px; border-bottom:2px solid #f1f5f9; margin-bottom:22px; }
        .section-number { width:36px; height:36px; border-radius:50%; background:#3b82f6; color:white; display:flex; align-items:center; justify-content:center; font-weight:800; font-size:15px; flex-shrink:0; }
        .section-header h2 { font-size:18px; font-weight:700; color:#0f172a; }
        .section-header p { font-size:13px; color:#64748b; margin-top:4px; }
        .section-score { margin-left:auto; background:#eff6ff; color:#1e40af; padding:8px 14px; border-radius:10px; font-size:13px; font-weight:600; display:flex; align-items:center; gap:6px; }

        /* --- Question --- */
        .question-card { padding:22px 0; border-bottom:1px dashed #e2e8f0; }
        .question-card:last-child { border-bottom:none; padding-bottom:0; }
        .question-header { display:flex; gap:12px; align-items:center; margin-bottom:14px; flex-wrap:wrap; }
        .question-num { background:#f1f5f9; color:#475569; padding:4px 10px; border-radius:6px; font-size:12px; font-weight:700; letter-spacing:0.3px; }
        .question-type { background:#e0e7ff; color:#4338ca; padding:4px 10px; border-radius:6px; font-size:11px; font-weight:600; display:flex; align-items:center; gap:4px; }
        .question-cote { margin-left:auto; background:#fef3c7; color:#92400e; padding:4px 10px; border-radius:6px; font-size:12px; font-weight:700; display:flex; align-items:center; gap:4px; }
        .question-enonce { font-size:15px; color:#0f172a; line-height:1.6; margin-bottom:16px; font-weight:500; }

        /* --- Options --- */
        .question-options { display:flex; flex-direction:column; gap:10px; }
        .option-item { display:flex; align-items:center; gap:12px; padding:14px 18px; border:2px solid #e2e8f0; border-radius:10px; cursor:pointer; transition:0.2s; background:white; }
        .option-item:hover { border-color:#93c5fd; background:#f8fafc; }
        .option-item input { position:absolute; opacity:0; pointer-events:none; }
        .option-check { width:20px; height:20px; border:2px solid #cbd5e1; border-radius:50%; flex-shrink:0; position:relative; transition:0.2s; }
        input[type="checkbox"] + .option-check { border-radius:6px; }
        .option-item input:checked ~ .option-check { background:#3b82f6; border-color:#3b82f6; }
        .option-item input:checked ~ .option-check::after { content:''; position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:white; font-size:12px; font-weight:bold; }
        input[type="radio"]:checked ~ .option-check::after { content:'●'; font-size:14px; }
        input[type="checkbox"]:checked ~ .option-check::after { content:'✓'; }
        .option-item input:checked ~ .option-text { color:#1e40af; font-weight:600; }
        .option-item:has(input:checked) { border-color:#3b82f6; background:#eff6ff; }
        .option-text { flex:1; font-size:14px; color:#334155; line-height:1.5; }

        /* --- Submit bar --- */
        .submit-bar { background:white; border-radius:16px; padding:24px 32px; border:1px solid #e2e8f0; display:flex; justify-content:space-between; align-items:center; gap:20px; flex-wrap:wrap; margin-top:10px; position:sticky; bottom:20px; box-shadow:0 -4px 20px -8px rgba(0,0,0,0.08); }
        .submit-warning { color:#f59e0b; font-size:13px; display:flex; align-items:center; gap:8px; }
        .btn-submit { background:linear-gradient(135deg,#16a34a,#15803d); color:white; border:none; padding:14px 28px; border-radius:10px; font-size:14px; font-weight:700; cursor:pointer; display:flex; align-items:center; gap:8px; transition:0.2s; }
        .btn-submit:hover { transform:translateY(-1px); box-shadow:0 8px 20px -4px rgba(22,163,74,0.4); }
        .btn-submit:disabled { background:#94a3b8; cursor:not-allowed; transform:none; box-shadow:none; }

        /* --- Résultat --- */
        .resultat-card { background:white; border-radius:24px; padding:60px 40px; text-align:center; max-width:600px; margin:40px auto; box-shadow:0 20px 40px -20px rgba(0,0,0,0.15); }
        .resultat-card.success { border-top:6px solid #16a34a; }
        .resultat-card.fail { border-top:6px solid #dc2626; }
        .resultat-icon { font-size:72px; margin-bottom:20px; }
        .resultat-card.success .resultat-icon { color:#16a34a; }
        .resultat-card.fail .resultat-icon { color:#dc2626; }
        .resultat-card h1 { font-size:26px; font-weight:800; margin-bottom:8px; color:#0f172a; }
        .resultat-quiz-titre { color:#64748b; font-size:14px; margin-bottom:30px; }
        .resultat-cote { display:flex; align-items:baseline; justify-content:center; gap:8px; margin-bottom:24px; }
        .cote-value { font-size:64px; font-weight:900; line-height:1; }
        .resultat-card.success .cote-value { color:#16a34a; }
        .resultat-card.fail .cote-value { color:#dc2626; }
        .cote-total { font-size:22px; color:#94a3b8; font-weight:600; }
        .resultat-pourcentage { max-width:300px; margin:0 auto 24px; display:flex; align-items:center; gap:12px; }
        .progress-bar { flex:1; height:10px; background:#f1f5f9; border-radius:5px; overflow:hidden; }
        .progress-fill { height:100%; border-radius:5px; transition:width 0.6s ease; }
        .resultat-card.success .progress-fill { background:linear-gradient(90deg,#16a34a,#22c55e); }
        .resultat-card.fail .progress-fill { background:linear-gradient(90deg,#dc2626,#ef4444); }
        .resultat-pourcentage span { font-size:14px; font-weight:700; color:#475569; }
        .resultat-date { color:#64748b; font-size:13px; margin-bottom:24px; display:inline-flex; align-items:center; gap:6px; }
        .resultat-actions { padding-top:20px; border-top:1px solid #f1f5f9; }
        .resultat-actions .btn-primary { display:inline-flex; padding:12px 28px; border-radius:10px; background:#3b82f6; color:white; text-decoration:none; font-weight:600; font-size:14px; align-items:center; gap:8px; transition:0.2s; }
        .resultat-actions .btn-primary:hover { background:#2563eb; }

        /* --- Footer --- */
        .footer { text-align:center; color:#94a3b8; font-size:13px; padding:30px 20px 20px; }

        /* --- Responsive --- */
        @media (max-width:768px) {
            .app-header { padding:12px 20px; flex-direction:column; gap:10px; }
            .container { padding:20px; }
            .quiz-header { padding:20px; }
            .section-card { padding:20px; }
            .submit-bar { flex-direction:column; align-items:stretch; }
            .btn-submit { justify-content:center; }
            .resultat-card { padding:40px 24px; }
            .cote-value { font-size:48px; }
        }
        """;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
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
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return DATE_FMT.format(ldt);
    }

    private static long calculerSecondesRestantes(java.util.Date expiration) {
        if (expiration == null) return 0;
        long diff = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0, diff);
    }

    private static String formatDuree(long secondes) {
        long h = secondes / 3600;
        long m = (secondes % 3600) / 60;
        long s = secondes % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }
}