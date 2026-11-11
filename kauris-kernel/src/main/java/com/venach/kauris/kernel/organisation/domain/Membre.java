package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Une personne dans une organisation, avec ses rôles. La même personne peut être membre de plusieurs
 * organisations : elle y a alors un {@code Membre} par organisation, reliés par la même {@link Identite}.
 * <p>
 * Un membre invité n'a pas encore d'identité : elle lui est rattachée à sa première connexion.
 */
public final class Membre {

    public static final int NOM_MAX = 120;

    /** État complet, pour la reconstitution depuis le stockage. */
    public record Etat(UUID id, UUID tenantId, Identite identite, String email, String nom, StatutMembre statut,
                       Set<AttributionRole> roles, Instant creeLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private Identite identite;
    private String email;
    private String nom;
    private StatutMembre statut;
    private Set<AttributionRole> roles;

    private Membre(Etat e) {
        this.id = Objects.requireNonNull(e.id(), "id requis");
        this.tenantId = Objects.requireNonNull(e.tenantId(), "tenant requis");
        this.creeLe = Objects.requireNonNull(e.creeLe(), "date de création requise");
        this.identite = e.identite();
        this.email = Saisie.email(e.email());
        this.nom = Saisie.nom(e.nom(), "Nom du membre", NOM_MAX);
        this.statut = Objects.requireNonNull(e.statut(), "statut requis");
        this.roles = rolesValides(e.roles());
        if (statut == StatutMembre.ACTIF && identite == null) {
            throw new IllegalStateException("Un membre actif est toujours relié à une connexion");
        }
    }

    /** Première personne de l'organisation : active tout de suite, propriétaire. */
    public static Membre fondateur(UUID tenantId, Identite identite, String email, String nom, Instant maintenant) {
        Objects.requireNonNull(identite, "identité requise");
        return new Membre(new Etat(UUID.randomUUID(), tenantId, identite, email, nom, StatutMembre.ACTIF,
                Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)), maintenant));
    }

    /** Membre invité par son email : il deviendra actif à sa première connexion. */
    public static Membre inviter(UUID tenantId, String email, String nom, Set<AttributionRole> roles,
                                 Instant maintenant) {
        return new Membre(new Etat(UUID.randomUUID(), tenantId, null, email, nom, StatutMembre.INVITE, roles,
                maintenant));
    }

    public static Membre reconstituer(Etat etat) {
        return new Membre(etat);
    }

    /** Relie le membre invité à la personne qui vient de se connecter. */
    public void rattacher(Identite personne) {
        Objects.requireNonNull(personne, "identité requise");
        if (identite != null && !identite.equals(personne)) {
            throw new ErreurMetier("Ce membre est déjà relié à une autre connexion.");
        }
        this.identite = personne;
        if (statut == StatutMembre.INVITE) {
            this.statut = StatutMembre.ACTIF;
        }
    }

    public void renommer(String nouveauNom) {
        this.nom = Saisie.nom(nouveauNom, "Nom du membre", NOM_MAX);
    }

    public void definirRoles(Set<AttributionRole> nouveaux) {
        this.roles = rolesValides(nouveaux);
    }

    public void desactiver() {
        this.statut = StatutMembre.DESACTIVE;
    }

    /** Retour à l'état d'avant : actif s'il s'est déjà connecté, invité sinon. */
    public void reactiver() {
        if (statut == StatutMembre.DESACTIVE) {
            this.statut = identite != null ? StatutMembre.ACTIF : StatutMembre.INVITE;
        }
    }

    private static Set<AttributionRole> rolesValides(Set<AttributionRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ErreurMetier("Un membre a au moins un rôle.");
        }
        return Set.copyOf(roles);
    }

    // ------------------------------------------------------------------ droits

    public boolean a(Role role) {
        return roles.stream().anyMatch(r -> r.role() == role);
    }

    public boolean estProprietaire() {
        return a(Role.PROPRIETAIRE);
    }

    /** Propriétaire ou administrateur : gère établissements, membres et rôles. */
    public boolean administre() {
        return roles.stream().anyMatch(r -> r.role().administre());
    }

    /** Vrai si un des rôles du membre permet de modifier les données de cet établissement. */
    public boolean peutModifier(UUID etablissementId) {
        return roles.stream().anyMatch(r -> r.role().peutModifier() && r.couvre(etablissementId));
    }

    /** Vrai si le membre a un rôle, quel qu'il soit, sur cet établissement. */
    public boolean peutConsulter(UUID etablissementId) {
        return roles.stream().anyMatch(r -> r.couvre(etablissementId));
    }

    // ------------------------------------------------------------------ lecture

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Optional<Identite> identite() {
        return Optional.ofNullable(identite);
    }

    public String email() {
        return email;
    }

    public String nom() {
        return nom;
    }

    public StatutMembre statut() {
        return statut;
    }

    public boolean estActif() {
        return statut == StatutMembre.ACTIF;
    }

    public Set<AttributionRole> roles() {
        return roles;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
