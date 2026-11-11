package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AccesService implements ResoudreAccesUseCase {

    private final MembreRepository membres;
    private final OrganisationRepository organisations;
    private final UniteDeTravail transaction;

    public AccesService(MembreRepository membres, OrganisationRepository organisations,
                        UniteDeTravail transaction) {
        this.membres = membres;
        this.organisations = organisations;
        this.transaction = transaction;
    }

    @Override
    public List<Acces> accesDe(Identite identite) {
        List<Membre> actifs = membres.parIdentite(identite).stream().filter(Membre::estActif).toList();
        if (actifs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organisation> parId = organisations
                .trouverToutes(actifs.stream().map(Membre::tenantId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Organisation::id, Function.identity()));
        return actifs.stream()
                .filter(m -> parId.containsKey(m.tenantId()))
                .map(m -> {
                    Organisation o = parId.get(m.tenantId());
                    return new Acces(o.id(), o.nom(), o.estActive(), m.id(), m.nom(), m.email(), m.roles());
                })
                .sorted(Comparator.comparing(Acces::organisation, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public int rattacherInvitations(IdentiteConnectee personne) {
        if (!personne.emailVerifie() || personne.email() == null || personne.email().isBlank()) {
            return 0;
        }
        String email;
        try {
            email = Saisie.email(personne.email());
        } catch (ErreurMetier adresseInutilisable) {
            return 0;
        }
        return transaction.executer(() -> {
            Set<UUID> dejaMembre = membres.parIdentite(personne.identite()).stream()
                    .map(Membre::tenantId)
                    .collect(Collectors.toSet());
            int rejointes = 0;
            for (Membre invitation : membres.invitationsEnAttente(email)) {
                if (dejaMembre.add(invitation.tenantId())) {
                    invitation.rattacher(personne.identite());
                    membres.sauvegarder(invitation);
                    rejointes++;
                }
            }
            return rejointes;
        });
    }
}
