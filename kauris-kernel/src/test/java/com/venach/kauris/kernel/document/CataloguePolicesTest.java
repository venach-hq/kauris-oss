package com.venach.kauris.kernel.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CataloguePolicesTest {

    /** En-tête d'une police TrueType : suffisant pour le catalogue, qui ne lit que le nom et la signature. */
    private static final byte[] TRUETYPE = {0, 1, 0, 0, 0, 0, 0, 0};

    @TempDir
    Path dossier;

    private void police(String nom) throws IOException {
        Files.write(dossier.resolve(nom), TRUETYPE);
    }

    @Test
    void lesFichiersDuServeurDonnentFamillesGraissesEtItaliques() throws IOException {
        // les noms du dossier static/fonts du serveur, y compris leurs écarts de casse
        for (String nom : List.of("Inter-Regular.ttf", "Inter-Regular-italic.ttf", "Lato-BlackItalic.ttf",
                "Lato-Bold.ttf", "Poppins-ExtraBoldItalic.ttf", "Poppins-SemiBold.ttf", "Poppins-Thin.ttf",
                "PlayfairDisplay-Regular.ttf", "OpenSans-Italic.ttf", "Roboto-regular.ttf")) {
            police(nom);
        }
        Files.writeString(dossier.resolve("OFL.txt"), "licence");
        Files.writeString(dossier.resolve("Faux-Regular.ttf"), "pas une police");

        CataloguePolices catalogue = CataloguePolices.depuis(dossier);

        assertEquals(List.of("Inter", "Lato", "OpenSans", "PlayfairDisplay", "Poppins", "Roboto", "Helvetica",
                "Times", "Courier"), catalogue.familles());
        assertEquals("Playfair Display", catalogue.police("PlayfairDisplay").orElseThrow().libelle());
        assertEquals("Open Sans", catalogue.police("OpenSans").orElseThrow().libelle());
        CataloguePolices.Fichier italique = catalogue.fichier("Inter-Regular-italic.ttf").orElseThrow();
        assertEquals(400, italique.graisse());
        assertTrue(italique.italique());
        assertEquals(900, catalogue.fichier("Lato-BlackItalic.ttf").orElseThrow().graisse());
        assertEquals(800, catalogue.fichier("Poppins-ExtraBoldItalic.ttf").orElseThrow().graisse());
        assertEquals(600, catalogue.fichier("Poppins-SemiBold.ttf").orElseThrow().graisse());
        assertEquals(100, catalogue.fichier("Poppins-Thin.ttf").orElseThrow().graisse());
        assertEquals(400, catalogue.fichier("Roboto-regular.ttf").orElseThrow().graisse());
        assertFalse(catalogue.fichier("Faux-Regular.ttf").isPresent());
        assertFalse(catalogue.fichier("OFL.txt").isPresent());
        // rien d'autre que les fichiers du catalogue : pas de chemin vers le reste du disque
        assertFalse(catalogue.fichier("../../etc/passwd").isPresent());
    }

    @Test
    void sansDossierIlResteLesPolicesDeBase() {
        assertEquals(CataloguePolices.DE_BASE, CataloguePolices.vide().familles());
        assertEquals(CataloguePolices.DE_BASE, CataloguePolices.depuis(Path.of("/nexiste/pas")).familles());
        assertTrue(CataloguePolices.vide().police("Helvetica").orElseThrow().deBase());
    }
}
