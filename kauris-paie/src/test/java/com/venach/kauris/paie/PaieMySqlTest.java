package com.venach.kauris.paie;

import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.TypeElement;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La paie sur un vrai MySQL : migrations et schéma, parcours par les écrans (salarié, contrat, mois, éléments du
 * mois, clôture, paiement), bulletin et tableaux en PDF, Excel et CSV, réglages. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaieMySqlTest {

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
    private GererEntrepriseUseCase entreprises;
    @Autowired
    private PaieService paie;

    private final YearMonth mois = YearMonth.now(ZoneOffset.UTC);

    private Acces ouvrir(String nom, String pays) {
        String sujet = "sub-" + UUID.randomUUID();
        OrganisationOuverte o = organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet),
                sujet + "@exemple.test", "Awa Mensah"));
        CONNECTE.set(new IdentiteConnectee(Identite.venach(sujet), sujet + "@exemple.test", true, "Awa Mensah"));
        Acces a = acces.accesDe(Identite.venach(sujet)).stream().filter(x -> x.tenantId().equals(o.tenantId()))
                .findFirst().orElseThrow();
        if (pays != null) {
            entreprises.enregistrer(a, new GererEntrepriseUseCase.Saisie(nom, "", "SARL", "1 000 000", pays, "Lomé",
                    "12 rue du Commerce", "", "+228 90 00 00 00", "contact@exemple.test", "",
                    Map.of("rccm", "TG-LFW-01-2024-B12-00001", "nif", "1000123456", "cnss", "EMP-1234"), "XOF",
                    Set.of("XOF"), "Ecobank Togo", nom, "TG42 0000 0000 0000", "", "", ""));
        }
        return a;
    }

    @Test
    void moisDePaieParLesEcrans() throws Exception {
        Acces a = ouvrir("Boulangerie du Port", "TG");
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ouvrir la paie du mois")))
                .andExpect(content().string(containsString("ajouter un salarié")));

        // Un salarié, puis son contrat avec une prime de transport non soumise.
        mvc.perform(post("/paie/salaries").param("nom", "Mensah").param("prenoms", "Akosua")
                        .param("situation", "MARIE").param("enfants", "2").param("conjointACharge", "on")
                        .param("numeroSocial", "CNSS-778899").param("modePaiement", "VIREMENT")
                        .param("banque", "Ecobank").param("compte", "TG001 0000 1234"))
                .andExpect(redirectedUrlPattern("/paie/salaries/*/contrats/nouveau"));
        mvc.perform(post("/paie/salaries").param("nom", "Agbeko").param("situation", "CELIBATAIRE")
                        .param("modePaiement", "VIREMENT").param("compte", ""))
                .andExpect(flash().attribute("erreur", containsString("compte bancaire")));
        Salarie s = paie.salaries(a).get(0).salarie();
        assertEquals("S-0001", s.matricule());
        mvc.perform(post("/paie/salaries/" + s.id() + "/contrats").param("type", "CDI").param("poste", "Comptable")
                        .param("debut", mois.minusYears(1).atDay(1).toString()).param("salaireBase", "300 000")
                        .param("element_libelle_0", "Transport").param("element_type_0", "GAIN_NON_SOUMIS")
                        .param("element_montant_0", "20 000").param("element_libelle_1", "")
                        .param("element_montant_1", ""))
                .andExpect(flash().attributeExists("succes"));
        ContratTravail c = paie.contratsDuSalarie(a, s.id()).get(0);
        assertEquals(1, c.elements().size());
        assertEquals(TypeElement.GAIN_NON_SOUMIS, c.elements().get(0).type());
        mvc.perform(post("/paie/salaries/" + s.id() + "/contrats").param("type", "CDD").param("poste", "Caissière")
                        .param("debut", mois.atDay(1).toString()).param("salaireBase", "100000"))
                .andExpect(flash().attributeExists("erreur"));

        // Le mois, ouvert par l'écran.
        mvc.perform(post("/paie/mois").param("mois", mois.toString()))
                .andExpect(redirectedUrlPattern("/paie/mois/*"));
        PeriodePaie p = paie.periodes(a).get(0);
        mvc.perform(post("/paie/mois").param("mois", mois.toString()))
                .andExpect(flash().attribute("erreur", containsString("existe déjà")));
        mvc.perform(get("/paie/mois/" + p.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("MENSAH Akosua")))
                .andExpect(content().string(containsString("Clôturer le mois")));

        // Éléments du mois : une prime et une avance.
        String ici = "/paie/mois/" + p.id() + "/salaries/" + s.id();
        mvc.perform(post(ici + "/variables").param("joursAbsence", "0")
                        .param("element_libelle_0", "Prime de fin d'année").param("element_type_0", "GAIN_SOUMIS")
                        .param("element_montant_0", "50000")
                        .param("element_libelle_1", "Avance").param("element_type_1", "RETENUE")
                        .param("element_montant_1", "40 000"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post(ici + "/variables").param("joursAbsence", "trois"))
                .andExpect(flash().attribute("erreur", containsString("nombre entier")));
        Bulletin b = paie.bulletinDe(a, p.id(), s.id());
        assertEquals(370_000, b.resultat().brut());
        assertEquals(40_000, b.resultat().retenues());
        mvc.perform(get(ici)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Prime de fin d")))
                .andExpect(content().string(containsString("Enregistrer et recalculer")));
        mvc.perform(get(ici + "/bulletin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Brouillon")))
                .andExpect(content().string(containsString("Net à payer")));

        // Clôture, puis paiement.
        mvc.perform(post("/paie/mois/" + p.id() + "/clore")).andExpect(flash().attributeExists("succes"));
        b = paie.bulletinDe(a, p.id(), s.id());
        assertEquals("BP-" + mois.getYear() + String.format("%02d", mois.getMonthValue()) + "-0001", b.numero());
        mvc.perform(get("/paie/mois/" + p.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString(b.numero())));
        mvc.perform(post("/paie/mois/" + p.id() + "/verser"))
                .andExpect(flash().attribute("erreur", containsString("fichier de virement")));

        // Le bulletin en PDF.
        byte[] pdf = mvc.perform(get(ici + "/bulletin.pdf")).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        String texte = texte(pdf);
        assertTrue(!texte.contains("font-size") && !texte.contains("white-space"), "pas de CSS imprimé : " + texte);
        String serre = texte.replaceAll("\\s+", "");
        assertTrue(serre.contains("MENSAHAkosua") && serre.contains(b.numero()) && serre.contains("IRPP"), texte);
        enregistrer("paie-bulletin.pdf", pdf);

        // Les tableaux du mois, dans les trois formats.
        for (String quoi : new String[]{"livre", "sociale", "fiscale", "virements"}) {
            byte[] tableau = mvc.perform(get("/paie/mois/" + p.id() + "/export/" + quoi + ".pdf"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertTrue(texte(tableau).replaceAll("\\s+", "").contains("MENSAHAkosua"), quoi);
            enregistrer("paie-" + quoi + ".pdf", tableau);
            mvc.perform(get("/paie/mois/" + p.id() + "/export/" + quoi + ".xlsx")).andExpect(status().isOk());
        }
        byte[] csv = mvc.perform(get("/paie/mois/" + p.id() + "/export/sociale.csv")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse()
                .getContentAsByteArray();
        assertTrue(new String(csv, StandardCharsets.UTF_8).contains("CNSS-778899"));
        byte[] xlsx = mvc.perform(get("/paie/mois/" + p.id() + "/export/virements.xlsx")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(contient(xlsx, "xl/worksheets/sheet1.xml", "TG001 0000 1234"));
        mvc.perform(get("/paie/mois/" + p.id() + "/export/inconnu.csv")).andExpect(status().isNotFound());

        mvc.perform(post("/paie/mois/" + p.id() + "/payer").param("date", LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/paie/mois/" + p.id() + "/rouvrir"))
                .andExpect(flash().attribute("erreur", containsString("payés")));
        assertTrue(paie.periode(a, p.id()).payeLe() != null);

        // Réglages : le taux d'accident du travail corrigé à partir du mois suivant.
        mvc.perform(post("/paie/reglages").param("parametre", Parametre.TG_CNSS_RISQUES_EMPLOYEUR.name())
                        .param("depuis", mois.plusMonths(1).toString()).param("valeur", "2,5")
                        .param("note", "Lettre de la CNSS"))
                .andExpect(flash().attributeExists("succes"));
        assertEquals(1, paie.corrections(a).size());
        mvc.perform(get("/paie/reglages")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lettre de la CNSS")));

        // Tous les écrans.
        for (String page : new String[]{"/paie", "/paie/salaries", "/paie/salaries/nouveau", "/paie/salaries/" + s.id(),
                "/paie/salaries/" + s.id() + "/contrats/nouveau", "/paie/contrats/" + c.id(), "/paie/reglages", ici}) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }

        // Une autre organisation (au Sénégal, où la paie ne calcule pas encore) ne voit rien.
        ouvrir("Autre", "SN");
        mvc.perform(get("/paie/mois/" + p.id())).andExpect(status().isNotFound());
        mvc.perform(get("/paie/salaries/" + s.id())).andExpect(status().isNotFound());
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Togo et la Côte d")));
    }

    @Test
    void coteDIvoire() throws Exception {
        Acces a = ouvrir("Ivoire Services", "CI");
        mvc.perform(post("/paie/salaries").param("nom", "Kouassi").param("situation", "MARIE").param("enfants", "1")
                        .param("modePaiement", "MOBILE_MONEY").param("compte", "07 07 07 07 07").param("expatrie", "on"))
                .andExpect(flash().attributeExists("succes"));
        Salarie s = paie.salaries(a).get(0).salarie();
        mvc.perform(post("/paie/salaries/" + s.id() + "/contrats").param("type", "CDI").param("poste", "Directeur")
                        .param("debut", mois.minusMonths(3).atDay(1).toString()).param("salaireBase", "1500000"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/paie/mois").param("mois", mois.toString())).andExpect(redirectedUrlPattern("/paie/mois/*"));
        PeriodePaie p = paie.periodes(a).get(0);
        Bulletin b = paie.bulletinDe(a, p.id(), s.id());
        assertEquals(0, b.parts().compareTo(new java.math.BigDecimal("2.5")));
        assertEquals(156_000, b.resultat().employeurDe("DGI"), "10,4 % pour un expatrié");
        mvc.perform(get("/paie/mois/" + p.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("ITS")));
        byte[] pdf = mvc.perform(get("/paie/mois/" + p.id() + "/salaries/" + s.id() + "/bulletin.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = texte(pdf).replaceAll("\\s+", "");
        assertTrue(texte.contains("CNPS") && texte.contains("CMU") && texte.contains("ITS"), texte);
        enregistrer("paie-bulletin-ci.pdf", pdf);
        mvc.perform(get("/paie/mois/" + p.id() + "/export/fiscale.pdf")).andExpect(status().isOk());
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
