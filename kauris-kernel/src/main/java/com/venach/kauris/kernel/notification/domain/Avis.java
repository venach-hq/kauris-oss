package com.venach.kauris.kernel.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ce qui voyage quand une notification vient d'être créée : de quoi prévenir tout de suite le membre s'il a
 * Kauris ouvert. La notification elle-même reste en base ; perdre un avis ne perd rien.
 *
 * @param lien chemin dans l'application, ou nul
 */
public record Avis(UUID notificationId, UUID tenantId, UUID membreId, String titre, String message, String lien,
                   Instant creeLe) {

    public static Avis de(Notification n) {
        return new Avis(n.id(), n.tenantId(), n.membreId(), n.titre(), n.message(), n.lien().orElse(null),
                n.creeLe());
    }
}
