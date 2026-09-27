import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UIAppel {

    private static final Logger LOGGER = Logger.getLogger(UIAppel.class.getName());

    // ============================================================
    // CONFIGURATION TWILIO (ou autre fournisseur d'appels)
    // ============================================================
    private static String getTwilioAccountSid() {
        String v = System.getenv("TWILIO_ACCOUNT_SID");
        return (v != null && !v.isBlank()) ? v : "";
    }

    private static String getTwilioAuthToken() {
        String v = System.getenv("TWILIO_AUTH_TOKEN");
        return (v != null && !v.isBlank()) ? v : "";
    }

    private static String getTwilioFromNumber() {
        String v = System.getenv("TWILIO_FROM_NUMBER");
        return (v != null && !v.isBlank()) ? v : "";
    }

    private static String getTwilioTwiMLUrl() {
        String v = System.getenv("TWILIO_TWIML_URL");
        return (v != null && !v.isBlank()) ? v : "";
    }

    // ============================================================
    // MÉTHODE D'ENVOI RÉELLE (appelée par HandlerCommunication)
    // ============================================================
    public static boolean envoyer(Etudiant etudiant, String objet, String message) throws IOException, InterruptedException {
        if (etudiant == null) return false;

        String telephone = etudiant.getTelephone();
        if (telephone == null || telephone.isBlank()) {
            LOGGER.warning(() -> "⚠️ Appel impossible : téléphone manquant pour "
                    + etudiant.getNumeroIdentifiantEtudiant());
            return false;
        }

        String numero = normaliserNumero(telephone);
        if (numero.isBlank()) {
            LOGGER.warning(() -> "⚠️ Appel impossible : numéro invalide pour "
                    + etudiant.getNumeroIdentifiantEtudiant());
            return false;
        }

        String accountSid = getTwilioAccountSid();
        String authToken = getTwilioAuthToken();
        String fromNumber = getTwilioFromNumber();
        String twimlUrl = getTwilioTwiMLUrl();

        // ✅ Mode simulation si non configuré
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            LOGGER.warning(() -> "⚠️ Twilio non configuré — simulation d'appel pour " + numero);
            LOGGER.info(() -> "📞 [SIMULATION Appel] → " + numero
                    + " (" + etudiant.getNom() + " " + etudiant.getPrenom() + ")");
            return true;
        }

        try {
            // ✅ Endpoint Twilio
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Calls.json";

            // ✅ Paramètres POST
            StringBuilder postData = new StringBuilder();
            postData.append("To=").append(URLEncoder.encode(numero, StandardCharsets.UTF_8));
            postData.append("&From=").append(URLEncoder.encode(fromNumber, StandardCharsets.UTF_8));

            if (!twimlUrl.isBlank()) {
                // ✅ Utiliser un TwiML hébergé (recommandé)
                postData.append("&Url=").append(URLEncoder.encode(twimlUrl, StandardCharsets.UTF_8));
            } else {
                // ✅ TwiML inline (fallback) — message lu par synthèse vocale
                String twiml = "<Response><Say language=\"fr-FR\">"
                        + escapeXml(message)
                        + "</Say></Response>";
                postData.append("&Twiml=").append(URLEncoder.encode(twiml, StandardCharsets.UTF_8));
            }

            // ✅ Authentification Basic
            String auth = accountSid + ":" + authToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(postData.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info(() -> "📞 Appel initié vers " + numero
                        + " (" + etudiant.getNom() + " " + etudiant.getPrenom() + ")");
                return true;
            } else {
                LOGGER.warning(() -> "❌ Échec appel à " + numero
                        + " — HTTP " + response.statusCode()
                        + " : " + response.body());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "❌ Erreur réseau appel à " + numero, e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    // ============================================================
    // NORMALISATION DU NUMÉRO (E.164)
    // ============================================================
    private static String normaliserNumero(String brut) {
        if (brut == null) return "";
        String clean = brut.replaceAll("[^0-9+]", "");

        // Si commence par +
        if (clean.startsWith("+")) {
            return clean.substring(1).length() >= 10 ? clean : "";
        }

        // Si 8 chiffres → Haïti (509)
        if (clean.length() == 8) {
            return "+509" + clean;
        }
        // Si 11 chiffres commençant par 509
        if (clean.length() == 11 && clean.startsWith("509")) {
            return "+" + clean;
        }
        // Sinon, retourner tel quel avec +
        return clean.length() >= 10 ? "+" + clean : "";
    }

    // ============================================================
    // ÉCHAPPEMENT XML (pour TwiML)
    // ============================================================
    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ============================================================
    // PAGE HTML
    // ============================================================
    public static String renderPage(String institutionId) {
        String instIdJs = echapperJs(institutionId);
        String instIdHtml = echapperHtml(institutionId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Appel - Communication</title>");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");

        sb.append("""
            <style>
                * { margin:0; padding:0; box-sizing:border-box; }
                html, body { width: 100%; min-height: 100vh; background: #FAFBFB; font-family: 'Inter', sans-serif; }
                body { padding: 20px; }

                .container { width: 100%; max-width: 1400px; margin: 0 auto; }

                /* NAV */
                .nav-menu { display: flex; gap: 8px; background: white; border-radius: 12px; padding: 8px; margin-bottom: 20px; border: 1px solid #DCE1E6; flex-wrap: wrap; }
                .nav-btn { flex: 1 1 180px; text-align: center; padding: 12px 20px; background: none; border: none; cursor: pointer; font-weight: 600; border-radius: 8px; transition: all 0.2s ease-in-out; text-decoration: none; color: #333; font-size: 14px; }
                .nav-btn.active { background: #3B82F6; color: white; }
                .nav-btn:hover:not(.active) { background: #EFF6FF; color: #1D4ED8; }

                /* HEADER */
                .header { background: white; color: #1E293B; padding: 24px; border-radius: 16px; text-align: center; margin-bottom: 20px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
                .header h1 { font-size: 22px; font-weight: 700; color: #1E40AF; display: flex; align-items: center; justify-content: center; gap: 10px; }
                .header h1 i { color: #3B82F6; font-size: 26px; }
                .header .subtitle { color: #64748B; font-size: 14px; margin-top: 6px; }

                /* FILTRES */
                .filters { background: white; border-radius: 16px; padding: 24px; margin-bottom: 20px; display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 16px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); align-items: end; }
                .filter-group { display: flex; flex-direction: column; gap: 6px; }
                .filter-group label { font-weight: 600; font-size: 13px; color: #334155; }
                .filter-group select { padding: 10px 14px; border: 1px solid #DCE1E6; border-radius: 8px; font-size: 14px; background: white; transition: all 0.2s; width: 100%; font-family: 'Inter', sans-serif; }
                .filter-group select:focus { border-color: #3B82F6; outline: none; box-shadow: 0 0 0 3px rgba(59,130,246,0.1); }

                /* BOUTONS */
                .btn { padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 14px; transition: all 0.2s; display: inline-flex; align-items: center; gap: 8px; height: 42px; text-decoration: none; justify-content: center; }
                .btn:disabled { opacity: 0.6; cursor: not-allowed; }
                .btn-primary { background: #3B82F6; color: white; }
                .btn-primary:hover:not(:disabled) { background: #2563EB; }
                .btn-success { background: #10B981; color: white; }
                .btn-success:hover:not(:disabled) { background: #059669; }
                .btn-secondary { background: #F1F5F9; color: #475569; border: 1px solid #DCE1E6; }
                .btn-secondary:hover:not(:disabled) { background: #E2E8F0; color: #1E293B; }

                /* ACTIONS */
                .actions { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; background: white; padding: 16px 24px; border-radius: 16px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); align-items: center; }
                .actions-left, .actions-right { display: flex; gap: 12px; flex-wrap: wrap; }
                .selected-count { font-size: 13px; color: #475569; font-weight: 600; display: flex; align-items: center; gap: 8px; padding: 8px 14px; background: #EFF6FF; border-radius: 8px; }
                .selected-count i { color: #3B82F6; }

                /* CALL BOX */
                .call-box { background: white; border-radius: 16px; padding: 20px 24px; margin-bottom: 20px; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
                .call-box .info { color: #64748B; font-size: 13px; display: flex; align-items: center; gap: 8px; }
                .call-box .info i { color: #3B82F6; }
                .call-box textarea {
                    width: 100%; padding: 12px 14px; border: 1px solid #DCE1E6; border-radius: 8px;
                    font-size: 14px; min-height: 100px; resize: vertical;
                    font-family: 'Inter', sans-serif; transition: all 0.2s;
                    margin-bottom: 12px;
                }
                .call-box textarea:focus { border-color: #3B82F6; outline: none; box-shadow: 0 0 0 3px rgba(59,130,246,0.1); }
                .char-counter { text-align: right; font-size: 13px; color: #64748B; margin-bottom: 12px; }

                /* TABLEAU */
                .table-container { background: white; border-radius: 16px; overflow: hidden; border: 1px solid #DCE1E6; box-shadow: 0 2px 8px rgba(0,0,0,0.06); margin-bottom: 20px; }
                .table-responsive { width: 100%; overflow-x: auto; }
                table { width: 100%; border-collapse: collapse; }
                th { background: #EFF6FF; color: #1E293B; padding: 14px 16px; font-weight: 600; font-size: 13px; text-align: center; border-bottom: 1px solid #DCE1E6; }
                td { padding: 12px 16px; border-bottom: 1px solid #E2E8F0; text-align: center; font-size: 14px; vertical-align: middle; }
                tr:hover { background: #FAFBFB; }
                tr:last-child td { border-bottom: none; }

                .empty-state { text-align: center; padding: 40px; color: #64748B; }
                .empty-state i { font-size: 42px; color: #CBD5E1; margin-bottom: 10px; }
                .empty-state p { font-size: 15px; }

                .checkbox-custom { width: 18px; height: 18px; cursor: pointer; accent-color: #3B82F6; }

                /* LOADER */
                .loader-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(15,23,42,0.5); display: none; align-items: center; justify-content: center; z-index: 1000; }
                .loader-overlay.active { display: flex; }
                .loader-box { background: white; padding: 32px 48px; border-radius: 16px; text-align: center; }
                .loader-spinner { width: 48px; height: 48px; border: 4px solid #E2E8F0; border-top-color: #3B82F6; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 16px; }
                @keyframes spin { to { transform: rotate(360deg); } }
                .loader-text { font-weight: 600; color: #1E293B; }

                /* TOAST */
                .toast { position: fixed; bottom: 30px; right: 30px; background: #1E293B; color: white; padding: 14px 20px; border-radius: 10px; z-index: 1100; font-weight: 500; box-shadow: 0 10px 30px rgba(0,0,0,0.2); display: flex; align-items: center; gap: 10px; animation: slideIn 0.3s ease; max-width: 400px; }
                .toast.success { background: #10B981; }
                .toast.error { background: #EF4444; }
                .toast.info { background: #3B82F6; }
                @keyframes slideIn { from { opacity: 0; transform: translateX(100px); } to { opacity: 1; transform: translateX(0); } }

                /* FOOTER */
                .footer { text-align: center; margin-top: 30px; color: #64748B; font-size: 13px; }

                @media (max-width: 768px) {
                    .filters { grid-template-columns: 1fr; }
                    .actions { flex-direction: column; align-items: stretch; }
                    .actions-left, .actions-right { justify-content: center; }
                }
            </style>
        """);

        sb.append("</head>");
        sb.append("<body>");

        // Loader
        sb.append("<div class=\"loader-overlay\" id=\"loaderOverlay\">");
        sb.append("<div class=\"loader-box\">");
        sb.append("<div class=\"loader-spinner\"></div>");
        sb.append("<div class=\"loader-text\" id=\"loaderText\">Traitement en cours...</div>");
        sb.append("</div></div>");

        sb.append("<div class=\"container\">");

        // NAV
        sb.append("<div class=\"nav-menu\">");
        sb.append("<a href=\"/dashboard\" class=\"nav-btn\"><i class=\"fas fa-home\"></i> Tableau de bord</a>");
        sb.append("<a href=\"/admin/communication\" class=\"nav-btn\"><i class=\"fas fa-comments\"></i> Communication</a>");
        sb.append("<a href=\"/admin/communication/appel\" class=\"nav-btn active\"><i class=\"fas fa-phone\"></i> Appel</a>");
        sb.append("</div>");

        // HEADER
        sb.append("<div class=\"header\">");
        sb.append("<h1><i class=\"fas fa-phone\"></i> Module Appel</h1>");
        sb.append("<div class=\"subtitle\">Passez des appels aux étudiants via Twilio</div>");
        sb.append("</div>");

        // FILTRES
        sb.append("<div class=\"filters\">");
        sb.append("<div class=\"filter-group\"><label for=\"anneeSelect\"><i class=\"fas fa-calendar\"></i> Année académique</label>");
        sb.append("<select id=\"anneeSelect\"><option value=\"\">Chargement...</option></select></div>");
        sb.append("<div class=\"filter-group\"><label for=\"sessionSelect\"><i class=\"fas fa-clock\"></i> Session</label>");
        sb.append("<select id=\"sessionSelect\"><option value=\"\">Sélectionner une année d'abord</option></select></div>");
        sb.append("<div class=\"filter-group\"><label for=\"classeSelect\"><i class=\"fas fa-users\"></i> Classe</label>");
        sb.append("<select id=\"classeSelect\"><option value=\"\">Chargement...</option></select></div>");
        sb.append("<button class=\"btn btn-primary\" id=\"btnCharger\"><i class=\"fas fa-search\"></i> Charger</button>");
        sb.append("</div>");

        // ACTIONS
        sb.append("<div class=\"actions\">");
        sb.append("<div class=\"actions-left\">");
        sb.append("<button class=\"btn btn-secondary\" id=\"btnToutCocher\"><i class=\"fas fa-check-double\"></i> Tout cocher</button>");
        sb.append("<button class=\"btn btn-secondary\" id=\"btnToutDecocher\"><i class=\"fas fa-times\"></i> Tout décocher</button>");
        sb.append("<div class=\"selected-count\" id=\"selectedCount\"><i class=\"fas fa-user-check\"></i> 0 étudiant sélectionné</div>");
        sb.append("</div>");
        sb.append("<div class=\"actions-right\">");
        sb.append("<a href=\"/admin/communication\" class=\"btn btn-secondary\"><i class=\"fas fa-arrow-left\"></i> Retour</a>");
        sb.append("<button class=\"btn btn-success\" id=\"btnAppeler\"><i class=\"fas fa-phone-alt\"></i> Appeler</button>");
        sb.append("</div>");
        sb.append("</div>");

        // CALL BOX (message à lire)
        sb.append("<div class=\"call-box\">");
        sb.append("<textarea id=\"messageText\" placeholder=\"Message à lire lors de l'appel (synthèse vocale)...\" maxlength=\"500\"></textarea>");
        sb.append("<div class=\"char-counter\"><span id=\"charCount\">0</span> / 500 caractères</div>");
        sb.append("<div class=\"info\"><i class=\"fas fa-info-circle\"></i> Les appels seront initiés via l'infrastructure téléphonique configurée (Twilio).</div>");
        sb.append("</div>");

        // TABLEAU
        sb.append("<div class=\"table-container\">");
        sb.append("<div class=\"table-responsive\">");
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th><input type=\"checkbox\" id=\"checkAll\" class=\"checkbox-custom\"></th>");
        sb.append("<th>Matricule</th><th>Nom</th><th>Prénom</th><th>Téléphone</th><th>Classe</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody id=\"etudiantsBody\">");
        sb.append("<tr><td colspan=\"6\"><div class=\"empty-state\"><i class=\"fas fa-users\"></i><p>Sélectionnez les filtres et cliquez sur Charger</p></div></td></tr>");
        sb.append("</tbody></table>");
        sb.append("</div>");
        sb.append("</div>");

        // FOOTER
        sb.append("<div class=\"footer\">© 2026 M-TECH Academy — Institution : ")
          .append(instIdHtml)
          .append("</div>");
        sb.append("</div>");

        // SCRIPT
        sb.append("<script>");
        sb.append("const INST_ID = '").append(instIdJs).append("';");

        sb.append("""
            function showLoader(text) {
                document.getElementById('loaderText').textContent = text || 'Traitement en cours...';
                document.getElementById('loaderOverlay').classList.add('active');
            }
            function hideLoader() {
                document.getElementById('loaderOverlay').classList.remove('active');
            }
            function showToast(msg, type) {
                const t = document.createElement('div');
                t.className = 'toast ' + (type || 'success');
                const icon = type === 'error' ? 'exclamation-circle' : (type === 'info' ? 'info-circle' : 'check-circle');
                t.innerHTML = '<i class="fas fa-' + icon + '"></i> ' + escapeHtml(msg);
                document.body.appendChild(t);
                setTimeout(() => t.remove(), 4500);
            }
            function escapeHtml(str) {
                if (str === null || str === undefined) return '';
                return String(str).replace(/[&<>"']/g, m => ({
                    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
                }[m]));
            }
            function fetchJson(url, options) {
                return fetch(url, options).then(r => {
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.json();
                });
            }

            function loadFilters() {
                fetchJson('/admin/communication/api/annees')
                    .then(d => {
                        const sel = document.getElementById('anneeSelect');
                        sel.innerHTML = '<option value="">Sélectionner</option>';
                        if (d.success && d.data) d.data.forEach(a => {
                            const opt = document.createElement('option');
                            opt.value = a; opt.textContent = a; sel.appendChild(opt);
                        });
                    })
                    .catch(err => {
                        console.error('Erreur annees:', err);
                        document.getElementById('anneeSelect').innerHTML = '<option value="">Erreur</option>';
                    });

                fetchJson('/admin/communication/api/classes')
                    .then(d => {
                        const sel = document.getElementById('classeSelect');
                        sel.innerHTML = '<option value="">Sélectionner</option>';
                        if (d.success && d.data) d.data.forEach(c => {
                            const opt = document.createElement('option');
                            opt.value = c; opt.textContent = c; sel.appendChild(opt);
                        });
                    })
                    .catch(err => {
                        console.error('Erreur classes:', err);
                        document.getElementById('classeSelect').innerHTML = '<option value="">Erreur</option>';
                    });
            }

            document.getElementById('anneeSelect').addEventListener('change', function() {
                const annee = this.value;
                const sel = document.getElementById('sessionSelect');
                if (!annee) {
                    sel.innerHTML = '<option value="">Sélectionner une année d\\'abord</option>';
                    return;
                }
                sel.innerHTML = '<option value="">Chargement...</option>';
                fetchJson('/admin/communication/api/sessions?annee=' + encodeURIComponent(annee))
                    .then(d => {
                        sel.innerHTML = '<option value="">Sélectionner</option>';
                        if (d.success && d.data) d.data.forEach(s => {
                            const opt = document.createElement('option');
                            opt.value = s; opt.textContent = s; sel.appendChild(opt);
                        });
                    })
                    .catch(err => {
                        console.error('Erreur sessions:', err);
                        sel.innerHTML = '<option value="">Erreur</option>';
                    });
            });

            function updateSelectedCount() {
                const n = document.querySelectorAll('.row-checkbox:checked').length;
                document.getElementById('selectedCount').innerHTML = '<i class="fas fa-user-check"></i> ' + n + ' étudiant' + (n > 1 ? 's' : '') + ' sélectionné' + (n > 1 ? 's' : '');
            }

            function chargerEtudiants() {
                const annee = document.getElementById('anneeSelect').value;
                const session = document.getElementById('sessionSelect').value;
                const classe = document.getElementById('classeSelect').value;
                if (!annee || !session || !classe) {
                    showToast('Veuillez sélectionner une année, une session et une classe.', 'error');
                    return;
                }
                const params = new URLSearchParams({ annee, session, classe });
                const tbody = document.getElementById('etudiantsBody');
                tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><i class="fas fa-spinner fa-spin"></i><p>Chargement...</p></div></td></tr>';

                fetchJson('/admin/communication/api/etudiants?' + params)
                    .then(d => {
                        if (d.success && d.data && d.data.length > 0) {
                            tbody.innerHTML = '';
                            d.data.forEach(e => {
                                const tr = document.createElement('tr');
                                tr.innerHTML =
                                    '<td><input type="checkbox" class="checkbox-custom row-checkbox" data-id="' + escapeHtml(e.id) + '"></td>' +
                                    '<td>' + escapeHtml(e.matricule || '') + '</td>' +
                                    '<td>' + escapeHtml(e.nom) + '</td>' +
                                    '<td>' + escapeHtml(e.prenom) + '</td>' +
                                    '<td>' + escapeHtml(e.telephone || '-') + '</td>' +
                                    '<td>' + escapeHtml(e.classe) + '</td>';
                                tbody.appendChild(tr);
                            });
                            document.querySelectorAll('.row-checkbox').forEach(cb => {
                                cb.addEventListener('change', updateSelectedCount);
                            });
                            updateSelectedCount();
                        } else {
                            tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><i class="fas fa-info-circle"></i><p>Aucun étudiant trouvé</p></div></td></tr>';
                        }
                    })
                    .catch(err => {
                        console.error('Erreur chargement étudiants:', err);
                        tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><i class="fas fa-exclamation-triangle"></i><p>Erreur de chargement</p></div></td></tr>';
                    });
            }

            document.getElementById('btnCharger').addEventListener('click', chargerEtudiants);

            document.getElementById('checkAll').addEventListener('change', function() {
                document.querySelectorAll('.row-checkbox').forEach(cb => cb.checked = this.checked);
                updateSelectedCount();
            });

            document.getElementById('btnToutCocher').addEventListener('click', function() {
                document.querySelectorAll('.row-checkbox').forEach(cb => cb.checked = true);
                document.getElementById('checkAll').checked = true;
                updateSelectedCount();
            });

            document.getElementById('btnToutDecocher').addEventListener('click', function() {
                document.querySelectorAll('.row-checkbox').forEach(cb => cb.checked = false);
                document.getElementById('checkAll').checked = false;
                updateSelectedCount();
            });

            const messageText = document.getElementById('messageText');
            const charCount = document.getElementById('charCount');
            if (messageText) {
                messageText.addEventListener('input', function() {
                    charCount.textContent = this.value.length;
                });
            }

            document.getElementById('btnAppeler').addEventListener('click', function() {
                const selected = [];
                document.querySelectorAll('.row-checkbox:checked').forEach(cb => selected.push(cb.dataset.id));
                if (selected.length === 0) { showToast('Veuillez sélectionner au moins un étudiant.', 'error'); return; }

                let message = document.getElementById('messageText').value.trim();
                if (!message) { message = 'Bonjour, ceci est un message automatique de l\\'institution.'; }

                const payload = { canal: 'appel', etudiants: selected, message: message };

                const btn = this;
                btn.disabled = true;
                showLoader('Initiation des appels en cours...');

                fetchJson('/admin/communication/api/envoyer', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                })
                .then(d => {
                    hideLoader();
                    if (d.success) {
                        showToast(d.message || 'Appels initiés avec succès', 'success');
                        document.getElementById('messageText').value = '';
                        document.getElementById('charCount').textContent = '0';
                        document.querySelectorAll('.row-checkbox').forEach(cb => cb.checked = false);
                        document.getElementById('checkAll').checked = false;
                        updateSelectedCount();
                    } else {
                        showToast(d.error || 'Erreur lors de l\\'initiation des appels', 'error');
                    }
                })
                .catch(e => {
                    hideLoader();
                    console.error('Erreur appel:', e);
                    showToast('Erreur de connexion : ' + e.message, 'error');
                })
                .finally(() => { btn.disabled = false; });
            });

            loadFilters();
        """);

        sb.append("</script>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ============================================================
    // UTILITAIRES
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
                .replace("\r", "\\r")
                .replace("</", "<\\/");
    }
}