package com.venach.kauris.kernel.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Le cadre d'informations sous le titre d'un document : des cases « ÉTIQUETTE / valeur » (entité, période,
 * NIF, monnaie…), rangées par lignes de quatre.
 */
public record Cartouche(List<Case> cases) {

    public static final int PAR_LIGNE = 4;

    public record Case(String libelle, String valeur) {

        public Case {
            libelle = libelle == null ? "" : libelle;
            valeur = valeur == null || valeur.isBlank() ? "—" : valeur;
        }
    }

    public Cartouche {
        cases = List.copyOf(cases);
    }

    public static Cartouche de(Case... cases) {
        return new Cartouche(List.of(cases));
    }

    public static Case c(String libelle, String valeur) {
        return new Case(libelle, valeur);
    }

    /** Les cases par lignes de quatre ; la dernière est complétée de cases vides. */
    public List<List<Case>> lignes() {
        List<List<Case>> lignes = new ArrayList<>();
        for (int i = 0; i < cases.size(); i += PAR_LIGNE) {
            List<Case> ligne = new ArrayList<>(cases.subList(i, Math.min(i + PAR_LIGNE, cases.size())));
            while (ligne.size() < PAR_LIGNE) {
                ligne.add(new Case("", ""));
            }
            lignes.add(ligne);
        }
        return lignes;
    }
}
