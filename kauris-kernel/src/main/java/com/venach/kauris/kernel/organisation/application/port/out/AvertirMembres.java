package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.organisation.domain.Membre;

/** Ce que l'organisation fait savoir à ses membres quand elle change. Appelé dans la transaction du changement. */
public interface AvertirMembres {

    /** Un invité vient de rejoindre l'organisation. */
    void membreARejoint(Membre membre);

    /** Les rôles de ce membre viennent de changer. */
    void rolesChanges(Membre membre);

    /** N'avertit personne. */
    AvertirMembres PERSONNE = new AvertirMembres() {
        @Override
        public void membreARejoint(Membre membre) {
        }

        @Override
        public void rolesChanges(Membre membre) {
        }
    };
}
