package com.venach.kauris.compta.adapter;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.comptabilite.OperationComptable;

import java.time.LocalDate;
import java.util.UUID;

/** Reçoit, par le noyau, les opérations des autres modules (dans leur transaction). */
public class RecepteurCompta implements Comptabilisation.Recepteur {

    private final ComptaService compta;

    public RecepteurCompta(ComptaService compta) {
        this.compta = compta;
    }

    @Override
    public void recevoir(OperationComptable operation) {
        compta.recevoir(operation);
    }

    @Override
    public void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
        compta.annulerOperation(tenantId, origine, reference, date, motif);
    }
}
