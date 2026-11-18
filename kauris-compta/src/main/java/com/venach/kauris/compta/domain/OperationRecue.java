package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.comptabilite.OperationComptable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une opération reçue d'un autre module, gardée telle quelle : elle devient une écriture, ou reste « à revoir » si
 * elle ne peut pas s'écrire (exercice clos, compte manquant) ; rien ne se perd, et une opération n'est reçue qu'une
 * fois (origine + référence).
 */
public record OperationRecue(UUID id, OperationComptable operation, Statut statut, String message, UUID ecritureId,
                             Instant recueLe, LocalDate annuleeLe, String motifAnnulation) {

    public enum Statut {
        ECRITE("Écrite"),
        A_REVOIR("À revoir"),
        IGNOREE("Ignorée"),
        ANNULEE("Annulée"),
        ANNULATION_A_REVOIR("Annulation à revoir");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    public OperationRecue {
        message = message == null ? "" : (message.length() > 300 ? message.substring(0, 300) : message);
        motifAnnulation = motifAnnulation == null ? "" : motifAnnulation;
    }

    public UUID tenantId() {
        return operation.tenantId();
    }

    public OperationRecue ecrite(UUID ecriture) {
        return new OperationRecue(id, operation, Statut.ECRITE, "", ecriture, recueLe, annuleeLe, motifAnnulation);
    }

    public OperationRecue aRevoir(String pourquoi) {
        return new OperationRecue(id, operation, Statut.A_REVOIR, pourquoi, null, recueLe, annuleeLe,
                motifAnnulation);
    }

    public OperationRecue avecStatut(Statut s, String pourquoi, UUID ecriture, LocalDate le, String motif) {
        return new OperationRecue(id, operation, s, pourquoi, ecriture, recueLe, le, motif);
    }
}
