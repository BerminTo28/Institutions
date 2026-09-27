import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Façade métier au-dessus de {@link NoteData}.
 *
 * RÈGLES :
 *  - Privilégier {@link #trouverNoteParContexte} (6 critères) pour toute
 *    recherche ou vérification de doublon.
 *  - Privilégier {@link #supprimerNoteAvecContexte} (6 critères) pour toute
 *    suppression ciblée.
 *  - Les anciennes méthodes (période seule) sont conservées pour compatibilité
 *    mais marquées comme dépréciées.
 */
public class NoteManager {

    private static final Logger LOGGER = Logger.getLogger(NoteManager.class.getName());

    /** Valeur par défaut pour verifiedBy si non fourni. */
    private static final String VERIFIED_BY_DEFAULT = "ADMIN_SYSTEM";

    private final NoteData noteDAO;

    public NoteManager(NoteData noteDAO) {
        if (noteDAO == null) {
            throw new IllegalArgumentException("NoteData ne peut pas être nul.");
        }
        this.noteDAO = noteDAO;
    }

    // ============================================================
    // AJOUT
    // ============================================================
    public boolean ajouterNote(Note note) throws SQLException {
        if (note == null) {
            LOGGER.warning("ajouterNote() : note nulle");
            return false;
        }
        return noteDAO.create(note);
    }

    // ============================================================
    // RECHERCHE
    // ============================================================

    /**
     * Recherche une note par matière + étudiant + institution.
     *
     * ⚠️ Ne filtre NI par période, NI par année, NI par classe.
     *    Peut retourner une note d'une autre période si plusieurs existent.
     *    Utiliser {@link #trouverNoteParContexte} à la place.
     */
    public Note trouverNote(String codeCours, String numeroEtudiant, String institutionId) {
        if (estVide(codeCours) || estVide(numeroEtudiant) || estVide(institutionId)) {
            return null;
        }
        return noteDAO.read(codeCours, numeroEtudiant, institutionId);
    }

    /**
     * Recherche une note par matière + étudiant + institution + période.
     *
     * ⚠️ Ne filtre NI par année, NI par classe.
     *    Peut retourner une note d'une autre année/classe si plusieurs existent.
     *    Utiliser {@link #trouverNoteParContexte} à la place.
     *
     * @deprecated Remplacé par {@link #trouverNoteParContexte}.
     */
    @Deprecated
    public Note trouverNoteAvecPeriode(String codeCours, String numeroEtudiant,
                                         String institutionId, String periode) {
        if (estVide(codeCours) || estVide(numeroEtudiant)
                || estVide(institutionId) || estVide(periode)) {
            return null;
        }
        LOGGER.fine(() -> "trouverNoteAvecPeriode (déprécié) — préférez trouverNoteParContexte");
        return noteDAO.readWithPeriode(codeCours, numeroEtudiant, institutionId, periode);
    }

    /**
     * ✅ RECHERCHE COMPLÈTE (recommandée).
     *
     * Filtre par les 6 critères :
     *   institution + code_cours + numero_etudiant + periode + annee + classe.
     *
     * Empêche toute confusion entre périodes / années / classes.
     */
    public Note trouverNoteParContexte(String codeCours, String numeroEtudiant,
                                         String institutionId, String periode,
                                         String annee, String classe) {
        if (estVide(codeCours) || estVide(numeroEtudiant) || estVide(institutionId)
                || estVide(periode) || estVide(annee) || estVide(classe)) {
            LOGGER.warning("trouverNoteParContexte() : paramètres null ou vides");
            return null;
        }
        return noteDAO.trouverNoteParContexte(
                codeCours, numeroEtudiant, institutionId, periode, annee, classe);
    }

    // ============================================================
    // LISTES
    // ============================================================
    public List<Note> listerToutesNotes(String institutionId) {
        if (estVide(institutionId)) {
            return Collections.emptyList();
        }
        return noteDAO.readAll(institutionId);
    }

    public List<Note> listerNotesParPeriode(String institutionId, String periode) {
        if (estVide(institutionId) || estVide(periode)) {
            return Collections.emptyList();
        }
        return noteDAO.readAllWithPeriode(institutionId, periode);
    }

    /**
     * ✅ Lance SQLException (comme NoteData.getNotesByEtudiant).
     */
    public List<Note> listerNotesParEtudiant(String numeroEtudiant, String institutionId,
                                               String annee, String periode)
            throws SQLException {
        if (estVide(numeroEtudiant) || estVide(institutionId)) {
            return Collections.emptyList();
        }
        return noteDAO.getNotesByEtudiant(numeroEtudiant, institutionId, annee, periode);
    }

    /**
     * ✅ Lance SQLException.
     */
    public List<Note> listerNotesValideesParEtudiant(String numeroEtudiant,
                                                       String institutionId)
            throws SQLException {
        if (estVide(numeroEtudiant) || estVide(institutionId)) {
            return Collections.emptyList();
        }
        return noteDAO.getAllValidatedNotesByEtudiant(numeroEtudiant, institutionId);
    }

    /**
     * ✅ Lance SQLException.
     */
    public List<Note> listerNotesFiltreesEtudiant(String etudiantId, String institutionId,
                                                    String annee, String periode,
                                                    String classe)
            throws SQLException {
        if (estVide(etudiantId) || estVide(institutionId)) {
            return Collections.emptyList();
        }
        return noteDAO.getNotesWithFiltersForStudent(etudiantId, institutionId,
                annee, periode, classe);
    }

    // ============================================================
    // MISE À JOUR
    // ============================================================
    public boolean mettreAJourNote(Note note) throws SQLException {
        if (note == null) {
            LOGGER.warning("mettreAJourNote() : note nulle");
            return false;
        }
        return noteDAO.update(note);
    }

@Deprecated
public boolean supprimerNote(String codeCours, String numeroEtudiant, String institutionId) {
    throw new UnsupportedOperationException(
            "supprimerNote() est obsolète et dangereuse (risque de supprimer "
            + "plusieurs lignes). Utilisez supprimerNoteAvecContexte() avec "
            + "les 6 critères : code_cours + numero_etudiant + institution "
            + "+ periode + annee + classe.");
}
    public boolean supprimerNoteAvecPeriode(String codeCours, String numeroEtudiant,
                                              String institutionId, String periode) {
        if (estVide(codeCours) || estVide(numeroEtudiant)
                || estVide(institutionId) || estVide(periode)) {
            return false;
        }
        LOGGER.fine(() -> "supprimerNoteAvecPeriode (déprécié) — "
                + "préférez supprimerNoteAvecContexte");
        return noteDAO.deleteWithContexte(codeCours, numeroEtudiant, institutionId,
                periode, null, null);
    }

    /**
     * ✅ SUPPRESSION COMPLÈTE (recommandée).
     *
     * Filtre par les 6 critères :
     *   institution + code_cours + numero_etudiant + periode + annee + classe.
     *
     * Cible précisément UNE ligne — aucune ambiguïté.
     */
    public boolean supprimerNoteAvecContexte(String codeCours, String numeroEtudiant,
                                               String institutionId, String periode,
                                               String annee, String classe) {
        if (estVide(codeCours) || estVide(numeroEtudiant) || estVide(institutionId)
                || estVide(periode) || estVide(annee) || estVide(classe)) {
            LOGGER.warning("supprimerNoteAvecContexte() : paramètres null ou vides");
            return false;
        }
        return noteDAO.deleteWithContexte(codeCours, numeroEtudiant, institutionId,
                periode, annee, classe);
    }

    // ============================================================
    // VALIDATION / REJET
    // ============================================================
    public boolean validerNote(String noteId, String verifiedBy) throws SQLException {
        if (estVide(noteId)) {
            LOGGER.warning("validerNote() : noteId null ou vide");
            return false;
        }
        String verificateur = estVide(verifiedBy) ? VERIFIED_BY_DEFAULT : verifiedBy;
        return noteDAO.validerNote(noteId, verificateur);
    }

    public boolean rejeterNote(String noteId) throws SQLException {
        if (estVide(noteId)) {
            LOGGER.warning("rejeterNote() : noteId null ou vide");
            return false;
        }
        return noteDAO.rejeterNote(noteId);
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private boolean estVide(String s) {
        return s == null || s.isBlank();
    }
}