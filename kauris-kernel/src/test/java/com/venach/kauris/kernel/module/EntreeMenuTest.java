package com.venach.kauris.kernel.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntreeMenuTest {

    @Test
    void uneEntreeSansRangementVaDansAutresAvecUneCouleurNeutre() {
        EntreeMenu e = new EntreeMenu("Rapports", "/rapports", null);

        assertEquals(CategorieModule.AUTRES, e.categorie());
        assertEquals(EntreeMenu.COULEUR_PAR_DEFAUT, e.couleur());
        assertEquals("module", e.icone());
    }

    @Test
    void uneCouleurOuUneIconeMalFormeeNePassePasDansLaPage() {
        EntreeMenu e = new EntreeMenu("Ventes", "/ventes", null, CategorieModule.VENTES,
                "red;background:url(x)", "a\"><script>");

        assertEquals(EntreeMenu.COULEUR_PAR_DEFAUT, e.couleur());
        assertEquals("module", e.icone());
        assertThrows(IllegalArgumentException.class, () -> new EntreeMenu("X", "//ailleurs.test"));
    }

    @Test
    void lesCouleursDuNoyauGardentLeTexteBlancLisible() {
        for (EntreeMenu e : new ModuleNoyau("test").menu()) {
            assertTrue(contrasteAvecLeBlanc(e.couleur()) >= 4.5, e.libelle() + " : " + e.couleur());
        }
        assertTrue(contrasteAvecLeBlanc(EntreeMenu.COULEUR_PAR_DEFAUT) >= 4.5);
    }

    /** Rapport de contraste WCAG entre la couleur et le blanc. */
    public static double contrasteAvecLeBlanc(String couleur) {
        int rgb = Integer.parseInt(couleur.substring(1), 16);
        double l = 0.2126 * canal(rgb >> 16) + 0.7152 * canal(rgb >> 8) + 0.0722 * canal(rgb);
        return 1.05 / (l + 0.05);
    }

    private static double canal(int valeur) {
        double c = (valeur & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
