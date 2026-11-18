package com.venach.kauris.kernel.module;

import java.util.List;

/** Le noyau se déclare lui-même : le registre n'est jamais vide et donne la version du socle. */
public final class ModuleNoyau implements ModuleKauris {

    private final String version;

    public ModuleNoyau(String version) {
        this.version = version;
    }

    @Override
    public String code() {
        return "kernel";
    }

    @Override
    public String nom() {
        return "Noyau Kauris";
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public List<Permission> permissions() {
        return PermissionsNoyau.TOUTES;
    }

    @Override
    public List<EntreeMenu> menu() {
        return List.of(new EntreeMenu("Accueil", "/"),
                new EntreeMenu("Organisation", "/organisation", PermissionsNoyau.ORGANISATION_CONSULTER,
                        CategorieModule.ORGANISATION, "#334155", "organisation"),
                new EntreeMenu("Membres", "/organisation/membres", PermissionsNoyau.MEMBRES_CONSULTER,
                        CategorieModule.ORGANISATION, "#4338CA", "membres"),
                new EntreeMenu("Rôles", "/organisation/roles", PermissionsNoyau.MEMBRES_CONSULTER,
                        CategorieModule.ORGANISATION, "#6D28D9", "roles"),
                new EntreeMenu("Entreprise", "/entreprise", PermissionsNoyau.ENTREPRISE_CONSULTER,
                        CategorieModule.ORGANISATION, "#0F766E", "entreprise"),
                new EntreeMenu("Mon profil", "/profil", null, CategorieModule.ORGANISATION, "#475569",
                        "profil"));
    }
}
