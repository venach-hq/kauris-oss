package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Une ligne d'un document de vente. Les prix sont en plus petite unité de la monnaie de l'entreprise (le franc
 * pour le F CFA) ; la quantité a trois décimales au plus ; remise et TVA sont des pourcentages à deux décimales.
 *
 * @param articleId l'article du catalogue d'où vient la ligne, ou null pour une ligne libre
 */
public record Ligne(UUID articleId, String designation, BigDecimal quantite, String unite, long prixUnitaire,
                    BigDecimal remise, BigDecimal tauxTva) {

    public static final int DESIGNATION_MAX = 500;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public Ligne {
        designation = designation == null ? "" : designation.strip();
        if (designation.isEmpty()) {
            throw new ErreurMetier("Chaque ligne a une désignation.");
        }
        if (designation.length() > DESIGNATION_MAX) {
            throw new ErreurMetier("Désignation : " + DESIGNATION_MAX + " caractères au plus.");
        }
        if (quantite == null || quantite.signum() <= 0) {
            throw new ErreurMetier("Quantité : un nombre positif (ligne « " + designation + " »).");
        }
        if (quantite.scale() > 3) {
            quantite = quantite.setScale(3, RoundingMode.HALF_UP);
        }
        quantite = quantite.stripTrailingZeros();
        if (quantite.scale() < 0) {
            quantite = quantite.setScale(0);
        }
        unite = unite == null ? "" : unite.strip();
        if (unite.length() > 20) {
            throw new ErreurMetier("Unité : 20 caractères au plus.");
        }
        if (prixUnitaire < 0) {
            throw new ErreurMetier("Prix unitaire : un montant positif ou nul.");
        }
        remise = pourcentage(remise, "Remise");
        tauxTva = pourcentage(tauxTva, "Taux de TVA");
    }

    static BigDecimal pourcentage(BigDecimal valeur, String champ) {
        BigDecimal v = valeur == null ? BigDecimal.ZERO : valeur.setScale(2, RoundingMode.HALF_UP);
        if (v.signum() < 0 || v.compareTo(CENT) > 0) {
            throw new ErreurMetier(champ + " : un pourcentage entre 0 et 100.");
        }
        return v;
    }

    /** Quantité × prix unitaire, remise déduite, arrondi à l'unité. */
    public long montantHt() {
        BigDecimal brut = quantite.multiply(BigDecimal.valueOf(prixUnitaire));
        BigDecimal net = brut.multiply(CENT.subtract(remise)).divide(CENT, 0, RoundingMode.HALF_UP);
        return net.longValueExact();
    }
}
