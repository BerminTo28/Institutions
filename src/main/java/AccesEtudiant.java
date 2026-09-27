public class AccesEtudiant {
    private String source;
    private String numeroIdentifiant;
    private String institutionId;
    private boolean connexion;
    private boolean profil;
    private boolean notes;
    private boolean bulletin;
    private boolean paiement;
    private boolean messages;
    private boolean documents;
    private boolean edt;
    private boolean attestations;
    private boolean absences;
    private boolean assistance;
    private boolean activites;
    private boolean statistiques;
    // Getters et setters (générés automatiquement)
    public String getSource(){ return source;}
    public void setSource(String source){this.source=source;}
    public String getNumeroIdentifiant() { return numeroIdentifiant; }
    public void setNumeroIdentifiant(String numeroIdentifiant) { this.numeroIdentifiant = numeroIdentifiant; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public boolean isConnexion() { return connexion; }
    public void setConnexion(boolean connexion) { this.connexion = connexion; }

    public boolean isProfil() { return profil; }
    public void setProfil(boolean profil) { this.profil = profil; }

    public boolean isNotes() { return notes; }
    public void setNotes(boolean notes) { this.notes = notes; }

    public boolean isBulletin() { return bulletin; }
    public void setBulletin(boolean bulletin) { this.bulletin = bulletin; }

    public boolean isPaiement() { return paiement; }
    public void setPaiement(boolean paiement) { this.paiement = paiement; }

    public boolean isMessages() { return messages; }
    public void setMessages(boolean messages) { this.messages = messages; }

    public boolean isDocuments() { return documents; }
    public void setDocuments(boolean documents) { this.documents = documents; }

    public boolean isEdt() { return edt; }
    public void setEdt(boolean edt) { this.edt = edt; }

    public boolean isAttestations() { return attestations; }
    public void setAttestations(boolean attestations) { this.attestations = attestations; }

    public boolean isAbsences() { return absences; }
    public void setAbsences(boolean absences) { this.absences = absences; }

    public boolean isAssistance() { return assistance; }
    public void setAssistance(boolean assistance) { this.assistance = assistance; }
    public boolean isActivites() { return activites; }
    public void setActivites(boolean activites) { this.activites = activites; }
    public boolean isStatistiques() { return statistiques; }
    public void setStatistiques(boolean statistiques) { this.statistiques = statistiques; }
}