package com.venach.kauris.kernel.notification.application.port.in;

import java.util.UUID;

/**
 * Prévenir des membres. À appeler dans la transaction du changement métier : la notification n'existe que
 * si le changement est enregistré, et l'inverse.
 */
public interface NotifierUseCase {

    /** @param lien chemin dans l'application où aller en cliquant (« /organisation/membres »), ou nul */
    void notifier(UUID tenantId, UUID membreId, String titre, String message, String lien);

    /** Prévient tous les propriétaires et administrateurs actifs de l'organisation. */
    void notifierAdministrateurs(UUID tenantId, String titre, String message, String lien);
}
