package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'organisation dans laquelle la personne connectée travaille. Les droits sont relus en base à chaque
 * requête : un membre désactivé ou un rôle retiré prend effet tout de suite. Seul le choix de l'organisation
 * est gardé dans la session.
 */
public class ContexteCourant {

    static final String ORGANISATION_CHOISIE = "kauris.organisation";
    private static final String DEJA_CALCULE = ContexteCourant.class.getName() + ".contexte";

    private final SessionKauris session;
    private final ResoudreAccesUseCase acces;
    private final RegistreModules modules;
    private final ConsulterNotificationsUseCase notifications;

    public ContexteCourant(SessionKauris session, ResoudreAccesUseCase acces, RegistreModules modules) {
        this(session, acces, modules, null);
    }

    /** @param notifications nul si l'application n'a pas les notifications : le compteur reste à zéro */
    public ContexteCourant(SessionKauris session, ResoudreAccesUseCase acces, RegistreModules modules,
                           ConsulterNotificationsUseCase notifications) {
        this.session = session;
        this.acces = acces;
        this.modules = modules;
        this.notifications = notifications;
    }

    /** Le contexte de la requête ; vide si personne n'est connecté. Calculé une seule fois par requête. */
    public Optional<ContexteKauris> contexte(HttpServletRequest requete) {
        Object connu = requete.getAttribute(DEJA_CALCULE);
        if (connu instanceof ContexteKauris contexte) {
            return Optional.of(contexte);
        }
        Optional<ContexteKauris> calcule = session.personne().map(personne -> calculer(personne, requete));
        calcule.ifPresent(contexte -> requete.setAttribute(DEJA_CALCULE, contexte));
        return calcule;
    }

    /**
     * L'accès à l'organisation courante.
     *
     * @throws AucuneOrganisation si la personne n'est membre actif d'aucune organisation
     */
    public Acces exiger(HttpServletRequest requete) {
        return contexte(requete).map(ContexteKauris::acces).orElseThrow(AucuneOrganisation::new);
    }

    /**
     * L'accès à l'organisation courante, s'il comporte cette permission.
     *
     * @throws AucuneOrganisation si la personne n'est membre actif d'aucune organisation
     * @throws AccesRefuse        si ses rôles ne lui donnent pas la permission
     */
    public Acces exiger(HttpServletRequest requete, String permission) {
        Acces courant = exiger(requete);
        if (!courant.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous donnent pas accès à cette partie de l'organisation.");
        }
        return courant;
    }

    /** Change d'organisation ; sans effet si la personne n'en est pas membre actif. */
    public void choisir(HttpServletRequest requete, UUID tenantId) {
        boolean membre = session.personne()
                .map(personne -> acces.accesDe(personne.identite()))
                .orElse(List.of()).stream()
                .anyMatch(a -> a.tenantId().equals(tenantId));
        if (membre) {
            requete.getSession(true).setAttribute(ORGANISATION_CHOISIE, tenantId);
            requete.removeAttribute(DEJA_CALCULE);
        }
    }

    private ContexteKauris calculer(IdentiteConnectee personne, HttpServletRequest requete) {
        List<Acces> tous = acces.accesDe(personne.identite());
        HttpSession http = requete.getSession(false);
        Object choisie = http == null ? null : http.getAttribute(ORGANISATION_CHOISIE);
        Acces courant = tous.stream().filter(a -> a.tenantId().equals(choisie)).findFirst()
                .orElse(tous.isEmpty() ? null : tous.get(0));
        List<Acces> autres = tous.stream().filter(a -> a != courant).toList();
        long nonLues = courant == null || notifications == null ? 0
                : notifications.nonLues(courant.tenantId(), courant.membreId());
        // Le menu ne montre que ce que les rôles de la personne lui ouvrent.
        List<EntreeMenu> menu = courant == null ? List.of() : modules.menu().stream()
                .filter(e -> e.permission() == null || courant.peut(e.permission()))
                .toList();
        return new ContexteKauris(personne, courant, autres, menu,
                session.comptesLocaux(), nonLues);
    }
}
