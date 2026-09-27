import java.text.Normalizer;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Constantes globales du système (couleurs, chemins, listes statiques).
 * Cette classe ne doit pas être instanciée.
 */
public final class Constantes {

    private Constantes() {} // Empêche l'instanciation

    // Dans Constantes.java
public static final String[] TYPES_INSTITUTION = {
    "Université", "École Supérieure", "Lycée", "Collège", "École Primaire", "Centre de Formation"
};

public static final String[] NIVEAUX_INSTITUTION = {
    "Primaire", "Secondaire", "Supérieur", "Post-universitaire"
};

public static final String[] CATEGORIES_INSTITUTION = {
    "Publique", "Privée", "Mixte", "Confessionnelle", "Laïque"
};
    // ==================== CODES POSTAUX PAR DÉPARTEMENT ====================
    public static final String[] CODES_POSTAUX_ARTIBONITE = {
        "4110", // Gonaïves
        "4120", // Saint-Marc
        "4130", // Verrettes
        "4140", // Dessalines
        "4150", // Marmelade
        "4160", // Petite-Rivière-de-l'Artibonite
        "4170", // Grande-Saline
        "4180", // Ennery
        "4190"  // L'Estère
    };

    public static final String[] CODES_POSTAUX_CENTRE = {
        "5110", // Hinche
        "5120", // Mirebalais
        "5130", // Lascahobas
        "5140", // Cerca-la-Source
        "5150", // Thomonde
        "5160", // Belladère
        "5170", // Saut-d'Eau
        "5180", // Maïssade
        "5190"  // Boucan-Carré
    };

    public static final String[] CODES_POSTAUX_GRANDE_ANSE = {
        "6110", // Jérémie
        "6120", // Anse-d'Hainault
        "6130", // Dame-Marie
        "6140", // Corail
        "6150", // Moron
        "6160", // Roseaux
        "6170", // Beaumont
        "6180"  // Chambellan
    };

    public static final String[] CODES_POSTAUX_NIPPES = {
        "7110", // Miragoâne
        "7120", // Anse-à-Veau
        "7130", // Baradères
        "7140", // Petit-Trou-de-Nippes
        "7150", // Paillant
        "7160", // Plaisance-du-Sud
        "7170", // Fonds-des-Nègres
        "7180"  // Arnaud
    };

    public static final String[] CODES_POSTAUX_NORD = {
        "1110", // Cap-Haïtien
        "1120", // Limonade
        "1130", // Milot
        "1140", // Plaine-du-Nord
        "1150", // Grande-Rivière-du-Nord
        "1160", // Acul-du-Nord
        "1170", // Borgne
        "1180", // Port-Margot
        "1190"  // Quartier-Morin
    };

    public static final String[] CODES_POSTAUX_NORD_EST = {
        "2110", // Fort-Liberté
        "2120", // Ouanaminthe
        "2130", // Trou-du-Nord
        "2140", // Vallières
        "2150", // Ferrier
        "2160", // Capotille
        "2170", // Mont-Organisé
        "2180"  // Perches
    };

    public static final String[] CODES_POSTAUX_NORD_OUEST = {
        "3110", // Port-de-Paix
        "3120", // Saint-Louis-du-Nord
        "3130", // Jean-Rabel
        "3140", // Môle-Saint-Nicolas
        "3150", // Bombardopolis
        "3160", // Baie-de-Henne
        "3170", // Anse-à-Foleur
        "3180"  // Bassin-Bleu
    };

    public static final String[] CODES_POSTAUX_OUEST = {
        "6110", // Port-au-Prince
        "6120", // Delmas
        "6130", // Pétion-Ville
        "6140", // Carrefour
        "6150", // Kenscoff
        "6160", // Gressier
        "6170", // Croix-des-Bouquets
        "6180", // Thomazeau
        "6190", // Tabarre
        "6210", // Cité Soleil
        "6220", // Léogâne
        "6230", // Grand-Goâve
        "6240", // Petit-Goâve
        "6250", // Arcahaie
        "6260", // Cabaret
        "6270", // Cornillon
        "6280", // Anse-à-Galets
        "6290"  // Pointe-à-Raquette
    };

