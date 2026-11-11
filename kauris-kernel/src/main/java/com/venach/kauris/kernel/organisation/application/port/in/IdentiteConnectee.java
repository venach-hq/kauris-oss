package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.Identite;

import java.util.Objects;

/**
 * La personne connectée, telle que le fournisseur d'identité la décrit.
 *
 * @param emailVerifie vrai seulement si le fournisseur garantit que l'adresse appartient à la personne ;
 *                     sans cela, aucune invitation ne lui est rattachée
 */
public record IdentiteConnectee(Identite identite, String email, boolean emailVerifie, String nom) {

    public IdentiteConnectee {
        Objects.requireNonNull(identite, "identité requise");
    }
}
