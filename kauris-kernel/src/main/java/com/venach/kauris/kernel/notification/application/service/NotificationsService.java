package com.venach.kauris.kernel.notification.application.service;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.notification.application.port.in.NotifierUseCase;
import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi;
import com.venach.kauris.kernel.notification.application.port.out.NotificationRepository;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.notification.domain.Notification;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.Membre;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NotificationsService implements NotifierUseCase, ConsulterNotificationsUseCase {

    static final int MAX_PAR_PAGE = 200;

    private final NotificationRepository notifications;
    private final BoiteDEnvoi boite;
    private final MembreRepository membres;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public NotificationsService(NotificationRepository notifications, BoiteDEnvoi boite, MembreRepository membres,
                                UniteDeTravail transaction, Clock horloge) {
        this.notifications = notifications;
        this.boite = boite;
        this.membres = membres;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public void notifier(UUID tenantId, UUID membreId, String titre, String message, String lien) {
        transaction.executer(() -> enregistrer(tenantId, membreId, titre, message, lien));
    }

    @Override
    public void notifierAdministrateurs(UUID tenantId, String titre, String message, String lien) {
        transaction.executer(() -> membres.lister(tenantId).stream()
                .filter(Membre::estActif)
                .filter(Membre::administre)
                .forEach(m -> enregistrer(tenantId, m.id(), titre, message, lien)));
    }

    /** La notification et son avis dans la boîte d'envoi : les deux, ou aucun. */
    private void enregistrer(UUID tenantId, UUID membreId, String titre, String message, String lien) {
        Instant maintenant = horloge.instant();
        Notification notification = Notification.creer(tenantId, membreId, titre, message, lien, maintenant);
        notifications.sauvegarder(notification);
        boite.deposer(Avis.de(notification), maintenant);
    }

    @Override
    public List<Notification> recentes(UUID tenantId, UUID membreId, int max) {
        return notifications.recentes(tenantId, membreId, Math.max(1, Math.min(max, MAX_PAR_PAGE)));
    }

    @Override
    public long nonLues(UUID tenantId, UUID membreId) {
        return notifications.compterNonLues(tenantId, membreId);
    }

    @Override
    public Optional<Notification> marquerLue(UUID tenantId, UUID membreId, UUID notificationId) {
        return transaction.executer(() -> {
            Optional<Notification> trouvee = notifications.trouver(tenantId, membreId, notificationId);
            trouvee.filter(n -> !n.estLue()).ifPresent(n -> {
                n.marquerLue(horloge.instant());
                notifications.sauvegarder(n);
            });
            return trouvee;
        });
    }

    @Override
    public void toutMarquerLu(UUID tenantId, UUID membreId) {
        transaction.executer(() -> notifications.marquerToutesLues(tenantId, membreId, horloge.instant()));
    }
}
