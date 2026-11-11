package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.module.EntreeMenu;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;

import java.util.List;

/**
 * Ce que l'interface commune affiche sur chaque page, sous le nom {@code kauris} dans les gabarits.
 *
 * @param acces         l'organisation dans laquelle la personne travaille ; nul si elle n'en a aucune
 * @param autresAcces   ses autres organisations, pour en changer
 * @param comptesLocaux vrai en mode de connexion « local »
 * @param notificationsNonLues dans l'organisation courante
 */
public record ContexteKauris(IdentiteConnectee personne, Acces acces, List<Acces> autresAcces,
                             List<EntreeMenu> menu, boolean comptesLocaux, long notificationsNonLues) {

    /** Le nom à afficher : celui du membre, sinon celui du compte, sinon l'email. */
    public String nomAffiche() {
        if (acces != null) {
            return acces.nomMembre();
        }
        if (personne.nom() != null && !personne.nom().isBlank()) {
            return personne.nom();
        }
        return personne.email() != null ? personne.email() : "";
    }
}
