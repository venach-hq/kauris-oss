package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.in.NotifierUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.domain.Membre;

/** Les changements de l'organisation deviennent des notifications. */
public class AvertirMembresParNotification implements AvertirMembres {

    private static final String PAGE_MEMBRES = "/organisation/membres";

    private final NotifierUseCase notifier;

    public AvertirMembresParNotification(NotifierUseCase notifier) {
        this.notifier = notifier;
    }

    @Override
    public void membreARejoint(Membre membre) {
        notifier.notifierAdministrateurs(membre.tenantId(), "Nouveau membre",
                membre.nom() + " a rejoint l'organisation.", PAGE_MEMBRES);
    }

    @Override
    public void rolesChanges(Membre membre) {
        if (membre.estActif()) {
            notifier.notifier(membre.tenantId(), membre.id(), "Vos rôles ont changé",
                    "Un administrateur a modifié vos rôles dans l'organisation.", PAGE_MEMBRES);
        }
    }
}
