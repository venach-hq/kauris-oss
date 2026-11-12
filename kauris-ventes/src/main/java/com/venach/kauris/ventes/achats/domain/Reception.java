package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un bon de réception (BR-AAAA-NNNN) : ce qui est arrivé d'une commande, dans quel dépôt. Les articles suivis
 * entrent en stock au prix de la commande. Il ne se modifie pas.
 *
 * @param reference numéro du bon de livraison du fournisseur
 */
public record Reception(UUID id, UUID tenantId, String numero, UUID commandeId, String commandeNumero,
                        UUID fournisseurId, String fournisseurNom, LocalDate date, UUID depotId, String reference,
                        List<LigneRecue> lignes, UUID creePar, Instant creeLe) {

    /** Une ligne reçue : rang dans la commande, quantité, coût unitaire retenu pour le stock. */
    public record LigneRecue(int rang, UUID articleId, String designation, BigDecimal quantite, String unite,
                             long coutUnitaire) {
    }

    public Reception {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(numero);
        Objects.requireNonNull(commandeId);
        Objects.requireNonNull(date);
        Objects.requireNonNull(depotId);
        Objects.requireNonNull(creeLe);
        reference = Textes.texte(reference, "Bon de livraison", 60);
        lignes = List.copyOf(lignes);
        if (lignes.isEmpty()) {
            throw new ErreurMetier("Indiquez au moins une quantité reçue.");
        }
    }
}
