package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository {

    Optional<Organisation> trouver(UUID id);

    List<Organisation> trouverToutes(Collection<UUID> ids);

    boolean auMoinsUne();

    void sauvegarder(Organisation organisation);
}
