package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un établissement d'une organisation (siège, agence, boutique…). Chaque organisation en a au moins un,
 * et exactement un établissement principal, qui ne peut pas être fermé.
 */
public final class Etablissement {

    public static final int NOM_MAX = 120;
    public static final String NOM_PAR_DEFAUT = "Siège";

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private String nom;
    private boolean principal;
    private boolean ouvert;

    private Etablissement(UUID id, UUID tenantId, String nom, boolean principal, boolean ouvert, Instant creeLe) {
        this.id = Objects.requireNonNull(id, "id requis");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant requis");
        this.creeLe = Objects.requireNonNull(creeLe, "date de création requise");
        this.nom = Saisie.nom(nom, "Nom de l'établissement", NOM_MAX);
        this.principal = principal;
        this.ouvert = ouvert;
        if (principal && !ouvert) {
            throw new IllegalStateException("L'établissement principal ne peut pas être fermé");
        }
    }

    public static Etablissement creer(UUID tenantId, String nom, boolean principal, Instant maintenant) {
        return new Etablissement(UUID.randomUUID(), tenantId, nom, principal, true, maintenant);
    }

    /** Reconstitution depuis le stockage. */
    public static Etablissement reconstituer(UUID id, UUID tenantId, String nom, boolean principal, boolean ouvert,
                                             Instant creeLe) {
        return new Etablissement(id, tenantId, nom, principal, ouvert, creeLe);
    }

    public void renommer(String nouveauNom) {
        this.nom = Saisie.nom(nouveauNom, "Nom de l'établissement", NOM_MAX);
    }

    public void fermer() {
        if (principal) {
            throw new ErreurMetier("L'établissement principal ne peut pas être fermé : "
                    + "choisissez d'abord un autre établissement principal.");
        }
        this.ouvert = false;
    }

    public void rouvrir() {
        this.ouvert = true;
    }

    public void devenirPrincipal() {
        if (!ouvert) {
            throw new ErreurMetier("Un établissement fermé ne peut pas devenir principal.");
        }
        this.principal = true;
    }

    public void cesserDEtrePrincipal() {
        this.principal = false;
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

    public boolean estPrincipal() {
        return principal;
    }

    public boolean estOuvert() {
        return ouvert;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
