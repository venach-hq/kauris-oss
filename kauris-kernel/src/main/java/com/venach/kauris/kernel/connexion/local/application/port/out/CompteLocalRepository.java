package com.venach.kauris.kernel.connexion.local.application.port.out;

import com.venach.kauris.kernel.connexion.local.domain.CompteLocal;

import java.util.Optional;
import java.util.UUID;

public interface CompteLocalRepository {

    /** Recherche à la connexion : l'email est unique dans toute l'installation. */
    Optional<CompteLocal> parEmail(String email);

    Optional<CompteLocal> trouver(UUID id);

    void sauvegarder(CompteLocal compte);
}
