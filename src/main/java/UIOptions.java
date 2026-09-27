import java.util.List;
import java.util.Map;

public class UIOptions {

    public static String rendrePage(
            List<Map<String, Object>> options,
            String institutionId,
            List<String> annees,
            List<String> periodes,
            List<String> classes,
            List<String> promotions,
            String messageErreur,
            String messageSucces) {

        if (options == null) options = List.of();
        if (annees == null) annees = List.of();
        if (periodes == null) periodes = List.of();
        if (classes == null) classes = List.of();
        if (promotions == null) promotions = List.of();
        if (messageErreur == null) messageErreur = "";
        if (messageSucces == null) messageSucces = "";

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"fr\" class=\"h-100\">\n")
          .append("<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("    <title>Gestion des Options</title>\n")
          .append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">\n")
          .append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n")
          .append("    <style>\n")
          .append("        :root { --primary-color: #4f46e5; --primary-hover: #4338ca; --bg-color: #f8fafc; }\n")
          .append("        html, body { height: 100%; margin: 0; overflow: hidden; font-family: 'Inter', sans-serif; background-color: var(--bg-color); color: #1e293b; }\n")
          .append("        .main-wrapper { height: 100vh; display: flex; flex-direction: column; padding: 1.5rem; box-sizing: border-box; }\n")
          .append("        .glass-header {\n")
          .append("            background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%); color: white;\n")
          .append("            border-radius: 16px; padding: 20px 24px; box-shadow: 0 10px 25px -5px rgba(79, 70, 229, 0.3);\n")
          .append("            flex-shrink: 0;\n")
          .append("        }\n")
          .append("        .custom-card { border: none; border-radius: 16px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05); background: #ffffff; }\n")
          .append("        .table-container { flex: 1; min-height: 0; display: flex; flex-direction: column; margin-top: 20px; }\n")
          .append("        .table-responsive-custom { flex: 1; overflow-y: auto; border-radius: 16px; }\n")
          .append("        .table-custom { margin-bottom: 0; }\n")
          .append("        .table-custom thead { position: sticky; top: 0; z-index: 10; background-color: #f8fafc; }\n")
          .append("        .btn-primary { background-color: var(--primary-color); border-color: var(--primary-color); border-radius: 10px; padding: 9px 20px; font-weight: 500; }\n")
          .append("        .btn-primary:hover { background-color: var(--primary-hover); border-color: var(--primary-hover); }\n")
          .append("        .btn-success { border-radius: 10px; padding: 9px 24px; font-weight: 500; }\n")
          .append("        .badge-soft-primary { background-color: #e0e7ff; color: #4338ca; font-weight: 600; border-radius: 6px; padding: 6px 10px; }\n")
          .append("        .form-control, .form-select { border-radius: 10px; border: 1px solid #e2e8f0; padding: 9px 14px; font-size: 0.9rem; transition: all 0.2s; }\n")
          .append("        .form-control:focus, .form-select:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); }\n")
          .append("        .toolbar-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }\n")
          .append("        .toolbar-actions .btn { font-size: 13px; padding: 6px 16px; }\n")
          .append("        .modal-content { border-radius: 16px; }\n")
          .append("        .modal-header { border-bottom: none; padding-bottom: 0; }\n")
          .append("        .modal-footer { border-top: none; padding-top: 0; }\n")
          .append("        .toast-container { position: fixed; bottom: 30px; right: 30px; z-index: 1100; }\n")
          .append("        .toast-custom { background: #0f172a; color: white; padding: 14px 22px; border-radius: 14px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.2); font-weight: 500; font-size: 14px; animation: fadeInOut 3s ease; }\n")
          .append("        .toast-custom.success { background: #16a34a; }\n")
          .append("        .toast-custom.error { background: #dc2626; }\n")
          .append("        @keyframes fadeInOut { 0% { opacity: 0; transform: translateY(20px); } 15% { opacity: 1; transform: translateY(0); } 85% { opacity: 1; } 100% { opacity: 0; transform: translateY(20px); } }\n")
          .append("        @media (max-width: 768px) { .toolbar-actions { flex-direction: column; align-items: stretch; } }\n")
          .append("    </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("<div class=\"main-wrapper\">\n");

