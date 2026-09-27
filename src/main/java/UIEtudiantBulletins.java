import java.time.Year;
import java.util.List;

public class UIEtudiantBulletins {

    public static String render(List<String> annees,
                                List<String> periodes,
                                String anneeSel,
                                List<String> periodesSel,
                                String error,
                                String success) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Mes Bulletins</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append(renderStyles());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"container\">");

        // En-tête
        sb.append(renderHeader());

        // Messages
        if (error != null && !error.isEmpty()) {
            sb.append("<div class=\"message error\"><i class=\"fas fa-exclamation-triangle\"></i> ").append(echapperHtml(error)).append("</div>");
        }
        if (success != null && !success.isEmpty()) {
            sb.append("<div class=\"message success\"><i class=\"fas fa-check-circle\"></i> ").append(echapperHtml(success)).append("</div>");
        }

        // ================= FILTRAGE : Année académique =================
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"filters\">");
        sb.append("<div class=\"form-group\">");
        sb.append("<label for=\"annee\"><i class=\"fas fa-calendar\"></i> Année académique</label>");
        sb.append("<select id=\"annee\" class=\"form-control\" onchange=\"loadPeriodes()\">");
        sb.append("<option value=\"\">-- Sélectionner --</option>");
        for (String a : annees) {
            String selected = a.equals(anneeSel) ? "selected" : "";
            sb.append("<option value=\"").append(echapperHtml(a)).append("\" ").append(selected).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("</select>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");

        // ================= PÉRIODES (cases à cocher) =================
        sb.append("<div class=\"card\" style=\"margin-top:20px;\">");
        sb.append("<div id=\"periodesContainer\">");
        if (anneeSel != null && !anneeSel.isEmpty()) {
            sb.append("<label class=\"fw-semibold\"><i class=\"fas fa-clock\"></i> Périodes disponibles</label>");
            sb.append("<div class=\"periodes-checkboxes\">");
            for (String p : periodes) {
                boolean checked = periodesSel != null && periodesSel.contains(p);
                sb.append("<label class=\"checkbox-label\">");
                sb.append("<input type=\"checkbox\" name=\"periodes\" value=\"").append(echapperHtml(p)).append("\" ").append(checked ? "checked" : "").append("> ");
                sb.append(echapperHtml(p));
                sb.append("</label>");
            }
            sb.append("</div>");
        } else {
            sb.append("<p class=\"text-muted\">Sélectionnez d'abord une année pour voir les périodes.</p>");
        }
        sb.append("</div>");
        sb.append("</div>");

        // ================= BOUTON GÉNÉRER =================
        sb.append("<div class=\"text-center mt-4\">");
        sb.append("<button id=\"btnGenerer\" class=\"btn-generate\" disabled><i class=\"fas fa-file-pdf\"></i> Générer mon bulletin multi-sessions</button>");
        sb.append("</div>");

        // ================= INFOS =================
        if (anneeSel != null && !anneeSel.isEmpty() && periodesSel != null && !periodesSel.isEmpty()) {
            sb.append("<div class=\"info-box\">");
            sb.append("<p><i class=\"fas fa-info-circle\"></i> Vous allez générer un bulletin pour l'année <strong>").append(echapperHtml(anneeSel)).append("</strong> et les périodes : <strong>").append(String.join(", ", periodesSel)).append("</strong>.</p>");
            sb.append("<p style=\"font-size:13px; color:#64748b;\">Le PDF sera téléchargé automatiquement.</p>");
            sb.append("</div>");
        } else {
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fas fa-file-pdf\"></i>");
            sb.append("<p>Sélectionnez une année, choisissez une ou plusieurs périodes, puis cliquez sur \"Générer\".</p>");
            sb.append("</div>");
        }

        sb.append("<div class=\"footer\">© ").append(Year.now().getValue()).append(" M-TECH Academy - Tous droits réservés</div>");
        sb.append("</div>");

        // ================= JAVASCRIPT =================
        sb.append("""
                  <script>
                      function loadPeriodes() {
                          const annee = document.getElementById('annee').value;
                          const container = document.getElementById('periodesContainer');
                          const btn = document.getElementById('btnGenerer');
                          if (!annee) {
                              container.innerHTML = '<p class="text-muted">S\u00e9lectionnez d\\'abord une ann\u00e9e pour voir les p\u00e9riodes.</p>';
                              btn.disabled = true;
                              return;
                          }
                          // Charger les p\u00e9riodes via AJAX
                          fetch('/etudiant/bulletins/api/periodes?annee=' + encodeURIComponent(annee))
                              .then(res => res.json())
                              .then(data => {
                                  if (data.success) {
                                      let html = '<label class="fw-semibold"><i class="fas fa-clock"></i> P\u00e9riodes disponibles</label>';
                                      html += '<div class="periodes-checkboxes">';
                                      data.data.forEach(p => {
                                          html += '<label class="checkbox-label">';
                                          html += '<input type="checkbox" name="periodes" value="' + escapeHtml(p) + '"> ';
                                          html += escapeHtml(p);
                                          html += '</label>';
                                      });
                                      html += '</div>';
                                      container.innerHTML = html;
                                      // Ajouter les \u00e9couteurs pour activer le bouton
                                      document.querySelectorAll('input[name="periodes"]').forEach(cb => {
                                          cb.addEventListener('change', verifierSelection);
                                      });
                                      verifierSelection();
                                  } else {
                                      container.innerHTML = '<p class="text-danger">Erreur chargement p\u00e9riodes</p>';
                                  }
                              })
                              .catch(err => {
                                  console.error(err);
                                  container.innerHTML = '<p class="text-danger">Erreur r\u00e9seau</p>';
                              });
                      }
                  
                      function verifierSelection() {
                          const checkboxes = document.querySelectorAll('input[name="periodes"]:checked');
                          const btn = document.getElementById('btnGenerer');
                          btn.disabled = checkboxes.length === 0;
                      }
                  
                      function escapeHtml(str) {
                          if (!str) return '';
                          return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
                      }
                  
                      document.getElementById('btnGenerer').addEventListener('click', function() {
                          const annee = document.getElementById('annee').value;
                          const checkboxes = document.querySelectorAll('input[name="periodes"]:checked');
                          const periodes = Array.from(checkboxes).map(cb => cb.value);
                          if (!annee || periodes.length === 0) {
                              alert('Veuillez s\u00e9lectionner une ann\u00e9e et au moins une p\u00e9riode.');
                              return;
                          }
                          // Envoi en POST
                          const formData = new URLSearchParams();
                          formData.append('annee', annee);
                          periodes.forEach(p => formData.append('periodes', p));
                  
                          fetch('/etudiant/bulletins', {
                              method: 'POST',
                              headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                              body: formData
                          })
                          .then(response => {
                              if (response.ok) {
                                  return response.blob();
                              } else {
                                  return response.text().then(text => { throw new Error(text); });
                              }
                          })
                          .then(blob => {
                              const url = window.URL.createObjectURL(blob);
                              const a = document.createElement('a');
                              a.href = url;
                              a.download = 'BULLETIN_MULTI_' + annee.replace('/','-') + '.pdf';
                              document.body.appendChild(a);
                              a.click();
                              a.remove();
                              window.URL.revokeObjectURL(url);
                          })
                          .catch(err => {
                              alert('Erreur : ' + err.message);
                          });
                      });
                  
                      // Initialisation
                      window.onload = function() {
                          const anneeSel = document.getElementById('annee').value;
                          if (anneeSel) {
                              loadPeriodes();
                          }
                      };
                  </script>
                  <style>.periodes-checkboxes { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 8px; }.checkbox-label { display: flex; align-items: center; gap: 6px; background: #f8fafc; padding: 6px 14px; border-radius: 20px; border: 1px solid #e2e8f0; cursor: pointer; font-weight: 500; }.checkbox-label input { accent-color: #2563eb; width: 16px; height: 16px; }.btn-generate { background: #16a34a; color: white; border: none; padding: 12px 32px; border-radius: 40px; font-weight: 600; font-size: 16px; transition: 0.2s; }.btn-generate:hover:not(:disabled) { background: #15803d; transform: translateY(-2px); box-shadow: 0 6px 20px rgba(22,163,74,0.3); }.btn-generate:disabled { opacity: 0.5; cursor: not-allowed; }</style>""" // Ajout de styles supplémentaires pour les checkboxes
        );

        sb.append("</body></html>");
        return sb.toString();
    }

    public static String renderError(String message) {
    StringBuilder sb = new StringBuilder();
    sb.append("<!DOCTYPE html>");
    sb.append("<html>");
    sb.append("<head><meta charset=\"UTF-8\"><title>Erreur</title>");
    sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600&display=swap\" rel=\"stylesheet\">");
    sb.append("<style>");
    sb.append("*{margin:0;padding:0;box-sizing:border-box;font-family:'Plus Jakarta Sans',sans-serif;}");
    sb.append("body{background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;}");
    sb.append(".error-container{background:white;padding:40px;border-radius:16px;text-align:center;max-width:500px;box-shadow:0 10px 25px -5px rgba(0,0,0,0.1);}");
    sb.append(".error-container i{font-size:64px;color:#dc2626;margin-bottom:20px;}");
    sb.append(".error-container h1{color:#1e293b;margin-bottom:10px;}");
    sb.append(".error-container p{color:#64748b;}");
    sb.append(".error-container .btn{display:inline-block;margin-top:20px;padding:10px 24px;background:#1e40af;color:white;text-decoration:none;border-radius:8px;font-weight:600;}");
    sb.append(".error-container .btn:hover{background:#1e3a8a;}");
    sb.append("</style>");
    sb.append("</head>");
    sb.append("<body>");
    sb.append("<div class=\"error-container\">");
    sb.append("<i class=\"fas fa-exclamation-triangle\"></i>");
    sb.append("<h1>Erreur</h1>");
    sb.append("<p>").append(echapperHtml(message)).append("</p>");
    sb.append("<a href=\"/etudiant/dashboard\" class=\"btn\"><i class=\"fas fa-arrow-left\"></i> Retour au tableau de bord</a>");
    sb.append("</div>");
    sb.append("</body>");
    sb.append("</html>");
    return sb.toString();
}

    private static String renderStyles() {
        // ... inchangé (mais on peut adapter)
        return """
        *{margin:0;padding:0;box-sizing:border-box;}
        html, body { width: 100%; min-height: 100vh; background: linear-gradient(135deg, #f0f4f8 0%, #d9e2ec 100%); font-family:'Plus Jakarta Sans',sans-serif; }
        body{padding:0;display:flex;flex-direction:column;}
        .container{width:100%;min-height:100vh;margin:0;background:rgba(255,255,255,0.85);backdrop-filter:blur(10px);border-radius:0;box-shadow:none;border:none;display:flex;flex-direction:column;overflow:hidden;}
        .header{background:linear-gradient(135deg,#1e293b,#0f172a);color:white;padding:30px 40px 24px;border-radius:0;text-align:center;margin-bottom:0;position:relative;overflow:hidden;width:100%;}
        .header h1{font-size:24px;font-weight:700;margin-top:8px;position:relative;z-index:1;}
        .header h1 i{color:#3b82f6;margin-right:10px;}
        .header-nav{display:flex;justify-content:center;gap:15px;margin-top:12px;flex-wrap:wrap;position:relative;z-index:1;}
        .header-nav a{color:#94a3b8;text-decoration:none;font-size:13px;transition:0.2s;padding:6px 14px;border-radius:20px;background:rgba(255,255,255,0.08);}
        .header-nav a:hover{color:white;background:rgba(255,255,255,0.16);}
        .header-nav a.active{color:#f5cd79;background:rgba(255,255,255,0.16);font-weight:600;}
        .message{padding:12px 18px;border-radius:8px;margin:20px 40px 0 40px;}
        .message.success{background:#d1fae5;color:#16a34a;border-left:4px solid #16a34a;}
        .message.error{background:#fee2e2;color:#dc2626;border-left:4px solid #dc2626;}
        .card{background:white;border-radius:0;padding:40px;border:none;box-shadow:none;width:100%;max-width:1600px;margin:0 auto;}
        .filters{display:flex;gap:20px;flex-wrap:wrap;align-items:end;}
        .filters .form-group{flex:1;min-width:180px;}
        .filters label{display:block;font-weight:600;color:#475569;margin-bottom:4px;font-size:13px;}
        .filters select{width:100%;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;background:white;}
        .info-box{background:#eff6ff;padding:20px 30px;border-radius:12px;margin:20px auto;max-width:1600px;width:calc(100% - 80px);border-left:4px solid #3b82f6;}
        .info-box p{margin:5px 0;}
        .empty-state{text-align:center;padding:40px;background:white;border-radius:12px;border:1px solid #e2e8f0;margin:20px auto;max-width:1600px;width:calc(100% - 80px);}
        .empty-state i{font-size:48px;color:#94a3b8;margin-bottom:12px;}
        .empty-state p{color:#64748b;font-size:16px;}
        .footer{margin-top:auto;text-align:center;color:#64748b;font-size:13px;border-top:1px solid rgba(0,0,0,0.06);padding:25px 0 15px;width:100%;}
        @media (max-width:768px){.filters{flex-direction:column;}.message, .info-box, .empty-state{width:calc(100% - 40px);margin-left:20px;margin-right:20px;}}
        """;
    }

    private static String renderHeader() {
        return """
        <div class="header">
            <h1><i class="fas fa-file-pdf"></i> Mes Bulletins Multi-Sessions</h1>
            <div class="header-nav">
                <a href="/etudiant/dashboard"><i class="fas fa-home"></i> Dashboard</a>
                <a href="/etudiant/profil"><i class="fas fa-user"></i> Mon Profil</a>
                <a href="/etudiant/notes"><i class="fas fa-book"></i> Mes Notes</a>
                <a href="/etudiant/bulletins" class="active"><i class="fas fa-file-pdf"></i> Bulletins</a>
                <a href="/etudiant/messages"><i class="fas fa-envelope"></i> Messages</a>
                <a href="/logout"><i class="fas fa-sign-out-alt"></i> Déconnexion</a>
            </div>
        </div>
        """;
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}