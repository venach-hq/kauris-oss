package com.venach.kauris.ventes.stock.adapter.web;

import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.ModuleKauris;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.ventes.stock.application.PermissionsStock;

import java.util.List;

/**
 * Le module « Achats et stock ». Il vit dans le même jar que les Ventes, dont il partage le catalogue d'articles,
 * mais se déclare à part : il a son menu, ses permissions, et une formule peut l'ouvrir ou non.
 */
public class ModuleAchatsStock implements ModuleKauris {

    public static final String CODE = "achats-stock";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String nom() {
        return "Achats et stock";
    }

    @Override
    public String version() {
        String version = ModuleAchatsStock.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    @Override
    public String description() {
        return "Stock des articles par dépôt, valeur au coût moyen pondéré, inventaires ; fournisseurs et achats.";
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsStock.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Stock", StockController.PAGE, PermissionsStock.CONSULTER));
    }
}
