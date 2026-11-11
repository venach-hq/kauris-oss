package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mode de connexion « local » : email + mot de passe, vérifiés sur les comptes du noyau.
 * Tout demande d'être connecté, sauf la page de connexion, l'installation du premier démarrage,
 * les fichiers statiques et l'état de santé.
 */
@Configuration(proxyBeanMethods = false)
public class SecuriteLocaleConfig {

    static final String PAGE_CONNEXION = "/connexion";
    static final String PAGE_INSTALLATION = "/installation";

    @Bean
    public SecurityFilterChain securite(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers(PAGE_CONNEXION, PAGE_INSTALLATION, "/error", "/favicon.ico", "/css/**",
                                "/js/**", "/img/**", "/kauris/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(connexion -> connexion
                        .loginPage(PAGE_CONNEXION)
                        .usernameParameter("email")
                        .passwordParameter("motDePasse")
                        .defaultSuccessUrl("/")
                        .failureUrl(PAGE_CONNEXION + "?erreur"))
                .logout(deconnexion -> deconnexion
                        .logoutSuccessUrl(PAGE_CONNEXION + "?deconnexion"));
        return http.build();
    }

    /** Le même algorithme que celui qui calcule les empreintes dans le noyau (BCrypt). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService comptesLocaux(GererComptesLocauxUseCase comptes) {
        return email -> comptes.pourConnexion(email)
                .map(UtilisateurLocal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));
    }
}
