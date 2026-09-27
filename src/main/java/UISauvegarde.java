import java.time.Year;

public final class UISauvegarde {

    private UISauvegarde() {
        // utilitaire — pas d'instanciation
    }
    public static String rendrePage(String messageErreur, String messageSucces) {
        StringBuilder sb = new StringBuilder(16384);

        // ============================================================
        // HEAD
        // ============================================================
        sb.append("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Sauvegardes — Paramètres</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
            """);

        // ============================================================
        // CSS
        // ============================================================
        sb.append("""
                <style>
                    :root {
                        --bg: #f4f6f9;
                        --card: #ffffff;
                        --border: #e2e8f0;
                        --text: #0f172a;
                        --text-muted: #64748b;
                        --primary: #1e40af;
                        --primary-hover: #1e3a8a;
                        --success: #16a34a;
                        --success-hover: #15803d;
                        --danger: #dc2626;
                        --danger-hover: #b91c1c;
                        --warning-bg: #fef2f2;
                        --warning-border: #fecaca;
                        --radius: 14px;
                        --shadow: 0 4px 20px rgba(15, 23, 42, 0.04);
                    }

                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: 'Plus Jakarta Sans', 'Segoe UI', sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        min-height: 100vh;
                        padding: 32px 20px;
                        font-size: 14px;
                        line-height: 1.5;
                    }

                    .container {
                        max-width: 980px;
                        margin: 0 auto;
                        display: flex;
                        flex-direction: column;
                        gap: 20px;
                    }

                    /* ---------- HEADER ---------- */
                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        gap: 16px;
                        flex-wrap: wrap;
                    }

                    .header h1 {
                        font-size: 22px;
                        font-weight: 700;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }

                    .header h1 i { color: var(--primary); }

                    .back-link {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        color: var(--primary);
                        font-weight: 600;
                        text-decoration: none;
                        padding: 8px 14px;
                        border-radius: 10px;
                        transition: background 0.15s;
                    }

                    .back-link:hover { background: rgba(30, 64, 175, 0.08); }

                    /* ---------- CARD ---------- */
                    .card {
                        background: var(--card);
                        border: 1px solid var(--border);
                        border-radius: var(--radius);
                        box-shadow: var(--shadow);
                        padding: 22px 24px;
                    }

                    .card-title {
                        font-size: 15px;
                        font-weight: 700;
                        color: var(--text);
                        margin-bottom: 16px;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }

                    .card-title .count {
                        font-size: 13px;
                        font-weight: 600;
                        color: var(--text-muted);
                        background: var(--bg);
                        padding: 2px 10px;
                        border-radius: 20px;
                    }

                    /* ---------- BOUTONS ---------- */
                    .btn {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        padding: 10px 18px;
                        border: none;
                        border-radius: 10px;
                        font-family: inherit;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.15s;
                        text-decoration: none;
                        white-space: nowrap;
                    }

