import java.time.Year;
import java.util.List;


public class UIParametres {

    // ============================================================
    // PAGE DE LOGIN (re-authentification avant accès aux paramètres)
    // ============================================================
    public static String rendrePageLogin(String messageErreur) {
        // Noms aléatoires par chargement → aucun pattern reconnaissable par le navigateur
        String randMail = "u_" + Long.toHexString(System.nanoTime());
        String randPass = "p_" + Long.toHexString(System.nanoTime() + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">");
        sb.append("<meta http-equiv=\"Cache-Control\" content=\"no-store, no-cache, must-revalidate\">");
        sb.append("<meta http-equiv=\"Pragma\" content=\"no-cache\">");
        sb.append("<title>Authentification - Paramètres</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI',sans-serif; }");
        sb.append("body { background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); display:flex; justify-content:center; align-items:center; min-height:100vh; min-height:100dvh; padding:20px; }");
        sb.append(".login-card { background:white; padding:40px; border-radius:16px; box-shadow:0 20px 60px rgba(0,0,0,0.3); width:420px; max-width:100%; text-align:center; animation: slideUp 0.4s ease-out; }");
        sb.append("@keyframes slideUp { from { opacity:0; transform: translateY(20px); } to { opacity:1; transform: translateY(0); } }");
        sb.append(".login-card .logo-icon { width:70px; height:70px; margin:0 auto 16px; border-radius:50%; background: linear-gradient(135deg, #1e40af, #3b82f6); display:flex; align-items:center; justify-content:center; box-shadow: 0 8px 20px rgba(30,64,175,0.3); }");
        sb.append(".login-card .logo-icon i { color:white; font-size:30px; }");
        sb.append(".login-card h1 { color:#1e293b; margin-bottom:8px; font-size:22px; font-weight:700; }");
        sb.append(".login-card p { color:#64748b; margin-bottom:25px; font-size:13px; line-height:1.5; }");
        sb.append(".form-group { margin-bottom:15px; text-align:center; }");
        sb.append(".form-group label { display:block; margin-bottom:6px; font-weight:600; color:#475569; font-size:13px; text-align:center; }");
        sb.append(".form-group input { width:100%; padding:12px 16px; border:1.5px solid #cbd5e1; border-radius:10px; font-size:14px; box-sizing:border-box; text-align:center; transition: 0.2s; background:#f8fafc; }");
        sb.append(".form-group input:focus { outline:none; border-color:#1e40af; background:white; box-shadow: 0 0 0 4px rgba(30,64,175,0.1); }");
        // Masquage CSS du mot de passe tant qu'il est en type="text"
        sb.append(".pwd-masked { -webkit-text-security: disc; -moz-text-security: disc; text-security: disc; }");
        sb.append(".btn { width:100%; padding:13px; background: linear-gradient(135deg, #1e40af, #2563eb); color:white; border:none; border-radius:10px; font-weight:700; cursor:pointer; font-size:15px; transition:0.2s; margin-top:8px; box-shadow: 0 4px 12px rgba(30,64,175,0.25); }");
        sb.append(".btn:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(30,64,175,0.35); }");
        sb.append(".btn:active { transform: translateY(0); }");
        sb.append(".error { color:#991b1b; background:#fee2e2; border:1px solid #fecaca; padding:12px; border-radius:10px; margin-bottom:18px; text-align:center; font-size:13px; display:flex; align-items:center; justify-content:center; gap:8px; }");
        sb.append(".back-link { display:block; text-align:center; margin-top:18px; color:#64748b; text-decoration:none; font-size:13px; transition: 0.2s; }");
        sb.append(".back-link:hover { color:#1e40af; }");
        sb.append(".security-note { margin-top:20px; padding:10px; background:#f1f5f9; border-radius:8px; font-size:11px; color:#64748b; display:flex; align-items:center; justify-content:center; gap:6px; }");
        sb.append(".security-note i { color:#1e40af; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"login-card\">");

        sb.append("<div class=\"logo-icon\"><i class=\"fas fa-cog\"></i></div>");
        sb.append("<h1>Paramètres</h1>");
        sb.append("<p>Authentification administrateur requise</p>");

        if (messageErreur != null && !messageErreur.isEmpty()) {
            sb.append("<div class=\"error\"><i class=\"fas fa-exclamation-triangle\"></i> ")
              .append(echapperHtml(messageErreur)).append("</div>");
        }

        sb.append("<form method=\"POST\" action=\"/admin/parametres\" ")
          .append("autocomplete=\"off\" id=\"loginForm\" novalidate>");

        sb.append("<input type=\"hidden\" name=\"action\" value=\"login\">");

        // Champs leurres cachés (absorbent l'autofill du navigateur)
        sb.append("<input type=\"text\" name=\"decoy_username\" id=\"decoy_username\" ")
          .append("style=\"position:absolute; left:-9999px; width:1px; height:1px; opacity:0;\" ")
          .append("autocomplete=\"username\" tabindex=\"-1\" aria-hidden=\"true\">");
        sb.append("<input type=\"password\" name=\"decoy_password\" id=\"decoy_password\" ")
          .append("style=\"position:absolute; left:-9999px; width:1px; height:1px; opacity:0;\" ")
          .append("autocomplete=\"current-password\" tabindex=\"-1\" aria-hidden=\"true\">");

        // Champ email visible : readonly au chargement, nom aléatoire
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"vis_mail\">Email principal</label>");
        sb.append("<input type=\"text\" ")
          .append("id=\"vis_mail\" ")
          .append("name=\"").append(randMail).append("\" ")
          .append("placeholder=\"votre.email@exemple.com\" ")
          .append("required ")
          .append("readonly ")
          .append("autocomplete=\"off\" ")
          .append("autocorrect=\"off\" ")
          .append("autocapitalize=\"off\" ")
          .append("spellcheck=\"false\" ")
          .append("inputmode=\"email\" ")
          .append("data-lpignore=\"true\" ")
          .append("data-form-type=\"other\">");
        sb.append("</div>");

