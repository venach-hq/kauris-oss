package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.Identite;

import java.util.List;

/** Fait le lien entre une personne connectée et les organisations où elle est membre. */
public interface ResoudreAccesUseCase {

    /** Organisations où la personne est membre actif, par nom. */
    List<Acces> accesDe(Identite identite);

    /**
     * Rattache à la personne les invitations en attente à son adresse email, si cette adresse est vérifiée.
     *
     * @return le nombre d'organisations rejointes
     */
    int rattacherInvitations(IdentiteConnectee personne);
}
