package com.venach.kauris.kernel.commun.adapter;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Transaction Spring : rejoint celle en cours s'il y en a une, sinon en ouvre une. */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;

    public UniteDeTravailSpring(PlatformTransactionManager gestionnaire) {
        this.transaction = new TransactionTemplate(gestionnaire);
    }

    @Override
    public <T> T executer(Supplier<T> action) {
        return transaction.execute(etat -> action.get());
    }
}
