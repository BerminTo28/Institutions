import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.imageio.ImageIO;

public class DocumentPrivateHeader {

    private static final float DEFAULT_MARGIN = 30f;
    private static final String LOGO_PAYS_PATH = "/Haiti.png";

    // ============================================================
    // MÉTHODE PRINCIPALE
    // ============================================================
    public static float draw(Graphics2D g2, Institution institution,
                             float pageWidth, float pageHeight,
                             Date dateEmission,
                             BufferedImage logoInst, BufferedImage logoPays) {

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        float y = DEFAULT_MARGIN;
        int logoSize = 65;

        // ============================================================
        // 1. LOGO DE L'INSTITUTION (à gauche, avec un peu de marge)
        // ============================================================
        if (logoInst != null) {
            int leftX = (int) DEFAULT_MARGIN+50;
            int logoY = (int) y + 25;
            g2.drawImage(logoInst, leftX, logoY, logoSize, logoSize, null);
            System.out.println("✅ DocumentPrivateHeader: Logo institution affiché à gauche");
        } else {
            System.out.println("⚠️ DocumentPrivateHeader: Logo institution NULL");
        }

        // ============================================================
        // 2. NOM DE L'INSTITUTION (centré, en gras, majuscules, noir pur)
        // ============================================================
        g2.setFont(new Font("Times New Roman", Font.BOLD, 13));
        g2.setColor(Color.BLACK); // Forcé en noir pur opaque
        String nomInst = institution.getNomInstitution();
        if (nomInst == null || nomInst.trim().isEmpty()) {
            nomInst = "INSTITUTION NON DÉFINIE";
        } else {
            nomInst = nomInst.toUpperCase();
        }
        FontMetrics fm = g2.getFontMetrics();
        int xCenter = (int) (pageWidth / 2 - fm.stringWidth(nomInst) / 2);
        y += 8;
        g2.drawString(nomInst, xCenter, y);
        y += fm.getHeight() + 4;

        // ============================================================
        // 3. SIGLE (centré, entre parenthèses, en gras)
        // ============================================================
        String sigle = institution.getSigleInstitution();
        if (sigle != null && !sigle.trim().isEmpty()) {
            g2.setFont(new Font("Times New Roman", Font.BOLD, 12)); // Passé en gras et noir
            g2.setColor(Color.BLACK);
            String sigleText = "(" + sigle.toUpperCase() + ")";
            fm = g2.getFontMetrics();
            xCenter = (int) (pageWidth / 2 - fm.stringWidth(sigleText) / 2);
            g2.drawString(sigleText, xCenter, y);
            y += fm.getHeight() + 3;
        }

        // ============================================================
        // 4. DEVISES (centré, en italique, bien visible)
        // ============================================================
        String devise = institution.getDeviseInstitution();
        if (devise != null && !devise.trim().isEmpty()) {
            g2.setFont(new Font("Times New Roman", Font.ITALIC, 11));
            g2.setColor(new Color(40, 40, 40)); // Noir très foncé au lieu de gris clair
            fm = g2.getFontMetrics();
            xCenter = (int) (pageWidth / 2 - fm.stringWidth(devise) / 2);
            g2.drawString(devise, xCenter, y);
            y += fm.getHeight() + 4;
        }

        // ============================================================
        // 5. ADRESSE (centré, en gras)
        // ============================================================
        String adresse = institution.getAdresseInstitution();
        if (adresse != null && !adresse.trim().isEmpty()
                && !"Adresse non renseignée".equals(adresse)) {
            g2.setFont(new Font("Times New Roman", Font.BOLD, 10)); // Passé en gras
            g2.setColor(Color.BLACK);
            fm = g2.getFontMetrics();
            xCenter = (int) (pageWidth / 2 - fm.stringWidth(adresse) / 2);
            g2.drawString(adresse, xCenter, y);
            y += fm.getHeight() + 3;
        }

        // ============================================================
        // 6. EMAIL (centré, en gras)
        // ============================================================
        String emailPrincipal = institution.getMailPrimaire();
        String mailDirecteur = institution.getMailResponsableInstitution();
        StringBuilder emailText = new StringBuilder("Email : ");
        if (emailPrincipal != null && !emailPrincipal.trim().isEmpty()) {
            emailText.append(emailPrincipal);
        }
        if (mailDirecteur != null && !mailDirecteur.trim().isEmpty()) {
            if (emailText.length() > 8) emailText.append(", ");
            emailText.append(mailDirecteur);
        }
        if (emailText.length() == 8) {
            emailText.append("non renseigné");
        }
        g2.setFont(new Font("Times New Roman", Font.BOLD, 10)); // Passé en gras
        g2.setColor(Color.BLACK);
        fm = g2.getFontMetrics();
        xCenter = (int) (pageWidth / 2 - fm.stringWidth(emailText.toString()) / 2);
        g2.drawString(emailText.toString(), xCenter, y);
        y += fm.getHeight() + 3;

        // ============================================================
        // 7. TÉLÉPHONE (centré, en gras)
        // ============================================================
        String tel1 = institution.getTelephonePrimaireInstitution();
        String tel2 = institution.getTelephoneSecondaireInstitution();
        StringBuilder telText = new StringBuilder("Téléphone : ");
        if (tel1 != null && !tel1.trim().isEmpty()) {
            telText.append(tel1);
        }
        if (tel2 != null && !tel2.trim().isEmpty()) {
            if (telText.length() > 11) telText.append(" / ");
            telText.append(tel2);
        }
        if (telText.length() == 11) {
            telText.append("non renseigné");
        }
        g2.setFont(new Font("Times New Roman", Font.BOLD, 10)); // Passé en gras
        g2.setColor(Color.BLACK);
        fm = g2.getFontMetrics();
        xCenter = (int) (pageWidth / 2 - fm.stringWidth(telText.toString()) / 2);
        g2.drawString(telText.toString(), xCenter, y);
        y += fm.getHeight() + 10;

        // ============================================================
        // 8. DATE (alignée à droite, en gras)
        // ============================================================
        String commune = institution.getCommuneInstitution();
        if (commune == null || commune.trim().isEmpty()) {
            commune = "Port-au-Prince";
        }
        String dateStr = formatDate(dateEmission);
        String dateLine = "" + commune + ", le " + dateStr + "";
        g2.setFont(new Font("Times New Roman", Font.BOLD, 10)); // Passé en gras
        g2.setColor(Color.BLACK);
        fm = g2.getFontMetrics();
        int xDate = (int) (pageWidth - DEFAULT_MARGIN - fm.stringWidth(dateLine));
        g2.drawString(dateLine, xDate, y);
        y += fm.getHeight() + 12;

        // ============================================================
        // 9. LIGNE DE SÉPARATION (bien noire et nette au lieu de gris clair)
        // ============================================================
        g2.setColor(Color.BLACK); // Ligne noire pleine
        g2.setStroke(new java.awt.BasicStroke(1.0f)); // Trait légèrement plus appuyé
        int lineY = (int) y;
        g2.drawLine((int) DEFAULT_MARGIN, lineY, (int) (pageWidth - DEFAULT_MARGIN), lineY);
        y += 18;

        return y;
    }

