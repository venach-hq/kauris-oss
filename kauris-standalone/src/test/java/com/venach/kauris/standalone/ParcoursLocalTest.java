package com.venach.kauris.standalone;

import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le mode autonome de bout en bout sur un vrai MySQL : premier démarrage, installation, connexion locale,
 * puis les écrans du noyau (établissements, membres, rôles) sur l'interface commune.
 * Un seul scénario, dans l'ordre, parce que l'installation ne se fait qu'une fois par base. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ParcoursLocalTest {

    private static final String EMAIL = "awa@exemple.test";
    private static final String MOT_DE_PASSE = "pain-chaud-2026";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistreModules registre;
    @Autowired
    private GererComptesLocauxUseCase comptes;
    @Autowired
    private ResoudreAccesUseCase acces;
    @Autowired
    private GererMembresUseCase membres;
    @Autowired
    private GererEtablissementsUseCase etablissements;

    @Test
    void premierDemarragePuisConnexion() throws Exception {
        assertTrue(registre.present("kernel"));

        // Rien n'est installé : tout mène à l'installation, sans connexion.
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));
        mvc.perform(get("/connexion")).andExpect(redirectedUrl("/installation"));
        mvc.perform(get("/installation")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Installer Kauris")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // Un mot de passe trop court est refusé avec un message, et rien n'est créé.
        mvc.perform(post("/installation").with(csrf())
                        .param("nomOrganisation", "Boulangerie du Port").param("nom", "Awa Mensah")
                        .param("email", EMAIL).param("motDePasse", "court"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("entre 10 et 128")));
        // Sans jeton CSRF, la demande est refusée.
        mvc.perform(post("/installation").param("nomOrganisation", "Pirate"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/installation").with(csrf())
                        .param("nomOrganisation", "Boulangerie du Port").param("nom", "Awa Mensah")
                        .param("email", EMAIL).param("motDePasse", MOT_DE_PASSE))
                .andExpect(redirectedUrl("/connexion?installe"));

        // L'installation est fermée pour toujours.
        mvc.perform(get("/installation")).andExpect(redirectedUrl("/connexion"));
        mvc.perform(post("/installation").with(csrf())
                        .param("nomOrganisation", "Autre").param("nom", "Pirate")
                        .param("email", "pirate@exemple.test").param("motDePasse", MOT_DE_PASSE))
                .andExpect(redirectedUrl("/connexion"));
        mvc.perform(formLogin("/connexion").userParameter("email").passwordParam("motDePasse")
                        .user("pirate@exemple.test").password(MOT_DE_PASSE))
                .andExpect(unauthenticated());

        // Mauvais mot de passe, puis le bon.
        mvc.perform(formLogin("/connexion").userParameter("email").passwordParam("motDePasse")
                        .user(EMAIL).password("mauvais-mot-de-passe"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/connexion?erreur"));
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/connexion")
                        .userParameter("email").passwordParam("motDePasse").user(EMAIL).password(MOT_DE_PASSE))
                .andExpect(authenticated().withUsername(EMAIL))
                .andReturn().getRequest().getSession(false);

        // L'accueil est sur l'interface commune : organisation, rôle, menu, feuille de style.
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boulangerie du Port")))
                .andExpect(content().string(containsString("Propriétaire")))
                .andExpect(content().string(containsString("/kauris/kauris.css")))
                .andExpect(content().string(containsString("Mon compte")));
        mvc.perform(get("/kauris/kauris.css")).andExpect(status().isOk());

        // Établissements.
        mvc.perform(get("/organisation").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Siège")));
        mvc.perform(post("/organisation/etablissements").session(session).with(csrf()).param("nom", "Agence Kara"))
                .andExpect(redirectedUrl("/organisation"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/organisation").session(session))
                .andExpect(content().string(containsString("Agence Kara")));

        // Un invité, son compte local, sa connexion.
        mvc.perform(post("/organisation/membres").session(session).with(csrf())
                        .param("nom", "Kofi").param("email", "kofi@exemple.test").param("role", "LECTEUR"))
                .andExpect(redirectedUrl("/organisation/membres"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/organisation/membres").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("kofi@exemple.test")))
                .andExpect(content().string(containsString("Invité")))
                .andExpect(content().string(containsString("Inviter un membre")));
        UUID tenant = acces.accesDe(Identite.locale(
                comptes.pourConnexion(EMAIL).orElseThrow().compteId().toString())).get(0).tenantId();
        UUID kofi = membres.lister(tenant).stream().filter(m -> m.nom().equals("Kofi")).findFirst()
                .orElseThrow().id();
        mvc.perform(post("/organisation/membres/" + kofi + "/compte-local").session(session).with(csrf())
                        .param("motDePasse", "premier-passage"))
                .andExpect(redirectedUrl("/organisation/membres"))
                .andExpect(flash().attributeExists("succes"));
        MockHttpSession sessionKofi = (MockHttpSession) mvc.perform(formLogin("/connexion")
                        .userParameter("email").passwordParam("motDePasse")
                        .user("kofi@exemple.test").password("premier-passage"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);

        // Un lecteur consulte mais ne modifie pas : le noyau refuse, l'écran affiche le refus.
        mvc.perform(get("/organisation/membres").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Inviter un membre"))));
        mvc.perform(post("/organisation/etablissements").session(sessionKofi).with(csrf()).param("nom", "Intrus"))
                .andExpect(redirectedUrl("/organisation"))
                .andExpect(flash().attributeExists("erreur"));
        assertEquals(2, etablissements.lister(tenant).size());

        // Changer son mot de passe demande l'actuel.
        mvc.perform(post("/compte/mot-de-passe").session(sessionKofi).with(csrf())
                        .param("actuel", "mauvais-mot-de-passe").param("nouveau", "second-passage"))
                .andExpect(redirectedUrl("/compte"))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(get("/compte").session(sessionKofi)).andExpect(status().isOk());

        // Désactivé, Kofi perd l'accès tout de suite, sans attendre la fin de sa session.
        mvc.perform(post("/organisation/membres/" + kofi + "/desactiver").session(session).with(csrf()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/").session(sessionKofi)).andExpect(redirectedUrl("/sans-organisation"));
        mvc.perform(get("/sans-organisation").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(containsString("membre actif")));
        mvc.perform(get("/organisation").session(sessionKofi)).andExpect(redirectedUrl("/sans-organisation"));
    }
}
