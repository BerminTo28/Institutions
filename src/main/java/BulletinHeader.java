import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * En‑tête de bulletin – choisit l’en‑tête (public ou privé) selon la catégorie
 * de l’institution, et transmet les logos.
 */
public class BulletinHeader {

    private static final Logger LOGGER = Logger.getLogger(BulletinHeader.class.getName());
    private static final float PAGE_WIDTH = 595f;
    private static final String LOGO_DIR = System.getProperty("user.dir") + "/logos/";
    private static final String LOGO_PAYS_PATH = "/Haiti.png";

    /**
     * Méthode principale – dessine l’en‑tête avec les logos fournis.
     */
    public static float draw(Graphics2D g2,
                             Institution institution,
                             String reference,
                             String numero,
                             String nomEtudiant,
                             String prenomEtudiant,
                             String anneeAcademique,
                             String classe,
                             BufferedImage logoInstParam,
                             BufferedImage logoPaysParam,
                             Date dateEmissionParam,
                             boolean afficherBlocEtudiant) {

        final Date dateEmission = (dateEmissionParam == null) ? new Date() : dateEmissionParam;

        // Résolution des logos dans des variables effectivement finales pour les lambdas
        BufferedImage resolvedLogoInst = logoInstParam;
        if (resolvedLogoInst == null && institution != null) {
            resolvedLogoInst = loadImage(institution.getLogoInstitution());
        }
        
        BufferedImage resolvedLogoPays = logoPaysParam;
        if (resolvedLogoPays == null) {
            resolvedLogoPays = loadImage(LOGO_PAYS_PATH);
        }

        final BufferedImage logoInstFinal = resolvedLogoInst;
        final BufferedImage logoPaysFinal = resolvedLogoPays;

        float pageHeight = 842f;
        float yAfterHeader;

        // Déterminer le type d’en‑tête selon la catégorie
        String entite = institution != null ? institution.getCategorieInstitution() : "";
        boolean isPublic = (entite != null && entite.trim().equalsIgnoreCase("Publique"));

        LOGGER.info(() -> "🔍 BulletinHeader – Catégorie: '" + entite + "', public? " + isPublic);
        LOGGER.info(() -> "📌 Logo institution: " + (logoInstFinal != null ? "présent" : "null"));
        LOGGER.info(() -> "📌 Logo pays: " + (logoPaysFinal != null ? "présent" : "null"));

        // Appel de l’en‑tête adéquat avec les logos
        if (isPublic) {
            yAfterHeader = DocumentPublicHeader.draw(g2, institution, PAGE_WIDTH, pageHeight,
                                                     reference, numero, dateEmission,
                                                     logoInstFinal, logoPaysFinal);
        } else {
            yAfterHeader = DocumentPrivateHeader.draw(g2, institution, PAGE_WIDTH, pageHeight,
                                                      dateEmission, logoInstFinal, logoPaysFinal);
        }

        // Ajout du bloc étudiant si demandé
        if (afficherBlocEtudiant) {
            yAfterHeader = drawBlocEtudiant(g2, nomEtudiant, prenomEtudiant,
                                            anneeAcademique, classe, yAfterHeader);
        }

        return yAfterHeader;
    }

    /**
     * Surcharge avec date par défaut (date courante).
     */
    public static float draw(Graphics2D g2,
                             Institution institution,
                             String reference,
                             String numero,
                             String nomEtudiant,
                             String prenomEtudiant,
                             String anneeAcademique,
                             String classe,
                             BufferedImage logoInst,
                             BufferedImage logoPays,
                             boolean afficherBlocEtudiant) {
        return draw(g2, institution, reference, numero,
                    nomEtudiant, prenomEtudiant, anneeAcademique, classe,
                    logoInst, logoPays, new Date(), afficherBlocEtudiant);
    }

    /**
     * Surcharge sans passage explicite des logos (ils seront chargés automatiquement).
     */
    public static float draw(Graphics2D g2,
                             Institution institution,
                             String reference,
                             String numero,
                             String nomEtudiant,
                             String prenomEtudiant,
                             String anneeAcademique,
                             String classe,
                             boolean afficherBlocEtudiant) {
        return draw(g2, institution, reference, numero,
                    nomEtudiant, prenomEtudiant, anneeAcademique, classe,
                    null, null, new Date(), afficherBlocEtudiant);
    }

    /**
     * Surcharge avec date uniquement, sans passage explicite des logos.
     */
    public static float draw(Graphics2D g2,
                             Institution institution,
                             String reference,
                             String numero,
                             String nomEtudiant,
                             String prenomEtudiant,
                             String anneeAcademique,
                             String classe,
                             Date dateEmission,
                             boolean afficherBlocEtudiant) {
        return draw(g2, institution, reference, numero,
                    nomEtudiant, prenomEtudiant, anneeAcademique, classe,
                    null, null, dateEmission, afficherBlocEtudiant);
    }

    /**
     * Méthode utilitaire pour charger les images proprement.
     */
    private static BufferedImage loadImage(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        try {
            InputStream is = BulletinHeader.class.getResourceAsStream(path);
            if (is != null) {
                return ImageIO.read(is);
            }

            File file;
            if (path.startsWith("/")) {
                file = new File("." + path);
            } else {
                file = new File(path);
            }
            if (file.exists()) {
                return ImageIO.read(file);
            }

            String fileName = new File(path).getName();
            File logoFile = new File(LOGO_DIR + fileName);
            if (logoFile.exists()) {
                return ImageIO.read(logoFile);
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "Erreur de chargement d'image pour le chemin : " + path);
        }
        return null;
    }

    /**
     * Dessine le bloc d’informations de l’étudiant (nom, année, classe).
     */
    private static float drawBlocEtudiant(Graphics2D g2,
                                          String nomEtudiant,
                                          String prenomEtudiant,
                                          String anneeAcademique,
                                          String classe,
                                          float yStart) {
        float y = yStart + 15;

        // Nom complet centré
        g2.setFont(new Font("Times New Roman", Font.BOLD, 13));
        String nomComplet = (prenomEtudiant != null ? prenomEtudiant : "")
                + " " + (nomEtudiant != null ? nomEtudiant : "");
        if (!nomComplet.trim().isEmpty()) {
            String titre = "BULLETIN DE L'ETUDIANT : " + nomComplet.toUpperCase();
            drawCenteredString(g2, titre, PAGE_WIDTH / 2, y);
            y += 20;
        }

        // Année académique
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 12));
        if (anneeAcademique != null && !anneeAcademique.trim().isEmpty()) {
            String anneeStr = "Pour l'année académique : " + anneeAcademique;
            drawCenteredString(g2, anneeStr, PAGE_WIDTH / 2, y);
            y += 20;
        }

        // Classe
        if (classe != null && !classe.trim().isEmpty()) {
            g2.setFont(new Font("Times New Roman", Font.PLAIN, 11));
            String classeStr = "Classe : " + classe;
            drawCenteredString(g2, classeStr, PAGE_WIDTH / 2, y);
            y += 20;
        }

        return y;
    }

    /**
     * Dessine une chaîne centrée sur la largeur de la page.
     */
    private static void drawCenteredString(Graphics2D g2, String text, float centerX, float y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontMetrics fm = g2.getFontMetrics();
        int x = (int) (centerX - fm.stringWidth(text) / 2f);
        g2.drawString(text, x, (int) y);
    }
}