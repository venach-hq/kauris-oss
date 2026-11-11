package com.venach.kauris.kernel.commun;

/**
 * Une règle métier refuse l'action. Le message est écrit pour la personne qui utilise Kauris :
 * il peut être affiché tel quel.
 */
public class ErreurMetier extends RuntimeException {

    public ErreurMetier(String message) {
        super(message);
    }
}
