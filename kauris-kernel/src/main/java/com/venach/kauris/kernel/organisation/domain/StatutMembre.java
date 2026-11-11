package com.venach.kauris.kernel.organisation.domain;

public enum StatutMembre {
    /** Invité par son email : pas encore relié à une connexion. */
    INVITE,
    ACTIF,
    /** Ne peut plus entrer dans l'organisation ; son historique est gardé. */
    DESACTIVE
}
