package com.venach.kauris.flotte;

import com.venach.kauris.kernel.InterfaceCommuneAutoConfiguration;
import com.venach.kauris.kernel.OrganisationAutoConfiguration;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.flotte.adapter.persistence.StockageFlotte;
import com.venach.kauris.flotte.adapter.web.FlotteController;
import com.venach.kauris.flotte.adapter.web.GestionFlotteController;
import com.venach.kauris.flotte.adapter.web.ModuleFlotte;
import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.application.PortsFlotte;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.time.Clock;

/**
 * Le module Flotte, branché automatiquement dans toute application qui dépend de kauris-flotte. Ses tables viennent
 * de db/migration/flotte. Par défaut les versements se font à la caisse ou par Mobile Money avec référence, et la
 * demande de paiement est un message tout prêt (WhatsApp, SMS) ; Kauris Cloud fournit les siens (PI-SPI).
 */
@AutoConfiguration(after = {OrganisationAutoConfiguration.class, InterfaceCommuneAutoConfiguration.class})
public class FlotteAutoConfiguration {

    @Bean
    public StockageFlotte stockageFlotte(EntityManagerFactory fabrique) {
        return new StockageFlotte(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean(PortsFlotte.Encaissements.class)
    public PortsFlotte.Encaissements encaissementsFlotte() {
        return PortsFlotte.Encaissements.AUCUN;
    }

    @Bean
    @ConditionalOnMissingBean(PortsFlotte.Demandes.class)
    public PortsFlotte.Demandes demandesFlotte() {
        return PortsFlotte.Demandes.AUCUN;
    }

    @Bean
    public FlotteService flotteService(StockageFlotte s, PortsFlotte.Encaissements encaissements,
                                       PortsFlotte.Demandes demandes, UniteDeTravail transaction, Clock horloge) {
        return new FlotteService(s, s, s, s, s, s, s, s, encaissements, demandes, transaction, horloge);
    }

    @Bean
    public ModuleFlotte moduleFlotte() {
        return new ModuleFlotte();
    }

    /** Les écrans, quand l'application a l'interface commune. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(ContexteCourant.class)
    static class Ecrans {

        @Bean
        public FlotteController flotteController(ContexteCourant contexte, FlotteService flotte,
                                                 GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
            return new FlotteController(contexte, flotte, entreprises, producteur);
        }

        @Bean
        public GestionFlotteController gestionFlotteController(ContexteCourant contexte, FlotteService flotte,
                                                               GererEntrepriseUseCase entreprises) {
            return new GestionFlotteController(contexte, flotte, entreprises);
        }
    }
}
