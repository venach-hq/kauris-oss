package com.venach.kauris.flotte;

import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.Versement;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.SessionKauris;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

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
 * La flotte sur un vrai MySQL : migrations et schéma, parcours de la caissière par les écrans (encaisser, reçu,
 * demande de paiement, jour férié), corrections du contrat, exports CSV, Excel et PDF. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FlotteMySqlTest {

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
    private FlotteService flotte;

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
    void parcoursDeLaCaissiere() throws Exception {
        Acces a = ouvrir("Zem Express");
        mvc.perform(get("/flotte")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun contrat en cours")));

        // Le véhicule, le conducteur et le contrat, par les écrans.
        mvc.perform(post("/flotte/vehicules").param("type", "MOTO").param("immatriculation", "ab 1234 rt")
                        .param("marque", "Haojue").param("referencePaiement", "90 00 00 01"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/flotte/vehicules").param("type", "MOTO").param("immatriculation", "AB-1234-RT"))
                .andExpect(flash().attribute("erreur", containsString("AB-1234-RT")));
        mvc.perform(post("/flotte/conducteurs").param("nom", "Koffi Mensah").param("telephone", "90 12 34 56")
                        .param("permisExpireLe", jour.plusYears(2).toString()))
                .andExpect(flash().attributeExists("succes"));
        UUID moto = flotte.vehicules(a).get(0).id();
        UUID koffi = flotte.conducteurs(a).get(0).id();
        assertEquals("AB-1234-RT", flotte.vehicules(a).get(0).immatriculation());

        // Contrat de location commencé il y a 10 jours, 5 000 par jour, sans jour de repos.
        mvc.perform(post("/flotte/contrats").param("vehicule", moto.toString()).param("conducteur", koffi.toString())
                        .param("type", "LOCATION").param("debut", jour.minusDays(10).toString())
                        .param("parJour", "5 000"))
                .andExpect(flash().attributeExists("succes"));
        Contrat c = flotte.contrats(a).get(0);
        assertEquals("CT-" + jour.minusDays(10).getYear() + "-0001", c.numero());
        Compte.Situation s = flotte.compte(a, c.id());
        assertEquals(Compte.Etat.EN_RETARD, s.etat());
        assertEquals(10, s.joursEnRetard());
        assertEquals(55_000, s.aEncaisser(), "10 jours de retard et le jour même");

        mvc.perform(get("/flotte")).andExpect(status().isOk())
                .andExpect(content().string(containsString("AB-1234-RT")))
                .andExpect(content().string(containsString("Demander le paiement")));
        mvc.perform(get("/flotte/contrats/" + c.id() + "/encaisser")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Encaisser et imprimer le reçu")));

        // Un jour férié déclaré par la caissière : un jour de moins à payer.
        mvc.perform(post("/flotte/feries").param("date", jour.minusDays(3).toString()).param("libelle", "Fête"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/flotte/feries").param("date", jour.minusDays(3).toString()).param("libelle", "Fête"))
                .andExpect(flash().attribute("erreur", containsString("déjà férié")));
        assertEquals(50_000, flotte.compte(a, c.id()).aEncaisser());

        // Un versement en Mobile Money sans référence est refusé ; en espèces, il mène au reçu.
        mvc.perform(post("/flotte/contrats/" + c.id() + "/encaisser").param("montant", "20000")
                        .param("mode", "MOBILE_MONEY").param("reference", ""))
                .andExpect(flash().attributeExists("erreur"));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/encaisser").param("montant", "20 000")
                        .param("mode", "ESPECES"))
                .andExpect(redirectedUrlPattern("/flotte/versements/*"));
        Versement v = flotte.versementsDuContrat(a, c.id()).get(0);
        assertEquals("VR-" + jour.getYear() + "-000001", v.numero());
        s = flotte.compte(a, c.id());
        assertEquals(jour.minusDays(10).plusDays(3), s.payeJusquAu(), "4 jours payés, du plus ancien");
        mvc.perform(get("/flotte/versements/" + v.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("REÇU DE VERSEMENT")))
                .andExpect(content().string(containsString("https://wa.me/")));

        // Sans demande de paiement branchée : le message tout prêt.
        mvc.perform(post("/flotte/contrats/" + c.id() + "/demander"))
                .andExpect(redirectedUrl("/flotte/contrats/" + c.id() + "/message"));
        mvc.perform(get("/flotte/contrats/" + c.id() + "/message")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Bonjour Koffi Mensah")))
                .andExpect(content().string(containsString("90 00 00 01")));

        // Corrections : amende, pause pour panne, nouveau tarif, fin et réouverture.
        mvc.perform(post("/flotte/contrats/" + c.id() + "/ajustements").param("type", "AMENDE").param("montant", "2000")
                        .param("motif", "Retard de restitution").param("date", jour.toString()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/pauses").param("du", jour.minusDays(2).toString())
                        .param("au", jour.minusDays(1).toString()).param("motif", "Panne moteur"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/tarif").param("depuis", jour.plusDays(1).toString())
                        .param("montant", "6000"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/tarif").param("depuis", "pas une date")
                        .param("montant", "6000"))
                .andExpect(flash().attribute("erreur", containsString("date invalide")));
        s = flotte.compte(a, c.id());
        // 11 jours − 1 férié − 2 de pause = 8 jours × 5 000 + 2 000 d'amende − 20 000 payés.
        assertEquals(-22_000, s.solde());
        mvc.perform(get("/flotte/contrats/" + c.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Relevé du compte")))
                .andExpect(content().string(containsString("Panne moteur")));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/terminer").param("dernierJour", jour.toString())
                        .param("motif", ""))
                .andExpect(flash().attribute("succes", "Contrat terminé."));
        mvc.perform(post("/flotte/contrats/" + c.id() + "/rouvrir")).andExpect(flash().attributeExists("succes"));
        assertTrue(flotte.contrat(a, c.id()).enCours());

        // Annuler le versement : il ne compte plus.
        mvc.perform(post("/flotte/versements/" + v.id() + "/annuler").param("motif", "Erreur de montant"))
                .andExpect(flash().attributeExists("succes"));
        assertEquals(-42_000, flotte.compte(a, c.id()).solde());

        // Papiers : l'assurance qui expire bientôt s'affiche sur la caisse.
        mvc.perform(post("/flotte/papiers").param("vehicule", moto.toString()).param("type", "ASSURANCE")
                        .param("reference", "POL-77").param("expireLe", jour.plusDays(10).toString())
                        .param("cout", "45 000").param("retour", "/flotte/vehicules/" + moto))
                .andExpect(redirectedUrl("/flotte/vehicules/" + moto));
        mvc.perform(get("/flotte")).andExpect(content().string(containsString("À surveiller")));

        // Tous les écrans.
        for (String page : new String[]{"/flotte/versements", "/flotte/contrats", "/flotte/contrats?tous=true",
                "/flotte/contrats/nouveau", "/flotte/vehicules", "/flotte/vehicules/" + moto, "/flotte/vehicules/nouveau",
                "/flotte/conducteurs", "/flotte/conducteurs/" + koffi, "/flotte/conducteurs/nouveau", "/flotte/papiers",
                "/flotte/feries", "/flotte?voir=retard"}) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }

        // Exports.
        byte[] csv = mvc.perform(get("/flotte/export/situation.csv")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse()
                .getContentAsByteArray();
        assertTrue(new String(csv, StandardCharsets.UTF_8).contains("AB-1234-RT"));
        byte[] xlsx = mvc.perform(get("/flotte/export/versements.xlsx?du=" + jour.minusDays(30) + "&au=" + jour))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(contient(xlsx, "xl/worksheets/sheet1.xml", "VR-"));
        for (String quoi : new String[]{"situation", "versements", "vehicules", "conducteurs", "papiers",
                "releve"}) {
            byte[] pdf = mvc.perform(get("/flotte/export/" + quoi + ".pdf" + (quoi.equals("releve") ? "?contrat="
                            + c.id() : ""))).andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf")).andReturn().getResponse()
                    .getContentAsByteArray();
            String texte = texte(pdf).replaceAll("\\s+", "");
            assertTrue(texte.contains(quoi.equals("conducteurs") ? "KoffiMensah" : quoi.equals("versements")
                    ? "VR-" : "AB-1234-RT"), quoi + " : " + texte);
            enregistrer("flotte-" + quoi + ".pdf", pdf);
        }
        mvc.perform(get("/flotte/export/inconnu.csv")).andExpect(status().isNotFound());

        // Une autre organisation ne voit rien.
        ouvrir("Autre");
        mvc.perform(get("/flotte/contrats/" + c.id())).andExpect(status().isNotFound());
        mvc.perform(get("/flotte/versements/" + v.id())).andExpect(status().isNotFound());
    }

    private static boolean contient(byte[] zip, String entree, String texte) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                if (e.getName().equals(entree)) {
                    return new String(z.readAllBytes(), StandardCharsets.UTF_8).contains(texte);
                }
            }
        }
        return false;
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
