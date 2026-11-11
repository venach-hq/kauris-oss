package com.venach.kauris.kernel.commun;

import java.util.Locale;
import java.util.regex.Pattern;

/** Nettoyage et contrôle des textes saisis, partagés par tous les modules. */
public final class Saisie {

    public static final int EMAIL_MAX = 190;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern ESPACES = Pattern.compile("\\s+");

    private Saisie() {
    }

    /** Nom nettoyé (espaces en trop retirés), de 2 à {@code max} caractères. */
    public static String nom(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : ESPACES.matcher(valeur.strip()).replaceAll(" ");
        if (propre.length() < 2 || propre.length() > max) {
            throw new ErreurMetier(champ + " : entre 2 et " + max + " caractères.");
        }
        return propre;
    }

    /** Adresse email en minuscules ; refuse une adresse mal formée. */
    public static String email(String valeur) {
        String propre = valeur == null ? "" : valeur.strip().toLowerCase(Locale.ROOT);
        if (propre.length() > EMAIL_MAX || !EMAIL.matcher(propre).matches()) {
            throw new ErreurMetier("Adresse email invalide.");
        }
        return propre;
    }
}
