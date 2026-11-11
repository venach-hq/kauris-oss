package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les rôles que le propriétaire crée pour les postes de son entreprise, et les permissions qu'il y met.
 * Seul un propriétaire les crée, les modifie ou les supprime ({@code acteurId}).
 */
public interface GererRolesUseCase {

    /** Les rôles personnalisés de l'organisation, par nom. */
    List<RolePersonnalise> lister(UUID tenantId);

    /** Toutes les permissions qu'on peut cocher. */
    List<Permission> permissions();

    RolePersonnalise creer(UUID tenantId, UUID acteurId, String nom, String description, Set<String> permissions);

    void modifier(UUID tenantId, UUID acteurId, UUID roleId, String nom, String description,
                  Set<String> permissions);

    /** Refusé tant qu'un membre porte ce rôle. */
    void supprimer(UUID tenantId, UUID acteurId, UUID roleId);
}
