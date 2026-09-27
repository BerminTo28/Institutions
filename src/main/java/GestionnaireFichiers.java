import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class GestionnaireFichiers {

    private static final String DOSSIER_PHOTOS = "data/photos/";
    private static final String DOSSIER_EMPREINTES = "data/empreintes/";
    private static final String DOSSIER_VISAGES = "data/visages/";
    private static final String DOSSIER_QR = "data/qr_codes/";
    private static final String DOSSIER_BULLETINS = "data/bulletins/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 Mo

    static {
        // Créer les dossiers au démarrage
        creerDossiers();
    }

    /**
     * Crée tous les dossiers nécessaires
     */
    private static void creerDossiers() {
        String[] dossiers = {
            DOSSIER_PHOTOS, DOSSIER_EMPREINTES, DOSSIER_VISAGES,
            DOSSIER_QR, DOSSIER_BULLETINS
        };
        for (String dossier : dossiers) {
            File dir = new File(dossier);
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println("✅ Dossier créé: " + dossier);
            }
        }
    }

    /**
     * Sauvegarde une photo depuis un fichier uploadé
     */
    public static String sauvegarderPhoto(byte[] data, String nom, String prenom, String institutionId) {
        if (data == null || data.length == 0) {
            return null;
        }
        if (data.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Le fichier est trop volumineux (max " + MAX_FILE_SIZE + " octets)");
        }
        
        String extension = detecterExtension(data);
        String nomFichier = genererNomFichier(nom, prenom, "PHOTO", extension);
        String chemin = DOSSIER_PHOTOS + nomFichier;
        
        return sauvegarderFichier(data, chemin);
    }

    /**
     * Sauvegarde une empreinte
     */
    public static String sauvegarderEmpreinte(byte[] data, String etudiantId) {
        if (data == null || data.length == 0) {
            return null;
        }
        String nomFichier = etudiantId + "_EMP_" + System.currentTimeMillis() + ".bin";
        String chemin = DOSSIER_EMPREINTES + nomFichier;
        return sauvegarderFichier(data, chemin);
    }

    /**
     * Sauvegarde une image de visage
     */
    public static String sauvegarderVisage(byte[] data, String etudiantId) {
        if (data == null || data.length == 0) {
            return null;
        }
        String nomFichier = etudiantId + "_VIS_" + System.currentTimeMillis() + ".jpg";
        String chemin = DOSSIER_VISAGES + nomFichier;
        return sauvegarderFichier(data, chemin);
    }

    /**
     * Sauvegarde un QR code
     */
    public static String sauvegarderQR(byte[] data, String etudiantId) {
        if (data == null || data.length == 0) {
            return null;
        }
        String nomFichier = etudiantId + "_QR_" + System.currentTimeMillis() + ".png";
        String chemin = DOSSIER_QR + nomFichier;
        return sauvegarderFichier(data, chemin);
    }

    /**
     * Sauvegarde un bulletin PDF
     */
    public static String sauvegarderBulletin(byte[] data, String etudiantId, String periode) {
        if (data == null || data.length == 0) {
            return null;
        }
        String nomFichier = etudiantId + "_BUL_" + periode + "_" + System.currentTimeMillis() + ".pdf";
        String chemin = DOSSIER_BULLETINS + nomFichier;
        return sauvegarderFichier(data, chemin);
    }

    /**
     * Sauvegarde générique d'un fichier
     */
    private static String sauvegarderFichier(byte[] data, String chemin) {
        try {
            File fichier = new File(chemin);
            // Créer les dossiers parents si nécessaire
            fichier.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(fichier)) {
                fos.write(data);
            }
            System.out.println("✅ Fichier sauvegardé: " + chemin);
            return chemin;
        } catch (IOException e) {
            System.err.println("❌ Erreur sauvegarde fichier: " + e.getMessage());
            return null;
        }
    }

    /**
     * Génère un nom de fichier unique
     */
    private static String genererNomFichier(String nom, String prenom, String type, String extension) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String date = sdf.format(new Date());
        String nomClean = (nom != null ? nom.substring(0, Math.min(3, nom.length())) : "XXX").toUpperCase();
        String prenomClean = (prenom != null ? prenom.substring(0, Math.min(3, prenom.length())) : "XXX").toUpperCase();
        String uuid = UUID.randomUUID().toString().substring(0, 6);
        return nomClean + "_" + prenomClean + "_" + type + "_" + date + "_" + uuid + extension;
    }

    /**
     * Détecte l'extension d'un fichier à partir de ses données
     */
    private static String detecterExtension(byte[] data) {
        if (data.length < 4) return ".bin";
        
        // JPEG
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) return ".jpg";
        // PNG
        if (data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47) return ".png";
        // GIF
        if (data[0] == (byte)0x47 && data[1] == (byte)0x49 && data[2] == (byte)0x46) return ".gif";
        // PDF
        if (data[0] == (byte)0x25 && data[1] == (byte)0x50 && data[2] == (byte)0x44 && data[3] == (byte)0x46) return ".pdf";
        // WEBP
        if (data.length > 12 && data[0] == (byte)0x52 && data[1] == (byte)0x49 && 
            data[2] == (byte)0x46 && data[3] == (byte)0x46 && 
            data[8] == (byte)0x57 && data[9] == (byte)0x45 && 
            data[10] == (byte)0x42 && data[11] == (byte)0x50) return ".webp";
        
        return ".bin";
    }

    /**
     * Supprime un fichier
     */
    public static boolean supprimerFichier(String chemin) {
        if (chemin == null || chemin.isEmpty()) return true;
        try {
            File fichier = new File(chemin);
            if (fichier.exists()) {
                return fichier.delete();
            }
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erreur suppression fichier: " + e.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si un fichier existe
     */
    public static boolean fichierExiste(String chemin) {
        if (chemin == null || chemin.isEmpty()) return false;
        File fichier = new File(chemin);
        return fichier.exists() && fichier.isFile();
    }

    /**
     * Récupère un fichier en bytes
     */
    public static byte[] lireFichier(String chemin) throws IOException {
        if (chemin == null || chemin.isEmpty()) return null;
        File fichier = new File(chemin);
        if (!fichier.exists()) return null;
        return Files.readAllBytes(fichier.toPath());
    }
}