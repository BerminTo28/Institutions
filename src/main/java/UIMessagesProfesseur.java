import java.time.Year;
import java.util.List;

public class UIMessagesProfesseur {

    // ============================================================
    // LISTE DES MESSAGES
    // ============================================================
    public static String renderListe(String professeurId, List<Message> messages, String vue,
                                     int nonLus, String institutionId,
                                     String msgType, String msg) {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Messages - Professeur");
        sb.append("<body>");
        sb.append("<div class='container'>");

        sb.append(renderHeader(professeurId, "PROFESSEUR", nonLus));
        appendNotification(sb, msgType, msg);

        // Onglets
        sb.append("<div class='tabs'>");
        sb.append("<a href='/professeur/messages?vue=reception' class='tab ")
          .append("reception".equals(vue) ? "active" : "")
          .append("'><i class='fas fa-inbox'></i> Boîte de réception")
          .append(nonLus > 0 ? " <span class='badge'>" + nonLus + "</span>" : "")
          .append("</a>");
        sb.append("<a href='/professeur/messages?vue=envoyes' class='tab ")
          .append("envoyes".equals(vue) ? "active" : "")
          .append("'><i class='fas fa-paper-plane'></i> Envoyés</a>");
        sb.append("<a href='/professeur/messages?vue=nouveau' class='tab ")
          .append("nouveau".equals(vue) ? "active" : "")
          .append("'><i class='fas fa-plus-circle'></i> Nouveau</a>");
        sb.append("</div>");

        // Liste
        sb.append("<div class='messages-list'>");
        if (messages == null || messages.isEmpty()) {
            sb.append("<div class='empty-state'>")
              .append("<i class='fas fa-envelope-open'></i>")
              .append("<p>Aucun message</p>")
              .append("</div>");
        } else {
            for (Message m : messages) {
                appendMessageItem(sb, m);
            }
        }
        sb.append("</div>");

        appendFooter(sb);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static void appendMessageItem(StringBuilder sb, Message m) {
        String from = m.getNomExpediteur() != null && !m.getNomExpediteur().isBlank()
                ? m.getNomExpediteur()
                : m.getExpediteurId();
        String date = m.getDateEnvoi() != null ? m.getDateEnvoi().toString() : "";
        String classe = m.isLu() ? "message-item read" : "message-item unread";

        sb.append("<a href='/professeur/messages?action=lire&id=")
          .append(echapperUrl(m.getId()))
          .append("' class='").append(classe).append("'>");

        sb.append("<div class='msg-avatar'><i class='fas fa-user-circle'></i></div>");
        sb.append("<div class='msg-content'>");
        sb.append("<div class='msg-header'>");
        sb.append("<span class='msg-from'>").append(echapperHtml(from)).append("</span>");
        sb.append("<span class='msg-date'>").append(echapperHtml(date)).append("</span>");
        sb.append("</div>");
        sb.append("<div class='msg-sujet'>").append(echapperHtml(m.getSujet())).append("</div>");

        String contenu = m.getContenu() != null ? m.getContenu() : "";
        String preview = contenu.length() > 100 ? contenu.substring(0, 100) + "..." : contenu;
        sb.append("<div class='msg-preview'>").append(echapperHtml(preview)).append("</div>");
        sb.append("</div>");
        sb.append("</a>");
    }

    // ============================================================
    // LECTURE D'UN MESSAGE
    // ============================================================
    public static String renderLire(String professeurId, Message message, String institutionId) {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Lire le message");
        sb.append("<body>");
        sb.append("<div class='container'>");

        sb.append(renderHeader(professeurId, "PROFESSEUR", 0));

        sb.append("<div class='message-detail'>");
        sb.append("<div class='md-header'>");
        String expediteur = message.getNomExpediteur() != null && !message.getNomExpediteur().isBlank()
                ? message.getNomExpediteur()
                : message.getExpediteurId();
        sb.append("<div class='md-from'><i class='fas fa-user'></i> De: <strong>")
          .append(echapperHtml(expediteur)).append("</strong></div>");
        sb.append("<div class='md-date'><i class='fas fa-calendar-alt'></i> ")
          .append(echapperHtml(message.getDateEnvoi() != null ? message.getDateEnvoi().toString() : ""))
          .append("</div>");
        sb.append("</div>");

        sb.append("<div class='md-sujet'><h2>")
          .append(echapperHtml(message.getSujet())).append("</h2></div>");

        // ✅ Contenu échappé PUIS sauts de ligne convertis (sécurité XSS)
        String contenu = message.getContenu() != null ? message.getContenu() : "";
        String contenuFormatted = echapperHtml(contenu).replace("\n", "<br>");
        sb.append("<div class='md-contenu'>").append(contenuFormatted).append("</div>");

        sb.append("<div class='md-actions'>");
        // ✅ Répondre avec le vrai UUID du message parent
        sb.append("<a href='/professeur/messages?vue=nouveau&reponse_a=")
          .append(echapperUrl(message.getId()))
          .append("' class='btn btn-primary'><i class='fas fa-reply'></i> Répondre</a>");

        sb.append("<a href='/professeur/messages?action=delete&id=")
          .append(echapperUrl(message.getId()))
          .append("' class='btn btn-danger' onclick='return confirm(\"Supprimer ce message ?\")'>")
          .append("<i class='fas fa-trash'></i> Supprimer</a>");

        sb.append("<a href='/professeur/messages' class='btn btn-secondary'>")
          .append("<i class='fas fa-arrow-left'></i> Retour</a>");
        sb.append("</div>");
        sb.append("</div>");

        appendFooter(sb);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ============================================================
    // NOUVEAU MESSAGE
    // ============================================================
    public static String renderNouveau(String professeurId, List<Etudiant> etudiants,
                                       List<Professeur> professeurs, String institutionId,
                                       String msgType, String msg) {
        return renderNouveau(professeurId, etudiants, professeurs, institutionId,
                msgType, msg, null);
    }

    /**
     * ✅ Version avec reponseA (UUID du message auquel on répond).
     */
    public static String renderNouveau(String professeurId, List<Etudiant> etudiants,
                                       List<Professeur> professeurs, String institutionId,
                                       String msgType, String msg, String reponseA) {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Nouveau message");
        sb.append("<body>");
        sb.append("<div class='container'>");

        sb.append(renderHeader(professeurId, "PROFESSEUR", 0));
        appendNotification(sb, msgType, msg);

        sb.append("<div class='message-form'>");
        sb.append("<form method='POST' action='/professeur/messages'>");
        sb.append("<input type='hidden' name='action' value='envoyer'>");

        // ✅ Champ caché pour reponse_a (si réponse)
        if (reponseA != null && !reponseA.isBlank()) {
            sb.append("<input type='hidden' name='reponse_a' value='")
              .append(echapperHtml(reponseA)).append("'>");
            sb.append("<div class='reply-banner'>")
              .append("<i class='fas fa-reply'></i> Réponse à un message")
              .append("</div>");
        }

        // Destinataire
        sb.append("<div class='form-group'>")
          .append("<label>Destinataire</label>")
          .append("<select name='destinataire_id' required>")
          .append("<option value=''>-- Choisir --</option>");

        // Étudiants
        sb.append("<optgroup label='Étudiants'>");
        if (etudiants != null) {
            for (Etudiant e : etudiants) {
                sb.append("<option value='")
                  .append(echapperHtml(e.getNumeroIdentifiantEtudiant()))
                  .append("' data-type='ETUDIANT'>")
                  .append(echapperHtml(e.getNomComplet()))
                  .append("</option>");
            }
        }
        sb.append("</optgroup>");

        // Professeurs (sauf soi-même)
        sb.append("<optgroup label='Professeurs'>");
        if (professeurs != null) {
            for (Professeur p : professeurs) {
                if (!p.getNumeroIdentifiantProfesseur().equals(professeurId)) {
                    sb.append("<option value='")
                      .append(echapperHtml(p.getNumeroIdentifiantProfesseur()))
                      .append("' data-type='PROFESSEUR'>")
                      .append(echapperHtml(p.getNomComplet()))
                      .append("</option>");
                }
            }
        }
        sb.append("</optgroup>");

        sb.append("</select>")
          .append("<input type='hidden' name='destinataire_type' id='destinataire_type' value=''>")
          .append("</div>");

        // Sujet
        sb.append("<div class='form-group'><label>Sujet</label>")
          .append("<input type='text' name='sujet' required></div>");

        // Contenu
        sb.append("<div class='form-group'><label>Message</label>")
          .append("<textarea name='contenu' rows='8' required></textarea></div>");

        // Actions
        sb.append("<div class='form-actions'>")
          .append("<button type='submit' class='btn btn-primary'>")
          .append("<i class='fas fa-paper-plane'></i> Envoyer</button>")
          .append("<a href='/professeur/messages' class='btn btn-secondary'>")
          .append("<i class='fas fa-times'></i> Annuler</a>")
          .append("</div>");

        sb.append("</form>");
        sb.append("</div>");

        appendFooter(sb);
        sb.append("</div>");

        // Script de synchronisation destinataire_type
        sb.append("<script>");
        sb.append("document.querySelector('select[name=\"destinataire_id\"]').addEventListener('change', function() {");
        sb.append("  var selected = this.options[this.selectedIndex];");
        sb.append("  if (selected && selected.dataset.type) {");
        sb.append("    document.getElementById('destinataire_type').value = selected.dataset.type;");
        sb.append("  }");
        sb.append("});");
        sb.append("</script>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ============================================================
    // PAGE D'ERREUR
    // ============================================================
    public static String renderError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='fr'><head><meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
          .append("<title>Erreur</title>")
          .append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>")
          .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap' rel='stylesheet'>")
          .append("<style>")
          .append("* { margin:0; padding:0; box-sizing:border-box; font-family:'Plus Jakarta Sans',sans-serif; }")
          .append("html, body { width:100%; min-height:100vh; background:linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); display:flex; justify-content:center; align-items:center; }")
          .append(".error-container { background:white; padding:40px; border-radius:16px; text-align:center; max-width:500px; box-shadow:0 10px 25px rgba(0,0,0,0.05); }")
          .append(".error-container i { font-size:56px; color:#dc2626; margin-bottom:20px; }")
          .append(".error-container h1 { color:#1e293b; margin-bottom:10px; font-size:24px; }")
          .append(".error-container p { color:#64748b; margin-bottom:20px; }")
          .append(".error-container .btn { display:inline-block; padding:10px 24px; background:#1e40af; color:white; text-decoration:none; border-radius:8px; font-weight:600; }")
          .append(".error-container .btn:hover { background:#1e3a8a; }")
          .append("</style></head><body>")
          .append("<div class='error-container'>")
          .append("<i class='fas fa-exclamation-triangle'></i>")
          .append("<h1>Erreur</h1>")
          .append("<p>").append(echapperHtml(message)).append("</p>")
          .append("<a href='/professeur/messages' class='btn'><i class='fas fa-arrow-left'></i> Retour</a>")
          .append("</div></body></html>");
        return sb.toString();
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static void appendHead(StringBuilder sb, String title) {
        sb.append("<!DOCTYPE html><html lang='fr'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
          .append("<title>").append(echapperHtml(title)).append("</title>")
          .append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>")
          .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap' rel='stylesheet'>")
          .append("<style>").append(renderStyles()).append("</style>")
          .append("</head>");
    }

    private static void appendNotification(StringBuilder sb, String msgType, String msg) {
        if (msg == null || msgType == null) return;
        String icon = "success".equals(msgType) ? "check-circle" : "exclamation-triangle";
        sb.append("<div class='message ").append(msgType).append("'>")
          .append("<i class='fas fa-").append(icon).append("'></i> ")
          .append(echapperHtml(msg))
          .append("</div>");
    }

    private static void appendFooter(StringBuilder sb) {
        sb.append("<div class='footer'>© ")
          .append(Year.now().getValue())
          .append(" M-TECH Academy</div>");
    }

    private static String renderHeader(String userId, String type, int nonLus) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='header'>");
        sb.append("<h1><i class='fas fa-envelope-open-text'></i> Messagerie</h1>");
        sb.append("<div class='header-actions'>");

        // Nom d'utilisateur
        sb.append("<span style='color:#94a3b8; font-size:14px; margin-right:15px;'>")
          .append("<i class='fas fa-user'></i> ")
          .append(echapperHtml(type.equals("PROFESSEUR") ? "Professeur" : type))
          .append(": ").append(echapperHtml(userId))
          .append("</span>");

        // Liens
        sb.append("<a href='/dashboard/professeur'><i class='fas fa-home'></i> Dashboard</a>");
        sb.append("<a href='/professeur/messages'><i class='fas fa-envelope'></i> Messages")
          .append(nonLus > 0 ? " <span class='badge'>" + nonLus + "</span>" : "")
          .append("</a>");
        sb.append("<a href='/logout'><i class='fas fa-sign-out-alt'></i> Déconnexion</a>");

        sb.append("</div></div>");
        return sb.toString();
    }

    private static String renderStyles() {
        return """
        * { margin:0; padding:0; box-sizing:border-box; }
        html, body { width:100%; min-height:100vh; background:linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans',sans-serif; }
        body { display:flex; flex-direction:column; align-items:stretch; }
        .container { width:100%; min-height:100vh; background:rgba(255,255,255,0.85); backdrop-filter:blur(10px); display:flex; flex-direction:column; overflow-x:hidden; }
        
        .header { background:linear-gradient(135deg,#1e293b,#0f172a); color:white; padding:30px 40px 24px; display:flex; justify-content:space-between; align-items:center; width:100%; }
        .header h1 { font-size:24px; font-weight:700; display:flex; align-items:center; gap:10px; }
        .header-actions { display:flex; gap:15px; align-items:center; flex-wrap:wrap; }
        .header-actions a { color:#94a3b8; text-decoration:none; font-size:13px; transition:0.2s; padding:6px 14px; border-radius:20px; background:rgba(255,255,255,0.08); display:flex; align-items:center; gap:6px; font-weight:500; }
        .header-actions a:hover { color:white; background:rgba(255,255,255,0.16); }
        .header-actions .badge { background:#dc2626; color:white; border-radius:50%; padding:2px 7px; font-size:11px; margin-left:4px; font-weight:700; }
        
        .tabs { display:flex; justify-content:center; gap:10px; margin:30px auto 25px auto; border-bottom:2px solid #e2e8f0; width:calc(100% - 80px); max-width:1600px; }
        .tab { padding:10px 22px; cursor:pointer; font-weight:600; color:#64748b; border-radius:8px 8px 0 0; text-decoration:none; transition:all 0.2s ease; display:flex; align-items:center; gap:8px; font-size:14px; }
        .tab:hover { color:#1e293b; background:rgba(255,255,255,0.7); }
        .tab.active { color:#1e40af; border-bottom:3px solid #1e40af; background:white; box-shadow:0 -2px 6px rgba(0,0,0,0.02); }
        .tab .badge { background:#dc2626; color:white; border-radius:50%; padding:1px 6px; font-size:11px; font-weight:700; }
        
        .messages-list { display:flex; flex-direction:column; gap:15px; width:calc(100% - 80px); max-width:1600px; margin:0 auto; }
        .message-item { background:white; padding:20px 25px; border-radius:12px; border-left:4px solid #cbd5e1; text-decoration:none; color:#0f172a; display:flex; align-items:center; gap:16px; transition:all 0.2s ease; box-shadow:0 2px 5px rgba(0,0,0,0.02); border:1px solid #e2e8f0; }
        .message-item:hover { box-shadow:0 6px 15px rgba(0,0,0,0.06); transform:translateY(-2px); border-color:#3b82f6; }
        .message-item.unread { border-left-color:#1e40af; background:#f8fafc; }
        .message-item.unread .msg-sujet { font-weight:700; color:#1e293b; }
        .msg-avatar { font-size:36px; color:#cbd5e1; }
        .msg-content { flex:1; min-width:0; }
        .msg-header { display:flex; justify-content:space-between; font-size:13px; color:#64748b; margin-bottom:4px; }
        .msg-from { font-weight:600; color:#334155; }
        .msg-sujet { font-size:15px; margin-bottom:4px; color:#475569; }
        .msg-preview { font-size:13px; color:#94a3b8; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        
        .empty-state { text-align:center; padding:50px 20px; background:white; border-radius:12px; box-shadow:0 2px 5px rgba(0,0,0,0.02); color:#64748b; border:1px solid #e2e8f0; width:calc(100% - 80px); max-width:1600px; margin:0 auto; }
        .empty-state i { font-size:48px; color:#cbd5e1; margin-bottom:12px; }
        
        .footer { margin-top:auto; text-align:center; color:#64748b; font-size:13px; border-top:1px solid rgba(0,0,0,0.06); padding:25px 0 15px; width:100%; }
        
        .message { padding:14px 18px; border-radius:10px; margin:20px auto 0 auto; font-size:14px; display:flex; align-items:center; gap:10px; box-shadow:0 2px 5px rgba(0,0,0,0.02); max-width:1600px; width:calc(100% - 80px); }
        .message.success { background:#d1fae5; color:#065f46; border-left:4px solid #059669; }
        .message.error { background:#fee2e2; color:#991b1b; border-left:4px solid #dc2626; }
        
        .message-detail { background:white; border-radius:12px; padding:30px 40px; box-shadow:0 2px 5px rgba(0,0,0,0.02); border:1px solid #e2e8f0; width:calc(100% - 80px); max-width:1600px; margin:30px auto 0 auto; }
        .md-header { display:flex; justify-content:space-between; font-size:14px; color:#64748b; margin-bottom:20px; border-bottom:1px solid #f1f5f9; padding-bottom:15px; }
        .md-sujet { margin-bottom:20px; }
        .md-sujet h2 { font-size:22px; color:#1e293b; font-weight:700; }
        .md-contenu { font-size:15px; line-height:1.8; color:#334155; white-space:pre-wrap; background:#f8fafc; padding:20px; border-radius:12px; border:1px solid #f1f5f9; }
        .md-actions { display:flex; gap:10px; margin-top:25px; }
        
        .btn { padding:10px 20px; border:none; border-radius:8px; cursor:pointer; text-decoration:none; display:inline-flex; align-items:center; gap:8px; font-weight:600; font-size:14px; transition:0.2s; }
        .btn-primary { background:#1e40af; color:white; }
        .btn-primary:hover { background:#1e3a8a; }
        .btn-danger { background:#dc2626; color:white; }
        .btn-danger:hover { background:#b91c1c; }
        .btn-secondary { background:#64748b; color:white; }
        .btn-secondary:hover { background:#475569; }
        
        .message-form { background:white; border-radius:12px; padding:30px 40px; box-shadow:0 2px 5px rgba(0,0,0,0.02); border:1px solid #e2e8f0; width:calc(100% - 80px); max-width:1600px; margin:30px auto 0 auto; }
        .form-group { margin-bottom:20px; }
        .form-group label { display:block; font-weight:600; color:#334155; margin-bottom:8px; font-size:14px; }
        .form-group input, .form-group select, .form-group textarea { width:100%; padding:12px; border:1px solid #cbd5e1; border-radius:8px; font-size:14px; font-family:inherit; color:#1e293b; background:#fff; transition:border-color 0.2s; }
        .form-group input:focus, .form-group select:focus, .form-group textarea:focus { outline:none; border-color:#1e40af; box-shadow:0 0 0 3px rgba(30,64,175,0.1); }
        .form-group textarea { resize:vertical; }
        .form-actions { display:flex; gap:10px; margin-top:25px; }
        
        .reply-banner { background:#eff6ff; color:#1e40af; padding:10px 15px; border-radius:8px; margin-bottom:20px; font-weight:600; font-size:14px; border-left:4px solid #1e40af; }
        
        @media (max-width:768px) { 
            .header { flex-direction:column; align-items:stretch; gap:15px; padding:20px; } 
            .header-actions { flex-wrap:wrap; justify-content:center; } 
            .tabs { flex-direction:column; gap:5px; border-bottom:none; width:calc(100% - 40px); margin-left:20px; margin-right:20px; }
            .tab { border-radius:8px; justify-content:center; }
            .tab.active { border-bottom:none; background:#1e40af; color:white; }
            .tab.active .badge { background:white; color:#1e40af; }
            .message-item, .empty-state, .message-detail, .message-form, .message { width:calc(100% - 40px); margin-left:20px; margin-right:20px; padding:20px; }
        }
        """;
    }

    // ============================================================
    // ÉCHAPPEMENT
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * ✅ Échappe une valeur pour l'utiliser dans une URL.
     */
    private static String echapperUrl(String input) {
        if (input == null) return "";
        try {
            return java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return input;
        }
    }
}