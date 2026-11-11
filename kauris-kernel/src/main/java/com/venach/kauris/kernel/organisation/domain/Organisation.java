package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une organisation cliente (une entreprise). C'est le « tenant » : son identifiant est le {@code tenant_id}
 * porté par toutes les données de Kauris.
 */
public final class Organisation {

    public static final int NOM_MAX = 120;

    private final UUID id;
    private final Instant creeLe;
    private String nom;
    private StatutOrganisation statut;

    private Organisation(UUID id, String nom, StatutOrganisation statut, Instant creeLe) {
        this.id = Objects.requireNonNull(id, "id requis");
        this.creeLe = Objects.requireNonNull(creeLe, "date de création requise");
        this.nom = Saisie.nom(nom, "Nom de l'organisation", NOM_MAX);
        this.statut = Objects.requireNonNull(statut, "statut requis");
    }

    public static Organisation creer(String nom, Instant maintenant) {
        return new Organisation(UUID.randomUUID(), nom, StatutOrganisation.ACTIVE, maintenant);
    }

    /** Reconstitution depuis le stockage. */
    public static Organisation reconstituer(UUID id, String nom, StatutOrganisation statut, Instant creeLe) {
        return new Organisation(id, nom, statut, creeLe);
    }

    public void renommer(String nouveauNom) {
        this.nom = Saisie.nom(nouveauNom, "Nom de l'organisation", NOM_MAX);
    }

    public void suspendre() {
        this.statut = StatutOrganisation.SUSPENDUE;
    }

    public void reactiver() {
        this.statut = StatutOrganisation.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    /** Identique à {@link #id()} : l'organisation est son propre tenant. */
    public UUID tenantId() {
        return id;
    }

    public String nom() {
        return nom;
    }

    public StatutOrganisation statut() {
        return statut;
    }

    public boolean estActive() {
        return statut == StatutOrganisation.ACTIVE;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
