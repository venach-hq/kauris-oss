package com.venach.kauris.kernel;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.commun.adapter.UniteDeTravailSpring;
import com.venach.kauris.kernel.connexion.local.adapter.out.CompteLocalJpaAdapter;
import com.venach.kauris.kernel.connexion.local.adapter.out.EmpreinteBCrypt;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.connexion.local.application.port.out.CompteLocalRepository;
import com.venach.kauris.kernel.connexion.local.application.port.out.EmpreinteMotDePasse;
import com.venach.kauris.kernel.connexion.local.application.service.ComptesLocauxService;
import com.venach.kauris.kernel.organisation.adapter.out.persistence.EtablissementJpaAdapter;
import com.venach.kauris.kernel.organisation.adapter.out.persistence.MembreJpaAdapter;
import com.venach.kauris.kernel.organisation.adapter.out.persistence.OrganisationJpaAdapter;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.application.service.AccesService;
import com.venach.kauris.kernel.organisation.application.service.EtablissementsService;
import com.venach.kauris.kernel.organisation.application.service.MembresService;
import com.venach.kauris.kernel.organisation.application.service.OrganisationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * Organisation, établissements, membres, rôles et comptes locaux : services du noyau branchés sur la base
 * de l'application (JPA). Les tables viennent des migrations Flyway de {@code db/migration/kernel}.
 * <p>
 * {@link AutoConfigurationPackage} ajoute le paquet du noyau à ceux de l'application : ses entités sont
 * trouvées sans que l'application ait à les déclarer, et sans cacher les siennes.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@AutoConfigurationPackage(basePackageClasses = OrganisationAutoConfiguration.class)
public class OrganisationAutoConfiguration {

    /** Façade liée à la transaction en cours, utilisable depuis n'importe quel fil d'exécution. */
    private static EntityManager partage(EntityManagerFactory fabrique) {
        return SharedEntityManagerCreator.createSharedEntityManager(fabrique);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public UniteDeTravail uniteDeTravail(PlatformTransactionManager gestionnaire) {
        return new UniteDeTravailSpring(gestionnaire);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrganisationRepository organisationRepository(EntityManagerFactory fabrique) {
        return new OrganisationJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public EtablissementRepository etablissementRepository(EntityManagerFactory fabrique) {
        return new EtablissementJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public MembreRepository membreRepository(EntityManagerFactory fabrique) {
        return new MembreJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public GererOrganisationUseCase gererOrganisation(OrganisationRepository organisations,
                                                      EtablissementRepository etablissements,
                                                      MembreRepository membres, UniteDeTravail transaction,
                                                      Clock horloge) {
        return new OrganisationService(organisations, etablissements, membres, transaction, horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    public GererEtablissementsUseCase gererEtablissements(EtablissementRepository etablissements,
                                                          MembreRepository membres, UniteDeTravail transaction,
                                                          Clock horloge) {
        return new EtablissementsService(etablissements, membres, transaction, horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    public GererMembresUseCase gererMembres(MembreRepository membres, EtablissementRepository etablissements,
                                            UniteDeTravail transaction, Clock horloge) {
        return new MembresService(membres, etablissements, transaction, horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResoudreAccesUseCase resoudreAcces(MembreRepository membres, OrganisationRepository organisations,
                                              UniteDeTravail transaction) {
        return new AccesService(membres, organisations, transaction);
    }

    // ------------------------------------------------------------------ connexion « local »

    @Bean
    @ConditionalOnMissingBean
    public CompteLocalRepository compteLocalRepository(EntityManagerFactory fabrique) {
        return new CompteLocalJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public EmpreinteMotDePasse empreinteMotDePasse() {
        return new EmpreinteBCrypt();
    }

    @Bean
    @ConditionalOnMissingBean
    public GererComptesLocauxUseCase gererComptesLocaux(CompteLocalRepository comptes, EmpreinteMotDePasse empreinte,
                                                        GererOrganisationUseCase organisations,
                                                        MembreRepository membres, UniteDeTravail transaction,
                                                        Clock horloge) {
        return new ComptesLocauxService(comptes, empreinte, organisations, membres, transaction, horloge);
    }
}
