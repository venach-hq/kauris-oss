package com.venach.kauris.ventes.achats;

import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.stock.application.StockService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les achats sur un vrai MySQL : fournisseur, commande, bon de commande en PDF, réception (entrée en stock),
 * facture fournisseur, paiement, dettes ; écrans de bout en bout. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AchatsMySqlTest {

    static final AtomicReference<IdentiteConnectee> CONNECTE = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {

        @Bean
        SessionKauris sessionDeTest() {
            return () -> Optional.ofNullable(CONNECTE.get());
        }
    }

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GererOrganisationUseCase organisations;
    @Autowired
    private ResoudreAccesUseCase acces;
    @Autowired
    private VentesService ventes;
    @Autowired
    private StockService stock;

    private final LocalDate jour = LocalDate.now(ZoneOffset.UTC);

    private Acces ouvrir(String nom) {
        String sujet = "sub-" + UUID.randomUUID();
        OrganisationOuverte o = organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet),
                sujet + "@exemple.test", "Awa Mensah"));
        CONNECTE.set(new IdentiteConnectee(Identite.venach(sujet), sujet + "@exemple.test", true, "Awa Mensah"));
        return acces.accesDe(Identite.venach(sujet)).stream().filter(a -> a.tenantId().equals(o.tenantId()))
                .findFirst().orElseThrow();
    }

    @Test
    void deLaCommandeALaReceptionEtAuPaiementDuFournisseur() throws Exception {
        Acces a = ouvrir("Quincaillerie du Port");
        Article ciment = ventes.creerArticle(a, new Article.Etat(null, null, Article.Type.BIEN, "CIM", "Ciment 50 kg",
                "sac", 6_000, new BigDecimal("18"), true, null, true, null));
        mvc.perform(get("/achats")).andExpect(status().isOk())
                .andExpect(content().string(containsString("À payer aux fournisseurs")));

        MvcResult f = mvc.perform(post("/achats/fournisseurs").param("nom", "Cimtogo").param("ville", "Lomé")
                        .param("delaiPaiement", "15"))
                .andExpect(redirectedUrlPattern("/achats/fournisseurs/*")).andReturn();
        String fournisseur = f.getResponse().getRedirectedUrl().replace("/achats/fournisseurs/", "");

        mvc.perform(get("/achats/commandes/nouvelle")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ciment 50 kg")));
        MvcResult c = mvc.perform(post("/achats/commandes").param("fournisseur", fournisseur)
                        .param("date", jour.toString()).param("objet", "Réassort")
                        .param("ligne.article", ciment.id().toString(), "")
                        .param("ligne.designation", "Ciment 50 kg", "").param("ligne.quantite", "100", "")
                        .param("ligne.unite", "sac", "").param("ligne.prix", "4 500", "")
                        .param("ligne.tva", "18", "18"))
                .andExpect(redirectedUrlPattern("/achats/commandes/*")).andReturn();
        String fiche = c.getResponse().getRedirectedUrl();
        mvc.perform(post(fiche + "/envoyer")).andExpect(flash().attribute("succes", containsString("BC-" + jour.getYear() + "-0001")));
        byte[] bon = mvc.perform(get(fiche + ".pdf")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        String texte = texte(bon);
        assertTrue(texte.contains("BON DE COMMANDE"), texte);
        assertTrue(texte.contains("Cimtogo"), texte);
        enregistrer("bon-de-commande.pdf", bon);

        UUID magasin = stock.depots(a, false).get(0).id();
        mvc.perform(post(fiche + "/recevoir").param("recu.0", "60").param("depot", magasin.toString())
                        .param("date", jour.toString()).param("reference", "BL-77"))
                .andExpect(flash().attribute("succes", containsString("BR-" + jour.getYear() + "-0001")));
        StockService.Etat etat = stock.etat(a, null, null, false);
        assertEquals(new BigDecimal("60.000"), etat.lignes().get(0).quantite());
        assertEquals(270_000, etat.valeurTotale());
        mvc.perform(get(fiche)).andExpect(content().string(containsString("Reçue en partie")));

        mvc.perform(get("/achats/factures/nouvelle").param("commande", fiche.replace("/achats/commandes/", "")))
                .andExpect(content().string(containsString("450000")));
        MvcResult x = mvc.perform(post("/achats/factures").param("commande", fiche.replace("/achats/commandes/", ""))
                        .param("fournisseur", fournisseur).param("reference", "FAC-889").param("date", jour.toString())
                        .param("ht", "270 000").param("tva", "48 600"))
                .andExpect(redirectedUrlPattern("/achats/factures/*")).andReturn();
        String facture = x.getResponse().getRedirectedUrl();
        mvc.perform(post("/achats/factures").param("fournisseur", fournisseur).param("reference", "fac-889")
                        .param("date", jour.toString()).param("ht", "1"))
                .andExpect(flash().attribute("erreur", containsString("déjà")));
        mvc.perform(post(facture + "/reglements").param("date", jour.toString()).param("montant", "100 000")
                .param("mode", "VIREMENT").param("reference", "VIR-1")).andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/achats/dettes")).andExpect(content().string(containsString("FAC-889")));
        mvc.perform(get(facture)).andExpect(content().string(containsString("218 600")));
        mvc.perform(get("/achats")).andExpect(content().string(containsString("218 600")));
        mvc.perform(get("/achats/fournisseurs/" + fournisseur)).andExpect(status().isOk());
        mvc.perform(get("/achats/commandes")).andExpect(status().isOk());
        mvc.perform(get("/achats/factures")).andExpect(status().isOk());

        ouvrir("Autre");
        mvc.perform(get(fiche)).andExpect(status().isNotFound());
    }

    private static String texte(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static void enregistrer(String nom, byte[] pdf) throws IOException {
        Path sortie = Path.of("target", "documents-exemple");
        Files.createDirectories(sortie);
        Files.write(sortie.resolve(nom), pdf);
    }
}

