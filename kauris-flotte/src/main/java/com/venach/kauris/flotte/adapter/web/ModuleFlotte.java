package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.kernel.module.CategorieModule;
import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.flotte.application.PermissionsFlotte;

import java.util.List;

/** La flotte se déclare comme un module : entrée de menu et permissions. */
public class ModuleFlotte implements ModuleKauris {

    public static final String CODE = "flotte";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String nom() {
        return "Flotte";
    }

    @Override
    public String version() {
        String version = ModuleFlotte.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public String description() {
        return "Motos, taxis, tricycles, bus et camions : conducteurs, contrats (location, location-vente), "
                + "versements journaliers, retards, jours fériés et papiers.";
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsFlotte.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Flotte", FlotteController.PAGE, PermissionsFlotte.CONSULTER,
                CategorieModule.ACTIVITES, "#BE123C", "flotte"));
    }
}
