package com.venach.kauris.kernel.entreprise;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.Saisie;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.SaisieTheme;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.FormeJuridique;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntrepriseTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    private final Monde monde = new Monde();

    private Acces proprietaire(OrganisationOuverte o) {
        return monde.acces.accesDe(Identite.venach("awa")).stream()
                .filter(a -> a.tenantId().equals(o.tenantId())).findFirst().orElseThrow();
    }

    private static Saisie togolaise() {
        return new Saisie("Boulangerie du Port", "BDP", "SARL", "1 000 000", "TG", "Lomé",
                "12 rue du Commerce", "1234", "+228 90 00 00 00", "Contact@Exemple.test", "",
                Map.of("rccm", "TG-LFW-01-2024-B12-00001", "nif", "1000123456", "ninea", "ne compte pas au Togo"),
                "XOF", Set.of("EUR", "XOF"), "Ecobank Togo", "Boulangerie du Port SARL",
                "TG42 TG90 8010 0100 4521 8730 1642", "ecoctglx", "Lomé Centre", "Agrément n° 12");
    }

    @Test
    void tantQueRienNEstSaisiOnPartDuNomDeLOrganisation() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");

        Entreprise e = monde.entreprises.consulter(o.tenantId());

        assertEquals("Boulangerie du Port", e.denomination());
        assertEquals(Pays.TOGO, e.pays());
        assertEquals(Devise.XOF, e.devise());
        assertEquals(ThemeDocument.PAR_DEFAUT, e.theme());
        assertFalse(e.aUnLogo());
        assertFalse(e.manques().isEmpty());
    }

    @Test
    void lesParametresEnregistresDonnentLaMentionLegaleDuPays() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        monde.entreprises.enregistrer(proprietaire(o), togolaise());

        Entreprise e = monde.entreprises.consulter(o.tenantId());
        assertEquals(FormeJuridique.SARL, e.forme());
        assertEquals(1_000_000L, e.capital());
        assertEquals("contact@exemple.test", e.email());
        // seuls les champs du pays sont gardés ; la monnaie principale n'est pas répétée dans les autres
        assertEquals(Map.of("rccm", "TG-LFW-01-2024-B12-00001", "nif", "1000123456"), e.identifiants());
        assertEquals(Set.of(Devise.EUR), e.autresDevises());
        assertEquals(ThemeDocument.PAR_DEFAUT, e.theme());
        assertEquals("Ecobank Togo", e.banque().banque());
        assertTrue(e.manques().isEmpty(), e.manques().toString());
        assertEquals("Boulangerie du Port SARL au capital de 1\u00a0000\u00a0000\u00a0F CFA"
                        + " · Siège : 12 rue du Commerce, BP 1234, Lomé, Togo"
                        + " · RCCM TG-LFW-01-2024-B12-00001 · NIF 1000123456"
                        + " · Tél. +228 90 00 00 00 · contact@exemple.test",
                e.mentionLegale());
    }

    @Test
    void chaquePaysDemandeSonRegistreEtSonIdentifiantFiscal() {
        for (Pays pays : Pays.values()) {
            assertEquals("rccm", pays.champsEssentiels().get(0).code(), pays.nom());
            assertEquals(2, pays.champsEssentiels().size(), pays.nom());
            assertEquals(pays, Pays.parCode(pays.code()).orElseThrow());
        }
        assertEquals(8, Pays.values().length);
        assertEquals("NINEA", Pays.SENEGAL.identifiantFiscal().sigle());
        assertEquals("NCC", Pays.COTE_D_IVOIRE.identifiantFiscal().sigle());
        assertEquals("NIF", Pays.TOGO.identifiantFiscal().sigle());
    }

    @Test
    void changerDePaysNeGardePasLesNumerosDeLAncien() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        monde.entreprises.enregistrer(proprietaire(o), togolaise());
        Saisie s = togolaise();

        monde.entreprises.enregistrer(proprietaire(o), new Saisie(s.denomination(), s.sigle(), s.forme(),
                s.capital(), "SN", "Dakar", s.adresse(), s.boitePostale(), s.telephone(), s.email(), s.siteWeb(),
                Map.of("ninea", "005123456", "nif", "1000123456"), s.devise(), s.autresDevises(), s.banque(), s.titulaireCompte(), s.numeroCompte(), s.bic(), s.agence(), s.mentions()));

        Entreprise e = monde.entreprises.consulter(o.tenantId());
        assertEquals(Pays.SENEGAL, e.pays());
        assertEquals(Map.of("ninea", "005123456"), e.identifiants());
        assertEquals(java.util.List.of("le numéro RCCM"), e.manques());
    }

    @Test
    void uneSaisieIncorrecteEstRefuseeEtRienNEstEnregistre() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        Saisie s = togolaise();

        assertThrows(ErreurMetier.class, () -> monde.entreprises.enregistrer(proprietaire(o),
                new Saisie(s.denomination(), s.sigle(), s.forme(), "un million", s.pays(), s.ville(), s.adresse(),
                        s.boitePostale(), s.telephone(), s.email(), s.siteWeb(), s.identifiants(), s.devise(),
                        s.autresDevises(), s.banque(), s.titulaireCompte(), s.numeroCompte(), s.bic(), s.agence(), s.mentions())));
        assertThrows(ErreurMetier.class, () -> monde.entreprises.enregistrer(proprietaire(o),
                new Saisie(s.denomination(), s.sigle(), s.forme(), s.capital(), s.pays(), s.ville(), s.adresse(),
                        s.boitePostale(), s.telephone(), s.email(), s.siteWeb(), s.identifiants(), s.devise(),
                        s.autresDevises(), "B".repeat(81), s.titulaireCompte(), s.numeroCompte(), s.bic(),
                        s.agence(), s.mentions())));
        assertThrows(ErreurMetier.class, () -> monde.entreprises.enregistrer(proprietaire(o),
                new Saisie(s.denomination(), s.sigle(), s.forme(), s.capital(), "FR", s.ville(), s.adresse(),
                        s.boitePostale(), s.telephone(), s.email(), s.siteWeb(), s.identifiants(), s.devise(),
                        s.autresDevises(), s.banque(), s.titulaireCompte(), s.numeroCompte(), s.bic(), s.agence(), s.mentions())));

        assertTrue(monde.entreprisesStockees.trouver(o.tenantId()).isEmpty());
    }

    @Test
    void sansLaPermissionOnConsulteMaisOnNeModifiePas() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));
        monde.acces.rattacherInvitations(new IdentiteConnectee(Identite.venach("kofi"), "kofi@exemple.test", true,
                "Kofi"));
        Acces kofi = monde.acces.accesDe(Identite.venach("kofi")).get(0);

        assertThrows(AccesRefuse.class, () -> monde.entreprises.enregistrer(kofi, togolaise()));
        assertThrows(AccesRefuse.class, () -> monde.entreprises.definirLogo(kofi, PNG));
        assertEquals("Boulangerie", monde.entreprises.consulter(o.tenantId()).denomination());
    }

    @Test
    void leLogoEstUnePngOuUneJpegReconnueASonContenu() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        Acces awa = proprietaire(o);

        monde.entreprises.definirLogo(awa, PNG);
        assertEquals("image/png", monde.entreprises.logo(o.tenantId()).orElseThrow().typeMime());
        assertTrue(monde.entreprises.consulter(o.tenantId()).aUnLogo());

        assertEquals("image/jpeg", Logo.depuis(JPEG).typeMime());
        assertThrows(ErreurMetier.class, () -> monde.entreprises.definirLogo(awa,
                "<svg onload=alert(1)>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(ErreurMetier.class, () -> Logo.depuis(new byte[0]));
        byte[] lourd = new byte[Logo.TAILLE_MAX + 1];
        System.arraycopy(PNG, 0, lourd, 0, PNG.length);
        assertThrows(ErreurMetier.class, () -> Logo.depuis(lourd));

        monde.entreprises.retirerLogo(awa);
        assertFalse(monde.entreprises.consulter(o.tenantId()).aUnLogo());
    }

    @Test
    void lesTroisModelesSontProposesEtLePersonnaliseGardeLesReglages() {
        assertEquals("Inter", ThemeDocument.du(ThemeDocument.Modele.MODERNE).policeTexte());
        assertEquals("Merriweather", ThemeDocument.du(ThemeDocument.Modele.CLASSIQUE).policeTitres());
        assertEquals(ThemeDocument.PositionLogo.DROITE, ThemeDocument.du(ThemeDocument.Modele.ELEGANT).logo());

        // un modèle proposé ignore les réglages envoyés avec lui
        assertEquals(ThemeDocument.CLASSIQUE, monde.entreprises.lire(new SaisieTheme("CLASSIQUE", "Poppins",
                "Poppins", "14", "#000000", "#000000", "#000000", "DROITE", "MAJUSCULES")));
        ThemeDocument perso = monde.entreprises.lire(new SaisieTheme("PERSONNALISE", "PlayfairDisplay", "Lato",
                "11", "#0a5c36", "#f1f3f4", "#111111", "DROITE", "NORMAL"));
        assertEquals("PlayfairDisplay", perso.policeTitres());
        assertEquals("#0A5C36", perso.couleurPrincipale());
        // texte foncé sur un en-tête clair, blanc sur un en-tête foncé
        assertEquals("#1F1F1F", perso.texteSurEntetes());
        assertEquals("#FFFFFF", ThemeDocument.MODERNE.texteSurEntetes());
    }

    @Test
    void lePersonnaliseEstControle() {
        assertThrows(ErreurMetier.class, () -> monde.entreprises.lire(new SaisieTheme("PERSONNALISE", "Inter",
                "Inter", "7", "#000000", "#000000", "#000000", "GAUCHE", "NORMAL")));
        assertThrows(ErreurMetier.class, () -> monde.entreprises.lire(new SaisieTheme("PERSONNALISE", "Inter",
                "Inter", "10", "red; background:url(x)", "#000000", "#000000", "GAUCHE", "NORMAL")));
        // une police ne peut pas sortir de sa règle CSS
        assertThrows(ErreurMetier.class, () -> monde.entreprises.lire(new SaisieTheme("PERSONNALISE",
                "Inter'; } body { display:none", "Inter", "10", "#000000", "#000000", "#000000", "GAUCHE",
                "NORMAL")));
        assertThrows(ErreurMetier.class, () -> monde.entreprises.lire(new SaisieTheme("FLUO", "Inter", "Inter",
                "10", "#000000", "#000000", "#000000", "GAUCHE", "NORMAL")));
    }

    @Test
    void leThemeEnregistreSAppliqueSansToucherAuReste() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        monde.entreprises.enregistrer(proprietaire(o), togolaise());

        monde.entreprises.definirTheme(proprietaire(o), new SaisieTheme("ELEGANT", "", "", "", "", "", "", "", ""));
        // réenregistrer l'identité garde le thème
        monde.entreprises.enregistrer(proprietaire(o), togolaise());

        Entreprise e = monde.entreprises.consulter(o.tenantId());
        assertEquals(ThemeDocument.ELEGANT, e.theme());
        assertEquals("BDP", e.sigle());
        String css = e.theme().feuille();
        assertTrue(css.contains("'Poppins', Helvetica"), css);
        assertTrue(css.contains("text-transform:uppercase"), css);
        assertTrue(ThemeDocument.CLASSIQUE.feuille().contains("'Merriweather', Times"));
    }

    @Test
    void lePiedLegalEtLesCoordonneesBancaires() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        monde.entreprises.enregistrer(proprietaire(o), togolaise());

        Entreprise e = monde.entreprises.consulter(o.tenantId());
        assertEquals("Boulangerie du Port SARL \u2022 Capital social : 1\u00a0000\u00a0000\u00a0F CFA"
                + " \u2022 NIF : 1000123456 \u2022 RCCM : TG-LFW-01-2024-B12-00001", e.piedLegal());
        assertEquals("Ecobank Togo - Titulaire : Boulangerie du Port SARL - IBAN : TG42 TG90 8010 0100 4521 8730 1642"
                + " - BIC/SWIFT : ECOCTGLX - Agence : Lomé Centre", e.banque().ligne());
        assertEquals("NIF", e.identifiantsEnTete().get(0).sigle());
    }

    @Test
    void lesMontantsSEcriventDansLaMonnaieSansCoupureEntreLesChiffres() {
        assertEquals("1\u00a0250\u00a0000\u00a0F CFA", Devise.XOF.formater(1_250_000));
        assertEquals("1\u00a0250,50\u00a0€", Devise.EUR.formaterDecimal(new java.math.BigDecimal("1250.5")));
    }
}
