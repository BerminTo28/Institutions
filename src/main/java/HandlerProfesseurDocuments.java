import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandlerProfesseurDocuments implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerProfesseurDocuments.class.getName());
    private static final String UPLOAD_DIR = "./data/documents/";

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
            LOGGER.log(Level.SEVERE, "Erreur dans HandlerProfesseurDocuments", e);
            String html = UIProfesseurDocuments.renderError("Erreur interne: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== GET ====================
    private void handleGet(HttpExchange exchange, String professeurId, String institutionId) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String matiere = params.getOrDefault("matiere", "");
        String classe = params.getOrDefault("classe", "");
        String periode = params.getOrDefault("periode", "");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            DocumentManager manager = new DocumentManager(conn);
            List<DocumentProfesseur> documents;

            if (!matiere.isEmpty() || !classe.isEmpty() || !periode.isEmpty()) {
                documents = manager.listerDocumentsAvecFiltres(institutionId, professeurId, matiere, classe, periode);
            } else {
                documents = manager.listerDocuments(institutionId, professeurId);
            }

            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux = profData.getCreneauxParProfesseur(professeurId, institutionId);

            List<String> matieres = getMatieres(creneaux);
            List<String> classesDispo = getClasses(creneaux);
            List<String> periodesDispo = getPeriodes(creneaux);

            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            String anneeActive = "";
            String periodeActive = "";
            if (!creneaux.isEmpty()) {
                Professeur.Creneau first = creneaux.get(0);
                anneeActive = first.getAnneeAcademique() != null ? first.getAnneeAcademique() : "";
                periodeActive = first.getPeriode() != null ? first.getPeriode() : "";
            }

            String html = UIProfesseurDocuments.render(
                professeurId,
                documents,
                matieres,
                classesDispo,
                periodesDispo,
                matiere,
                classe,
                periode,
                institutionId,
                annees,
                periodes,
                anneeActive,
                periodeActive,
                null,
                null
            );
            ResponseUtil.sendHtml(exchange, 200, html);

        } catch (DocumentManager.DocumentException e) {
            LOGGER.log(Level.SEVERE, "Erreur Document", e);
            String html = UIProfesseurDocuments.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UIProfesseurDocuments.renderError("Erreur base de données: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== POST ====================
    private void handlePost(HttpExchange exchange, String professeurId, String institutionId) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> params;

        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            try {
                MultipartParser.MultipartData multipart = MultipartParser.parse(exchange.getRequestBody(), contentType);
                params = multipart.getFields();
                if (multipart.hasFile()) {
                    String fileName = multipart.getFileName();
                    byte[] content = multipart.getFileContent();
                    File profDir = new File(UPLOAD_DIR + professeurId);
                    if (!profDir.exists()) {
                        profDir.mkdirs();
                    }
                    String uniqueName = System.currentTimeMillis() + "_" + fileName;
                    Path filePath = Paths.get(profDir.getAbsolutePath(), uniqueName);
                    Files.write(filePath, content);
                    params.put("fichier_nom", fileName);
                    params.put("fichier_chemin", filePath.toString());
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Erreur parsing multipart", e);
                handleGetWithMessage(exchange, professeurId, institutionId, "error",
                        "Erreur lors de l'upload: " + e.getMessage());
                return;
            }
        } else {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            params = parseFormData(body);
        }

        processPost(exchange, professeurId, institutionId, params);
    }

    private void processPost(HttpExchange exchange, String professeurId, String institutionId,
                              Map<String, String> params) throws IOException {
        String action = params.get("action");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            DocumentManager manager = new DocumentManager(conn);
            ProfesseurData profData = new ProfesseurData(conn);

            if ("ajouter".equalsIgnoreCase(action) || "modifier".equalsIgnoreCase(action)) {
                String matiere = params.get("matiere");
                String classe = params.get("classe");
                String periode = params.get("periode");
                String anneeAcademique = params.get("annee_academique");

                // ✅ Vérification des droits : le professeur doit avoir un créneau correspondant
                List<Professeur.Creneau> creneaux = profData.getCreneauxParProfesseur(professeurId, institutionId);

                boolean aLeDroit = false;
                for (Professeur.Creneau c : creneaux) {
                    boolean matiereOk = matiere == null || matiere.isBlank() ||
                                       (c.getCodeCours() != null && c.getCodeCours().equals(matiere));
                    boolean classeOk = classe == null || classe.isBlank() ||
                                      (c.getClasse() != null && c.getClasse().equals(classe));
                    boolean periodeOk = periode == null || periode.isBlank() ||
                                       (c.getPeriode() != null && c.getPeriode().equals(periode));
                    boolean anneeOk = anneeAcademique == null || anneeAcademique.isBlank() ||
                                     (c.getAnneeAcademique() != null && c.getAnneeAcademique().equals(anneeAcademique));

                    if (matiereOk && classeOk && periodeOk && anneeOk) {
                        aLeDroit = true;
                        break;
                    }
                }

                if (!aLeDroit) {
                    handleGetWithMessage(exchange, professeurId, institutionId, "error",
                            "Vous n'êtes pas autorisé à ajouter un document pour cette matière/classe/période.");
                    return;
                }

                DocumentProfesseur doc = new DocumentProfesseur();
                doc.setInstitutionId(institutionId);
                doc.setProfesseurId(professeurId);
                doc.setTitre(params.get("titre"));
                doc.setDescription(params.get("description"));
                doc.setType(params.get("type"));
                doc.setMatiere(matiere);
                doc.setClasse(classe);
                doc.setPeriode(periode);
                doc.setAnneeAcademique(anneeAcademique);
                doc.setEstPublic("on".equalsIgnoreCase(params.get("est_public")));

                String url = params.get("url");
                doc.setUrl(url != null ? url : "");

                String fichierNom = params.get("fichier_nom");
                String fichierChemin = params.get("fichier_chemin");
                if (fichierNom != null && !fichierNom.isBlank()) {
                    doc.setFichierNom(fichierNom);
                    doc.setFichierChemin(fichierChemin != null ? fichierChemin
                            : UPLOAD_DIR + professeurId + "/" + fichierNom);
                    File f = new File(doc.getFichierChemin());
                    doc.setTaille(f.exists() ? f.length() : 0);
                }

                boolean success;
                if ("ajouter".equalsIgnoreCase(action)) {
                    success = manager.ajouterDocument(doc);
                } else {
                    // ✅ id en String (UUID) — plus de Integer.parseInt
                    String idStr = params.get("id");
                    if (idStr == null || idStr.isBlank()) {
                        throw new DocumentManager.DocumentException("ID manquant pour la modification");
                    }
                    doc.setId(idStr);                              // ✅ String
                    success = manager.modifierDocument(doc);
                }

                if (success) {
                    String msg = "Document " + ("ajouter".equalsIgnoreCase(action) ? "ajouté" : "modifié")
                            + " avec succès.";
                    handleGetWithMessage(exchange, professeurId, institutionId, "success", msg);
                } else {
                    throw new DocumentManager.DocumentException("Échec de l'opération");
                }

            } else if ("supprimer".equalsIgnoreCase(action)) {
                // ✅ id en String (UUID)
                String idStr = params.get("id");
                if (idStr == null || idStr.isBlank()) {
                    throw new DocumentManager.DocumentException("ID manquant pour la suppression");
                }
                boolean success = manager.supprimerDocument(idStr, institutionId, professeurId);
                if (success) {
                    handleGetWithMessage(exchange, professeurId, institutionId, "success",
                            "Document supprimé avec succès.");
                } else {
                    throw new DocumentManager.DocumentException("Échec de la suppression");
                }
            } else {
                handleGetWithMessage(exchange, professeurId, institutionId, "error", "Action inconnue.");
            }
        } catch (DocumentManager.DocumentException e) {
            LOGGER.log(Level.SEVERE, "Erreur DocumentException", e);
            handleGetWithMessage(exchange, professeurId, institutionId, "error", "Erreur: " + e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            handleGetWithMessage(exchange, professeurId, institutionId, "error",
                    "Erreur base de données: " + e.getMessage());
        }
    }

    private void handleGetWithMessage(HttpExchange exchange, String professeurId, String institutionId,
                                      String msgType, String msg) throws IOException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            DocumentManager manager = new DocumentManager(conn);
            List<DocumentProfesseur> documents = manager.listerDocuments(institutionId, professeurId);

            ProfesseurData profData = new ProfesseurData(conn);
            List<Professeur.Creneau> creneaux = profData.getCreneauxParProfesseur(professeurId, institutionId);
            List<String> matieres = getMatieres(creneaux);
            List<String> classesDispo = getClasses(creneaux);
            List<String> periodesDispo = getPeriodes(creneaux);

            AnneeAcademiqueData anneeData = new AnneeAcademiqueData(conn);
            List<AnneeAcademique> annees = anneeData.listByInstitution(institutionId);

            PeriodeData periodeData = new PeriodeData(conn);
            List<Periode> periodes = periodeData.listByInstitution(institutionId);

            String anneeActive = "";
            String periodeActive = "";
            if (!creneaux.isEmpty()) {
                Professeur.Creneau first = creneaux.get(0);
                anneeActive = first.getAnneeAcademique() != null ? first.getAnneeAcademique() : "";
                periodeActive = first.getPeriode() != null ? first.getPeriode() : "";
            }

            String html = UIProfesseurDocuments.render(
                professeurId,
                documents,
                matieres,
                classesDispo,
                periodesDispo,
                "",
                "",
                "",
                institutionId,
                annees,
                periodes,
                anneeActive,
                periodeActive,
                msgType,
                msg
            );
            ResponseUtil.sendHtml(exchange, 200, html);
        } catch (DocumentManager.DocumentException e) {
            LOGGER.log(Level.SEVERE, "Erreur DocumentException", e);
            String html = UIProfesseurDocuments.renderError("Erreur: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL", e);
            String html = UIProfesseurDocuments.renderError("Erreur base de données: " + e.getMessage());
            ResponseUtil.sendHtml(exchange, 500, html);
        }
    }

    // ==================== UTILITAIRES ====================
    private List<String> getMatieres(List<Professeur.Creneau> creneaux) {
        Map<String, String> map = new HashMap<>();
        for (Professeur.Creneau c : creneaux) {
            if (c.getCodeCours() != null && !c.getCodeCours().isBlank()) {
                map.put(c.getCodeCours(), c.getCodeCours());
            }
        }
        return new ArrayList<>(map.keySet());
    }

    private List<String> getClasses(List<Professeur.Creneau> creneaux) {
        Map<String, String> map = new HashMap<>();
        for (Professeur.Creneau c : creneaux) {
            if (c.getClasse() != null && !c.getClasse().isBlank()) {
                map.put(c.getClasse(), c.getClasse());
            }
        }
        return new ArrayList<>(map.keySet());
    }

    private List<String> getPeriodes(List<Professeur.Creneau> creneaux) {
        Map<String, String> map = new HashMap<>();
        for (Professeur.Creneau c : creneaux) {
            if (c.getPeriode() != null && !c.getPeriode().isBlank()) {
                map.put(c.getPeriode(), c.getPeriode());
            }
        }
        return new ArrayList<>(map.keySet());
    }

    // ==================== SESSION ====================
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
            String[] parts = cookie.trim().split("=");
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
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) { /* ignore */ }
            }
        }
        return params;
    }
}