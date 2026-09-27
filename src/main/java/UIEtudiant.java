import java.util.ArrayList;
import java.util.List;

public class UIEtudiant {

    public static String rendrePage(
            List<Etudiant> etudiants,
            String motCle,
            String institutionId,
            String messageErreur,
            String successMessage,
            List<String> anneesDisponibles,
            List<String> periodesDisponibles,
            List<String> promotionsDisponibles,
            List<String> classesDisponibles,
            String anneeFiltre,
            String periodeFiltre,
            String classeFiltre,
            boolean canCreate,
            boolean canModify,
            boolean canDelete) {

        // Sécurisation des listes et des chaînes
        if (etudiants == null) etudiants = new ArrayList<>();
        if (motCle == null) motCle = "";
        if (institutionId == null) institutionId = "";
        if (messageErreur == null) messageErreur = "";
        if (successMessage == null) successMessage = "";
        if (anneesDisponibles == null) anneesDisponibles = new ArrayList<>();
        if (periodesDisponibles == null) periodesDisponibles = new ArrayList<>();
        if (classesDisponibles == null) classesDisponibles = new ArrayList<>();
        if (anneeFiltre == null) anneeFiltre = "";
        if (periodeFiltre == null) periodeFiltre = "";
        if (classeFiltre == null) classeFiltre = "";

        String etudiantsJson = serializeEtudiants(etudiants);
        String institutionIdJson = escapeJson(institutionId);

        StringBuilder sb = new StringBuilder();

        // ========== HTML & CSS ==========
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"fr\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">");
        sb.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">");
        sb.append("<title>Gestion des Étudiants</title>");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        sb.append("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
        sb.append("<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\">");
        sb.append("<style>");

        // ---------- ROOT ----------
        sb.append(":root {");
        sb.append("  --primary: #4f46e5;");
        sb.append("  --primary-light: #6366f1;");
        sb.append("  --primary-dark: #4338ca;");
        sb.append("  --bg-body: #f1f5f9;");
        sb.append("  --card-bg: #ffffff;");
        sb.append("  --text-dark: #0f172a;");
        sb.append("  --text-muted: #64748b;");
        sb.append("  --border-color: #e2e8f0;");
        sb.append("  --shadow: 0 4px 12px -4px rgba(0,0,0,0.06);");
        sb.append("  --safe-top: env(safe-area-inset-top, 0px);");
        sb.append("  --safe-bottom: env(safe-area-inset-bottom, 0px);");
        sb.append("  --safe-left: env(safe-area-inset-left, 0px);");
        sb.append("  --safe-right: env(safe-area-inset-right, 0px);");
        sb.append("}");

        // ---------- RESET + PLEIN ÉCRAN ----------
        sb.append("*, *::before, *::after { box-sizing: border-box; }");
        sb.append("html, body {");
        sb.append("  width: 100%;");
        sb.append("  min-width: 320px;");
        sb.append("  height: 100vh;");
        sb.append("  height: 100dvh;");
        sb.append("  min-height: 100vh;");
        sb.append("  min-height: 100dvh;");
        sb.append("  margin: 0;");
        sb.append("  padding: 0;");
        sb.append("  font-family: 'Inter', -apple-system, sans-serif;");
        sb.append("  background-color: var(--bg-body);");
        sb.append("  color: var(--text-dark);");
        sb.append("  overflow: hidden;");
        sb.append("  -webkit-text-size-adjust: 100%;");
        sb.append("  -webkit-tap-highlight-color: transparent;");
        sb.append("  overscroll-behavior: none;");
        sb.append("}");

        sb.append(".app-wrapper {");
        sb.append("  display: flex;");
        sb.append("  flex-direction: column;");
        sb.append("  width: 100%;");
        sb.append("  max-width: none;");
        sb.append("  height: 100vh;");
        sb.append("  height: 100dvh;");
        sb.append("  margin: 0;");
        sb.append("  padding: 8px;");
        sb.append("  padding-top: calc(8px + var(--safe-top));");
        sb.append("  padding-bottom: calc(8px + var(--safe-bottom));");
        sb.append("  padding-left: calc(8px + var(--safe-left));");
        sb.append("  padding-right: calc(8px + var(--safe-right));");
        sb.append("  gap: 8px;");
        sb.append("}");

        // ---------- HEADER ----------
        sb.append(".header-bar {");
        sb.append("  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);");
        sb.append("  color: white;");
        sb.append("  border-radius: 12px;");
        sb.append("  padding: 10px 18px;");
        sb.append("  box-shadow: 0 8px 20px -8px rgba(79,70,229,0.35);");
        sb.append("  display: flex;");
        sb.append("  justify-content: space-between;");
        sb.append("  align-items: center;");
        sb.append("  gap: 12px;");
        sb.append("  flex-shrink: 0;");
        sb.append("  min-height: 52px;");
        sb.append("}");
        sb.append(".header-bar h1 {");
        sb.append("  font-weight: 700; font-size: 1.15rem;");
        sb.append("  margin: 0; letter-spacing: -0.3px;");
        sb.append("  display: flex; align-items: center; gap: 0.5rem;");
        sb.append("}");
        sb.append(".header-bar .subtitle {");
        sb.append("  opacity: 0.85; font-size: 0.78rem; margin: 0;");
        sb.append("}");
        sb.append(".header-bar .actions { display: flex; gap: 8px; align-items: center; }");
        sb.append(".btn-glass {");
        sb.append("  background: rgba(255,255,255,0.15);");
        sb.append("  border: 1px solid rgba(255,255,255,0.25);");
        sb.append("  color: white;");
        sb.append("  border-radius: 50px;");
        sb.append("  padding: 0.35rem 0.9rem;");
        sb.append("  font-weight: 500;");
        sb.append("  text-decoration: none;");
        sb.append("  transition: 0.15s;");
        sb.append("  font-size: 0.82rem;");
        sb.append("  white-space: nowrap;");
        sb.append("  display: inline-flex;");
        sb.append("  align-items: center;");
        sb.append("}");
        sb.append(".btn-glass:hover { background: rgba(255,255,255,0.25); color: white; transform: translateY(-1px); }");

        // ---------- LIVE INDICATOR ----------
        sb.append(".live-indicator {");
        sb.append("  display: inline-flex;");
        sb.append("  align-items: center;");
        sb.append("  gap: 6px;");
        sb.append("  font-size: 11px;");
        sb.append("  color: rgba(255,255,255,0.9);");
        sb.append("  background: rgba(255,255,255,0.1);");
        sb.append("  padding: 4px 10px;");
        sb.append("  border-radius: 20px;");
        sb.append("  font-weight: 600;");
        sb.append("}");
        sb.append(".live-dot {");
        sb.append("  width: 8px;");
        sb.append("  height: 8px;");
        sb.append("  border-radius: 50%;");
        sb.append("  background: #22c55e;");
        sb.append("  box-shadow: 0 0 0 0 rgba(34,197,94,0.7);");
        sb.append("  animation: pulse 2s infinite;");
        sb.append("}");
        sb.append(".live-dot.paused { background: #f59e0b; animation: none; box-shadow: none; }");
        sb.append(".live-dot.error { background: #ef4444; animation: none; box-shadow: none; }");
        sb.append("@keyframes pulse {");
        sb.append("  0% { box-shadow: 0 0 0 0 rgba(34,197,94,0.7); }");
        sb.append("  70% { box-shadow: 0 0 0 8px rgba(34,197,94,0); }");
        sb.append("  100% { box-shadow: 0 0 0 0 rgba(34,197,94,0); }");
        sb.append("}");

        // ---------- FILTERS ----------
        sb.append(".filters-bar {");
        sb.append("  background: var(--card-bg);");
        sb.append("  border-radius: 10px;");
        sb.append("  padding: 8px 12px;");
        sb.append("  box-shadow: var(--shadow);");
        sb.append("  border: 1px solid var(--border-color);");
        sb.append("  flex-shrink: 0;");
        sb.append("}");
        sb.append(".filters-bar .form-control,");
        sb.append(".filters-bar .form-select {");
        sb.append("  border-radius: 8px;");
        sb.append("  border: 1px solid var(--border-color);");
        sb.append("  padding: 0.35rem 0.65rem;");
        sb.append("  font-size: 0.82rem;");
        sb.append("  background: #f8fafc;");
        sb.append("  height: 34px;");
        sb.append("  transition: 0.15s;");
        sb.append("}");
        sb.append(".filters-bar .form-control:focus,");
        sb.append(".filters-bar .form-select:focus {");
        sb.append("  border-color: var(--primary);");
        sb.append("  box-shadow: 0 0 0 3px rgba(79,70,229,0.12);");
        sb.append("  background: white;");
        sb.append("}");
        sb.append(".filters-bar .input-group-text {");
        sb.append("  background: #f8fafc;");
        sb.append("  border-color: var(--border-color);");
        sb.append("  height: 34px;");
        sb.append("  padding: 0 0.55rem;");
        sb.append("  font-size: 0.82rem;");
        sb.append("}");
        sb.append(".btn-primary-sm {");
        sb.append("  background: var(--primary);");
        sb.append("  border: none;");
        sb.append("  border-radius: 8px;");
        sb.append("  padding: 0.35rem 0.9rem;");
        sb.append("  font-weight: 600;");
        sb.append("  font-size: 0.82rem;");
        sb.append("  color: white;");
        sb.append("  height: 34px;");
        sb.append("  transition: 0.15s;");
        sb.append("  white-space: nowrap;");
        sb.append("}");
        sb.append(".btn-primary-sm:hover { background: var(--primary-dark); }");
        sb.append(".btn-outline-sm {");
        sb.append("  border: 1px solid var(--border-color);");
        sb.append("  border-radius: 8px;");
        sb.append("  background: white;");
        sb.append("  color: var(--text-muted);");
        sb.append("  height: 34px;");
        sb.append("  padding: 0 0.65rem;");
        sb.append("  display: inline-flex;");
        sb.append("  align-items: center;");
        sb.append("  justify-content: center;");
        sb.append("  transition: 0.15s;");
        sb.append("}");
        sb.append(".btn-outline-sm:hover { background: #f1f5f9; color: var(--primary); }");

        // ---------- TABLE ----------
        sb.append(".table-container {");
        sb.append("  flex: 1;");
        sb.append("  min-height: 0;");
        sb.append("  background: var(--card-bg);");
        sb.append("  border-radius: 10px;");
        sb.append("  box-shadow: var(--shadow);");
        sb.append("  border: 1px solid var(--border-color);");
        sb.append("  display: flex;");
        sb.append("  flex-direction: column;");
        sb.append("  overflow: hidden;");
        sb.append("}");
        sb.append(".table-scroll { flex: 1; overflow-y: auto; overflow-x: auto; -webkit-overflow-scrolling: touch; }");
        sb.append(".table-scroll table {");
        sb.append("  width: 100%;");
        sb.append("  border-collapse: collapse;");
        sb.append("  min-width: 900px;");
        sb.append("}");
        sb.append(".table-scroll thead {");
        sb.append("  position: sticky;");
        sb.append("  top: 0;");
        sb.append("  z-index: 10;");
        sb.append("  background: #f8fafc;");
        sb.append("}");
        sb.append(".table-scroll th {");
        sb.append("  padding: 0.6rem 0.8rem;");
        sb.append("  font-weight: 600;");
        sb.append("  font-size: 0.66rem;");
        sb.append("  text-transform: uppercase;");
        sb.append("  letter-spacing: 0.4px;");
        sb.append("  color: var(--text-muted);");
        sb.append("  text-align: left;");
        sb.append("  white-space: nowrap;");
        sb.append("  border-bottom: 2px solid var(--border-color);");
        sb.append("  background: #f8fafc;");
        sb.append("}");
        sb.append(".table-scroll td {");
        sb.append("  padding: 0.5rem 0.8rem;");
        sb.append("  border-bottom: 1px solid #f1f5f9;");
        sb.append("  vertical-align: middle;");
        sb.append("  font-size: 0.82rem;");
        sb.append("}");
        sb.append(".table-scroll tbody tr { transition: 0.1s; }");
        sb.append(".table-scroll tbody tr:hover { background: #f8fafc; }");
        sb.append(".table-scroll tbody tr.selected { background: #eef2ff; }");
        sb.append(".table-scroll tbody tr.row-new {");
        sb.append("  animation: rowHighlight 2s ease;");
        sb.append("}");
        sb.append("@keyframes rowHighlight {");
        sb.append("  0% { background: #fef3c7; }");
        sb.append("  100% { background: transparent; }");
        sb.append("}");
        sb.append(".badge-soft {");
        sb.append("  display: inline-block;");
        sb.append("  padding: 0.18rem 0.55rem;");
        sb.append("  border-radius: 50px;");
        sb.append("  font-weight: 500;");
        sb.append("  font-size: 0.68rem;");
        sb.append("  background: #e0e7ff;");
        sb.append("  color: var(--primary-dark);");
        sb.append("  white-space: nowrap;");
        sb.append("}");
        sb.append(".badge-soft-info { background: #e0f2fe; color: #0369a1; }");
        sb.append(".col-select { width: 40px; text-align: center; }");
        sb.append(".empty-state { padding: 3rem; text-align: center; color: var(--text-muted); }");
        sb.append(".empty-state i { font-size: 2.5rem; margin-bottom: 0.75rem; opacity: 0.3; display: block; }");

        // ---------- ACTION BAR ----------
        sb.append(".action-bar {");
        sb.append("  padding: 0.6rem 1rem;");
        sb.append("  background: #f8fafc;");
        sb.append("  border-top: 1px solid var(--border-color);");
        sb.append("  display: flex;");
        sb.append("  justify-content: flex-end;");
        sb.append("  gap: 8px;");
        sb.append("  flex-shrink: 0;");
        sb.append("  border-radius: 0 0 10px 10px;");
        sb.append("}");
        sb.append(".btn-action {");
        sb.append("  border-radius: 8px;");
        sb.append("  padding: 0.45rem 1.1rem;");
        sb.append("  font-weight: 600;");
        sb.append("  font-size: 0.82rem;");
        sb.append("  border: none;");
        sb.append("  transition: 0.15s;");
        sb.append("  text-decoration: none;");
        sb.append("  display: inline-flex;");
        sb.append("  align-items: center;");
        sb.append("  gap: 0.4rem;");
        sb.append("  height: 34px;");
        sb.append("  cursor: pointer;");
        sb.append("}");
        sb.append(".btn-action:disabled { opacity: 0.4; cursor: not-allowed; }");
        sb.append(".btn-success-action { background: #22c55e; color: white; }");
        sb.append(".btn-success-action:hover:not(:disabled) { background: #16a34a; }");
        sb.append(".btn-warning-action { background: #f59e0b; color: white; }");
        sb.append(".btn-warning-action:hover:not(:disabled) { background: #d97706; }");
        sb.append(".btn-danger-action { background: #ef4444; color: white; }");
        sb.append(".btn-danger-action:hover:not(:disabled) { background: #dc2626; }");

        // ---------- TOAST ----------
        sb.append(".toast-container {");
        sb.append("  position: fixed;");
        sb.append("  bottom: calc(20px + var(--safe-bottom));");
        sb.append("  right: calc(20px + var(--safe-right));");
        sb.append("  z-index: 1100;");
        sb.append("  display: flex;");
        sb.append("  flex-direction: column;");
        sb.append("  gap: 8px;");
        sb.append("}");
        sb.append(".toast-item {");
        sb.append("  background: #0f172a;");
        sb.append("  color: white;");
        sb.append("  padding: 12px 18px;");
        sb.append("  border-radius: 12px;");
        sb.append("  box-shadow: 0 10px 25px -5px rgba(0,0,0,0.2);");
        sb.append("  font-size: 13px;");
        sb.append("  font-weight: 500;");
        sb.append("  animation: fadeInOut 3s ease;");
        sb.append("  display: flex;");
        sb.append("  align-items: center;");
        sb.append("  gap: 8px;");
        sb.append("}");
        sb.append(".toast-item.success { background: #16a34a; }");
        sb.append(".toast-item.error { background: #dc2626; }");
        sb.append("@keyframes fadeInOut {");
        sb.append("  0% { opacity: 0; transform: translateY(20px); }");
        sb.append("  15% { opacity: 1; transform: translateY(0); }");
        sb.append("  85% { opacity: 1; }");
        sb.append("  100% { opacity: 0; transform: translateY(20px); }");
        sb.append("}");

        // ---------- RESPONSIVE — 4K ----------
        sb.append("@media (min-width: 1920px) {");
        sb.append("  .app-wrapper { padding: 12px; gap: 12px; }");
        sb.append("  .header-bar { padding: 14px 24px; }");
        sb.append("  .header-bar h1 { font-size: 1.35rem; }");
        sb.append("  .filters-bar { padding: 10px 16px; }");
        sb.append("  .table-scroll th { padding: 0.7rem 0.9rem; font-size: 0.72rem; }");
        sb.append("  .table-scroll td { padding: 0.6rem 0.9rem; font-size: 0.88rem; }");
        sb.append("  .table-scroll table { min-width: 1100px; }");
        sb.append("}");

        // ---------- RESPONSIVE — MOBILE ----------
        sb.append("@media (max-width: 768px) {");
        sb.append("  html, body { overflow: auto; height: auto; min-height: 100dvh; }");
        sb.append("  .app-wrapper {");
        sb.append("    height: auto;");
        sb.append("    min-height: 100dvh;");
        sb.append("    overflow: visible;");
        sb.append("    padding: 6px;");
        sb.append("    gap: 6px;");
        sb.append("  }");
        sb.append("  .header-bar { padding: 8px 12px; border-radius: 10px; min-height: 48px; }");
        sb.append("  .header-bar h1 { font-size: 0.95rem; }");
        sb.append("  .header-bar .subtitle { display: none; }");
        sb.append("  .btn-glass { padding: 0.25rem 0.7rem; font-size: 0.72rem; }");
        sb.append("  .live-indicator { padding: 3px 8px; font-size: 10px; }");
        sb.append("  .filters-bar { padding: 8px; border-radius: 8px; }");
        sb.append("  .filters-bar form.row { display: flex; flex-direction: column; gap: 6px !important; }");
        sb.append("  .filters-bar form .col,");
        sb.append("  .filters-bar form .col-auto { width: 100% !important; }");
        sb.append("  .filters-bar form .form-control,");
        sb.append("  .filters-bar form .form-select { width: 100%; }");
        sb.append("  .filters-bar form .btn-primary-sm { width: 100%; justify-content: center; }");
        sb.append("  .table-container { border-radius: 8px; height: auto; max-height: none; }");
        sb.append("  .table-scroll { max-height: 65vh; }");
        sb.append("  .table-scroll th { padding: 0.4rem 0.5rem; font-size: 0.62rem; }");
        sb.append("  .table-scroll td { padding: 0.4rem 0.5rem; font-size: 0.75rem; }");
        sb.append("  .table-scroll table { min-width: 800px; }");
        sb.append("  .action-bar {");
        sb.append("    padding: 0.5rem 0.7rem;");
        sb.append("    flex-wrap: wrap;");
        sb.append("    border-radius: 0 0 8px 8px;");
        sb.append("  }");
        sb.append("  .btn-action {");
        sb.append("    padding: 0.4rem 0.8rem;");
        sb.append("    font-size: 0.75rem;");
        sb.append("    height: 32px;");
        sb.append("    flex: 1;");
        sb.append("    justify-content: center;");
        sb.append("    min-width: 0;");
        sb.append("  }");
        sb.append("  .toast-container {");
        sb.append("    bottom: calc(12px + var(--safe-bottom));");
        sb.append("    right: calc(12px + var(--safe-right));");
        sb.append("    left: calc(12px + var(--safe-left));");
        sb.append("  }");
        sb.append("  .toast-item { font-size: 12.5px; padding: 10px 14px; }");
        sb.append("}");

        // ---------- RESPONSIVE — TRÈS PETIT MOBILE ----------
        sb.append("@media (max-width: 400px) {");
        sb.append("  .header-bar h1 { font-size: 0.85rem; }");
        sb.append("  .header-bar h1 i { font-size: 0.8rem; }");
        sb.append("  .live-indicator { display: none; }");
        sb.append("  .table-scroll th { padding: 0.3rem 0.4rem; font-size: 0.58rem; }");
        sb.append("  .table-scroll td { padding: 0.3rem 0.4rem; font-size: 0.7rem; }");
        sb.append("  .table-scroll table { min-width: 700px; }");
        sb.append("  .badge-soft { font-size: 0.6rem; padding: 0.12rem 0.4rem; }");
        sb.append("}");

        // ---------- RESPONSIVE — PAYSAGE MOBILE ----------
        sb.append("@media (max-height: 500px) and (orientation: landscape) {");
        sb.append("  .app-wrapper { padding: 4px; gap: 4px; }");
        sb.append("  .header-bar { padding: 6px 12px; min-height: 40px; }");
        sb.append("  .header-bar h1 { font-size: 0.9rem; }");
        sb.append("  .header-bar .subtitle { display: none; }");
        sb.append("  .filters-bar { padding: 6px 8px; }");
        sb.append("  .table-scroll { max-height: 55vh; }");
        sb.append("  .action-bar { padding: 0.4rem 0.6rem; }");
        sb.append("}");

        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<div class=\"app-wrapper\">");

