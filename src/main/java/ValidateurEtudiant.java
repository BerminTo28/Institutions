import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validateur métier pour les étudiants.
 * Accumule TOUTES les erreurs avant de lancer une exception.
 * Utilise Constantes comme source unique de vérité.
 */
public final class ValidateurEtudiant {

    private static final Pattern PATTERN_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern PATTERN_TEL =
            Pattern.compile("^\\+?\\d[\\d\\s-]{6,20}$");

    private static final Pattern PATTERN_NINU_CHIFFRES =
            Pattern.compile("^1\\d{9}$");

    private static final Pattern PATTERN_NINU_FORMATE =
            Pattern.compile("^1\\d{2}-\\d{3}-\\d{3}-\\d$");

    private final Map<String, String> erreurs = new LinkedHashMap<>();

    // =========================================================
    // RÈGLES GÉNÉRIQUES
    // =========================================================

    public ValidateurEtudiant requireText(String label, String value) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
        }
        return this;
    }

    public ValidateurEtudiant requireTextMin(String label, String value, int min) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
        } else if (value.trim().length() < min) {
            erreurs.put(label, "Doit contenir au moins " + min + " caractères");
        }
        return this;
    }

    public ValidateurEtudiant requireDatePassee(String label, String value) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        try {
            LocalDate d = LocalDate.parse(value.trim());
            if (d.isAfter(LocalDate.now())) {
                erreurs.put(label, "La date doit être dans le passé");
            }
        } catch (DateTimeParseException e) {
            erreurs.put(label, "Format de date invalide (attendu : yyyy-MM-dd)");
        }
        return this;
    }

    public ValidateurEtudiant requirePattern(String label, String value, Pattern p, String format) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        if (!p.matcher(value.trim()).matches()) {
            erreurs.put(label, "Format invalide (attendu : " + format + ")");
        }
        return this;
    }

    public ValidateurEtudiant requireNinu(String label, String value) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        String v = value.trim();
        boolean ok = PATTERN_NINU_CHIFFRES.matcher(v).matches()
                  || PATTERN_NINU_FORMATE.matcher(v).matches();
        if (!ok) {
            erreurs.put(label, "NINU invalide : 10 chiffres commençant par 1 (ex : 123-456-789-0)");
        }
        return this;
    }

    public ValidateurEtudiant requireTelephone(String label, String value) {
        return requirePattern(label, value, PATTERN_TEL, "ex : 32-45-67-89 ou +50932456789");
    }

    public ValidateurEtudiant requireEmail(String label, String value) {
        return requirePattern(label, value, PATTERN_EMAIL, "ex : nom@domaine.com");
    }

    public ValidateurEtudiant requireInList(String label, String value, Collection<String> allowed) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        boolean ok = allowed.stream().anyMatch(a -> a.equalsIgnoreCase(value.trim()));
        if (!ok) {
            erreurs.put(label, "Valeur non autorisée. Autorisées : " + String.join(", ", allowed));
        }
        return this;
    }

    public ValidateurEtudiant requireTrue(String label, boolean condition, String message) {
        if (!condition) erreurs.put(label, message);
        return this;
    }

    // =========================================================
    // RÈGLES SPÉCIFIQUES AUX RÉFÉRENTIELS HAÏTIENS
    // =========================================================

    /** Valide un sexe contre Constantes.SEXES. */
    public ValidateurEtudiant requireSexe(String label, String value) {
        return requireInList(label, value, java.util.Arrays.asList(Constantes.SEXES));
    }

    /** Valide un groupe sanguin contre Constantes.GROUPES_SANGUINS. */
    public ValidateurEtudiant requireGroupeSanguin(String label, String value) {
        return requireInList(label, value, java.util.Arrays.asList(Constantes.GROUPES_SANGUINS));
    }

    /** Valide un département contre Constantes.DEPARTEMENTS. */
    public ValidateurEtudiant requireDepartement(String label, String value) {
        if (value == null || value.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        if (!Constantes.estDepartementValide(value)) {
            erreurs.put(label, "Département inconnu : " + value);
        }
        return this;
    }

    /** Valide une commune contre Constantes.getCommunes(departement). */
    public ValidateurEtudiant requireCommune(String label, String commune, String departement) {
        if (commune == null || commune.isBlank()) {
            erreurs.put(label, "Champ obligatoire manquant");
            return this;
        }
        if (departement == null || departement.isBlank()) {
            // Ne pas doubler l'erreur : le département est déjà signalé
            return this;
        }
        if (!Constantes.estCommuneValide(commune, departement)) {
            erreurs.put(label, "Commune inconnue pour le département " + departement);
        }
        return this;
    }

    // =========================================================
    // RÉSULTAT
    // =========================================================

    public boolean hasErrors() { return !erreurs.isEmpty(); }

    public Map<String, String> erreurs() { return erreurs; }

    public String message() {
        StringBuilder sb = new StringBuilder("Champs invalides : ");
        erreurs.forEach((k, v) -> sb.append("[").append(k).append(" → ").append(v).append("] "));
        return sb.toString().trim();
    }

    public void throwIfInvalid() {
        if (hasErrors()) {
            throw new IllegalArgumentException(message());
        }
    }
}