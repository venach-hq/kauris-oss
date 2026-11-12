package com.venach.kauris.ventes.stock.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Le stock d'un article, tous dépôts confondus : quantité et valeur au coût moyen unitaire pondéré (CMUP). Chaque
 * entrée recalcule le coût moyen ; une sortie part au coût moyen du moment. Une quantité négative est possible
 * (vente enregistrée avant la réception) : elle se voit et se corrige par une entrée ou un inventaire.
 *
 * @param dernierCout coût unitaire de la dernière entrée, pour valoriser une sortie quand il n'y a plus de stock
 */
public record Position(UUID tenantId, UUID articleId, BigDecimal quantite, long valeur, long dernierCout) {

    public Position {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(articleId);
        quantite = quantite.setScale(3, RoundingMode.HALF_UP);
    }

    public static Position vide(UUID tenantId, UUID articleId) {
        return new Position(tenantId, articleId, BigDecimal.ZERO, 0, 0);
    }

    /** Coût moyen unitaire pondéré ; à défaut de stock, le dernier coût connu. */
    public long cmup() {
        if (quantite.signum() <= 0) {
            return dernierCout;
        }
        return BigDecimal.valueOf(valeur).divide(quantite, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** La valeur d'une quantité à un coût unitaire, arrondie à l'unité. */
    public static long valeur(BigDecimal quantite, long coutUnitaire) {
        return quantite.multiply(BigDecimal.valueOf(coutUnitaire)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Après une entrée de cette quantité à ce coût unitaire. */
    public Position entrer(BigDecimal q, long coutUnitaire) {
        BigDecimal nouvelle = quantite.add(q);
        long nouvelleValeur = valeur + valeur(q, coutUnitaire);
        if (quantite.signum() < 0) {
            // Le stock était négatif : la partie vendue d'avance est désormais couverte à ce coût.
            nouvelleValeur = valeur(nouvelle.max(BigDecimal.ZERO), coutUnitaire);
        }
        return new Position(tenantId, articleId, nouvelle, nouvelle.signum() == 0 ? 0 : nouvelleValeur, coutUnitaire);
    }

    /** Après une sortie de cette quantité, au coût moyen du moment ({@link #cmup()}). */
    public Position sortir(BigDecimal q) {
        BigDecimal nouvelle = quantite.subtract(q);
        long cout = cmup();
        long nouvelleValeur = nouvelle.signum() <= 0 ? valeur(nouvelle, cout) : valeur - valeur(q, cout);
        return new Position(tenantId, articleId, nouvelle, nouvelle.signum() == 0 ? 0 : nouvelleValeur,
                dernierCout == 0 ? cout : dernierCout);
    }
}
