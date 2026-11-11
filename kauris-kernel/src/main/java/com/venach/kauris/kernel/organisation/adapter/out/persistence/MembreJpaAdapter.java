package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Membres et rôles. Les rôles d'un membre sont réécrits en entier à chaque enregistrement : la liste est
 * courte et cela évite toute différence entre l'objet du domaine et la base.
 */
public class MembreJpaAdapter implements MembreRepository {

    private final EntityManager em;

    public MembreJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Membre> lister(UUID tenantId) {
        return versDomaine(em.createQuery("select m from MembreEntity m where m.tenantId = :tenant",
                        MembreEntity.class)
                .setParameter("tenant", tenantId)
                .getResultList());
    }

    @Override
    public Optional<Membre> trouver(UUID tenantId, UUID id) {
        return versDomaine(em.createQuery("select m from MembreEntity m where m.tenantId = :tenant and m.id = :id",
                        MembreEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("id", id)
                .getResultList()).stream().findFirst();
    }

    @Override
    public Optional<Membre> parEmail(UUID tenantId, String email) {
        return versDomaine(em.createQuery(
                        "select m from MembreEntity m where m.tenantId = :tenant and m.email = :email",
                        MembreEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("email", email)
                .getResultList()).stream().findFirst();
    }

    @Override
    public List<Membre> parIdentite(Identite identite) {
        return versDomaine(em.createQuery(
                        "select m from MembreEntity m where m.fournisseur = :fournisseur and m.sujet = :sujet",
                        MembreEntity.class)
                .setParameter("fournisseur", identite.fournisseur())
                .setParameter("sujet", identite.sujet())
                .getResultList());
    }

    @Override
    public List<Membre> invitationsEnAttente(String email) {
        return versDomaine(em.createQuery(
                        "select m from MembreEntity m where m.email = :email and m.statut = :statut",
                        MembreEntity.class)
                .setParameter("email", email)
                .setParameter("statut", StatutMembre.INVITE.name())
                .getResultList());
    }

    @Override
    public void sauvegarder(Membre membre) {
        MembreEntity e = new MembreEntity();
        e.id = membre.id();
        e.tenantId = membre.tenantId();
        e.fournisseur = membre.identite().map(Identite::fournisseur).orElse(null);
        e.sujet = membre.identite().map(Identite::sujet).orElse(null);
        e.email = membre.email();
        e.nom = membre.nom();
        e.statut = membre.statut().name();
        e.creeLe = membre.creeLe();
        em.merge(e);
        // Le membre doit exister en base avant ses rôles (clé étrangère).
        em.flush();

        em.createQuery("delete from MembreRoleEntity r where r.membreId = :membre")
                .setParameter("membre", membre.id())
                .executeUpdate();
        for (AttributionRole role : membre.roles()) {
            MembreRoleEntity r = new MembreRoleEntity();
            r.id = UUID.randomUUID();
            r.tenantId = membre.tenantId();
            r.membreId = membre.id();
            r.codeRole = role.role().name();
            r.etablissementId = role.etablissementId();
            r.rolePersonnaliseId = role.rolePersonnaliseId();
            em.persist(r);
        }
    }

    private List<Membre> versDomaine(List<MembreEntity> entites) {
        if (entites.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<AttributionRole>> roles = em.createQuery(
                        "select r from MembreRoleEntity r where r.membreId in :membres", MembreRoleEntity.class)
                .setParameter("membres", entites.stream().map(m -> m.id).toList())
                .getResultList().stream()
                .collect(Collectors.groupingBy(r -> r.membreId, Collectors.mapping(
                        r -> new AttributionRole(Role.valueOf(r.codeRole), r.etablissementId, r.rolePersonnaliseId),
                        Collectors.toCollection(HashSet::new))));
        return entites.stream()
                .map(m -> Membre.reconstituer(new Membre.Etat(m.id, m.tenantId,
                        m.fournisseur == null ? null : new Identite(m.fournisseur, m.sujet), m.email, m.nom,
                        StatutMembre.valueOf(m.statut), roles.getOrDefault(m.id, Set.of()), m.creeLe)))
                .toList();
    }
}
