package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.commun.AccesRefuse;

/** La personne connectée n'est membre actif d'aucune organisation. */
public class AucuneOrganisation extends AccesRefuse {

    public AucuneOrganisation() {
        super("Vous n'êtes membre d'aucune organisation.");
    }
}