                    .btn:disabled { opacity: 0.6; cursor: not-allowed; }
                    .btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }

                    .btn-primary { background: var(--primary); color: white; }
                    .btn-primary:hover:not(:disabled) { background: var(--primary-hover); }

                    .btn-success { background: var(--success); color: white; }
                    .btn-success:hover:not(:disabled) { background: var(--success-hover); }

                    .btn-danger { background: var(--danger); color: white; }
                    .btn-danger:hover:not(:disabled) { background: var(--danger-hover); }

                    .btn-outline {
                        background: transparent;
                        border: 1px solid var(--border);
                        color: var(--text-muted);
                    }
                    .btn-outline:hover:not(:disabled) {
                        background: var(--bg);
                        color: var(--text);
                    }

                    .btn-icon {
                        padding: 8px 10px;
                        font-size: 13px;
                    }

                    /* ---------- TABLE ---------- */
                    .table-wrap {
                        overflow-x: auto;
                        border-radius: 10px;
                        margin: 0 -8px;
                    }

                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 14px;
                    }

                    thead th {
                        text-align: left;
                        padding: 12px 14px;
                        color: var(--text-muted);
                        font-size: 12px;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.03em;
                        border-bottom: 2px solid var(--border);
                        white-space: nowrap;
                    }

                    tbody td {
                        padding: 14px;
                        border-bottom: 1px solid var(--border);
                        vertical-align: middle;
                    }

                    tbody tr:last-child td { border-bottom: none; }
                    tbody tr:hover { background: #f8fafc; }

                    .file-cell {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        font-weight: 600;
                        word-break: break-all;
                    }

                    .file-cell i { color: var(--primary); flex-shrink: 0; }

                    .actions-cell {
                        display: flex;
                        gap: 6px;
                        justify-content: flex-end;
                    }

                    /* ---------- ÉTATS ---------- */
                    .state {
                        text-align: center;
                        padding: 48px 20px;
                        color: var(--text-muted);
                    }

                    .state i {
                        font-size: 36px;
                        margin-bottom: 12px;
                        display: block;
                        opacity: 0.5;
                    }

                    .state-title {
                        font-size: 15px;
                        font-weight: 600;
                        color: var(--text);
                        margin-bottom: 4px;
                    }

                    .state-subtitle { font-size: 13px; }

                    /* ---------- ALERTES ---------- */
                    .alert {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        padding: 14px 18px;
                        border-radius: 10px;
                        font-size: 14px;
                        font-weight: 500;
                    }

                    .alert-success {
                        background: #dcfce7;
                        color: #166534;
                        border-left: 4px solid var(--success);
                    }

                    .alert-error {
                        background: #fee2e2;
                        color: #991b1b;
                        border-left: 4px solid var(--danger);
                    }

                    /* ---------- ZONE DANGEREUSE ---------- */
                    .danger-zone {
                        background: var(--warning-bg);
                        border: 1px solid var(--warning-border);
                    }

                    .danger-zone .card-title { color: #991b1b; }

                    .danger-zone p {
                        color: #7f1d1d;
                        font-size: 13px;
                        margin-bottom: 16px;
                        line-height: 1.6;
                    }

                    /* ---------- TOAST ---------- */
                    .toast {
                        position: fixed;
                        bottom: 24px;
                        right: 24px;
                        background: #0f172a;
                        color: white;
                        padding: 14px 20px;
                        border-radius: 12px;
                        font-weight: 600;
                        font-size: 14px;
                        z-index: 1000;
                        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
                        animation: toastIn 0.3s ease, toastOut 0.3s ease 2.7s;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }

                    .toast.success { background: var(--success); }
                    .toast.error { background: var(--danger); }

                    @keyframes toastIn {
                        from { opacity: 0; transform: translateY(20px); }
                        to   { opacity: 1; transform: translateY(0); }
                    }
                    @keyframes toastOut {
                        from { opacity: 1; transform: translateY(0); }
                        to   { opacity: 0; transform: translateY(20px); }
                    }

                    /* ---------- FOOTER ---------- */
                    .footer {
                        text-align: center;
                        color: var(--text-muted);
                        font-size: 12px;
                        padding: 20px 0;
                    }

                    /* ---------- RESPONSIVE ---------- */
                    @media (max-width: 600px) {
                        body { padding: 16px 12px; }
                        .card { padding: 18px 16px; }
                        thead th, tbody td { padding: 10px 8px; }
                        .header { flex-direction: column; align-items: flex-start; }
                        .actions-cell { flex-direction: column; }
                    }
                </style>
            </head>
            <body>
            """);

        // ============================================================
        // BODY
        // ============================================================
        sb.append("<div class=\"container\">");

        // ---------- Header ----------
        sb.append("""
            <header class="header">
                <h1>
                    <i class="fas fa-database"></i>
                    Gestion des sauvegardes
                </h1>
                <a href="/admin/parametres" class="back-link">
                    <i class="fas fa-arrow-left"></i>
                    Retour aux paramètres
                </a>
            </header>
            """);

        // ---------- Messages flash ----------
        if (messageErreur != null && !messageErreur.isEmpty()) {
            sb.append("<div class=\"alert alert-error\" role=\"alert\">")
              .append("<i class=\"fas fa-circle-exclamation\"></i>")
              .append("<span>").append(echapperHtml(messageErreur)).append("</span>")
              .append("</div>");
        }
        if (messageSucces != null && !messageSucces.isEmpty()) {
            sb.append("<div class=\"alert alert-success\" role=\"status\">")
              .append("<i class=\"fas fa-circle-check\"></i>")
              .append("<span>").append(echapperHtml(messageSucces)).append("</span>")
              .append("</div>");
        }

        // ---------- Bouton créer ----------
        sb.append("""
            <div class="card" style="padding: 18px 24px;">
                <button class="btn btn-success" id="btnCreateBackup" type="button">
                    <i class="fas fa-plus-circle"></i>
                    Créer une nouvelle sauvegarde
                </button>
            </div>
            """);

        // ---------- Liste ----------
        sb.append("""
            <div class="card">
                <div class="card-title">
                    <i class="fas fa-list" style="color: var(--text-muted);"></i>
                    Sauvegardes existantes
                    <span class="count" id="backupCount">…</span>
                </div>
                <div id="backupList">
                    <div class="state">
                        <i class="fas fa-spinner fa-spin"></i>
                        <div class="state-title">Chargement…</div>
                    </div>
                </div>
            </div>
            """);

        // ---------- Zone dangereuse ----------
        sb.append("""
            <div class="card danger-zone">
                <div class="card-title">
                    <i class="fas fa-triangle-exclamation"></i>
                    Zone dangereuse
                </div>
                <p>
                    Supprime <strong>définitivement</strong> toutes les données de votre institution.
                    Cette action est <strong>irréversible</strong>.
                    Créez une sauvegarde avant de continuer.
                </p>
                <button class="btn btn-danger" id="btnDeleteData" type="button">
                    <i class="fas fa-trash"></i>
                    Supprimer mes données
                </button>
            </div>
            """);

        // ---------- Footer ----------
        sb.append("<footer class=\"footer\">© ")
          .append(Year.now().getValue())
          .append(" M-TECH — Tous droits réservés</footer>");

        sb.append("</div>");  // /container

        // ============================================================
        // SCRIPT
        // ============================================================
        // ⚠️ Le JS ne connaît PAS l'institutionId. Il ne l'envoie JAMAIS.
        //    C'est le HandlerSauvegarde qui le lit depuis la session.
        // ============================================================
        sb.append("""
            <script>
                'use strict';

                const backupListEl  = document.getElementById('backupList');
                const backupCountEl = document.getElementById('backupCount');
                const btnCreate     = document.getElementById('btnCreateBackup');
                const btnDeleteData = document.getElementById('btnDeleteData');

                const API = '/admin/parametres/sauvegarde/api';

                // ---------- UTILITAIRES ----------
                function echapperHtml(s) {
                    if (s == null) return '';
                    return String(s)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#39;');
                }

                function formaterTaille(octets) {
                    if (octets < 1024) return octets + ' o';
                    if (octets < 1024 * 1024) return (octets / 1024).toFixed(1) + ' Ko';
                    return (octets / (1024 * 1024)).toFixed(1) + ' Mo';
                }

                function formaterDate(ts) {
                    const d = new Date(ts);
                    return d.toLocaleString('fr-FR', {
                        day: '2-digit', month: '2-digit', year: 'numeric',
                        hour: '2-digit', minute: '2-digit'
                    });
                }

                async function lireJson(res) {
                    const texte = await res.text();
                    try {
                        return JSON.parse(texte);
                    } catch {
                        throw new Error('Réponse non-JSON du serveur');
                    }
                }

                function showToast(message, type) {
                    type = type || 'success';
                    const icone = type === 'success'
                        ? '<i class="fas fa-circle-check"></i>'
                        : '<i class="fas fa-circle-exclamation"></i>';
                    const toast = document.createElement('div');
                    toast.className = 'toast ' + type;
                    toast.innerHTML = icone + '<span>' + echapperHtml(message) + '</span>';
                    document.body.appendChild(toast);
                    setTimeout(function () { toast.remove(); }, 3000);
                }

                // ---------- ÉTATS ----------
                function afficherChargement() {
                    backupListEl.innerHTML =
                        '<div class="state">' +
                            '<i class="fas fa-spinner fa-spin"></i>' +
                            '<div class="state-title">Chargement…</div>' +
                        '</div>';
                    backupCountEl.textContent = '…';
                }

                function afficherVide() {
                    backupListEl.innerHTML =
                        '<div class="state">' +
                            '<i class="fas fa-inbox"></i>' +
                            '<div class="state-title">Aucune sauvegarde</div>' +
                            '<div class="state-subtitle">' +
                                'Cliquez sur « Créer une nouvelle sauvegarde » pour commencer.' +
                            '</div>' +
                        '</div>';
                    backupCountEl.textContent = '0';
                }

                function afficherErreur(msg) {
                    backupListEl.innerHTML =
                        '<div class="state">' +
                            '<i class="fas fa-circle-exclamation" style="color: var(--danger);"></i>' +
                            '<div class="state-title">Erreur de chargement</div>' +
                            '<div class="state-subtitle">' + echapperHtml(msg) + '</div>' +
                        '</div>';
                    backupCountEl.textContent = '!';
                }

                // ---------- LISTE ----------
                function afficherBackups(backups) {
                    if (!backups || backups.length === 0) {
                        afficherVide();
                        return;
                    }

                    backupCountEl.textContent = backups.length;

                    let html =
                        '<div class="table-wrap"><table><thead><tr>' +
                            '<th>Nom du fichier</th>' +
                            '<th>Taille</th>' +
                            '<th>Date</th>' +
                            '<th style="text-align:right;">Actions</th>' +
                        '</tr></thead><tbody>';

                    backups.forEach(function (b) {
                        const nom = echapperHtml(b.name);
                        const nomUrl = encodeURIComponent(b.name);
                        const taille = formaterTaille(b.size);
                        const date = formaterDate(b.lastModified);

                        html +=
                            '<tr>' +
                                '<td>' +
                                    '<div class="file-cell">' +
                                        '<i class="fas fa-file-code"></i>' +
                                        '<span>' + nom + '</span>' +
                                    '</div>' +
                                '</td>' +
                                '<td>' + taille + '</td>' +
                                '<td>' + date + '</td>' +
                                '<td>' +
                                    '<div class="actions-cell">' +
                                        '<a href="' + API + '/download?file=' + nomUrl + '" ' +
                                           'class="btn btn-outline btn-icon" ' +
                                           'title="Télécharger" download>' +
                                            '<i class="fas fa-download"></i>' +
                                        '</a>' +
                                        '<button type="button" ' +
                                                'class="btn btn-danger btn-icon btn-supprimer" ' +
                                                'data-file="' + nom + '" ' +
                                                'title="Supprimer">' +
                                            '<i class="fas fa-trash"></i>' +
                                        '</button>' +
                                    '</div>' +
                                '</td>' +
                            '</tr>';
                    });

                    html += '</tbody></table></div>';
                    backupListEl.innerHTML = html;

                    // Attacher les événements après le rendu
                    backupListEl.querySelectorAll('.btn-supprimer').forEach(function (btn) {
                        btn.addEventListener('click', function () {
                            supprimer(btn.dataset.file);
                        });
                    });
                }

                // ---------- CHARGER ----------
                async function chargerListe() {
                    afficherChargement();
                    try {
                        const res = await fetch(API + '/list');
                        const data = await lireJson(res);
                        if (data.success) {
                            afficherBackups(data.data);
                        } else {
                            afficherErreur(data.error || 'Erreur inconnue');
                        }
                    } catch (err) {
                        afficherErreur(err.message);
                    }
                }

                // ---------- SUPPRIMER UNE SAUVEGARDE ----------
                async function supprimer(filename) {
                    if (!confirm('Supprimer la sauvegarde « ' + filename + ' » ?')) return;

                    try {
                        const res = await fetch(
                            API + '/delete?file=' + encodeURIComponent(filename),
                            { method: 'DELETE' });
                        const data = await lireJson(res);

                        if (data.success) {
                            showToast('Sauvegarde supprimée', 'success');
                            chargerListe();
                        } else {
                            showToast(data.error || 'Erreur', 'error');
                        }
                    } catch (err) {
                        showToast('Erreur réseau : ' + err.message, 'error');
                    }
                }

                // ---------- CRÉER ----------
                btnCreate.addEventListener('click', async function () {
                    btnCreate.disabled = true;
                    const original = btnCreate.innerHTML;
                    btnCreate.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Création…';

                    try {
                        // ⚠️ PAS d'institutionId dans l'URL — le serveur le lit
                        //    depuis la session.
                        const res = await fetch(API + '/create', { method: 'POST' });
                        const data = await lireJson(res);

                        if (data.success) {
                            showToast('Sauvegarde créée avec succès', 'success');
                            chargerListe();
                        } else {
                            showToast(data.error || 'Erreur', 'error');
                        }
                    } catch (err) {
                        showToast('Erreur réseau : ' + err.message, 'error');
                    } finally {
                        btnCreate.disabled = false;
                        btnCreate.innerHTML = original;
                    }
                });

                // ---------- SUPPRIMER LES DONNÉES ----------
                btnDeleteData.addEventListener('click', async function () {
                    const reponse = prompt(
                        '⚠️ ATTENTION — Action irréversible\\n\\n' +
                        'Toutes les données de votre institution seront supprimées.\\n\\n' +
                        'Tapez SUPPRIMER pour confirmer :');

                    if (reponse !== 'SUPPRIMER') {
                        if (reponse !== null) showToast('Suppression annulée', 'error');
                        return;
                    }

                    btnDeleteData.disabled = true;
                    const original = btnDeleteData.innerHTML;
                    btnDeleteData.innerHTML =
                        '<i class="fas fa-spinner fa-spin"></i> Suppression…';

                    try {
                        // ⚠️ PAS d'institutionId dans l'URL — le serveur le lit
                        //    depuis la session. Impossible de cibler une autre
                        //    institution.
                        const res = await fetch(API + '/deleteData', { method: 'POST' });
                        const data = await lireJson(res);

                        if (data.success) {
                            showToast('Données supprimées', 'success');
                        } else {
                            showToast(data.error || 'Erreur', 'error');
                        }
                    } catch (err) {
                        showToast('Erreur réseau : ' + err.message, 'error');
                    } finally {
                        btnDeleteData.disabled = false;
                        btnDeleteData.innerHTML = original;
                    }
                });

                // ---------- DÉMARRAGE ----------
                chargerListe();
            </script>
            </body>
            </html>
            """);

        return sb.toString();
    }

    // ============================================================
    // ÉCHAPPEMENT HTML
    // ============================================================
    private static String echapperHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}