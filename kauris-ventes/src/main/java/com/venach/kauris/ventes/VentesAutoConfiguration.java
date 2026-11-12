package com.venach.kauris.ventes;

import com.venach.kauris.kernel.InterfaceCommuneAutoConfiguration;
import com.venach.kauris.kernel.OrganisationAutoConfiguration;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.ventes.adapter.persistence.StockageVentes;
import com.venach.kauris.ventes.adapter.web.DocumentsVentesController;
import com.venach.kauris.ventes.adapter.web.ModuleVentes;
import com.venach.kauris.ventes.adapter.web.VentesController;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.time.Clock;

/**
 * Le module Ventes, branché automatiquement dans toute application qui dépend de kauris-ventes (mode autonome
 * ou Kauris Cloud). Ses tables viennent des migrations de db/migration/ventes ; ses entités sont trouvées grâce à
 * {@link AutoConfigurationPackage}. Les écrans ne s'activent que si l'interface commune est là.
 */
@AutoConfiguration(after = {OrganisationAutoConfiguration.class, InterfaceCommuneAutoConfiguration.class})
@AutoConfigurationPackage(basePackageClasses = VentesAutoConfiguration.class)
public class VentesAutoConfiguration {

    @Bean
    public StockageVentes stockageVentes(EntityManagerFactory fabrique) {
        return new StockageVentes(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    /** Rien à ajouter aux encaissements par défaut ; Kauris Cloud fournit le sien (sa trésorerie). */
    @Bean
    @ConditionalOnMissingBean(Ports.Encaissements.class)
    public Ports.Encaissements encaissementsVentes() {
        return Ports.Encaissements.AUCUN;
    }

    @Bean
    public VentesService ventesService(StockageVentes stockage, Ports.Encaissements encaissements,
                                       UniteDeTravail transaction, Clock horloge) {
        return new VentesService(stockage, stockage, stockage, stockage, stockage, encaissements, transaction,
                horloge);
    }

    @Bean
    public ModuleVentes moduleVentes() {
        return new ModuleVentes();
    }

    /** Les écrans, quand l'application a l'interface commune (une personne connectée, des documents). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(ContexteCourant.class)
    static class Ecrans {

        @Bean
        public VentesController ventesController(ContexteCourant contexte, VentesService ventes,
                                                 GererEntrepriseUseCase entreprises) {
            return new VentesController(contexte, ventes, entreprises);
        }

        @Bean
        public DocumentsVentesController documentsVentesController(ContexteCourant contexte, VentesService ventes,
                                                                   GererEntrepriseUseCase entreprises,
                                                                   ProducteurDocuments producteur) {
            return new DocumentsVentesController(contexte, ventes, entreprises, producteur);
        }
    }
}