    // ============================================================
    // SURCHARGE SANS LOGOS (charge automatiquement)
    // ============================================================
    public static float draw(Graphics2D g2, Institution institution,
                             float pageWidth, float pageHeight,
                             Date dateEmission) {

        BufferedImage logoInst = loadImage(institution.getLogoInstitution());
        BufferedImage logoPays = loadImage(LOGO_PAYS_PATH);

        return draw(g2, institution, pageWidth, pageHeight,
                   dateEmission, logoInst, logoPays);
    }

    // ============================================================
    // CHARGEMENT D'IMAGE
    // ============================================================
  // ============================================================
// CHARGEMENT D'IMAGE DYNAMIQUE VIA LOGORESOLVER
// ============================================================
private static BufferedImage loadImage(String path) {
    if (path == null || path.trim().isEmpty()) {
        System.err.println("⚠️ DocumentPrivateHeader: Chemin d'image NULL ou vide");
        return null;
    }
    System.out.println("🔍 DocumentPrivateHeader: Tentative de chargement dynamique: " + path);

    try {
        // 1. Utiliser le validateur/résolveur dynamique partagé (LogoResolver)
       Path resolvedPath = LogoResolver.trouverLogo(path);
if (resolvedPath != null && Files.exists(resolvedPath)) {
    BufferedImage img = ImageIO.read(resolvedPath.toFile());
    if (img != null) {
        System.out.println("✅ DocumentHeader: Logo décodé avec succès: " + resolvedPath.getFileName());
        return img;
    } else {
        System.err.println("❌ DocumentHeader: ImageIO a retourné NULL (Format d'image non supporté par Java, ex: WebP sans plugin ?): " + resolvedPath);
    }
}

        // 2. Fallback de secours : Ressource du classpath (pour les éléments statiques internes comme le logo pays)
        InputStream is = DocumentPrivateHeader.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path);
        if (is != null) {
            BufferedImage img = ImageIO.read(is);
            System.out.println("✅ DocumentPrivateHeader: Logo chargé depuis les ressources classpath: " + path);
            return img;
        }

        System.err.println("❌ DocumentPrivateHeader: Logo non trouvé dynamiquement: " + path);

    } catch (IOException e) {
        System.err.println("❌ DocumentPrivateHeader: Erreur chargement image: " + path + " - " + e.getMessage());
    }
    return null;
}
    // ============================================================
    // FORMATAGE DE LA DATE
    // ============================================================
    private static String formatDate(Date date) {
        if (date == null) date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH);
        return sdf.format(date);
    }
}