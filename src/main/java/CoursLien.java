import java.time.LocalDateTime;
import java.util.UUID;

public class CoursLien {
    private String id;
    private String titre;
    private String description;
    private String url;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String professeurId;
    private String institutionId;

    public CoursLien() {
        this.id = "LIEN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public CoursLien(String titre, String url, LocalDateTime dateDebut, LocalDateTime dateFin) {
        this();
        this.titre = titre;
        this.url = url;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public String getProfesseurId() { return professeurId; }
    public void setProfesseurId(String professeurId) { this.professeurId = professeurId; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
}