public class AccesProfesseur {
    private String numeroIdentifiant;
    private String source;
    private String institutionId;
    private boolean profil;
    private boolean cours;
    private boolean notes;
    private boolean edt;
    private boolean absences;
    private boolean documents;
    private boolean messages;
    private boolean parametres;
    private boolean programmes;
    private boolean activites; // ✅ AJOUTÉ

    // Getters et Setters
    public String getSource(){return source;}
    public void setSource(String source){this.source=source;}
    public String getNumeroIdentifiant() { return numeroIdentifiant; }
    public void setNumeroIdentifiant(String numeroIdentifiant) { this.numeroIdentifiant = numeroIdentifiant; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public boolean isProfil() { return profil; }
    public void setProfil(boolean profil) { this.profil = profil; }
    public boolean isCours() { return cours; }
    public void setCours(boolean cours) { this.cours = cours; }
    public boolean isNotes() { return notes; }
    public void setNotes(boolean notes) { this.notes = notes; }
    public boolean isEdt() { return edt; }
    public void setEdt(boolean edt) { this.edt = edt; }
    public boolean isAbsences() { return absences; }
    public void setAbsences(boolean absences) { this.absences = absences; }
    public boolean isDocuments() { return documents; }
    public void setDocuments(boolean documents) { this.documents = documents; }
    public boolean isMessages() { return messages; }
    public void setMessages(boolean messages) { this.messages = messages; }
    public boolean isParametres() { return parametres; }
    public void setParametres(boolean parametres) { this.parametres = parametres; }
    public boolean isProgrammes() { return programmes; } // ✅ AJOUTÉ
    public void setProgrammes(boolean programmes) { this.programmes = programmes; } 
    public boolean isActivites() {return activites;
    }
    public void setActivites(boolean activites){this.activites=activites;}// ✅ AJOUTÉ
}