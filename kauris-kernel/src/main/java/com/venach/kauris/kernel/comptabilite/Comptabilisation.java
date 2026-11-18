package com.venach.kauris.kernel.comptabilite;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Le point de sortie vers la comptabilité, que tout module peut appeler sans dépendre du module de comptabilité.
 * L'appel se fait dans la transaction de l'opération : la comptabilité l'enregistre en même temps, ou pas du tout.
 * Sans module de comptabilité installé, rien ne se passe.
 */
public interface Comptabilisation {

    /** Transmet une opération ; une opération déjà reçue (même origine et référence) est ignorée. */
    void enregistrer(OperationComptable operation);

    /**
     * Annule une opération déjà transmise (règlement annulé, facture fournisseur annulée) : la comptabilité passe
     * l'écriture inverse à cette date, ou supprime l'écriture si elle n'était pas encore validée.
     */
    void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif);

    /** Aucune comptabilité. */
    Comptabilisation AUCUNE = new Comptabilisation() {
        @Override
        public void enregistrer(OperationComptable operation) {
        }

        @Override
        public void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
        }
    };

    /**
     * Ce que fournit le module de comptabilité. Le noyau expose une seule {@link Comptabilisation}, qui transmet à
     * ce récepteur s'il existe.
     */
    interface Recepteur {

        void recevoir(OperationComptable operation);

        void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif);
    }
}
