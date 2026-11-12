package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Un fournisseur de l'entreprise. {@code delaiPaiement} : jours accordés pour payer ses factures (0 = comptant). */
public final class Fournisseur {

    public record Etat(UUID id, UUID tenantId, String nom, String contact, String email, String telephone,
                       String adresse, String ville, String pays, String nif, String rccm, int delaiPaiement,
                       String notes, boolean actif, Instant creeLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private String nom;
    private String contact;
    private String email;
    private String telephone;
    private String adresse;
    private String ville;
    private String pays;
    private String nif;
    private String rccm;
    private int delaiPaiement;
    private String notes;
    private boolean actif;

    private Fournisseur(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.actif = e.actif();
        definir(e);
    }

    public static Fournisseur creer(UUID tenantId, Etat s, Instant maintenant) {
        return new Fournisseur(new Etat(UUID.randomUUID(), tenantId, s.nom(), s.contact(), s.email(), s.telephone(),
                s.adresse(), s.ville(), s.pays(), s.nif(), s.rccm(), s.delaiPaiement(), s.notes(), true, maintenant));
    }

    public static Fournisseur reconstituer(Etat e) {
        return new Fournisseur(e);
    }

    public void definir(Etat e) {
        this.nom = Saisie.nom(e.nom(), "Nom du fournisseur", 150);
        this.contact = Textes.texte(e.contact(), "Contact", 120);
        this.email = e.email() == null || e.email().isBlank() ? "" : Saisie.email(e.email());
        this.telephone = Textes.texte(e.telephone(), "Téléphone", 40);
        this.adresse = Textes.texte(e.adresse(), "Adresse", 190);
        this.ville = Textes.texte(e.ville(), "Ville", 80);
        this.pays = Textes.texte(e.pays(), "Pays", 60);
        this.nif = Textes.texte(e.nif(), "NIF", 60);
        this.rccm = Textes.texte(e.rccm(), "RCCM", 60);
        if (e.delaiPaiement() < 0 || e.delaiPaiement() > 365) {
            throw new ErreurMetier("Délai de paiement : entre 0 et 365 jours.");
        }
        this.delaiPaiement = e.delaiPaiement();
        this.notes = Textes.texte(e.notes(), "Notes", 1000);
    }

    public void archiver(boolean oui) {
        this.actif = !oui;
    }

    public String adresseComplete() {
        return String.join(", ", java.util.stream.Stream.of(adresse, ville, pays).filter(x -> !x.isBlank()).toList());
    }

    public Etat etat() {
        return new Etat(id, tenantId, nom, contact, email, telephone, adresse, ville, pays, nif, rccm, delaiPaiement,
                notes, actif, creeLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String nom() { return nom; }

    public String contact() { return contact; }

    public String email() { return email; }

    public String telephone() { return telephone; }

    public String nif() { return nif; }

    public String rccm() { return rccm; }

    public String ville() { return ville; }

    public int delaiPaiement() { return delaiPaiement; }

    public boolean actif() { return actif; }
}
