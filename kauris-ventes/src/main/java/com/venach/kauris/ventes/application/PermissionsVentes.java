package com.venach.kauris.ventes.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/** Les permissions des ventes. Leurs codes ne changent plus : les rôles personnalisés les enregistrent. */
public final class PermissionsVentes {

    public static final String CONSULTER = "ventes.consulter";
    public static final String SAISIR = "ventes.saisir";
    public static final String EMETTRE = "ventes.emettre";
    public static final String ENCAISSER = "ventes.encaisser";
    public static final String CATALOGUE = "ventes.catalogue";
    public static final String REGLAGES = "ventes.reglages";

    private static final String GROUPE = "Ventes";

    public static final List<Permission> TOUTES = List.of(
            Permission.consulter(CONSULTER, "Voir les clients, le catalogue, les devis, les factures et les impayés",
                    GROUPE),
            Permission.modifier(SAISIR, "Ajouter des clients et préparer des devis, factures et avoirs (brouillons)",
                    GROUPE),
            Permission.modifier(EMETTRE, "Émettre (numéroter) les devis, factures et avoirs", GROUPE),
            Permission.modifier(ENCAISSER, "Enregistrer et annuler les règlements des clients", GROUPE),
            Permission.modifier(CATALOGUE, "Gérer le catalogue des articles et prestations, et leurs prix", GROUPE),
            Permission.administrer(REGLAGES, "Régler la TVA, les délais et les conditions de vente", GROUPE));

    private PermissionsVentes() {
    }

    public static void exiger(Acces acces, String permission) {
        if (!acces.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération dans les ventes.");
        }
    }

    /** Ce que la personne peut faire, pour afficher ou cacher les boutons. */
    public record Droits(boolean saisir, boolean emettre, boolean encaisser, boolean catalogue, boolean reglages) {

        public static Droits de(Acces acces) {
            return new Droits(acces.peut(SAISIR), acces.peut(EMETTRE), acces.peut(ENCAISSER), acces.peut(CATALOGUE),
                    acces.peut(REGLAGES));
        }
    }
}
