package com.venach.kauris.standalone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lance les modules open source présents dans le classpath, sans Kauris Cloud.
 * Profil par défaut : dev. En production : SPRING_PROFILES_ACTIVE=prod.
 */
@SpringBootApplication
public class KaurisStandaloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaurisStandaloneApplication.class, args);
    }
}
