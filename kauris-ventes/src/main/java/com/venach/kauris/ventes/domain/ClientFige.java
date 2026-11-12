package com.venach.kauris.ventes.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le client tel qu'il est imprimé sur un document : copié du fichier clients au moment de l'émission, il ne
 * change plus ensuite, même si la fiche du client change.
 */
public record ClientFige(UUID id, Client.Type type, String nom, String contact, String email, String telephone,
                         String adresse, String ville, String pays, String nif, String rccm) {

    /** « 12 rue du Commerce, Lomé, Togo » avec ce qui est rempli. */
    public String adresseComplete() {
        List<String> parties = new ArrayList<>();
        for (String p : List.of(adresse, ville, pays)) {
            if (p != null && !p.isBlank()) {
                parties.add(p);
            }
        }
        return String.join(", ", parties);
    }
}