        // Champ mot de passe visible : type="text" + masquage CSS, readonly au chargement
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"vis_pass\">Mot de passe</label>");
        sb.append("<input type=\"text\" ")
          .append("id=\"vis_pass\" ")
          .append("name=\"").append(randPass).append("\" ")
          .append("class=\"pwd-masked\" ")
          .append("placeholder=\"••••••••\" ")
          .append("required ")
          .append("readonly ")
          .append("autocomplete=\"off\" ")
          .append("data-lpignore=\"true\" ")
          .append("data-form-type=\"other\">");
        sb.append("</div>");

        // Champs cachés qui seront réellement soumis au serveur
        sb.append("<input type=\"hidden\" name=\"mail_primaire\" id=\"real_mail\">");
        sb.append("<input type=\"hidden\" name=\"mot_de_passe\" id=\"real_pass\">");

        sb.append("<button type=\"submit\" class=\"btn\"><i class=\"fas fa-sign-in-alt\"></i> Accéder</button>");
        sb.append("</form>");

        sb.append("<div class=\"security-note\"><i class=\"fas fa-shield-alt\"></i> Connexion sécurisée — aucune donnée mémorisée</div>");
        sb.append("<a href=\"/dashboard\" class=\"back-link\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");
        sb.append("</div>");

        // ============================================================
        // SCRIPT — nettoyage initial UNE SEULE FOIS, jamais après interaction
        // ============================================================
        sb.append("<script>");
        sb.append("(function() {");
        sb.append("  'use strict';");

        sb.append("  var visMail = document.getElementById('vis_mail');");
        sb.append("  var visPass = document.getElementById('vis_pass');");
        sb.append("  var realMail = document.getElementById('real_mail');");
        sb.append("  var realPass = document.getElementById('real_pass');");
        sb.append("  var form = document.getElementById('loginForm');");

        // Dès que l'utilisateur touche un champ, on ne le nettoie plus JAMAIS
        sb.append("  var mailTouched = false;");
        sb.append("  var passTouched = false;");

        sb.append("  function nettoyageInitial() {");
        sb.append("    if (visMail && !mailTouched) { visMail.value = ''; visMail.setAttribute('readonly', true); }");
        sb.append("    if (visPass && !passTouched) { visPass.value = ''; visPass.setAttribute('readonly', true); }");
        sb.append("  }");

        // EMAIL
        sb.append("  if (visMail) {");
        sb.append("    visMail.addEventListener('focus', function() {");
        sb.append("      if (!mailTouched) {");
        sb.append("        this.value = '';");
        sb.append("        this.removeAttribute('readonly');");
        sb.append("        mailTouched = true;");
        sb.append("      }");
        sb.append("    });");
        sb.append("    visMail.addEventListener('input', function() {");
        sb.append("      realMail.value = this.value;");
        sb.append("    });");
        sb.append("  }");

        // MOT DE PASSE : conversion type text → password au premier focus
        sb.append("  if (visPass) {");
        sb.append("    visPass.addEventListener('focus', function() {");
        sb.append("      if (!passTouched) {");
        sb.append("        this.value = '';");
        sb.append("        this.removeAttribute('readonly');");
        sb.append("        if (this.type !== 'password') this.type = 'password';");
        sb.append("        passTouched = true;");
        sb.append("      }");
        sb.append("    });");
        sb.append("    visPass.addEventListener('input', function() {");
        sb.append("      realPass.value = this.value;");
        sb.append("    });");
        sb.append("  }");

        // Copie finale au submit + validation
        sb.append("  if (form) {");
        sb.append("    form.addEventListener('submit', function(e) {");
        sb.append("      realMail.value = visMail ? visMail.value.trim() : '';");
        sb.append("      realPass.value = visPass ? visPass.value : '';");
        sb.append("      if (!realMail.value || !realPass.value) {");
        sb.append("        e.preventDefault();");
        sb.append("        alert('Veuillez remplir tous les champs.');");
        sb.append("        return false;");
        sb.append("      }");
        sb.append("    });");
        sb.append("  }");

        // Nettoyage initial — uniquement si l'utilisateur n'a pas touché
        sb.append("  if (document.readyState === 'loading') {");
        sb.append("    document.addEventListener('DOMContentLoaded', nettoyageInitial);");
        sb.append("  } else {");
        sb.append("    nettoyageInitial();");
        sb.append("  }");
        sb.append("  setTimeout(function() { if (!mailTouched && !passTouched) nettoyageInitial(); }, 100);");
        sb.append("  setTimeout(function() { if (!mailTouched && !passTouched) nettoyageInitial(); }, 500);");

        sb.append("})();");
        sb.append("</script>");

        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }


    public static String rendreDashboard(String nomInstitution,
                                          String logoPath,
                                          List<ParametreModule> modules,
                                          String messageType,
                                          String message) {

        if (nomInstitution == null) nomInstitution = "Institution";
        if (logoPath == null || logoPath.isBlank()) {
            logoPath = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E%3Crect width='100' height='100' fill='%231e40af'/%3E%3Ctext x='50' y='60' font-size='40' text-anchor='middle' fill='white' font-family='sans-serif'%3E🏛%3C/text%3E%3C/svg%3E";
        }
        if (modules == null) modules = List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">");
        sb.append("<title>Paramètres - ").append(echapperHtml(nomInstitution)).append("</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<style>");
        sb.append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Segoe UI', sans-serif; }");
        sb.append("body { background:#f4f6f9; padding:20px; display:flex; justify-content:center; align-items:flex-start; min-height:100vh; min-height:100dvh; }");
        sb.append(".container { max-width:1400px; width:100%; margin:0 auto; }");
        sb.append(".header { background:white; padding:25px 30px; border-radius:12px; box-shadow:0 2px 4px rgba(0,0,0,0.04); text-align:center; margin-bottom:25px; }");
        sb.append(".header-logo { width:70px; height:70px; border-radius:50%; object-fit:cover; border:3px solid #1e40af; display:inline-block; margin-bottom:10px; }");
        sb.append(".header h1 { font-size:24px; color:#1e293b; margin:5px 0; }");
        sb.append(".header h1 i { color:#1e40af; margin-right:10px; }");
        sb.append(".header .subtitle { color:#64748b; font-size:14px; margin-bottom:12px; }");
        sb.append(".message-container { margin-bottom:20px; }");
        sb.append(".message { padding:12px 20px; border-radius:8px; margin-bottom:10px; font-size:14px; display:flex; align-items:center; gap:10px; }");
        sb.append(".message.success { background:#d1fae5; color:#065f46; border:1px solid #a7f3d0; }");
        sb.append(".message.error { background:#fee2e2; color:#991b1b; border:1px solid #fecaca; }");
        sb.append(".modules-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:24px; margin-bottom:30px; }");
        sb.append(".module-card { background:white; border-radius:16px; padding:28px 16px; text-align:center; text-decoration:none; color:#0f172a; border:1px solid #e2e8f0; transition:0.25s ease; display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:140px; }");
        sb.append(".module-card:hover { transform:translateY(-6px); box-shadow:0 12px 24px rgba(0,0,0,0.08); border-color:#1e40af; background:#ffffff; }");
        sb.append(".module-card.disabled { opacity:0.55; cursor:not-allowed; }");
        sb.append(".module-card.disabled:hover { transform:none; box-shadow:none; border-color:#e2e8f0; }");
        sb.append(".module-card .icon { font-size:38px; color:#1e40af; margin-bottom:12px; display:block; }");
        sb.append(".module-card .name { font-weight:700; font-size:16px; margin-bottom:4px; }");
        sb.append(".module-card .desc { font-size:12px; color:#94a3b8; line-height:1.4; }");
        sb.append(".module-card .badge { font-size:10px; background:#fef3c7; color:#92400e; padding:2px 8px; border-radius:10px; margin-top:8px; font-weight:600; }");
        sb.append("@media (max-width: 992px) { .modules-grid { grid-template-columns: repeat(2, 1fr); } }");
        sb.append("@media (max-width: 600px) { .modules-grid { grid-template-columns: repeat(1, 1fr); } }");
        sb.append(".footer { text-align:center; margin-top:30px; }");
        sb.append(".btn-logout { background:#e74c3c; color:white; padding:12px 30px; border:none; border-radius:6px; font-size:16px; font-weight:bold; cursor:pointer; text-decoration:none; display:inline-block; transition:0.2s; }");
        sb.append(".btn-logout:hover { background:#c0392b; }");
        sb.append(".footer .copyright { color:#94a3b8; font-size:13px; display:block; margin-top:10px; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // Header
        sb.append("<div class=\"header\">");
        sb.append("<img src=\"").append(echapperHtml(logoPath)).append("\" alt=\"Logo\" class=\"header-logo\" onerror=\"this.style.display='none'\">");
        sb.append("<h1><i class=\"fas fa-cog\"></i> Paramètres</h1>");
        sb.append("<div class=\"subtitle\">").append(echapperHtml(nomInstitution)).append("</div>");
        sb.append("</div>");

        // Message
        if (message != null && !message.isEmpty() && messageType != null) {
            String icon = "success".equals(messageType) ? "fa-check-circle" : "fa-exclamation-circle";
            String cssClass = "success".equals(messageType) ? "message success" : "message error";
            sb.append("<div class=\"message-container\">");
            sb.append("<div class=\"").append(cssClass).append("\"><i class=\"fas ").append(icon).append("\"></i> ")
              .append(echapperHtml(message)).append("</div>");
            sb.append("</div>");
        }

        // Grille
        sb.append("<div class=\"modules-grid\">");
        for (ParametreModule mod : modules) {
            if (mod.disponible()) {
                sb.append("<a href=\"").append(echapperHtml(mod.lien())).append("\" class=\"module-card\">");
            } else {
                sb.append("<div class=\"module-card disabled\" onclick=\"alert('Module ")
                  .append(echapperJs(mod.nom())).append(" sera bientôt disponible.')\">");
            }
            sb.append("<span class=\"icon\"><i class=\"").append(echapperHtml(mod.icone())).append("\"></i></span>");
            sb.append("<div class=\"name\">").append(echapperHtml(mod.nom())).append("</div>");
            sb.append("<div class=\"desc\">").append(echapperHtml(mod.description())).append("</div>");
            if (!mod.disponible()) {
                sb.append("<div class=\"badge\">Bientôt disponible</div>");
            }
            sb.append(mod.disponible() ? "</a>" : "</div>");
        }
        sb.append("</div>");

        // Footer
        sb.append("<div class=\"footer\">");
        sb.append("<a href=\"/admin/parametres?action=logout\" class=\"btn-logout\"><i class=\"fas fa-sign-out-alt\"></i> Déconnexion</a>");
        sb.append("<span class=\"copyright\">© ").append(Year.now().getValue()).append(" M-TECH - Paramètres de l'institution</span>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ============================================================
    // RECORD — DTO UI d'un module Paramètres
    // ============================================================
    /**
     * Représentation d'un module pour l'affichage Paramètres.
     * <p>
     * Simple DTO UI : aucune logique, aucune décision.
     * Le handler qui appelle {@link #rendreDashboard} prépare la liste.
     *
     * @param nom         libellé affiché
     * @param icone       classe FontAwesome (ex: "fas fa-user")
     * @param description courte description sous le titre
     * @param lien        URL cible (si {@code disponible()} = true)
     * @param disponible  true si le module est actif, false sinon
     */
    public record ParametreModule(String nom, String icone, String description,
                                   String lien, boolean disponible) {
    }

    // ============================================================
    // ÉCHAPPEMENTS
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String echapperJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}