package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/** Petites règles de saisie communes au domaine de la flotte. */
final class Textes {

    private Textes() {
    }

    /** Le texte nettoyé ; vide permis si {@code obligatoire} est faux. */
    static String propre(String valeur, String champ, int max, boolean obligatoire) {
        String v = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
        if (obligatoire && v.isEmpty()) {
            throw new ErreurMetier(champ + " : obligatoire.");
        }
        if (v.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return v;
    }

    static long montant(long montant, String champ, boolean zeroPermis) {
        if (montant < 0 || (!zeroPermis && montant == 0) || montant > 1_000_000_000L) {
            throw new ErreurMetier(champ + " : un montant " + (zeroPermis ? "positif ou nul" : "positif") + ".");
        }
        return montant;
    }
}
