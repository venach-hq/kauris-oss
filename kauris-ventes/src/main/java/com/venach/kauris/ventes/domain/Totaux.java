package com.venach.kauris.ventes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Les montants d'un document : total des lignes, remise globale, base et TVA par taux, total TTC.
 * La remise globale s'applique à chaque taux en proportion ; la TVA se calcule par taux, sur la base arrondie.
 *
 * @param lignesHt       somme des montants hors taxes des lignes
 * @param remiseGlobale  montant de la remise globale
 * @param totalHt        lignesHt − remiseGlobale
 * @param tva            une entrée par taux de TVA présent, du plus petit au plus grand
 */
public record Totaux(long lignesHt, long remiseGlobale, long totalHt, List<LigneTva> tva, long totalTva,
                     long totalTtc) {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** La TVA d'un taux : sur quelle base, combien. */
    public record LigneTva(BigDecimal taux, long base, long montant) {
    }

    public static Totaux de(List<Ligne> lignes, BigDecimal remiseGlobalePourcent) {
        BigDecimal remise = Ligne.pourcentage(remiseGlobalePourcent, "Remise globale");
        Map<BigDecimal, Long> htParTaux = new TreeMap<>();
        long lignesHt = 0;
        for (Ligne l : lignes) {
            long ht = l.montantHt();
            lignesHt += ht;
            htParTaux.merge(l.tauxTva(), ht, Long::sum);
        }
        List<LigneTva> tva = new ArrayList<>();
        long totalHt = 0;
        long totalTva = 0;
        for (Map.Entry<BigDecimal, Long> e : htParTaux.entrySet()) {
            long ht = e.getValue();
            long base = ht - BigDecimal.valueOf(ht).multiply(remise).divide(CENT, 0, RoundingMode.HALF_UP)
                    .longValueExact();
            long montant = BigDecimal.valueOf(base).multiply(e.getKey()).divide(CENT, 0, RoundingMode.HALF_UP)
                    .longValueExact();
            tva.add(new LigneTva(e.getKey(), base, montant));
            totalHt += base;
            totalTva += montant;
        }
        return new Totaux(lignesHt, lignesHt - totalHt, totalHt, List.copyOf(tva), totalTva, totalHt + totalTva);
    }

    /** Vrai si une ligne porte de la TVA. */
    public boolean avecTva() {
        return totalTva > 0 || tva.stream().anyMatch(t -> t.taux().signum() > 0);
    }
}
