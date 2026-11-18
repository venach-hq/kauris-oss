package com.venach.kauris.compta;

import com.venach.kauris.compta.adapter.RecepteurCompta;
import com.venach.kauris.compta.adapter.persistence.StockageCompta;
import com.venach.kauris.compta.adapter.web.ComptaController;
import com.venach.kauris.compta.adapter.web.EtatsComptaController;
import com.venach.kauris.compta.adapter.web.ModuleCompta;
import com.venach.kauris.compta.adapter.web.ParametresComptaController;
import com.venach.kauris.compta.adapter.web.SectionAccueilCompta;
import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.kernel.InterfaceCommuneAutoConfiguration;
import com.venach.kauris.kernel.OrganisationAutoConfiguration;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.time.Clock;

/**
 * Le module Comptabilité, branché automatiquement dans toute application qui dépend de kauris-compta. Ses tables
 * viennent de db/migration/compta. Il reçoit les opérations des autres modules par le noyau
 * ({@link Comptabilisation.Recepteur}).
 */
@AutoConfiguration(after = {OrganisationAutoConfiguration.class, InterfaceCommuneAutoConfiguration.class})
public class ComptaAutoConfiguration {

    @Bean
    public StockageCompta stockageCompta(EntityManagerFactory fabrique) {
        return new StockageCompta(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    @Bean
    public ComptaService comptaService(StockageCompta s, UniteDeTravail transaction, Clock horloge) {
        return new ComptaService(s, s, s, s, s, s, s, s, transaction, horloge);
    }

    @Bean
    public Comptabilisation.Recepteur recepteurCompta(ComptaService compta) {
        return new RecepteurCompta(compta);
    }

    @Bean
    public SectionAccueilCompta sectionAccueilCompta(ComptaService compta, GererEntrepriseUseCase entreprises) {
        return new SectionAccueilCompta(compta, entreprises);
    }

    @Bean
    public ModuleCompta moduleCompta() {
        return new ModuleCompta();
    }

    /** Les écrans, quand l'application a l'interface commune. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(ContexteCourant.class)
    static class Ecrans {

        @Bean
        public ComptaController comptaController(ContexteCourant contexte, ComptaService compta,
                                                 GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
            return new ComptaController(contexte, compta, entreprises, producteur);
        }

        @Bean
        public EtatsComptaController etatsComptaController(ContexteCourant contexte, ComptaService compta,
                                                           GererEntrepriseUseCase entreprises,
                                                           ProducteurDocuments producteur) {
            return new EtatsComptaController(contexte, compta, entreprises, producteur);
        }

        @Bean
        public ParametresComptaController parametresComptaController(ContexteCourant contexte, ComptaService compta,
                                                                     GererEntrepriseUseCase entreprises,
                                                                     ProducteurDocuments producteur) {
            return new ParametresComptaController(contexte, compta, entreprises, producteur);
        }
    }
}
