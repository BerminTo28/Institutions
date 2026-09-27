public class AuthResult {
    private boolean success;
    private String message;
    private String role;
    private String userId;
    private String redirect;
    private String displayName;
    private String email;
    private String institutionId;
    
    public AuthResult() {
        this.success = false;
        this.message = "";
        this.role = "";
        this.userId = "";
        this.redirect = "/";
        this.displayName = "";
        this.email = "";
        this.institutionId = null;
    }
    
    // Constructeur avec tous les paramètres
    public AuthResult(boolean success, String role, String userId, String institutionId, String message) {
        this.success = success;
        this.role = role;
        this.userId = userId;
        this.institutionId = institutionId;
        this.message = message;
        this.redirect = "/";
        this.displayName = "";
        this.email = "";
    }
    
    // Getters et Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getRedirect() { return redirect; }
    public void setRedirect(String redirect) { this.redirect = redirect; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
}