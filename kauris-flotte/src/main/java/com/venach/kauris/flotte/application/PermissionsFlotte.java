package com.venach.kauris.flotte.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/** Les permissions de la flotte. Leurs codes ne changent plus : les rôles personnalisés les enregistrent. */
public final class PermissionsFlotte {

    public static final String CONSULTER = "flotte.consulter";
    public static final String ENCAISSER = "flotte.encaisser";
    public static final String GERER = "flotte.gerer";

    private static final String GROUPE = "Flotte";

    public static final List<Permission> TOUTES = List.of(
            Permission.consulter(CONSULTER, "Voir les véhicules, les conducteurs, les contrats et les versements",
                    GROUPE),
            Permission.modifier(ENCAISSER, "Encaisser les versements, déclarer les jours fériés, demander un "
                    + "paiement à un conducteur en retard", GROUPE),
            Permission.modifier(GERER, "Gérer les véhicules, conducteurs, contrats et documents ; avances, amendes, "
                    + "pauses ; annuler un versement", GROUPE));

    private PermissionsFlotte() {
    }

    public static void exiger(Acces acces, String permission) {
        if (!acces.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération dans la flotte.");
        }
    }

    /** Ce que la personne peut faire, pour afficher ou cacher les boutons. */
    public record Droits(boolean encaisser, boolean gerer) {
        public static Droits de(Acces a) {
            return new Droits(a.peut(ENCAISSER), a.peut(GERER));
        }
    }
}
