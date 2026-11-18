package com.venach.kauris.paie;

import com.venach.kauris.kernel.InterfaceCommuneAutoConfiguration;
import com.venach.kauris.kernel.OrganisationAutoConfiguration;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.paie.adapter.persistence.StockagePaie;
import com.venach.kauris.paie.adapter.web.GestionPaieController;
import com.venach.kauris.paie.adapter.web.ModulePaie;
import com.venach.kauris.paie.adapter.web.PaieController;
import com.venach.kauris.paie.adapter.web.SectionAccueilPaie;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PortsPaie;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.time.Clock;
import java.util.Optional;

/**
 * Le module Paie, branché automatiquement dans toute application qui dépend de kauris-paie. Ses tables viennent de
 * db/migration/paie. Par défaut les salaires se versent par un fichier de virement remis à la banque ; Kauris Cloud
 * peut fournir son propre versement ({@link PortsPaie.Versements}).
 */
@AutoConfiguration(after = {OrganisationAutoConfiguration.class, InterfaceCommuneAutoConfiguration.class})
public class PaieAutoConfiguration {

    @Bean
    public StockagePaie stockagePaie(EntityManagerFactory fabrique) {
        return new StockagePaie(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean(PortsPaie.Versements.class)
    public PortsPaie.Versements versementsPaie() {
        return PortsPaie.Versements.MANUEL;
    }

    @Bean
    public PaieService paieService(StockagePaie s, GererEntrepriseUseCase entreprises, PortsPaie.Versements versements,
                                   UniteDeTravail transaction, Clock horloge) {
        PortsPaie.PaysEntreprise pays = tenantId -> Optional.ofNullable(entreprises.consulter(tenantId).pays());
        return new PaieService(s, s, s, s, s, s, s, s, pays, versements, transaction, horloge);
    }

    @Bean
    public SectionAccueilPaie sectionAccueilPaie(PaieService paie, GererEntrepriseUseCase entreprises) {
        return new SectionAccueilPaie(paie, entreprises);
    }

    @Bean
    public ModulePaie modulePaie() {
        return new ModulePaie();
    }

    /** Les écrans, quand l'application a l'interface commune. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(ContexteCourant.class)
    static class Ecrans {

        @Bean
        public PaieController paieController(ContexteCourant contexte, PaieService paie,
                                             GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
            return new PaieController(contexte, paie, entreprises, producteur);
        }

        @Bean
        public GestionPaieController gestionPaieController(ContexteCourant contexte, PaieService paie,
                                                           GererEntrepriseUseCase entreprises) {
            return new GestionPaieController(contexte, paie, entreprises);
        }
    }
}
