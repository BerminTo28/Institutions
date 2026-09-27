import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.google.zxing.WriterException;

public class DocumentFooter {

    private static final float DEFAULT_MARGIN = 50f;
    private static final float PAGE_WIDTH = 595f;
    private static final float PAGE_HEIGHT = 842f;
    private static final String LOGO_DIR = System.getProperty("user.dir") + "/logos/";

    public static void draw(Graphics2D g2, Institution institution, String reference, String numero) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int qrSize = 40;
        int logoSize = 40;
        int espaceTexte = 10;
        int lineHeight = 14;

        // Données
        String adresse = (institution.getAdresseInstitution() != null && !institution.getAdresseInstitution().trim().isEmpty())
                ? institution.getAdresseInstitution().trim()
                : "#06, Rue Tertulien Guilbaud (Route de Bourdon) B.P 1796 Port-au-Prince, Haïti";

        String tel1 = institution.getTelephonePrimaireInstitution();
        String tel2 = institution.getTelephoneSecondaireInstitution();
        String telResp = institution.getTelephoneResponsableInstitution();
        StringBuilder telephoneBuilder = new StringBuilder("Tél : ");
        if (tel1 != null && !tel1.trim().isEmpty()) telephoneBuilder.append(tel1.trim());
        if (tel2 != null && !tel2.trim().isEmpty()) {
            if (telephoneBuilder.length() > 6) telephoneBuilder.append(" / ");
            telephoneBuilder.append(tel2.trim());
        }
        if (telResp != null && !telResp.trim().isEmpty()) {
            if (telephoneBuilder.length() > 6) telephoneBuilder.append(" / ");
            telephoneBuilder.append(telResp.trim());
        }
        if (telephoneBuilder.length() == 6) telephoneBuilder.append("non renseigné");
        String telephone = telephoneBuilder.toString();

        String email1 = (institution.getMailPrimaire() != null && !institution.getMailPrimaire().trim().isEmpty())
                ? institution.getMailPrimaire().trim() : "secretariat.edu@ctpea.ht";
        String email2 = institution.getMailSecondaire();
        String email3 = institution.getMailResponsableInstitution();
        StringBuilder emails = new StringBuilder("E-mail : ").append(email1);
        if (email2 != null && !email2.trim().isEmpty()) emails.append(" / ").append(email2.trim());
        if (email3 != null && !email3.trim().isEmpty()) emails.append(" / ").append(email3.trim());
        String emailLine = emails.toString();

        // QR Code
        BufferedImage qrImage = generateQRCode(institution, reference, numero, qrSize);
        float baseY = PAGE_HEIGHT - DEFAULT_MARGIN;

        int qrX = (int) DEFAULT_MARGIN;
        int qrY = (int) (baseY - qrSize);

        if (qrImage != null) {
            // Fond noir avec arrondis
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(qrX - 2, qrY - 2, qrSize + 4, qrSize + 4, 4, 4);
            // Fond blanc pour le QR
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(qrX, qrY, qrSize, qrSize, 4, 4);

            int padding = 3;
            g2.drawImage(qrImage, qrX + padding, qrY + padding,
                         qrSize - (padding * 2), qrSize - (padding * 2), null);

            // ---- BANDE BLANCHE EN BAS POUR "SCAN ME" ----
            int bandeHauteur = 12;
            int bandeY = qrY + qrSize - bandeHauteur;
            g2.setColor(Color.WHITE);
            g2.fillRect(qrX + 2, bandeY, qrSize - 4, bandeHauteur);

            // Texte "SCAN ME" en noir sur fond blanc
            g2.setFont(new Font("Arial", Font.BOLD, 8));
            g2.setColor(Color.BLACK);
            String scan = "SCAN ME";
            FontMetrics fmSmall = g2.getFontMetrics();
            int scanX = qrX + (qrSize - fmSmall.stringWidth(scan)) / 2;
            int scanY = bandeY + (bandeHauteur + fmSmall.getAscent()) / 2 - 1;
            g2.drawString(scan, scanX, scanY);
        }

        // Logo
        BufferedImage logoInst = loadInstitutionLogo(institution);
        int logoX = (int) (PAGE_WIDTH - DEFAULT_MARGIN - logoSize);
        int logoY = (int) (baseY - logoSize);
        if (logoInst != null) {
            g2.drawImage(logoInst, logoX, logoY, logoSize, logoSize, null);
        }

        // ===== TEXTE DU PIED DE PAGE (corrigé) =====
        g2.setFont(new Font("Times New Roman", Font.BOLD, 8));
        g2.setColor(Color.BLACK); // ✅ Forcer la couleur noire pour le texte
        FontMetrics fm = g2.getFontMetrics();
        int descent = fm.getDescent();

