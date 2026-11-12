package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un devis, une facture ou un avoir.
 * <p>
 * Un brouillon se modifie librement et n'a pas de numéro. À l'émission, il reçoit le numéro suivant de l'année
 * (sans trou), le client et les mentions sont figés, et plus rien ne change : une facture émise ne se modifie ni
 * ne se supprime, elle se corrige par un avoir.
 * <p>
 * Pour une facture, {@code regle} est la somme des règlements reçus et {@code credite} celle des avoirs émis sur
 * elle ; le reste à payer en découle. Pour un devis, {@code echeance} est la date de fin de validité ; pour une
 * facture, la date limite de paiement ; un avoir n'en a pas.
 */
public final class DocumentVente {

    public static final int OBJET_MAX = 200;
    public static final int REFERENCE_MAX = 80;
    public static final int NOTES_MAX = 1000;
    public static final int LIGNES_MAX = 200;

    /** Ce qui se saisit dans un brouillon. */
    public record Contenu(LocalDate date, LocalDate echeance, String objet, String reference, List<Ligne> lignes,
                          BigDecimal remiseGlobale, String notes) {

        public Contenu {
            Objects.requireNonNull(date, "date");
            objet = Client.texte(objet, "Objet", OBJET_MAX);
            reference = Client.texte(reference, "Référence du client", REFERENCE_MAX);
            notes = Client.texte(notes, "Notes", NOTES_MAX);
            lignes = lignes == null ? List.of() : List.copyOf(lignes);
            if (lignes.size() > LIGNES_MAX) {
                throw new ErreurMetier("Un document a " + LIGNES_MAX + " lignes au plus.");
            }
            remiseGlobale = Ligne.pourcentage(remiseGlobale, "Remise globale");
            if (echeance != null && echeance.isBefore(date)) {
                throw new ErreurMetier("L'échéance ne peut pas précéder la date du document.");
            }
        }
    }

