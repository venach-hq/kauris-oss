package com.venach.kauris.kernel.ui.notification;

import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.notification.domain.Notification;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** Les notifications de la personne connectée, dans l'organisation courante. */
@Controller
@RequestMapping("/notifications")
public class NotificationsController {

    private static final int AFFICHEES = 50;
    /** Heure UTC : c'est aussi l'heure locale à Lomé et à Dakar. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneOffset.UTC);

    /** Une notification prête à afficher. */
    public record Ligne(UUID id, String titre, String message, String date, boolean lue, boolean aUnLien) {

        static Ligne de(Notification n) {
            return new Ligne(n.id(), n.titre(), n.message(), DATE.format(n.creeLe()), n.estLue(),
                    n.lien().isPresent());
        }
    }

    private final ContexteCourant contexte;
    private final ConsulterNotificationsUseCase notifications;

    public NotificationsController(ContexteCourant contexte, ConsulterNotificationsUseCase notifications) {
        this.contexte = contexte;
        this.notifications = notifications;
    }

    @GetMapping
    public String liste(HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete);
        model.addAttribute("notifications", notifications.recentes(acces.tenantId(), acces.membreId(), AFFICHEES)
                .stream().map(Ligne::de).toList());
        return "kauris/notifications";
    }

    /** Pour le compteur de l'en-tête, relu par le navigateur quand un avis arrive. */
    @GetMapping("/non-lues")
    @ResponseBody
    public Map<String, Long> nonLues(HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete);
        return Map.of("nonLues", notifications.nonLues(acces.tenantId(), acces.membreId()));
    }

    /** Marque la notification comme lue, puis va là où elle mène. */
    @PostMapping("/{id}/lue")
    public String lue(@PathVariable UUID id, HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete);
        return notifications.marquerLue(acces.tenantId(), acces.membreId(), id)
                .flatMap(Notification::lien)
                .map(lien -> "redirect:" + lien)
                .orElse("redirect:/notifications");
    }

    @PostMapping("/lues")
    public String toutesLues(HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete);
        notifications.toutMarquerLu(acces.tenantId(), acces.membreId());
        return "redirect:/notifications";
    }
}
