package com.venach.kauris.ventes;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.DocumentVente.Contenu;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.TypeDocument;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les ventes sur un vrai MySQL : migrations, validation du schéma par Hibernate, relecture de tout ce qui est
 * écrit, numérotation, puis les écrans et les PDF à travers l'interface commune. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VentesMySqlTest {

    /** Qui est « connecté » pour les écrans : changé par chaque test. */
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
    private Ports.Documents documents;
    @Autowired
    private RegistreModules registre;

    private final LocalDate jour = LocalDate.now(ZoneOffset.UTC);

    private Acces ouvrir(String nom) {
        String sujet = "sub-" + UUID.randomUUID();
        OrganisationOuverte o = organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet),
                sujet + "@exemple.test", "Awa Mensah"));
        CONNECTE.set(new IdentiteConnectee(Identite.venach(sujet), sujet + "@exemple.test", true, "Awa Mensah"));
        return acces.accesDe(Identite.venach(sujet)).stream().filter(a -> a.tenantId().equals(o.tenantId()))
                .findFirst().orElseThrow();
    }

    private Client client(Acces a, String nom) {
        return ventes.creerClient(a, new Client.Etat(null, null, Client.Type.ENTREPRISE, nom, "M. Koffi",
                "compta@exemple.test", "+228 90 00 00 00", "Rue du Commerce", "Lomé", "Togo", "1000123456",
                "TG-LFW-2020-B-123", 30, "", true, null));
    }

    @Test
    void toutSeRelitDeLaBaseEtLaNumerotationSeSuitParOrganisation() {
        Acces a = ouvrir("Quincaillerie du Port");
        assertTrue(registre.present("ventes"));
        Client c = client(a, "Société Alpha");
        Article cable = ventes.creerArticle(a, new Article.Etat(null, null, Article.Type.BIEN, "CAB-2.5",
                "Câble électrique 2,5 mm²", "m", 850, new BigDecimal("18"), true, null));
        ventes.definirReglages(a, true, new BigDecimal("18"), "", 30, 15, "Pénalité de 1 % par mois de retard.");

        DocumentVente devis = ventes.creerBrouillon(a, TypeDocument.DEVIS, c.id(), new Contenu(jour,
                jour.plusDays(15), "Chantier Bè", "BC-77", List.of(cable.ligne(new BigDecimal("120.5")),
                new Ligne(null, "Main-d'œuvre", new BigDecimal("2"), "jour", 25_000, new BigDecimal("10"),
                        new BigDecimal("18"))), new BigDecimal("5"), "Livraison sous 48 h."));
        devis = ventes.emettre(a, devis.id());
        DocumentVente facture = ventes.emettre(a, ventes.facturerDevis(a, devis.id()).id());
        ventes.encaisser(a, facture.id(), jour, 50_000, ModeReglement.MOBILE_MONEY, "MX-1");

        DocumentVente relue = documents.document(a.tenantId(), facture.id()).orElseThrow();
        assertEquals(TypeDocument.FACTURE.numero(jour.getYear(), 1), relue.numero());
        assertEquals("Société Alpha", relue.client().nom());
        assertEquals("TG-LFW-2020-B-123", relue.client().rccm());
        assertEquals(2, relue.contenu().lignes().size());
        assertEquals("Câble électrique 2,5 mm²", relue.contenu().lignes().get(0).designation());
        assertEquals(new BigDecimal("120.5"), relue.contenu().lignes().get(0).quantite());
        assertEquals(cable.id(), relue.contenu().lignes().get(0).articleId());
        // 102 425 + 45 000 = 147 425 ; remise 5 % → 140 054 HT ; TVA 18 % → 25 210 ; TTC 165 264
        assertEquals(140_054, relue.totaux().totalHt());
        assertEquals(25_210, relue.totaux().totalTva());
        assertEquals(165_264, relue.totaux().totalTtc());
        assertEquals(50_000, relue.regle());
        assertEquals("Pénalité de 1 % par mois de retard.", relue.conditions());
        assertEquals(1, ventes.reglements(a, facture.id()).size());
        assertEquals(1, ventes.impayes(a).size());
        assertEquals(devis.id(), ventes.issusDe(a, devis.id()).get(0).origineId());

        // Une autre organisation recommence à 1, et ne voit rien de la première.
        Acces b = ouvrir("Pharmacie Lumière");
        Client cb = client(b, "Client B");
        DocumentVente fb = ventes.creerBrouillon(b, TypeDocument.FACTURE, cb.id(), new Contenu(jour, jour, "", "",
                List.of(new Ligne(null, "Conseil", BigDecimal.ONE, "", 10_000, null, null)), null, ""));
        assertEquals(TypeDocument.FACTURE.numero(jour.getYear(), 1), ventes.emettre(b, fb.id()).numero());
        assertTrue(documents.document(b.tenantId(), facture.id()).isEmpty());
        assertTrue(ventes.documents(b, Ports.Filtre.tous(0)).stream().noneMatch(d -> d.id().equals(facture.id())));

        // La recherche et le filtre des impayés passent par la base.
        assertEquals(1, ventes.documents(a, new Ports.Filtre(null, null, null, null, "alpha", true, 0)).size());
        assertEquals(0, ventes.documents(a, new Ports.Filtre(null, null, null, null, "100%_", false, 0)).size());

        // Un brouillon supprimé emporte ses lignes ; l'avoir réduit la facture relue.
        DocumentVente avoir = ventes.avoirSur(a, facture.id());
        ventes.emettre(a, avoir.id());
        DocumentVente apres = documents.document(a.tenantId(), facture.id()).orElseThrow();
        assertEquals(165_264, apres.credite());
        assertEquals(-50_000, apres.resteAPayer());
        DocumentVente jete = ventes.creerBrouillon(a, TypeDocument.DEVIS, c.id(), new Contenu(jour, null, "", "",
                List.of(cable.ligne(BigDecimal.ONE)), null, ""));
        ventes.supprimerBrouillon(a, jete.id());
        assertTrue(documents.document(a.tenantId(), jete.id()).isEmpty());
    }

    @Test
    void lesEcransMenentDuClientALaFactureEnPdf() throws Exception {
        Acces a = ouvrir("Boulangerie du Port");
        mvc.perform(get("/ventes")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tableau de bord")))
                .andExpect(content().string(containsString("Nouvelle facture")));

        MvcResult cree = mvc.perform(post("/ventes/clients").param("type", "ENTREPRISE")
                        .param("nom", "Hôtel Sarakawa").param("contact", "Mme Adjo").param("ville", "Lomé")
                        .param("pays", "Togo").param("nif", "1000999999").param("delaiPaiement", "15"))
                .andExpect(redirectedUrlPattern("/ventes/clients/*")).andReturn();
        UUID clientId = UUID.fromString(cree.getResponse().getRedirectedUrl().replace("/ventes/clients/", ""));

        mvc.perform(get("/ventes/documents/nouveau").param("type", "FACTURE").param("client", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hôtel Sarakawa")))
                .andExpect(content().string(containsString(jour.plusDays(15).toString())));

        // Une ligne mal saisie : le brouillon n'est pas créé, la personne garde un message clair.
        mvc.perform(post("/ventes/documents").param("type", "FACTURE").param("client", clientId.toString())
                        .param("date", jour.toString()).param("echeance", jour.toString())
                        .param("ligne.article", "").param("ligne.designation", "Pain")
                        .param("ligne.quantite", "2").param("ligne.unite", "").param("ligne.prix", "25 000,50")
                        .param("ligne.remise", "").param("ligne.tva", "18"))
                .andExpect(redirectedUrlPattern("/ventes/documents/nouveau*"))
                .andExpect(flash().attribute("erreur", containsString("sans décimales")));

        MvcResult brouillon = mvc.perform(post("/ventes/documents").param("type", "FACTURE")
                        .param("client", clientId.toString()).param("date", jour.toString())
                        .param("echeance", jour.plusDays(15).toString()).param("objet", "Petits-déjeuners")
                        .param("remiseGlobale", "")
                        .param("ligne.article", "", "", "").param("ligne.designation", "Croissants", "Pain", "")
                        .param("ligne.quantite", "200", "1,5", "").param("ligne.unite", "u", "kg", "")
                        .param("ligne.prix", "250", "1 000", "").param("ligne.remise", "", "", "")
                        .param("ligne.tva", "18", "0", "18"))
                .andExpect(redirectedUrlPattern("/ventes/documents/*")).andReturn();
        String fiche = brouillon.getResponse().getRedirectedUrl();
        UUID id = UUID.fromString(fiche.replace("/ventes/documents/", ""));
        mvc.perform(get(fiche)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Émettre")))
                .andExpect(content().string(containsString("Croissants")));

        mvc.perform(post(fiche + "/emettre")).andExpect(redirectedUrl(fiche));
        String numero = TypeDocument.FACTURE.numero(jour.getYear(), 1);
        mvc.perform(get(fiche)).andExpect(content().string(containsString(numero)))
                .andExpect(content().string(containsString("Enregistrer un règlement")));
        mvc.perform(post(fiche + "/reglements").param("date", jour.toString()).param("montant", "20 000")
                .param("mode", "ESPECES").param("reference", "")).andExpect(redirectedUrl(fiche));

        // 200 × 250 = 50 000 à 18 % (9 000) + 1,5 × 1 000 = 1 500 sans TVA → 60 500 TTC ; 20 000 réglés
        DocumentVente lu = ventes.document(a, id);
        assertEquals(60_500, lu.totaux().totalTtc());
        assertEquals(40_500, lu.resteAPayer());

        mvc.perform(get(fiche + "/imprimer")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Télécharger le PDF")))
                .andExpect(content().string(containsString("soixante mille cinq cents francs CFA")));
        byte[] pdf = mvc.perform(get(fiche + ".pdf")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        String texte = texte(pdf);
        assertTrue(texte.contains("FACTURE"), texte);
        assertTrue(texte.contains(numero), texte);
        assertTrue(texte.contains("Hôtel Sarakawa"), texte);
        assertTrue(texte.contains("Reste à payer"), texte);
        assertTrue(texte.contains("TVA 18 %"), texte);
        enregistrer("facture.pdf", pdf);

        byte[] releve = mvc.perform(get("/ventes/clients/" + clientId + "/releve.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(texte(releve).contains("RELEVÉ DE COMPTE CLIENT"), texte(releve));
        enregistrer("releve-client.pdf", releve);

        mvc.perform(get("/ventes/impayes")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hôtel Sarakawa")));
        mvc.perform(get("/ventes/documents").param("type", "FACTURE")).andExpect(status().isOk())
                .andExpect(content().string(containsString(numero)));
        mvc.perform(get("/ventes/catalogue")).andExpect(status().isOk());
        mvc.perform(get("/ventes/reglages")).andExpect(status().isOk())
                .andExpect(content().string(containsString("factures normalisées")));

        // Une autre organisation ne voit pas la facture.
        ouvrir("Autre entreprise");
        mvc.perform(get(fiche)).andExpect(status().isNotFound());
        mvc.perform(get(fiche + ".pdf")).andExpect(status().isNotFound());
    }

    private static String texte(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** Les PDF produits, gardés par la CI pour être regardés. */
    private static void enregistrer(String nom, byte[] pdf) throws IOException {
        Path sortie = Path.of("target", "documents-exemple");
        Files.createDirectories(sortie);
        Files.write(sortie.resolve(nom), pdf);
    }
}
