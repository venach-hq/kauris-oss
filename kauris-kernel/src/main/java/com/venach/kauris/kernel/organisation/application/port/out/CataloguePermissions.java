package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.module.Permission;

import java.util.List;

/** Les permissions déclarées par le noyau et les modules installés. */
public interface CataloguePermissions {

    List<Permission> toutes();

    /** Aucune permission déclarée. */
    CataloguePermissions VIDE = List::of;
}
