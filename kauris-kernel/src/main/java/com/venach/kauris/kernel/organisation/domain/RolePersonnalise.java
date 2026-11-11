package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Un rôle créé par le propriétaire pour un poste de son entreprise (comptable, caissier, responsable RH…),
 * avec les permissions qu'il y coche. Il limite ce que ses titulaires voient et modifient.
 */
public final class RolePersonnalise {

    public static final int NOM_MAX = 60;
    public static final int DESCRIPTION_MAX = 200;

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private String nom;
    private String description;
    private Set<String> permissions;

    private RolePersonnalise(UUID id, UUID tenantId, String nom, String description, Set<String> permissions,
                             Instant creeLe) {
        this.id = Objects.requireNonNull(id, "id requis");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant requis");
        this.creeLe = Objects.requireNonNull(creeLe, "date de création requise");
        definir(nom, description, permissions);
    }

    public static RolePersonnalise creer(UUID tenantId, String nom, String description, Set<String> permissions,
                                         Instant maintenant) {
        return new RolePersonnalise(UUID.randomUUID(), tenantId, nom, description, permissions, maintenant);
    }

    /** Reconstitution depuis le stockage. */
    public static RolePersonnalise reconstituer(UUID id, UUID tenantId, String nom, String description,
                                                Set<String> permissions, Instant creeLe) {
        return new RolePersonnalise(id, tenantId, nom, description, permissions, creeLe);
    }

    public void definir(String nouveauNom, String nouvelleDescription, Set<String> nouvellesPermissions) {
        String propre = nouvelleDescription == null ? "" : nouvelleDescription.strip();
        if (propre.length() > DESCRIPTION_MAX) {
            throw new ErreurMetier("Description : " + DESCRIPTION_MAX + " caractères au plus.");
        }
        this.nom = Saisie.nom(nouveauNom, "Nom du rôle", NOM_MAX);
        this.description = propre;
        this.permissions = nouvellesPermissions == null ? Set.of() : Set.copyOf(nouvellesPermissions);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String nom() {
        return nom;
    }

    public String description() {
        return description;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
