package com.venach.kauris.paie.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Un barème progressif : chaque taux ne s'applique qu'à la part du revenu comprise dans sa tranche.
 *
 * @param tranches dans l'ordre ; la dernière a une limite {@link Long#MAX_VALUE}
 */
public record Bareme(String nom, List<Tranche> tranches) {

    /** Jusqu'à {@code jusqua} inclus, le taux est {@code taux} pour cent. */
    public record Tranche(long jusqua, BigDecimal taux) {

        static Tranche t(long jusqua, String taux) {
            return new Tranche(jusqua, new BigDecimal(taux));
        }
    }

    public Bareme {
        tranches = List.copyOf(tranches);
        long avant = 0;
        for (Tranche t : tranches) {
            if (t.jusqua() <= avant) {
                throw new IllegalArgumentException("Tranches dans le désordre : " + nom);
            }
            avant = t.jusqua();
        }
        if (avant != Long.MAX_VALUE) {
            throw new IllegalArgumentException("La dernière tranche n'a pas de limite : " + nom);
        }
    }

    /**
     * IRPP du Togo, barème annuel (article 74 du CGI, en vigueur en 2026) : 0 % jusqu'à 900 000, puis 3, 10, 15,
     * 20, 25, 30 et 35 % au-delà de 20 000 000.
     */
    public static final Bareme IRPP_TOGO = new Bareme("IRPP Togo (annuel)", List.of(
            Tranche.t(900_000, "0"),
            Tranche.t(3_000_000, "3"),
            Tranche.t(6_000_000, "10"),
            Tranche.t(9_000_000, "15"),
            Tranche.t(12_000_000, "20"),
            Tranche.t(15_000_000, "25"),
            Tranche.t(20_000_000, "30"),
            Tranche.t(Long.MAX_VALUE, "35")));

    /**
     * ITS de Côte d'Ivoire, barème mensuel de la réforme (en vigueur depuis le 1er janvier 2024) : 0 % jusqu'à
     * 75 000, puis 16, 21, 24, 28 et 32 % au-delà de 8 000 000.
     */
    public static final Bareme ITS_COTE_D_IVOIRE = new Bareme("ITS Côte d'Ivoire (mensuel)", List.of(
            Tranche.t(75_000, "0"),
            Tranche.t(240_000, "16"),
            Tranche.t(800_000, "21"),
            Tranche.t(2_400_000, "24"),
            Tranche.t(8_000_000, "28"),
            Tranche.t(Long.MAX_VALUE, "32")));

    /** L'impôt sur ce revenu, arrondi au franc inférieur. */
    public long impot(long revenu) {
        if (revenu <= 0) {
            return 0;
        }
        BigDecimal total = BigDecimal.ZERO;
        long bas = 0;
        for (Tranche t : tranches) {
            long haut = Math.min(revenu, t.jusqua());
            if (haut > bas) {
                total = total.add(BigDecimal.valueOf(haut - bas).multiply(t.taux()));
            }
            if (revenu <= t.jusqua()) {
                break;
            }
            bas = t.jusqua();
        }
        return total.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValue();
    }
}
