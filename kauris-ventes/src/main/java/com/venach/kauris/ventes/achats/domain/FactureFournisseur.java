package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Une facture reçue d'un fournisseur, enregistrée (FF-AAAA-NNNN) avec son propre numéro : ce que l'entreprise doit.
 * Elle se règle en une ou plusieurs fois ; une erreur de saisie se corrige en l'annulant (si rien n'est réglé).
 */
public final class FactureFournisseur {

    public enum Statut { A_PAYER, ANNULEE }

    public record Etat(UUID id, UUID tenantId, String numero, UUID fournisseurId, String fournisseurNom,
                       String referenceFournisseur, LocalDate date, LocalDate echeance, String objet, long montantHt,
                       long montantTva, UUID commandeId, String commandeNumero, long regle, Statut statut,
                       UUID creePar, Instant creeLe) {
    }

    private final Etat base;
    private long regle;
    private Statut statut;

    private FactureFournisseur(Etat e) {
        Objects.requireNonNull(e.id());
        Objects.requireNonNull(e.tenantId());
        Objects.requireNonNull(e.numero());
        Objects.requireNonNull(e.fournisseurId());
        Objects.requireNonNull(e.date());
        Objects.requireNonNull(e.creeLe());
        if (e.montantHt() < 0 || e.montantTva() < 0 || e.montantHt() + e.montantTva() <= 0) {
            throw new ErreurMetier("Montants : hors taxes et TVA positifs, total supérieur à zéro.");
        }
        if (e.echeance() != null && e.echeance().isBefore(e.date())) {
            throw new ErreurMetier("L'échéance ne peut pas précéder la date de la facture.");
        }
        this.base = new Etat(e.id(), e.tenantId(), e.numero(), e.fournisseurId(), e.fournisseurNom(),
                Textes.texte(e.referenceFournisseur(), "Numéro de la facture du fournisseur", 60), e.date(),
                e.echeance(), Textes.texte(e.objet(), "Objet", 200), e.montantHt(), e.montantTva(), e.commandeId(),
                e.commandeNumero(), 0, Statut.A_PAYER, e.creePar(), e.creeLe());
        if (base.referenceFournisseur().isEmpty()) {
            throw new ErreurMetier("Indiquez le numéro de la facture du fournisseur.");
        }
        this.regle = e.regle();
        this.statut = e.statut() == null ? Statut.A_PAYER : e.statut();
    }

    public static FactureFournisseur enregistrer(UUID tenantId, String numero, Fournisseur f, String reference,
                                                 LocalDate date, LocalDate echeance, String objet, long ht, long tva,
                                                 Commande commande, UUID creePar, Instant maintenant) {
        if (commande != null && !commande.fournisseurId().equals(f.id())) {
            throw new ErreurMetier("La commande est d'un autre fournisseur.");
        }
        return new FactureFournisseur(new Etat(UUID.randomUUID(), tenantId, numero, f.id(), f.nom(), reference, date,
                echeance, objet, ht, tva, commande == null ? null : commande.id(),
                commande == null ? null : commande.numero(), 0, Statut.A_PAYER, creePar, maintenant));
    }

    public static FactureFournisseur reconstituer(Etat e) {
        return new FactureFournisseur(e);
    }

    public long montantTtc() {
        return base.montantHt() + base.montantTva();
    }

    public long reste() {
        return statut == Statut.ANNULEE ? 0 : montantTtc() - regle;
    }

    public void payer(long montant) {
        if (statut == Statut.ANNULEE) {
            throw new ErreurMetier("Cette facture est annulée.");
        }
        if (montant <= 0) {
            throw new ErreurMetier("Montant du règlement : un montant positif.");
        }
        if (montant > reste()) {
            throw new ErreurMetier("Le règlement dépasse ce qui reste à payer (" + reste() + ").");
        }
        regle += montant;
    }

    public void retirer(long montant) {
        if (montant <= 0 || montant > regle) {
            throw new IllegalArgumentException("Montant à retirer incohérent");
        }
        regle -= montant;
    }

    public void annuler() {
        if (regle > 0) {
            throw new ErreurMetier("Une facture déjà réglée en partie ne s'annule pas : annulez d'abord ses "
                    + "règlements.");
        }
        statut = Statut.ANNULEE;
    }

    /** « À payer », « Payée », « En retard », « Annulée ». */
    public String situation(LocalDate aujourdHui) {
        if (statut == Statut.ANNULEE) {
            return "Annulée";
        }
        if (reste() <= 0) {
            return "Payée";
        }
        if (base.echeance() != null && base.echeance().isBefore(aujourdHui)) {
            return "En retard";
        }
        return regle > 0 ? "Payée en partie" : "À payer";
    }

    public Etat etat() {
        return new Etat(base.id(), base.tenantId(), base.numero(), base.fournisseurId(), base.fournisseurNom(),
                base.referenceFournisseur(), base.date(), base.echeance(), base.objet(), base.montantHt(),
                base.montantTva(), base.commandeId(), base.commandeNumero(), regle, statut, base.creePar(),
                base.creeLe());
    }

    public UUID id() { return base.id(); }

    public UUID tenantId() { return base.tenantId(); }

    public String numero() { return base.numero(); }

    public UUID fournisseurId() { return base.fournisseurId(); }

    public String fournisseurNom() { return base.fournisseurNom(); }

    public String referenceFournisseur() { return base.referenceFournisseur(); }

    public LocalDate date() { return base.date(); }

    public LocalDate echeance() { return base.echeance(); }

    public String objet() { return base.objet(); }

    public long montantHt() { return base.montantHt(); }

    public long montantTva() { return base.montantTva(); }

    public UUID commandeId() { return base.commandeId(); }

    public String commandeNumero() { return base.commandeNumero(); }

    public long regle() { return regle; }

    public Statut statut() { return statut; }
}
