package com.venach.kauris.kernel.connexion.local.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.organisation.domain.Identite;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Compte du mode de connexion « local » (Kauris installé seul, sans account.venach.com) : un email et un
 * mot de passe, dont seule l'empreinte est gardée. Il ne sert qu'à prouver qui se connecte ; les droits
 * restent ceux du membre relié.
 */
public final class CompteLocal {

    public static final int MOT_DE_PASSE_MIN = 10;
    public static final int MOT_DE_PASSE_MAX = 128;

    private final UUID id;
    private final UUID tenantId;
    private final String email;
    private final Instant creeLe;
    private String empreinteMotDePasse;

    private CompteLocal(UUID id, UUID tenantId, String email, String empreinteMotDePasse, Instant creeLe) {
        this.id = Objects.requireNonNull(id, "id requis");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant requis");
        this.email = Saisie.email(email);
        this.creeLe = Objects.requireNonNull(creeLe, "date de création requise");
        this.empreinteMotDePasse = empreinteValide(empreinteMotDePasse);
    }

    public static CompteLocal creer(UUID id, UUID tenantId, String email, String empreinteMotDePasse,
                                    Instant maintenant) {
        return new CompteLocal(id, tenantId, email, empreinteMotDePasse, maintenant);
    }

    /** Reconstitution depuis le stockage. */
    public static CompteLocal reconstituer(UUID id, UUID tenantId, String email, String empreinteMotDePasse,
                                           Instant creeLe) {
        return new CompteLocal(id, tenantId, email, empreinteMotDePasse, creeLe);
    }

    /** Règle commune à tous les mots de passe locaux, contrôlée avant de calculer l'empreinte. */
    public static void verifierMotDePasse(String motDePasse, String email) {
        if (motDePasse == null || motDePasse.length() < MOT_DE_PASSE_MIN || motDePasse.length() > MOT_DE_PASSE_MAX) {
            throw new ErreurMetier("Mot de passe : entre " + MOT_DE_PASSE_MIN + " et " + MOT_DE_PASSE_MAX
                    + " caractères.");
        }
        if (email != null && motDePasse.equalsIgnoreCase(email.strip())) {
            throw new ErreurMetier("Le mot de passe ne peut pas être l'adresse email.");
        }
    }

    public void changerEmpreinte(String nouvelleEmpreinte) {
        this.empreinteMotDePasse = empreinteValide(nouvelleEmpreinte);
    }

    private static String empreinteValide(String empreinte) {
        if (empreinte == null || empreinte.isBlank()) {
            throw new IllegalArgumentException("Empreinte du mot de passe requise");
        }
        return empreinte;
    }

    /** L'identité que ce compte donne à la personne connectée. */
    public Identite identite() {
        return Identite.locale(id.toString());
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String empreinteMotDePasse() {
        return empreinteMotDePasse;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