    public static final String[] CODES_POSTAUX_SUD = {
        "8110", // Les Cayes
        "8120", // Port-Salut
        "8130", // Chantal
        "8140", // Torbeck
        "8150", // Camp-Perrin
        "8160", // Aquin
        "8170", // Côteaux
        "8180", // Saint-Jean-du-Sud
        "8190"  // Roche-à-Bateau
    };

    public static final String[] CODES_POSTAUX_SUD_EST = {
        "9110", // Jacmel
        "9120", // Bainet
        "9130", // Belle-Anse
        "9140", // Côtes-de-Fer
        "9150", // Marigot
        "9160", // Cayes-Jacmel
        "9170", // La Vallée de Jacmel
        "9180", // Anse-à-Pitres
        "9190"  // Grand-Gosier
    };

    // ==================== NOMS DES TABLES STATIQUES ====================
    public static final String TABLE_ETUDIANTS = "etudiants";
    public static final String TABLE_ADMINS = "administrateurs";
    public static final String TABLE_PROFESSEURS = "professeurs";
    public static final String TABLE_MATIERES = "matieres";
    public static final String TABLE_NOTES = "notes";
    public static final String TABLE_CLASSES = "classes";
    public static final String TABLE_AUTORISATIONS = "autorisations";

    // ==================== DOSSIERS STATIQUES ====================
    public static final String DOSSIER_DONNEES = "data/";
    public static final String DOSSIER_PHOTOS = "data/photos/";
    public static final String DOSSIER_QR = "data/qr_codes/";
    public static final String DOSSIER_BULLETINS = "data/bulletins/";
    public static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 Mo
    public static final String FORMAT_DATE = "yyyy-MM-dd";
    
    public static final String[] OPTIONS_ATTESTATION = {
        "Attestation de scolarité",
        "Attestation de Fin d'Etudes",
        "Attestation de présence"
    };
    
    public static final String[] TYPES = {
        "Statistiques",
        "Economie Appliquee",
        "Planification"
    };

    // ==================== LISTES PRÉDÉFINIES ====================
    public static final String[] SEXES = { "Masculin", "Féminin" };
    public static final String[] GROUPES_SANGUINS = { "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-" };
    public static final String[] SITUATIONS_MATRIMONIALES = {
        "Célibataire", "Marié(e)", "Divorcé(e)", "Veuf/Veuve","Placé(e)"
    };
    
    public static final String[] DEPARTEMENTS = { 
        "Artibonite", "Centre", "Grande-Anse", "Nippes", "Ouest",
        "Nord-Ouest", "Nord-Est", "Sud", "Sud-Est", "Nord" 
    };

    // ==================== COMMUNES PAR DÉPARTEMENT ====================
    public static final String[] COMMUNES_ARTIBONITE = {
        "Gonaïves", "Saint-Marc", "Verrettes", "Dessalines", "Marmelade",
        "Petite-Rivière-de-l'Artibonite", "Grande-Saline", "Ennery", "L'Estère",
        "Anse-Rouge", "Terre-Neuve", "Gros-Morne", "Saint-Michel-de-l'Attalaye",
        "La Chapelle", "Desdunes"
    };

    public static final String[] COMMUNES_CENTRE = {
        "Hinche", "Mirebalais", "Lascahobas", "Cerca-la-Source", "Thomonde",
        "Belladère", "Saut-d'Eau", "Maïssade", "Boucan-Carré", "Cerca-Carvajal",
        "Savanette", "Thomassique"
    };

    public static final String[] COMMUNES_GRANDE_ANSE = {
        "Jérémie", "Anse-d'Hainault", "Dame-Marie", "Corail", "Moron",
        "Roseaux", "Beaumont", "Chambellan", "Les Irois", "Abricots",
        "Bonbon", "Pestel"
    };

    public static final String[] COMMUNES_NIPPES = {
        "Miragoâne", "Anse-à-Veau", "Baradères", "Petit-Trou-de-Nippes",
        "Paillant", "Plaisance-du-Sud", "Fonds-des-Nègres", "Arnaud",
        "L'Asile", "Grand-Boucan", "Petite-Rivière-de-Nippes"
    };