        // ===== HEADER =====
        sb.append("<div class=\"header-bar\">");
        sb.append("  <div>");
        sb.append("    <h1><i class=\"fa-solid fa-graduation-cap\"></i> Gestion des Étudiants</h1>");
        sb.append("    <p class=\"subtitle\">Consultez, ajoutez, modifiez et gérez les étudiants</p>");
        sb.append("  </div>");
        sb.append("  <div class=\"actions\">");
        sb.append("    <span class=\"live-indicator\" id=\"liveIndicator\" title=\"Actualisation automatique\">");
        sb.append("      <span class=\"live-dot\" id=\"liveDot\"></span>");
        sb.append("      <span id=\"liveLabel\">Live</span>");
        sb.append("    </span>");
        sb.append("    <a href=\"/dashboard\" class=\"btn-glass\"><i class=\"fa-solid fa-arrow-left me-1\"></i> Tableau de bord</a>");
        sb.append("  </div>");
        sb.append("</div>");

        // ===== MESSAGES =====
        if (!successMessage.isEmpty()) {
            sb.append("<div class=\"alert alert-success alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">");
            sb.append("  <i class=\"fa-solid fa-circle-check me-2\"></i>");
            sb.append(echapperHtml(successMessage));
            sb.append("  <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>");
            sb.append("</div>");
        }
        if (!messageErreur.isEmpty()) {
            sb.append("<div class=\"alert alert-danger alert-dismissible fade show border-0 shadow-sm rounded-3 mb-0 py-2\" role=\"alert\">");
            sb.append("  <i class=\"fa-solid fa-circle-exclamation me-2\"></i>");
            sb.append(echapperHtml(messageErreur));
            sb.append("  <button type=\"button\" class=\"btn-close py-2\" data-bs-dismiss=\"alert\"></button>");
            sb.append("</div>");
        }

        // ===== FILTRES =====
        sb.append("<div class=\"filters-bar\">");
        sb.append("<form method=\"GET\" action=\"/admin/etudiants\" class=\"row g-2 align-items-center\" id=\"filterForm\">");
        sb.append("  <div class=\"col-auto\">");
        sb.append("    <select name=\"classe\" class=\"form-select form-select-sm\" onchange=\"this.form.submit()\">");
        sb.append("      <option value=\"\">Toutes les classes</option>");
        for (String cl : classesDisponibles) {
            String selected = cl.equals(classeFiltre) ? "selected" : "";
            sb.append("      <option value=\"").append(echapperHtml(cl)).append("\" ").append(selected).append(">").append(echapperHtml(cl)).append("</option>");
        }
        sb.append("    </select>");
        sb.append("  </div>");
        sb.append("  <div class=\"col-auto\">");
        sb.append("    <select name=\"anneeAcademique\" class=\"form-select form-select-sm\" onchange=\"this.form.submit()\">");
        sb.append("      <option value=\"\">Toutes les années</option>");
        for (String a : anneesDisponibles) {
            String selected = a.equals(anneeFiltre) ? "selected" : "";
            sb.append("      <option value=\"").append(echapperHtml(a)).append("\" ").append(selected).append(">").append(echapperHtml(a)).append("</option>");
        }
        sb.append("    </select>");
        sb.append("  </div>");
        sb.append("  <div class=\"col-auto\">");
        sb.append("    <select name=\"periode\" class=\"form-select form-select-sm\" onchange=\"this.form.submit()\">");
        sb.append("      <option value=\"\">Toutes les périodes</option>");
        for (String p : periodesDisponibles) {
            String selected = p.equals(periodeFiltre) ? "selected" : "";
            sb.append("      <option value=\"").append(echapperHtml(p)).append("\" ").append(selected).append(">").append(echapperHtml(p)).append("</option>");
        }
        sb.append("    </select>");
        sb.append("  </div>");
        sb.append("  <div class=\"col\">");
        sb.append("    <div class=\"input-group input-group-sm\">");
        sb.append("      <span class=\"input-group-text\"><i class=\"fa-solid fa-magnifying-glass text-muted\"></i></span>");
        sb.append("      <input type=\"text\" name=\"q\" class=\"form-control\" placeholder=\"Rechercher un étudiant...\" value=\"").append(echapperHtml(motCle)).append("\">");
        sb.append("    </div>");
        sb.append("  </div>");
        sb.append("  <div class=\"col-auto d-flex gap-2\">");
        sb.append("    <button type=\"submit\" class=\"btn-primary-sm\"><i class=\"fa-solid fa-filter me-1\"></i> Filtrer</button>");
        sb.append("    <a href=\"/admin/etudiants\" class=\"btn-outline-sm\" title=\"Réinitialiser\"><i class=\"fa-solid fa-rotate-right\"></i></a>");
        sb.append("  </div>");
        sb.append("</form>");
        sb.append("</div>");

        // ===== TABLEAU =====
        sb.append("<div class=\"table-container\">");
        sb.append("<div class=\"table-scroll\">");
        sb.append("<form id=\"etudiantForm\" method=\"POST\" action=\"/admin/etudiants\">");
        sb.append("  <input type=\"hidden\" name=\"action\" id=\"actionHidden\" value=\"\">");
        sb.append("  <table class=\"table table-hover mb-0\">");
        sb.append("    <thead>");
        sb.append("      <tr>");
        sb.append("        <th class=\"col-select\">Sél.</th>");
        sb.append("        <th>Identifiant</th>");
        sb.append("        <th>Étudiant</th>");
        sb.append("        <th>Classe</th>");
        sb.append("        <th>Période</th>");
        sb.append("        <th>Année</th>");
        sb.append("        <th>Email</th>");
        sb.append("        <th>Téléphone</th>");
        sb.append("      </tr>");
        sb.append("    </thead>");
        sb.append("    <tbody id=\"etudiantBody\">");

        if (etudiants.isEmpty()) {
            sb.append("<tr><td colspan=\"8\">");
            sb.append("<div class=\"empty-state\">");
            sb.append("<i class=\"fa-solid fa-user-slash\"></i>");
            sb.append("<p class=\"mb-0\">Aucun étudiant trouvé.</p>");
            sb.append("</div>");
            sb.append("</td></tr>");
        } else {
            for (Etudiant e : etudiants) {
                String id = e.getNumeroIdentifiantEtudiant();
                sb.append("<tr data-id=\"").append(echapperHtml(id)).append("\">");
                sb.append("  <td class=\"col-select\"><input type=\"radio\" name=\"selectedId\" class=\"form-check-input\" value=\"").append(echapperHtml(id)).append("\"></td>");
                sb.append("  <td><span class=\"badge-soft\">").append(echapperHtml(id)).append("</span></td>");
                sb.append("  <td><strong>").append(echapperHtml(e.getNom())).append(" ").append(echapperHtml(e.getPrenom())).append("</strong></td>");
                sb.append("  <td><span class=\"badge-soft-info\">").append(echapperHtml(e.getClasse() != null ? e.getClasse() : "-")).append("</span></td>");
                sb.append("  <td>").append(echapperHtml(e.getPeriode() != null ? e.getPeriode() : "-")).append("</td>");
                sb.append("  <td>").append(echapperHtml(e.getAnneeAcademique() != null ? e.getAnneeAcademique() : "-")).append("</td>");
                sb.append("  <td>").append(echapperHtml(e.getEmail() != null ? e.getEmail() : "-")).append("</td>");
                sb.append("  <td>").append(echapperHtml(e.getTelephone() != null ? e.getTelephone() : "-")).append("</td>");
                sb.append("</tr>");
            }
        }

        sb.append("    </tbody>");
        sb.append("  </table>");
        sb.append("</form>");
        sb.append("</div>");

        // ===== ACTION BAR =====
        sb.append("<div class=\"action-bar\">");
        sb.append("  <a href=\"/admin/etudiants?action=nouveau&institutionId=").append(echapperHtml(institutionId)).append("\" class=\"btn-action btn-success-action\"");
        if (!canCreate) sb.append(" disabled");
        sb.append("><i class=\"fa-solid fa-plus\"></i> Ajouter</a>");
        sb.append("  <button type=\"button\" class=\"btn-action btn-warning-action\" onclick=\"modifierEtudiant()\"");
        if (!canModify) sb.append(" disabled");
        sb.append("><i class=\"fa-solid fa-pen\"></i> Modifier</button>");
        sb.append("  <button type=\"button\" class=\"btn-action btn-danger-action\" onclick=\"supprimerEtudiant()\"");
        if (!canDelete) sb.append(" disabled");
        sb.append("><i class=\"fa-solid fa-trash\"></i> Supprimer</button>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("</div>");

        // ===== TOAST CONTAINER =====
        sb.append("<div class=\"toast-container\" id=\"toastContainer\"></div>");

        // ===== SCRIPTS =====
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>");
        sb.append("<script>");
        sb.append("const etudiantsData = ").append(etudiantsJson).append(";");
        sb.append("const institutionId = \"").append(institutionIdJson).append("\";");
        sb.append("const canModify = ").append(canModify).append(";");
        sb.append("const canDelete = ").append(canDelete).append(";");
        sb.append("let idsConnus = new Set(").append(etudiantsJson.isEmpty() ? "[]" : "");

        // Inject existing IDs
        sb.append("["); 
        for (int i = 0; i < etudiants.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(etudiants.get(i).getNumeroIdentifiantEtudiant())).append("\"");
        }
        sb.append("]);");

        // ============ TOAST ============
        sb.append("function showToast(msg, type) {");
        sb.append("  var container = document.getElementById('toastContainer');");
        sb.append("  var t = document.createElement('div');");
        sb.append("  t.className = 'toast-item ' + (type || '');");
        sb.append("  var icon = type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle';");
        sb.append("  t.innerHTML = '<i class=\"fa-solid ' + icon + '\"></i> ' + msg;");
        sb.append("  container.appendChild(t);");
        sb.append("  setTimeout(function() { t.remove(); }, 3000);");
        sb.append("}");

        // ============ CRUD ============
        sb.append("function getSelectedId() {");
        sb.append("  var radios = document.getElementsByName('selectedId');");
        sb.append("  for (var i = 0; i < radios.length; i++) {");
        sb.append("    if (radios[i].checked) return radios[i].value;");
        sb.append("  }");
        sb.append("  return null;");
        sb.append("}");

        sb.append("function modifierEtudiant() {");
        sb.append("  if (!canModify) { showToast('Vous n\\'avez pas le droit de modifier.', 'error'); return; }");
        sb.append("  var id = getSelectedId();");
        sb.append("  if (!id) { showToast('Veuillez sélectionner un étudiant.', 'error'); return; }");
        sb.append("  window.location.href = '/admin/etudiants?action=edit&id=' + encodeURIComponent(id) + '&institutionId=' + encodeURIComponent(institutionId);");
        sb.append("}");

        sb.append("function supprimerEtudiant() {");
        sb.append("  if (!canDelete) { showToast('Vous n\\'avez pas le droit de supprimer.', 'error'); return; }");
        sb.append("  var id = getSelectedId();");
        sb.append("  if (!id) { showToast('Veuillez sélectionner un étudiant.', 'error'); return; }");
        sb.append("  if (confirm('Supprimer définitivement cet étudiant ?')) {");
        sb.append("    var form = document.getElementById('etudiantForm');");
        sb.append("    document.getElementById('actionHidden').value = 'delete';");
        sb.append("    var hidden = document.createElement('input');");
        sb.append("    hidden.type = 'hidden'; hidden.name = 'id'; hidden.value = id;");
        sb.append("    form.appendChild(hidden);");
        sb.append("    form.submit();");
        sb.append("  }");
        sb.append("}");

        // ============ AUTO-REFRESH ============
        sb.append("let refreshInterval = 5000;");
        sb.append("let refreshTimer = null;");
        sb.append("let isRefreshing = false;");
        sb.append("let isPaused = false;");
        sb.append("let consecutiveErrors = 0;");

        sb.append("function escapeHtml(s) {");
        sb.append("  if (s === null || s === undefined) return '';");
        sb.append("  return String(s).replace(/[&<>\"']/g, function(c) {");
        sb.append("    return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c];");
        sb.append("  });");
        sb.append("}");

        sb.append("function buildRow(e) {");
        sb.append("  var id = escapeHtml(e.numeroIdentifiantEtudiant || '');");
        sb.append("  var nom = escapeHtml(e.nom || '');");
        sb.append("  var prenom = escapeHtml(e.prenom || '');");
        sb.append("  var classe = escapeHtml(e.classe || '-');");
        sb.append("  var periode = escapeHtml(e.periode || '-');");
        sb.append("  var annee = escapeHtml(e.anneeAcademique || '-');");
        sb.append("  var email = escapeHtml(e.email || '-');");
        sb.append("  var tel = escapeHtml(e.telephone || '-');");
        sb.append("  return '<tr data-id=\"' + id + '\">' +");
        sb.append("    '<td class=\"col-select\"><input type=\"radio\" name=\"selectedId\" class=\"form-check-input\" value=\"' + id + '\"></td>' +");
        sb.append("    '<td><span class=\"badge-soft\">' + id + '</span></td>' +");
        sb.append("    '<td><strong>' + nom + ' ' + prenom + '</strong></td>' +");
        sb.append("    '<td><span class=\"badge-soft-info\">' + classe + '</span></td>' +");
        sb.append("    '<td>' + periode + '</td>' +");
        sb.append("    '<td>' + annee + '</td>' +");
        sb.append("    '<td>' + email + '</td>' +");
        sb.append("    '<td>' + tel + '</td>' +");
        sb.append("  '</tr>';");
        sb.append("}");

        sb.append("function refreshTable() {");
        sb.append("  if (isRefreshing || isPaused) return;");
        sb.append("  isRefreshing = true;");
        sb.append("  fetch('/admin/etudiants/api/liste', { headers: { 'Accept': 'application/json' } })");
        sb.append("    .then(function(r) { return r.json(); })");
        sb.append("    .then(function(data) {");
        sb.append("      if (!data.success || !data.data) throw new Error('Réponse invalide');");
        sb.append("      var tbody = document.getElementById('etudiantBody');");
        sb.append("      var currentIds = new Set();");
        sb.append("      var newIds = [];");
        sb.append("      var html = '';");
        sb.append("      var selected = getSelectedId();");
        sb.append("      data.data.forEach(function(e) {");
        sb.append("        currentIds.add(e.numeroIdentifiantEtudiant);");
        sb.append("        var isNew = !idsConnus.has(e.numeroIdentifiantEtudiant);");
        sb.append("        if (isNew) newIds.push(e.numeroIdentifiantEtudiant);");
        sb.append("        var row = buildRow(e);");
        sb.append("        if (isNew) row = row.replace('<tr ', '<tr class=\"row-new\" ');");
        sb.append("        html += row;");
        sb.append("      });");
        sb.append("      if (data.data.length === 0) {");
        sb.append("        html = '<tr><td colspan=\"8\"><div class=\"empty-state\"><i class=\"fa-solid fa-user-slash\"></i><p class=\"mb-0\">Aucun étudiant trouvé.</p></div></td></tr>';");
        sb.append("      }");
        sb.append("      tbody.innerHTML = html;");
        sb.append("      if (selected) {");
        sb.append("        var radio = document.querySelector('input[name=\"selectedId\"][value=\"' + selected.replace(/\"/g, '\\\\\"') + '\"]');");
        sb.append("        if (radio) radio.checked = true;");
        sb.append("      }");
        sb.append("      if (newIds.length > 0) {");
        sb.append("        showToast(newIds.length + ' nouvel étudiant' + (newIds.length > 1 ? 's' : '') + ' détecté' + (newIds.length > 1 ? 's' : ''), 'success');");
        sb.append("        idsConnus = currentIds;");
        sb.append("      } else if (currentIds.size !== idsConnus.size) {");
        sb.append("        idsConnus = currentIds;");
        sb.append("      }");
        sb.append("      consecutiveErrors = 0;");
        sb.append("      setLiveStatus('live');");
        sb.append("    })");
        sb.append("    .catch(function(err) {");
        sb.append("      consecutiveErrors++;");
        sb.append("      if (consecutiveErrors >= 3) setLiveStatus('error');");
        sb.append("      else setLiveStatus('paused');");
        sb.append("    })");
        sb.append("    .finally(function() { isRefreshing = false; });");
        sb.append("}");

        sb.append("function setLiveStatus(state) {");
        sb.append("  var dot = document.getElementById('liveDot');");
        sb.append("  var label = document.getElementById('liveLabel');");
        sb.append("  if (!dot || !label) return;");
        sb.append("  dot.classList.remove('paused', 'error');");
        sb.append("  if (state === 'live') {");
        sb.append("    label.textContent = 'Live';");
        sb.append("  } else if (state === 'paused') {");
        sb.append("    dot.classList.add('paused');");
        sb.append("    label.textContent = 'Ralenti';");
        sb.append("  } else if (state === 'error') {");
        sb.append("    dot.classList.add('error');");
        sb.append("    label.textContent = 'Hors ligne';");
        sb.append("  } else if (state === 'paused-user') {");
        sb.append("    dot.classList.add('paused');");
        sb.append("    label.textContent = 'En pause';");
        sb.append("  }");
        sb.append("}");

        sb.append("function startAutoRefresh() {");
        sb.append("  if (refreshTimer) clearInterval(refreshTimer);");
        sb.append("  refreshTimer = setInterval(refreshTable, refreshInterval);");
        sb.append("}");

        sb.append("document.addEventListener('visibilitychange', function() {");
        sb.append("  if (document.hidden) {");
        sb.append("    isPaused = true;");
        sb.append("    setLiveStatus('paused-user');");
        sb.append("  } else {");
        sb.append("    isPaused = false;");
        sb.append("    setLiveStatus('live');");
        sb.append("    refreshTable();");
        sb.append("  }");
        sb.append("});");

        sb.append("if (document.readyState === 'loading') {");
        sb.append("  document.addEventListener('DOMContentLoaded', startAutoRefresh);");
        sb.append("} else {");
        sb.append("  startAutoRefresh();");
        sb.append("}");

        sb.append("</script>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    // ==================== SÉRIALISATION MANUELLE ====================
    private static String serializeEtudiants(List<Etudiant> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Etudiant e = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"numeroIdentifiantEtudiant\":\"").append(escapeJson(e.getNumeroIdentifiantEtudiant())).append("\",");
            sb.append("\"nom\":\"").append(escapeJson(e.getNom())).append("\",");
            sb.append("\"prenom\":\"").append(escapeJson(e.getPrenom())).append("\",");
            sb.append("\"sexe\":\"").append(escapeJson(e.getSexe())).append("\",");
            sb.append("\"dateNaissance\":\"").append(e.getDateNaissance() != null ? e.getDateNaissance().toString() : "").append("\",");
            sb.append("\"groupeSanguin\":\"").append(escapeJson(e.getGroupeSanguin())).append("\",");
            sb.append("\"telephone\":\"").append(escapeJson(e.getTelephone())).append("\",");
            sb.append("\"email\":\"").append(escapeJson(e.getEmail())).append("\",");
            sb.append("\"adresse\":\"").append(escapeJson(e.getAdresse())).append("\",");
            sb.append("\"classe\":\"").append(escapeJson(e.getClasse())).append("\",");
            sb.append("\"anneeAcademique\":\"").append(escapeJson(e.getAnneeAcademique())).append("\",");
            sb.append("\"periode\":\"").append(escapeJson(e.getPeriode())).append("\",");
            sb.append("\"promotion\":\"").append(escapeJson(e.getPromotion())).append("\",");
            sb.append("\"matricule\":\"").append(escapeJson(e.getMatricule())).append("\",");
            sb.append("\"ninu\":\"").append(escapeJson(e.getNinu())).append("\",");
            sb.append("\"statut\":\"").append(escapeJson(e.getStatut())).append("\",");
            sb.append("\"dateInscription\":\"").append(e.getDateInscription() != null ? e.getDateInscription().toString() : "").append("\",");
            sb.append("\"departementNaissance\":\"").append(escapeJson(e.getDepartementNaissance())).append("\",");
            sb.append("\"communeNaissance\":\"").append(escapeJson(e.getCommuneNaissance())).append("\",");
            sb.append("\"nomPere\":\"").append(escapeJson(e.getNomPere())).append("\",");
            sb.append("\"prenomPere\":\"").append(escapeJson(e.getPrenomPere())).append("\",");
            sb.append("\"nomMere\":\"").append(escapeJson(e.getNomMere())).append("\",");
            sb.append("\"prenomMere\":\"").append(escapeJson(e.getPrenomMere())).append("\",");
            sb.append("\"telephoneParents\":\"").append(escapeJson(e.getTelephoneParents())).append("\",");
            sb.append("\"residenceParents\":\"").append(escapeJson(e.getResidenceParents())).append("\",");
            sb.append("\"lienParente\":\"").append(escapeJson(e.getLienParente())).append("\",");
            sb.append("\"typeResponsable\":\"").append(escapeJson(e.getTypeResponsable())).append("\",");
            sb.append("\"nomResponsable\":\"").append(escapeJson(e.getNomResponsable())).append("\",");
            sb.append("\"prenomResponsable\":\"").append(escapeJson(e.getPrenomResponsable())).append("\",");
            sb.append("\"photoPath\":\"").append(escapeJson(e.getPhotoPath())).append("\",");
            sb.append("\"observations\":\"").append(escapeJson(e.getObservations())).append("\",");
            sb.append("\"biometrieActive\":").append(e.isBiometrieActive()).append(",");
            sb.append("\"option\":\"").append(escapeJson(e.getOption())).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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