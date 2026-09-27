import java.util.List;

public class UIFormulaireClasse {

    // ============================================================
    // POINT D'ENTRÉE
    // ============================================================
    public static String rendreModal(
            String institutionId,
            List<String> annees,
            List<String> periodes,
            List<String> promotions) {

        institutionId = institutionId == null ? "" : institutionId;
        annees = annees == null ? List.of() : annees;
        periodes = periodes == null ? List.of() : periodes;
        promotions = promotions == null ? List.of() : promotions;

        StringBuilder sb = new StringBuilder(14_000);

        appendModalStyles(sb);
        appendModalHtml(sb, institutionId, annees, periodes, promotions);
        appendModalScripts(sb);

        return sb.toString();
    }

    // ============================================================
    // STYLES — préfixés ufc- pour éviter tout conflit
    // ============================================================
    private static void appendModalStyles(StringBuilder sb) {
        sb.append("""
            <style>
                .ufc-overlay {
                    display: none;
                    position: fixed;
                    inset: 0;
                    z-index: 1060;
                    background: rgba(15, 23, 42, 0.55);
                    align-items: center;
                    justify-content: center;
                    padding: 16px;
                    overflow-y: auto;
                    -webkit-overflow-scrolling: touch;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                }
                .ufc-overlay.ufc-show { display: flex; }

                .ufc-box {
                    background: #ffffff;
                    border-radius: 14px;
                    width: 100%;
                    max-width: 720px;
                    max-height: 92vh;
                    display: flex;
                    flex-direction: column;
                    box-shadow: 0 25px 60px -15px rgba(0,0,0,0.4);
                    overflow: hidden;
                    min-height: 0;
                    animation: ufcSlideIn 0.18s ease-out;
                }
                @keyframes ufcSlideIn {
                    from { opacity: 0; transform: translateY(-16px); }
                    to   { opacity: 1; transform: translateY(0); }
                }

                .ufc-header {
                    padding: 14px 20px;
                    background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);
                    color: #ffffff;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    flex-shrink: 0;
                }
                .ufc-header h2 {
                    margin: 0;
                    font-size: 1.05rem;
                    font-weight: 700;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .ufc-close {
                    background: rgba(255,255,255,0.18);
                    border: none;
                    color: #ffffff;
                    width: 30px;
                    height: 30px;
                    border-radius: 50%;
                    font-size: 1.1rem;
                    line-height: 1;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    transition: 0.15s;
                }
                .ufc-close:hover { background: rgba(255,255,255,0.32); }

                .ufc-form {
                    display: flex;
                    flex-direction: column;
                    flex: 1 1 auto;
                    min-height: 0;
                    overflow: hidden;
                }
                .ufc-body {
                    padding: 20px;
                    overflow-y: auto;
                    flex: 1 1 auto;
                    min-height: 0;
                }

                .ufc-row {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 14px;
                    margin-bottom: 14px;
                }
                .ufc-group { display: flex; flex-direction: column; gap: 5px; }
                .ufc-label {
                    font-size: 0.72rem;
                    font-weight: 700;
                    color: #475569;
                    text-transform: uppercase;
                    letter-spacing: 0.3px;
                }
                .ufc-required { color: #ef4444; margin-left: 2px; }
                .ufc-input,
                .ufc-select {
                    border: 1px solid #e2e8f0;
                    border-radius: 8px;
                    padding: 0.5rem 0.7rem;
                    font-size: 0.88rem;
                    font-family: inherit;
                    background: #f8fafc;
                    color: #1e293b;
                    outline: none;
                    transition: 0.15s;
                    width: 100%;
                    height: 40px;
                    box-sizing: border-box;
                }
                .ufc-input:focus,
                .ufc-select:focus {
                    border-color: #4f46e5;
                    box-shadow: 0 0 0 3px rgba(79,70,229,0.15);
                    background: #ffffff;
                }
                .ufc-input[readonly],
                .ufc-input[disabled] {
                    background: #f1f5f9;
                    color: #64748b;
                    cursor: not-allowed;
                }
                .ufc-hint {
                    font-size: 0.68rem;
                    color: #94a3b8;
                    margin-top: 2px;
                }

                .ufc-footer {
                    padding: 12px 20px;
                    background: #f8fafc;
                    border-top: 1px solid #e2e8f0;
                    display: flex;
                    justify-content: flex-end;
                    gap: 8px;
                    flex-shrink: 0;
                }
                .ufc-btn {
                    border-radius: 8px;
                    padding: 0.5rem 1.2rem;
                    font-weight: 600;
                    font-size: 0.85rem;
                    border: none;
                    cursor: pointer;
                    height: 40px;
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    transition: 0.15s;
                    font-family: inherit;
                }
                .ufc-btn-cancel {
                    background: #ffffff;
                    color: #475569;
                    border: 1px solid #e2e8f0;
                }
                .ufc-btn-cancel:hover { background: #f1f5f9; }
                .ufc-btn-submit {
                    background: #4f46e5;
                    color: #ffffff;
                }
                .ufc-btn-submit:hover { background: #4338ca; }
                .ufc-btn-submit:disabled { opacity: 0.6; cursor: not-allowed; }

                .ufc-svg {
                    width: 1em;
                    height: 1em;
                    display: inline-block;
                    vertical-align: -0.125em;
                    fill: currentColor;
                }
                .ufc-svg-lg { width: 18px; height: 18px; }

                @media (max-width: 600px) {
                    .ufc-row { grid-template-columns: 1fr; }
                    .ufc-box { max-height: 95vh; border-radius: 12px; }
                    .ufc-header { padding: 12px 16px; }
                    .ufc-body { padding: 16px; }
                    .ufc-footer { padding: 10px 16px; }
                    .ufc-btn { flex: 1; justify-content: center; }
                }
            </style>
            """);
    }

