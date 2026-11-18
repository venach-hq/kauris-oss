package com.venach.kauris.standalone;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.application.PortsCompta;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererRolesUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.TypeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    /** Une vraie image PNG d'un pixel : le PDF l'intègre. */
    private static final byte[] PNG_1X1 = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

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
    @Autowired
    private GererRolesUseCase roles;
    @Autowired
    private VentesService ventes;
    @Autowired
    private ComptaService compta;

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
        // L'icône de l'onglet est publique, comme la page de connexion qui l'affiche.
        mvc.perform(get("/kauris/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/favicon.ico")).andExpect(status().isOk());
        mvc.perform(get("/installation")).andExpect(content().string(containsString("/kauris/favicon.svg")));

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

        // Mon profil : chacun change son propre nom.
        mvc.perform(get("/profil").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Awa Mensah")))
                .andExpect(content().string(containsString("href=\"/compte\"")))
                // Aide et code source (licence AGPL-3.0) sur chaque page.
                .andExpect(content().string(containsString("https://github.com/venach-hq/kauris-oss/blob/main/docs/guide")))
                .andExpect(content().string(containsString("AGPL-3.0")));
        mvc.perform(post("/profil/nom").session(session).with(csrf()).param("nom", "Awa M. Mensah"))
                .andExpect(redirectedUrl("/profil"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/").session(session))
                .andExpect(content().string(containsString("Awa M. Mensah")));

        // Le module Ventes est installé : dans le menu, avec ses écrans, son script et ses formulaires protégés.
        assertTrue(registre.present("ventes"));
        mvc.perform(get("/ventes").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/ventes\"")))
                .andExpect(content().string(containsString("Tableau de bord")));
        mvc.perform(get("/ventes/documents/nouveau").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/ventes/ventes.js")));
        mvc.perform(get("/ventes/ventes.js").session(session)).andExpect(status().isOk());
        assertTrue(registre.present("achats-stock"));
        mvc.perform(get("/stock").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/stock\"")));
        mvc.perform(get("/achats").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/achats\"")));
        mvc.perform(get("/caisse").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/caisse\"")))
                .andExpect(content().string(containsString("Ouvrir ma caisse")));
        mvc.perform(get("/caisse/calculatrice.js").session(session)).andExpect(status().isOk());
        // L'accueil est un tableau de bord : une section par module, avec ses raccourcis.
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Reste à encaisser")))
                .andExpect(content().string(containsString("Ouvrir ma caisse")))
                .andExpect(content().string(containsString("Valeur du stock")))
                .andExpect(content().string(containsString("À payer aux fournisseurs")))
                .andExpect(content().string(containsString("Ajouter un véhicule")));
        // Une page inconnue : un message lisible.
        mvc.perform(get("/nexiste-pas").session(session)).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("existe pas")));

        // Le module Flotte est installé : menu, caisse du jour, formulaires protégés, exports.
        assertTrue(registre.present("flotte"));
        mvc.perform(get("/flotte").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/flotte\"")))
                .andExpect(content().string(containsString("Caisse du jour")));
        mvc.perform(post("/flotte/vehicules").session(session).with(csrf()).param("type", "MOTO")
                        .param("immatriculation", "TG-0001-A"))
                .andExpect(redirectedUrlPattern("/flotte/vehicules/*"));
        mvc.perform(post("/flotte/vehicules").session(session).param("type", "MOTO")
                        .param("immatriculation", "PIRATE"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/flotte/export/vehicules.xlsx").session(session)).andExpect(status().isOk());

        // Le module Paie est installé : menu, écrans, formulaires protégés.
        assertTrue(registre.present("rh-paie"));
        mvc.perform(get("/paie").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/paie\"")))
                .andExpect(content().string(containsString("Mois de paie")));
        mvc.perform(get("/paie/salaries/nouveau").session(session)).andExpect(status().isOk());
        mvc.perform(post("/paie/salaries").session(session).param("nom", "Pirate").param("situation", "CELIBATAIRE")
                        .param("modePaiement", "ESPECES"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ventes/clients").session(session).with(csrf()).param("type", "PARTICULIER")
                        .param("nom", "Yao Kossi"))
                .andExpect(redirectedUrlPattern("/ventes/clients/*"));
        mvc.perform(post("/ventes/clients").session(session).param("type", "PARTICULIER").param("nom", "Pirate"))
                .andExpect(status().isForbidden());

        // La comptabilité est installée : une facture émise et encaissée arrive au brouillard, sur les bons comptes.
        assertTrue(registre.present("compta-syscohada"));
        Acces patron = acces.accesDe(Identite.locale(comptes.pourConnexion(EMAIL).orElseThrow().compteId()
                .toString())).get(0);
        LocalDate jour = ventes.aujourdHui();
        Client yao = ventes.clients(patron, "Yao", false).get(0);
        DocumentVente facture = ventes.creerBrouillon(patron, TypeDocument.FACTURE, yao.id(), new DocumentVente.Contenu(
                jour, jour.plusDays(30), "Prestation", "", List.of(new Ligne(null, "Conseil", BigDecimal.ONE, "u",
                100_000, BigDecimal.ZERO, new BigDecimal("18"))), BigDecimal.ZERO, ""));
        facture = ventes.emettre(patron, facture.id());
        ventes.encaisser(patron, facture.id(), jour, 118_000, ModeReglement.ESPECES, "");
        List<Ecriture> recues = compta.ecritures(patron, new PortsCompta.Filtre(null, null, null, null, null, 50));
        assertEquals(2, recues.size(), "la facture et son règlement");
        Ecriture vente = recues.stream().filter(e -> e.journal().equals("VE")).findFirst().orElseThrow();
        assertEquals(List.of("411000", "706000", "443100"), vente.lignes().stream().map(l -> l.compte()).toList());
        assertEquals(118_000, vente.total());
        mvc.perform(get("/compta").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/compta\"")))
                .andExpect(content().string(containsString("Dernières écritures")));
        mvc.perform(post("/compta/brouillard/valider").session(session).with(csrf()).param("jusqua", jour.toString()))
                .andExpect(flash().attribute("succes", "2 écriture(s) validée(s) et numérotée(s)."));
        mvc.perform(post("/compta/lettrage/automatique").session(session).with(csrf()).param("compte", "411000"))
                .andExpect(flash().attribute("succes", "1 groupe(s) de lignes lettré(s)."));
        // Une écriture saisie à l'écran, puis corrigée au brouillard.
        String saisie = mvc.perform(post("/compta/ecritures").session(session).with(csrf()).param("journal", "OD")
                        .param("date", jour.toString()).param("piece", "REL-1").param("libelle", "Frais bancaires")
                        .param("ligne_compte_0", "631000 Frais bancaires").param("ligne_debit_0", "2 500")
                        .param("ligne_compte_1", "521000").param("ligne_credit_1", "2500"))
                .andExpect(redirectedUrlPattern("/compta/ecritures/*")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post("/compta/ecritures").session(session).with(csrf()).param("journal", "OD")
                        .param("date", jour.toString()).param("libelle", "Déséquilibrée")
                        .param("ligne_compte_0", "631000").param("ligne_debit_0", "2500")
                        .param("ligne_compte_1", "521000").param("ligne_credit_1", "2000"))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(get(saisie).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Écriture au brouillard")));
        mvc.perform(post("/compta/recettes-depenses").session(session).with(csrf()).param("sens", "DEPENSE")
                        .param("date", jour.toString()).param("tresorerie", "571000").param("depense", "605000")
                        .param("montant", "15000").param("libelle", "Fournitures"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/compta/ecritures").session(session).param("journal", "OD"))
                .andExpect(status().isForbidden());
        // Tous les écrans et les exports s'affichent, système normal puis système minimal.
        String annee = String.valueOf(jour.getYear());
        String mois = jour.toString().substring(0, 7);
        for (String page : List.of("/compta/ecritures", "/compta/ecritures?statut=BROUILLARD&q=Frais",
                "/compta/ecritures/nouvelle", "/compta/recettes-depenses", "/compta/operations",
                "/compta/grand-livre", "/compta/grand-livre?compte=411000", "/compta/balance",
                "/compta/balance?compte=411000&brouillard=non", "/compta/lettrage?compte=411000&toutes=1",
                "/compta/etats?annee=" + annee, "/compta/tva?mois=" + mois, "/compta/exercices", "/compta/parametres",
                "/compta/plan", "/compta/plan?q=banque", "/compta/ecritures/" + vente.id())) {
            mvc.perform(get(page).session(session)).andExpect(status().isOk());
        }
        for (String format : List.of("xlsx", "csv", "pdf")) {
            for (String export : List.of("/compta/balance." + format, "/compta/grand-livre." + format + "?compte=411000",
                    "/compta/grand-livre." + format, "/compta/etats/bilan." + format + "?annee=" + annee,
                    "/compta/etats/resultat." + format + "?annee=" + annee, "/compta/tva." + format + "?mois=" + mois,
                    "/compta/plan." + format)) {
                mvc.perform(get(export).session(session)).andExpect(status().isOk());
            }
        }
        mvc.perform(get("/compta/ecritures/" + vente.id() + ".pdf").session(session)).andExpect(status().isOk());
        mvc.perform(post("/compta/tva/liquider").session(session).with(csrf()).param("mois", mois))
                .andExpect(redirectedUrlPattern("/compta/ecritures/*"));
        mvc.perform(post("/compta/plan").session(session).with(csrf()).param("numero", "521100")
                        .param("libelle", "Banque Ecobank"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/compta/journaux").session(session).with(csrf()).param("code", "BQ1")
                        .param("libelle", "Banque Ecobank"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/compta/parametres").session(session).with(csrf()).param("systeme", "MINIMAL"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/compta/etats?annee=" + annee).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("TOTAL DES RECETTES")));
        mvc.perform(get("/compta/etats/bilan.pdf?annee=" + annee).session(session)).andExpect(status().isOk());
        mvc.perform(post("/compta/parametres").session(session).with(csrf()).param("systeme", "NORMAL"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Comptabilité")));

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

        // Notifications : Awa change les rôles de Kofi, Kofi est prévenu et ne voit que les siennes.
        mvc.perform(get("/notifications/non-lues").session(sessionKofi))
                .andExpect(jsonPath("$.nonLues").value(0));
        mvc.perform(post("/organisation/membres/" + kofi + "/roles").session(session).with(csrf())
                        .param("role", "GESTIONNAIRE"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/notifications/non-lues").session(sessionKofi))
                .andExpect(jsonPath("$.nonLues").value(1));
        mvc.perform(get("/notifications/non-lues").session(session))
                .andExpect(jsonPath("$.nonLues").value(0));
        mvc.perform(get("/notifications").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Vos rôles ont changé")))
                .andExpect(content().string(containsString("/kauris/kauris.js")));
        mvc.perform(post("/notifications/lues").session(sessionKofi).with(csrf()))
                .andExpect(redirectedUrl("/notifications"));
        mvc.perform(get("/notifications/non-lues").session(sessionKofi))
                .andExpect(jsonPath("$.nonLues").value(0));
        // Le canal WebSocket demande d'être connecté.
        mvc.perform(get("/ws/notifications")).andExpect(status().is3xxRedirection());

        // Paramètres de l'entreprise : identification selon le pays, monnaie, thème, logo, document d'exemple.
        mvc.perform(get("/entreprise").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Identification légale (Togo)")))
                .andExpect(content().string(containsString("identifiant.nif")));
        mvc.perform(get("/entreprise").param("pays", "SN").session(session))
                .andExpect(content().string(containsString("identifiant.ninea")));
        mvc.perform(post("/entreprise").session(session).with(csrf())
                        .param("denomination", "Boulangerie du Port").param("sigle", "BDP").param("forme", "SARL")
                        .param("capital", "1 000 000").param("pays", "TG").param("ville", "Lomé")
                        .param("adresse", "12 rue du Commerce").param("boitePostale", "1234")
                        .param("telephone", "+228 90 00 00 00").param("email", "contact@exemple.test")
                        .param("siteWeb", "").param("identifiant.rccm", "TG-LFW-01-2024-B12-00001")
                        .param("identifiant.nif", "1000123456").param("devise", "XOF")
                        .param("autresDevises", "EUR").param("banque", "Ecobank Togo")
                        .param("titulaireCompte", "Boulangerie du Port SARL")
                        .param("numeroCompte", "TG42 TG90 8010 0100 4521 8730 1642").param("bic", "")
                        .param("agence", "Lomé Centre").param("mentions", ""))
                .andExpect(redirectedUrl("/entreprise"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(multipart("/entreprise/logo").file(new MockMultipartFile("fichier", "logo.png", "image/png",
                        PNG_1X1))
                        .session(session).with(csrf()))
                .andExpect(redirectedUrl("/entreprise"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/entreprise/logo").session(session)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mvc.perform(get("/entreprise/apercu").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("1000123456")))
                .andExpect(content().string(containsString("Coordonnées bancaires")))
                .andExpect(content().string(containsString("'Inter', Helvetica")))
                .andExpect(content().string(containsString("data:image/png;base64,")))
                .andExpect(content().string(containsString("/entreprise/apercu.pdf")));

        // Thème des documents : trois modèles, un personnalisé à l'aperçu sans l'enregistrer, puis enregistré.
        mvc.perform(get("/entreprise/theme").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Moderne")))
                .andExpect(content().string(containsString("Classique")))
                .andExpect(content().string(containsString("Élégant")));
        mvc.perform(get("/entreprise/apercu").session(session).param("modele", "PERSONNALISE")
                        .param("policeTitres", "Poppins").param("policeTexte", "Lato").param("taille", "11")
                        .param("couleurPrincipale", "#0a5c36").param("couleurEntetes", "#0a5c36")
                        .param("couleurTexte", "#111111").param("logo", "DROITE").param("titre", "NORMAL"))
                .andExpect(content().string(containsString("'Poppins', Helvetica")));
        mvc.perform(post("/entreprise/theme").session(session).with(csrf()).param("modele", "CLASSIQUE"))
                .andExpect(redirectedUrl("/entreprise/theme"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/entreprise/apercu").session(session))
                .andExpect(content().string(containsString("'Merriweather', Times")));
        byte[] pdf = mvc.perform(get("/entreprise/apercu.pdf").session(session)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
        mvc.perform(get("/kauris/polices/inconnue.ttf")).andExpect(status().isNotFound());

        // Gestionnaire, Kofi voit l'entreprise mais ne la modifie pas.
        mvc.perform(get("/entreprise").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Envoyer le logo"))));
        mvc.perform(post("/entreprise/logo/retirer").session(sessionKofi).with(csrf()))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(post("/entreprise/theme").session(sessionKofi).with(csrf()).param("modele", "ELEGANT"))
                .andExpect(flash().attributeExists("erreur"));

        // Un rôle « Caissier » créé par Awa : Kofi ne voit plus que ce qui y est coché.
        mvc.perform(post("/organisation/roles").session(session).with(csrf())
                        .param("nom", "Caissier").param("description", "Tient la caisse")
                        .param("permissions", "entreprise.consulter"))
                .andExpect(redirectedUrl("/organisation/roles"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/organisation/roles").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Caissier")));
        UUID caissier = roles.lister(tenant).get(0).id();
        mvc.perform(post("/organisation/membres/" + kofi + "/roles").session(session).with(csrf())
                        .param("role", "PERSO:" + caissier))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Caissier")))
                .andExpect(content().string(containsString("/entreprise")))
                .andExpect(content().string(not(containsString("/organisation/membres"))));
        mvc.perform(get("/organisation/membres").session(sessionKofi)).andExpect(status().isForbidden());
        mvc.perform(get("/entreprise").session(sessionKofi)).andExpect(status().isOk());
        // Seul le propriétaire définit les rôles ; un rôle donné ne se supprime pas.
        mvc.perform(post("/organisation/roles").session(sessionKofi).with(csrf()).param("nom", "Intrus"))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(post("/organisation/roles/" + caissier + "/supprimer").session(session).with(csrf()))
                .andExpect(flash().attributeExists("erreur"));

        // Désactivé, Kofi perd l'accès tout de suite, sans attendre la fin de sa session.
        mvc.perform(post("/organisation/membres/" + kofi + "/desactiver").session(session).with(csrf()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/").session(sessionKofi)).andExpect(redirectedUrl("/sans-organisation"));
        mvc.perform(get("/sans-organisation").session(sessionKofi)).andExpect(status().isOk())
                .andExpect(content().string(containsString("membre actif")));
        mvc.perform(get("/organisation").session(sessionKofi)).andExpect(redirectedUrl("/sans-organisation"));
    }
}
