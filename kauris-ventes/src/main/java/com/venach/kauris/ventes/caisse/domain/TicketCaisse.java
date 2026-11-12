package com.venach.kauris.ventes.caisse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un ticket encaissé dans une caisse : la facture émise et réglée, et comment elle a été payée.
 *
 * @param especes part du total payée en espèces
 * @param recu    espèces données par le client (au moins {@code especes})
 * @param rendu   monnaie rendue : {@code recu - especes}
 */
public record TicketCaisse(UUID id, UUID tenantId, UUID sessionId, UUID documentId, String numero, Instant creeLe,
                           long total, long especes, long mobile, long carte, long recu, long rendu,
                           String referenceMobile) {

    public TicketCaisse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(documentId);
        referenceMobile = referenceMobile == null ? "" : referenceMobile.strip();
    }
}
