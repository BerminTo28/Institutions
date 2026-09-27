import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ImageConverterUtil {

    public static String convertirVersPng(File sourceFile, File outputDir) {
        try {
            BufferedImage sourceImage = ImageIO.read(sourceFile);
            
            if (sourceImage == null) {
                System.err.println("❌ Impossible de lire l'image source pour la conversion : " + sourceFile.getName());
                return null;
            }

            // 2. Générer un nom propre avec l'extension .png
            String baseName = sourceFile.getName();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            // Nettoyer d'éventuels paramètres indésirables
            baseName = baseName.replaceAll("[^a-zA-Z0-9_]", "_");
            String nouveauNomFichier = baseName + ".png";

            File outputFile = new File(outputDir, nouveauNomFichier);

            // 3. Créer une image de rendu (gère la transparence si le PNG/WebP d'origine en a)
            BufferedImage pngImage = new BufferedImage(
                sourceImage.getWidth(), 
                sourceImage.getHeight(), 
                BufferedImage.TYPE_INT_ARGB
            );
            
            Graphics2D g2d = pngImage.createGraphics();
            g2d.drawImage(sourceImage, 0, 0, null);
            g2d.dispose();

            // 4. Écrire le fichier final au format PNG
            boolean success = ImageIO.write(pngImage, "png", outputFile);
            
            if (success) {
                System.out.println("✅ Image convertie et enregistrée en PNG : " + outputFile.getAbsolutePath());
                return nouveauNomFichier;
            }

        } catch (IOException e) {
            System.err.println("❌ Erreur lors de la conversion de l'image : " + e.getMessage());
        }
        
        return null;
    }
}