    public static final String[] COMMUNES_NORD = {
        "Cap-Haïtien", "Limonade", "Milot", "Plaine-du-Nord", "Grande-Rivière-du-Nord",
        "Acul-du-Nord", "Borgne", "Port-Margot", "Quartier-Morin", "Bas-Limbé",
        "Dondon", "Limbé", "Pignon", "Pilate", "Plaisance", "Ranquitte",
        "Saint-Raphaël", "Bahon", "La Victoire"
    };

    public static final String[] COMMUNES_NORD_EST = {
        "Fort-Liberté", "Ouanaminthe", "Trou-du-Nord", "Vallières", "Ferrier",
        "Capotille", "Mont-Organisé", "Perches", "Caracol", "Sainte-Suzanne",
        "Terrier-Rouge", "Carice", "Mombin-Crochu"
    };

    public static final String[] COMMUNES_NORD_OUEST = {
        "Port-de-Paix", "Saint-Louis-du-Nord", "Jean-Rabel", "Môle-Saint-Nicolas",
        "Bombardopolis", "Baie-de-Henne", "Anse-à-Foleur", "Bassin-Bleu",
        "Chansolme", "La Tortue"
    };

    public static final String[] COMMUNES_OUEST = {
        "Port-au-Prince", "Delmas", "Pétion-Ville", "Carrefour", "Kenscoff",
        "Gressier", "Croix-des-Bouquets", "Thomazeau", "Tabarre", "Cité Soleil",
        "Léogâne", "Grand-Goâve", "Petit-Goâve", "Arcahaie", "Cabaret",
        "Cornillon", "Anse-à-Galets", "Pointe-à-Raquette", "Ganthier", "Fond-Verrettes"
    };

    public static final String[] COMMUNES_SUD = {
        "Les Cayes", "Port-Salut", "Chantal", "Torbeck", "Camp-Perrin",
        "Aquin", "Côteaux", "Saint-Jean-du-Sud", "Roche-à-Bateau", "Arniquet",
        "Cavaillon", "Saint-Louis-du-Sud", "Anglais", "Port-à-Piment",
        "Randel", "Tiburon", "Maniche", "Ile-à-Vache"
    };

    public static final String[] COMMUNES_SUD_EST = {
        "Jacmel", "Bainet", "Belle-Anse", "Côtes-de-Fer", "Marigot",
        "Cayes-Jacmel", "La Vallée de Jacmel", "Anse-à-Pitres", "Grand-Gosier", "Thiotte"
    };

    // ==================== MAPS POUR LA RECHERCHE RAPIDE ====================
    
    // Map des communes par département
    private static final Map<String, String[]> COMMUNES_MAP = new HashMap<>();
    
    // Map des codes postaux par département
    private static final Map<String, String[]> CODES_POSTAUX_MAP = new HashMap<>();
    
    // Map des alias pour les noms de départements
    private static final Map<String, String> DEPARTEMENT_ALIAS = new HashMap<>();

