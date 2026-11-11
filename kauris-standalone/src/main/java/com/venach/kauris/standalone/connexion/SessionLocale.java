package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.ui.SessionKauris;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Dit à l'interface commune qui est connecté : le compte local de la session en cours. */
@Component
public class SessionLocale implements SessionKauris {

    @Override
    public Optional<IdentiteConnectee> personne() {
        return utilisateur().map(u -> new IdentiteConnectee(u.identite(), u.getUsername(), true, null));
    }

    @Override
    public boolean comptesLocaux() {
        return true;
    }

    public static Optional<UtilisateurLocal> utilisateur() {
        Authentication connecte = SecurityContextHolder.getContext().getAuthentication();
        if (connecte != null && connecte.isAuthenticated()
                && connecte.getPrincipal() instanceof UtilisateurLocal utilisateur) {
            return Optional.of(utilisateur);
        }
        return Optional.empty();
    }
}
