package com.venach.kauris.kernel.notification.application.port.out;

import com.venach.kauris.kernel.notification.domain.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    void sauvegarder(Notification notification);

    List<Notification> recentes(UUID tenantId, UUID membreId, int max);

    long compterNonLues(UUID tenantId, UUID membreId);

    Optional<Notification> trouver(UUID tenantId, UUID membreId, UUID id);

    void marquerToutesLues(UUID tenantId, UUID membreId, Instant maintenant);
}
