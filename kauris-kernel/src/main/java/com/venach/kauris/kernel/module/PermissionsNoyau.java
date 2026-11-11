package com.venach.kauris.kernel.module;

import java.util.List;

/** Les permissions que le noyau déclare. Les modules ajoutent les leurs par {@link ModuleKauris#permissions()}. */
public final class PermissionsNoyau {

    public static final String ORGANISATION_CONSULTER = "organisation.consulter";
    public static final String MEMBRES_CONSULTER = "membres.consulter";
    public static final String ENTREPRISE_CONSULTER = "entreprise.consulter";
    public static final String ENTREPRISE_MODIFIER = "entreprise.modifier";

    private static final String GROUPE = "Organisation";

    public static final List<Permission> TOUTES = List.of(
            Permission.consulter(ORGANISATION_CONSULTER, "Voir l'organisation et ses établissements", GROUPE),
            Permission.consulter(MEMBRES_CONSULTER, "Voir les membres et leurs rôles", GROUPE),
            Permission.consulter(ENTREPRISE_CONSULTER,
                    "Voir les paramètres de l'entreprise (identité, identification légale, monnaies)", GROUPE),
            Permission.administrer(ENTREPRISE_MODIFIER,
                    "Modifier les paramètres de l'entreprise, son logo et le thème de ses documents", GROUPE));

    private PermissionsNoyau() {
    }
}
