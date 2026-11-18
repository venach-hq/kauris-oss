package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le lettrage : rapprocher, sur un compte de tiers, les lignes qui se compensent (une facture et son règlement). Les
 * lignes lettrées ensemble portent le même code (A, B… Z, AA, AB…), et leur total est nul.
 */
public final class Lettrage {

    private Lettrage() {
    }

    /** Le code suivant, après ceux déjà utilisés sur le compte. */
    public static String prochain(Set<String> utilises) {
        long n = 0;
        while (true) {
            String code = code(n++);
            if (!utilises.contains(code)) {
                return code;
            }
        }
    }

    /** 0 → A, 25 → Z, 26 → AA… */
    static String code(long n) {
        StringBuilder s = new StringBuilder();
        long x = n;
        do {
            s.insert(0, (char) ('A' + x % 26));
            x = x / 26 - 1;
        } while (x >= 0);
        return s.toString();
    }

    /** Vérifie qu'un groupe de lignes peut se lettrer. */
    public static void verifier(Collection<Mouvement> lignes) {
        if (lignes.size() < 2) {
            throw new ErreurMetier("Choisissez au moins deux lignes à lettrer.");
        }
        if (lignes.stream().map(Mouvement::compte).distinct().count() > 1) {
            throw new ErreurMetier("Les lignes à lettrer sont sur le même compte.");
        }
        if (lignes.stream().anyMatch(m -> !m.validee())) {
            throw new ErreurMetier("Seules les écritures validées se lettrent.");
        }
        if (lignes.stream().anyMatch(Mouvement::lettree)) {
            throw new ErreurMetier("Une des lignes est déjà lettrée : délettrez-la d'abord.");
        }
        long solde = lignes.stream().mapToLong(Mouvement::solde).sum();
        if (solde != 0) {
            throw new ErreurMetier("Les lignes ne se compensent pas : écart de " + Math.abs(solde) + ".");
        }
    }

    /**
     * Les groupes à lettrer automatiquement parmi les lignes non lettrées d'un compte : d'abord par tiers et pièce
     * (une facture et ses règlements), puis, pour chaque tiers, tout ce qui reste s'il se compense.
     */
    public static List<List<Mouvement>> automatique(List<Mouvement> nonLettrees) {
        List<List<Mouvement>> groupes = new ArrayList<>();
        Map<String, List<Mouvement>> parPiece = new LinkedHashMap<>();
        for (Mouvement m : nonLettrees) {
            if (m.validee() && !m.lettree() && !m.piece().isEmpty()) {
                parPiece.computeIfAbsent(m.tiers() + "\u0000" + m.piece(), k -> new ArrayList<>()).add(m);
            }
        }
        List<Mouvement> pris = new ArrayList<>();
        for (List<Mouvement> g : parPiece.values()) {
            if (g.size() >= 2 && g.stream().mapToLong(Mouvement::solde).sum() == 0) {
                groupes.add(g);
                pris.addAll(g);
            }
        }
        Map<String, List<Mouvement>> parTiers = new LinkedHashMap<>();
        for (Mouvement m : nonLettrees) {
            if (m.validee() && !m.lettree() && !m.tiers().isEmpty() && !pris.contains(m)) {
                parTiers.computeIfAbsent(m.tiers(), k -> new ArrayList<>()).add(m);
            }
        }
        for (List<Mouvement> g : parTiers.values()) {
            if (g.size() >= 2 && g.stream().mapToLong(Mouvement::solde).sum() == 0) {
                groupes.add(g);
            }
        }
        return groupes;
    }
}
