package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.paie.application.PermissionsPaie;

import java.util.List;

/** La paie se déclare comme un module : entrée de menu et permissions. */
public class ModulePaie implements ModuleKauris {

    public static final String CODE = "rh-paie";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String nom() {
        return "Paie";
    }

    @Override
    public String version() {
        String version = ModulePaie.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public String description() {
        return "Paie du Togo et de la Côte d'Ivoire : salariés, contrats, bulletins, CNSS, AMU, IRPP, CNPS, CMU, "
                + "ITS, livre de paie, déclarations et virement des salaires.";
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsPaie.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Paie", PaieController.PAGE, PermissionsPaie.CONSULTER));
    }
}
