package com.venach.kauris.kernel.accueil.application.service;

import com.venach.kauris.kernel.accueil.application.port.in.GererFondAccueilUseCase;
import com.venach.kauris.kernel.accueil.application.port.out.FondAccueilRepository;
import com.venach.kauris.kernel.accueil.domain.FondAccueil;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.time.Clock;
import java.util.Optional;

/** Le membre ne touche jamais qu'à sa propre image : l'organisation et le membre viennent de la session. */
public class FondAccueilService implements GererFondAccueilUseCase {

    private final FondAccueilRepository fonds;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public FondAccueilService(FondAccueilRepository fonds, UniteDeTravail transaction, Clock horloge) {
        this.fonds = fonds;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public Optional<FondAccueil> fond(Acces acteur) {
        return fonds.trouver(acteur.tenantId(), acteur.membreId());
    }

    @Override
    public String version(Acces acteur) {
        if (acteur == null) {
            return null;
        }
        return fonds.modifieLe(acteur.tenantId(), acteur.membreId())
                .map(quand -> Long.toString(quand.toEpochMilli(), 36))
                .orElse(null);
    }

    @Override
    public void definir(Acces acteur, byte[] contenu) {
        FondAccueil fond = FondAccueil.depuis(contenu);
        transaction.executer(() -> fonds.enregistrer(acteur.tenantId(), acteur.membreId(), fond, horloge.instant()));
    }

    @Override
    public void retirer(Acces acteur) {
        transaction.executer(() -> fonds.retirer(acteur.tenantId(), acteur.membreId()));
    }
}