    static {
        // Initialiser la map des communes
        COMMUNES_MAP.put("artibonite", COMMUNES_ARTIBONITE);
        COMMUNES_MAP.put("centre", COMMUNES_CENTRE);
        COMMUNES_MAP.put("grande-anse", COMMUNES_GRANDE_ANSE);
        COMMUNES_MAP.put("grandeanse", COMMUNES_GRANDE_ANSE);
        COMMUNES_MAP.put("grande_anse", COMMUNES_GRANDE_ANSE);
        COMMUNES_MAP.put("nippes", COMMUNES_NIPPES);
        COMMUNES_MAP.put("nord", COMMUNES_NORD);
        COMMUNES_MAP.put("nord-est", COMMUNES_NORD_EST);
        COMMUNES_MAP.put("nordest", COMMUNES_NORD_EST);
        COMMUNES_MAP.put("nord-ouest", COMMUNES_NORD_OUEST);
        COMMUNES_MAP.put("nordouest", COMMUNES_NORD_OUEST);
        COMMUNES_MAP.put("ouest", COMMUNES_OUEST);
        COMMUNES_MAP.put("sud", COMMUNES_SUD);
        COMMUNES_MAP.put("sud-est", COMMUNES_SUD_EST);
        COMMUNES_MAP.put("sudest", COMMUNES_SUD_EST);
        
        // Initialiser la map des codes postaux
        CODES_POSTAUX_MAP.put("artibonite", CODES_POSTAUX_ARTIBONITE);
        CODES_POSTAUX_MAP.put("centre", CODES_POSTAUX_CENTRE);
        CODES_POSTAUX_MAP.put("grande-anse", CODES_POSTAUX_GRANDE_ANSE);
        CODES_POSTAUX_MAP.put("grandeanse", CODES_POSTAUX_GRANDE_ANSE);
        CODES_POSTAUX_MAP.put("grande_anse", CODES_POSTAUX_GRANDE_ANSE);
        CODES_POSTAUX_MAP.put("nippes", CODES_POSTAUX_NIPPES);
        CODES_POSTAUX_MAP.put("nord", CODES_POSTAUX_NORD);
        CODES_POSTAUX_MAP.put("nord-est", CODES_POSTAUX_NORD_EST);
        CODES_POSTAUX_MAP.put("nordest", CODES_POSTAUX_NORD_EST);
        CODES_POSTAUX_MAP.put("nord-ouest", CODES_POSTAUX_NORD_OUEST);
        CODES_POSTAUX_MAP.put("nordouest", CODES_POSTAUX_NORD_OUEST);
        CODES_POSTAUX_MAP.put("ouest", CODES_POSTAUX_OUEST);
        CODES_POSTAUX_MAP.put("sud", CODES_POSTAUX_SUD);
        CODES_POSTAUX_MAP.put("sud-est", CODES_POSTAUX_SUD_EST);
        CODES_POSTAUX_MAP.put("sudest", CODES_POSTAUX_SUD_EST);
        
        // Initialiser les alias des départements
        DEPARTEMENT_ALIAS.put("artibonite", "artibonite");
        DEPARTEMENT_ALIAS.put("artibonites", "artibonite");
        DEPARTEMENT_ALIAS.put("centre", "centre");
        DEPARTEMENT_ALIAS.put("centres", "centre");
        DEPARTEMENT_ALIAS.put("grande-anse", "grande-anse");
        DEPARTEMENT_ALIAS.put("grandeanse", "grande-anse");
        DEPARTEMENT_ALIAS.put("grande_anse", "grande-anse");
        DEPARTEMENT_ALIAS.put("nippes", "nippes");
        DEPARTEMENT_ALIAS.put("nord", "nord");
        DEPARTEMENT_ALIAS.put("nord-est", "nord-est");
        DEPARTEMENT_ALIAS.put("nordest", "nord-est");
        DEPARTEMENT_ALIAS.put("nord-ouest", "nord-ouest");
        DEPARTEMENT_ALIAS.put("nordouest", "nord-ouest");
        DEPARTEMENT_ALIAS.put("ouest", "ouest");
        DEPARTEMENT_ALIAS.put("sud", "sud");
        DEPARTEMENT_ALIAS.put("sud-est", "sud-est");
        DEPARTEMENT_ALIAS.put("sudest", "sud-est");
    }

    // ==================== MÉTHODES UTILITAIRES ====================
    
    /**
     * Normalise une chaîne de caractères (supprime les accents, les caractères spéciaux, etc.)
     */
    private static String normaliser(String texte) {
        if (texte == null || texte.isEmpty()) return "";
        
        String normalise = texte.trim().toLowerCase();
        
        // Remplacer les apostrophes et caractères spéciaux
        normalise = normalise.replace("'", "").replace("’", "")
                            .replace("´", "").replace("`", "")
                            .replace("’", "").replace("'", "");
        
        // Normaliser les accents
        normalise = Normalizer.normalize(normalise, Normalizer.Form.NFD);
        normalise = normalise.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        return normalise;
    }

    /**
     * Nettoie et normalise un nom de département pour la recherche
     */
    private static String nettoyerDepartement(String departement) {
        if (departement == null || departement.isEmpty()) return "";
        
        String normalise = normaliser(departement);
        
        // Remplacer les tirets et underscores par rien pour les versions sans séparateurs
        String sansSeparateur = normalise.replace("-", "").replace("_", "");
        
        // Vérifier si c'est un alias connu
        String alias = DEPARTEMENT_ALIAS.get(normalise);
        if (alias != null) return alias;
        
        alias = DEPARTEMENT_ALIAS.get(sansSeparateur);
        if (alias != null) return alias;
        
        // Si ce n'est pas un alias, retourner la version normalisée
        return normalise;
    }

