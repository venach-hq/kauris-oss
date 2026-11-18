package com.venach.kauris.compta.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne d'écriture vue avec son écriture : la matière du grand livre, de la balance, du lettrage et des états.
 *
 * @param rang place de la ligne dans l'écriture (à partir de 0)
 */
public record Mouvement(UUID ecritureId, int rang, String journal, String numero, LocalDate date, String piece,
                        String libelle, String compte, String tiers, String tiersNom, long debit, long credit,
                        String lettrage, boolean validee, String origine) {

    public long solde() {
        return debit - credit;
    }

    public boolean lettree() {
        return lettrage != null && !lettrage.isEmpty();
    }

    public boolean aNouveau() {
        return Journal.A_NOUVEAUX.equals(journal);
    }
}
