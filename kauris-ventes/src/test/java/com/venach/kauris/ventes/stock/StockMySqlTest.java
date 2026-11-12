package com.venach.kauris.ventes.stock;

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
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.TypeDocument;
import com.venach.kauris.ventes.stock.application.StockService;
import com.venach.kauris.ventes.stock.domain.Depot;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le stock sur un vrai MySQL : migrations et schéma, coût moyen relu de la base, sortie à l'émission d'une
 * facture dans la même transaction, transfert, inventaire, écrans et PDF. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StockMySqlTest {

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
    void duStockInitialALaFactureEtALInventaire() throws Exception {
        Acces a = ouvrir("Quincaillerie Lumière");
        mvc.perform(get("/stock")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun article suivi")));
        Article tole = ventes.creerArticle(a, new Article.Etat(null, null, Article.Type.BIEN, "TOL-35", "Tôle bac 3,5 m",
                "feuille", 9_000, new BigDecimal("18"), true, new BigDecimal("10"), true, null));
        UUID magasin = stock.depots(a, false).get(0).id();
        assertEquals(Depot.NOM_PAR_DEFAUT, stock.depots(a, false).get(0).nom());

        // Deux entrées à des coûts différents : coût moyen (20 × 6 000 + 20 × 7 000) / 40 = 6 500.
        mvc.perform(post("/stock/mouvement").param("type", "STOCK_INITIAL").param("article", tole.id().toString())
                        .param("depot", magasin.toString()).param("quantite", "20").param("cout", "6 000")
                        .param("date", jour.toString()).param("libelle", "").param("reference", ""))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/stock/mouvement").param("type", "ENTREE").param("article", tole.id().toString())
                        .param("depot", magasin.toString()).param("quantite", "20").param("cout", "7000")
                        .param("date", jour.toString()).param("libelle", "Achat Sotoco").param("reference", "BL-88"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/stock/mouvement").param("type", "ENTREE").param("article", tole.id().toString())
                        .param("depot", magasin.toString()).param("quantite", "1").param("cout", "7000,50")
                        .param("date", jour.toString()))
                .andExpect(flash().attribute("erreur", containsString("sans décimales")));
        assertEquals(6_500, stock.etat(a, null, null, false).lignes().get(0).cmup());

        // Une facture de 25 tôles fait sortir 25 du magasin principal, au coût moyen.
        Client c = ventes.creerClient(a, new Client.Etat(null, null, Client.Type.ENTREPRISE, "BTP Kara", "", "", "",
                "", "", "", "", "", 0, "", true, null));
        DocumentVente f = ventes.creerBrouillon(a, TypeDocument.FACTURE, c.id(), new DocumentVente.Contenu(jour, jour,
                "", "", List.of(tole.ligne(new BigDecimal("25"))), null, ""));
        ventes.emettre(a, f.id());
        StockService.Etat etat = stock.etat(a, null, null, false);
        assertEquals(new BigDecimal("15.000"), etat.lignes().get(0).quantite());
        assertEquals(97_500, etat.valeurTotale());
        assertEquals(0, etat.alertes());

        // Transfert vers une boutique, puis inventaire de la boutique : 4 manquent → stock bas.
        mvc.perform(post("/stock/depots").param("nom", "Boutique Kara")).andExpect(flash().attributeExists("succes"));
        UUID boutique = stock.depots(a, false).stream().filter(d -> !d.principal()).findFirst().orElseThrow().id();
        mvc.perform(post("/stock/transfert").param("article", tole.id().toString()).param("de", magasin.toString())
                        .param("vers", boutique.toString()).param("quantite", "10").param("date", jour.toString()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/stock/inventaire").param("depot", boutique.toString())
                        .param("compte." + tole.id(), "6").param("date", jour.toString()).param("reference", "INV-1"))
                .andExpect(flash().attribute("succes", containsString("1 écart")));
        etat = stock.etat(a, null, null, false);
        assertEquals(new BigDecimal("11.000"), etat.lignes().get(0).quantite());
        assertEquals(new BigDecimal("6.000"), stock.etat(a, boutique, null, false).lignes().get(0).quantite());
        assertEquals(71_500, etat.valeurTotale(), "les 4 manquants sortent au coût moyen");
        assertEquals(0, stock.etat(a, null, null, true).lignes().size(), "11 au-dessus du seuil de 10");

        mvc.perform(get("/stock")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tôle bac 3,5 m")));
        mvc.perform(get("/stock/articles/" + tole.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString(f.id().toString())));
        mvc.perform(get("/stock/mouvements")).andExpect(status().isOk());
        mvc.perform(get("/stock/inventaire")).andExpect(status().isOk());
        mvc.perform(get("/stock/depots")).andExpect(status().isOk());
        byte[] pdf = mvc.perform(get("/stock/documents/etat.pdf")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        String texte = texte(pdf);
        assertTrue(texte.contains("ÉTAT DU STOCK"), texte);
        assertTrue(texte.contains("Tôle bac 3,5 m"), texte);
        enregistrer("etat-du-stock.pdf", pdf);
        byte[] fiche = mvc.perform(get("/stock/articles/" + tole.id() + "/fiche.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(texte(fiche).contains("FICHE DE STOCK"), texte(fiche));
        enregistrer("fiche-de-stock.pdf", fiche);

        // Une autre organisation ne voit rien.
        ouvrir("Autre");
        mvc.perform(get("/stock/articles/" + tole.id())).andExpect(status().isNotFound());
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
