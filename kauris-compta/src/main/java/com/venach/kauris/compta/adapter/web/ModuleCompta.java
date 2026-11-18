package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.compta.application.PermissionsCompta;
import com.venach.kauris.kernel.module.CategorieModule;
import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.Permission;

import java.util.List;

/** La comptabilité se déclare comme un module : entrée de menu et permissions. */
public class ModuleCompta implements ModuleKauris {

    public static final String CODE = "compta-syscohada";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String nom() {
        return "Comptabilité";
    }

    @Override
    public String version() {
        String version = ModuleCompta.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public String description() {
        return "Comptabilité SYSCOHADA révisé, système normal ou minimal de trésorerie : écritures automatiques des "
                + "ventes, achats, caisse, paie et flotte, saisie, lettrage, grand livre, balance, bilan, compte de "
                + "résultat, TVA et clôture d'exercice.";
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsCompta.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Comptabilité", ComptaController.PAGE, PermissionsCompta.CONSULTER,
                CategorieModule.FINANCES, "#047857", "compta"));
    }
}
