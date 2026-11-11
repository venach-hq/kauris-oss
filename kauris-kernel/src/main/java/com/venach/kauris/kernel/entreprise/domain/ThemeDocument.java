package com.venach.kauris.kernel.entreprise.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Le thème de tous les documents d'une entreprise : polices, taille du texte, couleurs, place du logo et style
 * du titre. Trois modèles sont proposés ; « Personnalisé » part de l'un d'eux et change ce qu'on veut.
 *
 * <p>Les polices sont désignées par leur famille (« Inter », « PlayfairDisplay ») : celles du dossier de
 * polices du serveur, ou les trois polices de base de tout PDF (Helvetica, Times, Courier). Une famille absente
 * du serveur est remplacée par la police de base de même genre, sans erreur.
 *
 * @param taille taille du texte courant, en points (8 à 14)
 */
public record ThemeDocument(Modele modele, String policeTitres, String policeTexte, int taille,
                            String couleurPrincipale, String couleurEntetes, String couleurTexte,
                            PositionLogo logo, StyleTitre titre) {

    public static final int TAILLE_MIN = 8;
    public static final int TAILLE_MAX = 14;

    // Avant les modèles : leurs constructeurs s'en servent.
    private static final Pattern COULEUR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern FAMILLE = Pattern.compile("[A-Za-z][A-Za-z0-9 ]{0,39}");
    /** Familles à empattements connues : leur remplaçant est Times, pas Helvetica. */
    private static final Set<String> A_EMPATTEMENTS = Set.of("merriweather", "playfairdisplay", "playfair display",
            "times", "georgia", "garamond", "lora", "ptserif", "pt serif", "librebaskerville", "sourceserifpro",
            "notoserif", "crimsontext", "ebgaramond");

    public enum Modele {
        MODERNE("Moderne", "Sobre et net : police Inter, bleu nuit, titre en capitales espacées."),
        CLASSIQUE("Classique", "Titres à empattements, filets fins, en-têtes de tableau clairs."),
        ELEGANT("Élégant", "Police Poppins, couleur affirmée, logo à droite."),
        PERSONNALISE("Personnalisé", "Vos polices, vos couleurs, à partir d'un des modèles.");

        private final String libelle;
        private final String description;

        Modele(String libelle, String description) {
            this.libelle = libelle;
            this.description = description;
        }

        public String libelle() {
            return libelle;
        }

        public String description() {
            return description;
        }
    }

    public enum PositionLogo {
        GAUCHE("À gauche"), DROITE("À droite");

        private final String libelle;

        PositionLogo(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    public enum StyleTitre {
        MAJUSCULES("CAPITALES ESPACÉES"), NORMAL("Normal");

        private final String libelle;

        StyleTitre(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    public static final ThemeDocument MODERNE = new ThemeDocument(Modele.MODERNE, "Inter", "Inter", 9,
            "#1E293B", "#1E293B", "#111827", PositionLogo.GAUCHE, StyleTitre.MAJUSCULES);
    public static final ThemeDocument CLASSIQUE = new ThemeDocument(Modele.CLASSIQUE, "Merriweather", "Lato", 10,
            "#7A1F2B", "#EDE7DD", "#1F1F1F", PositionLogo.GAUCHE, StyleTitre.NORMAL);
    public static final ThemeDocument ELEGANT = new ThemeDocument(Modele.ELEGANT, "Poppins", "Poppins", 9,
            "#0F766E", "#0F766E", "#1F2937", PositionLogo.DROITE, StyleTitre.MAJUSCULES);
    public static final ThemeDocument PAR_DEFAUT = MODERNE;

    public ThemeDocument {
        Objects.requireNonNull(modele, "modèle requis");
        Objects.requireNonNull(logo, "position du logo requise");
        Objects.requireNonNull(titre, "style du titre requis");
        if (taille < TAILLE_MIN || taille > TAILLE_MAX) {
            throw new ErreurMetier("Taille du texte : entre " + TAILLE_MIN + " et " + TAILLE_MAX + " points.");
        }
        policeTitres = famille(policeTitres, "Police des titres");
        policeTexte = famille(policeTexte, "Police du texte");
        couleurPrincipale = couleur(couleurPrincipale, "Couleur principale");
        couleurEntetes = couleur(couleurEntetes, "Couleur des en-têtes de tableau");
        couleurTexte = couleur(couleurTexte, "Couleur du texte");
    }

    /** Le thème d'un modèle proposé ; pour « Personnalisé », le thème moderne comme point de départ. */
    public static ThemeDocument du(Modele modele) {
        return switch (modele) {
            case MODERNE, PERSONNALISE -> MODERNE;
            case CLASSIQUE -> CLASSIQUE;
            case ELEGANT -> ELEGANT;
        };
    }

    private static String famille(String valeur, String champ) {
        String propre = valeur == null ? "" : valeur.strip();
        if (!FAMILLE.matcher(propre).matches()) {
            throw new ErreurMetier(champ + " : police inconnue.");
        }
        return propre;
    }

    private static String couleur(String valeur, String champ) {
        if (valeur == null || !COULEUR.matcher(valeur.strip()).matches()) {
            throw new ErreurMetier(champ + " : une couleur au format #RRGGBB, par exemple #1E293B.");
        }
        return valeur.strip().toUpperCase(Locale.ROOT);
    }

    /** La valeur CSS de font-family : la famille, puis la police de base de même genre si elle manque. */
    public static String pileDePolices(String famille) {
        String cle = famille.toLowerCase(Locale.ROOT);
        String repli = cle.equals("courier") ? "Courier, monospace"
                : A_EMPATTEMENTS.contains(cle) ? "Times, 'Times New Roman', serif"
                : "Helvetica, Arial, sans-serif";
        return "'" + famille + "', " + repli;
    }

    /** Blanc ou presque noir, pour rester lisible sur la couleur des en-têtes de tableau. */
    public String texteSurEntetes() {
        int r = Integer.parseInt(couleurEntetes.substring(1, 3), 16);
        int g = Integer.parseInt(couleurEntetes.substring(3, 5), 16);
        int b = Integer.parseInt(couleurEntetes.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.6 ? "#1F1F1F" : "#FFFFFF";
    }

    /**
     * Les règles CSS du thème, avec les valeurs en clair (le moteur PDF ne lit pas les variables CSS). Sûres à
     * écrire dans une balise style : couleurs et familles sont contrôlées à la construction.
     */
    public String feuille() {
        String titres = pileDePolices(policeTitres);
        String texte = pileDePolices(policeTexte);
        boolean capitales = titre == StyleTitre.MAJUSCULES;
        return ".k-doc{font-family:" + texte + ";font-size:" + taille + "pt;color:" + couleurTexte + ";}"
                + ".k-doc-nom,.k-doc-titre,.k-doc h2{font-family:" + titres + ";}"
                + ".k-doc-titre{color:" + couleurPrincipale + ";"
                + (capitales ? "text-transform:uppercase;letter-spacing:0.12em;" : "") + "}"
                + ".k-doc-nom{color:" + couleurTexte + ";}"
                + ".k-doc-entete{border-bottom-color:" + couleurPrincipale + ";}"
                + ".k-doc h2{color:" + couleurPrincipale + ";}"
                + ".k-doc-tableau th{background-color:" + couleurEntetes + ";color:" + texteSurEntetes() + ";}"
                + ".k-doc-tableau tr.k-doc-total td{border-top-color:" + couleurPrincipale + ";}"
                + ".k-doc-pied{border-top-color:" + couleurPrincipale + ";}";
    }
}
