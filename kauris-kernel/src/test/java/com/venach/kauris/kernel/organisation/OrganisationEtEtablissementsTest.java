package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganisationEtEtablissementsTest {

    private final Monde monde = new Monde();

    @Test
    void ouvrirCreeLOrganisationSonSiegeEtSonProprietaire() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");

        assertEquals("Boulangerie du Port", monde.organisations.consulter(o.tenantId()).nom());
        List<Etablissement> etablissements = monde.etablissements.lister(o.tenantId());
        assertEquals(1, etablissements.size());
        assertEquals("Siège", etablissements.get(0).nom());
        assertTrue(etablissements.get(0).estPrincipal());
        assertEquals(o.etablissementId(), etablissements.get(0).id());
        Membre fondateur = monde.membres.lister(o.tenantId()).get(0);
        assertEquals(o.membreId(), fondateur.id());
        assertTrue(fondateur.estProprietaire());
        assertTrue(monde.organisations.auMoinsUneOrganisation());
    }

    @Test
    void lesEtablissementsSontListesPrincipalDAbordPuisParNom() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        monde.etablissements.ajouter(o.tenantId(), o.membreId(), "Zongo");
        monde.etablissements.ajouter(o.tenantId(), o.membreId(), "agence Kara");

        assertEquals(List.of("Siège", "agence Kara", "Zongo"),
                monde.etablissements.lister(o.tenantId()).stream().map(Etablissement::nom).toList());
    }

    @Test
    void deuxEtablissementsNePortentPasLeMemeNom() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Etablissement kara = monde.etablissements.ajouter(o.tenantId(), o.membreId(), "Agence Kara");

        assertThrows(ErreurMetier.class,
                () -> monde.etablissements.ajouter(o.tenantId(), o.membreId(), "agence kara"));
        assertThrows(ErreurMetier.class,
                () -> monde.etablissements.renommer(o.tenantId(), o.membreId(), kara.id(), "SIÈGE"));
        // se renommer avec son propre nom (casse différente) reste permis
        monde.etablissements.renommer(o.tenantId(), o.membreId(), kara.id(), "AGENCE KARA");
    }

    @Test
    void changerDePrincipalPuisFermerLAncien() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Etablissement kara = monde.etablissements.ajouter(o.tenantId(), o.membreId(), "Agence Kara");

        assertThrows(ErreurMetier.class,
                () -> monde.etablissements.fermer(o.tenantId(), o.membreId(), o.etablissementId()));

        monde.etablissements.definirPrincipal(o.tenantId(), o.membreId(), kara.id());
        monde.etablissements.fermer(o.tenantId(), o.membreId(), o.etablissementId());

        List<Etablissement> liste = monde.etablissements.lister(o.tenantId());
        assertEquals(1, liste.stream().filter(Etablissement::estPrincipal).count());
        assertEquals("Agence Kara", liste.get(0).nom());
        assertFalse(liste.get(1).estOuvert());
    }

    @Test
    void uneOrganisationNeVoitNiNeModifieLesEtablissementsDUneAutre() {
        OrganisationOuverte port = monde.ouvrir("Boulangerie du Port", "awa");
        OrganisationOuverte garage = monde.ouvrir("Garage Central", "kofi");

        assertEquals(1, monde.etablissements.lister(garage.tenantId()).size());
        // le propriétaire du garage n'est rien dans la boulangerie
        assertThrows(AccesRefuse.class,
                () -> monde.etablissements.ajouter(port.tenantId(), garage.membreId(), "Intrus"));
        // et ne peut pas viser un établissement de la boulangerie depuis son organisation
        assertThrows(ElementIntrouvable.class, () -> monde.etablissements.renommer(garage.tenantId(),
                garage.membreId(), port.etablissementId(), "Piraté"));
        assertEquals("Siège", monde.etablissements.lister(port.tenantId()).get(0).nom());
    }

    @Test
    void seulsProprietairesEtAdministrateursModifient() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Membre lecteur = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));

        assertThrows(AccesRefuse.class,
                () -> monde.etablissements.ajouter(o.tenantId(), lecteur.id(), "Agence"));
        assertThrows(AccesRefuse.class,
                () -> monde.organisations.renommer(o.tenantId(), lecteur.id(), "Autre nom"));
        assertThrows(AccesRefuse.class,
                () -> monde.organisations.renommer(o.tenantId(), UUID.randomUUID(), "Autre nom"));

        monde.organisations.renommer(o.tenantId(), o.membreId(), "Boulangerie du Grand Port");
        assertEquals("Boulangerie du Grand Port", monde.organisations.consulter(o.tenantId()).nom());
    }
}
