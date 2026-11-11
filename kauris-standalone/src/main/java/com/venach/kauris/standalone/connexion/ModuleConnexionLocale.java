package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import org.springframework.stereotype.Component;

import java.util.List;

/** Le mode de connexion « local » se déclare comme un module : il ajoute « Mon compte » au menu. */
@Component
public class ModuleConnexionLocale implements ModuleKauris {

    @Override
    public String code() {
        return "connexion-locale";
    }

    @Override
    public String nom() {
        return "Connexion locale";
    }

    @Override
    public String version() {
        String version = ModuleConnexionLocale.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Mon compte", "/compte"));
    }
}
