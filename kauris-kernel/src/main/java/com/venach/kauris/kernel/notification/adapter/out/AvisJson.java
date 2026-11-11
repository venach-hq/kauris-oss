package com.venach.kauris.kernel.notification.adapter.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venach.kauris.kernel.notification.domain.Avis;

import java.time.Instant;
import java.util.UUID;

/**
 * Forme JSON d'un avis, la même dans la boîte d'envoi et sur RabbitMQ. Que des textes : elle ne dépend
 * d'aucun réglage de Jackson, et un champ ajouté plus tard n'empêche pas de lire les anciens messages.
 */
public record AvisJson(String notificationId, String tenantId, String membreId, String titre, String message,
                       String lien, String creeLe) {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static String ecrire(Avis a) {
        try {
            return JSON.writeValueAsString(new AvisJson(a.notificationId().toString(), a.tenantId().toString(),
                    a.membreId().toString(), a.titre(), a.message(), a.lien(), a.creeLe().toString()));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Avis impossible à écrire en JSON", impossible);
        }
    }

    /** @throws IllegalArgumentException si le texte n'est pas un avis lisible */
    public static Avis lire(String json) {
        try {
            AvisJson a = JSON.readValue(json, AvisJson.class);
            return new Avis(UUID.fromString(a.notificationId()), UUID.fromString(a.tenantId()),
                    UUID.fromString(a.membreId()), a.titre(), a.message(), a.lien(), Instant.parse(a.creeLe()));
        } catch (JsonProcessingException | RuntimeException illisible) {
            throw new IllegalArgumentException("Avis illisible", illisible);
        }
    }
}
