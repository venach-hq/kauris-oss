package com.venach.kauris.kernel.accueil.application.port.in;

import com.venach.kauris.kernel.accueil.domain.FondAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.Optional;

/**
 * L'image de fond du tableau de bord : chaque membre choisit la sienne, pour lui seul. Sans image, l'accueil
 * garde son fond par défaut (le mot « KAURIS » en filigrane).
 */
public interface GererFondAccueilUseCase {

    /** L'image du membre connecté. */
    Optional<FondAccueil> fond(Acces acteur);

    /**
     * Une marque qui change à chaque nouvelle image (à mettre dans l'adresse de l'image, pour que le navigateur la
     * garde en cache sans jamais montrer l'ancienne) ; nulle s'il n'y a pas d'image.
     */
    String version(Acces acteur);

    void definir(Acces acteur, byte[] contenu);

    void retirer(Acces acteur);
}
