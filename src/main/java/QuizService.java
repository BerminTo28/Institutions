import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuizService {

    private static final Logger LOGGER = Logger.getLogger(QuizService.class.getName());

    private final Connection conn;
    private final String institutionId;

    public QuizService(Connection conn, String institutionId) {
        this.conn = conn;
        this.institutionId = institutionId;
    }

    // ============================================================
    // CRÉATION COMPLÈTE D'UN QUIZ (professeur)
    // ============================================================
    /**
     * Crée un quiz complet avec sections, questions et options.
     * Calcule automatiquement :
     *   - cote_section  = somme des cotes des questions
     *   - note_totale   = somme des cotes des sections
     */
    public boolean creerQuiz(Quiz quiz) throws SQLException {
        if (quiz == null) throw new IllegalArgumentException("Quiz null");

        // ✅ Validation métier
        if (quiz.getDateExpiration() == null) {
            throw new IllegalArgumentException("La date d'expiration est obligatoire.");
        }
        if (quiz.getSections() == null || quiz.getSections().isEmpty()) {
            throw new IllegalArgumentException("Un quiz doit avoir au moins une section.");
        }
        for (QuizSection s : quiz.getSections()) {
            if (s.getQuestions() == null || s.getQuestions().isEmpty()) {
                throw new IllegalArgumentException(
                        "La section '" + s.getTitre() + "' n'a aucune question.");
            }
            for (QuizQuestion q : s.getQuestions()) {
                if (!q.isValide()) {
                    throw new IllegalArgumentException(
                            "Question invalide : " + q.getEnonce());
                }
            }
            s.recalculerCote();
        }
        quiz.setNoteTotale(quiz.getSections().stream()
                .mapToDouble(QuizSection::getCoteSection).sum());

        boolean autoCommitOrig = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            quiz.setId(UUID.randomUUID().toString());
            quiz.setInstitutionId(institutionId);
            quiz.setStatut("BROUILLON");

            // ① INSERT quiz
            insertQuiz(quiz);

            // ② INSERT sections + questions + options
            for (QuizSection section : quiz.getSections()) {
                section.setId(UUID.randomUUID().toString());
                section.setInstitutionId(institutionId);
                section.setQuizId(quiz.getId());
                insertSection(section);

                for (QuizQuestion q : section.getQuestions()) {
                    q.setId(UUID.randomUUID().toString());
                    q.setInstitutionId(institutionId);
                    q.setSectionId(section.getId());
                    insertQuestion(q);

                    for (QuizOption opt : q.getOptions()) {
                        opt.setId(UUID.randomUUID().toString());
                        opt.setInstitutionId(institutionId);
                        opt.setQuestionId(q.getId());
                        insertOption(opt);
                    }
                }
            }

            conn.commit();
            LOGGER.info(() -> "✅ Quiz créé : " + quiz.getTitre()
                    + " (" + quiz.getSections().size() + " sections, "
                    + quiz.getNoteTotale() + " pts)");
            return true;

        } catch (SQLException e) {
            conn.rollback();
            LOGGER.log(Level.SEVERE, "Erreur création quiz", e);
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOrig);
        }
    }

    // ============================================================
    // ACCÈS ÉTUDIANT — vérifie l'expiration
    // ============================================================
    /**
     * Récupère un quiz pour un étudiant, s'il est accessible.
     * @return null si le quiz est expiré ou non publié.
     */
    public Quiz getQuizPourEtudiant(String quizId, String etudiantId) throws SQLException {
        Quiz quiz = getQuizComplet(quizId);
        if (quiz == null) return null;

        // ⏰ Vérification de l'expiration
        if (quiz.isExpire()) {
            LOGGER.info(() -> "⏰ Quiz expiré, accès refusé : " + quizId);
            return null;
        }
        if (!quiz.isAccessible()) {
            LOGGER.info(() -> "🚫 Quiz non accessible (statut=" + quiz.getStatut()
                    + ", debut=" + quiz.getDateDebut() + ")");
            return null;
        }
        return quiz;
    }

    // ============================================================
    // SOUMISSION D'UNE TENTATIVE ÉTUDIANT
    // ============================================================
    /**
     * Enregistre les réponses d'un étudiant et calcule sa cote.
     * @param quizId    id du quiz
     * @param etudiantId id de l'étudiant
     * @param reponses  Map<questionId, List<optionId>>
     * @return cote obtenue
     */
  public double soumettreTentative(String quizId, String etudiantId,
                                   java.util.Map<String, List<String>> reponses)
        throws SQLException {

    Quiz quiz = getQuizComplet(quizId);
    if (quiz == null) throw new SQLException("Quiz introuvable.");
    if (quiz.isExpire()) throw new SQLException("Quiz expiré, soumission refusée.");

    boolean autoCommitOrig = conn.getAutoCommit();
    conn.setAutoCommit(false);
    try {
        // ① Tentative
        QuizTentative tentative = new QuizTentative();
        tentative.setId(UUID.randomUUID().toString());
        tentative.setInstitutionId(institutionId);
        tentative.setQuizId(quizId);
        tentative.setEtudiantId(etudiantId);
        tentative.setDateDebut(new Date());
        tentative.setStatut("EN_COURS");
        insertTentative(tentative);

        // ② Calcul de la cote
        double coteTotale = 0.0;

        for (QuizSection section : quiz.getSections()) {
            for (QuizQuestion q : section.getQuestions()) {
                List<String> choix = reponses.getOrDefault(q.getId(), List.of());

                List<String> correctes = q.getOptions().stream()
                        .filter(QuizOption::isEstCorrecte)
                        .map(QuizOption::getId).toList();

                boolean juste;
                if (q.getType() == QuizQuestion.Type.SIMPLE) {
                    juste = choix.size() == 1 && correctes.contains(choix.get(0));
                } else {
                    juste = choix.size() == correctes.size()
                            && choix.containsAll(correctes);
                }

                if (juste) coteTotale += q.getCote();

                for (String optionId : choix) {
                    QuizReponseEtudiant r = new QuizReponseEtudiant();
                    r.setId(UUID.randomUUID().toString());
                    r.setInstitutionId(institutionId);
                    r.setTentativeId(tentative.getId());
                    r.setQuestionId(q.getId());
                    r.setOptionId(optionId);
                    insertReponseEtudiant(r);
                }
            }
        }

        // ✅ Copie finale AVANT toute lambda
        final double coteFinale = coteTotale;
        final double noteTotale = quiz.getNoteTotale();

        // ④ Mise à jour de la tentative
        tentative.setDateFin(new Date());
        tentative.setCoteObtenue(coteFinale);
        tentative.setStatut("TERMINEE");
        updateTentative(tentative);

        conn.commit();

        LOGGER.info(() -> "✅ Quiz soumis : étudiant=" + etudiantId
                + " | cote=" + coteFinale + "/" + noteTotale);

        return coteFinale;

    } catch (SQLException e) {
        conn.rollback();
        throw e;
    } finally {
        conn.setAutoCommit(autoCommitOrig);
    }
}
public List<Quiz> getQuizsByProfesseur(String professeurId) throws SQLException {
    List<Quiz> list = new ArrayList<>();
    String sql = "SELECT * FROM quiz WHERE institution_id=? AND professeur_id=? "
            + "ORDER BY date_creation DESC";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ps.setString(2, professeurId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapQuiz(rs));
        }
    }
    return list;
}

