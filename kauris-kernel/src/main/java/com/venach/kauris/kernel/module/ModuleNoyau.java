package com.venach.kauris.kernel.module;

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
}
