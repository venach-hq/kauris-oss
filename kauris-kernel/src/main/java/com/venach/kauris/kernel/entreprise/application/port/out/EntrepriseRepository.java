package com.venach.kauris.kernel.entreprise.application.port.out;

import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.Logo;

import java.util.Optional;
import java.util.UUID;

public interface EntrepriseRepository {

    /** Les paramètres enregistrés ; {@code aUnLogo} y est à jour. Vide si rien n'a encore été saisi. */
    Optional<Entreprise> trouver(UUID tenantId);

    void sauvegarder(Entreprise entreprise);

    Optional<Logo> logo(UUID tenantId);

    void enregistrerLogo(UUID tenantId, Logo logo);

    void retirerLogo(UUID tenantId);
}
