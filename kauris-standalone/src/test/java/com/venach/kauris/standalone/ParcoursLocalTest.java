package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le mode autonome de bout en bout sur un vrai MySQL : premier démarrage, installation, connexion locale.
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

        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boulangerie du Port")))
                .andExpect(content().string(containsString("Propriétaire")));
    }
}
