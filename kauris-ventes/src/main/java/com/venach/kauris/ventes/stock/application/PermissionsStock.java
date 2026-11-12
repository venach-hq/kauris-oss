package com.venach.kauris.ventes.stock.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/** Les permissions du stock (module « Achats et stock »). Les codes ne changent plus. */
public final class PermissionsStock {

    public static final String CONSULTER = "stock.consulter";
    public static final String MOUVEMENTER = "stock.mouvementer";
    public static final String DEPOTS = "stock.depots";

    private static final String GROUPE = "Stock";

    public static final List<Permission> TOUTES = List.of(
            Permission.consulter(CONSULTER, "Voir le stock, sa valeur et ses mouvements", GROUPE),
            Permission.modifier(MOUVEMENTER, "Saisir les entrées, sorties, transferts et inventaires", GROUPE),
            Permission.administrer(DEPOTS, "Créer et régler les dépôts (magasins, entrepôts)", GROUPE));

    private PermissionsStock() {
    }

    public static void exiger(Acces acces, String permission) {
        if (!acces.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération sur le stock.");
        }
    }

    public record Droits(boolean mouvementer, boolean depots) {

        public static Droits de(Acces a) {
            return new Droits(a.peut(MOUVEMENTER), a.peut(DEPOTS));
        }
    }
}
