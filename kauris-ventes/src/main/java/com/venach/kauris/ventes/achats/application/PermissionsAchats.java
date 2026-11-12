package com.venach.kauris.ventes.achats.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/** Les permissions des achats (module « Achats et stock »). Les codes ne changent plus. */
public final class PermissionsAchats {

    public static final String CONSULTER = "achats.consulter";
    public static final String COMMANDER = "achats.commander";
    public static final String RECEVOIR = "achats.recevoir";
    public static final String PAYER = "achats.payer";

    private static final String GROUPE = "Achats";

    public static final List<Permission> TOUTES = List.of(
            Permission.consulter(CONSULTER, "Voir les fournisseurs, commandes, réceptions, factures et dettes", GROUPE),
            Permission.modifier(COMMANDER, "Gérer les fournisseurs, préparer et envoyer les commandes", GROUPE),
            Permission.modifier(RECEVOIR, "Réceptionner les livraisons (entrées en stock)", GROUPE),
            Permission.modifier(PAYER, "Enregistrer les factures fournisseurs et leurs règlements", GROUPE));

    private PermissionsAchats() {
    }

    public static void exiger(Acces a, String permission) {
        if (!a.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération dans les achats.");
        }
    }

    public record Droits(boolean commander, boolean recevoir, boolean payer) {

        public static Droits de(Acces a) {
            return new Droits(a.peut(COMMANDER), a.peut(RECEVOIR), a.peut(PAYER));
        }
    }
}
