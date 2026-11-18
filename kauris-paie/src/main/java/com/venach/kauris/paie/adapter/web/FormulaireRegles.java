package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.ReglesPaie.Application;
import com.venach.kauris.paie.domain.ReglesPaie.Arrondi;
import com.venach.kauris.paie.domain.ReglesPaie.Comptes;
import com.venach.kauris.paie.domain.ReglesPaie.Cotisation;
import com.venach.kauris.paie.domain.ReglesPaie.Deduction;
import com.venach.kauris.paie.domain.ReglesPaie.Famille;
import com.venach.kauris.paie.domain.ReglesPaie.HeuresSupplementaires;
import com.venach.kauris.paie.domain.ReglesPaie.Impot;
import com.venach.kauris.paie.domain.ReglesPaie.Majoration;
import com.venach.kauris.paie.domain.ReglesPaie.Parts;
import com.venach.kauris.paie.domain.ReglesPaie.Periodicite;
import com.venach.kauris.paie.domain.ReglesPaie.TaxeEmployeur;
import com.venach.kauris.paie.domain.ReglesPaie.Tranche;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * L'éditeur des règles de paie : lecture du formulaire (lignes numérotées pour les cotisations, les taxes, les
 * tranches et les majorations) et comparaison avec le modèle recommandé, pour afficher « Recommandé : … » à côté de
 * chaque valeur qui s'en écarte.
 */
final class FormulaireRegles {

    /** Lignes vides proposées en plus des lignes existantes. */
    static final int LIGNES_EN_PLUS = 2;

    private FormulaireRegles() {
    }

    // ================================================================== lecture

