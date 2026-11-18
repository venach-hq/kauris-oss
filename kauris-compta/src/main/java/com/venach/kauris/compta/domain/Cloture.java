package com.venach.kauris.compta.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * La clôture d'un exercice : les soldes des comptes de bilan, et le résultat (charges et produits), deviennent
 * l'écriture d'à-nouveaux du 1er janvier suivant. Le résultat va en « résultat en instance d'affectation » (1301
 * bénéfice, 1309 perte) ; son affectation (réserves, report à nouveau, dividendes) se saisit ensuite en opération
 * diverse. Les comptes de tiers gardent leur détail par tiers.
 */
public final class Cloture {

    public static final String BENEFICE = "130100";
    public static final String PERTE = "130900";

    private Cloture() {
    }

    /** Le résultat de l'exercice : produits − charges (positif = bénéfice). */
    public static long resultat(List<Mouvement> mouvements) {
        long r = 0;
        for (Mouvement m : mouvements) {
            if (Compte.natureDe(m.compte()) != Compte.Nature.BILAN) {
                r -= m.solde();
            }
        }
        return r;
    }

    /** Les lignes d'à-nouveaux, à partir de tous les mouvements validés de l'exercice. */
    public static List<LigneEcriture> aNouveaux(List<Mouvement> mouvements, int annee,
                                               java.util.function.Predicate<String> lettrable) {
        Map<String, Long> soldes = new TreeMap<>();
        Map<String, String> noms = new TreeMap<>();
        for (Mouvement m : mouvements) {
            if (Compte.natureDe(m.compte()) != Compte.Nature.BILAN) {
                continue;
            }
            String tiers = lettrable.test(m.compte()) ? m.tiers() : "";
            String cle = m.compte() + "\u0000" + tiers;
            soldes.merge(cle, m.solde(), Long::sum);
            if (!tiers.isEmpty()) {
                noms.put(tiers, m.tiersNom());
            }
        }
        List<LigneEcriture> lignes = new ArrayList<>();
        String texte = "À-nouveaux " + annee;
        soldes.forEach((cle, solde) -> {
            if (solde == 0) {
                return;
            }
            String[] p = cle.split("\u0000", -1);
            lignes.add(new LigneEcriture(p[0], p[1], noms.getOrDefault(p[1], ""), texte, Math.max(0, solde),
                    Math.max(0, -solde), ""));
        });
        long r = resultat(mouvements);
        if (r > 0) {
            lignes.add(LigneEcriture.credit(BENEFICE, r, "Résultat " + (annee - 1) + " en instance d'affectation"));
        } else if (r < 0) {
            lignes.add(LigneEcriture.debit(PERTE, -r, "Résultat " + (annee - 1) + " en instance d'affectation"));
        }
        return lignes;
    }
}