        int emailY = (int) (baseY - descent);
        int telY = emailY - lineHeight;
        int adrY = telY - lineHeight;

        int leftLimit = qrX + qrSize + espaceTexte;
        int rightLimit = logoX - espaceTexte;
        int availableWidth = rightLimit - leftLimit;
        int centerX = leftLimit + availableWidth / 2;

        // Ligne 1 : Adresse
        int x1 = centerX - fm.stringWidth(adresse) / 2;
        if (x1 < leftLimit) x1 = leftLimit;
        g2.drawString(adresse, x1, adrY);

        // Ligne 2 : Téléphone
        int x2 = centerX - fm.stringWidth(telephone) / 2;
        if (x2 < leftLimit) x2 = leftLimit;
        g2.drawString(telephone, x2, telY);

        // Ligne 3 : Email
        int x3 = centerX - fm.stringWidth(emailLine) / 2;
        if (x3 < leftLimit) x3 = leftLimit;
        g2.drawString(emailLine, x3, emailY);

        // Trait de séparation
        int maxWidth = Math.max(fm.stringWidth(adresse),
                Math.max(fm.stringWidth(telephone), fm.stringWidth(emailLine)));
        int traitX1 = centerX - maxWidth / 2;
        int traitX2 = centerX + maxWidth / 2;
        if (traitX1 < leftLimit) traitX1 = leftLimit;
        if (traitX2 > rightLimit) traitX2 = rightLimit;

        int traitY = adrY - 6;
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(0.6f));
        g2.drawLine(traitX1, traitY, traitX2, traitY);
    }

    // ============================================================
    // MÉTHODES DE CHARGEMENT (corrigées)
    // ============================================================

    private static BufferedImage generateQRCode(Institution institution, String reference, String numero, int size) {
        String sigle = (institution.getSigleInstitution() != null && !institution.getSigleInstitution().isEmpty())
                ? institution.getSigleInstitution() : institution.getNomInstitution();
        String qrText = (sigle != null ? sigle : "") + " - " +
                (reference != null ? reference : "") + " - " +
                (numero != null ? numero : "");
        if (qrText.length() > 100) qrText = qrText.substring(0, 97) + "...";
        try {
            byte[] qrBytes = QRCodeGenerator.generateQRCodeImage(qrText, size, size);
            return ImageIO.read(new ByteArrayInputStream(qrBytes));
        } catch (WriterException | IOException e) {
            BufferedImage fake = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = fake.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setColor(Color.BLACK);
            g.drawRect(0, 0, size - 1, size - 1);
            g.setFont(new Font("Arial", Font.BOLD, 8));
            String text = "QR";
            FontMetrics fm = g.getFontMetrics();
            int x = (size - fm.stringWidth(text)) / 2;
            int y = (size + fm.getAscent()) / 2;
            g.drawString(text, x, y);
            g.dispose();
            return fake;
        }
    }

    private static BufferedImage loadInstitutionLogo(Institution institution) {
        String logoPath = institution.getLogoInstitution();
        if (logoPath == null || logoPath.trim().isEmpty()) {
            logoPath = LOGO_DIR + institution.getInstitutionId() + "_logo.png";
            File f = new File(logoPath);
            if (!f.exists()) logoPath = LOGO_DIR + institution.getInstitutionId() + "_logo.jpg";
        }
        return loadImage(logoPath);
    }

    /**
     * Charge une image depuis le chemin donné.
     * Supporte les chemins relatifs (commençant par /) vers le répertoire de travail.
     */
    private static BufferedImage loadImage(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            // 1) Depuis les ressources du classpath
            InputStream is = DocumentFooter.class.getResourceAsStream(path);
            if (is != null) return ImageIO.read(is);

            // 2) Depuis le système de fichiers (chemin relatif au répertoire de travail)
            File file;
            if (path.startsWith("/")) {
                file = new File("." + path);
            } else {
                file = new File(path);
            }
            if (file.exists()) {
                System.out.println("✅ DocumentFooter: Logo chargé depuis: " + file.getAbsolutePath());
                return ImageIO.read(file);
            }

            // 3) Depuis le dossier logos (fallback)
            String fileName = new File(path).getName();
            File logoFile = new File(LOGO_DIR + fileName);
            if (logoFile.exists()) {
                System.out.println("✅ DocumentFooter: Logo chargé depuis: " + logoFile.getAbsolutePath());
                return ImageIO.read(logoFile);
            }

            System.err.println("❌ DocumentFooter: Logo non trouvé: " + path);
        } catch (IOException e) {
            System.err.println("❌ DocumentFooter: Erreur chargement image: " + path + " - " + e.getMessage());
        }
        return null;
    }
}