    static ReglesPaie.Contenu lire(Map<String, String> f) {
        List<Cotisation> cotisations = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String p = "cot_" + i + "_";
            if (vide(f, p + "code") && vide(f, p + "libelle")) {
                continue;
            }
            String nom = texte(f, p + "libelle", "Cotisation " + (i + 1));
            cotisations.add(new Cotisation(f.get(p + "code"), f.get(p + "libelle"), f.get(p + "organisme"),
                    taux(f, p + "ts", nom), taux(f, p + "te", nom), montant(f, p + "plafond", nom + " : plafond"),
                    montant(f, p + "min", nom + " : assiette minimale"), montant(f, p + "fs", nom + " : montant fixe"),
                    montant(f, p + "fe", nom + " : montant fixe"), entier(f, p + "pmax", nom),
                    choix(Deduction.class, f.get(p + "deduction"), Deduction.TOTALE),
                    choix(Application.class, f.get(p + "application"), Application.TOUS), coche(f, p + "apprentis"),
                    f.get(p + "compte"), f.get(p + "note")));
        }
        List<TaxeEmployeur> taxes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String p = "tax_" + i + "_";
            if (vide(f, p + "code") && vide(f, p + "libelle")) {
                continue;
            }
            String nom = texte(f, p + "libelle", "Taxe " + (i + 1));
            taxes.add(new TaxeEmployeur(f.get(p + "code"), f.get(p + "libelle"), f.get(p + "organisme"),
                    taux(f, p + "taux", nom), montant(f, p + "plafond", nom + " : plafond"),
                    choix(Application.class, f.get(p + "application"), Application.TOUS), coche(f, p + "apprentis"),
                    f.get(p + "charge"), f.get(p + "compte"), f.get(p + "note")));
        }
        List<Tranche> tranches = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String p = "tr_" + i + "_";
            if (vide(f, p + "jusqua") && vide(f, p + "taux")) {
                continue;
            }
            tranches.add(new Tranche(montant(f, p + "jusqua", "Tranche " + (i + 1)), taux(f, p + "taux",
                    "Tranche " + (i + 1))));
        }
        Parts parts = new Parts(decimal(f, "parts_celibataire", "1"), decimal(f, "parts_marie", "1"),
                decimal(f, "parts_seul_enfants", "1"), decimal(f, "parts_veuf_enfants", "1"),
                decimal(f, "parts_enfant", "0"), decimal(f, "parts_max", "1"));
        Impot impot = new Impot(f.get("imp_nom"), f.get("imp_organisme"),
                choix(Periodicite.class, f.get("imp_periodicite"), Periodicite.MENSUELLE), tranches,
                coche(f, "imp_deduire"), taux(f, "imp_retraite", "Retraite déductible"),
                taux(f, "imp_abattement", "Abattement"), montant(f, "imp_abattement_limite", "Limite de l'abattement"),
                choix(Famille.class, f.get("imp_famille"), Famille.AUCUNE),
                montant(f, "imp_deduction_personne", "Déduction par personne"),
                entier(f, "imp_personnes_max", "Personnes à charge au plus"),
                montant(f, "imp_reduction_demi_part", "Réduction par demi-part"), parts,
                Math.max(1, montant(f, "imp_arrondi_revenu", "Arrondi du revenu")),
                Math.max(1, montant(f, "imp_arrondi_impot", "Arrondi de l'impôt")),
                choix(Arrondi.class, f.get("imp_arrondi_mensuel"), Arrondi.PLUS_PROCHE),
                coche(f, "imp_differentielles"), coche(f, "imp_apprentis"),
                taux(f, "imp_gerant", "Frais des gérants"), montant(f, "imp_gerant_limite", "Limite des frais"),
                f.get("imp_compte"), f.get("imp_note"));
        List<Majoration> majorations = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String p = "maj_" + i + "_";
            if (vide(f, p + "libelle") && vide(f, p + "taux")) {
                continue;
            }
            majorations.add(new Majoration(f.get(p + "libelle"), decimal(f, p + "taux", "0")));
        }
        HeuresSupplementaires heures = new HeuresSupplementaires(decimal(f, "hs_heures", "173.33"), majorations,
                f.get("hs_note"));
        Comptes comptes = new Comptes(f.get("cpt_salaires"), f.get("cpt_indemnites"), f.get("cpt_charges"),
                f.get("cpt_net"), f.get("cpt_avances"));
        return new ReglesPaie.Contenu(cotisations, taxes, impot, heures, comptes);
    }

    private static boolean vide(Map<String, String> f, String cle) {
        return f.getOrDefault(cle, "").isBlank();
    }

    private static boolean coche(Map<String, String> f, String cle) {
        String v = f.getOrDefault(cle, "");
        return v.equals("on") || v.equals("true");
    }

    private static String texte(Map<String, String> f, String cle, String sinon) {
        return vide(f, cle) ? sinon : f.get(cle).strip();
    }

    private static BigDecimal taux(Map<String, String> f, String cle, String champ) {
        return vide(f, cle) ? BigDecimal.ZERO : FormatsPaie.lireDecimal(f.get(cle), champ);
    }

    private static BigDecimal decimal(Map<String, String> f, String cle, String sinon) {
        return vide(f, cle) ? new BigDecimal(sinon) : FormatsPaie.lireDecimal(f.get(cle), cle);
    }

    private static long montant(Map<String, String> f, String cle, String champ) {
        return vide(f, cle) ? 0 : FormatsPaie.lireMontant(f.get(cle), champ);
    }

    private static int entier(Map<String, String> f, String cle, String champ) {
        return FormatsPaie.lireEntier(f.get(cle), champ, 0);
    }

    private static <E extends Enum<E>> E choix(Class<E> type, String valeur, E sinon) {
        if (valeur == null || valeur.isBlank()) {
            return sinon;
        }
        try {
            return Enum.valueOf(type, valeur.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier("Choix invalide : " + valeur + ".");
        }
    }

    // ================================================================== recommandations

    /**
     * Pour chaque champ qui s'écarte du modèle recommandé : la valeur recommandée, à afficher à côté. Clés : les noms
     * des champs du formulaire, plus « cot_CODE_… » et « tax_CODE_… » pour les lignes, « tranches » et
     * « majorations » pour les listes, « manquantes » pour les lignes recommandées absentes.
     */
    static Map<String, String> ecarts(ReglesPaie.Contenu c, Optional<ModelesRecommandes.Modele> modele, FormatsPaie f) {
        Map<String, String> e = new HashMap<>();
        if (modele.isEmpty()) {
            return e;
        }
        ReglesPaie.Contenu m = modele.get().contenu();
        Impot i = c.impot();
        Impot r = m.impot();
        diff(e, "imp_nom", i.nom(), r.nom(), r.nom());
        diff(e, "imp_organisme", i.organisme(), r.organisme(), r.organisme());
        diff(e, "imp_periodicite", i.periodicite(), r.periodicite(), r.periodicite().libelle());
        diff(e, "imp_deduire", i.deduireCotisations(), r.deduireCotisations(), r.deduireCotisations() ? "oui" : "non");
        diff(e, "imp_retraite", i.retraiteDeductibleMax(), r.retraiteDeductibleMax(), f.tauxOuAucun(
                r.retraiteDeductibleMax()));
        diff(e, "imp_abattement", i.abattement(), r.abattement(), f.tauxOuAucun(r.abattement()));
        diff(e, "imp_abattement_limite", i.abattementLimite(), r.abattementLimite(), f.montantOuAucun(
                r.abattementLimite()));
        diff(e, "imp_famille", i.famille(), r.famille(), r.famille().libelle());
        diff(e, "imp_deduction_personne", i.deductionParPersonne(), r.deductionParPersonne(), f.montantOuAucun(
                r.deductionParPersonne()));
        diff(e, "imp_personnes_max", i.personnesMax(), r.personnesMax(), String.valueOf(r.personnesMax()));
        diff(e, "imp_reduction_demi_part", i.reductionParDemiPart(), r.reductionParDemiPart(), f.montantOuAucun(
                r.reductionParDemiPart()));
        diff(e, "parts", i.parts(), r.parts(), f.parts(r.parts()));
        diff(e, "imp_arrondi_revenu", i.arrondiRevenu(), r.arrondiRevenu(), f.nombre(r.arrondiRevenu()));
        diff(e, "imp_arrondi_impot", i.arrondiImpot(), r.arrondiImpot(), f.nombre(r.arrondiImpot()));
        diff(e, "imp_arrondi_mensuel", i.arrondiMensuel(), r.arrondiMensuel(), r.arrondiMensuel().libelle());
        diff(e, "imp_differentielles", i.primesDifferentielles(), r.primesDifferentielles(),
                r.primesDifferentielles() ? "oui" : "non");
        diff(e, "imp_apprentis", i.apprentisExoneres(), r.apprentisExoneres(), r.apprentisExoneres() ? "oui" : "non");
        diff(e, "imp_gerant", i.gerantAbattement(), r.gerantAbattement(), f.tauxOuAucun(r.gerantAbattement()));
        diff(e, "imp_gerant_limite", i.gerantAbattementLimite(), r.gerantAbattementLimite(), f.montantOuAucun(
                r.gerantAbattementLimite()));
        diff(e, "tranches", i.tranches(), r.tranches(), f.bareme(r.tranches()));
        diff(e, "hs_heures", c.heures().heuresDuMois(), m.heures().heuresDuMois(),
                f.decimal(m.heures().heuresDuMois()));
        diff(e, "majorations", c.heures().majorations(), m.heures().majorations(), f.majorations(
                m.heures().majorations()));
        List<String> manquantes = new ArrayList<>();
        for (Cotisation rc : m.cotisations()) {
            Optional<Cotisation> x = c.cotisations().stream().filter(y -> y.code().equals(rc.code())).findFirst();
            if (x.isEmpty()) {
                manquantes.add(rc.libelle());
                continue;
            }
            String p = "cot_" + rc.code() + "_";
            Cotisation cc = x.get();
            diff(e, p + "ts", cc.tauxSalarie(), rc.tauxSalarie(), f.tauxOuAucun(rc.tauxSalarie()));
            diff(e, p + "te", cc.tauxEmployeur(), rc.tauxEmployeur(), f.tauxOuAucun(rc.tauxEmployeur()));
            diff(e, p + "plafond", cc.plafond(), rc.plafond(), f.montantOuAucun(rc.plafond()));
            diff(e, p + "min", cc.assietteMinimale(), rc.assietteMinimale(), f.montantOuAucun(rc.assietteMinimale()));
            diff(e, p + "fs", cc.fixeSalarie(), rc.fixeSalarie(), f.montantOuAucun(rc.fixeSalarie()));
            diff(e, p + "fe", cc.fixeEmployeur(), rc.fixeEmployeur(), f.montantOuAucun(rc.fixeEmployeur()));
            diff(e, p + "deduction", cc.deduction(), rc.deduction(), rc.deduction().libelle());
        }
        for (TaxeEmployeur rt : m.taxes()) {
            Optional<TaxeEmployeur> x = c.taxes().stream().filter(y -> y.code().equals(rt.code())).findFirst();
            if (x.isEmpty()) {
                manquantes.add(rt.libelle());
                continue;
            }
            diff(e, "tax_" + rt.code() + "_taux", x.get().taux(), rt.taux(), f.tauxOuAucun(rt.taux()));
        }
        if (!manquantes.isEmpty()) {
            e.put("manquantes", String.join(", ", manquantes));
        }
        return e;
    }

    private static void diff(Map<String, String> e, String cle, Object valeur, Object recommande, String affichage) {
        boolean pareil = valeur instanceof BigDecimal a && recommande instanceof BigDecimal b
                ? a.compareTo(b) == 0 : Objects.equals(valeur, recommande);
        if (!pareil) {
            e.put(cle, affichage);
        }
    }
}
