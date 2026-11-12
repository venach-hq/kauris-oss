package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un client de l'entreprise : un particulier ou une entreprise (avec ses numéros NIF/IFU et RCCM, qui figurent
 * sur les factures). {@code delaiPaiement} : nombre de jours entre la facture et son échéance (0 = comptant).
 */
public final class Client {

    public enum Type {
        ENTREPRISE("Entreprise"), PARTICULIER("Particulier");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** L'état complet, pour le stockage et les formulaires. */
    public record Etat(UUID id, UUID tenantId, Type type, String nom, String contact, String email, String telephone,
                       String adresse, String ville, String pays, String nif, String rccm, int delaiPaiement,
                       String notes, boolean actif, Instant creeLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final Instant creeLe;
    private Type type;
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

    private Client(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.actif = e.actif();
        definir(e);
    }

    public static Client creer(UUID tenantId, Etat saisie, Instant maintenant) {
        return new Client(new Etat(UUID.randomUUID(), tenantId, saisie.type(), saisie.nom(), saisie.contact(),
                saisie.email(), saisie.telephone(), saisie.adresse(), saisie.ville(), saisie.pays(), saisie.nif(),
                saisie.rccm(), saisie.delaiPaiement(), saisie.notes(), true, maintenant));
    }

    public static Client reconstituer(Etat etat) {
        return new Client(etat);
    }

    /** Remplace les informations du client (pas son identifiant, ni son état actif). */
    public void definir(Etat e) {
        this.type = e.type() == null ? Type.ENTREPRISE : e.type();
        this.nom = Saisie.nom(e.nom(), "Nom du client", 150);
        this.contact = texte(e.contact(), "Contact", 120);
        this.email = e.email() == null || e.email().isBlank() ? "" : Saisie.email(e.email());
        this.telephone = texte(e.telephone(), "Téléphone", 40);
        this.adresse = texte(e.adresse(), "Adresse", 190);
        this.ville = texte(e.ville(), "Ville", 80);
        this.pays = texte(e.pays(), "Pays", 60);
        this.nif = texte(e.nif(), "NIF / IFU", 60);
        this.rccm = texte(e.rccm(), "RCCM", 60);
        if (e.delaiPaiement() < 0 || e.delaiPaiement() > 365) {
            throw new ErreurMetier("Délai de paiement : entre 0 et 365 jours.");
        }
        this.delaiPaiement = e.delaiPaiement();
        this.notes = texte(e.notes(), "Notes", 1000);
    }

    static String texte(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : valeur.strip();
        if (propre.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return propre;
    }

    public void archiver() {
        this.actif = false;
    }

    public void reactiver() {
        this.actif = true;
    }

    /** Ce qui est imprimé sur un document, figé au moment où il est émis. */
    public ClientFige fige() {
        return new ClientFige(id, type, nom, contact, email, telephone, adresse, ville, pays, nif, rccm);
    }

    public Etat etat() {
        return new Etat(id, tenantId, type, nom, contact, email, telephone, adresse, ville, pays, nif, rccm,
                delaiPaiement, notes, actif, creeLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public Type type() { return type; }

    public String nom() { return nom; }

    public String email() { return email; }

    public String telephone() { return telephone; }

    public String ville() { return ville; }

    public String nif() { return nif; }

    public int delaiPaiement() { return delaiPaiement; }

    public boolean actif() { return actif; }
}
