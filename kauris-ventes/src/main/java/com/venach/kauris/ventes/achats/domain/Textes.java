package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/** Textes facultatifs saisis : nettoyés et bornés. */
final class Textes {

    private Textes() {
    }

    static String texte(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : valeur.strip();
        if (propre.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return propre;
    }
}
