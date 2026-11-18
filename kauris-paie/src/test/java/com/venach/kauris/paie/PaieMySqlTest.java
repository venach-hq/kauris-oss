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
import com.venach.kauris.paie.adapter.web.FormulaireReglesTest;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.ReglesPaie;
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
        // Sans règles : l'accueil propose les règles recommandées ; un mois ne s'ouvre pas.
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Activer et vérifier ces règles")));
        mvc.perform(post("/paie/mois").param("mois", mois.toString()))
                .andExpect(flash().attribute("erreur", containsString("Aucune règle")));
        mvc.perform(get("/paie/regles")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Togo — règles recommandées 2026")));
        mvc.perform(post("/paie/regles/activer").param("modele", ModelesRecommandes.TOGO_2026))
                .andExpect(redirectedUrlPattern("/paie/regles/*"));
        ReglesPaie regles = paie.regles(a).get(0);
        mvc.perform(get("/paie/regles/" + regles.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("aucun écart")))
                .andExpect(content().string(containsString("CNSS_PENSION")));
        // L'entreprise corrige son taux de risques professionnels dans l'éditeur.
        Map<String, String> champs = FormulaireReglesTest.champs(regles.contenu());
        champs.put("cot_2_te", "2,5");
        champs.put("nom", "Togo — Boulangerie");
        champs.put("depuis", "2026-01");
        champs.put("notes", "Taux notifié par la CNSS");
        var requete = post("/paie/regles/" + regles.id());
        champs.forEach(requete::param);
        mvc.perform(requete).andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/paie/regles/" + regles.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Recommandé : 2")))
                .andExpect(content().string(containsString("Togo — Boulangerie")));
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ouvrir la paie du mois")))
                .andExpect(content().string(containsString("ajouter un salarié")));

        // Un salarié, puis son contrat avec une prime de transport non soumise.
        mvc.perform(post("/paie/salaries").param("nom", "Mensah").param("prenoms", "Akosua")
                        .param("situation", "MARIE").param("enfants", "2").param("conjointACharge", "on")
                        .param("autresPersonnes", "1").param("regime", "NORMAL")
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
                        .param("heure_maj_0", "Heures de jour, jours ouvrables|15").param("heure_nombre_0", "4")
                        .param("element_libelle_0", "Prime de fin d'année").param("element_type_0", "GAIN_SOUMIS")
                        .param("element_montant_0", "50000")
                        .param("element_libelle_1", "Avance").param("element_type_1", "RETENUE")
                        .param("element_montant_1", "40 000"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post(ici + "/variables").param("joursAbsence", "trois"))
                .andExpect(flash().attribute("erreur", containsString("nombre entier")));
        Bulletin b = paie.bulletinDe(a, p.id(), s.id());
        // 300 000 + transport 20 000 + prime 50 000 + 4 h à +15 % (300 000 / 173,33 × 1,15 × 4 = 7 962).
        assertEquals(370_000 + 7_962, b.resultat().brut());
        assertEquals(40_000, b.resultat().retenues());
        assertEquals("4 personne(s) à charge", b.famille());
        // Un prêt, retenu dès ce mois-ci.
        mvc.perform(post("/paie/salaries/" + s.id() + "/avances").param("type", "PRET").param("libelle", "Moto")
                        .param("montant", "100 000").param("mensualite", "25 000").param("premierMois", mois.toString()))
                .andExpect(flash().attributeExists("succes"));
        assertEquals(40_000 + 25_000, paie.bulletinDe(a, p.id(), s.id()).resultat().retenues());
        mvc.perform(get("/paie/salaries/" + s.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Prêt · Moto")));
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
                .andExpect(flash().attribute("erreur", containsString("ordre de virement")));

        // Le bulletin en PDF.
        byte[] pdf = mvc.perform(get(ici + "/bulletin.pdf")).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        String texte = texte(pdf);
        assertTrue(!texte.contains("font-size") && !texte.contains("white-space"), "pas de CSS imprimé : " + texte);
        String serre = texte.replaceAll("\\s+", "");
        assertTrue(serre.contains("MENSAHAkosua") && serre.contains(b.numero()) && serre.contains("IRPP"), texte);
        enregistrer("paie-bulletin.pdf", pdf);

        // Les tableaux du mois, dans les trois formats.
        for (String quoi : new String[]{"livre", "sociale", "fiscale", "virements", "ecritures"}) {
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

        // Une nouvelle version des règles le mois suivant : le mois clos garde ses bulletins.
        mvc.perform(post("/paie/regles/" + regles.id() + "/version").param("depuis", mois.plusMonths(1).toString()))
                .andExpect(redirectedUrlPattern("/paie/regles/*"));
        assertEquals(2, paie.regles(a).size());
        byte[] ecritures = mvc.perform(get("/paie/mois/" + p.id() + "/export/ecritures.csv")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(ecritures, StandardCharsets.UTF_8).contains("422000"));

        // Tous les écrans.
        for (String page : new String[]{"/paie", "/paie/salaries", "/paie/salaries/nouveau", "/paie/salaries/" + s.id(),
                "/paie/salaries/" + s.id() + "/contrats/nouveau", "/paie/contrats/" + c.id(), "/paie/regles",
                "/paie/regles/" + regles.id(), ici}) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }

        // Une autre organisation (au Sénégal, sans règles activées) ne voit rien.
        ouvrir("Autre", "SN");
        mvc.perform(get("/paie/mois/" + p.id())).andExpect(status().isNotFound());
        mvc.perform(get("/paie/salaries/" + s.id())).andExpect(status().isNotFound());
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucune règle de paie")));
        mvc.perform(post("/paie/regles/activer").param("modele", "VIDE").param("pays", "SENEGAL")
                        .param("depuis", mois.toString()))
                .andExpect(redirectedUrlPattern("/paie/regles/*"));
    }

    @Test
    void coteDIvoire() throws Exception {
        Acces a = ouvrir("Ivoire Services", "CI");
        paie.activerModele(a, ModelesRecommandes.COTE_D_IVOIRE_2024, null, null);
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
        assertEquals("2,5 part(s)", b.famille());
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

    @Test
    void senegal() throws Exception {
        Acces a = ouvrir("Teranga Services", "SN");
        mvc.perform(get("/paie")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sénégal — règles recommandées 2026")));
        mvc.perform(post("/paie/regles/activer").param("modele", ModelesRecommandes.SENEGAL_2026))
                .andExpect(redirectedUrlPattern("/paie/regles/*"));
        ReglesPaie regles = paie.regles(a).get(0);
        assertEquals(ModelesRecommandes.SENEGAL_2026, regles.modele());
        // L'éditeur affiche la TRIMF, la réduction en pourcentage et le plafond ; on les renvoie tels quels.
        mvc.perform(get("/paie/regles/" + regles.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("frf_0_pal_5_montant")))
                .andExpect(content().string(containsString("red_7_parts")))
                .andExpect(content().string(containsString("imp_plafond")));
        var form = post("/paie/regles/" + regles.id()).param("nom", regles.nom())
                .param("depuis", regles.depuis().toString().substring(0, 7)).param("notes", regles.notes());
        for (var e : FormulaireReglesTest.champs(regles.contenu()).entrySet()) {
            form.param(e.getKey(), e.getValue());
        }
        mvc.perform(form).andExpect(flash().attributeExists("succes"));
        assertEquals(regles.contenu(), paie.reglesParId(a, regles.id()).contenu());

        mvc.perform(post("/paie/salaries").param("nom", "Diop").param("situation", "MARIE").param("enfants", "2")
                        .param("conjointACharge", "on").param("cadre", "on").param("modePaiement", "VIREMENT")
                        .param("banque", "CBAO").param("compte", "SN012 01001 000123456789 12"))
                .andExpect(flash().attributeExists("succes"));
        Salarie s = paie.salaries(a).get(0).salarie();
        assertTrue(s.cadre());
        mvc.perform(get("/paie/salaries/" + s.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"cadre\"")));
        mvc.perform(post("/paie/salaries/" + s.id() + "/contrats").param("type", "CDI").param("poste", "Directeur")
                        .param("debut", mois.minusMonths(3).atDay(1).toString()).param("salaireBase", "1000000"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post("/paie/mois").param("mois", mois.toString())).andExpect(redirectedUrlPattern("/paie/mois/*"));
        PeriodePaie p = paie.periodes(a).get(0);
        Bulletin b = paie.bulletinDe(a, p.id(), s.id());
        assertEquals("3 part(s)", b.famille());
        assertEquals(219_938, b.resultat().impot(), "IR 216 938 + TRIMF 3 000");
        assertEquals(724_370, b.net());
        byte[] pdf = mvc.perform(get("/paie/mois/" + p.id() + "/salaries/" + s.id() + "/bulletin.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = texte(pdf).replaceAll("\\s+", "");
        assertTrue(texte.contains("IPRES") && texte.contains("TRIMF") && texte.contains("CFCE"), texte);
        enregistrer("paie-bulletin-sn.pdf", pdf);
        byte[] fiscale = mvc.perform(get("/paie/mois/" + p.id() + "/export/fiscale.csv")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(fiscale, StandardCharsets.UTF_8);
        assertTrue(csv.contains("TRIMF retenu") && csv.contains("216938"), csv);
        mvc.perform(get("/paie/mois/" + p.id() + "/export/ecritures.pdf")).andExpect(status().isOk());
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
