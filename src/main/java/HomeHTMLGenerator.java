import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HomeHTMLGenerator {

    private static final Logger LOGGER = Logger.getLogger(HomeHTMLGenerator.class.getName());

    private static final String LOGO_DATA_URI = chargerLogoDataUri();

    /** ✅ Palette claire : Bleu pâle + Jaune doré + Rouge corail + Blanc. */
    private static final String COULEUR_PRIMAIRE = "#3b82f6";   // Bleu ciel
    private static final String COULEUR_ACCENT   = "#fbbf24";   // Jaune doré
    private static final String COULEUR_DANGER   = "#ef4444";   // Rouge corail

    private HomeHTMLGenerator() { /* utilitaire */ }

    public static String generate(String alias, int port) {
        String logoSrc = LOGO_DATA_URI != null ? LOGO_DATA_URI : "";
        String fallbackLogo = (LOGO_DATA_URI == null)
                ? "<span style=\"font-weight:900;color:" + COULEUR_PRIMAIRE
                        + ";font-size:28px;letter-spacing:-1px;\">MT</span>"
                : "";

        // ✅ Noms de champs ALÉATOIRES — empêche l'autofill navigateur
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String nomIdentifiant   = "ident_" + suffix;
        String nomPassword      = "passwd_" + suffix;
        String nomFakeUser      = "fake_u_" + suffix;
        String nomFakePassword  = "fake_p_" + suffix;

        return TEMPLATE
                .replace("@LOGO_SRC@", logoSrc)
                .replace("@LOGO_FALLBACK@", fallbackLogo)
                .replace("@COULEUR_PRIMAIRE@", COULEUR_PRIMAIRE)
                .replace("@COULEUR_ACCENT@", COULEUR_ACCENT)
                .replace("@COULEUR_DANGER@", COULEUR_DANGER)
                .replace("@NOM_IDENTIFIANT@", nomIdentifiant)
                .replace("@NOM_PASSWORD@", nomPassword)
                .replace("@NOM_FAKE_USER@", nomFakeUser)
                .replace("@NOM_FAKE_PASSWORD@", nomFakePassword);
    }

    // ==================== CHARGEMENT DU LOGO ====================

    private static String chargerLogoDataUri() {
        try (InputStream in = HomeHTMLGenerator.class.getResourceAsStream("/MTech.png")) {
            if (in == null) {
                LOGGER.warning("⚠️ /MTech.png introuvable — fallback texte 'MT' activé.");
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                LOGGER.warning("⚠️ /MTech.png est vide.");
                return null;
            }
            String b64 = Base64.getEncoder().encodeToString(bytes);
            LOGGER.fine(() -> "✅ Logo chargé (" + bytes.length + " octets).");
            return "data:image/png;base64," + b64;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "⚠️ Erreur lecture /MTech.png", e);
            return null;
        }
    }

    // ==================== TEMPLATE HTML ====================

    private static final String TEMPLATE = """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
          <meta name="theme-color" content="#dbeafe">
          <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate">
          <meta http-equiv="Pragma" content="no-cache">
          <title>M-TECH · Connexion</title>

          <link rel="preconnect" href="https://fonts.googleapis.com">
          <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
          <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">
          <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">

          <style>
            *, *::before, *::after {
              margin: 0;
              padding: 0;
              box-sizing: border-box;
            }

            :root {
              --primary: @COULEUR_PRIMAIRE@;
              --primary-light: #60a5fa;
              --primary-pale: #dbeafe;
              --primary-soft: #eff6ff;
              --primary-deep: #2563eb;

              --accent: @COULEUR_ACCENT@;
              --accent-light: #fcd34d;
              --accent-pale: #fef3c7;
              --accent-soft: rgba(251, 191, 36, 0.18);
              --accent-dark: #f59e0b;

              --danger: @COULEUR_DANGER@;
              --danger-light: #f87171;
              --danger-pale: #fee2e2;
              --danger-soft: rgba(239, 68, 68, 0.15);
              --danger-dark: #dc2626;

              --success: #10b981;
              --success-soft: rgba(16, 185, 129, 0.15);

              --white: #ffffff;
              --text-dark: #1e293b;
              --text-mid: #475569;
              --text-light: #64748b;
              --gray-50: #f8fafc;
              --gray-100: #f1f5f9;
              --gray-200: #e2e8f0;
              --gray-300: #cbd5e1;
              --gray-400: #94a3b8;

              --radius-md: 12px;
              --radius-lg: 16px;
              --radius-xl: 24px;

              --transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            }

            html, body { width: 100%; min-height: 100vh; }

            body {
              font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
              background:
                radial-gradient(ellipse at top left, rgba(96, 165, 250, 0.35) 0%, transparent 55%),
                radial-gradient(ellipse at bottom right, rgba(251, 191, 36, 0.30) 0%, transparent 55%),
                radial-gradient(circle at 85% 15%, rgba(239, 68, 68, 0.15) 0%, transparent 45%),
                linear-gradient(135deg, #eff6ff 0%, #dbeafe 50%, #bfdbfe 100%);
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              color: var(--text-dark);
              padding: clamp(12px, 3vw, 32px);
              -webkit-font-smoothing: antialiased;
              -moz-osx-font-smoothing: grayscale;
              position: relative;
              overflow-x: hidden;
            }

            body::before, body::after {
              content: '';
              position: fixed;
              border-radius: 50%;
              filter: blur(120px);
              opacity: 0.55;
              pointer-events: none;
              z-index: 0;
            }
            body::before {
              width: 450px; height: 450px;
              background: var(--accent-light);
              top: -120px; left: -120px;
              animation: float 22s ease-in-out infinite;
            }
            body::after {
              width: 500px; height: 500px;
              background: var(--primary-light);
              bottom: -180px; right: -180px;
              animation: float 28s ease-in-out infinite reverse;
              opacity: 0.45;
            }
            @keyframes float {
              0%, 100% { transform: translate(0, 0) scale(1); }
              50%      { transform: translate(40px, -40px) scale(1.15); }
            }

            /* ============================================================
               WRAPPER
               ============================================================ */
            .wrapper {
              width: 100%;
              max-width: 440px;
              background: #ffffff;
              border-radius: var(--radius-xl);
              padding: clamp(28px, 5vw, 44px) clamp(22px, 4vw, 38px);
              box-shadow:
                0 24px 48px -16px rgba(37, 99, 235, 0.18),
                0 8px 20px -6px rgba(251, 191, 36, 0.12),
                0 0 0 1px rgba(255, 255, 255, 0.8) inset;
              border: 1px solid rgba(191, 219, 254, 0.5);
              position: relative;
              z-index: 1;
              animation: slideUp 0.5s cubic-bezier(0.4, 0, 0.2, 1);
              overflow: hidden;
            }

            .wrapper::before {
              content: '';
              position: absolute;
              top: 0; left: 0; right: 0;
              height: 5px;
              background: linear-gradient(90deg,
                var(--primary) 0%,
                var(--primary) 33%,
                var(--accent) 33%,
                var(--accent) 66%,
                var(--danger) 66%,
                var(--danger) 100%);
            }

            @keyframes slideUp {
              from { opacity: 0; transform: translateY(20px) scale(0.98); }
              to   { opacity: 1; transform: translateY(0) scale(1); }
            }

            /* ============================================================
               BRAND
               ============================================================ */
            .brand-section {
              text-align: center;
              margin-bottom: clamp(24px, 4vw, 32px);
            }

            .logo-container {
              width: clamp(76px, 18vw, 96px);
              height: clamp(76px, 18vw, 96px);
              margin: 0 auto clamp(14px, 2vw, 18px);
              background: #ffffff;
              border: 3px solid var(--accent);
              border-radius: 50%;
              display: flex;
              align-items: center;
              justify-content: center;
              box-shadow:
                0 10px 28px rgba(59, 130, 246, 0.25),
                0 0 0 6px rgba(251, 191, 36, 0.20),
                0 0 0 12px rgba(96, 165, 250, 0.10);
              overflow: hidden;
              transition: var(--transition);
              position: relative;
              aspect-ratio: 1 / 1;
              flex-shrink: 0;
              clip-path: circle(50%);
              -webkit-clip-path: circle(50%);
            }

            .logo-container:hover {
              transform: scale(1.06);
              box-shadow:
                0 14px 34px rgba(59, 130, 246, 0.35),
                0 0 0 8px rgba(251, 191, 36, 0.30),
                0 0 0 16px rgba(96, 165, 250, 0.15);
            }

            .logo-container img {
              width: 100%;
              height: 100%;
              object-fit: cover;
              object-position: center;
              display: block;
              border-radius: 50%;
            }

            .brand-title {
              font-size: clamp(24px, 5vw, 30px);
              font-weight: 900;
              letter-spacing: -0.8px;
              margin-bottom: 8px;
              background: linear-gradient(135deg, #3b82f6 0%, #60a5fa 50%, #2563eb 100%);
              -webkit-background-clip: text;
              -webkit-text-fill-color: transparent;
              background-clip: text;
            }

            .brand-subtitle {
              font-size: clamp(12px, 2.5vw, 13px);
              color: var(--text-light);
              font-weight: 500;
              line-height: 1.5;
              padding: 0 8px;
            }

            .brand-divider {
              display: flex;
              align-items: center;
              justify-content: center;
              gap: 6px;
              margin-top: 14px;
            }
            .brand-divider .dot {
              width: 8px; height: 8px;
              border-radius: 50%;
              box-shadow: 0 2px 6px rgba(0,0,0,0.1);
            }
            .brand-divider .dot.blue   { background: var(--primary); }
            .brand-divider .dot.yellow { background: var(--accent); }
            .brand-divider .dot.red    { background: var(--danger); }
            .brand-divider .line {
              width: 32px; height: 2px;
              background: var(--gray-200);
              border-radius: 1px;
            }

            /* ============================================================
               FORMULAIRE
               ============================================================ */
            .input-group {
              margin-bottom: clamp(14px, 2.5vw, 18px);
              display: flex;
              flex-direction: column;
              align-items: center;
              width: 100%;
            }

            .input-label {
              display: block;
              font-size: 11px;
              font-weight: 700;
              color: var(--text-mid);
              margin-bottom: 8px;
              text-transform: uppercase;
              letter-spacing: 0.8px;
              text-align: center;
              width: 100%;
            }

            .input-field-wrapper {
              position: relative;
              width: 100%;
              max-width: 340px;
              margin: 0 auto;
            }

            .input-field-wrapper > i {
              position: absolute;
              left: 16px;
              top: 50%;
              transform: translateY(-50%);
              color: var(--gray-400);
              font-size: 14px;
              transition: color 0.2s;
              pointer-events: none;
              z-index: 2;
            }

            .input-field {
              width: 100%;
              background: var(--primary-soft);
              border: 1.5px solid var(--primary-pale);
              border-radius: var(--radius-md);
              padding: 13px 16px 13px 42px;
              font-size: 14px;
              color: var(--text-dark);
              font-family: inherit;
              font-weight: 500;
              transition: var(--transition);
              text-align: center;
              -webkit-appearance: none;
              appearance: none;
            }

            .input-field::placeholder {
              color: var(--gray-400);
              font-weight: 400;
              text-align: center;
            }

            .input-field:hover {
              border-color: var(--primary-light);
              background: #ffffff;
            }

            .input-field:focus {
              outline: none;
              border-color: var(--primary);
              box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.18);
              background: #ffffff;
            }

            .input-field-wrapper:focus-within > i { color: var(--primary); }

            /* ============================================================
               BOUTON PRINCIPAL
               ============================================================ */
            .btn-submit {
              width: 100%;
              max-width: 340px;
              margin: clamp(18px, 3vw, 24px) auto 0;
              background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
              color: white;
              border: none;
              border-radius: var(--radius-md);
              padding: clamp(13px, 2.5vw, 15px);
              font-size: clamp(14px, 2.5vw, 15px);
              font-weight: 700;
              font-family: inherit;
              cursor: pointer;
              box-shadow: 0 8px 24px -6px rgba(59, 130, 246, 0.5);
              transition: var(--transition);
              display: flex;
              align-items: center;
              justify-content: center;
              gap: 10px;
              letter-spacing: 0.3px;
              position: relative;
              overflow: hidden;
            }

            .btn-submit::before {
              content: '';
              position: absolute;
              top: 0; left: -100%;
              width: 100%; height: 100%;
              background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent);
              transition: left 0.6s;
            }
            .btn-submit:hover:not(:disabled)::before { left: 100%; }

            .btn-submit:hover:not(:disabled) {
              background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
              color: #1e3a8a;
              transform: translateY(-2px);
              box-shadow: 0 14px 32px -6px rgba(251, 191, 36, 0.55);
            }
            .btn-submit:active:not(:disabled) { transform: translateY(0); }
            .btn-submit:disabled { opacity: 0.7; cursor: not-allowed; }

            .btn-submit i { transition: transform 0.25s; }
            .btn-submit:hover:not(:disabled) i { transform: translateX(4px); }

            /* ============================================================
               MESSAGES
               ============================================================ */
            .feedback-message {
              margin-top: 14px;
              padding: 12px 16px;
              border-radius: var(--radius-md);
              font-size: 13px;
              font-weight: 500;
              text-align: center;
              display: none;
              align-items: center;
              justify-content: center;
              gap: 8px;
              animation: shakeIn 0.3s ease;
              max-width: 340px;
              margin-left: auto;
              margin-right: auto;
            }
            @keyframes shakeIn {
              0%   { transform: translateX(0); }
              25%  { transform: translateX(-6px); }
              50%  { transform: translateX(6px); }
              75%  { transform: translateX(-3px); }
              100% { transform: translateX(0); }
            }

            .feedback-message.error {
              background: var(--danger-pale);
              color: #991b1b;
              border: 1px solid #fca5a5;
              border-left: 4px solid var(--danger);
              display: flex;
            }
            .feedback-message.success {
              background: #d1fae5;
              color: #065f46;
              border: 1px solid #6ee7b7;
              border-left: 4px solid var(--success);
              display: flex;
            }

            /* ============================================================
               DIVIDER
               ============================================================ */
            .divider {
              height: 1px;
              background: linear-gradient(90deg, transparent, var(--gray-200), transparent);
              margin: clamp(20px, 3vw, 26px) 0;
              position: relative;
            }
            .divider::before {
              content: 'OU';
              position: absolute;
              top: 50%; left: 50%;
              transform: translate(-50%, -50%);
              background: #ffffff;
              padding: 0 12px;
              font-size: 10px;
              font-weight: 700;
              color: var(--gray-400);
              letter-spacing: 1px;
            }

            /* ============================================================
               ACTIONS SECONDAIRES
               ============================================================ */
            .actions-grid {
              display: grid;
              grid-template-columns: 1fr 1fr;
              gap: 10px;
            }

            .btn-secondary {
              background: #ffffff;
              border: 1.5px solid var(--gray-200);
              color: var(--text-mid);
              padding: clamp(10px, 2vw, 12px) 8px;
              border-radius: var(--radius-md);
              font-size: clamp(10px, 2vw, 11px);
              font-weight: 600;
              font-family: inherit;
              text-decoration: none;
              display: flex;
              align-items: center;
              justify-content: center;
              gap: 6px;
              transition: var(--transition);
              text-align: center;
              box-shadow: 0 2px 8px rgba(0,0,0,0.04);
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
            .btn-secondary i {
              color: var(--primary);
              font-size: 13px;
              transition: transform 0.2s, color 0.2s;
            }

            #btnAdmin {
              background: var(--accent-pale);
              border-color: rgba(251, 191, 36, 0.5);
              color: #92400e;
            }
            #btnAdmin i { color: var(--accent-dark); }
            #btnAdmin:hover {
              background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
              border-color: var(--accent-dark);
              color: #ffffff;
              transform: translateY(-2px);
              box-shadow: 0 8px 20px -4px rgba(251, 191, 36, 0.55);
            }
            #btnAdmin:hover i { color: #ffffff; transform: scale(1.15); }

            #btnEtudiant {
              background: var(--primary-soft);
              border-color: rgba(59, 130, 246, 0.4);
              color: #1e40af;
            }
            #btnEtudiant:hover {
              background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
              border-color: var(--primary);
              color: #ffffff;
              transform: translateY(-2px);
              box-shadow: 0 8px 20px -4px rgba(59, 130, 246, 0.5);
            }
            #btnEtudiant:hover i { color: #ffffff; transform: scale(1.15); }

            /* ============================================================
               INDICATEURS RÉSEAU
               ============================================================ */
            .network-status {
              margin-top: clamp(18px, 3vw, 24px);
              padding-top: clamp(14px, 2.5vw, 18px);
              border-top: 1px dashed var(--gray-200);
              display: flex;
              justify-content: center;
              align-items: center;
              flex-wrap: wrap;
              gap: clamp(16px, 4vw, 28px);
            }

            .status-item {
              display: flex;
              align-items: center;
              gap: 8px;
              font-size: 11px;
              font-weight: 700;
              color: var(--text-mid);
              letter-spacing: 0.4px;
              text-transform: uppercase;
              white-space: nowrap;
            }

            .status-dot {
              width: 10px;
              height: 10px;
              border-radius: 50%;
              background: var(--danger);
              transition: background 0.3s, box-shadow 0.3s;
              flex-shrink: 0;
              position: relative;
            }

            .status-dot.off {
              background: var(--danger);
              animation: pulseRed 1.6s ease-in-out infinite;
            }
            @keyframes pulseRed {
              0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7); }
              50%      { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
            }

            .status-dot.on {
              background: var(--success);
              box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.25);
              animation: none;
            }

            /* ============================================================
               SPINNER
               ============================================================ */
            .spinner {
              width: 16px; height: 16px;
              border: 2px solid rgba(255, 255, 255, 0.4);
              border-top-color: #ffffff;
              border-radius: 50%;
              animation: spin 0.7s linear infinite;
              flex-shrink: 0;
            }
            @keyframes spin { to { transform: rotate(360deg); } }

            /* ============================================================
               ANTI-AUTOFILL — technique renforcée
               ============================================================ */
            .fake-field {
              position: absolute !important;
              top: 0 !important;
              left: 0 !important;
              width: 1px !important;
              height: 1px !important;
              opacity: 0.01 !important;
              pointer-events: none !important;
              z-index: -1 !important;
              overflow: hidden !important;
              border: 0 !important;
            }

            input:-webkit-autofill,
            input:-webkit-autofill:hover,
            input:-webkit-autofill:focus,
            input:-webkit-autofill:active {
              -webkit-box-shadow: 0 0 0 30px var(--primary-soft) inset !important;
              -webkit-text-fill-color: var(--text-dark) !important;
              transition: background-color 5000s ease-in-out 0s !important;
            }

            /* ✅ Champ readonly = navigateur ne peut pas préremplir */
            input[readonly] {
              background: var(--primary-soft) !important;
              cursor: text;
            }

            /* ============================================================
               RESPONSIVE
               ============================================================ */
            @media (max-width: 380px) {
              .actions-grid { grid-template-columns: 1fr; }
              .btn-secondary { font-size: 11px; padding: 11px 12px; }
              .brand-subtitle { font-size: 11px; }
              .status-item { font-size: 10px; }
            }

            @media (max-width: 480px) {
              .wrapper { border-radius: var(--radius-lg); }
            }

            @media (min-width: 1024px) {
              .wrapper { max-width: 460px; }
            }

            @media (min-width: 1600px) {
              .wrapper { max-width: 500px; padding: 48px 42px; }
            }

            @media (max-height: 700px) and (orientation: landscape) {
              body { align-items: flex-start; padding: 16px; }
              .brand-section { margin-bottom: 16px; }
              .logo-container { width: 60px; height: 60px; }
              .brand-title { font-size: 20px; }
              .input-group { margin-bottom: 10px; }
              .btn-submit { margin-top: 14px; }
            }

            @media (prefers-reduced-motion: reduce) {
              *, *::before, *::after {
                animation-duration: 0.01ms !important;
                animation-iteration-count: 1 !important;
                transition-duration: 0.01ms !important;
              }
              body::before, body::after { display: none; }
            }
          </style>
        </head>
        <body>

          <div class="wrapper">
            <!-- BRAND -->
            <div class="brand-section">
              <div class="logo-container">
                @LOGO_FALLBACK@
                <img src="@LOGO_SRC@" alt="M-TECH"
                     onerror="this.style.display='none';">
              </div>
              <h1 class="brand-title">M-TECH</h1>
              <p class="brand-subtitle">Gérez votre institution en toute sérénité</p>

              <div class="brand-divider">
                <span class="line"></span>
                <span class="dot blue"></span>
                <span class="dot yellow"></span>
                <span class="dot red"></span>
                <span class="line"></span>
              </div>
            </div>

            <!--
              ✅ FORMULAIRE ANTI-AUTOFILL RENFORCÉ
              - noms de champs aléatoires (changent à chaque chargement)
              - champs leurres cachés
              - readonly initial retiré au focus
              - attributs data-*-ignore pour tous les gestionnaires
            -->
            <form id="loginForm"
                  onsubmit="return false;"
                  autocomplete="off"
                  name="loginForm_@NOM_IDENTIFIANT@">

              <!-- Champs leurres (pièges à autofill) -->
              <input type="text"
                     name="@NOM_FAKE_USER@"
                     class="fake-field"
                     tabindex="-1"
                     aria-hidden="true"
                     autocomplete="username"
                     readonly
                     onfocus="this.removeAttribute('readonly');">

              <input type="password"
                     name="@NOM_FAKE_PASSWORD@"
                     class="fake-field"
                     tabindex="-1"
                     aria-hidden="true"
                     autocomplete="current-password"
                     readonly
                     onfocus="this.removeAttribute('readonly');">

              <!-- Vrai champ identifiant -->
              <div class="input-group">
                <label class="input-label" for="loginIdentifiant">Identifiant</label>
                <div class="input-field-wrapper">
                  <input type="text"
                         id="loginIdentifiant"
                         name="@NOM_IDENTIFIANT@"
                         class="input-field"
                         placeholder="Entrez votre identifiant"
                         autocomplete="off"
                         autocorrect="off"
                         autocapitalize="off"
                         spellcheck="false"
                         data-lpignore="true"
                         data-1p-ignore="true"
                         data-bwignore="true"
                         data-dashlane-ignore="true"
                         data-form-type="other"
                         inputmode="text"
                         readonly
                         onfocus="this.removeAttribute('readonly');">
                  <i class="fas fa-user"></i>
                </div>
              </div>

              <!-- Vrai champ mot de passe -->
              <div class="input-group">
                <label class="input-label" for="loginComplement">Mot de passe</label>
                <div class="input-field-wrapper">
                  <input type="password"
                         id="loginComplement"
                         name="@NOM_PASSWORD@"
                         class="input-field"
                         placeholder="••••••••••••"
                         autocomplete="new-password"
                         data-lpignore="true"
                         data-1p-ignore="true"
                         data-bwignore="true"
                         data-dashlane-ignore="true"
                         data-form-type="other"
                         readonly
                         onfocus="this.removeAttribute('readonly');">
                  <i class="fas fa-lock"></i>
                </div>
              </div>

              <button type="submit" class="btn-submit" id="btnConnect">
                <span>Se connecter</span>
                <i class="fas fa-arrow-right"></i>
              </button>
            </form>

            <div id="loginMessage" class="feedback-message"></div>

            <div class="divider"></div>

            <!-- INVITE AUX INSCRIPTIONS -->
            <div class="actions-grid">
              <a href="/api/inscription/admin" class="btn-secondary" id="btnAdmin">
                <i class="fas fa-university"></i>
                <span>Créer Institution</span>
              </a>
              <a href="/inscription/etudiant" class="btn-secondary" id="btnEtudiant">
                <i class="fas fa-user-graduate"></i>
                <span>Inscription Étudiant</span>
              </a>
            </div>

            <!-- INDICATEURS RÉSEAU -->
            <div class="network-status" title="État des réseaux">
              <div class="status-item">
                <span class="status-dot off" id="dotLocal"></span>
                <span class="label">local</span>
              </div>
              <div class="status-item">
                <span class="status-dot off" id="dotDistant"></span>
                <span class="label">distant</span>
              </div>
            </div>
          </div>

          <script>
            /* ============================================================
               ✅ ANTI-AUTOFILL — nettoyage forcé
               ============================================================ */
            (function() {
              'use strict';

              function viderChamps() {
                const ids = ['loginIdentifiant', 'loginComplement'];
                ids.forEach(function(id) {
                  const el = document.getElementById(id);
                  if (el && el.value) {
                    el.value = '';
                  }
                });
              }

              // 1) Nettoyage au chargement initial
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', viderChamps);
              } else {
                viderChamps();
              }

              // 2) Nettoyage agressif : plusieurs tentatives sur 2 secondes
              //    (Chrome/Firefox remplissent parfois 200-500ms après le load)
              let tentatives = 0;
              const interval = setInterval(function() {
                viderChamps();
                tentatives++;
                if (tentatives >= 10) clearInterval(interval);
              }, 200);

              // 3) Nettoyage au pageshow (retour arrière/avant du navigateur)
              window.addEventListener('pageshow', viderChamps);

              // 4) Nettoyage lors du focus (dernier rempart)
              ['loginIdentifiant', 'loginComplement'].forEach(function(id) {
                const el = document.getElementById(id);
                if (!el) return;
                el.addEventListener('focus', function() {
                  this.removeAttribute('readonly');
                });
              });
            })();

            /* ============================================================
               SURVEILLANCE DES RÉSEAUX
               ============================================================ */
            (function() {
              const dotLocal = document.getElementById('dotLocal');
              const dotDistant = document.getElementById('dotDistant');

              function setEtat(dot, actif) {
                if (!dot) return;
                dot.classList.toggle('on', actif);
                dot.classList.toggle('off', !actif);
                dot.title = actif ? 'Réseau actif' : 'Réseau inactif';
              }

              async function verifier() {
                try {
                  const r = await fetch('/api/health', {
                    cache: 'no-store',
                    headers: { 'Accept': 'application/json' }
                  });
                  if (!r.ok) {
                    setEtat(dotLocal, false);
                    setEtat(dotDistant, false);
                    return;
                  }
                  const data = await r.json();
                  setEtat(dotLocal,   data.local   === true);
                  setEtat(dotDistant, data.distant === true);
                } catch (e) {
                  setEtat(dotLocal, false);
                  setEtat(dotDistant, false);
                }
              }

              verifier();
              setInterval(verifier, 4000);

              document.addEventListener('visibilitychange', function() {
                if (!document.hidden) verifier();
              });
            })();

            /* ============================================================
               FORMULAIRE DE CONNEXION
               ============================================================ */
            let enCours = false;

            function detecterRole(identifiant) {
              if (!identifiant) return 'AUTO';
              const patternEtudiant = /^[A-Z]{4}\\d{8}[A-Z]\\d{7}$/;
              if (patternEtudiant.test(identifiant)) return 'ETUDIANT';
              const patternProf = /^PROF\\d{8}$/;
              if (patternProf.test(identifiant)) return 'PROFESSEUR';
              const patternAdmin = /^[A-Z]{2,4}\\d{6}$/;
              if (patternAdmin.test(identifiant)) return 'ADMINISTRATEUR';
              return 'AUTO';
            }

            const btnConnect = document.getElementById('btnConnect');
            const identifiantInput = document.getElementById('loginIdentifiant');
            const complementInput = document.getElementById('loginComplement');
            const messageEl = document.getElementById('loginMessage');

            function afficherMessage(type, texte, icone) {
              messageEl.className = 'feedback-message ' + type;
              messageEl.innerHTML = '<i class="fas fa-' + icone + '"></i><span>' + texte + '</span>';
            }

            function reinitialiserMessage() {
              messageEl.className = 'feedback-message';
              messageEl.textContent = '';
            }

            async function tenterConnexion(role, identifiant, complement) {
              const body = new URLSearchParams({ role, identifiant, complement });
              const response = await fetch('/api/connexion', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
              });
              return await response.json();
            }

            btnConnect.addEventListener('click', async function(e) {
              e.preventDefault();
              if (enCours) return;

              const identifiant = identifiantInput.value.trim();
              const complement = complementInput.value.trim();

              reinitialiserMessage();

              if (!identifiant || !complement) {
                afficherMessage('error', 'Veuillez remplir tous les champs.', 'exclamation-circle');
                (identifiant ? complementInput : identifiantInput).focus();
                return;
              }

              const role = detecterRole(identifiant);
              enCours = true;
              const originalHtml = btnConnect.innerHTML;
              btnConnect.innerHTML = '<div class="spinner"></div><span>Connexion...</span>';
              btnConnect.disabled = true;

              try {
                let data = await tenterConnexion(role, identifiant, complement);

                if (!data.success && role !== 'AUTO') {
                  data = await tenterConnexion('AUTO', identifiant, complement);
                }

                if (data.success) {
                  afficherMessage('success', 'Connexion réussie. Redirection...', 'check-circle');
                  setTimeout(() => {
                    if (data.redirect) window.location.href = data.redirect;
                  }, 500);
                } else {
                  afficherMessage('error', data.message || 'Identifiants invalides.', 'exclamation-triangle');
                  complementInput.value = '';
                  complementInput.focus();
                }
              } catch (err) {
                console.error('Erreur connexion:', err);
                afficherMessage('error', 'Erreur de connexion au serveur.', 'wifi');
              } finally {
                btnConnect.innerHTML = originalHtml;
                btnConnect.disabled = false;
                enCours = false;
              }
            });

            document.addEventListener('keydown', function(e) {
              if (e.key === 'Enter') {
                const actif = document.activeElement;
                if (actif === identifiantInput || actif === complementInput) {
                  e.preventDefault();
                  btnConnect.click();
                }
              }
            });
          </script>
        </body>
        </html>
        """;
}