        // En-tête
        sb.append("    <div class=\"glass-header d-flex justify-content-between align-items-center mb-3\">\n")
          .append("        <div>\n")
          .append("            <h4 class=\"m-0 fw-bold\"><i class=\"fa-solid fa-list-ul me-2\"></i>Gestion des Options</h4>\n")
          .append("            <p class=\"m-0 opacity-75 fs-7 mt-1\">Gérez les filières / options proposées par classe et année</p>\n")
          .append("        </div>\n")
          .append("        <div>\n")
          .append("            <a href=\"/admin/parametres\" class=\"btn btn-light text-primary fw-semibold rounded-pill px-3 py-2 fs-7 shadow-sm\">\n")
          .append("                <i class=\"fa-solid fa-arrow-left me-1\"></i> Paramètres\n")
          .append("            </a>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        // Messages
        if (!messageErreur.isEmpty()) {
            sb.append("    <div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-3 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-exclamation me-2\"></i>").append(echapperHtml(messageErreur)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }
        if (!messageSucces.isEmpty()) {
            sb.append("    <div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-3 py-2\" role=\"alert\">\n")
              .append("        <i class=\"fa-solid fa-circle-check me-2\"></i>").append(echapperHtml(messageSucces)).append("\n")
              .append("        <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>\n")
              .append("    </div>\n");
        }

        // Toolbar
        sb.append("    <div class=\"d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2\">\n")
          .append("        <div class=\"fw-bold fs-6 text-dark\"><i class=\"fa-solid fa-list me-2 text-primary\"></i>Liste des options</div>\n")
          .append("        <div class=\"toolbar-actions\">\n")
          .append("            <button class=\"btn btn-primary btn-sm\" id=\"btnAjouter\"><i class=\"fa-solid fa-plus me-1\"></i>Ajouter</button>\n")
          .append("            <button class=\"btn btn-secondary btn-sm\" id=\"btnRafraichir\"><i class=\"fa-solid fa-rotate me-1\"></i>Rafraîchir</button>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        // Tableau
        sb.append("    <div class=\"custom-card table-container\">\n")
          .append("        <div class=\"table-responsive-custom\">\n")
          .append("            <table class=\"table table-custom align-middle mb-0\">\n")
          .append("                <thead class=\"table-light border-bottom\">\n")
          .append("                    <tr class=\"text-muted fs-7 text-uppercase\">\n")
          .append("                        <th class=\"ps-4 py-3\">#</th>\n")
          .append("                        <th class=\"py-3\">Option / Filière</th>\n")
          .append("                        <th class=\"py-3\">Classe</th>\n")
          .append("                        <th class=\"py-3\">Année académique</th>\n")
          .append("                        <th class=\"py-3\">Période</th>\n")
          .append("                        <th class=\"py-3\">Promotion</th>\n")
          .append("                        <th class=\"pe-4 py-3 text-center\" style=\"width: 140px;\">Actions</th>\n")
          .append("                    </tr>\n")
          .append("                </thead>\n")
          .append("                <tbody id=\"optionsBody\" class=\"border-top-0\">\n");

        if (options.isEmpty()) {
            sb.append("                    <tr>\n")
              .append("                        <td colspan=\"7\" class=\"text-center py-5 text-muted\">\n")
              .append("                            <i class=\"fa-solid fa-inbox fs-1 mb-3 text-black-50 d-block\"></i>\n")
              .append("                            <span>Aucune option enregistrée. Cliquez sur <strong>\"Ajouter\"</strong> pour créer une nouvelle option.</span>\n")
              .append("                        </td>\n")
              .append("                    </tr>\n");
        } else {
            int index = 1;
            for (Map<String, Object> opt : options) {
                String option = (String) opt.get("option");
                String classe = (String) opt.get("classe");
                String anneeAcademique = (String) opt.get("anneeAcademique");
                String periode = (String) opt.get("periode");
                String promotion = (String) opt.get("promotion");

                // ✅ Échappement pour les attributs data-*
                String optionEsc = echapperHtml(option);
                String classeEsc = echapperHtml(classe);
                String anneeEsc = echapperHtml(anneeAcademique);

                sb.append("                    <tr>\n")
                  .append("                        <td class=\"ps-4\"><span class=\"badge-soft-primary\">").append(index++).append("</span></td>\n")
                  .append("                        <td class=\"fw-semibold text-dark\">").append(optionEsc).append("</td>\n")
                  .append("                        <td>").append(classeEsc).append("</td>\n")
                  .append("                        <td>").append(anneeEsc).append("</td>\n")
                  .append("                        <td>").append(echapperHtml(periode != null ? periode : "-")).append("</td>\n")
                  .append("                        <td>").append(echapperHtml(promotion != null ? promotion : "-")).append("</td>\n")
                  .append("                        <td class=\"pe-4 text-center\">\n")
                  .append("                            <button class=\"btn btn-sm btn-outline-primary btn-edit me-1\" ")
                  .append("data-option=\"").append(optionEsc).append("\" ")
                  .append("data-classe=\"").append(classeEsc).append("\" ")
                  .append("data-annee=\"").append(anneeEsc).append("\">")
                  .append("<i class=\"fa-solid fa-pen\"></i></button>\n")
                  .append("                            <button class=\"btn btn-sm btn-outline-danger btn-delete\" ")
                  .append("data-option=\"").append(optionEsc).append("\" ")
                  .append("data-classe=\"").append(classeEsc).append("\" ")
                  .append("data-annee=\"").append(anneeEsc).append("\">")
                  .append("<i class=\"fa-solid fa-trash\"></i></button>\n")
                  .append("                        </td>\n")
                  .append("                    </tr>\n");
            }
        }

        sb.append("                </tbody>\n")
          .append("            </table>\n")
          .append("        </div>\n")
          .append("    </div>\n");

        sb.append("</div>\n");

        // ================= MODAL AJOUT / MODIFICATION =================
        sb.append("""
        <div class="modal fade" id="optionModal" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="modalTitle"><i class="fa-solid fa-pen-to-square me-2"></i>Ajouter une option</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <form id="optionForm">
                        <div class="modal-body">
                            <input type="hidden" id="editOldOption">
                            <input type="hidden" id="editOldClasse">
                            <input type="hidden" id="editOldAnnee">
                            <div class="mb-3">
                                <label for="inputOption" class="form-label fw-semibold">Option / Filière <span class="text-danger">*</span></label>
                                <input type="text" class="form-control" id="inputOption" placeholder="Ex: Sciences économiques" required>
                            </div>
                            <div class="mb-3">
                                <label for="inputClasse" class="form-label fw-semibold">Classe <span class="text-danger">*</span></label>
                                <select class="form-select" id="inputClasse" required>
                                    <option value="">-- Sélectionner --</option>
                                    """);
        for (String c : classes) {
            sb.append("<option value=\"").append(echapperHtml(c)).append("\">")
              .append(echapperHtml(c)).append("</option>");
        }
        sb.append("""
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="inputAnnee" class="form-label fw-semibold">Année académique <span class="text-danger">*</span></label>
                                <select class="form-select" id="inputAnnee" required>
                                    <option value="">-- Sélectionner --</option>
                                    """);
        for (String a : annees) {
            sb.append("<option value=\"").append(echapperHtml(a)).append("\">")
              .append(echapperHtml(a)).append("</option>");
        }
        sb.append("""
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="inputPeriode" class="form-label fw-semibold">Période</label>
                                <select class="form-select" id="inputPeriode">
                                    <option value="">-- Aucune --</option>
                                    """);
        for (String p : periodes) {
            sb.append("<option value=\"").append(echapperHtml(p)).append("\">")
              .append(echapperHtml(p)).append("</option>");
        }
        sb.append("""
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="inputPromotion" class="form-label fw-semibold">Promotion</label>
                                <select class="form-select" id="inputPromotion">
                                    <option value="">-- Aucune --</option>
                                    """);
        for (String p : promotions) {
            sb.append("<option value=\"").append(echapperHtml(p)).append("\">")
              .append(echapperHtml(p)).append("</option>");
        }
        sb.append("""
                                </select>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                            <button type="submit" class="btn btn-primary" id="btnSaveOption"><i class="fa-solid fa-save me-1"></i>Enregistrer</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        """);

        // ================= TOAST =================
        sb.append("    <div class=\"toast-container\" id=\"toastContainer\"></div>\n");

        // ================= JAVASCRIPT =================
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("<script>\n");
        sb.append("const API_BASE = '/admin/parametres/options/api/options';\n");
        sb.append("const modal = new bootstrap.Modal(document.getElementById('optionModal'));\n");
        sb.append("const form = document.getElementById('optionForm');\n");
        sb.append("const tbody = document.getElementById('optionsBody');\n");

        sb.append("""
                  function showToast(message, type) {
                      const container = document.getElementById('toastContainer');
                      const div = document.createElement('div');
                      div.className = 'toast-custom ' + type;
                      div.textContent = message;
                      container.appendChild(div);
                      setTimeout(() => div.remove(), 3000);
                  }
                  
                  function escapeHtml(str) {
                      if (!str) return '';
                      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
                  }
                  
                  /**
                   * \u2705 Construit la cl\u00e9 URL : /{option}/{classe}/{annee} (encod\u00e9e).
                   */
                  function buildCleUrl(option, classe, annee) {
                      return encodeURIComponent(option || '') + '/'
                           + encodeURIComponent(classe || '') + '/'
                           + encodeURIComponent(annee || '');
                  }
                  
                  function loadOptions() {
                      fetch(API_BASE)
                          .then(res => res.json())
                          .then(data => {
                              if (data.success) {
                                  renderTable(data.data);
                              } else {
                                  showToast('Erreur chargement: ' + (data.error || 'inconnue'), 'error');
                              }
                          })
                          .catch(err => {
                              console.error(err);
                              showToast('Erreur r\u00e9seau', 'error');
                          });
                  }
                  
                  function renderTable(options) {
                      if (!options || options.length === 0) {
                          tbody.innerHTML = `
                              <tr>
                                  <td colspan="7" class="text-center py-5 text-muted">
                                      <i class="fa-solid fa-inbox fs-1 mb-3 text-black-50 d-block"></i>
                                      <span>Aucune option enregistr\u00e9e.</span>
                                  </td>
                              </tr>`;
                          return;
                      }
                      let html = '';
                      options.forEach((opt, index) => {
                          const optionEsc = escapeHtml(opt.option || '');
                          const classeEsc = escapeHtml(opt.classe || '');
                          const anneeEsc = escapeHtml(opt.anneeAcademique || '');
                          html += `
                              <tr>
                                  <td class="ps-4"><span class="badge-soft-primary">${index + 1}</span></td>
                                  <td class="fw-semibold text-dark">${optionEsc}</td>
                                  <td>${classeEsc}</td>
                                  <td>${anneeEsc}</td>
                                  <td>${escapeHtml(opt.periode || '-')}</td>
                                  <td>${escapeHtml(opt.promotion || '-')}</td>
                                  <td class="pe-4 text-center">
                                      <button class="btn btn-sm btn-outline-primary btn-edit me-1"
                                          data-option="${optionEsc}"
                                          data-classe="${classeEsc}"
                                          data-annee="${anneeEsc}">
                                          <i class="fa-solid fa-pen"></i>
                                      </button>
                                      <button class="btn btn-sm btn-outline-danger btn-delete"
                                          data-option="${optionEsc}"
                                          data-classe="${classeEsc}"
                                          data-annee="${anneeEsc}">
                                          <i class="fa-solid fa-trash"></i>
                                      </button>
                                  </td>
                              </tr>`;
                      });
                      tbody.innerHTML = html;
                  
                      document.querySelectorAll('.btn-edit').forEach(btn => {
                          btn.addEventListener('click', function() {
                              openEditModal(this.dataset.option, this.dataset.classe, this.dataset.annee);
                          });
                      });
                      document.querySelectorAll('.btn-delete').forEach(btn => {
                          btn.addEventListener('click', function() {
                              if (confirm('Voulez-vous vraiment supprimer cette option ?')) {
                                  deleteOption(this.dataset.option, this.dataset.classe, this.dataset.annee);
                              }
                          });
                      });
                  }
                  
                  function openEditModal(option, classe, annee) {
                      const url = API_BASE + '/' + buildCleUrl(option, classe, annee);
                      fetch(url)
                          .then(res => res.json())
                          .then(data => {
                              if (data.success) {
                                  const opt = data.data;
                                  // \u2705 Stocker les anciennes valeurs
                                  document.getElementById('editOldOption').value = opt.option || '';
                                  document.getElementById('editOldClasse').value = opt.classe || '';
                                  document.getElementById('editOldAnnee').value = opt.anneeAcademique || '';
                  
                                  document.getElementById('inputOption').value = opt.option || '';
                                  document.getElementById('inputClasse').value = opt.classe || '';
                                  document.getElementById('inputAnnee').value = opt.anneeAcademique || '';
                                  document.getElementById('inputPeriode').value = opt.periode || '';
                                  document.getElementById('inputPromotion').value = opt.promotion || '';
                                  document.getElementById('modalTitle').innerHTML = '<i class="fa-solid fa-pen-to-square me-2"></i>Modifier l\\'option';
                                  modal.show();
                              } else {
                                  showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                              }
                          })
                          .catch(err => {
                              console.error(err);
                              showToast('Erreur r\u00e9seau', 'error');
                          });
                  }
                  
                  function resetForm() {
                      document.getElementById('editOldOption').value = '';
                      document.getElementById('editOldClasse').value = '';
                      document.getElementById('editOldAnnee').value = '';
                      document.getElementById('inputOption').value = '';
                      document.getElementById('inputClasse').value = '';
                      document.getElementById('inputAnnee').value = '';
                      document.getElementById('inputPeriode').value = '';
                      document.getElementById('inputPromotion').value = '';
                      document.getElementById('modalTitle').innerHTML = '<i class="fa-solid fa-plus-circle me-2"></i>Ajouter une option';
                  }
                  
                  function saveOption(event) {
                      event.preventDefault();
                      const oldOption = document.getElementById('editOldOption').value;
                      const oldClasse = document.getElementById('editOldClasse').value;
                      const oldAnnee = document.getElementById('editOldAnnee').value;
                      const option = document.getElementById('inputOption').value.trim();
                      const classe = document.getElementById('inputClasse').value;
                      const anneeAcademique = document.getElementById('inputAnnee').value;
                      const periode = document.getElementById('inputPeriode').value;
                      const promotion = document.getElementById('inputPromotion').value;
                  
                      if (!option || !classe || !anneeAcademique) {
                          showToast('Veuillez remplir tous les champs obligatoires (*)', 'error');
                          return;
                      }
                  
                      const payload = { option, classe, anneeAcademique, periode, promotion };
                      // \u2705 Mode \u00e9dition si les anciennes valeurs sont non vides
                      const isEdit = oldOption && oldClasse && oldAnnee;
                      const method = isEdit ? 'PUT' : 'POST';
                      const url = isEdit
                          ? API_BASE + '/' + buildCleUrl(oldOption, oldClasse, oldAnnee)
                          : API_BASE;
                  
                      fetch(url, {
                          method: method,
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify(payload)
                      })
                      .then(res => res.json())
                      .then(data => {
                          if (data.success) {
                              modal.hide();
                              showToast(data.message || 'Op\u00e9ration r\u00e9ussie', 'success');
                              loadOptions();
                          } else {
                              showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                          }
                      })
                      .catch(err => {
                          console.error(err);
                          showToast('Erreur r\u00e9seau', 'error');
                      });
                  }
                  
                  function deleteOption(option, classe, annee) {
                      const url = API_BASE + '/' + buildCleUrl(option, classe, annee);
                      fetch(url, { method: 'DELETE' })
                          .then(res => res.json())
                          .then(data => {
                              if (data.success) {
                                  showToast('Option supprim\u00e9e', 'success');
                                  loadOptions();
                              } else {
                                  showToast('Erreur: ' + (data.error || 'inconnue'), 'error');
                              }
                          })
                          .catch(err => {
                              console.error(err);
                              showToast('Erreur r\u00e9seau', 'error');
                          });
                  }
                  
                  // \u00c9couteurs
                  document.getElementById('btnAjouter').addEventListener('click', function() {
                      resetForm();
                      modal.show();
                  });
                  
                  document.getElementById('btnRafraichir').addEventListener('click', loadOptions);
                  
                  form.addEventListener('submit', saveOption);
                  
                  // Chargement initial
                  loadOptions();
                  
                  """);
        sb.append("</script>\n");
        sb.append("</body>\n");
        sb.append("</html>");
        return sb.toString();
    }

    // ============================================================
    // UTILITAIRE D'ÉCHAPPEMENT
    // ============================================================
    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}