public List<Quiz> getQuizsPourEtudiant(String etudiantId) throws SQLException {
    List<Quiz> list = new ArrayList<>();
    // Récupérer les classes / cours de l'étudiant et filtrer les quiz PUBLIES non expirés
    String sql = "SELECT q.* FROM quiz q "
            + "JOIN etudiants e ON e.institution_id=q.institution_id "
            + "WHERE q.institution_id=? AND e.numero_identifiant=? "
            + "AND q.statut='PUBLIE' AND q.date_expiration > NOW() "
            + "AND (q.classe IS NULL OR q.classe = e.classe) "
            + "ORDER BY q.date_expiration ASC";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ps.setString(2, etudiantId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapQuiz(rs));
        }
    }
    return list;
}

public QuizTentative getTentative(String quizId, String etudiantId) throws SQLException {
    String sql = "SELECT * FROM quiz_tentative WHERE institution_id=? AND quiz_id=? "
            + "AND etudiant_id=?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ps.setString(2, quizId);
        ps.setString(3, etudiantId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                QuizTentative t = new QuizTentative();
                t.setId(rs.getString("id"));
                t.setInstitutionId(rs.getString("institution_id"));
                t.setQuizId(rs.getString("quiz_id"));
                t.setEtudiantId(rs.getString("etudiant_id"));
                t.setDateDebut(rs.getTimestamp("date_debut"));
                t.setDateFin(rs.getTimestamp("date_fin"));
                t.setCoteObtenue(rs.getDouble("cote_obtenue"));
                t.setStatut(rs.getString("statut"));
                return t;
            }
        }
    }
    return null;
}

