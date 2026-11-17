package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/** Nettoyage des textes saisis : espaces en trop retirés, longueur contrôlée. */
public final class Textes {

    private Textes() {
    }

    public static String facultatif(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
        if (propre.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return propre;
    }

    public static String obligatoire(String valeur, String champ, int max) {
        String propre = facultatif(valeur, champ, max);
        if (propre.length() < 2) {
            throw new ErreurMetier(champ + " : au moins 2 caractères.");
        }
        return propre;
    }
}
