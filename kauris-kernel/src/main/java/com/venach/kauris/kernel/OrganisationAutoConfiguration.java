package com.venach.kauris.kernel;

import java.util.List;
import com.venach.kauris.kernel.organisation.application.service.RolesService;
import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.application.port.out.CataloguePermissions;
import com.venach.kauris.kernel.organisation.application.port.in.GererRolesUseCase;
import com.venach.kauris.kernel.organisation.adapter.out.persistence.RolePersonnaliseJpaAdapter;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.entreprise.application.service.EntrepriseService;
import com.venach.kauris.kernel.entreprise.application.port.out.EntrepriseRepository;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.adapter.out.EntrepriseJpaAdapter;
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
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.LimitesOrganisation;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.application.service.AccesService;
import com.venach.kauris.kernel.organisation.application.service.EtablissementsService;
import com.venach.kauris.kernel.organisation.application.service.MembresService;
import com.venach.kauris.kernel.organisation.application.service.OrganisationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
                                                          Clock horloge, ObjectProvider<LimitesOrganisation> limites) {
        return new EtablissementsService(etablissements, membres, transaction, horloge,
                limites.getIfAvailable(() -> LimitesOrganisation.AUCUNE));
    }

    @Bean
    @ConditionalOnMissingBean
    public GererMembresUseCase gererMembres(MembreRepository membres, EtablissementRepository etablissements,
                                            UniteDeTravail transaction, Clock horloge,
                                            ObjectProvider<AvertirMembres> avertir,
                                            ObjectProvider<LimitesOrganisation> limites,
                                            RolePersonnaliseRepository rolesPersonnalises) {
        return new MembresService(membres, etablissements, transaction, horloge,
                avertir.getIfAvailable(() -> AvertirMembres.PERSONNE),
                limites.getIfAvailable(() -> LimitesOrganisation.AUCUNE), rolesPersonnalises);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResoudreAccesUseCase resoudreAcces(MembreRepository membres, OrganisationRepository organisations,
                                              UniteDeTravail transaction,
                                              ObjectProvider<AvertirMembres> avertir,
                                              CataloguePermissions catalogue,
                                              RolePersonnaliseRepository rolesPersonnalises) {
        return new AccesService(membres, organisations, transaction,
                avertir.getIfAvailable(() -> AvertirMembres.PERSONNE), catalogue, rolesPersonnalises);
    }

    // ------------------------------------------------------------------ rôles personnalisés et permissions

    /** Les permissions viennent des modules installés : le registre les rassemble. */
    @Bean
    @ConditionalOnMissingBean
    public CataloguePermissions cataloguePermissions(ObjectProvider<RegistreModules> registre) {
        return () -> {
            RegistreModules modules = registre.getIfAvailable();
            return modules == null ? List.of() : modules.permissions();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RolePersonnaliseRepository rolePersonnaliseRepository(EntityManagerFactory fabrique) {
        return new RolePersonnaliseJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public GererRolesUseCase gererRoles(RolePersonnaliseRepository roles, MembreRepository membres,
                                        CataloguePermissions catalogue, UniteDeTravail transaction, Clock horloge) {
        return new RolesService(roles, membres, catalogue, transaction, horloge);
    }

    // ------------------------------------------------------------------ paramètres de l'entreprise

    @Bean
    @ConditionalOnMissingBean
    public EntrepriseRepository entrepriseRepository(EntityManagerFactory fabrique) {
        return new EntrepriseJpaAdapter(partage(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public GererEntrepriseUseCase gererEntreprise(EntrepriseRepository entreprises,
                                                  OrganisationRepository organisations,
                                                  UniteDeTravail transaction) {
        return new EntrepriseService(entreprises, organisations, transaction);
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
