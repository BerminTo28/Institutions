import com.google.gson.Gson;

public class UIBulletins {

    private static final Gson GSON = new Gson();

    public static String rendrePage(String institutionId) {
        return rendrePage(institutionId, "");
    }

    public static String rendrePage(String institutionId, String messageErreur) {
        String inst = institutionId == null ? "ADMIN12345" : institutionId;
        String err  = messageErreur == null ? "" : messageErreur;
        String instJson = GSON.toJson(inst);
        String errJson  = GSON.toJson(err);

        String html = """
<!DOCTYPE html>
<html lang="fr"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<title>Gestion des Bulletins</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
:root {
    --safe-top: env(safe-area-inset-top, 0px);
    --safe-bottom: env(safe-area-inset-bottom, 0px);
    --safe-left: env(safe-area-inset-left, 0px);
    --safe-right: env(safe-area-inset-right, 0px);
}

/* ============================================================
   RESET + PLEIN ÉCRAN
   ============================================================ */
*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

html, body {
    width: 100%;
    min-width: 320px;
    height: 100vh;
    height: 100dvh;
    min-height: 100vh;
    min-height: 100dvh;
    overflow: hidden;
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: #f8fafc;
    display: flex;
    flex-direction: column;
    -webkit-text-size-adjust: 100%;
    -webkit-tap-highlight-color: transparent;
    overscroll-behavior: none;
}

/* ============================================================
   HEADER
   ============================================================ */
.header {
    background: linear-gradient(135deg, #0f172a, #1e293b);
    padding: 10px 20px;
    padding-top: calc(10px + var(--safe-top));
    padding-left: calc(20px + var(--safe-left));
    padding-right: calc(20px + var(--safe-right));
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;
    box-shadow: 0 4px 12px -4px rgba(0,0,0,.15);
    z-index: 10;
    gap: 12px;
    flex-wrap: wrap;
    min-height: 52px;
}
.header-title {
    color: #fff;
    font-size: 17px;
    font-weight: 800;
    display: flex;
    align-items: center;
    gap: 10px;
}
.header-title i { color: #60a5fa; }
.header-admin {
    color: #cbd5e1;
    font-size: 12px;
    font-weight: 500;
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(255,255,255,.07);
    padding: 5px 12px;
    border-radius: 20px;
}
.header-admin i { color: #facc15; }

/* ============================================================
   CONTAINER — occupe tout l'espace restant
   ============================================================ */
.container {
    flex: 1;
    min-height: 0;
    max-width: none;
    width: 100%;
    margin: 0;
    padding: 8px;
    padding-left: calc(8px + var(--safe-left));
    padding-right: calc(8px + var(--safe-right));
    padding-bottom: calc(8px + var(--safe-bottom));
    display: flex;
    flex-direction: column;
    gap: 8px;
    overflow: hidden;
}

/* ============================================================
   ALERT
   ============================================================ */
.alert-error {
    background: #fef2f2;
    color: #991b1b;
    padding: 8px 14px;
    border-radius: 10px;
    border: 1px solid #fecaca;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 12.5px;
    flex-shrink: 0;
}

/* ============================================================
   FILTRES
   ============================================================ */
.filters-card {
    background: #fff;
    border-radius: 12px;
    padding: 10px 14px;
    border: 1px solid #e2e8f0;
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 10px;
    box-shadow: 0 2px 8px -2px rgba(0,0,0,.04);
    flex-shrink: 0;
}
.filter-group {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1 1 130px;
    min-width: 0;
}
.filter-group label {
    font-size: 10px;
    font-weight: 700;
    color: #475569;
    text-transform: uppercase;
    letter-spacing: .5px;
    display: flex;
    align-items: center;
    gap: 4px;
}
.filter-group label i { color: #3b82f6; }
.filter-group select,
.filter-group input[type=text] {
    padding: 7px 10px;
    border: 1.5px solid #cbd5e1;
    border-radius: 8px;
    font-size: 12.5px;
    background: #f8fafc;
    color: #0f172a;
    font-weight: 500;
    transition: .15s;
    font-family: inherit;
    height: 34px;
}
.filter-group select:focus,
.filter-group input[type=text]:focus {
    outline: none;
    border-color: #2563eb;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(37,99,235,.12);
}

.session-checkboxes {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
    padding: 4px 7px;
    background: #f8fafc;
    border: 1.5px solid #cbd5e1;
    border-radius: 8px;
    min-height: 34px;
    align-items: center;
    max-height: 58px;
    overflow-y: auto;
}
.session-checkboxes label {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    font-weight: 500;
    cursor: pointer;
    padding: 3px 7px;
    background: #fff;
    border-radius: 5px;
    border: 1px solid #e2e8f0;
    text-transform: none;
    letter-spacing: 0;
}
.session-checkboxes label:hover { background: #f1f5f9; }
.session-checkboxes input {
    width: 13px;
    height: 13px;
    cursor: pointer;
    accent-color: #2563eb;
}
.session-checkboxes .pending {
    color: #94a3b8;
    font-size: 11px;
    font-style: italic;
}

/* ============================================================
   BOUTONS
   ============================================================ */
.btn {
    padding: 7px 15px;
    border: none;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
    transition: .15s;
    font-size: 12.5px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-family: inherit;
    height: 34px;
    white-space: nowrap;
}
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn-success {
    background: linear-gradient(135deg, #16a34a, #15803d);
    color: #fff;
    box-shadow: 0 2px 8px rgba(22,163,74,.25);
}
.btn-success:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(22,163,74,.35);
}
.btn-secondary { background: #475569; color: #fff; }
.btn-secondary:hover:not(:disabled) { background: #334155; transform: translateY(-1px); }
.btn-ghost {
    background: transparent;
    color: #475569;
    border: 1.5px solid #cbd5e1;
    padding: 6px 11px;
}
.btn-ghost:hover:not(:disabled) { background: #f1f5f9; border-color: #94a3b8; }

/* ============================================================
   TOOLBAR
   ============================================================ */
.table-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    flex-shrink: 0;
}
.table-toolbar .left {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 800;
    color: #0f172a;
    font-size: 14px;
}
.table-toolbar .left i { color: #2563eb; }
.table-toolbar .right { display: flex; gap: 6px; }

/* ============================================================
   TABLEAU
   ============================================================ */
.table-container {
    flex: 1;
    min-height: 0;
    overflow: auto;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 4px 12px -4px rgba(0,0,0,.05);
    -webkit-overflow-scrolling: touch;
}
.table-container::-webkit-scrollbar { width: 8px; height: 8px; }
.table-container::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }

table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
thead th {
    position: sticky;
    top: 0;
    background: #0f172a;
    color: #fff;
    padding: 10px 12px;
    text-align: center;
    font-weight: 600;
    z-index: 2;
    white-space: nowrap;
    font-size: 12px;
}
tbody td {
    padding: 9px 12px;
    border-bottom: 1px solid #f1f5f9;
    text-align: center;
    color: #1e293b;
    font-size: 12.5px;
}
tbody tr:hover { background: #f8fafc; }
tbody tr:last-child td { border-bottom: none; }

.badge-admis {
    background: #dcfce7;
    color: #166534;
    padding: 3px 9px;
    border-radius: 12px;
    font-size: 10.5px;
    font-weight: 700;
    display: inline-block;
    border: 1px solid #bbf7d0;
}
.badge-echec {
    background: #fee2e2;
    color: #991b1b;
    padding: 3px 9px;
    border-radius: 12px;
    font-size: 10.5px;
    font-weight: 700;
    display: inline-block;
    border: 1px solid #fecaca;
}
.loading {
    text-align: center;
    padding: 40px;
    color: #94a3b8;
    font-weight: 500;
}
.loading i { font-size: 20px; margin-right: 8px; color: #3b82f6; }

/* ============================================================
   STATUS BAR
   ============================================================ */
.status-bar {
    background: #fff;
    border-radius: 10px;
    padding: 8px 14px;
    border: 1px solid #e2e8f0;
    font-size: 12.5px;
    color: #475569;
    font-weight: 500;
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-shrink: 0;
    gap: 10px;
    flex-wrap: wrap;
}

/* ============================================================
   BOTTOM ACTIONS
   ============================================================ */
.bottom-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
    flex-shrink: 0;
}

/* ============================================================
   TOAST
   ============================================================ */
.toast {
    position: fixed;
    bottom: calc(20px + var(--safe-bottom));
    right: calc(20px + var(--safe-right));
    background: #0f172a;
    color: #fff;
    padding: 12px 18px;
    border-radius: 12px;
    z-index: 1100;
    animation: fadeInOut 3s ease;
    box-shadow: 0 10px 25px -5px rgba(0,0,0,.2);
    font-size: 13px;
    max-width: calc(100vw - 40px);
}
.toast.success { background: #16a34a; }
.toast.error { background: #dc2626; }
@keyframes fadeInOut {
    0%{opacity:0;transform:translateY(20px)}
    15%{opacity:1;transform:translateY(0)}
    85%{opacity:1}
    100%{opacity:0;transform:translateY(20px)}
}

/* ============================================================
   RESPONSIVE — 4K / ULTRA-WIDE
   ============================================================ */
@media (min-width: 1920px) {
    .header { padding: 14px 28px; }
    .header-title { font-size: 20px; }
    .container { padding: 12px; gap: 12px; }
    .filters-card { padding: 14px 18px; }
    .filter-group select,
    .filter-group input[type=text] { height: 38px; font-size: 13.5px; }
    .btn { height: 38px; font-size: 13.5px; }
    table { font-size: 13.5px; }
    thead th { padding: 12px 14px; font-size: 13px; }
    tbody td { padding: 11px 14px; font-size: 13.5px; }
}

/* ============================================================
   RESPONSIVE — TABLETTE
   ============================================================ */
@media (max-width: 900px) {
    .filter-group { min-width: 110px; }
    .header { padding: 8px 14px; }
    .header-title { font-size: 15px; }
    .container { padding: 6px; gap: 6px; }
}

/* ============================================================
   RESPONSIVE — MOBILE
   ============================================================ */
@media (max-width: 768px) {
    html, body { overflow: auto; height: auto; min-height: 100dvh; }

    .header {
        padding: 8px 12px;
        min-height: 48px;
    }
    .header-title { font-size: 14px; }
    .header-admin { font-size: 11px; padding: 4px 10px; }

    .container {
        height: auto;
        min-height: 100dvh;
        overflow: visible;
        padding: 6px;
        gap: 6px;
    }

    .filters-card {
        flex-direction: column;
        align-items: stretch;
        padding: 10px;
        gap: 8px;
    }
    .filter-group { flex: 1 1 auto; min-width: 0; }
    .filter-group select,
    .filter-group input[type=text] { width: 100%; }
    .filters-card > div[style*="max-width"] { max-width: none !important; }
    .filters-card > div[style*="flex:0"] { flex: 1 1 auto !important; min-width: 0 !important; }
    .filters-card #btnRefresh { width: 100%; }

    .session-checkboxes { max-height: none; }

    .table-toolbar { flex-direction: column; align-items: stretch; gap: 6px; }
    .table-toolbar .right { justify-content: stretch; }
    .table-toolbar .right .btn { flex: 1; justify-content: center; }

    .table-container {
        flex: none;
        max-height: 65vh;
        border-radius: 10px;
    }
    table { font-size: 11.5px; }
    thead th { padding: 8px 8px; font-size: 10.5px; }
    tbody td { padding: 7px 8px; font-size: 11.5px; }
    .badge-admis, .badge-echec { font-size: 10px; padding: 2px 8px; }

    .status-bar { flex-direction: column; align-items: flex-start; gap: 4px; }

    .bottom-actions {
        flex-direction: column;
        gap: 6px;
    }
    .bottom-actions .btn {
        width: 100%;
        justify-content: center;
    }

    .toast {
        bottom: calc(12px + var(--safe-bottom));
        right: calc(12px + var(--safe-right));
        left: calc(12px + var(--safe-left));
        max-width: none;
    }
}

/* ============================================================
   RESPONSIVE — TRÈS PETIT MOBILE
   ============================================================ */
@media (max-width: 400px) {
    .header-title { font-size: 13px; }
    .header-title i { font-size: 12px; }
    .header-admin { font-size: 10px; padding: 3px 8px; }
    table { font-size: 11px; }
    thead th { padding: 6px 6px; font-size: 10px; }
    tbody td { padding: 6px 6px; font-size: 11px; }
    .badge-admis, .badge-echec { font-size: 9.5px; padding: 2px 6px; }
    .btn { font-size: 12px; padding: 6px 12px; }
}

/* ============================================================
   RESPONSIVE — PAYSAGE MOBILE
   ============================================================ */
@media (max-height: 500px) and (orientation: landscape) {
    .header {
        padding: 4px 12px;
        min-height: 40px;
    }
    .header-title { font-size: 13px; }
    .header-admin { display: none; }
    .container { padding: 4px; gap: 4px; }
    .filters-card { padding: 6px 10px; gap: 6px; }
    .filter-group label { font-size: 9px; }
    .filter-group select,
    .filter-group input[type=text] { height: 30px; padding: 4px 8px; font-size: 11.5px; }
    .btn { height: 30px; padding: 4px 12px; font-size: 11.5px; }
    .table-toolbar .left { font-size: 12px; }
    .table-container { max-height: 50vh; }
    .status-bar { padding: 6px 12px; font-size: 11.5px; }
}
</style></head><body>
<header class="header">
  <div class="header-title"><i class="fas fa-file-pdf"></i> Gestion des Bulletins</div>
  <div class="header-admin"><i class="fas fa-user-circle"></i> <span>__INST_HTML__</span></div>
</header>
<div class="container">
__ERR_BLOCK__
  <div class="filters-card">
    <div class="filter-group" style="max-width:180px">
      <label><i class="fas fa-calendar"></i> Année</label>
      <select id="filterAnnee"><option value="">--</option></select>
    </div>
    <div class="filter-group" style="flex:2">
      <label><i class="fas fa-clock"></i> Périodes <span id="sessionCount" style="color:#94a3b8;text-transform:none"></span></label>
      <div id="sessionCheckboxes" class="session-checkboxes"><span class="pending">Sélectionnez une année d'abord</span></div>
    </div>
    <div class="filter-group" style="max-width:200px">
      <label><i class="fas fa-school"></i> Classe</label>
      <select id="filterClasse"><option value="">--</option></select>
    </div>
    <div class="filter-group" style="max-width:180px">
      <label><i class="fas fa-hashtag"></i> Numéro</label>
      <input type="text" id="inputNumero" placeholder="2025-001">
    </div>
    <div class="filter-group" style="max-width:200px">
      <label><i class="fas fa-tag"></i> Référence</label>
      <input type="text" id="inputReference" placeholder="CTPEA/REL/2025">
    </div>
    <div class="filter-group" style="flex:0;min-width:auto">
      <button class="btn btn-ghost" id="btnRefresh" title="Actualiser"><i class="fas fa-sync-alt"></i></button>
    </div>
  </div>

  <div class="table-toolbar">
    <div class="left"><i class="fas fa-users"></i> Liste des étudiants</div>
    <div class="right">
      <button class="btn btn-secondary" id="btnSelectAll" style="padding:5px 12px;font-size:12px"><i class="fas fa-check-double"></i> TOUT</button>
      <button class="btn btn-secondary" id="btnDeselectAll" style="padding:5px 12px;font-size:12px"><i class="fas fa-times"></i> AUCUN</button>
    </div>
  </div>

  <div class="table-container">
    <table>
      <thead><tr>
        <th style="width:40px"><input type="checkbox" id="selectAll" style="width:15px;height:15px;cursor:pointer;accent-color:#2563eb"></th>
        <th style="width:50px">N°</th><th>Nom</th><th>Prénom</th><th>Classe</th><th>Moyenne</th><th>Décision</th>
      </tr></thead>
      <tbody id="tableBody">
        <tr><td colspan="7" class="loading"><i class="fas fa-filter"></i> Sélectionnez une année, au moins une période et une classe</td></tr>
      </tbody>
    </table>
  </div>

  <div class="status-bar">
    <span id="statusLabel"><i class="fas fa-check-circle" style="color:#16a34a"></i> Prêt</span>
    <span id="countLabel">0 étudiant(s)</span>
  </div>
  <div class="bottom-actions">
    <button class="btn btn-success" id="btnGenerer"><i class="fas fa-file-pdf"></i> GÉNÉRER LES BULLETINS</button>
    <button class="btn btn-secondary" id="btnReset"><i class="fas fa-undo"></i> RÉINITIALISER</button>
  </div>
</div>
<script>
const institutionId = __INST_JSON__;
const messageErreur = __ERR_JSON__;
let etudiantsData=[],currentAnnee='',currentSessions=[],currentClasse='',chargement=false,autoTimer=null;

const $ = id => document.getElementById(id);
const escapeHtml = s => !s ? '' : String(s).replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[m]);
const getSessions = () => [...document.querySelectorAll('#sessionCheckboxes input:checked')].map(c=>c.value);
const getEtudiantIds = () => [...document.querySelectorAll('.etudiant-check:checked')].map(c=>c.getAttribute('data-id'));
const filtresComplets = () => $('filterAnnee').value && getSessions().length>0 && $('filterClasse').value;

function showToast(msg,type){const t=document.createElement('div');t.className='toast '+type;t.textContent=msg;document.body.appendChild(t);setTimeout(()=>t.remove(),3000);}
function updateStatus(msg,type){
    const icon = type==='success'?'fa-check-circle':type==='error'?'fa-exclamation-circle':'fa-spinner fa-spin';
    const color = type==='success'?'#16a34a':type==='error'?'#dc2626':'#64748b';
    $('statusLabel').innerHTML = `<i class="fas ${icon}" style="color:${color}"></i> ${msg}`;
}
function majCompteurSessions(){const n=getSessions().length;$('sessionCount').textContent=n>0?`(${n})`:'';}

async function loadAnnees(){
    try{
        const d=await(await fetch('/admin/bulletins/api/annees')).json();
        if(!d.success)return showToast('Erreur chargement années','error');
        $('filterAnnee').innerHTML='<option value="">--</option>'+d.data.map(a=>`<option value="${escapeHtml(a)}">${escapeHtml(a)}</option>`).join('');
    }catch(e){showToast('Erreur chargement années','error');}
}
async function loadSessions(annee){
    const c=$('sessionCheckboxes');$('sessionCount').textContent='';
    if(!annee){c.innerHTML='<span class="pending">Sélectionnez une année d\\'abord</span>';return;}
    try{
        const d=await(await fetch('/admin/bulletins/api/sessions?annee='+encodeURIComponent(annee))).json();
        if(!d.success)return showToast('Erreur chargement sessions','error');
        c.innerHTML = d.data.length===0 ? '<span class="pending">Aucune période trouvée</span>'
            : d.data.map(s=>`<label><input type="checkbox" value="${escapeHtml(s.nom)}" checked>${escapeHtml(s.nom)}${s.active?' ●':''}</label>`).join('');
        planifierChargement();
    }catch(e){showToast('Erreur chargement sessions','error');}
}
async function loadClasses(){
    try{
        const d=await(await fetch('/admin/bulletins/api/classes')).json();
        if(!d.success)return showToast('Erreur chargement classes','error');
        $('filterClasse').innerHTML='<option value="">--</option>'+d.data.map(c=>`<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
    }catch(e){showToast('Erreur chargement classes','error');}
}

function planifierChargement(){
    if(autoTimer)clearTimeout(autoTimer);
    autoTimer=setTimeout(()=>{
        majCompteurSessions();
        if(filtresComplets())loadEtudiants();
        else updateStatus('Complétez les filtres (année, période(s), classe)','loading');
    },250);
}

async function loadEtudiants(){
    if(chargement||!filtresComplets())return;
    chargement=true;
    currentAnnee=$('filterAnnee').value;
    currentSessions=getSessions();
    currentClasse=$('filterClasse').value;
    updateStatus('Chargement...','loading');
    $('btnRefresh').disabled=true;

    try{
        const map=new Map();
        for(const s of currentSessions){
            const url=`/admin/bulletins/api/etudiants?annee=${encodeURIComponent(currentAnnee)}&session=${encodeURIComponent(s)}&classe=${encodeURIComponent(currentClasse)}`;
            const d=await(await fetch(url)).json();
            if(d.success&&d.data)d.data.forEach(e=>{if(!map.has(e.id))map.set(e.id,e);});
        }
        etudiantsData=[...map.values()];
        renderTable(etudiantsData);
        updateStatus(etudiantsData.length===0?'Aucun étudiant trouvé':'✅ '+etudiantsData.length+' étudiant(s) chargé(s)',etudiantsData.length===0?'error':'success');
    }catch(e){showToast('Erreur de chargement','error');updateStatus('❌ Erreur','error');}
    finally{chargement=false;$('btnRefresh').disabled=false;}
}

function renderTable(list){
    const tb=$('tableBody');
    $('countLabel').textContent=list.length+' étudiant(s)';
    if(!list||list.length===0){tb.innerHTML='<tr><td colspan="7" class="loading">Aucun étudiant trouvé</td></tr>';return;}
    tb.innerHTML=list.map((e,i)=>`
        <tr>
            <td><input type="checkbox" class="etudiant-check" data-id="${escapeHtml(e.id)}" ${e.selected?'checked':''} style="width:15px;height:15px;cursor:pointer;accent-color:#2563eb"></td>
            <td>${i+1}</td><td><strong>${escapeHtml(e.nom)}</strong></td><td>${escapeHtml(e.prenom)}</td>
            <td>${escapeHtml(e.classe)}</td><td><strong>${e.moyenne||'-'}</strong></td>
            <td><span class="badge-${e.decisionClass||'echec'}">${e.decision||'-'}</span></td>
        </tr>`).join('');
    $('selectAll').checked=false;
}

async function genererBulletins(){
    const ids=getEtudiantIds(),sessions=getSessions();
    if(ids.length===0)return showToast('Aucun étudiant sélectionné','error');
    if(!currentAnnee||sessions.length===0||!currentClasse)return showToast('Filtres non définis','error');

    const p=new URLSearchParams({annee:currentAnnee,session:sessions.join(','),classe:currentClasse,etudiants:ids.join(',')});
    const num=$('inputNumero').value.trim(),ref=$('inputReference').value.trim();
    if(num)p.append('numero',num);
    if(ref)p.append('reference',ref);

    updateStatus('⏳ Génération en cours...','loading');
    $('btnGenerer').disabled=true;
    try{
        const d=await(await fetch('/admin/bulletins/api/generer',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:p})).json();
        if(!d.success){showToast('❌ '+(d.error||'Erreur inconnue'),'error');return updateStatus('❌ Échec','error');}
        const f=d.fichiers||[];
        if(f.length===0)return showToast('Aucun fichier généré','error');
        for(let i=0;i<f.length;i++){
            window.open('/admin/bulletins/api/telecharger?fichier='+encodeURIComponent(f[i]),'_blank');
            if(i<f.length-1)await new Promise(r=>setTimeout(r,800));
        }
        showToast('✅ '+f.length+' bulletin(s) généré(s)','success');
        updateStatus('✅ '+f.length+' bulletin(s) téléchargé(s)','success');
    }catch(e){showToast('Erreur : '+e.message,'error');updateStatus('❌ Erreur','error');}
    finally{$('btnGenerer').disabled=false;}
}

$('filterAnnee').addEventListener('change',e=>{
    $('filterClasse').value='';
    $('tableBody').innerHTML='<tr><td colspan="7" class="loading"><i class="fas fa-spinner fa-spin"></i> Chargement...</td></tr>';
    loadSessions(e.target.value);
});
$('filterClasse').addEventListener('change',planifierChargement);
$('sessionCheckboxes').addEventListener('change',e=>{if(e.target.matches('input[type=checkbox]'))planifierChargement();});
$('btnRefresh').addEventListener('click',()=>filtresComplets()?loadEtudiants():showToast('Complétez les filtres','error'));
$('btnGenerer').addEventListener('click',genererBulletins);
$('btnReset').addEventListener('click',()=>{
    $('filterAnnee').value='';
    $('sessionCheckboxes').innerHTML='<span class="pending">Sélectionnez une année d\\'abord</span>';
    $('sessionCount').textContent='';
    ['filterClasse','inputNumero','inputReference'].forEach(id=>$(id).value='');
    $('tableBody').innerHTML='<tr><td colspan="7" class="loading"><i class="fas fa-filter"></i> Sélectionnez une année, au moins une période et une classe</td></tr>';
    $('countLabel').textContent='0 étudiant(s)';
    etudiantsData=[];currentSessions=[];
    if(autoTimer)clearTimeout(autoTimer);
    updateStatus('Prêt','success');
});
$('selectAll').addEventListener('change',function(){document.querySelectorAll('.etudiant-check').forEach(cb=>cb.checked=this.checked);});
$('btnSelectAll').addEventListener('click',()=>{document.querySelectorAll('.etudiant-check').forEach(cb=>cb.checked=true);$('selectAll').checked=true;});
$('btnDeselectAll').addEventListener('click',()=>{document.querySelectorAll('.etudiant-check').forEach(cb=>cb.checked=false);$('selectAll').checked=false;});

loadAnnees();loadClasses();
</script>
</body></html>
""";

        return html
                .replace("__INST_HTML__", echapperHtml(inst))
                .replace("__ERR_BLOCK__", err.isEmpty() ? "" :
                        "<div class=\"alert-error\"><i class=\"fas fa-exclamation-circle\"></i> " + echapperHtml(err) + "</div>")
                .replace("__INST_JSON__", instJson)
                .replace("__ERR_JSON__", errJson);
    }

    private static String echapperHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}