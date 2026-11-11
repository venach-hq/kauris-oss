package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembreRepository {

    List<Membre> lister(UUID tenantId);

    Optional<Membre> trouver(UUID tenantId, UUID id);

    Optional<Membre> parEmail(UUID tenantId, String email);

    /** Les membres reliés à cette personne, toutes organisations confondues (une personne, plusieurs organisations). */
    List<Membre> parIdentite(Identite identite);

    /** Les invitations pas encore acceptées pour cette adresse, toutes organisations confondues. */
    List<Membre> invitationsEnAttente(String email);

    void sauvegarder(Membre membre);
}
