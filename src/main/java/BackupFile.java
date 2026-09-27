import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupFile {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File file;
    private final String nom;
    private final long taille;
    private final LocalDateTime dateCreation;

    public BackupFile(File file) {
        this.file = file;
        this.nom = file.getName();
        this.taille = file.length();
        this.dateCreation = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.lastModified()),
                java.time.ZoneId.systemDefault());
    }

    public File getFile() { return file; }
    public String getNom() { return nom; }
    public long getTaille() { return taille; }
    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getTailleFormatee() {
        if (taille < 1024) return taille + " B";
        if (taille < 1024 * 1024) return String.format("%.1f KB", taille / 1024.0);
        if (taille < 1024L * 1024 * 1024) return String.format("%.1f MB", taille / (1024.0 * 1024));
        return String.format("%.1f GB", taille / (1024.0 * 1024 * 1024));
    }

    public String getDateFormatee() {
        return dateCreation.format(FORMAT);
    }
}