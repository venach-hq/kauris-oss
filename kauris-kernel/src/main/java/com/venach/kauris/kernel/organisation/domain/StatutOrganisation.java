package com.venach.kauris.kernel.organisation.domain;

public enum StatutOrganisation {
    ACTIVE,
    /** Accès coupé (par exemple abonnement impayé dans Kauris Cloud) ; les données sont gardées. */
    SUSPENDUE
}
