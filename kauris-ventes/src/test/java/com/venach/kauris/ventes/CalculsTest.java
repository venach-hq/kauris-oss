package com.venach.kauris.ventes;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.MontantEnLettres;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.Totaux;
import com.venach.kauris.ventes.domain.TypeDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les calculs des documents : lignes, remises, TVA par taux, arrondis ; numéros ; montants en lettres. */
class CalculsTest {

    private static Ligne ligne(String qte, long pu, String remise, String tva) {
        return new Ligne(null, "Article", new BigDecimal(qte), "u", pu, new BigDecimal(remise), new BigDecimal(tva));
    }

    @Test
    void uneLigneSeCalculeAvecSaRemiseEtSArrondit() {
        assertEquals(30_000, ligne("3", 10_000, "0", "18").montantHt());
        assertEquals(27_000, ligne("3", 10_000, "10", "18").montantHt());
        // 2,5 × 333 = 832,5 → 833 (arrondi au plus proche, demi vers le haut)
        assertEquals(833, ligne("2.5", 333, "0", "0").montantHt());
        // 0,333 × 1 000 × 92,5 % = 308,025 → 308
        assertEquals(308, ligne("0.333", 1_000, "7.5", "0").montantHt());
    }

    @Test
    void laTvaSeCalculeParTauxApresLaRemiseGlobale() {
        Totaux t = Totaux.de(List.of(
                ligne("2", 50_000, "0", "18"),    // 100 000 à 18 %
                ligne("1", 20_000, "0", "18"),    // 20 000 à 18 %
                ligne("4", 2_500, "0", "0")),     // 10 000 exonéré
                new BigDecimal("10"));
        assertEquals(130_000, t.lignesHt());
        assertEquals(2, t.tva().size());
        // 0 % : 10 000 − 1 000 = 9 000 ; 18 % : 120 000 − 12 000 = 108 000, TVA 19 440
        assertEquals(new BigDecimal("0.00"), t.tva().get(0).taux());
        assertEquals(9_000, t.tva().get(0).base());
        assertEquals(108_000, t.tva().get(1).base());
        assertEquals(19_440, t.tva().get(1).montant());
        assertEquals(13_000, t.remiseGlobale());
        assertEquals(117_000, t.totalHt());
        assertEquals(19_440, t.totalTva());
        assertEquals(136_440, t.totalTtc());
        assertTrue(t.avecTva());
    }

    @Test
    void sansTvaLeTtcEgaleLeHt() {
        Totaux t = Totaux.de(List.of(ligne("1", 15_000, "0", "0")), BigDecimal.ZERO);
        assertEquals(15_000, t.totalTtc());
        assertFalse(t.avecTva());
    }

    @Test
    void lesSaisiesAberrantesSontRefusees() {
        assertThrows(ErreurMetier.class, () -> ligne("0", 1_000, "0", "18"));
        assertThrows(ErreurMetier.class, () -> ligne("-1", 1_000, "0", "18"));
        assertThrows(ErreurMetier.class, () -> ligne("1", -5, "0", "18"));
        assertThrows(ErreurMetier.class, () -> ligne("1", 1_000, "101", "18"));
        assertThrows(ErreurMetier.class, () -> ligne("1", 1_000, "0", "-1"));
        assertThrows(ErreurMetier.class, () -> new Ligne(null, "  ", BigDecimal.ONE, "", 10, null, null));
    }

    @Test
    void lesNumerosSuiventLAnneeEtLeRang() {
        assertEquals("FA-2026-0001", TypeDocument.FACTURE.numero(2026, 1));
        assertEquals("DE-2027-0042", TypeDocument.DEVIS.numero(2027, 42));
        assertEquals("AV-2026-12345", TypeDocument.AVOIR.numero(2026, 12_345));
    }

    @Test
    void lesMontantsSEcriventEnLettres() {
        assertEquals("cent trente-six mille quatre cent quarante francs CFA",
                MontantEnLettres.montant(136_440, Devise.XOF));
        assertEquals("un million de francs CFA", MontantEnLettres.montant(1_000_000, Devise.XOF));
        assertEquals("deux millions cinq cent mille francs CFA", MontantEnLettres.montant(2_500_000, Devise.XOF));
        assertEquals("un franc CFA", MontantEnLettres.montant(1, Devise.XOF));
        assertEquals("quatre-vingts euros", MontantEnLettres.montant(80, Devise.EUR));
    }
}
