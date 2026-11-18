package com.venach.kauris.ventes.achats.application;

import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Ligne;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;

import java.util.List;
import java.util.UUID;

/**
 * Les opérations que les achats transmettent à la comptabilité : facture fournisseur enregistrée (journal des
 * achats ; achats de marchandises si elle suit une commande, autres achats sinon) et paiement (trésorerie).
 */
public final class ComptabiliteAchats {

    public static final String ORIGINE = "achats";

    private ComptabiliteAchats() {
    }

    public static String tiers(UUID fournisseurId) {
        return "FRS:" + fournisseurId;
    }

    public static String reference(FactureFournisseur f) {
        return "FACTURE:" + f.id();
    }

    public static String reference(ReglementFournisseur r) {
        return "REGLEMENT:" + r.id();
    }

    public static OperationComptable facture(FactureFournisseur f) {
        long ttc = f.montantHt() + f.montantTva();
        String libelle = "Facture " + f.referenceFournisseur() + " · " + f.fournisseurNom();
        return new OperationComptable(f.tenantId(), ORIGINE, reference(f), f.date(), OperationComptable.Journal.ACHATS,
                f.numero(), tronque(libelle), List.of(
                Ligne.debit(f.commandeId() != null ? Role.ACHAT_MARCHANDISES : Role.ACHAT_AUTRE, "", f.montantHt()),
                Ligne.debit(Role.TVA_DEDUCTIBLE, "", f.montantTva()),
                Ligne.credit(Role.FOURNISSEUR, "", ttc).pour(tiers(f.fournisseurId()), f.fournisseurNom())));
    }

    public static OperationComptable reglement(FactureFournisseur f, ReglementFournisseur r) {
        String precision = (r.compteId() == null ? "" : r.compteId() + "|") + r.mode().name();
        String libelle = "Paiement " + f.referenceFournisseur() + " · " + f.fournisseurNom() + " (" + r.mode().libelle()
                + ")";
        return new OperationComptable(r.tenantId(), ORIGINE, reference(r), r.date(),
                OperationComptable.Journal.TRESORERIE, f.numero(), tronque(libelle), List.of(
                Ligne.debit(Role.FOURNISSEUR, "", r.montant()).pour(tiers(f.fournisseurId()), f.fournisseurNom()),
                Ligne.credit(Role.TRESORERIE, precision, r.montant())));
    }

    private static String tronque(String s) {
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
