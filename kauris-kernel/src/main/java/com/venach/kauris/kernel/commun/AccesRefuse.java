package com.venach.kauris.kernel.commun;

/** La personne connectée n'a pas le rôle nécessaire pour cette action. */
public class AccesRefuse extends ErreurMetier {

    public AccesRefuse(String message) {
        super(message);
    }
}
