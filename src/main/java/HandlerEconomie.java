import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP pour le module ÉCONOMIE.
 *
 * RÈGLES DE SÉCURITÉ :
 *  1. Les endpoints PUBLICS (/api/health, /api/ping) ne nécessitent AUCUNE session.
 *     → évite la boucle de redirections login.
 *  2. Aucune exception ne remonte au framework (try/catch(Throwable) global).
 *  3. Une réponse HTTP est envoyée EXACTEMENT UNE FOIS (helpers safe*).
 *  4. Les messages d'erreur client ne contiennent JAMAIS de détails internes.
 *  5. Les logs sont corrélés via un reqId (8 chars UUID).
 *  6. Les endpoints API inconnus renvoient un 404 JSON propre.
 */
public class HandlerEconomie implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(HandlerEconomie.class.getName());

    /** SimpleDateFormat n'est PAS thread-safe : on en crée un par appel. */
    private static SimpleDateFormat newDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd");
    }

    /** Endpoints PUBLICS (aucune session requise). */
    private static final Set<String> PUBLIC_PATHS = new HashSet<>(List.of(
            "/api/health",
            "/api/ping",
            "/api/version",
            "/favicon.ico"
    ));

    // ============================================================
    // POINT D'ENTRÉE — filet de sécurité global
    // ============================================================
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String reqId = UUID.randomUUID().toString().substring(0, 8);
        final String path;
        final String method;

        try {
            path = exchange.getRequestURI().getPath();
            method = exchange.getRequestMethod();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + reqId + "] ⚠️ URI illisible", e);
            safeError(exchange, 400, "Requête invalide");
            return;
        }

        try {
            LOGGER.fine(() -> "[" + reqId + "] 🔵 " + method + " " + path);

            // ✅ 0) Endpoints PUBLICS (health-check, ping) — AVANT toute vérification de session
            if (handlePublicPath(exchange, path, reqId)) {
                return;
            }

            // 1) Session
            String institutionId = getInstitutionIdFromSession(exchange);
            String utilisateurId = getUtilisateurIdFromSession(exchange);

            if (isBlank(institutionId) || isBlank(utilisateurId)) {
                LOGGER.warning(() -> "[" + reqId + "] ⛔ Session invalide");
                if (path.startsWith("/api/")) {
                    safeErrorJson(exchange, 401, "Session invalide ou expirée");
                } else {
                    safeRedirect(exchange, "/login?error=Session+expirée");
                }
                return;
            }

            // 2) Permissions
            AccesAdmin acces = chargerPermissions(utilisateurId, institutionId);
            if (acces == null) {
                LOGGER.warning(() -> "[" + reqId + "] ⛔ Permissions non trouvées pour " + utilisateurId);
                if (path.startsWith("/api/")) {
                    safeErrorJson(exchange, 403, "Permissions non trouvées");
                } else {
                    safeRedirect(exchange, "/dashboard?error=Permissions+non+trouvées");
                }
                return;
            }

            if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.READ)) {
                LOGGER.warning(() -> "[" + reqId + "] ⛔ READ refusé sur ECONOMIE");
                if (path.startsWith("/api/")) {
                    safeErrorJson(exchange, 403, "Permission READ refusée");
                } else {
                    safeRedirect(exchange, "/dashboard?error=Accès+non+autorisé");
                }
                return;
            }

            // 3) Service métier (peut lever → catch global plus bas)
            GestionFinanciereService service = new GestionFinanciereService(institutionId);

            // 4) Routage
            if (path.startsWith("/api/")) {
                handleApi(exchange, path, method, service, acces, reqId);
                return;
            }

            if (path.startsWith("/admin/economie")) {
                boolean canCreate = acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.CREATE);
                boolean canUpdate = acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.UPDATE);
                boolean canDelete = acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.DELETE);

                String html = renderPage(path, service, canCreate, canUpdate, canDelete);
                safeHtml(exchange, 200, html);
                return;
            }

            safeError(exchange, 404, "Not found");

        } catch (SQLException t) {
            // ⛔ DERNIER FILET : aucune exception ne doit remonter
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Exception non catchée dans handle()", t);
            try {
                if (path != null && path.startsWith("/api/")) {
                    safeErrorJson(exchange, 500, "Erreur interne");
                } else {
                    safeErrorPage(exchange, "Erreur technique. Veuillez réessayer.");
                }
            } catch (Throwable ignored) {
                // Si même les helpers échouent, le framework renverra un 500 par défaut.
            }
        } finally {
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // ENDPOINTS PUBLICS
    // ============================================================
    /**
     * @return true si le chemin a été traité (endpoint public).
     */
    private boolean handlePublicPath(HttpExchange exchange, String path, String reqId) {
        if (!PUBLIC_PATHS.contains(path)) return false;

        try {
            if ("/api/health".equals(path)) {
                Map<String, Object> data = new HashMap<>();
                data.put("status", "UP");
                data.put("timestamp", System.currentTimeMillis());
                data.put("service", "economie");
                safeJson(exchange, data, 200);
                return true;
            }
            if ("/api/ping".equals(path)) {
                safeText(exchange, 200, "pong");
                return true;
            }
            if ("/api/version".equals(path)) {
                Map<String, Object> data = new HashMap<>();
                data.put("version", "1.0");
                safeJson(exchange, data, 200);
                return true;
            }
            if ("/favicon.ico".equals(path)) {
                try { exchange.sendResponseHeaders(204, -1); }
                catch (IOException ignored) {}
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + reqId + "] ⚠️ Erreur endpoint public " + path, e);
        }
        return true; // ne jamais laisser retomber dans le flux normal
    }

    // ============================================================
    // API
    // ============================================================
    private void handleApi(HttpExchange exchange, String path, String method,
                            GestionFinanciereService service, AccesAdmin acces,
                            String reqId) throws SQLException {
        try {
            LOGGER.fine(() -> "[" + reqId + "] 🔵 API " + method + " " + path);

            // ---------- FILTRES EN CASCADE ----------
            if (path.equals("/api/economie/annees")) {
                sendSuccessData(exchange, service.getAnneesAcademiques());
                return;
            }

            if (path.equals("/api/economie/periodes")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                if (isBlank(annee)) {
                    safeErrorJson(exchange, 400, "Paramètre 'annee' requis");
                    return;
                }
                sendSuccessData(exchange, service.getPeriodesParAnnee(annee));
                return;
            }

            if (path.equals("/api/economie/classes")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                String periode = params.get("periode");
                sendSuccessData(exchange, service.getClassesFiltrees(annee, periode));
                return;
            }

            if (path.equals("/api/economie/etudiants")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                String periode = params.get("periode");
                String classe = params.get("classe");
                if (isBlank(classe)) {
                    safeErrorJson(exchange, 400, "Paramètre 'classe' requis");
                    return;
                }
                sendSuccessData(exchange, service.getEtudiantsFiltres(annee, periode, classe));
                return;
            }

            if (path.equals("/api/economie/filtres/classes")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String table = params.getOrDefault("table", "depenses");
                String annee = params.get("annee");
                String mois = params.get("mois");
                sendSuccessData(exchange, service.getClassesUtilisees(table, annee, mois));
                return;
            }

            if (path.equals("/api/economie/filtres/mois")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String table = params.getOrDefault("table", "depenses");
                String annee = params.get("annee");
                String classe = params.get("classe");
                sendSuccessData(exchange, service.getMoisDisponibles(table, annee, classe));
                return;
            }

            // ---------- BUDGET ----------
            if (path.equals("/api/economie/budget/filtre")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                String mois = params.get("mois");
                Map<String, Object> data = service.getBudgetFiltre(annee, mois);
                sendSuccess(exchange, data);
                return;
            }

            if (path.equals("/api/economie/budget")) {
                Map<String, Object> data = new HashMap<>();
                data.put("totalRecettesInternes", service.getTotalRecettesInternes());
                data.put("totalRecettesExternes", service.getTotalRecettesExternes());
                data.put("totalDepenses", service.getTotalDepenses());
                data.put("totalSalaires", service.getTotalSalaires());
                data.put("solde", service.getSolde());
                sendSuccess(exchange, data);
                return;
            }

            // ---------- ÉTUDIANTS FINANCES ----------
            if (path.equals("/api/economie/etudiants-finances")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String annee = params.get("annee");
                String periode = params.get("periode");
                String classe = params.get("classe");
                String etudiantId = params.get("etudiantId");
                List<Map<String, Object>> rows = service.getEtudiantsAvecFinances(
                        annee, periode, classe, etudiantId);
                sendSuccessData(exchange, rows);
                return;
            }

            if (path.equals("/api/economie/etudiant/infos")) {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String etudiantId = params.get("etudiantId");
                String annee = params.get("annee");
                String classe = params.get("classe");
                if (isBlank(etudiantId)) {
                    safeErrorJson(exchange, 400, "Paramètre 'etudiantId' requis");
                    return;
                }
                Map<String, Object> infos = service.getInfosFinancieresEtudiant(
                        etudiantId, annee, classe);
                sendSuccess(exchange, infos);
                return;
            }

            // ---------- PAIEMENTS ÉTUDIANTS ----------
            if (path.equals("/api/economie/paiements")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String annee = params.get("annee");
                    String mois = params.get("mois");
                    String classe = params.get("classe");
                    List<Map<String, Object>> paiements =
                            service.getPaiementsEtudiants(annee, mois, classe);
                    Map<String, Object> data = new HashMap<>();
                    data.put("paiements", paiements);
                    data.put("total", service.getTotalRecettesInternes());
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.CREATE)) {
                        safeErrorJson(exchange, 403, "Permission CREATE refusée pour les paiements");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String etudiantId = params.get("etudiantId");
                    String moyen = params.get("moyen");
                    if (isBlank(etudiantId)) {
                        safeErrorJson(exchange, 400, "L'identifiant étudiant est requis");
                        return;
                    }
                    if (isBlank(moyen)) {
                        safeErrorJson(exchange, 400, "Le moyen de paiement est requis");
                        return;
                    }
                    double montant;
                    try {
                        montant = parseDoubleSafely(params.get("montant"), "montant");
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    String reference = params.getOrDefault("reference", "");
                    if (reference.isBlank()) {
                        reference = "PAY-" + System.currentTimeMillis();
                    }
                    service.enregistrerPaiementEtudiant(etudiantId, montant, moyen, reference);
                    safeSuccessJson(exchange, "Paiement enregistré");
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            if (path.equals("/api/economie/paiement/supprimer") && "POST".equals(method)) {
                if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.DELETE)) {
                    safeErrorJson(exchange, 403, "Permission DELETE refusée");
                    return;
                }
                Map<String, String> params = getBodyParams(exchange);
                String id = params.get("id");
                if (isBlank(id)) {
                    safeErrorJson(exchange, 400, "L'identifiant du paiement est requis");
                    return;
                }
                service.supprimerPaiementEtudiant(id);
                safeSuccessJson(exchange, "Paiement supprimé");
                return;
            }

            // ---------- FRAIS SCOLAIRES ----------
            if (path.equals("/api/economie/frais")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String classe = params.get("classe");
                    if (isBlank(classe)) {
                        safeErrorJson(exchange, 400, "Paramètre 'classe' requis");
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("classe", classe);
                    data.put("montant", service.getFraisScolaire(classe));
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.UPDATE)) {
                        safeErrorJson(exchange, 403, "Permission UPDATE refusée");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String classe = params.get("classe");
                    if (isBlank(classe)) {
                        safeErrorJson(exchange, 400, "Paramètre 'classe' requis");
                        return;
                    }
                    double montant;
                    try {
                        montant = parseDoubleSafely(params.get("montant"), "montant");
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    service.setFraisScolaire(classe, montant);
                    safeSuccessJson(exchange, "Frais enregistrés pour la classe " + classe);
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            // ---------- MODALITÉS ----------
            if (path.equals("/api/economie/modalites")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String classe = params.get("classe");
                    if (isBlank(classe)) {
                        safeErrorJson(exchange, 400, "Paramètre 'classe' requis");
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("classe", classe);
                    data.put("modalites", service.getModalites(classe));
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.UPDATE)) {
                        safeErrorJson(exchange, 403, "Permission UPDATE refusée");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String classe = params.get("classe");
                    if (isBlank(classe)) {
                        safeErrorJson(exchange, 400, "Paramètre 'classe' requis");
                        return;
                    }
                    String modalitesJson = params.get("modalites");
                    if (isBlank(modalitesJson)) {
                        safeErrorJson(exchange, 400, "Paramètre 'modalites' requis");
                        return;
                    }
                    Map<Integer, Double> modalites;
                    try {
                        modalites = parseModalitesJson(modalitesJson);
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    service.saveModalites(classe, modalites);
                    safeSuccessJson(exchange, "Modalités enregistrées pour " + classe);
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            // ---------- RECETTES EXTERNES ----------
            if (path.equals("/api/economie/recettes-externes")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String annee = params.get("annee");
                    String mois = params.get("mois");
                    List<Map<String, Object>> recettes = service.getRecettesExternes(annee, mois);
                    Map<String, Object> data = new HashMap<>();
                    data.put("recettes", recettes);
                    data.put("total", service.getTotalRecettesExternes());
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.CREATE)) {
                        safeErrorJson(exchange, 403, "Permission CREATE refusée");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String categorie = params.get("categorie");
                    String dateStr = params.get("date");
                    if (isBlank(categorie)) {
                        safeErrorJson(exchange, 400, "La catégorie est requise");
                        return;
                    }
                    if (isBlank(dateStr)) {
                        safeErrorJson(exchange, 400, "La date est requise");
                        return;
                    }
                    double montant;
                    try {
                        montant = parseDoubleSafely(params.get("montant"), "montant");
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    Date date;
                    try {
                        date = newDateFormat().parse(dateStr);
                    } catch (java.text.ParseException e) {
                        safeErrorJson(exchange, 400, "Format de date invalide (yyyy-MM-dd)");
                        return;
                    }
                    service.ajouterRecetteExterne(
                            categorie, date, montant,
                            params.get("donateur"), params.get("receveur"),
                            params.get("motif"));
                    safeSuccessJson(exchange, "Recette externe ajoutée");
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            if (path.equals("/api/economie/recette-externe/supprimer") && "POST".equals(method)) {
                if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.DELETE)) {
                    safeErrorJson(exchange, 403, "Permission DELETE refusée");
                    return;
                }
                Map<String, String> params = getBodyParams(exchange);
                String id = params.get("id");
                if (isBlank(id)) {
                    safeErrorJson(exchange, 400, "L'identifiant est requis");
                    return;
                }
                service.supprimerRecetteExterne(id);
                safeSuccessJson(exchange, "Recette externe supprimée");
                return;
            }

            // ---------- DÉPENSES ----------
            if (path.equals("/api/economie/depenses")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String annee = params.get("annee");
                    String mois = params.get("mois");
                    String classe = params.get("classe");
                    List<Depenses> depenses = service.getDepenses(annee, mois, classe);
                    Map<String, Object> data = new HashMap<>();
                    data.put("depenses", depenses);
                    data.put("total", service.getTotalDepenses() - service.getTotalSalaires());
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.CREATE)) {
                        safeErrorJson(exchange, 403, "Permission CREATE refusée");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String executant = params.get("executant");
                    String motif = params.get("motif");
                    String dateStr = params.get("date");
                    if (isBlank(executant)) {
                        safeErrorJson(exchange, 400, "L'exécutant est requis");
                        return;
                    }
                    if (isBlank(motif)) {
                        safeErrorJson(exchange, 400, "Le motif est requis");
                        return;
                    }
                    if (isBlank(dateStr)) {
                        safeErrorJson(exchange, 400, "La date est requise");
                        return;
                    }
                    double montant;
                    try {
                        montant = parseDoubleSafely(params.get("montant"), "montant");
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    Date date;
                    try {
                        date = newDateFormat().parse(dateStr);
                    } catch (java.text.ParseException e) {
                        safeErrorJson(exchange, 400, "Format de date invalide (yyyy-MM-dd)");
                        return;
                    }
                    Depenses d = new Depenses();
                    d.setInstitutionId(getInstitutionIdFromSession(exchange));
                    d.setResponsableDepense(executant);
                    d.setMotif(motif);
                    d.setMontantDepense(montant);
                    d.setDateDepense(date);
                    d.setNomDepense(params.get("article"));
                    d.setFournisseur(params.get("fournisseur"));
                    d.setAnneeAcademique(params.get("anneeAcademique"));
                    d.setMois(params.get("mois"));
                    d.setMatricule(params.get("matricule"));
                    d.setClasses(splitCsv(params.get("classes")));
                    service.ajouterDepense(d);
                    safeSuccessJson(exchange, "Dépense ajoutée");
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            if (path.equals("/api/economie/depense/supprimer") && "POST".equals(method)) {
                if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.DELETE)) {
                    safeErrorJson(exchange, 403, "Permission DELETE refusée");
                    return;
                }
                Map<String, String> params = getBodyParams(exchange);
                String id = params.get("id");
                if (isBlank(id)) {
                    safeErrorJson(exchange, 400, "L'identifiant est requis");
                    return;
                }
                service.supprimerDepense(id);
                safeSuccessJson(exchange, "Dépense supprimée");
                return;
            }

            // ---------- SALAIRES ----------
            if (path.equals("/api/economie/employes")) {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    String annee = params.get("annee");
                    String mois = params.get("mois");
                    String classe = params.get("classe");
                    List<Employe> employes = service.getEmployes(annee, mois, classe);
                    Map<String, Object> data = new HashMap<>();
                    data.put("employes", employes);
                    data.put("total", service.getTotalSalaires());
                    sendSuccess(exchange, data);
                    return;
                }
                if ("POST".equals(method)) {
                    if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.CREATE)) {
                        safeErrorJson(exchange, 403, "Permission CREATE refusée");
                        return;
                    }
                    Map<String, String> params = getBodyParams(exchange);
                    String nom = params.get("nom");
                    String poste = params.get("poste");
                    if (isBlank(nom)) {
                        safeErrorJson(exchange, 400, "Le nom est requis");
                        return;
                    }
                    if (isBlank(poste)) {
                        safeErrorJson(exchange, 400, "Le poste est requis");
                        return;
                    }
                    double salaireBase, deductions;
                    try {
                        salaireBase = parseDoubleSafely(params.get("salaireBase"), "salaireBase");
                        deductions = parseDoubleSafely(params.get("deductions"), "deductions");
                    } catch (IllegalArgumentException e) {
                        safeErrorJson(exchange, 400, e.getMessage());
                        return;
                    }
                    Employe emp = new Employe();
                    emp.setInstitutionId(getInstitutionIdFromSession(exchange));
                    emp.setNom(nom);
                    emp.setPoste(poste);
                    emp.setSalaireBase(salaireBase);
                    emp.setDeductions(deductions);
                    emp.setAnneeAcademique(params.get("anneeAcademique"));
                    emp.setMois(params.get("mois"));
                    emp.setClasses(splitCsv(params.get("classes")));
                    service.ajouterEmploye(emp);
                    safeSuccessJson(exchange, "Employé ajouté");
                    return;
                }
                safeErrorJson(exchange, 405, "Méthode non autorisée");
                return;
            }

            if (path.equals("/api/economie/employe/supprimer") && "POST".equals(method)) {
                if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.DELETE)) {
                    safeErrorJson(exchange, 403, "Permission DELETE refusée");
                    return;
                }
                Map<String, String> params = getBodyParams(exchange);
                String id = params.get("id");
                if (isBlank(id)) {
                    safeErrorJson(exchange, 400, "L'identifiant est requis");
                    return;
                }
                service.supprimerEmploye(id);
                safeSuccessJson(exchange, "Employé supprimé");
                return;
            }

            if (path.equals("/api/economie/employe/modifier") && "POST".equals(method)) {
                if (!acces.hasPermission(AccesAdmin.Module.ECONOMIE, AccesAdmin.Action.UPDATE)) {
                    safeErrorJson(exchange, 403, "Permission UPDATE refusée");
                    return;
                }
                Map<String, String> params = getBodyParams(exchange);
                String id = params.get("id");
                String nom = params.get("nom");
                String poste = params.get("poste");
                if (isBlank(id)) {
                    safeErrorJson(exchange, 400, "L'identifiant est requis");
                    return;
                }
                if (isBlank(nom)) {
                    safeErrorJson(exchange, 400, "Le nom est requis");
                    return;
                }
                if (isBlank(poste)) {
                    safeErrorJson(exchange, 400, "Le poste est requis");
                    return;
                }
                double salaireBase, deductions;
                try {
                    salaireBase = parseDoubleSafely(params.get("salaireBase"), "salaireBase");
                    deductions = parseDoubleSafely(params.get("deductions"), "deductions");
                } catch (IllegalArgumentException e) {
                    safeErrorJson(exchange, 400, e.getMessage());
                    return;
                }
                service.modifierEmploye(id, nom, poste, salaireBase, deductions);
                safeSuccessJson(exchange, "Employé modifié");
                return;
            }

            // ---------- 404 API ----------
            safeErrorJson(exchange, 404, "API inconnue: " + path);

        } catch (IllegalArgumentException | SQLException e) {
            LOGGER.log(Level.WARNING,
                    "[" + reqId + "] ⚠️ Erreur métier sur " + path, e);
            safeErrorJson(exchange, 400, safeMessage(e));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "[" + reqId + "] ❌ Erreur API sur " + path, e);
            safeErrorJson(exchange, 500, "Erreur interne");
        }
    }

    // ============================================================
    // RENDU DES PAGES HTML
    // ============================================================
    private String renderPage(String path, GestionFinanciereService service,
                               boolean canCreate, boolean canUpdate, boolean canDelete)
            throws SQLException {
        if (path.endsWith("/budget") || path.equals("/admin/economie")
                || path.equals("/admin/economie/")) {
            return UIBudget.generateHTML(service, canCreate, canUpdate, canDelete);
        }
        if (path.endsWith("/recettes/internes")) {
            return UIRecettesInternes.generateHTML(service, canCreate, canUpdate, canDelete);
        }
        if (path.endsWith("/recettes/externes")) {
            return UIRecettesExternes.generateHTML(service, canCreate, canUpdate, canDelete);
        }
        if (path.endsWith("/depenses")) {
            return UIDepenses.generateHTML(service, canCreate, canUpdate, canDelete);
        }
        if (path.endsWith("/salaires")) {
            return UISalaires.generateHTML(service, canCreate, canUpdate, canDelete);
        }
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>404</title></head>"
                + "<body><h1>Page non trouvée</h1>"
                + "<a href='/admin/economie'>Retour</a></body></html>";
    }

    // ============================================================
    // CHARGEMENT DES PERMISSIONS
    // ============================================================
    private AccesAdmin chargerPermissions(String utilisateurId, String institutionId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            AccesAdminData dao = new AccesAdminData(conn);
            return dao.loadByUtilisateurId(utilisateurId, institutionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur chargement permissions", e);
            return new AccesAdmin();
        }
    }

    // ============================================================
    // SESSION
    // ============================================================
    private String getInstitutionIdFromSession(HttpExchange exchange) {
        try {
            HttpSession session = getSession(exchange);
            if (session == null) return null;
            String instId = (String) session.getAttribute("institutionId");
            if (isBlank(instId)) instId = (String) session.getAttribute("institution_id");
            return instId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur récupération institutionId: " + e.getMessage());
            return null;
        }
    }

    private String getUtilisateurIdFromSession(HttpExchange exchange) {
        try {
            HttpSession session = getSession(exchange);
            if (session == null) return null;
            String userId = (String) session.getAttribute("userId");
            if (isBlank(userId)) userId = (String) session.getAttribute("username");
            return userId;
        } catch (Exception e) {
            LOGGER.warning(() -> "Erreur récupération utilisateurId: " + e.getMessage());
            return null;
        }
    }

    private HttpSession getSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "SESSION_ID".equals(parts[0])) {
                return SessionManager.getSession(parts[1]);
            }
        }
        return null;
    }

    // ============================================================
    // UTILITAIRES
    // ============================================================
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return "Erreur";
        // Éviter les fuites : tronquer les messages trop longs
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }

    private List<String> splitCsv(String csv) {
        List<String> result = new ArrayList<>();
        if (isBlank(csv)) return result;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    private double parseDoubleSafely(String value, String fieldName)
            throws IllegalArgumentException {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Le champ '" + fieldName + "' est requis.");
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Le champ '" + fieldName + "' doit être un nombre valide.");
        }
    }

    private Map<String, String> getBodyParams(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(),
                                  StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (isBlank(query)) return params;
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

    private Map<Integer, Double> parseModalitesJson(String json)
            throws IllegalArgumentException {
        Map<Integer, Double> map = new TreeMap<>();
        if (isBlank(json)) return map;
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException(
                    "Le format des modalités doit être un objet JSON valide.");
        }
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return map;
        for (String pair : content.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            try {
                int key = Integer.parseInt(kv[0].trim().replace("\"", ""));
                String valueStr = kv[1].trim().replace("\"", "");
                if (valueStr.isBlank()) {
                    throw new IllegalArgumentException(
                            "La valeur du versement " + key + " ne peut être vide.");
                }
                map.put(key, Double.valueOf(valueStr));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Valeur invalide dans les modalités pour le versement " + kv[0]);
            }
        }
        return map;
    }

    // ============================================================
    // HTTP RESPONSES — versions SÛRES
    // ============================================================

    private void sendSuccess(HttpExchange exchange, Map<String, Object> data) {
        if (data == null) data = new HashMap<>();
        data.put("success", true);
        safeJson(exchange, data, 200);
    }

    private void sendSuccessData(HttpExchange exchange, Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        safeJson(exchange, resp, 200);
    }

    private void safeSuccessJson(HttpExchange exchange, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        safeJson(exchange, resp, 200);
    }

    private void safeErrorJson(HttpExchange exchange, int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("message", message != null ? message : "Erreur");
        safeJson(exchange, resp, code);
    }

    private void safeJson(HttpExchange exchange, Map<String, Object> data, int status) {
        if (data == null) data = new HashMap<>();
        try {
            if (exchange.getResponseCode() != -1) return;
            String json = new com.google.gson.Gson().toJson(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeJson : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeJson : erreur inattendue", e);
        }
    }

    private void safeHtml(HttpExchange exchange, int status, String html) {
        if (html == null) html = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeHtml : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeHtml : erreur inattendue", e);
        }
    }

    private void safeText(HttpExchange exchange, int status, String text) {
        if (text == null) text = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeText : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeText : erreur inattendue", e);
        }
    }

    private void safeError(HttpExchange exchange, int status, String message) {
        if (message == null) message = "";
        try {
            if (exchange.getResponseCode() != -1) return;
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeError : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeError : erreur inattendue", e);
        }
    }

    private void safeRedirect(HttpExchange exchange, String location) {
        if (location == null) location = "/admin/economie";
        try {
            if (exchange.getResponseCode() != -1) return;
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ safeRedirect : envoi impossible", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ safeRedirect : erreur inattendue", e);
        }
    }

    private void safeErrorPage(HttpExchange exchange, String message) {
        String safe = (message == null ? "" : message)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<title>Erreur</title></head><body>"
                + "<h1>⚠️ Erreur technique</h1><p>" + safe + "</p>"
                + "<a href='/admin/economie'>Retour</a></body></html>";
        safeHtml(exchange, 500, html);
    }
}