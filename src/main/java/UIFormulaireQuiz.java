import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

public class UIFormulaireQuiz {

    private static final float HEADER_WIDTH  = 900f;
    private static final float HEADER_HEIGHT = 260f;

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String render(String professeurId,
                                 List<String> codesCours,
                                 List<String> classes,
                                 List<String> periodes,
                                 List<String> annees,
                                 Quiz quiz,
                                 Institution institution,
                                 String visibilite,
                                 String msgType,
                                 String msg) {

        // ✅ ① Extraction préalable — élimine tous les warnings null pointer
        final boolean modification = quiz != null
                && quiz.getId() != null
                && !quiz.getId().isBlank();

        // Valeurs sûres extraites UNE SEULE FOIS
       final String quizId = (quiz != null && quiz.getId() != null) ? quiz.getId() : null;
        final String quizTitre         = (quiz != null && quiz.getTitre() != null)
                                            ? quiz.getTitre() : "";
        final String quizDescription   = (quiz != null && quiz.getDescription() != null)
                                            ? quiz.getDescription() : "";
        final String quizCodeCours     = (quiz != null && quiz.getCodeCours() != null)
                                            ? quiz.getCodeCours() : "";
        final String quizClasse        = (quiz != null && quiz.getClasse() != null)
                                            ? quiz.getClasse() : "";
        final String quizPeriode       = (quiz != null && quiz.getPeriode() != null)
                                            ? quiz.getPeriode() : "";
        final String quizAnnee         = (quiz != null && quiz.getAnneeAcademique() != null)
                                            ? quiz.getAnneeAcademique() : "";
        final Date   quizDateDebut     = (quiz != null) ? quiz.getDateDebut() : null;
        final Date   quizDateExpiration = (quiz != null) ? quiz.getDateExpiration() : null;
        final int    quizDureeMinutes  = (quiz != null) ? quiz.getDureeMinutes() : 30;
        final List<QuizSection> quizSections = (quiz != null) ? quiz.getSections() : null;

        // Visibilité
        final String vis = (visibilite != null && !visibilite.isBlank())
                ? visibilite.toUpperCase() : "PRIVE";
        final boolean publique = "PUBLIC".equals(vis);

        // En-tête et titre
        final String headerBase64 = genererEnteteBase64(institution, publique);
        final String titrePage = modification ? "Modifier le quiz" : "Nouveau quiz";

        StringBuilder sb = new StringBuilder();
        sb.append(entete(titrePage, headerBase64, publique));
        sb.append("<div class=\"container\">");

        // Message
        if (msg != null && !msg.isEmpty()) {
            String cls = "success".equals(msgType) ? "success" : "error";
            String icone = "success".equals(msgType) ? "check-circle" : "exclamation-triangle";
            sb.append("<div class=\"message ").append(cls).append("\">")
              .append("<i class=\"fas fa-").append(icone).append("\"></i> ")
              .append(esc(msg))
              .append("</div>");
        }

        // En-tête de page
        sb.append("<div class=\"page-header\">");
        sb.append("  <div>");
        sb.append("    <h2><i class=\"fas fa-")
          .append(modification ? "pen-to-square" : "plus-circle").append("\"></i> ")
          .append(titrePage).append("</h2>");
        sb.append("    <p>Configurez les sections, questions et réponses du quiz.</p>");
        sb.append("  </div>");
        sb.append("  <a href=\"/professeur/quiz\" class=\"btn-back\">");
        sb.append("    <i class=\"fas fa-arrow-left\"></i> Retour");
        sb.append("  </a>");
        sb.append("</div>");

        // Formulaire
        sb.append("<form method=\"POST\" action=\"/professeur/quiz\" id=\"quizForm\">");
        sb.append("  <input type=\"hidden\" name=\"action\" value=\"enregistrer\">");
        if (quizId != null) {
            sb.append("  <input type=\"hidden\" name=\"quizId\" value=\"")
              .append(esc(quizId)).append("\">");
        }

        // ============================
        // Bloc 1 : Informations générales
        // ============================
        sb.append("<div class=\"form-section\">");
        sb.append("  <div class=\"form-section-header\">");
        sb.append("    <div class=\"form-section-number\">1</div>");
        sb.append("    <h3><i class=\"fas fa-info-circle\"></i> Informations générales</h3>");
        sb.append("  </div>");

        sb.append("  <div class=\"form-grid\">");

        sb.append("    <div class=\"form-group span-2\">");
        sb.append("      <label for=\"titre\">Titre du quiz <span class=\"required\">*</span></label>");
        sb.append("      <input type=\"text\" id=\"titre\" name=\"titre\" required ")
          .append("value=\"").append(esc(quizTitre)).append("\" ")
          .append("placeholder=\"Ex: Évaluation - Chapitre 1\">");
        sb.append("    </div>");

        sb.append("    <div class=\"form-group span-2\">");
        sb.append("      <label for=\"description\">Description (optionnelle)</label>");
        sb.append("      <textarea id=\"description\" name=\"description\" rows=\"2\" ")
          .append("placeholder=\"Description du quiz...\">")
          .append(esc(quizDescription)).append("</textarea>");
        sb.append("    </div>");

        // Code cours
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"codeCours\">Code cours <span class=\"required\">*</span></label>");
        sb.append("      <select id=\"codeCours\" name=\"codeCours\" required>");
        sb.append("        <option value=\"\">-- Sélectionner --</option>");
        if (codesCours != null) {
            for (String c : codesCours) {
                String sel = c.equals(quizCodeCours) ? "selected" : "";
                sb.append("        <option value=\"").append(esc(c)).append("\" ")
                  .append(sel).append(">").append(esc(c)).append("</option>");
            }
        }
        sb.append("      </select>");
        sb.append("    </div>");

