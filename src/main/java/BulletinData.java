import java.util.List;

/**
 * Conteneur de données pour un bulletin.
 * Les moyennes et décisions sont calculées par {@link BulletinService} et {@link BulletinGenerator}.
 */
public class BulletinData {
    private Etudiant etudiant;
    private Institution institution;
    private String annee;
    private String session;
    private List<BulletinReleveMatiere> releves;
    private double moyenneGenerale;      // moyenne annuelle (utilisée pour la décision)
    private double moyenneSession;       // moyenne de la session courante
    private int rang;
    private String decision;
    private String decisionColorHex;
    private String reference;
    private String numero;

    // ==================== CONSTRUCTEURS ====================
    
    public BulletinData() {}
    
    public BulletinData(Etudiant etudiant, String annee, String session, List<BulletinReleveMatiere> releves, 
                        double moyenneGenerale, double moyenneSession) {
        this.etudiant = etudiant;
        this.annee = annee;
        this.session = session;
        this.releves = releves;
        this.moyenneGenerale = moyenneGenerale;
        this.moyenneSession = moyenneSession;
        this.decision = moyenneGenerale >= 50 ? "ADMIS" : "ÉCHEC";
        this.decisionColorHex = moyenneGenerale >= 50 ? "#27ae60" : "#c0392b";
    }
    
    // ==================== GETTERS & SETTERS ====================

    public Etudiant getEtudiant() {
        return etudiant;
    }

    public void setEtudiant(Etudiant etudiant) {
        this.etudiant = etudiant;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public String getAnnee() {
        return annee;
    }

    public void setAnnee(String annee) {
        this.annee = annee;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public List<BulletinReleveMatiere> getReleves() {
        return releves;
    }

    public void setReleves(List<BulletinReleveMatiere> releves) {
        this.releves = releves;
    }

    public double getMoyenneGenerale() {
        return moyenneGenerale;
    }

    public void setMoyenneGenerale(double moyenneGenerale) {
        this.moyenneGenerale = moyenneGenerale;
        this.decision = moyenneGenerale >= 50 ? "ADMIS" : "ÉCHEC";
        this.decisionColorHex = moyenneGenerale >= 50 ? "#27ae60" : "#c0392b";
    }

    public double getMoyenneSession() {
        return moyenneSession;
    }

    public void setMoyenneSession(double moyenneSession) {
        this.moyenneSession = moyenneSession;
    }

    public int getRang() {
        return rang;
    }

    public void setRang(int rang) {
        this.rang = rang;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionColorHex() {
        return decisionColorHex;
    }

    public void setDecisionColorHex(String decisionColorHex) {
        this.decisionColorHex = decisionColorHex;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }
    
    // ==================== MÉTHODES UTILITAIRES ====================
    
    public String getMoyenneFormatee() {
        return String.format("%.2f", moyenneGenerale);
    }
    
    public String getMoyenneSessionFormatee() {
        return String.format("%.2f", moyenneSession);
    }
    
    public String getDecisionAvecEmoji() {
        return moyenneGenerale >= 50 ? "✅ " + decision : "❌ " + decision;
    }
    
    public String getClasse() {
        return etudiant != null ? etudiant.getClasse() : "";
    }
    
    public String getEtudiantNomComplet() {
        return etudiant != null ? etudiant.getNom() + " " + etudiant.getPrenom() : "";
    }
    
    // ==================== BUILDER PATTERN ====================
    
    public static class Builder {
        private Etudiant etudiant;
        private Institution institution;
        private String annee;
        private String session;
        private List<BulletinReleveMatiere> releves;
        private double moyenneGenerale;
        private double moyenneSession;
        private int rang;
        private String reference;
        private String numero;
        
        public Builder withEtudiant(Etudiant etudiant) {
            this.etudiant = etudiant;
            return this;
        }
        
        public Builder withInstitution(Institution institution) {
            this.institution = institution;
            return this;
        }
        
        public Builder withAnnee(String annee) {
            this.annee = annee;
            return this;
        }
        
        public Builder withSession(String session) {
            this.session = session;
            return this;
        }
        
        public Builder withReleves(List<BulletinReleveMatiere> releves) {
            this.releves = releves;
            return this;
        }
        
        public Builder withMoyenneGenerale(double moyenneGenerale) {
            this.moyenneGenerale = moyenneGenerale;
            return this;
        }
        
        public Builder withMoyenneSession(double moyenneSession) {
            this.moyenneSession = moyenneSession;
            return this;
        }
        
        public Builder withRang(int rang) {
            this.rang = rang;
            return this;
        }
        
        public Builder withReference(String reference) {
            this.reference = reference;
            return this;
        }
        
        public Builder withNumero(String numero) {
            this.numero = numero;
            return this;
        }
        
        public BulletinData build() {
            BulletinData data = new BulletinData();
            data.setEtudiant(etudiant);
            data.setInstitution(institution);
            data.setAnnee(annee);
            data.setSession(session);
            data.setReleves(releves);
            data.setMoyenneGenerale(moyenneGenerale);
            data.setMoyenneSession(moyenneSession);
            data.setRang(rang);
            data.setReference(reference);
            data.setNumero(numero);
            return data;
        }
    }
}