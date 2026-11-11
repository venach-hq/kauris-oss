package com.venach.kauris.kernel.notification.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Un message court adressé à un membre d'une organisation, gardé jusqu'à ce qu'il le lise. */
public final class Notification {

    public static final int TITRE_MAX = 120;
    public static final int MESSAGE_MAX = 500;
    public static final int LIEN_MAX = 255;

    private final UUID id;
    private final UUID tenantId;
    private final UUID membreId;
    private final String titre;
    private final String message;
    private final String lien;
    private final Instant creeLe;
    private Instant lueLe;

    private Notification(UUID id, UUID tenantId, UUID membreId, String titre, String message, String lien,
                         Instant creeLe, Instant lueLe) {
        this.id = Objects.requireNonNull(id, "id requis");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant requis");
        this.membreId = Objects.requireNonNull(membreId, "membre requis");
        this.titre = Saisie.nom(titre, "Titre", TITRE_MAX);
        this.message = messageValide(message);
        this.lien = lienValide(lien);
        this.creeLe = Objects.requireNonNull(creeLe, "date de création requise");
        this.lueLe = lueLe;
    }

    public static Notification creer(UUID tenantId, UUID membreId, String titre, String message, String lien,
                                     Instant maintenant) {
        return new Notification(UUID.randomUUID(), tenantId, membreId, titre, message, lien, maintenant, null);
    }

    /** Reconstitution depuis le stockage. */
    public static Notification reconstituer(UUID id, UUID tenantId, UUID membreId, String titre, String message,
                                            String lien, Instant creeLe, Instant lueLe) {
        return new Notification(id, tenantId, membreId, titre, message, lien, creeLe, lueLe);
    }

    public void marquerLue(Instant maintenant) {
        if (lueLe == null) {
            this.lueLe = Objects.requireNonNull(maintenant);
        }
    }

    private static String messageValide(String message) {
        String propre = message == null ? "" : message.strip();
        if (propre.length() > MESSAGE_MAX) {
            throw new ErreurMetier("Message : " + MESSAGE_MAX + " caractères au plus.");
        }
        return propre;
    }

    /** Un lien ne sort jamais de l'application : chemin qui commence par un seul « / », ou rien. */
    private static String lienValide(String lien) {
        if (lien == null || lien.isBlank()) {
            return null;
        }
        if (!lien.startsWith("/") || lien.startsWith("//") || lien.contains("\\") || lien.length() > LIEN_MAX) {
            throw new IllegalArgumentException("Lien de notification invalide : " + lien);
        }
        return lien;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID membreId() {
        return membreId;
    }

    public String titre() {
        return titre;
    }

    public String message() {
        return message;
    }

    public Optional<String> lien() {
        return Optional.ofNullable(lien);
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Optional<Instant> lueLe() {
        return Optional.ofNullable(lueLe);
    }

    public boolean estLue() {
        return lueLe != null;
    }
}
