package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.module.ModuleNoyau;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.ui.organisation.OrganisationController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContexteCourantTest {

    private final Monde monde = new Monde();
    private Identite connectee = Identite.venach("awa");
    private final SessionKauris session = () -> Optional.ofNullable(connectee)
            .map(i -> new IdentiteConnectee(i, i.sujet() + "@exemple.test", true, "Personne " + i.sujet()));
    private final ContexteCourant contexte = new ContexteCourant(session, monde.acces,
            new RegistreModules(List.of(new ModuleNoyau("test"))));
    private final MockHttpSession http = new MockHttpSession();

    private MockHttpServletRequest requete() {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setSession(http);
        return requete;
    }

    @Test
    void sansConnexionIlNYAPasDeContexte() {
        connectee = null;

        assertTrue(contexte.contexte(requete()).isEmpty());
        assertThrows(AucuneOrganisation.class, () -> contexte.exiger(requete()));
    }

    @Test
    void unePersonneSansOrganisationEstConnueMaisNAAucunAccesNiMenu() {
        ContexteKauris c = contexte.contexte(requete()).orElseThrow();

        assertNull(c.acces());
        assertTrue(c.menu().isEmpty());
        assertEquals("Personne awa", c.nomAffiche());
        assertThrows(AucuneOrganisation.class, () -> contexte.exiger(requete()));
    }

    @Test
    void laPremiereOrganisationEstCouranteEtOnPeutEnChanger() {
        monde.ouvrir("Boulangerie", "awa");
        OrganisationOuverte garage = monde.ouvrir("Garage", "awa");

        ContexteKauris avant = contexte.contexte(requete()).orElseThrow();
        assertEquals("Boulangerie", avant.acces().organisation());
        assertEquals(1, avant.autresAcces().size());
        assertFalse(avant.menu().isEmpty());

        contexte.choisir(requete(), garage.tenantId());

        assertEquals("Garage", contexte.exiger(requete()).organisation());
    }

    @Test
    void onNeChoisitPasUneOrganisationDontOnNEstPasMembre() {
        monde.ouvrir("Boulangerie", "awa");
        OrganisationOuverte autre = monde.ouvrir("Chez Kofi", "kofi");

        contexte.choisir(requete(), autre.tenantId());

        assertEquals("Boulangerie", contexte.exiger(requete()).organisation());
    }

    @Test
    void lesDroitsSontRelusAChaqueRequete() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "kofi");
        var invite = monde.membres.inviter(o.tenantId(), o.membreId(), "awa@exemple.test", "Awa",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));
        monde.acces.rattacherInvitations(session.personne().orElseThrow());
        assertFalse(contexte.exiger(requete()).administre());

        monde.membres.desactiver(o.tenantId(), o.membreId(), invite.id());

        assertThrows(AucuneOrganisation.class, () -> contexte.exiger(requete()));
    }

    @Test
    void unRoleLimiteDonneUneAttributionParEtablissementCoche() {
        UUID agence = UUID.randomUUID();
        UUID depot = UUID.randomUUID();

        assertEquals(Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)),
                OrganisationController.attributions("GESTIONNAIRE", null));
        assertEquals(Set.of(AttributionRole.pourEtablissement(Role.LECTEUR, agence),
                        AttributionRole.pourEtablissement(Role.LECTEUR, depot)),
                OrganisationController.attributions("LECTEUR", List.of(agence, depot)));
        // un rôle d'organisation ignore les établissements cochés
        assertEquals(Set.of(AttributionRole.pourTouteLOrganisation(Role.ADMINISTRATEUR)),
                OrganisationController.attributions("ADMINISTRATEUR", List.of(agence)));
    }
}
