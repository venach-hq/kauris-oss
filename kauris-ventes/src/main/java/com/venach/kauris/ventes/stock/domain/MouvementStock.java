package com.venach.kauris.ventes.stock.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Une ligne du journal du stock. Elle ne se modifie ni ne se supprime : une erreur se corrige par un mouvement
 * en sens inverse ou un inventaire.
 *
 * @param quantite     toujours positive ; le sens vient du type
 * @param coutUnitaire coût unitaire retenu (coût d'achat pour une entrée, CMUP pour une sortie)
 * @param valeur       quantité × coût, arrondi
 * @param reference    numéro du document d'origine (facture, avoir, bon de réception…) ou pièce saisie
 * @param documentId   le document d'origine, s'il est dans Kauris
 * @param lienId       l'autre moitié d'un transfert
 */
public record MouvementStock(UUID id, UUID tenantId, String numero, LocalDate date, TypeMouvement type,
                             UUID articleId, UUID depotId, BigDecimal quantite, long coutUnitaire, long valeur,
                             String libelle, String reference, UUID documentId, UUID lienId, UUID creePar,
                             Instant creeLe) {

    public MouvementStock {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(numero);
        Objects.requireNonNull(date);
        Objects.requireNonNull(type);
        Objects.requireNonNull(articleId);
        Objects.requireNonNull(depotId);
        Objects.requireNonNull(creeLe);
        if (quantite == null || quantite.signum() <= 0) {
            throw new ErreurMetier("Quantité : un nombre positif.");
        }
        if (quantite.scale() > 3) {
            quantite = quantite.setScale(3, RoundingMode.HALF_UP);
        }
        if (coutUnitaire < 0) {
            throw new ErreurMetier("Coût unitaire : un montant positif ou nul.");
        }
        libelle = coupe(libelle, 200);
        reference = coupe(reference, 60);
    }

    private static String coupe(String texte, int max) {
        String propre = texte == null ? "" : texte.strip();
        return propre.length() > max ? propre.substring(0, max) : propre;
    }

    /** La quantité avec son signe : positive pour une entrée, négative pour une sortie. */
    public BigDecimal effet() {
        return type.entree() ? quantite : quantite.negate();
    }
}
