package com.venach.kauris.kernel.connexion.local.adapter.out;

import com.venach.kauris.kernel.connexion.local.application.port.out.EmpreinteMotDePasse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Empreinte BCrypt (sel aléatoire inclus dans l'empreinte), la même que vérifie Spring Security à la connexion. */
public class EmpreinteBCrypt implements EmpreinteMotDePasse {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String calculer(String motDePasse) {
        return bcrypt.encode(motDePasse);
    }

    @Override
    public boolean correspond(String motDePasse, String empreinte) {
        return bcrypt.matches(motDePasse, empreinte);
    }
}
