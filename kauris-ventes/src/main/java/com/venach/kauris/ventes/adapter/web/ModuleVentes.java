package com.venach.kauris.ventes.adapter.web;

import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.ventes.application.PermissionsVentes;

import java.util.List;

/** Les ventes se déclarent comme un module : entrée de menu et permissions. */
public class ModuleVentes implements ModuleKauris {

    public static final String CODE = "ventes";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String nom() {
        return "Ventes";
    }

    @Override
    public String version() {
        String version = ModuleVentes.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public String description() {
        return "Clients, catalogue, devis, factures, avoirs, règlements, impayés et caisse (vente au comptoir).";
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsVentes.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Ventes", VentesController.PAGE, PermissionsVentes.CONSULTER),
                new EntreeMenu("Caisse", "/caisse", PermissionsVentes.CAISSE));
    }
}
