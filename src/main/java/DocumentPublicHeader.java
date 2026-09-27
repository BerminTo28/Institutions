import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.imageio.ImageIO;

public class DocumentPublicHeader {

    private static final String LOGO_PAYS_PATH = "/Haiti.png";
    private static final float DEFAULT_MARGIN = 50f;

    /**
     * MÉTHODE PRINCIPALE avec logos passés en paramètres
     */
    public static float draw(Graphics2D g2, Institution institution,
                             float pageWidth, float pageHeight,
                             String reference, String numero, Date dateEmission,
                             BufferedImage logoInst, BufferedImage logoPays) {

        // Filigrane (optionnel)
        drawWatermark(g2, logoInst, pageWidth, pageHeight);

        float y = DEFAULT_MARGIN;
        int logoSize = 60;

        // ========== LIGNE 1 : LOGOS (gauche + centre) ==========
        // Logo institution à gauche
        if (logoInst != null) {
            int leftX = (int) DEFAULT_MARGIN;
            g2.drawImage(logoInst, leftX, (int) y, logoSize, logoSize, null);
            System.out.println("✅ DocumentHeader: Logo institution affiché");
        } else {
            System.out.println("⚠️ DocumentHeader: Logo institution NULL");
        }

        // Logo pays à droite (centré)
        if (logoPays != null) {
            int centerX = (int) (pageWidth / 2 - logoSize / 2);

            // 1) Dessine un fond blanc sous le logo (pour les zones transparentes)
            g2.setColor(Color.WHITE);
            g2.fillRect(centerX, (int) y, logoSize, logoSize);

            // 2) Dessine le logo par‑dessus
            g2.drawImage(logoPays, centerX, (int) y, logoSize, logoSize, null);
            System.out.println("✅ DocumentHeader: Logo pays affiché");
        } else {
            System.out.println("⚠️ DocumentHeader: Logo pays NULL");
        }

        y += logoSize + 5;

        // ========== LIGNE 2 : "REPUBLIQUE D'HAITI" ==========
        g2.setFont(new Font("Times New Roman", Font.BOLD, 14));
        g2.setColor(new Color(0, 51, 102));
        String republique = "RÉPUBLIQUE D'HAÏTI";
        FontMetrics fmRep = g2.getFontMetrics();
        int xRep = (int) (pageWidth / 2 - fmRep.stringWidth(republique) / 2);
        g2.drawString(republique, xRep, y);
        y += 25;

        // ========== LIGNE 3 : NOM DE L'INSTITUTION ==========
        g2.setFont(new Font("Times New Roman", Font.BOLD, 12));
        g2.setColor(Color.BLACK);
        String nomInst = (institution.getNomInstitution() != null)
                ? institution.getNomInstitution().toUpperCase()
                : "INSTITUTION NON DÉFINIE";
        FontMetrics fm = g2.getFontMetrics();
        int xNom = (int) (pageWidth / 2 - fm.stringWidth(nomInst) / 2);
        g2.drawString(nomInst, xNom, y);
        y += 25;

        // ========== LIGNE 4 : SIGLE ==========
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 14));
        String sigle = (institution.getSigleInstitution() != null && !institution.getSigleInstitution().isEmpty())
                ? "(" + institution.getSigleInstitution().toUpperCase() + ")"
                : "";
        if (!sigle.isEmpty()) {
            fm = g2.getFontMetrics();
            int xSigle = (int) (pageWidth / 2 - fm.stringWidth(sigle) / 2);
            g2.drawString(sigle, xSigle, y);
            y += 20;
        }

        // ========== LIGNE 5 : DEVISE ==========
        g2.setFont(new Font("Times New Roman", Font.ITALIC, 12));
        String devise = (institution.getDeviseInstitution() != null) ? institution.getDeviseInstitution() : "";
        if (!devise.isEmpty()) {
            fm = g2.getFontMetrics();
            int xDevise = (int) (pageWidth / 2 - fm.stringWidth(devise) / 2);
            g2.drawString(devise, xDevise, y);
            y += 25;
        }

        // ========== LIGNE 6 : RÉFÉRENCE (gauche) et DATE (droite) ==========
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 12));
        g2.setColor(Color.BLACK);
        String refText = "Réf : " + ((reference != null && !reference.isEmpty()) ? reference : "_______________");
        String dateText = "Port-au-Prince, le " + formatDate(dateEmission);

        g2.drawString(refText, DEFAULT_MARGIN, y);

        fm = g2.getFontMetrics();
        int xDate = (int) (pageWidth - DEFAULT_MARGIN - fm.stringWidth(dateText));
        g2.drawString(dateText, xDate, y);
        y += 20;

        // ========== LIGNE 7 : NUMÉRO (gauche) ==========
        String numText = "No : " + ((numero != null && !numero.isEmpty()) ? numero : "_______________");
        g2.drawString(numText, DEFAULT_MARGIN, y);
        y += 25;
        y += 15;

        return y;
    }

    /**
     * MÉTHODE DE COMPATIBILITÉ (sans logos en paramètres)
     * Charge les logos automatiquement
     */
    public static float draw(Graphics2D g2, Institution institution,
                             float pageWidth, float pageHeight,
                             String reference, String numero, Date dateEmission) {

        BufferedImage logoInst = loadImage(institution.getLogoInstitution());
        BufferedImage logoPays = loadImage(LOGO_PAYS_PATH);

        return draw(g2, institution, pageWidth, pageHeight,
                    reference, numero, dateEmission, logoInst, logoPays);
    }

    /**
     * Dessine le filigrane avec le logo passé en paramètre
     */
    private static void drawWatermark(Graphics2D g2, BufferedImage logo,
                                      float pageWidth, float pageHeight) {
        if (logo == null) return;

        Composite originalComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        int size = 100;
        int x = (int) ((pageWidth - size) / 2);
        int y = (int) ((pageHeight - size) / 2);
        g2.drawImage(logo, x, y, size, size, null);

        g2.setComposite(originalComposite);
    }

    /**
     * Charge une image dynamiquement via LogoResolver (ou depuis le classpath pour les ressources internes).
     */
    private static BufferedImage loadImage(String path) {
        if (path == null || path.trim().isEmpty()) {
            System.err.println("⚠️ DocumentHeader: Chemin d'image NULL ou vide");
            return null;
        }

        System.out.println("🔍 DocumentHeader: Tentative de chargement dynamique: " + path);

        try {
            // 1) Résolution dynamique via le composant centralisé LogoResolver
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

            // 2) Fallback : Ressource du classpath (pour les éléments internes comme /Haiti.png)
            String classpathResourcePath = path.startsWith("/") ? path : "/" + path;
            InputStream is = DocumentPublicHeader.class.getResourceAsStream(classpathResourcePath);
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                System.out.println("✅ DocumentHeader: Logo chargé depuis les ressources classpath: " + classpathResourcePath);
                return img;
            }

            System.err.println("❌ DocumentHeader: Logo non trouvé dynamiquement: " + path);

        } catch (IOException e) {
            System.err.println("❌ DocumentHeader: Erreur chargement image: " + path + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Formate une date en "dd MMMM yyyy" (français)
     */
    private static String formatDate(Date date) {
        if (date == null) date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH);
        return sdf.format(date);
    }
}