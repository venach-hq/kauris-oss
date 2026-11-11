package com.venach.kauris.kernel.entreprise.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * L'apparence des documents que l'entreprise produit (factures, bulletins, états) : police, taille et
 * couleurs. Toutes les valeurs sont contrôlées ici : elles finissent dans une feuille de style.
 *
 * @param taille            taille du texte courant, en points
 * @param couleurPrincipale titres, filets et en-tête, « #RRGGBB »
 * @param couleurTexte      texte courant, « #RRGGBB »
 */
public record ThemeDocument(Police police, int taille, String couleurPrincipale, String couleurTexte) {

    public static final int TAILLE_MIN = 8;
    public static final int TAILLE_MAX = 14;
    // Avant PAR_DEFAUT : le constructeur s'en sert.
    private static final Pattern COULEUR = Pattern.compile("#[0-9A-Fa-f]{6}");

    public static final ThemeDocument PAR_DEFAUT = new ThemeDocument(Police.SANS, 10, "#03C75A", "#1E1E1E");

    /** Familles de polices présentes sur tous les postes et à l'impression : rien à télécharger. */
    public enum Police {
        SANS("Arial / Helvetica", "Arial, Helvetica, sans-serif"),
        VERDANA("Verdana", "Verdana, Geneva, sans-serif"),
        TAHOMA("Tahoma", "Tahoma, Geneva, sans-serif"),
        TREBUCHET("Trebuchet MS", "'Trebuchet MS', Helvetica, sans-serif"),
        SERIF("Times New Roman", "'Times New Roman', Times, serif"),
        GEORGIA("Georgia", "Georgia, 'Times New Roman', serif"),
        GARAMOND("Garamond", "Garamond, Georgia, serif"),
        MONO("Courier New", "'Courier New', Courier, monospace");

        private final String libelle;
        private final String famille;

        Police(String libelle, String famille) {
            this.libelle = libelle;
            this.famille = famille;
        }

        public String libelle() {
            return libelle;
        }

        /** La valeur CSS de {@code font-family}. */
        public String famille() {
            return famille;
        }
    }

    public ThemeDocument {
        Objects.requireNonNull(police, "police requise");
        if (taille < TAILLE_MIN || taille > TAILLE_MAX) {
            throw new ErreurMetier("Taille du texte : entre " + TAILLE_MIN + " et " + TAILLE_MAX + " points.");
        }
        couleurPrincipale = couleur(couleurPrincipale, "Couleur principale");
        couleurTexte = couleur(couleurTexte, "Couleur du texte");
    }

    private static String couleur(String valeur, String champ) {
        if (valeur == null || !COULEUR.matcher(valeur.strip()).matches()) {
            throw new ErreurMetier(champ + " : une couleur au format #RRGGBB, par exemple #03C75A.");
        }
        return valeur.strip().toUpperCase();
    }

    /** Le style du corps d'un document, sûr à écrire dans un attribut {@code style}. */
    public String css() {
        return "font-family:" + police.famille() + ";font-size:" + taille + "pt;color:" + couleurTexte + ";"
                + "--k-doc-principale:" + couleurPrincipale + ";";
    }
}
