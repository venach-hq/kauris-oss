package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembresEtAccesTest {

    private static final Set<AttributionRole> LECTEUR = Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR));
    private static final Set<AttributionRole> ADMIN =
            Set.of(AttributionRole.pourTouteLOrganisation(Role.ADMINISTRATEUR));
    private static final Set<AttributionRole> PROPRIETAIRE =
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE));

    private final Monde monde = new Monde();

    private static IdentiteConnectee connecte(String sujet, String email, boolean emailVerifie) {
        return new IdentiteConnectee(Identite.venach(sujet), email, emailVerifie, "Personne " + sujet);
    }

    // ------------------------------------------------------------------ invitations

    @Test
    void uneInvitationEstAccepteeALaPremiereConnexionAvecLeMemeEmailVerifie() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Membre invite = monde.membres.inviter(o.tenantId(), o.membreId(), " Kofi@Exemple.test", "Kofi", LECTEUR);
        assertEquals(StatutMembre.INVITE, invite.statut());
        assertTrue(monde.acces.accesDe(Identite.venach("kofi")).isEmpty());

        int rejointes = monde.acces.rattacherInvitations(connecte("kofi", "KOFI@exemple.test", true));

        assertEquals(1, rejointes);
        List<Acces> acces = monde.acces.accesDe(Identite.venach("kofi"));
        assertEquals(1, acces.size());
        assertEquals(o.tenantId(), acces.get(0).tenantId());
        assertEquals(invite.id(), acces.get(0).membreId());
        assertFalse(acces.get(0).administre());
        // une seconde connexion ne change rien
        assertEquals(0, monde.acces.rattacherInvitations(connecte("kofi", "kofi@exemple.test", true)));
    }

    @Test
    void unEmailNonVerifieNOuvreAucuneInvitation() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", ADMIN);

        assertEquals(0, monde.acces.rattacherInvitations(connecte("pirate", "kofi@exemple.test", false)));
        assertEquals(0, monde.acces.rattacherInvitations(connecte("pirate", null, true)));
        assertTrue(monde.acces.accesDe(Identite.venach("pirate")).isEmpty());
    }

    @Test
    void uneAdresseNEstInviteeQuUneFoisParOrganisation() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", LECTEUR);

        assertThrows(ErreurMetier.class,
                () -> monde.membres.inviter(o.tenantId(), o.membreId(), "KOFI@exemple.test", "Kofi B", LECTEUR));
        // le fondateur aussi a déjà cette adresse
        assertThrows(ErreurMetier.class,
                () -> monde.membres.inviter(o.tenantId(), o.membreId(), "awa@exemple.test", "Awa", LECTEUR));
    }

    @Test
    void unePersonnePeutEtreMembreDePlusieursOrganisations() {
        OrganisationOuverte port = monde.ouvrir("Boulangerie du Port", "awa");
        OrganisationOuverte garage = monde.ouvrir("Atelier Central", "kofi");
        monde.membres.inviter(garage.tenantId(), garage.membreId(), "awa@exemple.test", "Awa", LECTEUR);
        monde.acces.rattacherInvitations(connecte("awa", "awa@exemple.test", true));

        List<Acces> acces = monde.acces.accesDe(Identite.venach("awa"));

        assertEquals(List.of("Atelier Central", "Boulangerie du Port"),
                acces.stream().map(Acces::organisation).toList());
        assertFalse(acces.get(0).administre());
        assertTrue(acces.get(1).a(Role.PROPRIETAIRE));
        assertEquals(port.tenantId(), acces.get(1).tenantId());
    }

    @Test
    void unMembreDesactiveNAPlusAcces() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Membre kofi = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", LECTEUR);
        monde.acces.rattacherInvitations(connecte("kofi", "kofi@exemple.test", true));

        monde.membres.desactiver(o.tenantId(), o.membreId(), kofi.id());
        assertTrue(monde.acces.accesDe(Identite.venach("kofi")).isEmpty());

        monde.membres.reactiver(o.tenantId(), o.membreId(), kofi.id());
        assertEquals(1, monde.acces.accesDe(Identite.venach("kofi")).size());
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void seulUnProprietaireToucheAuRoleDeProprietaire() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");
        Membre admin = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", ADMIN);
        monde.acces.rattacherInvitations(connecte("kofi", "kofi@exemple.test", true));

        // un administrateur ne nomme pas de propriétaire, ne se promeut pas, ne rétrograde pas le propriétaire
        assertThrows(AccesRefuse.class, () -> monde.membres.inviter(o.tenantId(), admin.id(),
                "ami@exemple.test", "Ami", PROPRIETAIRE));
        assertThrows(AccesRefuse.class,
                () -> monde.membres.definirRoles(o.tenantId(), admin.id(), admin.id(), PROPRIETAIRE));
        assertThrows(AccesRefuse.class,
                () -> monde.membres.definirRoles(o.tenantId(), admin.id(), o.membreId(), LECTEUR));
        assertThrows(AccesRefuse.class,
                () -> monde.membres.desactiver(o.tenantId(), admin.id(), o.membreId()));

        // le propriétaire, lui, peut nommer un second propriétaire
        monde.membres.definirRoles(o.tenantId(), o.membreId(), admin.id(), PROPRIETAIRE);
        assertTrue(monde.membres.lister(o.tenantId()).stream().allMatch(Membre::estProprietaire));
    }

    @Test
    void lOrganisationGardeToujoursUnProprietaireActif() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie du Port", "awa");

        assertThrows(ErreurMetier.class,
                () -> monde.membres.definirRoles(o.tenantId(), o.membreId(), o.membreId(), ADMIN));
        assertThrows(ErreurMetier.class,
                () -> monde.membres.desactiver(o.tenantId(), o.membreId(), o.membreId()));
        assertTrue(monde.membres.lister(o.tenantId()).get(0).estProprietaire());

        // un second propriétaire seulement invité (pas encore connecté) ne compte pas
        Membre second = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                PROPRIETAIRE);
        assertThrows(ErreurMetier.class,
                () -> monde.membres.definirRoles(o.tenantId(), o.membreId(), o.membreId(), ADMIN));

        // une fois connecté, le premier peut passer la main
        monde.acces.rattacherInvitations(connecte("kofi", "kofi@exemple.test", true));
        monde.membres.definirRoles(o.tenantId(), o.membreId(), o.membreId(), ADMIN);
        assertThrows(ErreurMetier.class,
                () -> monde.membres.definirRoles(o.tenantId(), second.id(), second.id(), LECTEUR));
    }

    @Test
    void unRoleLimiteViseUnEtablissementOuvertDeLaMemeOrganisation() {
        OrganisationOuverte port = monde.ouvrir("Boulangerie du Port", "awa");
        OrganisationOuverte garage = monde.ouvrir("Atelier Central", "kofi");
        Etablissement kara = monde.etablissements.ajouter(port.tenantId(), port.membreId(), "Agence Kara");

        Membre gestionnaire = monde.membres.inviter(port.tenantId(), port.membreId(), "ama@exemple.test", "Ama",
                Set.of(AttributionRole.pourEtablissement(Role.GESTIONNAIRE, kara.id())));
        assertTrue(gestionnaire.peutModifier(kara.id()));
        assertFalse(gestionnaire.peutModifier(port.etablissementId()));

        // établissement d'une autre organisation
        assertThrows(ErreurMetier.class, () -> monde.membres.definirRoles(port.tenantId(), port.membreId(),
                gestionnaire.id(),
                Set.of(AttributionRole.pourEtablissement(Role.GESTIONNAIRE, garage.etablissementId()))));
        // établissement fermé
        monde.etablissements.fermer(port.tenantId(), port.membreId(), kara.id());
        assertThrows(ErreurMetier.class, () -> monde.membres.inviter(port.tenantId(), port.membreId(),
                "yao@exemple.test", "Yao", Set.of(AttributionRole.pourEtablissement(Role.LECTEUR, kara.id()))));
    }

    @Test
    void onNeGereQueLesMembresDeSonOrganisation() {
        OrganisationOuverte port = monde.ouvrir("Boulangerie du Port", "awa");
        OrganisationOuverte garage = monde.ouvrir("Atelier Central", "kofi");

        assertThrows(ErreurMetier.class,
                () -> monde.membres.desactiver(garage.tenantId(), garage.membreId(), port.membreId()));
        assertThrows(AccesRefuse.class,
                () -> monde.membres.inviter(port.tenantId(), garage.membreId(), "x@exemple.test", "Xavier", LECTEUR));
        assertEquals(1, monde.membres.lister(port.tenantId()).size());
    }
}
