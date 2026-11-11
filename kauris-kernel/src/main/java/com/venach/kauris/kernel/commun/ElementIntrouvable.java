package com.venach.kauris.kernel.commun;

/** L'élément demandé n'existe pas dans l'organisation courante (ou appartient à une autre). */
public class ElementIntrouvable extends ErreurMetier {

    public ElementIntrouvable(String message) {
        super(message);
    }
}
