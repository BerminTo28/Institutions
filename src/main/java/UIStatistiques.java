public class UIStatistiques {

    public static String rendrePage(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            institutionId = "Institution";
        }

        return """
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<title>Statistiques — DataCenter</title>
<style>
*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

:root {
    --red-900: #7f1d1d; --red-700: #b91c1c; --red-600: #dc2626;
    --red-500: #ef4444; --red-300: #f87171;
    --blue-900: #1e3a8a; --blue-700: #1d4ed8; --blue-600: #2563eb;
    --blue-500: #3b82f6; --blue-300: #60a5fa;
    --bg: #f5f7fa; --card: #ffffff; --border: #e2e8f0;
    --text: #1e293b; --muted: #64748b;
    --shadow: 0 2px 12px rgba(15,23,42,0.06);
    --radius: 16px;
    --safe-top: env(safe-area-inset-top, 0px);
    --safe-bottom: env(safe-area-inset-bottom, 0px);
}

html, body {
    width: 100%; min-width: 320px;
    height: 100vh; height: 100dvh;
    min-height: 100vh; min-height: 100dvh;
    margin: 0; padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    background: var(--bg); color: var(--text);
    overflow: hidden;
    -webkit-text-size-adjust: 100%;
    overscroll-behavior: none;
}

/* ---------- Icônes SVG inline ---------- */
.icon { width: 1em; height: 1em; display: inline-block; vertical-align: -0.125em; fill: currentColor; }
.icon-lg { width: 2rem; height: 2rem; }

/* ---------- LAYOUT ---------- */
.page {
    width: 100%; height: 100vh; height: 100dvh;
    display: flex; flex-direction: column;
    padding: 10px;
    padding-top: calc(10px + var(--safe-top));
    padding-bottom: calc(10px + var(--safe-bottom));
    gap: 10px;
}

/* ---------- TOP BAR ---------- */
.top-bar {
    background: var(--card); border: 1px solid var(--border);
    border-radius: var(--radius); box-shadow: var(--shadow);
    padding: 10px 16px;
    display: flex; justify-content: center; align-items: center;
    gap: 16px; flex-wrap: wrap; flex-shrink: 0;
    text-align: center;
}
.brand {
    display: flex; align-items: center; gap: 10px;
    font-weight: 800; font-size: 0.95rem;
    justify-content: center;
}
.brand-icon {
    width: 36px; height: 36px; border-radius: 10px;
    background: linear-gradient(135deg, var(--blue-700), var(--red-700));
    color: #fff; display: flex; align-items: center; justify-content: center;
    font-size: 0.95rem; box-shadow: 0 4px 12px rgba(29,78,216,0.25);
}
.institution-label {
    font-size: 0.72rem; color: var(--muted); font-weight: 600;
    background: #f1f5f9; padding: 4px 10px; border-radius: 50px;
    white-space: nowrap;
}
.filters {
    display: flex; gap: 8px; align-items: center;
    flex-wrap: wrap; justify-content: center;
}
.filter-select {
    padding: 8px 12px; border: 1.5px solid var(--border); border-radius: 10px;
    font-size: 0.8rem; font-family: inherit; font-weight: 500;
    background: #f8fafc; min-width: 150px; height: 38px;
    cursor: pointer; color: var(--text); outline: none; transition: 0.15s;
    text-align: center;
}
.filter-select:focus {
    border-color: var(--blue-600);
    box-shadow: 0 0 0 3px rgba(37,99,235,0.12);
    background: #fff;
}
.btn-actualiser {
    background: linear-gradient(135deg, var(--blue-700), var(--blue-600));
    color: white; border: none; padding: 0 20px; height: 38px;
    border-radius: 10px; font-weight: 700; font-size: 0.78rem;
    font-family: inherit; cursor: pointer; transition: 0.15s;
    display: inline-flex; align-items: center; gap: 8px;
}
.btn-actualiser:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(29,78,216,0.3); }
.btn-actualiser:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

.btn-reset {
    background: #f1f5f9; color: var(--muted);
    border: 1.5px solid var(--border);
    padding: 0 16px; height: 38px; border-radius: 10px;
    font-weight: 700; font-size: 0.78rem; font-family: inherit;
    cursor: pointer; transition: 0.15s;
    display: inline-flex; align-items: center; gap: 8px;
}
.btn-reset:hover {
    background: #e2e8f0; color: var(--red-600);
    border-color: var(--red-300); transform: translateY(-1px);
}
.btn-reset:active { transform: translateY(0); }

/* ---------- DASHBOARD ---------- */
.dashboard {
    flex: 1; min-height: 0;
    overflow-y: auto; overflow-x: hidden;
    display: flex; flex-direction: column;
    gap: 12px; padding-right: 4px;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: thin;
}
.dashboard::-webkit-scrollbar { width: 8px; }
.dashboard::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }

/* ---------- KPI ---------- */
.kpi-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 12px; flex-shrink: 0;
}
.kpi-card {
    background: var(--card); border-radius: 14px; padding: 14px 16px;
    border: 1px solid var(--border); transition: 0.15s;
    position: relative; overflow: hidden;
    box-shadow: var(--shadow);
    text-align: center;
}
.kpi-card::before {
    content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
    background: linear-gradient(90deg, var(--blue-600), var(--red-600));
}
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(15,23,42,0.08); }
.kpi-label {
    font-size: 0.66rem; color: var(--muted); font-weight: 700;
    text-transform: uppercase; letter-spacing: 0.4px; margin-bottom: 6px;
    text-align: center;
}
.kpi-value {
    font-size: clamp(1.1rem, 1.6vw, 1.5rem); font-weight: 800;
    color: var(--text); line-height: 1.1; word-break: break-word;
    text-align: center;
}
.kpi-value.text { font-size: 1.05rem; }

/* ---------- CHARTS ---------- */
.charts-grid {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: 12px;
}
.chart-card {
    background: var(--card); border-radius: 14px; padding: 16px;
    border: 1px solid var(--border); box-shadow: var(--shadow);
    display: flex; flex-direction: column; gap: 10px;
    min-height: 340px;
    grid-column: span 6;
}
.chart-card h3 {
    font-size: 0.9rem; font-weight: 700; color: var(--text);
    display: flex; align-items: center; justify-content: center;
    gap: 8px; margin: 0;
    text-align: center;
    border-bottom: 3px solid var(--red-600);
    padding-bottom: 8px;
    padding-top: 2px;
}
.chart-card h3 .icon { color: var(--red-600); font-size: 0.9rem; }
.chart-body {
    flex: 1; position: relative; min-height: 0;
}
.chart-body canvas { max-width: 100% !important; max-height: 100% !important; display: block; }
.chart-card.full  { grid-column: span 12; min-height: 400px; }
.chart-card.half  { grid-column: span 6; }

/* ---------- TABLE ---------- */
.stats-table {
    width: 100%; border-collapse: collapse; background: #fff;
    border-radius: 12px; overflow: hidden; border: 1px solid var(--border);
    font-size: 0.82rem;
}
.stats-table th {
    background: linear-gradient(135deg, var(--blue-700), var(--blue-900));
    color: #fff; padding: 12px; font-weight: 700; text-align: center;
    font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.4px;
}
.stats-table td {
    padding: 10px 12px; text-align: center;
    border-bottom: 1px solid var(--border); color: var(--text);
}
.stats-table tr:last-child td { border-bottom: none; }
.stats-table tr:hover { background: #f8fafc; }

/* ---------- EMPTY ---------- */
.empty {
    display: flex; flex-direction: column; align-items: center;
    justify-content: center; min-height: 180px;
    color: var(--muted); font-size: 0.85rem; gap: 10px;
    text-align: center;
}
.empty .icon { font-size: 2rem; color: #cbd5e1; }
.empty-overlay {
    position: absolute; inset: 0;
    background: rgba(255,255,255,0.95);
    border-radius: 12px; z-index: 5;
}

/* ---------- RESPONSIVE ---------- */
@media (max-width: 1100px) {
    .chart-card, .chart-card.half, .chart-card.full { grid-column: span 12; }
}
@media (max-width: 768px) {
    html, body { overflow: auto; height: auto; }
    .page { height: auto; min-height: 100dvh; padding: 6px; gap: 8px; }
    .top-bar { padding: 10px 12px; }
    .filters { width: 100%; }
    .filter-select { min-width: 0; flex: 1 1 140px; }
    .btn-actualiser { flex: 1 1 140px; justify-content: center; }
    .btn-reset { flex: 1 1 140px; justify-content: center; }
    .dashboard { overflow: visible; padding-right: 0; }
    .kpi-grid { grid-template-columns: repeat(2, 1fr); gap: 8px; }
    .chart-card { min-height: 280px; padding: 12px; }
    .chart-card.full { min-height: 320px; }
    .stats-table { font-size: 0.72rem; }
    .stats-table th, .stats-table td { padding: 8px 6px; }
}
@media (max-width: 400px) {
    .kpi-grid { grid-template-columns: 1fr; }
    .brand span { display: none; }
}
</style>
</head>
<body>
<div class="page">

    <div class="top-bar">
        <div class="brand">
            <div class="brand-icon">
                <svg class="icon" viewBox="0 0 576 512"><path d="M304 240V16.6c0-9 7-16.6 16-16.6C443.7 0 544 100.3 544 224c0 9-7.6 16-16.6 16H304zM32 160c0-35.3 28.7-64 64-64s64 28.7 64 64v272c0 35.3-28.7 64-64 64s-64-28.7-64-64V160zm128 80c0-35.3 28.7-64 64-64s64 28.7 64 64v192c0 35.3-28.7 64-64 64s-64-28.7-64-64V240z"/></svg>
            </div>
            <span>Tableau de bord</span>
        </div>
        <div class="filters">
            <select id="anneeSelect" class="filter-select"><option>Toutes années</option></select>
            <select id="periodeSelect" class="filter-select"><option>Toutes periodes</option></select>
            <select id="classeSelect" class="filter-select"><option>Toutes classes</option></select>
            <select id="matiereSelect" class="filter-select"><option>Toutes matières</option></select>
            <button class="btn-actualiser" id="btnActualiser">
                <svg class="icon" viewBox="0 0 512 512"><path d="M105.1 202.6c7.7-21.8 20.2-42.3 37.8-59.8c62.5-62.5 163.8-62.5 226.3 0L386.3 160H336c-17.7 0-32 14.3-32 32s14.3 32 32 32H463.5c0 0 0 0 0 0h.4c17.7 0 32-14.3 32-32V64c0-17.7-14.3-32-32-32s-32 14.3-32 32v51.2L414.4 97.6c-87.5-87.5-229.3-87.5-316.8 0C73.2 122 55.6 150.7 44.8 181.4c-5.9 16.7 2.9 34.9 19.5 40.8s34.9-2.9 40.8-19.5zM39 289.3c-5 1.5-9.8 4.2-13.7 8.2c-4 4-6.7 8.8-8.1 14c-.3 1.2-.6 2.5-.8 3.8c-.3 1.7-.4 3.4-.4 5.1V448c0 17.7 14.3 32 32 32s32-14.3 32-32V396.9l17.6 17.5 0 0c87.5 87.4 229.3 87.4 316.7 0c24.4-24.4 42.1-53.1 52.9-83.7c5.9-16.7-2.9-34.9-19.5-40.8s-34.9 2.9-40.8 19.5c-7.7 21.8-20.2 42.3-37.8 59.8c-62.5 62.5-163.8 62.5-226.3 0l-.1-.1L125.6 352H176c17.7 0 32-14.3 32-32s-14.3-32-32-32H48.4c-1.6 0-3.2 .1-4.8 .3s-3.1 .5-4.6 1z"/></svg>
                ACTUALISER
            </button>
            <button class="btn-reset" id="btnReset" title="Réinitialiser les filtres">
                <svg class="icon" viewBox="0 0 512 512"><path d="M125.7 160H176c17.7 0 32 14.3 32 32s-14.3 32-32 32H48c-17.7 0-32-14.3-32-32V64c0-17.7 14.3-32 32-32s32 14.3 32 32v51.2L97.6 97.6c87.5-87.5 229.3-87.5 316.8 0s87.5 229.3 0 316.8s-229.3 87.5-316.8 0c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0c62.5 62.5 163.8 62.5 226.3 0s62.5-163.8 0-226.3s-163.8-62.5-226.3 0L125.7 160z"/></svg>
                RÉINITIALISER
            </button>
        </div>
        <div class="institution-label">__INST__</div>
    </div>

    <div class="dashboard" id="dashboard">
        <div class="kpi-grid" id="kpiGrid"></div>

        <div class="charts-grid">

            <div class="chart-card half">
                <h3>
                    <svg class="icon" viewBox="0 0 512 512"><path d="M16 0H64C88.2 0 108.6 14.4 117.6 34.4L193.7 156.8 264 84C279.9 67.5 304.1 60.9 326 65.5L390.6 78.5C406 81.6 416 95.3 416 111v19.6c0 20.5-12.4 38.8-31.2 46.2L288 208v64h144c26.5 0 48 21.5 48 48v48c0 26.5-21.5 48-48 48H80c-26.5 0-48-21.5-48-48V48c0-26.5 21.5-48 48-48z"/></svg>
                    Répartition par sexe et par classe
                </h3>
                <div class="chart-body"><canvas id="chartGenre"></canvas></div>
            </div>

            <div class="chart-card half">
                <h3>
                    <svg class="icon" viewBox="0 0 640 512"><path d="M144 0a80 80 0 1 1 0 160A80 80 0 1 1 144 0zM512 0a80 80 0 1 1 0 160A80 80 0 1 1 512 0zM0 298.7C0 239.8 47.8 192 106.7 192h42.7c15.9 0 31 3.5 44.6 9.7c-1.3 7.2-1.9 14.7-1.9 22.3c0 38.2 16.8 72.5 43.3 96c-.2 0-.4 0-.7 0H21.3C9.6 320 0 310.4 0 298.7zM405.3 320c-.2 0-.4 0-.7 0c26.6-23.5 43.3-57.8 43.3-96c0-7.6-.7-15-1.9-22.3c13.6-6.3 28.7-9.7 44.6-9.7h42.7C592.2 192 640 239.8 640 298.7c0 11.8-9.6 21.3-21.3 21.3H405.3zM224 224a96 96 0 1 1 192 0 96 96 0 1 1 -192 0zM128 485.3C128 411.7 187.7 352 261.3 352H378.7C452.3 352 512 411.7 512 485.3c0 14.7-11.9 26.7-26.7 26.7H154.7c-14.7 0-26.7-11.9-26.7-26.7z"/></svg>
                    Effectifs par classe
                </h3>
                <div class="chart-body"><canvas id="chartEffectifs"></canvas></div>
            </div>

            <div class="chart-card half">
                <h3>
                    <svg class="icon" viewBox="0 0 512 512"><path d="M32 32c17.7 0 32 14.3 32 32V400c0 8.8 7.2 16 16 16H480c17.7 0 32 14.3 32 32s-14.3 32-32 32H80c-44.2 0-80-35.8-80-80V64C0 46.3 14.3 32 32 32zM160 224c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32s-32-14.3-32-32V256c0-17.7 14.3-32 32-32zm128-64V320c0 17.7-14.3 32-32 32s-32-14.3-32-32V160c0-17.7 14.3-32 32-32s32 14.3 32 32zm64 32c17.7 0 32 14.3 32 32v96c0 17.7-14.3 32-32 32s-32-14.3-32-32V224c0-17.7 14.3-32 32-32zM480 96V320c0 17.7-14.3 32-32 32s-32-14.3-32-32V96c0-17.7 14.3-32 32-32s32 14.3 32 32z"/></svg>
                    Moyennes par classe
                </h3>
                <div class="chart-body"><canvas id="chartMoyennes"></canvas></div>
            </div>

            <div class="chart-card half">
                <h3>
                    <svg class="icon" viewBox="0 0 576 512"><path d="M304 240V16.6c0-9 7-16.6 16-16.6C443.7 0 544 100.3 544 224c0 9-7.6 16-16.6 16H304zM32 160c0-35.3 28.7-64 64-64s64 28.7 64 64v272c0 35.3-28.7 64-64 64s-64-28.7-64-64V160z"/></svg>
                    Répartition des promotions
                </h3>
                <div class="chart-body"><canvas id="chartRepartition"></canvas></div>
            </div>

            <div class="chart-card half">
                <h3>
                    <svg class="icon" viewBox="0 0 512 512"><path d="M64 96c0-17.7 14.3-32 32-32H416c17.7 0 32 14.3 32 32V416c0 17.7-14.3 32-32 32H96c-17.7 0-32-14.3-32-32V96zm80 80c0 17.7 14.3 32 32 32H336c17.7 0 32-14.3 32-32s-14.3-32-32-32H176c-17.7 0-32 14.3-32 32zm0 96c0 17.7 14.3 32 32 32H336c17.7 0 32-14.3 32-32s-14.3-32-32-32H176c-17.7 0-32 14.3-32 32zm0 96c0 17.7 14.3 32 32 32h64c17.7 0 32-14.3 32-32s-14.3-32-32-32H176c-17.7 0-32 14.3-32 32z"/></svg>
                    Évolution des moyennes par année
                </h3>
                <div class="chart-body"><canvas id="chartEvolution"></canvas></div>
            </div>

            <div class="chart-card half" style="min-height:auto;">
                <h3>
                    <svg class="icon" viewBox="0 0 512 512"><path d="M64 256c0 17.7 14.3 32 32 32H416c17.7 0 32-14.3 32-32s-14.3-32-32-32H96c-17.7 0-32 14.3-32 32zM64 384c0 17.7 14.3 32 32 32H416c17.7 0 32-14.3 32-32s-14.3-32-32-32H96c-17.7 0-32 14.3-32 32zM64 128c0 17.7 14.3 32 32 32H416c17.7 0 32-14.3 32-32s-14.3-32-32-32H96c-17.7 0-32 14.3-32 32z"/></svg>
                    Statistiques détaillées par classe
                </h3>
                <div style="overflow-x:auto;">
                    <table class="stats-table">
                        <thead>
                            <tr>
                                <th>Classe</th>
                                <th>Effectif</th>
                                <th>Moyenne</th>
                                <th>Meilleure note</th>
                                <th>Moins bonne note</th>
                            </tr>
                        </thead>
                        <tbody id="statsClasseBody">
                            <tr><td colspan="5"><div class="empty"><svg class="icon icon-lg" viewBox="0 0 512 512"><path d="M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM232 344V280H168c-13.3 0-24-10.7-24-24s10.7-24 24-24h64V168c0-13.3 10.7-24 24-24s24 10.7 24 24v64h64c13.3 0 24 10.7 24 24s-10.7 24-24 24H280v64c0 13.3-10.7 24-24 24s-24-10.7-24-24z"/></svg><span>Chargement…</span></div></td></tr>
                        </tbody>
                    </table>
                </div>
            </div>

        </div>
    </div>

</div>

<script>
/* ============================================================
   MINI CHART ENGINE — Canvas natif, aucune dépendance externe
   Supporte : bar (V/H), line, doughnut
   API compatible avec l'usage de Chart.js dans cette page
   ============================================================ */
(function() {
    'use strict';

    // ---------- Palette ----------
    window.__MIXED__ = [
        '#1d4ed8', '#b91c1c',
        '#3b82f6', '#ef4444',
        '#93c5fd', '#fca5a5',
        '#1e3a8a', '#7f1d1d',
        '#2563eb', '#dc2626'
    ];
    window.__BLUE2__ = '#2563eb';

    // ---------- Registre de charts ----------
    window.__CHARTS__ = {};

    function getCtx(id) {
        var canvas = document.getElementById(id);
        if (!canvas) return null;
        var dpr = window.devicePixelRatio || 1;
        var rect = canvas.getBoundingClientRect();
        // Le canvas doit avoir une taille physique
        var w = Math.max(rect.width, canvas.parentElement ? canvas.parentElement.clientWidth : 300);
        var h = Math.max(rect.height, canvas.parentElement ? canvas.parentElement.clientHeight : 200);
        canvas.width = w * dpr;
        canvas.height = h * dpr;
        canvas.style.width = w + 'px';
        canvas.style.height = h + 'px';
        var ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        return { ctx: ctx, w: w, h: h };
    }

    function roundRect(ctx, x, y, w, h, r) {
        r = Math.min(r, h / 2, w / 2);
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + w, y, x + w, y + h, r);
        ctx.arcTo(x + w, y + h, x, y + h, r);
        ctx.arcTo(x, y + h, x, y, r);
        ctx.arcTo(x, y, x + w, y, r);
        ctx.closePath();
    }

    function drawAxes(ctx, opts) {
        var pad = opts.padding;
        var chartX = pad.left, chartY = pad.top;
        var chartW = opts.w - pad.left - pad.right;
        var chartH = opts.h - pad.top - pad.bottom;

        ctx.strokeStyle = 'rgba(226,232,240,0.8)';
        ctx.lineWidth = 1;

        // Grid horizontal + labels Y
        var steps = opts.ySteps || 5;
        var yMax = opts.yMax || 1;
        ctx.font = '600 10px -apple-system, sans-serif';
        ctx.fillStyle = '#64748b';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (var i = 0; i <= steps; i++) {
            var y = chartY + chartH - (chartH * i / steps);
            ctx.beginPath();
            ctx.moveTo(chartX, y);
            ctx.lineTo(chartX + chartW, y);
            ctx.stroke();
            var val = (yMax * i / steps);
            var label = (opts.decimals ? val.toFixed(opts.decimals) : Math.round(val));
            ctx.fillText(label, chartX - 6, y);
        }

        return { chartX: chartX, chartY: chartY, chartW: chartW, chartH: chartH };
    }

    // ---------- BAR CHART ----------
    function drawBar(id, data, options) {
        options = options || {};
        var c = getCtx(id);
        if (!c) return;
        var ctx = c.ctx, w = c.w, h = c.h;
        ctx.clearRect(0, 0, w, h);

        var labels = Object.keys(data || {});
        var values = Object.values(data || {});
        if (!labels.length) return;

        var horizontal = !!options.horizontal;
        var pad = { top: 30, right: 20, bottom: horizontal ? 20 : 50, left: horizontal ? 60 : 45 };

        if (horizontal) {
            // Barres horizontales : labels à gauche, valeurs à droite
            pad = { top: 20, right: 40, bottom: 20, left: Math.min(140, Math.max(60, w * 0.25)) };
        }

        var maxVal = Math.max.apply(null, values.concat([1]));
        var yMax = maxVal * 1.15;

        if (horizontal) {
            var chartX = pad.left, chartY = pad.top;
            var chartW = w - pad.left - pad.right;
            var chartH = h - pad.top - pad.bottom;
            var barH = Math.max(6, Math.min(40, chartH / labels.length - 6));
            var gap = (chartH - barH * labels.length) / (labels.length + 1);

            ctx.font = '600 11px -apple-system, sans-serif';
            ctx.fillStyle = '#334155';
            ctx.textBaseline = 'middle';
            ctx.textAlign = 'right';

            for (var i = 0; i < labels.length; i++) {
                var y = chartY + gap + i * (barH + gap);
                ctx.fillText(String(labels[i]).substring(0, 14), chartX - 8, y + barH / 2);
                var bw = (values[i] / yMax) * chartW;
                ctx.fillStyle = window.__MIXED__[i % window.__MIXED__.length];
                roundRect(ctx, chartX, y, Math.max(1, bw), barH, 4);
                ctx.fill();

                // Valeur à droite
                ctx.fillStyle = '#334155';
                ctx.textAlign = 'left';
                var val = (typeof values[i] === 'number') ? values[i].toFixed(options.decimals || 0) : values[i];
                ctx.fillText(val, chartX + bw + 6, y + barH / 2);
                ctx.textAlign = 'right';
            }
            return;
        }

        // Barres verticales
        var axes = drawAxes(ctx, { padding: pad, w: w, h: h, yMax: yMax, decimals: options.decimals || 0 });
        var chartX = axes.chartX, chartY = axes.chartY, chartW = axes.chartW, chartH = axes.chartH;

        var barW = Math.max(6, Math.min(50, chartW / labels.length - 8));
        var slotW = chartW / labels.length;
        var barY_base = chartY + chartH;

        for (var j = 0; j < labels.length; j++) {
            var cx = chartX + slotW * j + slotW / 2;
            var bh = (values[j] / yMax) * chartH;
            ctx.fillStyle = window.__MIXED__[j % window.__MIXED__.length];
            roundRect(ctx, cx - barW / 2, barY_base - bh, barW, bh, 5);
            ctx.fill();

            // Valeur en haut
            ctx.fillStyle = '#334155';
            ctx.font = '700 10px -apple-system, sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'bottom';
            var v = (typeof values[j] === 'number') ? values[j].toFixed(options.decimals || 0) : values[j];
            ctx.fillText(v, cx, barY_base - bh - 3);

            // Label X
            ctx.fillStyle = '#64748b';
            ctx.font = '600 10px -apple-system, sans-serif';
            ctx.textBaseline = 'top';
            ctx.save();
            ctx.translate(cx, barY_base + 6);
            if (String(labels[j]).length > 8) {
                ctx.rotate(-Math.PI / 8);
                ctx.textAlign = 'right';
            } else {
                ctx.textAlign = 'center';
            }
            ctx.fillText(String(labels[j]).substring(0, 14), 0, 0);
            ctx.restore();
        }
    }

    // ---------- LINE CHART ----------
    function drawLine(id, data) {
        var c = getCtx(id);
        if (!c) return;
        var ctx = c.ctx, w = c.w, h = c.h;
        ctx.clearRect(0, 0, w, h);

        var labels = Object.keys(data || {});
        var values = Object.values(data || {});
        if (!labels.length) return;

        var maxVal = Math.max.apply(null, values.concat([1]));
        var minVal = Math.min.apply(null, values.concat([0]));
        var range = Math.max(1, maxVal - minVal);
        var yMax = maxVal + range * 0.15;
        var yMin = Math.max(0, minVal - range * 0.15);

        var pad = { top: 30, right: 20, bottom: 50, left: 45 };
        var chartX = pad.left, chartY = pad.top;
        var chartW = w - pad.left - pad.right;
        var chartH = h - pad.top - pad.bottom;

        // Grille
        ctx.strokeStyle = 'rgba(226,232,240,0.8)';
        ctx.lineWidth = 1;
        ctx.font = '600 10px -apple-system, sans-serif';
        ctx.fillStyle = '#64748b';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        var steps = 5;
        for (var i = 0; i <= steps; i++) {
            var y = chartY + chartH - (chartH * i / steps);
            ctx.beginPath();
            ctx.moveTo(chartX, y);
            ctx.lineTo(chartX + chartW, y);
            ctx.stroke();
            var vv = yMin + (yMax - yMin) * i / steps;
            ctx.fillText(vv.toFixed(1), chartX - 6, y);
        }

        // Points
        var n = labels.length;
        var pts = [];
        for (var j = 0; j < n; j++) {
            var x = chartX + (n === 1 ? chartW / 2 : (chartW * j / (n - 1)));
            var yv = chartY + chartH - ((values[j] - yMin) / (yMax - yMin)) * chartH;
            pts.push({ x: x, y: yv, v: values[j] });
        }

        // Aire sous la courbe
        ctx.beginPath();
        ctx.moveTo(pts[0].x, chartY + chartH);
        for (var k = 0; k < pts.length; k++) ctx.lineTo(pts[k].x, pts[k].y);
        ctx.lineTo(pts[pts.length - 1].x, chartY + chartH);
        ctx.closePath();
        var grad = ctx.createLinearGradient(0, chartY, 0, chartY + chartH);
        grad.addColorStop(0, 'rgba(37,99,235,0.25)');
        grad.addColorStop(1, 'rgba(37,99,235,0.02)');
        ctx.fillStyle = grad;
        ctx.fill();

        // Ligne
        ctx.beginPath();
        ctx.strokeStyle = window.__BLUE2__;
        ctx.lineWidth = 3;
        ctx.lineJoin = 'round';
        for (var m = 0; m < pts.length; m++) {
            if (m === 0) ctx.moveTo(pts[m].x, pts[m].y);
            else ctx.lineTo(pts[m].x, pts[m].y);
        }
        ctx.stroke();

        // Points + valeurs
        for (var p = 0; p < pts.length; p++) {
            ctx.beginPath();
            ctx.arc(pts[p].x, pts[p].y, 5, 0, Math.PI * 2);
            ctx.fillStyle = '#fff';
            ctx.fill();
            ctx.strokeStyle = window.__BLUE2__;
            ctx.lineWidth = 2;
            ctx.stroke();

            ctx.fillStyle = window.__BLUE2__;
            ctx.font = '700 10px -apple-system, sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'bottom';
            ctx.fillText(Number(pts[p].v).toFixed(1), pts[p].x, pts[p].y - 8);
        }

        // Labels X
        ctx.fillStyle = '#64748b';
        ctx.font = '600 10px -apple-system, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        for (var q = 0; q < pts.length; q++) {
            ctx.fillText(String(labels[q]).substring(0, 10), pts[q].x, chartY + chartH + 6);
        }
    }

    // ---------- DOUGHNUT ----------
    function drawDoughnut(id, data) {
        var c = getCtx(id);
        if (!c) return;
        var ctx = c.ctx, w = c.w, h = c.h;
        ctx.clearRect(0, 0, w, h);

        var labels = Object.keys(data || {});
        var values = Object.values(data || {});
        var total = values.reduce(function(a, b) { return a + b; }, 0);
        if (!labels.length || total === 0) return;

        // Zone camembert (gauche) + légende (droite)
        var cx = w * 0.35;
        var cy = h * 0.5;
        var radius = Math.min(w * 0.3, h * 0.4);
        var inner = radius * 0.62;

        var start = -Math.PI / 2;
        for (var i = 0; i < labels.length; i++) {
            var slice = (values[i] / total) * Math.PI * 2;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, radius, start, start + slice);
            ctx.closePath();
            ctx.fillStyle = window.__MIXED__[i % window.__MIXED__.length];
            ctx.fill();
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 3;
            ctx.stroke();

            // Pourcentage au centre de la part
            var mid = start + slice / 2;
            var tx = cx + Math.cos(mid) * (radius * 0.75);
            var ty = cy + Math.sin(mid) * (radius * 0.75);
            var pct = (values[i] / total) * 100;
            if (pct >= 6) {
                ctx.fillStyle = '#fff';
                ctx.font = '700 11px -apple-system, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText(Math.round(pct) + '%', tx, ty);
            }
            start += slice;
        }

        // Trou du doughnut
        ctx.beginPath();
        ctx.arc(cx, cy, inner, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();

        // Total au centre
        ctx.fillStyle = '#1e293b';
        ctx.font = '800 16px -apple-system, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(total), cx, cy - 6);
        ctx.fillStyle = '#64748b';
        ctx.font = '600 10px -apple-system, sans-serif';
        ctx.fillText('TOTAL', cx, cy + 12);

        // Légende à droite
        var lx = w * 0.68;
        var ly = 30;
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        for (var j = 0; j < labels.length; j++) {
            var y = ly + j * 20;
            if (y > h - 15) break;
            ctx.beginPath();
            ctx.arc(lx, y, 5, 0, Math.PI * 2);
            ctx.fillStyle = window.__MIXED__[j % window.__MIXED__.length];
            ctx.fill();

            ctx.fillStyle = '#334155';
            ctx.font = '600 11px -apple-system, sans-serif';
            var label = String(labels[j]).substring(0, 18);
            ctx.fillText(label + ' (' + values[j] + ')', lx + 12, y);
        }
    }

    // ---------- GENRE (double dataset bar) ----------
    function drawGenre(id, data) {
        var c = getCtx(id);
        if (!c) return;
        var ctx = c.ctx, w = c.w, h = c.h;
        ctx.clearRect(0, 0, w, h);

        var labels = data.classes || [];
        var g = data.garcons || [];
        var f = data.filles || [];
        if (!labels.length) return;

        var pad = { top: 40, right: 20, bottom: 50, left: 45 };
        var chartX = pad.left, chartY = pad.top;
        var chartW = w - pad.left - pad.right;
        var chartH = h - pad.top - pad.bottom;

        var maxVal = Math.max.apply(null, g.concat(f).concat([1]));
        var yMax = maxVal * 1.2;

        // Grille + Y
        ctx.strokeStyle = 'rgba(226,232,240,0.8)';
        ctx.font = '600 10px -apple-system, sans-serif';
        ctx.fillStyle = '#64748b';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (var i = 0; i <= 5; i++) {
            var y = chartY + chartH - (chartH * i / 5);
            ctx.beginPath();
            ctx.moveTo(chartX, y);
            ctx.lineTo(chartX + chartW, y);
            ctx.stroke();
            ctx.fillText(String(Math.round(yMax * i / 5)), chartX - 6, y);
        }

        var slotW = chartW / labels.length;
        var barW = Math.max(4, Math.min(20, slotW / 3));

        for (var j = 0; j < labels.length; j++) {
            var cx = chartX + slotW * j + slotW / 2;

            // Barre Garçons
            var bg = (g[j] / yMax) * chartH;
            ctx.fillStyle = '#2563eb';
            roundRect(ctx, cx - barW - 2, chartY + chartH - bg, barW, bg, 4);
            ctx.fill();
            if (g[j] > 0) {
                ctx.fillStyle = '#334155';
                ctx.font = '700 9px -apple-system, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'bottom';
                ctx.fillText(g[j], cx - barW / 2 - 2, chartY + chartH - bg - 2);
            }

            // Barre Filles
            var bf = (f[j] / yMax) * chartH;
            ctx.fillStyle = '#dc2626';
            roundRect(ctx, cx + 2, chartY + chartH - bf, barW, bf, 4);
            ctx.fill();
            if (f[j] > 0) {
                ctx.fillStyle = '#334155';
                ctx.font = '700 9px -apple-system, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'bottom';
                ctx.fillText(f[j], cx + barW / 2 + 2, chartY + chartH - bf - 2);
            }

            // Label X
            ctx.fillStyle = '#64748b';
            ctx.font = '600 10px -apple-system, sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'top';
            ctx.save();
            ctx.translate(cx, chartY + chartH + 6);
            if (String(labels[j]).length > 8) {
                ctx.rotate(-Math.PI / 8);
                ctx.textAlign = 'right';
            }
            ctx.fillText(String(labels[j]).substring(0, 14), 0, 0);
            ctx.restore();
        }

        // Légende en haut
        var lx = chartX;
        var ly = 15;
        ctx.beginPath();
        ctx.arc(lx + 6, ly, 5, 0, Math.PI * 2);
        ctx.fillStyle = '#2563eb'; ctx.fill();
        ctx.fillStyle = '#334155';
        ctx.font = '700 11px -apple-system, sans-serif';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        ctx.fillText('Garçons', lx + 16, ly);

        ctx.beginPath();
        ctx.arc(lx + 100, ly, 5, 0, Math.PI * 2);
        ctx.fillStyle = '#dc2626'; ctx.fill();
        ctx.fillStyle = '#334155';
        ctx.fillText('Filles', lx + 110, ly);
    }

    // ---------- API publique ----------
    window.MiniChart = {
        bar: drawBar,
        line: drawLine,
        doughnut: drawDoughnut,
        genre: drawGenre,
        destroy: function(id) {
            var c = document.getElementById(id);
            if (c) {
                var ctx = c.getContext('2d');
                ctx.clearRect(0, 0, c.width, c.height);
            }
        }
    };
})();

/* ============================================================
   PALETTE (pour usage JS ailleurs)
   ============================================================ */
const BLUE_SHADES = ['#1e3a8a', '#1d4ed8', '#2563eb', '#3b82f6', '#60a5fa', '#93c5fd', '#bfdbfe'];
const RED_SHADES  = ['#7f1d1d', '#991b1b', '#b91c1c', '#dc2626', '#ef4444', '#f87171', '#fca5a5'];
const MIXED = window.__MIXED__;

const DEFAULT_FILTERS = {
    annee:   'Toutes années',
    periode: 'Toutes periodes',
    classe:  'Toutes classes',
    matiere: 'Toutes matières'
};

const $ = id => document.getElementById(id);

function escapeHtml(t) {
    if (t === null || t === undefined) return '';
    return String(t).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function qs() {
    return new URLSearchParams({
        annee:   $('anneeSelect').value,
        periode: $('periodeSelect').value,
        classe:  $('classeSelect').value,
        matiere: $('matiereSelect').value
    }).toString();
}

async function fetchJson(url) {
    const r = await fetch(url, { headers: { 'Accept': 'application/json' } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
}

async function loadFilters() {
    try {
        const j = await fetchJson('/admin/statistiques/api/filtres');
        const d = j.data || j;
        $('anneeSelect').innerHTML   = '<option>Toutes années</option>'   + (d.annees   || []).map(v => `<option>${escapeHtml(v)}</option>`).join('');
        $('periodeSelect').innerHTML = '<option>Toutes periodes</option>' + (d.periodes || []).map(v => `<option>${escapeHtml(v)}</option>`).join('');
        $('classeSelect').innerHTML  = '<option>Toutes classes</option>'  + (d.classes  || []).map(v => `<option>${escapeHtml(v)}</option>`).join('');
        $('matiereSelect').innerHTML = '<option>Toutes matières</option>' + (d.matieres || []).map(v => `<option>${escapeHtml(v)}</option>`).join('');
    } catch (e) { console.error('loadFilters', e); }
}

function reinitialiserFiltres() {
    $('anneeSelect').value   = DEFAULT_FILTERS.annee;
    $('periodeSelect').value = DEFAULT_FILTERS.periode;
    $('classeSelect').value  = DEFAULT_FILTERS.classe;
    $('matiereSelect').value = DEFAULT_FILTERS.matiere;
    actualiser();
}

let loading = false;

async function actualiser() {
    if (loading) return;
    loading = true;
    $('btnActualiser').disabled = true;

    const params = qs();
    const classe = $('classeSelect').value;

    try {
        await Promise.all([
            chargerDashboard(params),
            chargerGenre(classe),
            chargerStatsClasses(params)
        ]);
    } catch (e) {
        console.error('❌ actualiser :', e);
        if (String(e.message).startsWith('HTTP 401')) {
            alert('Session expirée — veuillez vous reconnecter.');
        }
    } finally {
        loading = false;
        $('btnActualiser').disabled = false;
    }
}

async function chargerDashboard(params) {
    const j = await fetchJson('/admin/statistiques/api/dashboard?' + params);
    const d = j.data || j;
    renderKPIs(d.kpis || {});
    drawBarSafe('chartEffectifs',   d.effectifs   || {}, false, 0);
    drawBarSafe('chartMoyennes',    d.moyennes    || {}, true, 2);
    drawLineSafe('chartEvolution',  d.evolution   || {});
    drawDoughnutSafe('chartRepartition', d.repartition || {});
}

async function chargerGenre(classe) {
    const j = await fetchJson('/admin/statistiques/api/genre?classe=' + encodeURIComponent(classe));
    const d = j.data || j;
    if (!d.classes || d.classes.length === 0) {
        showEmpty('chartGenre', 'Aucune donnée de genre');
    } else {
        hideEmpty('chartGenre');
        window.MiniChart.genre('chartGenre', d);
    }
}

async function chargerStatsClasses(params) {
    const j = await fetchJson('/admin/statistiques/api/classes?' + params);
    const d = j.data || j;
    const rows = d.stats || [];
    const tbody = $('statsClasseBody');
    if (rows.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5"><div class="empty"><span>Aucune donnée</span></div></td></tr>';
    } else {
        tbody.innerHTML = rows.map(row => '<tr>' + row.map(c => `<td>${escapeHtml(String(c))}</td>`).join('') + '</tr>').join('');
    }
}

/* ---------- Wrappers chart ---------- */
function drawBarSafe(id, data, horizontal, decimals) {
    const labels = Object.keys(data || {});
    if (!labels.length) { showEmpty(id, 'Aucune donnée'); return; }
    hideEmpty(id);
    window.MiniChart.bar(id, data, { horizontal: horizontal, decimals: decimals || 0 });
}

function drawLineSafe(id, data) {
    const labels = Object.keys(data || {});
    if (!labels.length) { showEmpty(id, 'Aucune donnée'); return; }
    hideEmpty(id);
    window.MiniChart.line(id, data);
}

function drawDoughnutSafe(id, data) {
    const values = Object.values(data || {});
    const total = values.reduce((a, b) => a + b, 0);
    if (!values.length || total === 0) { showEmpty(id, 'Aucune donnée'); return; }
    hideEmpty(id);
    window.MiniChart.doughnut(id, data);
}

/* ---------- KPIs ---------- */
function renderKPIs(k) {
    const items = [
        { label: 'Moyenne générale',    value: fmtNum(k.moyenneGenerale) },
        { label: 'Taux de réussite',    value: fmtPct(k.tauxReussite)   },
        { label: 'Meilleure classe',    value: k.meilleureClasse || '—', text: true },
        { label: 'Promotion dominante', value: k.promoDominante || '—', text: true }
    ];
    $('kpiGrid').innerHTML = items.map(i => `
        <div class="kpi-card">
            <div class="kpi-label">${escapeHtml(i.label)}</div>
            <div class="kpi-value ${i.text ? 'text' : ''}">${escapeHtml(String(i.value))}</div>
        </div>`).join('');
}
function fmtNum(v) { return (v == null || isNaN(v)) ? '0.00' : Number(v).toFixed(2); }
function fmtPct(v) { return (v == null || isNaN(v)) ? '0.0%' : Number(v).toFixed(1) + '%'; }

/* ---------- Empty overlay ---------- */
function showEmpty(id, message) {
    const el = $(id);
    if (!el) return;
    const parent = el.parentElement;
    if (!parent) return;
    if (getComputedStyle(parent).position === 'static') parent.style.position = 'relative';

    let overlay = parent.querySelector('.empty-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.className = 'empty empty-overlay';
        parent.appendChild(overlay);
    }
    overlay.innerHTML = `<svg class="icon" style="font-size:2rem;color:#cbd5e1;" viewBox="0 0 512 512"><path d="M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM232 344V280H168c-13.3 0-24-10.7-24-24s10.7-24 24-24h64V168c0-13.3 10.7-24 24-24s24 10.7 24 24v64h64c13.3 0 24 10.7 24 24s-10.7 24-24 24H280v64c0 13.3-10.7 24-24 24s-24-10.7-24-24z"/></svg><span>${escapeHtml(message)}</span>`;
    overlay.style.display = 'flex';
    // Vider le canvas
    const c = el;
    if (c && c.getContext) {
        const ctx = c.getContext('2d');
        ctx.clearRect(0, 0, c.width, c.height);
    }
}

function hideEmpty(id) {
    const el = $(id);
    if (!el) return;
    const parent = el.parentElement;
    if (!parent) return;
    const overlay = parent.querySelector('.empty-overlay');
    if (overlay) overlay.style.display = 'none';
}

/* ---------- Listeners ---------- */
$('btnActualiser').addEventListener('click', actualiser);
$('btnReset').addEventListener('click', reinitialiserFiltres);

['anneeSelect', 'periodeSelect', 'classeSelect', 'matiereSelect'].forEach(id => {
    const el = $(id);
    if (el) el.addEventListener('change', actualiser);
});

/* Redraw au resize (léger debounce) */
let resizeTimer = null;
window.addEventListener('resize', () => {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
        // Redessiner en relisant les données en cache serait idéal,
        // mais par simplicité on relance juste le rendu
        actualiser();
    }, 300);
});

document.addEventListener('DOMContentLoaded', async () => {
    await loadFilters();
    await actualiser();
});
</script>
</body>
</html>
""".replace("__INST__", echapperHtml(institutionId));
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