    /** L'état complet, pour le stockage. */
    public record Etat(UUID id, UUID tenantId, TypeDocument type, String numero, StatutDocument statut,
                       ClientFige client, Contenu contenu, String mentionTva, String conditions, UUID origineId,
                       String origineNumero, long regle, long credite, UUID creePar, Instant creeLe,
                       Instant emisLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final TypeDocument type;
    private final UUID origineId;
    private final String origineNumero;
    private final UUID creePar;
    private final Instant creeLe;
    private String numero;
    private StatutDocument statut;
    private ClientFige client;
    private Contenu contenu;
    private String mentionTva;
    private String conditions;
    private long regle;
    private long credite;
    private Instant emisLe;

    private DocumentVente(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.type = Objects.requireNonNull(e.type());
        this.numero = e.numero();
        this.statut = Objects.requireNonNull(e.statut());
        this.client = Objects.requireNonNull(e.client());
        this.contenu = Objects.requireNonNull(e.contenu());
        this.mentionTva = e.mentionTva() == null ? "" : e.mentionTva();
        this.conditions = e.conditions() == null ? "" : e.conditions();
        this.origineId = e.origineId();
        this.origineNumero = e.origineNumero();
        this.regle = e.regle();
        this.credite = e.credite();
        this.creePar = e.creePar();
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.emisLe = e.emisLe();
    }

    /**
     * Un nouveau brouillon.
     *
     * @param origine le devis d'où vient une facture, ou la facture qu'un avoir corrige ; null sinon
     */
    public static DocumentVente brouillon(UUID tenantId, TypeDocument type, Client client, Contenu contenu,
                                          DocumentVente origine, UUID creePar, Instant maintenant) {
        if (!client.actif()) {
            throw new ErreurMetier("Ce client est archivé : réactivez-le pour lui faire un document.");
        }
        if (type == TypeDocument.AVOIR) {
            if (origine == null || origine.type != TypeDocument.FACTURE || origine.statut != StatutDocument.EMIS) {
                throw new ErreurMetier("Un avoir se fait à partir d'une facture émise.");
            }
            if (!origine.client.id().equals(client.id())) {
                throw new ErreurMetier("Un avoir est pour le client de la facture qu'il corrige.");
            }
        }
        if (type == TypeDocument.FACTURE && origine != null && origine.type != TypeDocument.DEVIS) {
            throw new ErreurMetier("Une facture ne vient que d'un devis.");
        }
        if (type == TypeDocument.DEVIS && origine != null) {
            throw new ErreurMetier("Un devis ne vient d'aucun autre document.");
        }
        DocumentVente d = new DocumentVente(new Etat(UUID.randomUUID(), tenantId, type, null,
                StatutDocument.BROUILLON, client.fige(), contenu, "", "",
                origine == null ? null : origine.id, origine == null ? null : origine.numero, 0, 0, creePar,
                maintenant, null));
        d.verifierEcheance();
        return d;
    }

    public static DocumentVente reconstituer(Etat etat) {
        return new DocumentVente(etat);
    }

    // ------------------------------------------------------------------ brouillon

    /** Change le contenu ou le client d'un brouillon. */
    public void modifier(Client nouveauClient, Contenu nouveau) {
        exigerBrouillon();
        if (!nouveauClient.actif()) {
            throw new ErreurMetier("Ce client est archivé : réactivez-le pour lui faire un document.");
        }
        if (type == TypeDocument.AVOIR && !nouveauClient.id().equals(client.id())) {
            throw new ErreurMetier("Un avoir est pour le client de la facture qu'il corrige.");
        }
        this.client = nouveauClient.fige();
        this.contenu = nouveau;
        verifierEcheance();
    }

    private void verifierEcheance() {
        if (type == TypeDocument.AVOIR && contenu.echeance() != null) {
            this.contenu = new Contenu(contenu.date(), null, contenu.objet(), contenu.reference(), contenu.lignes(),
                    contenu.remiseGlobale(), contenu.notes());
        }
    }

    /**
     * Émet le document : il prend son numéro, et le client, la mention de TVA et les conditions du moment sont
     * figés. Le client passé est relu juste avant (sa fiche a pu changer depuis le brouillon).
     */
    public void emettre(String numeroAttribue, Client clientActuel, String mention, String conditionsDuMoment,
                        Instant maintenant) {
        exigerBrouillon();
        if (contenu.lignes().isEmpty()) {
            throw new ErreurMetier("Ajoutez au moins une ligne avant d'émettre.");
        }
        if (type != TypeDocument.DEVIS && totaux().totalTtc() <= 0) {
            throw new ErreurMetier("Le total doit être supérieur à zéro.");
        }
        if (type == TypeDocument.FACTURE && contenu.echeance() == null) {
            throw new ErreurMetier("Indiquez la date d'échéance de la facture.");
        }
        if (!clientActuel.id().equals(client.id())) {
            throw new IllegalArgumentException("Client différent de celui du brouillon");
        }
        this.numero = Objects.requireNonNull(numeroAttribue);
        this.client = clientActuel.fige();
        this.mentionTva = totaux().avecTva() ? "" : Client.texte(mention, "Mention de TVA", 200);
        this.conditions = type == TypeDocument.FACTURE ? Client.texte(conditionsDuMoment, "Conditions", 1000) : "";
        this.statut = StatutDocument.EMIS;
        this.emisLe = maintenant;
    }

    private void exigerBrouillon() {
        if (statut != StatutDocument.BROUILLON) {
            throw new ErreurMetier(type == TypeDocument.FACTURE
                    ? "Une facture émise ne se modifie plus : corrigez-la par un avoir."
                    : "Ce document est émis : il ne se modifie plus.");
        }
    }

    public boolean modifiable() {
        return statut == StatutDocument.BROUILLON;
    }

    // ------------------------------------------------------------------ devis

    public void accepter() {
        exigerDevis(StatutDocument.EMIS, StatutDocument.REFUSE);
        this.statut = StatutDocument.ACCEPTE;
    }

    public void refuser() {
        exigerDevis(StatutDocument.EMIS, StatutDocument.ACCEPTE);
        this.statut = StatutDocument.REFUSE;
    }

    /** Le devis a donné une facture (au moment où celle-ci est émise). */
    public void marquerFacture() {
        exigerDevis(StatutDocument.EMIS, StatutDocument.ACCEPTE);
        this.statut = StatutDocument.FACTURE;
    }

    public void annulerDevis() {
        exigerDevis(StatutDocument.EMIS, StatutDocument.ACCEPTE, StatutDocument.REFUSE);
        this.statut = StatutDocument.ANNULE;
    }

    /** Un devis émis, accepté ou non encore répondu, se transforme en facture. */
    public boolean facturable() {
        return type == TypeDocument.DEVIS
                && (statut == StatutDocument.EMIS || statut == StatutDocument.ACCEPTE);
    }

    private void exigerDevis(StatutDocument... permis) {
        if (type != TypeDocument.DEVIS) {
            throw new ErreurMetier("Cette action ne concerne que les devis.");
        }
        for (StatutDocument s : permis) {
            if (statut == s) {
                return;
            }
        }
        throw new ErreurMetier("Ce devis est « " + statut.libelle().toLowerCase() + " » : action impossible.");
    }

    // ------------------------------------------------------------------ facture : règlements et avoirs

    /** Ce qui reste à payer : total TTC − règlements − avoirs. Négatif si le client a trop payé. */
    public long resteAPayer() {
        return type == TypeDocument.FACTURE ? totaux().totalTtc() - regle - credite : 0;
    }

    /** Ajoute un règlement reçu ; il ne peut dépasser ce qui reste à payer. */
    public void encaisser(long montant) {
        exigerFactureEmise();
        if (montant <= 0) {
            throw new ErreurMetier("Montant du règlement : un montant positif.");
        }
        if (montant > resteAPayer()) {
            throw new ErreurMetier("Le règlement dépasse ce qui reste à payer sur la facture " + numero + ".");
        }
        this.regle += montant;
    }

    /** Retire un règlement annulé. */
    public void desencaisser(long montant) {
        exigerFactureEmise();
        if (montant <= 0 || montant > regle) {
            throw new IllegalArgumentException("Montant à retirer incohérent");
        }
        this.regle -= montant;
    }

    /** Ce qu'un nouvel avoir peut encore retirer de la facture. */
    public long creditable() {
        return type == TypeDocument.FACTURE ? totaux().totalTtc() - credite : 0;
    }

    /** Déduit un avoir émis sur la facture. */
    public void crediter(long montant) {
        exigerFactureEmise();
        if (montant <= 0) {
            throw new IllegalArgumentException("Montant d'avoir incohérent");
        }
        if (montant > creditable()) {
            throw new ErreurMetier("L'avoir dépasse le montant de la facture " + numero + " qui reste à annuler ("
                    + creditable() + ").");
        }
        this.credite += montant;
    }

    private void exigerFactureEmise() {
        if (type != TypeDocument.FACTURE || statut != StatutDocument.EMIS) {
            throw new ErreurMetier("Seule une facture émise reçoit des règlements et des avoirs.");
        }
    }

    /** Où en est le document, en un mot, pour les listes. */
    public Situation situation(LocalDate aujourdHui) {
        if (statut == StatutDocument.BROUILLON) {
            return Situation.BROUILLON;
        }
        return switch (type) {
            case AVOIR -> Situation.EMIS;
            case DEVIS -> switch (statut) {
                case ACCEPTE -> Situation.ACCEPTE;
                case REFUSE -> Situation.REFUSE;
                case FACTURE -> Situation.FACTURE;
                case ANNULE -> Situation.ANNULE;
                default -> contenu.echeance() != null && contenu.echeance().isBefore(aujourdHui)
                        ? Situation.EXPIRE : Situation.EN_ATTENTE;
            };
            case FACTURE -> {
                long reste = resteAPayer();
                if (credite >= totaux().totalTtc()) {
                    yield Situation.ANNULEE_PAR_AVOIR;
                }
                if (reste <= 0) {
                    yield Situation.PAYEE;
                }
                if (contenu.echeance() != null && contenu.echeance().isBefore(aujourdHui)) {
                    yield Situation.EN_RETARD;
                }
                yield regle > 0 ? Situation.PARTIELLEMENT_PAYEE : Situation.A_PAYER;
            }
        };
    }

    /** Ce qu'on affiche dans les listes. {@code alerte} : à surveiller (retard, devis expiré). */
    public enum Situation {
        BROUILLON("Brouillon", false), EMIS("Émis", false), EN_ATTENTE("En attente de réponse", false),
        ACCEPTE("Accepté", false), REFUSE("Refusé", false), FACTURE("Facturé", false), ANNULE("Annulé", false),
        EXPIRE("Expiré", true), A_PAYER("À payer", false), PARTIELLEMENT_PAYEE("Payée en partie", false),
        PAYEE("Payée", false), EN_RETARD("En retard", true), ANNULEE_PAR_AVOIR("Annulée par avoir", false);

        private final String libelle;
        private final boolean alerte;

        Situation(String libelle, boolean alerte) {
            this.libelle = libelle;
            this.alerte = alerte;
        }

        public String libelle() {
            return libelle;
        }

        public boolean alerte() {
            return alerte;
        }
    }

    // ------------------------------------------------------------------ lecture

    public Totaux totaux() {
        return Totaux.de(contenu.lignes(), contenu.remiseGlobale());
    }

    /** « Facture FA-2026-0001 », « Devis (brouillon) ». */
    public String intitule() {
        return type.libelle() + (numero == null ? " (brouillon)" : " " + numero);
    }

    public Etat etat() {
        return new Etat(id, tenantId, type, numero, statut, client, contenu, mentionTva, conditions, origineId,
                origineNumero, regle, credite, creePar, creeLe, emisLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public TypeDocument type() { return type; }

    public String numero() { return numero; }

    public StatutDocument statut() { return statut; }

    public ClientFige client() { return client; }

    public Contenu contenu() { return contenu; }

    public String mentionTva() { return mentionTva; }

    public String conditions() { return conditions; }

    public UUID origineId() { return origineId; }

    public String origineNumero() { return origineNumero; }

    public long regle() { return regle; }

    public long credite() { return credite; }

    public UUID creePar() { return creePar; }

    public Instant creeLe() { return creeLe; }

    public Instant emisLe() { return emisLe; }
}
