import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseurQuiz implements HttpHandler {

    private static final Logger LOGGER =
            Logger.getLogger(HandlerProfesseurQuiz.class.getName());

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            final String professeurId = getProfesseurIdFromSession(exchange);
            final String institutionId = getInstitutionIdFromSession(exchange);

            if (professeurId == null || institutionId == null) {
                exchange.getResponseHeaders().set("Location", "/connexion");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, professeurId, institutionId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, professeurId, institutionId);
            } else {
                ResponseUtil.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerProfesseurQuiz", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerProfesseurQuiz", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ============================================================
    // GET — Liste des quiz / Détail / Édition
    // ============================================================
  private void handleGet(HttpExchange exchange, String professeurId, String institutionId)
        throws IOException {

    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
    String action = params.getOrDefault("action", "liste");
    String quizId = params.getOrDefault("id", "");

    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        QuizService service = new QuizService(conn, institutionId);

        // ============================================================
        // ① Action : delete
        // ============================================================
        if ("delete".equals(action) && !quizId.isBlank()) {
            boolean deleted = service.supprimerQuiz(quizId, professeurId);
            String msg = deleted ? "Quiz supprimé." : "Erreur suppression.";
            handleGetWithMessage(exchange, professeurId, institutionId,
                    deleted ? "success" : "error", msg);
            return;
        }

        // ============================================================
        // ② Action : publier / depublier
        // ============================================================
        if ("publier".equals(action) || "depublier".equals(action)) {
            if (quizId.isBlank()) {
                safeSendError(exchange, 400, "ID quiz manquant.");
                return;
            }
            String nouveauStatut = "publier".equals(action) ? "PUBLIE" : "BROUILLON";
            boolean ok = service.changerStatut(quizId, professeurId, nouveauStatut);
            String msg = ok
                    ? "Quiz " + ("publier".equals(action) ? "publié" : "dépublié") + "."
                    : "Erreur lors du changement de statut.";
            handleGetWithMessage(exchange, professeurId, institutionId,
                    ok ? "success" : "error", msg);
            return;
        }

        // ============================================================
        // ✅ ③ Action : nouveau (formulaire vide)
        // ============================================================
        if ("nouveau".equals(action)) {
            // Charger l'institution pour l'en-tête
            Institution institution = new InstitutionData(conn)
                    .readByIdOrInstitutionId(institutionId);

            // Charger les créneaux du professeur
            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux = profData
                    .getCreneauxParProfesseur(professeurId, institutionId);

            List<String> codesCours = creneaux.stream()
                    .map(Professeur.Creneau::getCodeCours)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> classes = creneaux.stream()
                    .map(Professeur.Creneau::getClasse)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> periodes = creneaux.stream()
                    .map(Professeur.Creneau::getPeriode)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> annees = creneaux.stream()
                    .map(Professeur.Creneau::getAnneeAcademique)
                    .filter(a -> a != null && !a.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            String html = UIProfesseurQuiz.renderFormulaire(
                    professeurId,
                    codesCours, classes, periodes, annees,
                    null,                       // quiz = null → création
                    institution,
                    "PRIVE",                    // visibilité par défaut
                    null, null                  // pas de message
            );
            ResponseUtil.sendHtml(exchange, 200, html);
            return;
        }

        // ============================================================
        // ✅ ④ Action : editer (formulaire pré-rempli)
        // ============================================================
        if ("editer".equals(action) && !quizId.isBlank()) {
            Quiz quiz = service.getQuizComplet(quizId);
            if (quiz == null || !professeurId.equals(quiz.getProfesseurId())) {
                safeSendError(exchange, 404, "Quiz introuvable.");
                return;
            }

            String visibilite = "PUBLIE".equalsIgnoreCase(quiz.getStatut())
                    ? "PUBLIC" : "PRIVE";

            Institution institution = new InstitutionData(conn)
                    .readByIdOrInstitutionId(institutionId);

            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux = profData
                    .getCreneauxParProfesseur(professeurId, institutionId);

            List<String> codesCours = creneaux.stream()
                    .map(Professeur.Creneau::getCodeCours)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> classes = creneaux.stream()
                    .map(Professeur.Creneau::getClasse)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> periodes = creneaux.stream()
                    .map(Professeur.Creneau::getPeriode)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            List<String> annees = creneaux.stream()
                    .map(Professeur.Creneau::getAnneeAcademique)
                    .filter(a -> a != null && !a.isBlank())
                    .distinct().sorted().collect(Collectors.toList());

            String html = UIProfesseurQuiz.renderFormulaire(
                    professeurId,
                    codesCours, classes, periodes, annees,
                    quiz,                       // quiz non-null → modification
                    institution,
                    visibilite,
                    null, null
            );
            ResponseUtil.sendHtml(exchange, 200, html);
            return;
        }

        // ============================================================
        // ⑤ Action par défaut : détail d'un quiz
        // ============================================================
        if (!quizId.isBlank()) {
            Quiz quiz = service.getQuizComplet(quizId);
            if (quiz == null || !professeurId.equals(quiz.getProfesseurId())) {
                safeSendError(exchange, 404, "Quiz introuvable.");
                return;
            }

            String html = UIProfesseurQuiz.renderDetail(quiz);
            ResponseUtil.sendHtml(exchange, 200, html);
            return;
        }

        // ============================================================
        // ⑥ Action : liste (par défaut)
        // ============================================================
        List<Quiz> quizList = service.getQuizsByProfesseur(professeurId);
        String html = UIProfesseurQuiz.renderListe(professeurId, quizList, null, null);
        ResponseUtil.sendHtml(exchange, 200, html);

    } catch (SQLException e) {
        LOGGER.log(Level.SEVERE, "Erreur SQL (GET)", e);
        safeSendError(exchange, 500, "Erreur base de données.");
    }
}
    private void handleGetWithMessage(HttpExchange exchange, String professeurId,
                                       String institutionId, String msgType, String msg)
            throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            QuizService service = new QuizService(conn, institutionId);
            List<Quiz> quizList = service.getQuizsByProfesseur(professeurId);

            String html = UIProfesseurQuiz.renderListe(
                    professeurId, quizList, msgType, msg);
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL (GET+msg)", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        }
    }

    // ============================================================
    // POST — Création ou modification d'un quiz
    // ============================================================
    private void handlePost(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, List<String>> multiParams = parseMultiFormData(body);

        String action = first(multiParams, "action");

        // ============================================================
        // Action : publier / dépublier
        // ============================================================
        if ("publier".equals(action) || "depublier".equals(action)) {
            String quizId = first(multiParams, "quizId");
            if (quizId == null || quizId.isBlank()) {
                safeSendError(exchange, 400, "ID quiz manquant.");
                return;
            }
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                QuizService service = new QuizService(conn, institutionId);
                String nouveauStatut = "publier".equals(action) ? "PUBLIE" : "BROUILLON";
                boolean ok = service.changerStatut(quizId, professeurId, nouveauStatut);
                String msg = ok
                        ? "Quiz " + ("publier".equals(action) ? "publié" : "dépublié") + "."
                        : "Erreur lors du changement de statut.";
                handleGetWithMessage(exchange, professeurId, institutionId,
                        ok ? "success" : "error", msg);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Erreur changement statut", e);
                handleGetWithMessage(exchange, professeurId, institutionId,
                        "error", "Erreur base de données.");
            }
            return;
        }

        // ============================================================
        // Action : enregistrer (créer ou modifier)
        // ============================================================
        if (!"enregistrer".equals(action)) {
            safeSendError(exchange, 400, "Action inconnue.");
            return;
        }

        // --- 1. Validation des champs globaux ---
        String titre = first(multiParams, "titre");
        String description = first(multiParams, "description");
        String codeCours = first(multiParams, "codeCours");
        String classe = first(multiParams, "classe");
        String periode = first(multiParams, "periode");
        String annee = first(multiParams, "annee_academique");
        String dateDebutStr = first(multiParams, "date_debut");
        String dateExpirationStr = first(multiParams, "date_expiration");
        String dureeStr = first(multiParams, "duree_minutes");

        if (isBlank(titre) || isBlank(codeCours) || isBlank(classe)
                || isBlank(periode) || isBlank(annee)
                || isBlank(dateExpirationStr)) {
            safeSendError(exchange, 400,
                    "Titre, cours, classe, période, année et date d'expiration sont obligatoires.");
            return;
        }

        Date dateDebut;
        Date dateExpiration;
        try {
            dateDebut = isBlank(dateDebutStr) ? new Date() : DATE_FMT.parse(dateDebutStr);
            dateExpiration = DATE_FMT.parse(dateExpirationStr);
        } catch (ParseException e) {
            safeSendError(exchange, 400, "Format de date invalide.");
            return;
        }

        if (dateExpiration.before(new Date())) {
            safeSendError(exchange, 400,
                    "La date d'expiration doit être dans le futur.");
            return;
        }

        int duree = 30;
        try {
            if (!isBlank(dureeStr)) duree = Integer.parseInt(dureeStr);
        } catch (NumberFormatException ignored) {}

        // --- 2. Construction des sections / questions / options ---
        List<QuizSection> sections;
        try {
            sections = extraireSections(multiParams);
        } catch (IllegalArgumentException e) {
            safeSendError(exchange, 400, e.getMessage());
            return;
        }

        if (sections.isEmpty()) {
            safeSendError(exchange, 400, "Le quiz doit contenir au moins une section.");
            return;
        }

        // --- 3. Création ou modification ---
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            QuizService service = new QuizService(conn, institutionId);

            Quiz quiz = new Quiz();
            quiz.setProfesseurId(professeurId);
            quiz.setCodeCours(codeCours.trim());
            quiz.setClasse(classe.trim());
            quiz.setPeriode(periode.trim());
            quiz.setAnneeAcademique(annee.trim());
            quiz.setTitre(titre.trim());
            quiz.setDescription(description != null ? description.trim() : "");
            quiz.setDateDebut(dateDebut);
            quiz.setDateExpiration(dateExpiration);
            quiz.setDureeMinutes(duree);
            quiz.setSections(sections);

            String quizIdExistant = first(multiParams, "quizId");
            boolean ok;

            if (quizIdExistant != null && !quizIdExistant.isBlank()) {
                quiz.setId(quizIdExistant);
                ok = service.modifierQuiz(quiz);
            } else {
                ok = service.creerQuiz(quiz);
            }

            String msg = ok
                    ? "Quiz " + (quizIdExistant != null && !quizIdExistant.isBlank()
                            ? "modifié" : "créé")
                      + " avec succès (" + quiz.getNoteTotale() + " pts)."
                    : "Erreur lors de l'enregistrement.";
            String msgType = ok ? "success" : "error";

            handleGetWithMessage(exchange, professeurId, institutionId, msgType, msg);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur enregistrement quiz", e);
            handleGetWithMessage(exchange, professeurId, institutionId,
                    "error", "Erreur : " + e.getMessage());
        }
    }

    // ============================================================
    // Parsing des sections / questions / options
    // ============================================================
    /**
     * Format attendu (noms de champs) :
     *   section_titre_0         = "Algèbre"
     *   section_desc_0          = "..."
     *   section_nbq_0           = "2"
     *
     *   q_0_0_enonce            = "Que vaut 2+2 ?"
     *   q_0_0_type              = "SIMPLE" | "MULTIPLE"
     *   q_0_0_cote              = "2"
     *   q_0_0_nbopt             = "3"
     *   q_0_0_opt_0_texte       = "3"
     *   q_0_0_opt_0_correct     = "off|on"
     *   ...
     */
    private List<QuizSection> extraireSections(Map<String, List<String>> params) {
        List<QuizSection> sections = new ArrayList<>();

        int sectionIndex = 0;
        while (true) {
            String titreKey = "section_titre_" + sectionIndex;
            String titre = first(params, titreKey);
            if (titre == null || titre.isBlank()) break;

            QuizSection section = new QuizSection();
            section.setTitre(titre.trim());
            section.setDescription(orEmpty(first(params, "section_desc_" + sectionIndex)));
            section.setOrdre(sectionIndex);

            int nbQ = parsePositiveInt(first(params, "section_nbq_" + sectionIndex), 0);
            List<QuizQuestion> questions = new ArrayList<>();

            for (int q = 0; q < nbQ; q++) {
                String prefix = "q_" + sectionIndex + "_" + q + "_";
                String enonce = first(params, prefix + "enonce");
                if (isBlank(enonce)) continue;

                QuizQuestion question = new QuizQuestion();
                question.setEnonce(enonce.trim());
                question.setOrdre(q);

                String typeStr = first(params, prefix + "type");
                question.setType("MULTIPLE".equalsIgnoreCase(typeStr)
                        ? QuizQuestion.Type.MULTIPLE
                        : QuizQuestion.Type.SIMPLE);

                question.setCote(parseDouble(first(params, prefix + "cote"), 1.0));
                question.setExplication(orEmpty(first(params, prefix + "explication")));

                int nbOpt = parsePositiveInt(first(params, prefix + "nbopt"), 0);
                List<QuizOption> options = new ArrayList<>();
                long nbCorrectes = 0;

                for (int o = 0; o < nbOpt; o++) {
                    String optPrefix = prefix + "opt_" + o + "_";
                    String texte = first(params, optPrefix + "texte");
                    if (isBlank(texte)) continue;

                    QuizOption option = new QuizOption();
                    option.setTexte(texte.trim());
                    option.setOrdre(o);
                    option.setEstCorrecte("on".equalsIgnoreCase(
                            first(params, optPrefix + "correct")));
                    if (option.isEstCorrecte()) nbCorrectes++;

                    options.add(option);
                }

                if (options.size() < 2) {
                    throw new IllegalArgumentException(
                            "La question « " + enonce + " » doit avoir au moins 2 options.");
                }
                if (nbCorrectes == 0) {
                    throw new IllegalArgumentException(
                            "La question « " + enonce + " » doit avoir au moins 1 bonne réponse.");
                }
                if (question.getType() == QuizQuestion.Type.SIMPLE && nbCorrectes != 1) {
                    throw new IllegalArgumentException(
                            "La question « " + enonce
                            + " » est de type SIMPLE : elle doit avoir exactement 1 bonne réponse.");
                }

                question.setOptions(options);
                questions.add(question);
            }

            if (questions.isEmpty()) {
                throw new IllegalArgumentException(
                        "La section « " + titre + " » n'a aucune question.");
            }

            section.setQuestions(questions);
            section.recalculerCote();
            sections.add(section);

            sectionIndex++;
        }

        return sections;
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getProfesseurIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        return session == null ? null : (String) session.getAttribute("userId");
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        HttpSession session = getSession(exchange);
        if (session == null) return null;
        String instId = (String) session.getAttribute("institutionId");
        if (instId == null || instId.isBlank()) {
            instId = (String) session.getAttribute("institution_id");
        }
        return instId;
    }

    private HttpSession getSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            return SessionManager.getSession(sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur session", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) return parts[1];
        }
        return null;
    }

    // ============================================================
    // PARSING
    // ============================================================
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private Map<String, List<String>> parseMultiFormData(String body) {
        Map<String, List<String>> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    params.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    private static String first(Map<String, List<String>> m, String key) {
        List<String> l = m.get(key);
        return (l == null || l.isEmpty()) ? null : l.get(0);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static int parsePositiveInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Math.max(0, Integer.parseInt(s.trim())); }
        catch (NumberFormatException e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s.trim().replace(",", ".")); }
        catch (NumberFormatException e) { return def; }
    }

    // ============================================================
    // SEND HELPERS
    // ============================================================
    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            String html = UIProfesseurQuiz.renderError(message);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer l'erreur", e);
            }
        }
    }

    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) {
            Throwable c = e.getCause();
            if (c != null && c.getMessage() != null) msg = c.getMessage();
        }
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("aborted")
            || lower.contains("broken pipe")
            || lower.contains("connection reset")
            || lower.contains("insufficient bytes")
            || lower.contains("connection closed")
            || lower.contains("software in your host machine");
    }
}