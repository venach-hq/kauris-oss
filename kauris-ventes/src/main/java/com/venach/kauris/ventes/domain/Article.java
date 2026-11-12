package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un article ou une prestation du catalogue : ce que l'entreprise vend, à quel prix hors taxes, dans quelle unité
 * et avec quel taux de TVA. Les lignes des documents en sont des copies : changer un prix ne change pas les
 * documents déjà faits.
 */
public final class Article {

    public enum Type {
        BIEN("Article"), SERVICE("Prestation");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /**
     * @param suiviStock  vrai si les quantités de l'article sont suivies en stock (un article, pas une prestation)
     * @param seuilAlerte quantité totale en dessous de laquelle le stock est signalé bas ; null = pas d'alerte
     */
    public record Etat(UUID id, UUID tenantId, Type type, String reference, String designation, String unite,
                       long prixHt, BigDecimal tauxTva, boolean suiviStock, BigDecimal seuilAlerte, boolean actif,
                       Instant creeLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private Type type;
    private String reference;
    private String designation;
    private String unite;
    private long prixHt;
    private BigDecimal tauxTva;
    private boolean suiviStock;
    private BigDecimal seuilAlerte;
    private boolean actif;

    private Article(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.actif = e.actif();
        definir(e);
    }

    public static Article creer(UUID tenantId, Etat saisie, Instant maintenant) {
        return new Article(new Etat(UUID.randomUUID(), tenantId, saisie.type(), saisie.reference(),
                saisie.designation(), saisie.unite(), saisie.prixHt(), saisie.tauxTva(), saisie.suiviStock(),
                saisie.seuilAlerte(), true, maintenant));
    }

    public static Article reconstituer(Etat etat) {
        return new Article(etat);
    }

    public void definir(Etat e) {
        this.type = e.type() == null ? Type.BIEN : e.type();
        this.reference = Client.texte(e.reference(), "Référence", 40);
        this.designation = Saisie.nom(e.designation(), "Désignation", 200);
        this.unite = Client.texte(e.unite(), "Unité", 20);
        if (e.prixHt() < 0) {
            throw new ErreurMetier("Prix : un montant positif ou nul.");
        }
        this.prixHt = e.prixHt();
        this.tauxTva = Ligne.pourcentage(e.tauxTva(), "Taux de TVA");
        if (e.suiviStock() && this.type == Type.SERVICE) {
            throw new ErreurMetier("Une prestation ne se suit pas en stock.");
        }
        this.suiviStock = e.suiviStock();
        if (e.seuilAlerte() != null && e.seuilAlerte().signum() < 0) {
            throw new ErreurMetier("Seuil d'alerte : une quantité positive ou nulle.");
        }
        this.seuilAlerte = e.suiviStock() ? e.seuilAlerte() : null;
    }

    public void archiver() {
        this.actif = false;
    }

    public void reactiver() {
        this.actif = true;
    }

    /** Une ligne de document à partir de l'article. */
    public Ligne ligne(BigDecimal quantite) {
        return new Ligne(id, designation, quantite, unite, prixHt, BigDecimal.ZERO, tauxTva);
    }

    public Etat etat() {
        return new Etat(id, tenantId, type, reference, designation, unite, prixHt, tauxTva, suiviStock, seuilAlerte,
                actif, creeLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public Type type() { return type; }

    public String reference() { return reference; }

    public String designation() { return designation; }

    public String unite() { return unite; }

    public long prixHt() { return prixHt; }

    public BigDecimal tauxTva() { return tauxTva; }

    public boolean suiviStock() { return suiviStock; }

    public BigDecimal seuilAlerte() { return seuilAlerte; }

    public boolean actif() { return actif; }
}
