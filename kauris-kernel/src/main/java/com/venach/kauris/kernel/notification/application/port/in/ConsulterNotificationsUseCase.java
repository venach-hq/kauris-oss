package com.venach.kauris.kernel.notification.application.port.in;

import com.venach.kauris.kernel.notification.domain.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Les notifications d'un membre. Chaque appel porte le tenant et le membre : on ne lit que les siennes. */
public interface ConsulterNotificationsUseCase {

    /** Les plus récentes d'abord. */
    List<Notification> recentes(UUID tenantId, UUID membreId, int max);

    long nonLues(UUID tenantId, UUID membreId);

    /** @return la notification, maintenant lue ; vide si elle n'existe pas ou n'est pas à ce membre */
    Optional<Notification> marquerLue(UUID tenantId, UUID membreId, UUID notificationId);

    void toutMarquerLu(UUID tenantId, UUID membreId);
}
