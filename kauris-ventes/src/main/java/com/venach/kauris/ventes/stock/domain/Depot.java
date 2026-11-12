package com.venach.kauris.ventes.stock.domain;

import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Un lieu où l'entreprise garde des marchandises : magasin, entrepôt, boutique. Le principal reçoit les ventes. */
public final class Depot {

    public static final String NOM_PAR_DEFAUT = "Magasin principal";

    public record Etat(UUID id, UUID tenantId, String nom, boolean principal, boolean actif, Instant creeLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private String nom;
    private boolean principal;
    private boolean actif;

    private Depot(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.nom = Saisie.nom(e.nom(), "Nom du dépôt", 80);
        this.principal = e.principal();
        this.actif = e.actif();
    }

    public static Depot nouveau(UUID tenantId, String nom, boolean principal, Instant maintenant) {
        return new Depot(new Etat(UUID.randomUUID(), tenantId, nom, principal, true, maintenant));
    }

    public static Depot reconstituer(Etat e) {
        return new Depot(e);
    }

    public void renommer(String nouveau) {
        this.nom = Saisie.nom(nouveau, "Nom du dépôt", 80);
    }

    public void devenirPrincipal(boolean oui) {
        this.principal = oui;
    }

    public void archiver() {
        this.actif = false;
        this.principal = false;
    }

    public void reactiver() {
        this.actif = true;
    }

    public Etat etat() {
        return new Etat(id, tenantId, nom, principal, actif, creeLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String nom() { return nom; }

    public boolean principal() { return principal; }

    public boolean actif() { return actif; }
}