    // ==================== GETTERS PUBLICS ====================
    
    public static String[] getSexes() {
        return SEXES.clone();
    }

    public static String[] getSituationsMatrimoniales() {
        return SITUATIONS_MATRIMONIALES.clone();
    }

    public static String[] getGroupesSanguins() {
        return GROUPES_SANGUINS.clone();
    }

    public static String[] getDepartements() {
        return DEPARTEMENTS.clone();
    }

    /**
     * Récupère les communes d'un département de manière robuste
     */
    public static String[] getCommunes(String departement) {
        if (departement == null || departement.trim().isEmpty()) {
            System.out.println("⚠️ Département null ou vide");
            return new String[0];
        }
        
        // Nettoyer et normaliser le nom du département
        String depClean = nettoyerDepartement(departement);
        
        System.out.println("🔍 Recherche communes pour: '" + departement + "' → normalisé: '" + depClean + "'");
        
        // Rechercher dans la map
        String[] communes = COMMUNES_MAP.get(depClean);
        
        if (communes == null) {
            // Essayer avec le nom original (sans normalisation)
            communes = COMMUNES_MAP.get(departement.trim().toLowerCase());
        }
        
        if (communes == null) {
            System.out.println("⚠️ Département non reconnu: '" + departement + "'");
            return new String[0];
        }
        
        System.out.println("✅ " + communes.length + " communes trouvées pour: " + departement);
        return communes.clone();
    }

    /**
     * Récupère les codes postaux d'un département de manière robuste
     */
    public static String[] getCodesPostaux(String departement) {
        if (departement == null || departement.trim().isEmpty()) {
            return new String[0];
        }
        
        // Nettoyer et normaliser le nom du département
        String depClean = nettoyerDepartement(departement);
        
        // Rechercher dans la map
        String[] codes = CODES_POSTAUX_MAP.get(depClean);
        
        if (codes == null) {
            // Essayer avec le nom original (sans normalisation)
            codes = CODES_POSTAUX_MAP.get(departement.trim().toLowerCase());
        }
        
        if (codes == null) {
            System.out.println("⚠️ Codes postaux non trouvés pour: '" + departement + "'");
            return new String[0];
        }
        
        return codes.clone();
    }

    /**
     * Vérifie si un département existe
     */
    public static boolean estDepartementValide(String departement) {
        if (departement == null || departement.isEmpty()) return false;
        
        String depClean = nettoyerDepartement(departement);
        return COMMUNES_MAP.containsKey(depClean);
    }

    /**
     * Vérifie si une commune existe pour un département donné
     */
    public static boolean estCommuneValide(String commune, String departement) {
        if (commune == null || commune.isEmpty() || departement == null || departement.isEmpty()) {
            return false;
        }
        
        String[] communes = getCommunes(departement);
        String communeNormalisee = normaliser(commune);
        
        for (String c : communes) {
            if (normaliser(c).equals(communeNormalisee)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Récupère la liste complète des départements avec leurs communes
     */
    public static String[][] getDepartementsCommunes() {
        String[][] result = new String[DEPARTEMENTS.length][];
        for (int i = 0; i < DEPARTEMENTS.length; i++) {
            String[] communes = getCommunes(DEPARTEMENTS[i]);
            result[i] = new String[communes.length + 1];
            result[i][0] = DEPARTEMENTS[i];
            System.arraycopy(communes, 0, result[i], 1, communes.length);
        }
        return result;
    }

    /**
     * Récupère les années académiques
     */
    public static String[] getAnneesAcademiques() {
        int annee = Calendar.getInstance().get(Calendar.YEAR);
        return new String[] { 
            (annee - 1) + "-" + annee, 
            annee + "-" + (annee + 1) 
        };
    }

    /**
     * Méthode utilitaire pour afficher toutes les données (debug)
     */
    public static void afficherToutesLesCommunes() {
        System.out.println("\n=== LISTE COMPLÈTE DES COMMUNES PAR DÉPARTEMENT ===");
        for (String departement : DEPARTEMENTS) {
            String[] communes = getCommunes(departement);
            System.out.println("\n📌 " + departement + " (" + communes.length + " communes):");
            for (String commune : communes) {
                System.out.println("   - " + commune);
            }
        }
    }

 
}