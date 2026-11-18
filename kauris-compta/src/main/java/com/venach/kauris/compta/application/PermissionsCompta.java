package com.venach.kauris.compta.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/**
 * Les permissions de la comptabilité. Elle montre toute l'activité (salaires compris) : toutes sont au niveau
 * « administrer », réservées au propriétaire et à l'administrateur tant qu'ils ne les cochent pas dans un rôle
 * (comptable). Leurs codes ne changent plus : les rôles personnalisés les enregistrent.
 */
public final class PermissionsCompta {

    public static final String CONSULTER = "compta.consulter";
    public static final String SAISIR = "compta.saisir";
    public static final String VALIDER = "compta.valider";
    public static final String PARAMETRER = "compta.parametres.modifier";

    private static final String GROUPE = "Comptabilité";

    public static final List<Permission> TOUTES = List.of(
            Permission.administrer(CONSULTER, "Voir les écritures, le grand livre, la balance, les états financiers "
                    + "et la TVA", GROUPE),
            Permission.administrer(SAISIR, "Saisir et corriger les écritures au brouillard, lettrer les comptes",
                    GROUPE),
            Permission.administrer(VALIDER, "Valider et contre-passer les écritures, liquider la TVA, clôturer un "
                    + "exercice", GROUPE),
            Permission.administrer(PARAMETRER, "Modifier le plan comptable, les journaux, les imputations et le "
                    + "système comptable", GROUPE));

    private PermissionsCompta() {
    }

    public static void exiger(Acces acces, String permission) {
        if (!acces.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération dans la comptabilité.");
        }
    }

    /** Ce que la personne peut faire, pour afficher ou cacher les boutons. */
    public record Droits(boolean saisir, boolean valider, boolean parametrer) {
        public static Droits de(Acces a) {
            return new Droits(a.peut(SAISIR), a.peut(VALIDER), a.peut(PARAMETRER));
        }
    }
}