        // Classe
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"classe\">Classe <span class=\"required\">*</span></label>");
        sb.append("      <select id=\"classe\" name=\"classe\" required>");
        sb.append("        <option value=\"\">-- Sélectionner --</option>");
        if (classes != null) {
            for (String c : classes) {
                String sel = c.equals(quizClasse) ? "selected" : "";
                sb.append("        <option value=\"").append(esc(c)).append("\" ")
                  .append(sel).append(">").append(esc(c)).append("</option>");
            }
        }
        sb.append("      </select>");
        sb.append("    </div>");

        // Période
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"periode\">Période <span class=\"required\">*</span></label>");
        sb.append("      <select id=\"periode\" name=\"periode\" required>");
        sb.append("        <option value=\"\">-- Sélectionner --</option>");
        if (periodes != null) {
            for (String p : periodes) {
                String sel = p.equals(quizPeriode) ? "selected" : "";
                sb.append("        <option value=\"").append(esc(p)).append("\" ")
                  .append(sel).append(">").append(esc(p)).append("</option>");
            }
        }
        sb.append("      </select>");
        sb.append("    </div>");

        // Année
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"annee_academique\">Année académique <span class=\"required\">*</span></label>");
        sb.append("      <select id=\"annee_academique\" name=\"annee_academique\" required>");
        sb.append("        <option value=\"\">-- Sélectionner --</option>");
        if (annees != null) {
            for (String a : annees) {
                String sel = a.equals(quizAnnee) ? "selected" : "";
                sb.append("        <option value=\"").append(esc(a)).append("\" ")
                  .append(sel).append(">").append(esc(a)).append("</option>");
            }
        }
        sb.append("      </select>");
        sb.append("    </div>");

        // Date début
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"date_debut\">Date de début (optionnelle)</label>");
        sb.append("      <input type=\"datetime-local\" id=\"date_debut\" name=\"date_debut\" ")
          .append("value=\"").append(formatDateInput(quizDateDebut)).append("\">");
        sb.append("    </div>");

        // Date expiration
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"date_expiration\">Date d'expiration <span class=\"required\">*</span></label>");
        sb.append("      <input type=\"datetime-local\" id=\"date_expiration\" ")
          .append("name=\"date_expiration\" required ")
          .append("value=\"").append(formatDateInput(quizDateExpiration)).append("\">");
        sb.append("    </div>");

        // Durée
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label for=\"duree_minutes\">Durée (minutes)</label>");
        sb.append("      <input type=\"number\" id=\"duree_minutes\" name=\"duree_minutes\" ")
          .append("min=\"1\" value=\"").append(quizDureeMinutes).append("\">");
        sb.append("    </div>");

        // Visibilité
        sb.append("    <div class=\"form-group span-2\">");
        sb.append("      <label>Visibilité du quiz <span class=\"required\">*</span></label>");
        sb.append("      <div class=\"radio-group\">");

        sb.append("        <label class=\"radio-card")
          .append(!publique ? " selected" : "").append("\">");
        sb.append("          <input type=\"radio\" name=\"visibilite\" value=\"PRIVE\" ")
          .append(!publique ? "checked" : "").append(" onchange=\"changerVisibilite(this)\">");
        sb.append("          <i class=\"fas fa-lock\"></i>");
        sb.append("          <span class=\"radio-title\">Privé</span>");
        sb.append("          <span class=\"radio-desc\">Brouillon, visible uniquement par vous</span>");
        sb.append("        </label>");

        sb.append("        <label class=\"radio-card")
          .append(publique ? " selected" : "").append("\">");
        sb.append("          <input type=\"radio\" name=\"visibilite\" value=\"PUBLIC\" ")
          .append(publique ? "checked" : "").append(" onchange=\"changerVisibilite(this)\">");
        sb.append("          <i class=\"fas fa-globe\"></i>");
        sb.append("          <span class=\"radio-title\">Public</span>");
        sb.append("          <span class=\"radio-desc\">Publié, accessible aux étudiants</span>");
        sb.append("        </label>");

        sb.append("      </div>");
        sb.append("    </div>");

        sb.append("  </div>");
        sb.append("</div>");

        // ============================
        // Bloc 2 : Sections / Questions / Options
        // ============================
        sb.append("<div class=\"form-section\">");
        sb.append("  <div class=\"form-section-header\">");
        sb.append("    <div class=\"form-section-number\">2</div>");
        sb.append("    <h3><i class=\"fas fa-layer-group\"></i> Sections & Questions</h3>");
        sb.append("  </div>");

        sb.append("  <div id=\"sectionsContainer\">");

        if (modification && quizSections != null && !quizSections.isEmpty()) {
            int sIdx = 0;
            for (QuizSection section : quizSections) {
                sb.append(renderSectionForm(section, sIdx));
                sIdx++;
            }
        }

        sb.append("  </div>");

        sb.append("  <button type=\"button\" class=\"btn-add-section\" onclick=\"ajouterSection()\">");
        sb.append("    <i class=\"fas fa-plus-circle\"></i> Ajouter une section");
        sb.append("  </button>");

        sb.append("</div>");

        // Barre d'actions
        sb.append("<div class=\"submit-bar\">");
        sb.append("  <a href=\"/professeur/quiz\" class=\"btn-cancel\">Annuler</a>");
        sb.append("  <button type=\"submit\" class=\"btn-submit\">");
        sb.append("    <i class=\"fas fa-save\"></i> ")
          .append(modification ? "Enregistrer les modifications" : "Créer le quiz");
        sb.append("  </button>");
        sb.append("</div>");

        sb.append("</form>");
        sb.append("</div>");

        sb.append(renderScripts());
        sb.append(pied());
        return sb.toString();
    }

    // ============================================================
    // GÉNÉRATION DE L'EN-TÊTE
    // ============================================================
    private static String genererEnteteBase64(Institution institution, boolean publique) {
        if (institution == null) return null;

        int width  = (int) HEADER_WIDTH;
        int height = (int) HEADER_HEIGHT;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            if (publique) {
                DocumentPublicHeader.draw(g2, institution,
                        HEADER_WIDTH, HEADER_HEIGHT, null, null, new Date());
            } else {
                DocumentPrivateHeader.draw(g2, institution,
                        HEADER_WIDTH, HEADER_HEIGHT, new Date());
            }

        } catch (Exception e) {
            System.err.println("❌ UIFormulaireQuiz: erreur génération en-tête — "
                    + e.getMessage());
        } finally {
            g2.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            System.err.println("❌ UIFormulaireQuiz: erreur encodage base64 — "
                    + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // SECTIONS
    // ============================================================
    private static String renderSectionForm(QuizSection section, int sectionIndex) {
        // ✅ Extraction préalable — plus de warnings
        final String sectionTitre = (section != null && section.getTitre() != null)
                ? section.getTitre() : "";
        final String sectionDesc  = (section != null && section.getDescription() != null)
                ? section.getDescription() : "";
        final int nbQuestions = (section != null && section.getQuestions() != null)
                ? section.getQuestions().size() : 0;
        final List<QuizQuestion> questions = (section != null) ? section.getQuestions() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section-form\" data-section-index=\"")
          .append(sectionIndex).append("\">");

        // En-tête centré
        sb.append("  <div class=\"section-form-header\">");
        sb.append("    <span class=\"section-badge\">")
          .append("<i class=\"fas fa-layer-group\"></i> Section ")
          .append(sectionIndex + 1).append("</span>");
        sb.append("    <h4 class=\"section-title\">")
          .append(sectionTitre.isEmpty() ? "Nouvelle section" : esc(sectionTitre))
          .append("</h4>");
        sb.append("    <button type=\"button\" class=\"btn-remove\" onclick=\"supprimerSection(this)\">");
        sb.append("      <i class=\"fas fa-trash\"></i> <span>Supprimer</span>");
        sb.append("    </button>");
        sb.append("  </div>");

        // Champs
        sb.append("  <div class=\"section-fields\">");
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label>Titre de la section <span class=\"required\">*</span></label>");
        sb.append("      <input type=\"text\" name=\"section_titre_").append(sectionIndex)
          .append("\" required value=\"").append(esc(sectionTitre)).append("\" ")
          .append("placeholder=\"Ex: Algèbre\">");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label>Description (optionnelle)</label>");
        sb.append("      <input type=\"text\" name=\"section_desc_").append(sectionIndex)
          .append("\" value=\"").append(esc(sectionDesc)).append("\" ")
          .append("placeholder=\"Ex: Chapitre sur les équations\">");
        sb.append("    </div>");
        sb.append("  </div>");

        sb.append("  <input type=\"hidden\" name=\"section_nbq_").append(sectionIndex)
          .append("\" value=\"").append(nbQuestions).append("\" ")
          .append("id=\"section_nbq_").append(sectionIndex).append("\">");

        // Questions
        sb.append("  <div class=\"questions-container\" id=\"questions_")
          .append(sectionIndex).append("\">");

        if (questions != null) {
            int qIdx = 0;
            for (QuizQuestion q : questions) {
                sb.append(renderQuestionForm(q, sectionIndex, qIdx));
                qIdx++;
            }
        }

        sb.append("  </div>");

        sb.append("  <button type=\"button\" class=\"btn-add-question\" ")
          .append("onclick=\"ajouterQuestion(").append(sectionIndex).append(")\">");
        sb.append("    <i class=\"fas fa-plus\"></i> Ajouter une question");
        sb.append("  </button>");

        sb.append("</div>");
        return sb.toString();
    }

    // ============================================================
    // QUESTIONS
    // ============================================================
    private static String renderQuestionForm(QuizQuestion q, int sectionIndex, int questionIndex) {
        // ✅ Extraction préalable — plus de warnings
        final boolean multiple = (q != null) && (q.getType() == QuizQuestion.Type.MULTIPLE);
        final String  prefix   = "q_" + sectionIndex + "_" + questionIndex + "_";
        final List<QuizOption> options = (q != null) ? q.getOptions() : null;
        final int nbOptions    = (options != null) ? options.size() : 2;
        final String enonce    = (q != null && q.getEnonce() != null) ? q.getEnonce() : "";
        final double cote      = (q != null) ? q.getCote() : 1.0;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"question-form\" data-q-index=\"")
          .append(questionIndex).append("\">");
        sb.append("  <div class=\"question-form-header\">");
        sb.append("    <h5><i class=\"fas fa-question-circle\"></i> Question <span class=\"q-index\">")
          .append(questionIndex + 1).append("</span></h5>");
        sb.append("    <button type=\"button\" class=\"btn-remove-small\" onclick=\"supprimerQuestion(this)\">");
        sb.append("      <i class=\"fas fa-times\"></i></button>");
        sb.append("  </div>");

        sb.append("  <div class=\"form-group\">");
        sb.append("    <label>Énoncé *</label>");
        sb.append("    <textarea name=\"").append(prefix).append("enonce\" rows=\"2\" required ")
          .append("placeholder=\"Saisissez la question...\">")
          .append(esc(enonce)).append("</textarea>");
        sb.append("  </div>");

        sb.append("  <div class=\"form-row\">");
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label>Type *</label>");
        sb.append("      <select name=\"").append(prefix).append("type\" required>");
        sb.append("        <option value=\"SIMPLE\"").append(!multiple ? " selected" : "")
          .append(">Choix unique</option>");
        sb.append("        <option value=\"MULTIPLE\"").append(multiple ? " selected" : "")
          .append(">Choix multiple</option>");
        sb.append("      </select>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group\">");
        sb.append("      <label>Cote *</label>");
        sb.append("      <input type=\"number\" name=\"").append(prefix).append("cote\" ")
          .append("step=\"0.5\" min=\"0.5\" required value=\"")
          .append(cote).append("\">");
        sb.append("    </div>");
        sb.append("  </div>");

        sb.append("  <input type=\"hidden\" name=\"").append(prefix).append("nbopt\" value=\"")
          .append(nbOptions).append("\" id=\"").append(prefix).append("nbopt\">");
        sb.append("  <div class=\"options-container\" id=\"options_")
          .append(prefix.replace("_", "")).append("\">");

        if (options != null) {
            int oIdx = 0;
            for (QuizOption opt : options) {
                sb.append(renderOptionForm(opt, prefix, oIdx));
                oIdx++;
            }
        } else {
            for (int oIdx = 0; oIdx < 2; oIdx++) {
                sb.append(renderOptionForm(null, prefix, oIdx));
            }
        }

        sb.append("  </div>");

        sb.append("  <button type=\"button\" class=\"btn-add-option\" ")
          .append("onclick=\"ajouterOption('").append(prefix).append("')\">");
        sb.append("    <i class=\"fas fa-plus\"></i> Ajouter une option");
        sb.append("  </button>");

        sb.append("</div>");
        return sb.toString();
    }

    // ============================================================
    // OPTIONS
    // ============================================================
    private static String renderOptionForm(QuizOption opt, String prefix, int optIndex) {
        // ✅ Extraction préalable — plus de warnings
        final String texteOpt = (opt != null && opt.getTexte() != null) ? opt.getTexte() : "";
        final boolean correcte = (opt != null) && opt.isEstCorrecte();

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"option-form\">");
        sb.append("  <input type=\"text\" name=\"").append(prefix).append("opt_")
          .append(optIndex).append("_texte\" placeholder=\"Texte de l'option\" required value=\"")
          .append(esc(texteOpt)).append("\">");
        sb.append("  <label class=\"checkbox-correct\">");
        sb.append("    <input type=\"checkbox\" name=\"").append(prefix).append("opt_")
          .append(optIndex).append("_correct\" ")
          .append(correcte ? "checked" : "").append(">");
        sb.append("    <span>Bonne réponse</span>");
        sb.append("  </label>");
        sb.append("  <button type=\"button\" class=\"btn-remove-option\" ")
          .append("onclick=\"supprimerOption(this)\">")
          .append("<i class=\"fas fa-times\"></i></button>");
        sb.append("</div>");
        return sb.toString();
    }

    // ============================================================
    // SCRIPTS DYNAMIQUES (inchangés)
    // ============================================================
    private static String renderScripts() {
        return """
        <script>
        let sectionCounter = document.querySelectorAll('.section-form').length;

        function changerVisibilite(radio) {
            document.querySelectorAll('.radio-card').forEach(card => {
                card.classList.remove('selected');
            });
            radio.closest('.radio-card').classList.add('selected');
        }

        function ajouterSection() {
            const container = document.getElementById('sectionsContainer');
            const div = document.createElement('div');
            div.innerHTML = getSectionTemplate(sectionCounter);
            container.appendChild(div.firstElementChild);
            sectionCounter++;
        }

        function supprimerSection(btn) {
            if (document.querySelectorAll('.section-form').length <= 1) {
                alert('Le quiz doit avoir au moins une section.');
                return;
            }
            if (confirm('Supprimer cette section ?')) {
                btn.closest('.section-form').remove();
            }
        }

        function getSectionTemplate(idx) {
            return `
            <div class="section-form" data-section-index="${idx}">
                <div class="section-form-header">
                    <span class="section-badge"><i class="fas fa-layer-group"></i> Section ${idx + 1}</span>
                    <h4 class="section-title">Nouvelle section</h4>
                    <button type="button" class="btn-remove" onclick="supprimerSection(this)">
                        <i class="fas fa-trash"></i> <span>Supprimer</span>
                    </button>
                </div>
                <div class="section-fields">
                    <div class="form-group">
                        <label>Titre de la section *</label>
                        <input type="text" name="section_titre_${idx}" required placeholder="Ex: Algèbre">
                    </div>
                    <div class="form-group">
                        <label>Description (optionnelle)</label>
                        <input type="text" name="section_desc_${idx}" placeholder="Ex: Chapitre sur les équations">
                    </div>
                </div>
                <input type="hidden" name="section_nbq_${idx}" value="0" id="section_nbq_${idx}">
                <div class="questions-container" id="questions_${idx}"></div>
                <button type="button" class="btn-add-question" onclick="ajouterQuestion(${idx})">
                    <i class="fas fa-plus"></i> Ajouter une question
                </button>
            </div>`;
        }

        function ajouterQuestion(sectionIdx) {
            const container = document.getElementById('questions_' + sectionIdx);
            const qIdx = container.querySelectorAll('.question-form').length;
            const div = document.createElement('div');
            div.innerHTML = getQuestionTemplate(sectionIdx, qIdx);
            container.appendChild(div.firstElementChild);

            const nbqInput = document.getElementById('section_nbq_' + sectionIdx);
            nbqInput.value = container.querySelectorAll('.question-form').length;
        }

        function supprimerQuestion(btn) {
            const questionForm = btn.closest('.question-form');
            const sectionForm = questionForm.closest('.section-form');
            const sectionIdx = sectionForm.dataset.sectionIndex;
            questionForm.remove();

            const container = document.getElementById('questions_' + sectionIdx);
            const nbqInput = document.getElementById('section_nbq_' + sectionIdx);
            nbqInput.value = container.querySelectorAll('.question-form').length;
        }

        function getQuestionTemplate(sIdx, qIdx) {
            const prefix = `q_${sIdx}_${qIdx}_`;
            let optionsHtml = '';
            for (let o = 0; o < 2; o++) {
                optionsHtml += getOptionTemplate(prefix, o);
            }
            return `
            <div class="question-form">
                <div class="question-form-header">
                    <h5><i class="fas fa-question-circle"></i> Question <span class="q-index">${qIdx + 1}</span></h5>
                    <button type="button" class="btn-remove-small" onclick="supprimerQuestion(this)">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="form-group">
                    <label>Énoncé *</label>
                    <textarea name="${prefix}enonce" rows="2" required placeholder="Saisissez la question..."></textarea>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Type *</label>
                        <select name="${prefix}type" required>
                            <option value="SIMPLE">Choix unique</option>
                            <option value="MULTIPLE">Choix multiple</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Cote *</label>
                        <input type="number" name="${prefix}cote" step="0.5" min="0.5" value="1" required>
                    </div>
                </div>
                <input type="hidden" name="${prefix}nbopt" value="2">
                <div class="options-container">
                    ${optionsHtml}
                </div>
                <button type="button" class="btn-add-option" onclick="ajouterOption('${prefix}')">
                    <i class="fas fa-plus"></i> Ajouter une option
                </button>
            </div>`;
        }

        function ajouterOption(prefix) {
            const questionForm = document.querySelector(`[name="${prefix}enonce"]`).closest('.question-form');
            const container = questionForm.querySelector('.options-container');
            const oIdx = container.querySelectorAll('.option-form').length;

            const div = document.createElement('div');
            div.innerHTML = getOptionTemplate(prefix, oIdx);
            container.appendChild(div.firstElementChild);

            const nboptInput = questionForm.querySelector(`[name="${prefix}nbopt"]`);
            nboptInput.value = container.querySelectorAll('.option-form').length;
        }

        function supprimerOption(btn) {
            const optionForm = btn.closest('.option-form');
            const questionForm = optionForm.closest('.question-form');
            const container = questionForm.querySelector('.options-container');

            if (container.querySelectorAll('.option-form').length <= 2) {
                alert('Une question doit avoir au moins 2 options.');
                return;
            }
            optionForm.remove();

            const prefix = questionForm.querySelector('textarea').name.replace('enonce', '');
            const nboptInput = questionForm.querySelector(`[name="${prefix}nbopt"]`);
            nboptInput.value = container.querySelectorAll('.option-form').length;
        }

        function getOptionTemplate(prefix, oIdx) {
            return `
            <div class="option-form">
                <input type="text" name="${prefix}opt_${oIdx}_texte" placeholder="Texte de l'option" required>
                <label class="checkbox-correct">
                    <input type="checkbox" name="${prefix}opt_${oIdx}_correct">
                    <span>Bonne réponse</span>
                </label>
                <button type="button" class="btn-remove-option" onclick="supprimerOption(this)">
                    <i class="fas fa-times"></i>
                </button>
            </div>`;
        }

        document.getElementById('quizForm').addEventListener('submit', function(e) {
            const sections = document.querySelectorAll('.section-form');
            if (sections.length === 0) {
                e.preventDefault();
                alert('Le quiz doit avoir au moins une section.');
                return;
            }

            let erreur = null;
            sections.forEach(sec => {
                const questions = sec.querySelectorAll('.question-form');
                if (questions.length === 0) {
                    erreur = 'Chaque section doit contenir au moins une question.';
                    return;
                }
                questions.forEach(q => {
                    const type = q.querySelector('select').value;
                    const correctes = q.querySelectorAll('input[type="checkbox"]:checked').length;
                    if (correctes === 0) {
                        erreur = 'Chaque question doit avoir au moins une bonne réponse.';
                    } else if (type === 'SIMPLE' && correctes > 1) {
                        erreur = 'Les questions à choix unique ne doivent avoir qu\\'une seule bonne réponse.';
                    }
                });
            });

            if (erreur) {
                e.preventDefault();
                alert(erreur);
            }
        });

        document.addEventListener('DOMContentLoaded', function() {
            if (document.querySelectorAll('.section-form').length === 0) {
                ajouterSection();
            }
        });
        </script>
        """;
    }

    // ============================================================
    // HELPERS GÉNÉRAUX
    // ============================================================
    private static String entete(String titre, String headerBase64, boolean publique) {
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

        if (headerBase64 != null) {
            sb.append("<div class=\"document-header ")
              .append(publique ? "header-public" : "header-private").append("\">");
            sb.append("  <img src=\"data:image/png;base64,").append(headerBase64)
              .append("\" alt=\"En-tête ").append(publique ? "public" : "privé").append("\">");
            sb.append("  <div class=\"visibility-badge\">")
              .append(publique
                      ? "<i class=\"fas fa-globe\"></i> PUBLIC"
                      : "<i class=\"fas fa-lock\"></i> PRIVÉ")
              .append("</div>");
            sb.append("</div>");
        }

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
        /* ══════════════════════════════════════════════════════════════
           RESET + BASE
           ══════════════════════════════════════════════════════════════ */
        * { margin:0; padding:0; box-sizing:border-box; }
        html, body {
            width:100%; min-height:100vh;
            background:#f7f7f5;
            font-family:'Plus Jakarta Sans',sans-serif;
            color:#1e293b;
        }
        body { padding:0; display:flex; flex-direction:column; }
        .main-wrapper { min-height:100vh; display:flex; flex-direction:column; }

        h1, h2, h3, h4, h5, h6 {
            text-align: inherit;
            margin: 0;
            padding: 0;
            font-weight: 700;
        }

        .document-header {
            background:white;
            padding:20px 40px;
            border-bottom:3px solid #e7e5e4;
            position:relative;
            text-align:center;
            box-shadow:0 4px 12px -8px rgba(0,0,0,0.08);
        }
        .document-header.header-public { border-bottom-color:#10b981; }
        .document-header.header-private { border-bottom-color:#f59e0b; }
        .document-header img { max-width:100%; height:auto; display:block; margin:0 auto; }
        .visibility-badge {
            position:absolute; top:20px; right:30px;
            padding:6px 14px; border-radius:20px;
            font-size:12px; font-weight:700; letter-spacing:0.4px;
            display:flex; align-items:center; gap:6px;
        }
        .header-public .visibility-badge { background:#d1fae5; color:#065f46; }
        .header-private .visibility-badge { background:#fef3c7; color:#92400e; }

        .app-header {
            background:#1c1917;
            padding:14px 40px;
            display:flex; justify-content:space-between; align-items:center;
            color:white;
        }
        .app-header .logo {
            display:flex; align-items:center; gap:10px;
            color:white; text-decoration:none; font-weight:700; font-size:16px;
        }
        .app-header .logo i { color:#10b981; font-size:22px; }
        .nav-links { display:flex; gap:8px; }
        .nav-links a {
            color:#a8a29e; text-decoration:none;
            padding:8px 14px; border-radius:8px;
            font-size:13px; font-weight:500;
            display:flex; align-items:center; gap:6px;
        }
        .nav-links a:hover, .nav-links a.active {
            background:rgba(255,255,255,0.1); color:white;
        }

        .container {
            max-width:1200px; margin:0 auto;
            padding:30px 40px; width:100%; flex:1;
        }

        .page-header {
            display:flex; justify-content:space-between; align-items:center;
            gap:20px; margin-bottom:30px; flex-wrap:wrap;
        }
        .page-header h2 {
            font-size:26px; font-weight:800;
            display:flex; align-items:center; gap:12px;
            color:#1c1917; text-align:left;
        }
        .page-header h2 i { color:#059669; }
        .page-header p { color:#57534e; margin-top:6px; font-size:14px; text-align:left; }

        .message {
            padding:14px 18px; border-radius:10px; margin-bottom:20px;
            display:flex; align-items:center; gap:10px; font-size:14px;
        }
        .message.success { background:#d1fae5; color:#065f46; border-left:4px solid #059669; }
        .message.error { background:#fee2e2; color:#991b1b; border-left:4px solid #dc2626; }

        .btn-back, .btn-cancel, .btn-submit {
            display:inline-flex; align-items:center; gap:8px;
            padding:10px 20px; border-radius:10px;
            font-weight:600; font-size:14px;
            text-decoration:none; border:none; cursor:pointer;
            transition:0.2s; font-family:inherit;
        }
        .btn-back, .btn-cancel { background:#f5f5f4; color:#57534e; }
        .btn-back:hover, .btn-cancel:hover { background:#e7e5e4; color:#1c1917; }
        .btn-submit {
            background:linear-gradient(135deg,#059669,#047857);
            color:white; padding:12px 28px;
        }
        .btn-submit:hover {
            transform:translateY(-1px);
            box-shadow:0 8px 20px -4px rgba(5,150,105,0.4);
        }

        .form-section {
            background:white;
            border-radius:16px;
            padding:30px 34px;
            margin-bottom:24px;
            border:1px solid #e7e5e4;
        }

        .form-section-header {
            display:flex !important;
            flex-direction:column !important;
            align-items:center !important;
            justify-content:center !important;
            text-align:center !important;
            gap:12px;
            padding-bottom:22px;
            border-bottom:2px dashed #e7e5e4;
            margin-bottom:28px;
            width:100% !important;
        }
        .form-section-header .form-section-number { margin: 0 auto !important; }
        .form-section-header h3 {
            font-size:20px !important;
            font-weight:800 !important;
            color:#1c1917 !important;
            display:flex !important;
            align-items:center;
            justify-content:center;
            gap:10px;
            text-align:center !important;
            margin:0 auto !important;
            width:100%;
        }
        .form-section-header h3 i { color:#059669; }

        .form-section-number {
            width:40px; height:40px; border-radius:50%;
            background:linear-gradient(135deg,#059669,#047857);
            color:white;
            display:flex; align-items:center; justify-content:center;
            font-weight:800; font-size:16px; flex-shrink:0;
        }

        .form-grid {
            display:grid; grid-template-columns:repeat(2, 1fr);
            gap:16px; margin-bottom:16px;
        }
        .form-grid .span-2 { grid-column:span 2; }
        .form-row { display:grid; grid-template-columns:2fr 1fr; gap:16px; }
        .form-group { display:flex; flex-direction:column; gap:6px; }

        .form-section .form-group label {
            font-size:13px; font-weight:600; color:#57534e;
            text-align:center !important;
            display:block !important;
            width:100% !important;
        }
        .form-section .form-group label .required { color:#dc2626; }

        .form-group input, .form-group select, .form-group textarea {
            padding:11px 14px;
            border:1.5px solid #e7e5e4;
            border-radius:10px;
            font-size:14px; font-family:inherit;
            background:white; transition:0.2s; color:#1c1917;
        }

        .form-section .form-group input,
        .form-section .form-group select {
            text-align: center !important;
        }
        .form-section .form-group textarea {
            text-align: left !important;
        }

        .form-group input:focus, .form-group select:focus, .form-group textarea:focus {
            outline:none;
            border-color:#059669;
            box-shadow:0 0 0 3px rgba(5,150,105,0.12);
        }
        .form-group textarea { resize:vertical; min-height:60px; }

        .radio-group { display:flex; gap:14px; flex-wrap:wrap; }
        .radio-card {
            flex:1; min-width:220px;
            background:#fafaf9;
            border:2px solid #e7e5e4;
            border-radius:12px;
            padding:18px;
            display:flex; flex-direction:column; gap:6px;
            cursor:pointer; transition:0.2s; position:relative;
            text-align:center;
            align-items:center;
        }
        .radio-card:hover { border-color:#6ee7b7; background:#f0fdf4; }
        .radio-card.selected {
            border-color:#059669;
            background:#ecfdf5;
            box-shadow:0 0 0 3px rgba(5,150,105,0.12);
        }
        .radio-card input[type="radio"] { position:absolute; opacity:0; pointer-events:none; }
        .radio-card i { font-size:22px; color:#059669; margin-bottom:4px; }
        .radio-title { font-size:15px; font-weight:700; color:#1c1917; }
        .radio-desc { font-size:12px; color:#78716c; text-align:center; }

        .section-form {
            background:white;
            border:2px solid #e7e5e4;
            border-radius:18px;
            padding:32px 28px;
            margin-bottom:24px;
            position:relative;
            overflow:hidden;
            transition:0.2s;
        }
        .section-form::before {
            content:'';
            position:absolute;
            top:0; left:0; right:0;
            height:4px;
            background:linear-gradient(90deg,#059669,#14b8a6,#f59e0b);
            opacity:0.9;
        }
        .section-form:hover {
            border-color:#6ee7b7;
            box-shadow:0 8px 24px -8px rgba(5,150,105,0.15);
        }

        .section-form-header {
            display:flex !important;
            flex-direction:column !important;
            align-items:center !important;
            justify-content:center !important;
            text-align:center !important;
            width:100% !important;
            gap:14px;
            margin-bottom:28px;
            padding-bottom:22px;
            border-bottom:2px dashed #e7e5e4;
            position:relative;
        }

        .section-badge {
            display:inline-flex !important;
            align-items:center;
            gap:8px;
            background:linear-gradient(135deg,#059669,#047857);
            color:white;
            padding:6px 18px;
            border-radius:20px;
            font-size:12px;
            font-weight:700;
            letter-spacing:0.5px;
            text-transform:uppercase;
            box-shadow:0 4px 12px -2px rgba(5,150,105,0.35);
            margin:0 auto !important;
            text-align:center !important;
        }
        .section-badge i { font-size:13px; }

        .section-form .section-title,
        .section-form-header h4.section-title,
        .section-form-header .section-title,
        h4.section-title {
            font-size:20px !important;
            font-weight:800 !important;
            color:#1c1917 !important;
            line-height:1.3 !important;
            max-width:600px !important;
            text-align:center !important;
            margin:0 auto !important;
            padding:0 !important;
            width:100%;
            display:block !important;
        }

        .section-form-header .btn-remove {
            position:absolute !important;
            top:0 !important;
            right:0 !important;
            background:#fee2e2;
            color:#991b1b;
            border:none;
            padding:8px 16px;
            border-radius:10px;
            font-weight:600;
            font-size:13px;
            cursor:pointer;
            display:inline-flex !important;
            align-items:center;
            gap:6px;
            transition:0.2s;
        }
        .section-form-header .btn-remove:hover {
            background:#fecaca;
            transform:translateY(-1px);
        }

        .section-fields {
            display:grid;
            grid-template-columns:1fr 1fr;
            gap:18px;
            margin-bottom:24px;
            max-width:900px;
            margin-left:auto;
            margin-right:auto;
        }

        .section-form .section-fields .form-group label {
            text-align:center !important;
            display:block !important;
            width:100% !important;
        }
        .section-form .section-fields .form-group input {
            text-align:center !important;
        }

        .questions-container {
            display:flex; flex-direction:column; gap:20px;
            margin-bottom:20px;
            text-align:left !important;
            max-width:100%;
        }
        .question-form {
            background:#fafaf9;
            border:1.5px solid #e7e5e4;
            border-radius:14px;
            padding:24px 24px 24px 28px;
            text-align:left !important;
            position:relative;
            transition:0.2s;
            border-left:4px solid #f59e0b;
        }
        .question-form:hover {
            border-color:#fcd34d;
            background:#fffbeb;
            box-shadow:0 4px 16px -4px rgba(245,158,11,0.15);
        }

        .question-form-header {
            display:flex; justify-content:space-between; align-items:center;
            margin-bottom:18px;
            padding-bottom:14px;
            border-bottom:1px solid #e7e5e4;
            text-align:left !important;
        }
        .question-form-header h5 {
            font-size:14px; font-weight:700;
            display:flex; align-items:center; gap:10px;
            color:#1c1917;
            margin:0;
            text-align:left !important;
        }
        .question-form-header h5 i { color:#d97706; font-size:16px; }
        .q-index {
            background:#fef3c7;
            color:#92400e;
            padding:3px 12px;
            border-radius:12px;
            font-size:12px;
            font-weight:700;
        }

        .question-form .form-group label,
        .question-form .form-row .form-group label {
            text-align:left !important;
            display:block !important;
        }
        .question-form .form-group input,
        .question-form .form-group select,
        .question-form .form-group textarea {
            text-align:left !important;
        }

        .question-form .form-group,
        .question-form .form-row,
        .question-form .options-container { text-align:left !important; }

        .btn-remove-small {
            width:28px; height:28px; border-radius:50%;
            background:#fee2e2; color:#991b1b;
            border:none; cursor:pointer;
            display:flex; align-items:center; justify-content:center;
            font-size:12px;
        }
        .btn-remove-small:hover { background:#fecaca; }

        .options-container {
            display:flex; flex-direction:column; gap:10px;
            margin:16px 0;
            text-align:left !important;
        }
        .option-form {
            display:grid;
            grid-template-columns:1fr auto auto;
            gap:12px;
            align-items:center;
            background:white;
            padding:12px 16px;
            border-radius:10px;
            border:1.5px solid #e7e5e4;
            transition:0.2s;
            text-align:left !important;
        }
        .option-form:hover {
            border-color:#fcd34d;
            background:#fffbeb;
        }
        .option-form input[type="text"] {
            padding:10px 14px;
            border:1.5px solid #e7e5e4;
            border-radius:8px;
            font-size:13px;
            font-family:inherit;
            background:#fafaf9;
            transition:0.2s;
            text-align:left !important;
        }
        .option-form input[type="text"]:focus {
            outline:none;
            border-color:#059669;
            background:white;
            box-shadow:0 0 0 3px rgba(5,150,105,0.12);
        }
        .checkbox-correct {
            display:flex; align-items:center; gap:8px;
            font-size:13px; font-weight:600; color:#57534e;
            white-space:nowrap; cursor:pointer;
            padding:6px 12px;
            background:#f5f5f4;
            border-radius:8px;
            transition:0.2s;
        }
        .checkbox-correct:hover {
            background:#d1fae5;
            color:#065f46;
        }
        .checkbox-correct input[type="checkbox"] {
            width:18px; height:18px;
            cursor:pointer; accent-color:#059669;
        }
        .btn-remove-option {
            width:28px; height:28px; border-radius:50%;
            background:#fee2e2; color:#991b1b;
            border:none; cursor:pointer;
            display:flex; align-items:center; justify-content:center;
            font-size:11px;
        }
        .btn-remove-option:hover { background:#fecaca; }

        .btn-add-section,
        .btn-add-question,
        .btn-add-option {
            background:white;
            color:#059669;
            border:2px dashed #6ee7b7;
            padding:14px 24px;
            border-radius:12px;
            font-weight:600;
            font-size:14px;
            cursor:pointer;
            display:inline-flex; align-items:center; gap:10px;
            transition:0.2s;
            width:100%;
            justify-content:center;
            font-family:inherit;
        }
        .btn-add-section:hover,
        .btn-add-question:hover,
        .btn-add-option:hover {
            background:#ecfdf5;
            border-color:#059669;
            color:#047857;
            transform:translateY(-1px);
        }
        .btn-add-question, .btn-add-option {
            padding:11px 20px;
            font-size:13px;
            border-radius:10px;
        }
        .btn-add-question {
            border-color:#fcd34d;
            color:#b45309;
        }
        .btn-add-question:hover {
            background:#fffbeb;
            border-color:#f59e0b;
            color:#92400e;
        }

        .submit-bar {
            display:flex; justify-content:flex-end; gap:12px;
            padding:24px 0;
            position:sticky; bottom:20px;
            background:linear-gradient(to top,
                rgba(247,247,245,1) 60%,
                rgba(247,247,245,0));
        }

        .footer {
            text-align:center; color:#a8a29e;
            font-size:13px; padding:30px 20px 20px;
        }

        @media (max-width:768px) {
            .app-header { padding:12px 20px; flex-direction:column; gap:10px; }
            .document-header { padding:15px 20px; }
            .visibility-badge { top:10px; right:15px; font-size:10px; padding:4px 10px; }
            .container { padding:20px; }
            .form-grid, .form-row, .section-fields { grid-template-columns:1fr; }
            .form-grid .span-2 { grid-column:span 1; }
            .option-form { grid-template-columns:1fr; }
            .submit-bar { flex-direction:column; position:static; }
            .page-header { flex-direction:column; align-items:stretch; }
            .radio-group { flex-direction:column; }
            .section-form-header .btn-remove {
                position:static !important;
                align-self:center;
                margin-top:6px;
            }
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

    private static String formatDateInput(java.util.Date date) {
        if (date == null) return "";
        LocalDateTime ldt = date.toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }
}