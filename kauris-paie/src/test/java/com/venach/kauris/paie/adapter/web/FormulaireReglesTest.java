package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.ReglesPaie;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'éditeur des règles : ce qui est affiché se relit à l'identique, et les écarts aux recommandations se voient. */
public class FormulaireReglesTest {

    /** Les champs que l'éditeur envoie pour ces règles (comme un navigateur). */
    public static Map<String, String> champs(ReglesPaie.Contenu c) {
        Map<String, String> f = new HashMap<>();
        for (int i = 0; i < c.cotisations().size(); i++) {
            ReglesPaie.Cotisation x = c.cotisations().get(i);
            String p = "cot_" + i + "_";
            f.put(p + "code", x.code());
            f.put(p + "libelle", x.libelle());
            f.put(p + "organisme", x.organisme());
            f.put(p + "ts", x.tauxSalarie().toPlainString().replace('.', ','));
            f.put(p + "te", x.tauxEmployeur().toPlainString());
            f.put(p + "plafond", x.plafond() == 0 ? "" : String.valueOf(x.plafond()));
            f.put(p + "min", x.assietteMinimale() == 0 ? "" : String.valueOf(x.assietteMinimale()));
            f.put(p + "fs", x.fixeSalarie() == 0 ? "" : String.valueOf(x.fixeSalarie()));
            f.put(p + "fe", x.fixeEmployeur() == 0 ? "" : String.valueOf(x.fixeEmployeur()));
            f.put(p + "pmax", x.personnesMax() == 0 ? "" : String.valueOf(x.personnesMax()));
            f.put(p + "deduction", x.deduction().name());
            f.put(p + "application", x.application().name());
            if (x.apprentis()) {
                f.put(p + "apprentis", "on");
            }
            f.put(p + "compte", x.compte());
            f.put(p + "note", x.note());
        }
        for (int i = 0; i < c.taxes().size(); i++) {
            ReglesPaie.TaxeEmployeur x = c.taxes().get(i);
            String p = "tax_" + i + "_";
            f.put(p + "code", x.code());
            f.put(p + "libelle", x.libelle());
            f.put(p + "organisme", x.organisme());
            f.put(p + "taux", x.taux().toPlainString());
            f.put(p + "plafond", x.plafond() == 0 ? "" : String.valueOf(x.plafond()));
            f.put(p + "application", x.application().name());
            if (x.apprentis()) {
                f.put(p + "apprentis", "on");
            }
            f.put(p + "charge", x.compteCharge());
            f.put(p + "compte", x.compte());
            f.put(p + "note", x.note());
        }
        ReglesPaie.Impot i = c.impot();
        f.put("imp_nom", i.nom());
        f.put("imp_organisme", i.organisme());
        f.put("imp_periodicite", i.periodicite().name());
        for (int n = 0; n < i.tranches().size(); n++) {
            ReglesPaie.Tranche t = i.tranches().get(n);
            f.put("tr_" + n + "_jusqua", t.jusqua() == 0 ? "" : String.valueOf(t.jusqua()));
            f.put("tr_" + n + "_taux", t.taux().toPlainString());
        }
        if (i.deduireCotisations()) {
            f.put("imp_deduire", "on");
        }
        f.put("imp_retraite", i.retraiteDeductibleMax().signum() == 0 ? "" : i.retraiteDeductibleMax().toPlainString());
        f.put("imp_abattement", i.abattement().signum() == 0 ? "" : i.abattement().toPlainString());
        f.put("imp_abattement_limite", i.abattementLimite() == 0 ? "" : String.valueOf(i.abattementLimite()));
        f.put("imp_famille", i.famille().name());
        f.put("imp_deduction_personne", i.deductionParPersonne() == 0 ? "" : String.valueOf(i.deductionParPersonne()));
        f.put("imp_personnes_max", String.valueOf(i.personnesMax()));
        f.put("imp_reduction_demi_part", i.reductionParDemiPart() == 0 ? "" : String.valueOf(i.reductionParDemiPart()));
        f.put("parts_celibataire", i.parts().celibataire().toPlainString());
        f.put("parts_marie", i.parts().marie().toPlainString());
        f.put("parts_seul_enfants", i.parts().seulAvecEnfants().toPlainString());
        f.put("parts_veuf_enfants", i.parts().veufAvecEnfants().toPlainString());
        f.put("parts_enfant", i.parts().parEnfant().toPlainString());
        f.put("parts_max", i.parts().maximum().toPlainString());
        f.put("imp_arrondi_revenu", String.valueOf(i.arrondiRevenu()));
        f.put("imp_arrondi_impot", String.valueOf(i.arrondiImpot()));
        f.put("imp_arrondi_mensuel", i.arrondiMensuel().name());
        if (i.primesDifferentielles()) {
            f.put("imp_differentielles", "on");
        }
        if (i.apprentisExoneres()) {
            f.put("imp_apprentis", "on");
        }
        f.put("imp_gerant", i.gerantAbattement().signum() == 0 ? "" : i.gerantAbattement().toPlainString());
        f.put("imp_gerant_limite", i.gerantAbattementLimite() == 0 ? "" : String.valueOf(i.gerantAbattementLimite()));
        f.put("imp_compte", i.compte());
        f.put("imp_note", i.note());
        f.put("hs_heures", c.heures().heuresDuMois().toPlainString());
        for (int n = 0; n < c.heures().majorations().size(); n++) {
            f.put("maj_" + n + "_libelle", c.heures().majorations().get(n).libelle());
            f.put("maj_" + n + "_taux", c.heures().majorations().get(n).taux().toPlainString());
        }
        f.put("hs_note", c.heures().note());
        f.put("cpt_salaires", c.comptes().salaires());
        f.put("cpt_indemnites", c.comptes().indemnites());
        f.put("cpt_charges", c.comptes().chargesSociales());
        f.put("cpt_net", c.comptes().remunerationsDues());
        f.put("cpt_avances", c.comptes().avances());
        return f;
    }