public boolean supprimerQuiz(String quizId, String professeurId) throws SQLException {
    String sql = "DELETE FROM quiz WHERE institution_id=? AND id=? AND professeur_id=?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, institutionId);
        ps.setString(2, quizId);
        ps.setString(3, professeurId);
        return ps.executeUpdate() > 0;
    }
}

public boolean changerStatut(String quizId, String professeurId, String statut)
        throws SQLException {
    String sql = "UPDATE quiz SET statut=? WHERE institution_id=? AND id=? "
            + "AND professeur_id=?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, statut);
        ps.setString(2, institutionId);
        ps.setString(3, quizId);
        ps.setString(4, professeurId);
        return ps.executeUpdate() > 0;
    }
}

/**
 * ✅ Modifie un quiz existant.
 *
 * Stratégie :
 *   ① Vérifier l'existence et l'appartenance au professeur.
 *   ② Mettre à jour les champs du quiz.
 *   ③ Supprimer les sections/questions/options existantes (CASCADE).
 *   ④ Réinsérer les nouvelles sections/questions/options.
 *   ⑤ Recalculer la note totale.
 *
 * Tout est fait dans une seule transaction.
 */
public boolean modifierQuiz(Quiz quiz) throws SQLException {
    if (quiz == null) throw new IllegalArgumentException("Quiz null");
    if (quiz.getId() == null || quiz.getId().isBlank()) {
        throw new IllegalArgumentException("ID quiz manquant pour la modification.");
    }

    // --- Validations métier ---
    if (quiz.getDateExpiration() == null) {
        throw new IllegalArgumentException("La date d'expiration est obligatoire.");
    }
    if (quiz.getSections() == null || quiz.getSections().isEmpty()) {
        throw new IllegalArgumentException("Un quiz doit avoir au moins une section.");
    }
    for (QuizSection s : quiz.getSections()) {
        if (s.getQuestions() == null || s.getQuestions().isEmpty()) {
            throw new IllegalArgumentException(
                    "La section '" + s.getTitre() + "' n'a aucune question.");
        }
        for (QuizQuestion q : s.getQuestions()) {
            if (!q.isValide()) {
                throw new IllegalArgumentException(
                        "Question invalide : " + q.getEnonce());
            }
        }
        s.recalculerCote();
    }
    // Note totale = somme des cotes de sections
    double noteTotale = quiz.getSections().stream()
            .mapToDouble(QuizSection::getCoteSection).sum();
    quiz.setNoteTotale(noteTotale);

    boolean autoCommitOrig = conn.getAutoCommit();
    conn.setAutoCommit(false);

    try {
        // ============================================================
        // ① Vérifier existence + propriétaire
        // ============================================================
        String checkSql = "SELECT 1 FROM quiz "
                + "WHERE institution_id=? AND id=? AND professeur_id=?";
        boolean existe;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, institutionId);
            ps.setString(2, quiz.getId());
            ps.setString(3, quiz.getProfesseurId());
            try (ResultSet rs = ps.executeQuery()) {
                existe = rs.next();
            }
        }

        if (!existe) {
            conn.rollback();
            LOGGER.warning(() -> "⚠️ Quiz introuvable ou non autorisé : " + quiz.getId());
            return false;
        }

        // ============================================================
        // ② Mettre à jour les champs du quiz
        // ============================================================
        String updateSql = "UPDATE quiz SET "
                + "titre=?, description=?, code_cours=?, classe=?, periode=?, "
                + "annee_academique=?, date_debut=?, date_expiration=?, "
                + "duree_minutes=?, note_totale=?, source=? "
                + "WHERE institution_id=? AND id=? AND professeur_id=?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            int i = 1;
            ps.setString(i++, quiz.getTitre());
            ps.setString(i++, quiz.getDescription());
            ps.setString(i++, quiz.getCodeCours());
            ps.setString(i++, quiz.getClasse());
            ps.setString(i++, quiz.getPeriode());
            ps.setString(i++, quiz.getAnneeAcademique());
            ps.setTimestamp(i++, quiz.getDateDebut() != null
                    ? new java.sql.Timestamp(quiz.getDateDebut().getTime()) : null);
            ps.setTimestamp(i++, new java.sql.Timestamp(quiz.getDateExpiration().getTime()));
            ps.setInt(i++, quiz.getDureeMinutes());
            ps.setDouble(i++, quiz.getNoteTotale());
            ps.setString(i++, quiz.getSource() != null ? quiz.getSource() : "LOCAL");
            ps.setString(i++, institutionId);
            ps.setString(i++, quiz.getId());
            ps.setString(i, quiz.getProfesseurId());

            ps.executeUpdate();
        }

        // ============================================================
        // ③ Supprimer les sections existantes (CASCADE sur questions/options)
        // ============================================================
        String deleteSql = "DELETE FROM quiz_section WHERE institution_id=? AND quiz_id=?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, institutionId);
            ps.setString(2, quiz.getId());
            ps.executeUpdate();
        }

        // ============================================================
        // ④ Réinsérer les nouvelles sections / questions / options
        // ============================================================
        int ordreSection = 0;
        for (QuizSection section : quiz.getSections()) {
            // ✅ Nouvel UUID pour chaque section (les anciens sont supprimés)
            section.setId(UUID.randomUUID().toString());
            section.setInstitutionId(institutionId);
            section.setQuizId(quiz.getId());
            section.setOrdre(ordreSection++);

            insertSection(section);

            int ordreQuestion = 0;
            for (QuizQuestion question : section.getQuestions()) {
                question.setId(UUID.randomUUID().toString());
                question.setInstitutionId(institutionId);
                question.setSectionId(section.getId());
                question.setOrdre(ordreQuestion++);

                insertQuestion(question);

                int ordreOption = 0;
                for (QuizOption option : question.getOptions()) {
                    option.setId(UUID.randomUUID().toString());
                    option.setInstitutionId(institutionId);
                    option.setQuestionId(question.getId());
                    option.setOrdre(ordreOption++);

                    insertOption(option);
                }
            }
        }

        // ============================================================
        // ⑤ Commit
        // ============================================================
        conn.commit();

        final int nbSections = quiz.getSections().size();
        final int nbQuestions = quiz.getSections().stream()
                .mapToInt(s -> s.getQuestions().size()).sum();
        final double noteFinale = quiz.getNoteTotale();

        LOGGER.info(() -> "✅ Quiz modifié : " + quiz.getTitre()
                + " (" + nbSections + " sections, "
                + nbQuestions + " questions, "
                + noteFinale + " pts)");

        return true;

    } catch (SQLException e) {
        try { conn.rollback(); }
        catch (SQLException rollbackEx) {
            LOGGER.log(Level.SEVERE, "Erreur rollback modifierQuiz", rollbackEx);
        }
        LOGGER.log(Level.SEVERE, "Erreur modification quiz " + quiz.getId(), e);
        throw e;
    } finally {
        try { conn.setAutoCommit(autoCommitOrig); }
        catch (SQLException ignored) {}
    }
}
    // ============================================================
    // LECTURE COMPLÈTE D'UN QUIZ
    // ============================================================
    public Quiz getQuizComplet(String quizId) throws SQLException {
        Quiz quiz = getQuizById(quizId);
        if (quiz == null) return null;

        quiz.setSections(getSections(quizId));
        for (QuizSection s : quiz.getSections()) {
            s.setQuestions(getQuestions(s.getId()));
            for (QuizQuestion q : s.getQuestions()) {
                q.setOptions(getOptions(q.getId()));
            }
        }
        return quiz;
    }

    // ============================================================
    // Méthodes CRUD privées (à compléter selon vos tables)
    // ============================================================
    private void insertQuiz(Quiz q) throws SQLException {
        String sql = "INSERT INTO quiz (institution_id, id, professeur_id, code_cours, "
                + "classe, periode, annee_academique, titre, description, date_debut, "
                + "date_expiration, duree_minutes, note_totale, statut, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, q.getInstitutionId());
            ps.setString(i++, q.getId());
            ps.setString(i++, q.getProfesseurId());
            ps.setString(i++, q.getCodeCours());
            ps.setString(i++, q.getClasse());
            ps.setString(i++, q.getPeriode());
            ps.setString(i++, q.getAnneeAcademique());
            ps.setString(i++, q.getTitre());
            ps.setString(i++, q.getDescription());
            ps.setTimestamp(i++, q.getDateDebut() != null
                    ? new java.sql.Timestamp(q.getDateDebut().getTime()) : null);
            ps.setTimestamp(i++, new java.sql.Timestamp(q.getDateExpiration().getTime()));
            ps.setInt(i++, q.getDureeMinutes());
            ps.setDouble(i++, q.getNoteTotale());
            ps.setString(i++, q.getStatut());
            ps.setString(i, q.getSource() != null ? q.getSource() : "LOCAL");
            ps.executeUpdate();
        }
    }

    private void insertSection(QuizSection s) throws SQLException {
        String sql = "INSERT INTO quiz_section (institution_id, id, quiz_id, titre, "
                + "description, ordre, cote_section) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, s.getInstitutionId());
            ps.setString(i++, s.getId());
            ps.setString(i++, s.getQuizId());
            ps.setString(i++, s.getTitre());
            ps.setString(i++, s.getDescription());
            ps.setInt(i++, s.getOrdre());
            ps.setDouble(i, s.getCoteSection());
            ps.executeUpdate();
        }
    }

    private void insertQuestion(QuizQuestion q) throws SQLException {
        String sql = "INSERT INTO quiz_question (institution_id, id, section_id, "
                + "enonce, type, cote, ordre, explication) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, q.getInstitutionId());
            ps.setString(i++, q.getId());
            ps.setString(i++, q.getSectionId());
            ps.setString(i++, q.getEnonce());
            ps.setString(i++, q.getType().name());
            ps.setDouble(i++, q.getCote());
            ps.setInt(i++, q.getOrdre());
            ps.setString(i, q.getExplication());
            ps.executeUpdate();
        }
    }

    private void insertOption(QuizOption o) throws SQLException {
        String sql = "INSERT INTO quiz_option (institution_id, id, question_id, "
                + "texte, est_correcte, ordre) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, o.getInstitutionId());
            ps.setString(i++, o.getId());
            ps.setString(i++, o.getQuestionId());
            ps.setString(i++, o.getTexte());
            ps.setBoolean(i++, o.isEstCorrecte());
            ps.setInt(i, o.getOrdre());
            ps.executeUpdate();
        }
    }

    private void insertTentative(QuizTentative t) throws SQLException {
        String sql = "INSERT INTO quiz_tentative (institution_id, id, quiz_id, "
                + "etudiant_id, date_debut, statut, source) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, t.getInstitutionId());
            ps.setString(i++, t.getId());
            ps.setString(i++, t.getQuizId());
            ps.setString(i++, t.getEtudiantId());
            ps.setTimestamp(i++, new java.sql.Timestamp(t.getDateDebut().getTime()));
            ps.setString(i++, t.getStatut());
            ps.setString(i, t.getSource() != null ? t.getSource() : "LOCAL");
            ps.executeUpdate();
        }
    }

    private void updateTentative(QuizTentative t) throws SQLException {
        String sql = "UPDATE quiz_tentative SET date_fin=?, cote_obtenue=?, statut=? "
                + "WHERE institution_id=? AND id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new java.sql.Timestamp(t.getDateFin().getTime()));
            ps.setDouble(2, t.getCoteObtenue());
            ps.setString(3, t.getStatut());
            ps.setString(4, t.getInstitutionId());
            ps.setString(5, t.getId());
            ps.executeUpdate();
        }
    }

    private void insertReponseEtudiant(QuizReponseEtudiant r) throws SQLException {
        String sql = "INSERT INTO quiz_reponse_etudiant (institution_id, id, "
                + "tentative_id, question_id, option_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getInstitutionId());
            ps.setString(2, r.getId());
            ps.setString(3, r.getTentativeId());
            ps.setString(4, r.getQuestionId());
            ps.setString(5, r.getOptionId());
            ps.executeUpdate();
        }
    }

    // --- Lecture ---
    private Quiz getQuizById(String quizId) throws SQLException {
        String sql = "SELECT * FROM quiz WHERE institution_id=? AND id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, quizId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapQuiz(rs);
            }
        }
        return null;
    }

    private List<QuizSection> getSections(String quizId) throws SQLException {
        List<QuizSection> list = new ArrayList<>();
        String sql = "SELECT * FROM quiz_section WHERE institution_id=? AND quiz_id=? "
                + "ORDER BY ordre";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, quizId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapSection(rs));
            }
        }
        return list;
    }

    private List<QuizQuestion> getQuestions(String sectionId) throws SQLException {
        List<QuizQuestion> list = new ArrayList<>();
        String sql = "SELECT * FROM quiz_question WHERE institution_id=? AND section_id=? "
                + "ORDER BY ordre";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, sectionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapQuestion(rs));
            }
        }
        return list;
    }

    private List<QuizOption> getOptions(String questionId) throws SQLException {
        List<QuizOption> list = new ArrayList<>();
        String sql = "SELECT * FROM quiz_option WHERE institution_id=? AND question_id=? "
                + "ORDER BY ordre";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, institutionId);
            ps.setString(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapOption(rs));
            }
        }
        return list;
    }

    // --- Mapping ---
    private Quiz mapQuiz(ResultSet rs) throws SQLException {
        Quiz q = new Quiz();
        q.setId(rs.getString("id"));
        q.setInstitutionId(rs.getString("institution_id"));
        q.setProfesseurId(rs.getString("professeur_id"));
        q.setCodeCours(rs.getString("code_cours"));
        q.setClasse(rs.getString("classe"));
        q.setPeriode(rs.getString("periode"));
        q.setAnneeAcademique(rs.getString("annee_academique"));
        q.setTitre(rs.getString("titre"));
        q.setDescription(rs.getString("description"));
        q.setDateDebut(rs.getTimestamp("date_debut"));
        q.setDateExpiration(rs.getTimestamp("date_expiration"));
        q.setDureeMinutes(rs.getInt("duree_minutes"));
        q.setNoteTotale(rs.getDouble("note_totale"));
        q.setStatut(rs.getString("statut"));
        q.setSource(rs.getString("source"));
        q.setDateCreation(rs.getTimestamp("date_creation"));
        q.setDateModification(rs.getTimestamp("date_modification"));
        return q;
    }

    private QuizSection mapSection(ResultSet rs) throws SQLException {
        QuizSection s = new QuizSection();
        s.setId(rs.getString("id"));
        s.setInstitutionId(rs.getString("institution_id"));
        s.setQuizId(rs.getString("quiz_id"));
        s.setTitre(rs.getString("titre"));
        s.setDescription(rs.getString("description"));
        s.setOrdre(rs.getInt("ordre"));
        s.setCoteSection(rs.getDouble("cote_section"));
        return s;
    }

    private QuizQuestion mapQuestion(ResultSet rs) throws SQLException {
        QuizQuestion q = new QuizQuestion();
        q.setId(rs.getString("id"));
        q.setInstitutionId(rs.getString("institution_id"));
        q.setSectionId(rs.getString("section_id"));
        q.setEnonce(rs.getString("enonce"));
        q.setType(QuizQuestion.Type.valueOf(rs.getString("type")));
        q.setCote(rs.getDouble("cote"));
        q.setOrdre(rs.getInt("ordre"));
        q.setExplication(rs.getString("explication"));
        return q;
    }

    private QuizOption mapOption(ResultSet rs) throws SQLException {
        QuizOption o = new QuizOption();
        o.setId(rs.getString("id"));
        o.setInstitutionId(rs.getString("institution_id"));
        o.setQuestionId(rs.getString("question_id"));
        o.setTexte(rs.getString("texte"));
        o.setEstCorrecte(rs.getBoolean("est_correcte"));
        o.setOrdre(rs.getInt("ordre"));
        return o;
    }
}