package com.venach.kauris.kernel;

import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.ModuleNoyau;
import com.venach.kauris.kernel.module.RegistreModules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Branché automatiquement dans toute application qui dépend de kauris-kernel. */
@AutoConfiguration
public class KernelAutoConfiguration {

    @Bean
    public ModuleNoyau moduleNoyau() {
        String version = KernelAutoConfiguration.class.getPackage().getImplementationVersion();
        return new ModuleNoyau(version != null ? version : "dev");
    }

    @Bean
    public RegistreModules registreModules(ObjectProvider<ModuleKauris> modules) {
        return new RegistreModules(modules.orderedStream().toList());
    }

    /**
     * Le point de sortie vers la comptabilité : transmet au module de comptabilité s'il est installé, sinon ne fait
     * rien. Le récepteur est cherché à chaque appel, pour ne pas dépendre de l'ordre de création des beans.
     */
    @Bean
    public Comptabilisation comptabilisation(ObjectProvider<Comptabilisation.Recepteur> recepteur) {
        return new Comptabilisation() {
            @Override
            public void enregistrer(OperationComptable operation) {
                recepteur.ifAvailable(r -> r.recevoir(operation));
            }

            @Override
            public void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
                recepteur.ifAvailable(r -> r.annuler(tenantId, origine, reference, date, motif));
            }
        };
    }

    /** Modules installés visibles dans /actuator/info (utile pour vérifier un déploiement). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")
    static class InfoModules {

        @Bean
        public InfoContributor modulesKauris(RegistreModules registre) {
            return builder -> {
                Map<String, String> modules = new LinkedHashMap<>();
                registre.tous().forEach(m -> modules.put(m.code(), m.version()));
                builder.withDetail("modules", modules);
            };
        }
    }
}
