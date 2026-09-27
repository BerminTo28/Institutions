import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseurNotes implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerProfesseurNotes.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            String professeurId = getProfesseurIdFromSession(exchange);
            String institutionId = getInstitutionIdFromSession(exchange);

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
            LOGGER.log(Level.SEVERE, "Erreur I/O dans HandlerProfesseurNotes", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue dans HandlerProfesseurNotes", e);
            safeSendError(exchange, 500, "Erreur interne.");
        }
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String annee = params.getOrDefault("annee", "");
        String periode = params.getOrDefault("periode", "");
        String codeCours = params.getOrDefault("codeCours", "");
        String classe = params.getOrDefault("classe", "");

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux = profData.getCreneauxParProfesseur(professeurId, institutionId);

            List<String> anneesDispo = creneaux.stream()
                    .map(Professeur.Creneau::getAnneeAcademique)
                    .filter(a -> a != null && !a.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            List<String> periodesDispo = new ArrayList<>();
            if (!annee.isEmpty()) {
                periodesDispo = creneaux.stream()
                        .filter(c -> annee.equals(c.getAnneeAcademique()))
                        .map(Professeur.Creneau::getPeriode)
                        .filter(p -> p != null && !p.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }

            List<String> classesDispo = new ArrayList<>();
            if (!annee.isEmpty() && !periode.isEmpty()) {
                classesDispo = creneaux.stream()
                        .filter(c -> annee.equals(c.getAnneeAcademique())
                                && periode.equals(c.getPeriode()))
                        .map(Professeur.Creneau::getClasse)
                        .filter(cl -> cl != null && !cl.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }

            List<Map<String, String>> coursDispo = new ArrayList<>();
            if (!annee.isEmpty() && !periode.isEmpty() && !classe.isEmpty()) {
                Map<String, Map<String, String>> coursMap = new HashMap<>();
                creneaux.stream()
                        .filter(c -> annee.equals(c.getAnneeAcademique())
                                && periode.equals(c.getPeriode())
                                && classe.equals(c.getClasse()))
                        .forEach(c -> {
                            String code = c.getCodeCours();
                            if (code != null && !code.isEmpty() && !coursMap.containsKey(code)) {
                                Map<String, String> info = new HashMap<>();
                                info.put("codeCours", code);
                                info.put("nomMatiere",
                                        c.getNomMatiere() != null ? c.getNomMatiere() : code);
                                coursMap.put(code, info);
                            }
                        });
                coursDispo = new ArrayList<>(coursMap.values());
                coursDispo.sort(Comparator.comparing(m -> m.get("nomMatiere")));
            }

            String codeCoursOriginal = codeCours;
            boolean autorise = false;
            if (!codeCoursOriginal.isEmpty() && !annee.isEmpty()
                    && !periode.isEmpty() && !classe.isEmpty()) {
                autorise = creneaux.stream().anyMatch(c ->
                        annee.equals(c.getAnneeAcademique())
                        && periode.equals(c.getPeriode())
                        && classe.equals(c.getClasse())
                        && codeCoursOriginal.equals(c.getCodeCours())
                );
            }
            if (!autorise) codeCours = "";

            List<Etudiant> etudiants = null;
            List<Note> notes = null;
            Matiere matiere = null;
            if (autorise) {
                EtudiantData etudiantData = new EtudiantData(conn);
                etudiants = etudiantData.readWithFilters(institutionId, classe, annee, periode);
                NoteData noteData = new NoteData(conn);
                notes = noteData.readAllWithPeriodeAndCours(institutionId, codeCours, periode, annee);
                MatiereData matiereData = new MatiereData(institutionId, conn);
                matiere = matiereData.trouverMatiereParCodeCours(codeCours);
            }

            String html = UIProfesseurNotes.render(
                    professeurId,
                    anneesDispo,
                    periodesDispo,
                    classesDispo,
                    coursDispo,
                    annee,
                    periode,
                    codeCours,
                    classe,
                    etudiants,
                    notes,
                    matiere,
                    null,
                    null
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            safeSendError(exchange, 500, "Erreur base de données.");
        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            throw e;
        } finally {
            closeQuietly(conn);
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange, String professeurId, String institutionId)
            throws IOException {

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormData(body);
        String action = params.get("action");

        if (!"saveNotes".equals(action)) {
            safeSendError(exchange, 400, "Action inconnue.");
            return;
        }

        String annee = params.get("annee");
        String periode = params.get("periode");
        String codeCours = params.get("codeCours");
        String classe = params.get("classe");

        if (annee == null || periode == null || codeCours == null || classe == null) {
            safeSendError(exchange, 400, "Paramètres manquants.");
            return;
        }

        Connection conn = null;
        boolean autoCommitOriginal;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // --- 1. Vérifier l'autorisation du professeur ---
                ProfesseurData profData = new ProfesseurData(conn);
                List<Professeur.Creneau> creneaux =
                        profData.getCreneauxParProfesseur(professeurId, institutionId);

                final String finalAnnee = annee;
                final String finalPeriode = periode;
                final String finalClasse = classe;
                final String finalCodeCours = codeCours;

                boolean autorise = creneaux.stream().anyMatch(c ->
                        finalAnnee.equals(c.getAnneeAcademique())
                        && finalPeriode.equals(c.getPeriode())
                        && finalClasse.equals(c.getClasse())
                        && finalCodeCours.equals(c.getCodeCours())
                );

                if (!autorise) {
                    throw new Exception("Vous n'êtes pas autorisé à saisir des notes "
                            + "pour ce cours, cette classe ou cette période.");
                }

                // --- 2. Professeur + matière ---
                Professeur prof = profData.trouverParId(professeurId, institutionId);
                if (prof == null) throw new Exception("Professeur introuvable");

                MatiereData matiereData = new MatiereData(institutionId, conn);
                Matiere matiere = matiereData.trouverMatiereParCodeCours(codeCours);
                if (matiere == null) {
                    throw new Exception("Matière introuvable pour le code cours : " + codeCours);
                }
                double noteMin = matiere.getNoteMinimale();
                double noteMax = matiere.getNoteMaximale();

                // --- 3. Étudiants de la classe ---
                EtudiantData etudiantData = new EtudiantData(conn);
                List<Etudiant> etudiantsDeLaClasse = etudiantData.readWithFilters(
                        institutionId, classe, annee, periode);
                Set<String> idsEtudiantsValides = etudiantsDeLaClasse.stream()
                        .map(Etudiant::getNumeroIdentifiantEtudiant)
                        .collect(Collectors.toSet());

                // --- 4. Sauvegarder les notes ---
                NoteData noteData = new NoteData(conn);
                int saved = 0;
                int errors = 0;
                List<String> errorMessages;
                errorMessages = new ArrayList<>();

                for (String key : params.keySet()) {
                    if (!key.startsWith("note_")) continue;

                    String[] parts = key.split("_", 2);
                    if (parts.length != 2) continue;

                    String numeroEtudiant = parts[1];

                    if (!idsEtudiantsValides.contains(numeroEtudiant)) {
                        errorMessages.add("Étudiant " + numeroEtudiant
                                + " n'appartient pas à la classe " + classe);
                        errors++;
                        continue;
                    }

                    String noteValueStr = params.get(key);
                    String coefficient = params.get("coef_" + numeroEtudiant);
                    String noteSur = params.get("sur_" + numeroEtudiant);
                    String noteBase = params.get("base_" + numeroEtudiant);

                    if (noteValueStr == null || noteValueStr.trim().isEmpty()) continue;

                    double noteValue;
                    try {
                        noteValue = Double.parseDouble(noteValueStr.trim());
                    } catch (NumberFormatException e) {
                        errorMessages.add("Note invalide pour l'étudiant " + numeroEtudiant);
                        errors++;
                        continue;
                    }

                    if (noteValue < noteMin || noteValue > noteMax) {
                        errorMessages.add("Note " + noteValue + " hors limites ["
                                + noteMin + ", " + noteMax + "]");
                        errors++;
                        continue;
                    }

                    Note note = new Note();
                    note.setInstitutionId(institutionId);
                    note.setNumeroIdentifiantEtudiant(numeroEtudiant);
                    note.setAnneeAcademique(annee);
                    note.setPeriode(periode);
                    note.setCodeCours(codeCours);
                    note.setClasse(classe);
                    note.setNumeroIdentifiantEnseignant(professeurId);
                    note.setEnseignantNom(prof.getNom());
                    note.setEnseignantPrenom(prof.getPrenom());
                    note.setNoteValue(String.valueOf(noteValue));
                    note.setCoefficient(coefficient != null && !coefficient.isBlank()
                            ? coefficient : "1.0");
                    note.setNoteSur(noteSur != null && !noteSur.isBlank()
                            ? noteSur : String.valueOf(noteMax));
                    note.setNoteBase(noteBase != null && !noteBase.isBlank() ? noteBase : "0");
                    note.setNoteMinimale(String.valueOf(noteMin));
                    note.setNoteMaximale(String.valueOf(noteMax));
                    note.setNoteDePassage(String.valueOf(matiere.getNotePassage()));

                    etudiantsDeLaClasse.stream()
                            .filter(e -> e.getNumeroIdentifiantEtudiant().equals(numeroEtudiant))
                            .findFirst()
                            .ifPresent(e -> {
                                note.setEtudiantNom(e.getNom());
                                note.setEtudiantPrenom(e.getPrenom());
                            });

                    Note existing = noteData.trouverNoteParContexte(codeCours, numeroEtudiant, institutionId, periode, annee, classe);
                    boolean ok;
                    if (existing == null) {
                        ok = noteData.create(note);
                    } else {
                        ok = noteData.update(note);
                    }
                    if (ok) {
                        saved++;
                    } else {
                        errors++;
                        errorMessages.add("Échec enregistrement pour " + numeroEtudiant);
                    }
                }

                // --- 5. Calcul des rangs (dans la même transaction) ---
                if (saved > 0) {
                    calculerRangs(conn, codeCours, institutionId, annee, periode, classe);
                }

                final int savedFinal = saved;
                final int errorsFinal = errors;

                conn.commit();
                LOGGER.info(() -> "✅ Notes enregistrées : " + savedFinal
                        + "/" + params.size() + " (transaction)");

                declencherSyncAsyncApresCommit();

                String msg = savedFinal + " note(s) enregistrée(s)"
                        + (errorsFinal > 0 ? ", " + errorsFinal + " erreur(s)" : "");
                String msgType = errorsFinal > 0 ? "error" : "success";

                // --- 6. Recharger les données pour affichage ---
                List<Professeur.Creneau> creneauxReload =
                        profData.getCreneauxParProfesseur(professeurId, institutionId);

                List<String> anneesDispo = creneauxReload.stream()
                        .map(Professeur.Creneau::getAnneeAcademique)
                        .filter(a -> a != null && !a.isEmpty())
                        .distinct().sorted().collect(Collectors.toList());

                List<String> periodesDispo = new ArrayList<>();
                if (!annee.isEmpty()) {
                    periodesDispo = creneauxReload.stream()
                            .filter(c -> annee.equals(c.getAnneeAcademique()))
                            .map(Professeur.Creneau::getPeriode)
                            .filter(p -> p != null && !p.isEmpty())
                            .distinct().sorted().collect(Collectors.toList());
                }

                List<String> classesDispo = new ArrayList<>();
                if (!annee.isEmpty() && !periode.isEmpty()) {
                    classesDispo = creneauxReload.stream()
                            .filter(c -> annee.equals(c.getAnneeAcademique())
                                    && periode.equals(c.getPeriode()))
                            .map(Professeur.Creneau::getClasse)
                            .filter(cl -> cl != null && !cl.isEmpty())
                            .distinct().sorted().collect(Collectors.toList());
                }

                List<Map<String, String>> coursDispo = new ArrayList<>();
                if (!annee.isEmpty() && !periode.isEmpty() && !classe.isEmpty()) {
                    Map<String, Map<String, String>> coursMap = new HashMap<>();
                    creneauxReload.stream()
                            .filter(c -> annee.equals(c.getAnneeAcademique())
                                    && periode.equals(c.getPeriode())
                                    && classe.equals(c.getClasse()))
                            .forEach(c -> {
                                String code = c.getCodeCours();
                                if (code != null && !code.isEmpty()
                                        && !coursMap.containsKey(code)) {
                                    Map<String, String> info = new HashMap<>();
                                    info.put("codeCours", code);
                                    info.put("nomMatiere",
                                            c.getNomMatiere() != null
                                                    ? c.getNomMatiere() : code);
                                    coursMap.put(code, info);
                                }
                            });
                    coursDispo = new ArrayList<>(coursMap.values());
                    coursDispo.sort(Comparator.comparing(m -> m.get("nomMatiere")));
                }

                String codeCoursOriginal = codeCours;
                boolean autoriseReload = false;
                if (!codeCoursOriginal.isEmpty() && !annee.isEmpty()
                        && !periode.isEmpty() && !classe.isEmpty()) {
                    autoriseReload = creneauxReload.stream().anyMatch(c ->
                            annee.equals(c.getAnneeAcademique())
                            && periode.equals(c.getPeriode())
                            && classe.equals(c.getClasse())
                            && codeCoursOriginal.equals(c.getCodeCours())
                    );
                }

                List<Etudiant> etudiants = null;
                List<Note> notes = null;
                if (autoriseReload) {
                    etudiants = new EtudiantData(conn)
                            .readWithFilters(institutionId, classe, annee, periode);
                    notes = noteData.readAllWithPeriodeAndCours(
                            institutionId, codeCours, periode, annee);
                }

                String html = UIProfesseurNotes.render(
                        professeurId,
                        anneesDispo,
                        periodesDispo,
                        classesDispo,
                        coursDispo,
                        annee,
                        periode,
                        codeCours,
                        classe,
                        etudiants,
                        notes,
                        matiere,
                        msgType,
                        msg
                );
                ResponseUtil.sendHtml(exchange, 200, html);

            } catch (Exception e) {
                try {
                    conn.rollback();
                    LOGGER.warning(() -> "⚠️ Rollback effectué : " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "❌ Erreur rollback", rollbackEx);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (SQLException ignored) {}
            }

        } catch (IOException e) {
            if (isNetworkException(e)) {
                LOGGER.fine(() -> "Client déconnecté : " + e.getMessage());
                return;
            }
            LOGGER.log(Level.SEVERE, "Erreur I/O sauvegarde notes", e);
            safeSendError(exchange, 500, "Erreur interne.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur sauvegarde notes", e);
            safeSendError(exchange, 500, "Erreur: " + e.getMessage());
        } finally {
            closeQuietly(conn);
        }
    }

    // ==================== SYNCHRONISATION ====================
    private void declencherSyncAsyncApresCommit() {
        try {
            DatabaseManager.getInstance().declencherSyncImmediateAsync();
            LOGGER.fine("⚡ Sync asynchrone déclenchée après commit.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "⚠️ Impossible de déclencher la sync async", e);
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException ignored) {}
    }

    // ==================== CALCUL DES RANGS ====================
    /**
     * ✅ CORRECTION : `notes.id` est un VARCHAR(36) (UUID).
     *    On lit avec getString et on écrit avec setString.
     */
    private void calculerRangs(Connection conn, String codeCours, String institutionId,
                                 String annee, String periode, String classe) throws SQLException {

        String sqlSelect = "SELECT n.id, n.numero_etudiant, n.note_value "
                + "FROM notes n "
                + "JOIN etudiants e ON n.numero_etudiant = e.numero_identifiant "
                + "WHERE n.code_cours = ? AND n.institution_id = ? "
                + "AND n.annee_academique = ? AND n.periode = ? AND e.classe = ? "
                + "AND n.note_value IS NOT NULL "
                + "ORDER BY CAST(n.note_value AS DECIMAL(10,2)) DESC";

        List<Map<String, Object>> notes = new ArrayList<>();
        try (var ps = conn.prepareStatement(sqlSelect)) {
            ps.setString(1, codeCours);
            ps.setString(2, institutionId);
            ps.setString(3, annee);
            ps.setString(4, periode);
            ps.setString(5, classe);
            try (var rs = ps.executeQuery()) {
                int rang = 1;
                Double prevNote = null;
                int compteur = 1;
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    // ✅ id est un UUID String
                    map.put("id", rs.getString("id"));
                    map.put("numero_etudiant", rs.getString("numero_etudiant"));
                    Double note = rs.getDouble("note_value");
                    map.put("note_value", note);

                    if (prevNote != null && prevNote.equals(note)) {
                        map.put("rang", rang);
                    } else {
                        rang = compteur;
                        map.put("rang", rang);
                    }
                    prevNote = note;
                    compteur++;
                    notes.add(map);
                }
            }
        }

        if (!notes.isEmpty()) {
            String sqlUpdate = "UPDATE notes SET rang_etudiant = ? WHERE id = ?";
            try (var ps = conn.prepareStatement(sqlUpdate)) {
                for (Map<String, Object> n : notes) {
                    ps.setString(1, String.valueOf(n.get("rang")));
                    // ✅ id est un UUID String
                    ps.setString(2, (String) n.get("id"));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    // ==================== SESSION & PARSING ====================
    private String getProfesseurIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            return (String) session.getAttribute("userId");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'ID professeur", e);
            return null;
        }
    }

    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader == null) return null;
            String sessionId = extractSessionId(cookieHeader);
            if (sessionId == null) return null;
            HttpSession session = SessionManager.getSession(sessionId);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (instId == null || instId.isBlank()) {
                instId = (String) session.getAttribute("institution_id");
            }
            return instId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de récupérer l'institutionId", e);
            return null;
        }
    }

    private String extractSessionId(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }

    // ==================== SEND HELPERS ====================
    private void safeSendError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() != -1) return;
            String html = UIProfesseurNotes.renderError(message);
            ResponseUtil.sendHtml(exchange, status, html);
        } catch (IOException e) {
            if (!isNetworkException(e)) {
                LOGGER.log(Level.WARNING, "Impossible d'envoyer la page d'erreur", e);
            }
        }
    }

    private static boolean isNetworkException(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null) msg = cause.getMessage();
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