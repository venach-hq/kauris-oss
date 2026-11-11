package com.venach.kauris.kernel.connexion.local;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase.Installation;
import com.venach.kauris.kernel.connexion.local.application.port.out.CompteLocalRepository;
import com.venach.kauris.kernel.connexion.local.application.port.out.EmpreinteMotDePasse;
import com.venach.kauris.kernel.connexion.local.application.service.ComptesLocauxService;
import com.venach.kauris.kernel.connexion.local.domain.CompteLocal;
import com.venach.kauris.kernel.organisation.EnMemoire;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComptesLocauxServiceTest {

    private static final String MOT_DE_PASSE = "pain-chaud-2026";

    /** Empreinte de test, visible et réversible : « empreinte:valeur ». */
    private static final EmpreinteMotDePasse EMPREINTE = new EmpreinteMotDePasse() {
        public String calculer(String motDePasse) {
            return "empreinte:" + motDePasse;
        }

        public boolean correspond(String motDePasse, String empreinte) {
            return empreinte.equals("empreinte:" + motDePasse);
        }
    };

    private static final class Comptes implements CompteLocalRepository {

        private final Map<UUID, CompteLocal> parId = new LinkedHashMap<>();

        public Optional<CompteLocal> parEmail(String email) {
            return parId.values().stream().filter(c -> c.email().equals(email)).findFirst();
        }

        public Optional<CompteLocal> trouver(UUID id) {
            return Optional.ofNullable(parId.get(id));
        }

        public void sauvegarder(CompteLocal compte) {
            parId.put(compte.id(), compte);
        }
    }

    private final Monde monde = new Monde();
    private final Comptes comptes = new Comptes();
    private final ComptesLocauxService service = new ComptesLocauxService(comptes, EMPREINTE, monde.organisations,
            monde.membresStockes, UniteDeTravail.DIRECTE, EnMemoire.HORLOGE);

    private OrganisationOuverte installer() {
        return service.installer(new Installation("Boulangerie du Port", "", "Awa Mensah", "Awa@Exemple.test",
                MOT_DE_PASSE));
    }

    @Test
    void lInstallationCreeLOrganisationEtLeCompteDuProprietaire() {
        assertTrue(service.installationAFaire());

        OrganisationOuverte o = installer();

        assertFalse(service.installationAFaire());
        CompteLocal compte = comptes.parEmail("awa@exemple.test").orElseThrow();
        assertEquals(o.tenantId(), compte.tenantId());
        assertNotEquals(MOT_DE_PASSE, compte.empreinteMotDePasse());
        List<Acces> acces = monde.acces.accesDe(compte.identite());
        assertEquals(1, acces.size());
        assertTrue(acces.get(0).a(Role.PROPRIETAIRE));
        assertEquals("Boulangerie du Port", acces.get(0).organisation());
    }

    @Test
    void lInstallationNeSeFaitQuUneFois() {
        installer();

        assertThrows(AccesRefuse.class, () -> service.installer(new Installation("Autre", "", "Pirate",
                "pirate@exemple.test", MOT_DE_PASSE)));
        assertTrue(comptes.parEmail("pirate@exemple.test").isEmpty());
    }

    @Test
    void unMotDePasseTropCourtOuEgalALEmailEstRefuseEtRienNEstCree() {
        assertThrows(ErreurMetier.class, () -> service.installer(new Installation("Boulangerie", "", "Awa",
                "awa@exemple.test", "court")));
        assertThrows(ErreurMetier.class, () -> service.installer(new Installation("Boulangerie", "", "Awa",
                "awa@exemple.test", "AWA@exemple.test")));
        assertTrue(service.installationAFaire());
        assertTrue(comptes.parId.isEmpty());
    }

    @Test
    void unAdministrateurDonneUnCompteAUnMembreInvite() {
        OrganisationOuverte o = installer();
        Membre kofi = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));

        service.creerComptePourMembre(o.tenantId(), o.membreId(), kofi.id(), "premier-passage");

        CompteLocal compte = comptes.parEmail("kofi@exemple.test").orElseThrow();
        assertEquals(1, monde.acces.accesDe(compte.identite()).size());
        // pas deux comptes pour le même membre
        assertThrows(ErreurMetier.class,
                () -> service.creerComptePourMembre(o.tenantId(), o.membreId(), kofi.id(), "second-passage"));
        // et un simple gestionnaire ne crée pas de compte
        Membre ama = monde.membres.inviter(o.tenantId(), o.membreId(), "ama@exemple.test", "Ama",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));
        assertThrows(AccesRefuse.class,
                () -> service.creerComptePourMembre(o.tenantId(), kofi.id(), ama.id(), "premier-passage"));
    }

    @Test
    void changerSonMotDePasseDemandeLActuel() {
        installer();
        CompteLocal compte = comptes.parEmail("awa@exemple.test").orElseThrow();

        assertThrows(ErreurMetier.class,
                () -> service.changerMotDePasse(compte.id(), "mauvais-mot-de-passe", "nouveau-mot-de-passe"));
        assertThrows(ErreurMetier.class, () -> service.changerMotDePasse(compte.id(), MOT_DE_PASSE, "court"));

        service.changerMotDePasse(compte.id(), MOT_DE_PASSE, "nouveau-mot-de-passe");
        assertTrue(EMPREINTE.correspond("nouveau-mot-de-passe",
                comptes.trouver(compte.id()).orElseThrow().empreinteMotDePasse()));
    }

    @Test
    void laConnexionEstRefuseeAUnMembreDesactive() {
        OrganisationOuverte o = installer();
        Membre kofi = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));
        service.creerComptePourMembre(o.tenantId(), o.membreId(), kofi.id(), "premier-passage");

        assertTrue(service.pourConnexion(" Kofi@Exemple.test ").orElseThrow().actif());
        assertTrue(service.pourConnexion("inconnu@exemple.test").isEmpty());
        assertTrue(service.pourConnexion("pas une adresse").isEmpty());

        monde.membres.desactiver(o.tenantId(), o.membreId(), kofi.id());

        assertFalse(service.pourConnexion("kofi@exemple.test").orElseThrow().actif());
    }
}