    @Test
    void cequiEstAfficheSeRelitALIdentique() {
        for (ModelesRecommandes.Modele m : ModelesRecommandes.tous()) {
            ReglesPaie.Contenu relu = FormulaireRegles.lire(champs(m.contenu()));
            assertEquals(m.contenu(), relu, m.code());
            assertTrue(FormulaireRegles.ecarts(relu, Optional.of(m), new FormatsPaie(Devise.XOF)).isEmpty(), m.code());
        }
    }

    @Test
    void ecartsSignales() {
        ModelesRecommandes.Modele ci = ModelesRecommandes.pour(Pays.COTE_D_IVOIRE).orElseThrow();
        Map<String, String> f = champs(ci.contenu());
        f.put("cot_3_te", "3,5");                 // accident du travail de l'entreprise
        f.put("tr_5_taux", "30");                 // dernière tranche corrigée
        f.remove("tax_4_code");                   // taxe additionnelle supprimée
        f.remove("tax_4_libelle");
        f.put("cot_5_code", "MUTUELLE");          // une ligne ajoutée
        f.put("cot_5_libelle", "Mutuelle d'entreprise");
        f.put("cot_5_organisme", "MUT");
        f.put("cot_5_ts", "1");
        f.put("cot_5_te", "2");
        ReglesPaie.Contenu c = FormulaireRegles.lire(f);
        assertEquals(6, c.cotisations().size());
        assertEquals(new BigDecimal("3.5"), c.cotisations().get(3).tauxEmployeur());
        Map<String, String> e = FormulaireRegles.ecarts(c, Optional.of(ci), new FormatsPaie(Devise.XOF));
        assertEquals("2 %", e.get("cot_CNPS_ACCIDENT_te"));
        assertTrue(e.get("tranches").contains("32"), e.get("tranches"));
        assertTrue(e.get("manquantes").contains("taxe additionnelle"), e.get("manquantes"));
        assertTrue(FormulaireRegles.ecarts(c, Optional.empty(), new FormatsPaie(Devise.XOF)).isEmpty());
    }

    @Test
    void saisiesInvalides() {
        Map<String, String> f = champs(ModelesRecommandes.pour(Pays.TOGO).orElseThrow().contenu());
        f.put("cot_0_ts", "quatre");
        assertThrows(ErreurMetier.class, () -> FormulaireRegles.lire(f));
        Map<String, String> g = champs(ModelesRecommandes.pour(Pays.TOGO).orElseThrow().contenu());
        g.put("tr_7_jusqua", "25000000");         // la dernière tranche doit rester sans limite
        assertThrows(ErreurMetier.class, () -> FormulaireRegles.lire(g));
        Map<String, String> h = champs(ModelesRecommandes.pour(Pays.TOGO).orElseThrow().contenu());
        h.put("imp_famille", "INCONNU");
        assertThrows(ErreurMetier.class, () -> FormulaireRegles.lire(h));
        assertEquals(List.of(), FormulaireRegles.lire(Map.of("imp_nom", "IR", "imp_organisme", "DGI",
                "tr_0_taux", "10")).cotisations(), "le strict minimum suffit");
    }
}