    // ============================================================
    // HTML DU MODAL
    // ============================================================
    private static void appendModalHtml(StringBuilder sb,
                                         String institutionId,
                                         List<String> annees,
                                         List<String> periodes,
                                         List<String> promotions) {

        sb.append("""
            <div class="ufc-overlay" id="classeModal" role="dialog" aria-modal="true" aria-labelledby="classeModalTitle">
                <div class="ufc-box">

                    <div class="ufc-header">
                        <h2 id="classeModalTitle">
                            <svg class="ufc-svg ufc-svg-lg" viewBox="0 0 576 512" aria-hidden="true">
                                <path d="M543.8 287.6c-7.3-15.2-25.5-22.2-40.8-14.9L448 295.8V224c0-35.3-28.7-64-64-64H352V69.3c0-21.4-24.5-33.2-41.5-20.7l-224 160c-16.8 12-16.8 37.4 0 49.4l224 160C327.5 442.4 352 430.6 352 409.2V320h32c8.8 0 16 7.2 16 16v48c0 17.7 14.3 32 32 32h16c17.7 0 32-14.3 32-32v-48c0-8.8 7.2-16 16-16h35.1c15.3 7.3 33.5 0.3 40.8-14.9l20.8-43.3c6.5-13.6 6.5-29.6 0-43.2l-20.8-43.3z"/>
                            </svg>
                            <span id="classeModalTitleText">Nouvelle classe</span>
                        </h2>
                        <button type="button" class="ufc-close"
                                onclick="window.closeClasseModal()" aria-label="Fermer">×</button>
                    </div>

                    <form id="classeForm" class="ufc-form" autocomplete="off">
                        <div class="ufc-body">
                            <input type="hidden" name="institutionId" value="""
        );
        sb.append(echapperHtml(institutionId));
        sb.append("""
                            ">
                            <input type="hidden" name="action" id="classeFormAction" value="add">
                            <input type="hidden" name="mode"   id="classeFormMode"   value="add">
                            <!-- moyenne_passage : le serveur a une valeur par défaut (10.0) -->
                            <input type="hidden" name="moyennePassage" value="10">

                            <div class="ufc-row">
                                <div class="ufc-group">
                                    <label class="ufc-label" for="codeClasse">
                                        Code <span class="ufc-required">*</span>
                                    </label>
                                    <input class="ufc-input" type="text" id="codeClasse" name="codeClasse"
                                           readonly placeholder="Généré automatiquement"
                                           title="Le code est généré automatiquement par le système">
                                    <span class="ufc-hint">Généré automatiquement par le système</span>
                                </div>
                                <div class="ufc-group">
                                    <label class="ufc-label" for="nomClasse">Nom <span class="ufc-required">*</span></label>
                                    <input class="ufc-input" type="text" id="nomClasse" name="nomClasse"
                                           required maxlength="60" placeholder="ex: Sixième A">
                                </div>
                            </div>

                            <div class="ufc-row">
                                <div class="ufc-group">
                                    <label class="ufc-label" for="niveauClasse">
                                        Niveau <span class="ufc-required">*</span>
                                    </label>
                                    <input class="ufc-input" type="number" id="niveauClasse" name="niveauClasse"
                                           required min="1" max="20" step="1" value="1"
                                           placeholder="1 à 20"
                                           title="Entrez un niveau entier entre 1 et 20">
                                    <span class="ufc-hint">Nombre entier entre 1 et 20</span>
                                </div>
                                <div class="ufc-group">
                                    <label class="ufc-label" for="capaciteClasse">
                                        Capacité <span class="ufc-required">*</span>
                                    </label>
                                    <input class="ufc-input" type="number" id="capaciteClasse" name="capaciteClasse"
                                           required min="1" max="500" step="1" value="40">
                                </div>
                            </div>

                            <div class="ufc-row">
                                <div class="ufc-group">
                                    <label class="ufc-label" for="anneeAcademique">
                                        Année académique <span class="ufc-required">*</span>
                                    </label>
                                    <select class="ufc-select" id="anneeAcademique" name="anneeAcademique" required>
                                        <option value="">-- Choisir --</option>
            """);
        for (String a : annees) {
            sb.append("                        <option value=\"").append(echapperHtml(a)).append("\">")
              .append(echapperHtml(a)).append("</option>\n");
        }
        sb.append("""
                                    </select>
                                </div>
                                <div class="ufc-group">
                                    <label class="ufc-label" for="periode">
                                        Période <span class="ufc-required">*</span>
                                    </label>
                                    <select class="ufc-select" id="periode" name="periode" required>
                                        <option value="">-- Choisir --</option>
            """);
        for (String p : periodes) {
            sb.append("                        <option value=\"").append(echapperHtml(p)).append("\">")
              .append(echapperHtml(p)).append("</option>\n");
        }
        sb.append("""
                                    </select>
                                </div>
                            </div>

                            <div class="ufc-row">
                                <div class="ufc-group">
                                    <label class="ufc-label" for="promotion">
                                        Promotion <span class="ufc-required">*</span>
                                    </label>
                                    <select class="ufc-select" id="promotion" name="promotion" required>
                                        <option value="">-- Choisir --</option>
            """);
        for (String pr : promotions) {
            sb.append("                        <option value=\"").append(echapperHtml(pr)).append("\">")
              .append(echapperHtml(pr)).append("</option>\n");
        }
        sb.append("""
                                    </select>
                                    <span class="ufc-hint">Obligatoire — doit exister dans la table promotions</span>
                                </div>
                                <div class="ufc-group">
                                    <label class="ufc-label" for="statut">Statut</label>
                                    <select class="ufc-select" id="statut" name="statut">
                                        <option value="ACTIF">Active</option>
                                        <option value="INACTIF">Inactive</option>
                                    </select>
                                </div>
                            </div>
                        </div>

                        <div class="ufc-footer">
                            <button type="button" class="ufc-btn ufc-btn-cancel"
                                    onclick="window.closeClasseModal()">
                                <svg class="ufc-svg" viewBox="0 0 384 512" aria-hidden="true">
                                    <path d="M342.6 150.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L192 210.7 86.6 105.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3L146.7 256 41.4 361.4c-12.5 12.5-12.5 32.8 0 45.3s32.8 12.5 45.3 0L192 301.3 297.4 406.6c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L237.3 256 342.6 150.6z"/>
                                </svg>
                                Annuler
                            </button>
                            <button type="submit" class="ufc-btn ufc-btn-submit" id="classeFormSubmit">
                                <svg class="ufc-svg" viewBox="0 0 448 512" aria-hidden="true">
                                    <path d="M64 32C28.7 32 0 60.7 0 96V416c0 35.3 28.7 64 64 64H384c35.3 0 64-28.7 64-64V173.3c0-17-6.7-33.3-18.7-45.3L352 50.7C340 38.7 323.7 32 306.7 32H64zm0 96c0-17.7 14.3-32 32-32H288c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32H96c-17.7 0-32-14.3-32-32V128zM224 288a64 64 0 1 1 0 128 64 64 0 1 1 0-128z"/>
                                </svg>
                                <span id="classeFormSubmitText">Enregistrer</span>
                            </button>
                        </div>
                    </form>

                </div>
            </div>
            """);
    }

