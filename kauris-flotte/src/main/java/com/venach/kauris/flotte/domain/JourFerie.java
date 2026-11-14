package com.venach.kauris.flotte.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Un jour férié déclaré par l'organisation : aucun versement n'est dû ce jour-là, pour aucun contrat. */
public record JourFerie(UUID tenantId, LocalDate date, String libelle) {
    public JourFerie {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(date);
        libelle = Textes.propre(libelle, "Nom du jour férié", 80, true);
    }
}
