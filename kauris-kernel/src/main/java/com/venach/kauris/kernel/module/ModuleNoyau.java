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
                new EntreeMenu("Organisation", "/organisation", PermissionsNoyau.ORGANISATION_CONSULTER),
                new EntreeMenu("Membres", "/organisation/membres", PermissionsNoyau.MEMBRES_CONSULTER),
                new EntreeMenu("Rôles", "/organisation/roles", PermissionsNoyau.MEMBRES_CONSULTER),
                new EntreeMenu("Entreprise", "/entreprise", PermissionsNoyau.ENTREPRISE_CONSULTER),
                new EntreeMenu("Mon profil", "/profil"));
    }
}