    // ============================================================
    // SCRIPTS — autonomes, pas de Bootstrap
    // ============================================================
    private static void appendModalScripts(StringBuilder sb) {
        sb.append("""
            <script>
            (function() {
                function initModal() {
                    const modalEl    = document.getElementById('classeModal');
                    const form       = document.getElementById('classeForm');
                    const titleText  = document.getElementById('classeModalTitleText');
                    const submitText = document.getElementById('classeFormSubmitText');
                    const actionField= document.getElementById('classeFormAction');
                    const modeField  = document.getElementById('classeFormMode');
                    const codeField  = document.getElementById('codeClasse');

                    if (!modalEl || !form) {
                        console.error('❌ UIFormulaireClasse : #classeModal ou #classeForm introuvable');
                        return;
                    }

                    // ---------- Ouverture ----------
                    window.openClasseModal = function() {
                        modalEl.classList.add('ufc-show');
                        document.body.style.overflow = 'hidden';
                        const first = form.querySelector('input:not([type=hidden]):not([readonly]), select');
                        if (first) setTimeout(function() { first.focus(); }, 60);
                    };

                    // ---------- Fermeture ----------
                    window.closeClasseModal = function() {
                        modalEl.classList.remove('ufc-show');
                        document.body.style.overflow = '';
                    };

                    // ---------- Reset (mode création) ----------
                    window.resetClasseForm = function() {
                        form.reset();
                        if (codeField) {
                            codeField.readOnly = true;
                            codeField.value = '';
                            codeField.placeholder = 'Généré automatiquement';
                        }
                        const niveau = document.getElementById('niveauClasse');
                        if (niveau) niveau.value = '1';
                        const capacite = document.getElementById('capaciteClasse');
                        if (capacite) capacite.value = '40';
                        const moy = form.querySelector('input[name="moyennePassage"]');
                        if (moy) moy.value = '10';

                        if (actionField) actionField.value = 'add';
                        if (modeField)   modeField.value   = 'add';
                        if (titleText)   titleText.textContent  = 'Nouvelle classe';
                        if (submitText)  submitText.textContent = 'Enregistrer';
                    };

                    // ---------- Préparer l'édition ----------
                    window.prepareEditClasse = function(data) {
                        const set = function(id, v) {
                            const el = document.getElementById(id);
                            if (el) el.value = v;
                        };

                        if (codeField) {
                            codeField.value = data.codeClasse || '';
                            codeField.readOnly = true;
                        }

                        set('nomClasse', data.nomClasse || '');

                        const niveau = document.getElementById('niveauClasse');
                        if (niveau) {
                            const n = parseInt(data.niveauClasse, 10);
                            niveau.value = (!isNaN(n) && n >= 1 && n <= 12) ? String(n) : '1';
                        }

                        const cap = document.getElementById('capaciteClasse');
                        if (cap) {
                            const c = parseInt(data.capaciteClasse, 10);
                            cap.value = (!isNaN(c) && c > 0) ? String(c) : '40';
                        }

                        const moy = form.querySelector('input[name="moyennePassage"]');
                        if (moy && data.moyennePassage != null) {
                            moy.value = String(data.moyennePassage);
                        }

                        set('anneeAcademique', data.anneeAcademique || '');
                        set('periode',         data.periode || '');
                        set('promotion',       data.promotion || '');
                        set('statut',          data.statut || 'ACTIF');

                        if (actionField) actionField.value = 'update';
                        if (modeField)   modeField.value   = 'update';
                        if (titleText)   titleText.textContent  = 'Modifier la classe';
                        if (submitText)  submitText.textContent = 'Mettre à jour';
                    };

                    // ---------- openEditClasseModal ----------
                    window.openEditClasseModal = function(code) {
                        const tr = document.querySelector('tr[data-code="' + code + '"]');
                        if (!tr) {
                            console.warn('Ligne introuvable pour code=' + code);
                            return;
                        }
                        fetch('/admin/classes?ajax=1&editCode=' + encodeURIComponent(code), {
                            headers: {
                                'Accept': 'application/json',
                                'X-Requested-With': 'XMLHttpRequest'
                            }
                        })
                            .then(function(r) { return r.ok ? r.json() : null; })
                            .then(function(data) {
                                if (data && data.success && data.classe) {
                                    window.prepareEditClasse(data.classe);
                                } else {
                                    window.prepareEditClasse({
                                        codeClasse:      tr.dataset.code,
                                        nomClasse:       tr.dataset.nom,
                                        niveauClasse:    tr.dataset.niveau,
                                        anneeAcademique: tr.dataset.annee,
                                        periode:         tr.dataset.periode,
                                        promotion:       tr.dataset.promotion,
                                        capaciteClasse:  tr.dataset.capacite,
                                        statut:          tr.dataset.statut
                                    });
                                }
                                window.openClasseModal();
                            })
                            .catch(function() {
                                window.prepareEditClasse({
                                    codeClasse:      tr.dataset.code,
                                    nomClasse:       tr.dataset.nom,
                                    niveauClasse:    tr.dataset.niveau,
                                    anneeAcademique: tr.dataset.annee,
                                    periode:         tr.dataset.periode,
                                    promotion:       tr.dataset.promotion,
                                    capaciteClasse:  tr.dataset.capacite,
                                    statut:          tr.dataset.statut
                                });
                                window.openClasseModal();
                            });
                    };

                    // ---------- Fermeture au clic sur le fond ----------
                    modalEl.addEventListener('click', function(e) {
                        if (e.target === modalEl) window.closeClasseModal();
                    });

                    // ---------- Fermeture par Échap ----------
                    document.addEventListener('keydown', function(e) {
                        if (e.key === 'Escape' && modalEl.classList.contains('ufc-show')) {
                            window.closeClasseModal();
                        }
                    });

                    // ---------- Garde-fou : empêcher la saisie manuelle du code ----------
                    if (codeField) {
                        codeField.addEventListener('keydown', function(e) {
                            const allowed = ['Tab','ArrowLeft','ArrowRight','Home','End',
                                             'Control','Meta','Shift','Alt'];
                            if (allowed.indexOf(e.key) === -1) e.preventDefault();
                        });
                    }

                    console.log('✅ UIFormulaireClasse : modal autonome prêt');
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initModal);
                } else {
                    initModal();
                }
            })();
            </script>
            """);
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
}