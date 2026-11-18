package com.venach.kauris.compta.application;

import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.Exercice;
import com.venach.kauris.compta.domain.Imputation;
import com.venach.kauris.compta.domain.Journal;
import com.venach.kauris.compta.domain.Mouvement;
import com.venach.kauris.compta.domain.OperationRecue;
import com.venach.kauris.compta.domain.Parametres;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Les ports de stockage de la comptabilité. */
public final class PortsCompta {

    private PortsCompta() {
    }

    public interface Comptes {
        List<Compte> comptes(UUID tenantId);

        Optional<Compte> compte(UUID tenantId, String numero);

        void sauvegarder(Compte c);
    }

    public interface Journaux {
        List<Journal> journaux(UUID tenantId);

        void sauvegarder(Journal j);
    }

    public interface Exercices {
        List<Exercice> exercices(UUID tenantId);

        Optional<Exercice> exercice(UUID tenantId, int annee);

        void sauvegarder(Exercice e);
    }

    /** Le filtre de la liste des écritures ; un champ null n'est pas filtré. */
    public record Filtre(String journal, LocalDate du, LocalDate au, Ecriture.Statut statut, String recherche,
                         int limite) {
    }

    public interface Ecritures {
        Optional<Ecriture> ecriture(UUID tenantId, UUID id);

        /** Enregistre l'écriture et remplace ses lignes. */
        void sauvegarder(Ecriture e);

        void supprimer(UUID tenantId, UUID id);

        List<Ecriture> ecritures(UUID tenantId, Filtre filtre);

        /** Les lignes des écritures datées entre {@code du} et {@code au}, au brouillard comprises. */
        List<Mouvement> mouvements(UUID tenantId, LocalDate du, LocalDate au);

        /** Les lignes d'un compte entre ces dates. */
        List<Mouvement> mouvementsDuCompte(UUID tenantId, String compte, LocalDate du, LocalDate au);

        /** Les écritures au brouillard datées entre ces dates, par date. */
        List<Ecriture> brouillard(UUID tenantId, LocalDate du, LocalDate au);

        /** L'écriture qui contre-passe celle-ci, s'il y en a une. */
        Optional<Ecriture> inverseDe(UUID tenantId, UUID id);

        /** L'écriture d'un journal à cette date, issue de cette origine et référence. */
        Optional<Ecriture> parReference(UUID tenantId, String origine, String reference);

        /** Les codes de lettrage déjà utilisés sur un compte. */
        java.util.Set<String> lettrages(UUID tenantId, String compte);
    }

    public interface Operations {
        Optional<OperationRecue> operation(UUID tenantId, String origine, String reference);

        Optional<OperationRecue> parId(UUID tenantId, UUID id);

        void sauvegarder(OperationRecue o);

        /** À revoir, et annulations à revoir. */
        List<OperationRecue> aRevoir(UUID tenantId);

        /** Les précisions de trésorerie reçues (modes, comptes), pour compléter les imputations. */
        List<String> precisionsDeTresorerie(UUID tenantId);
    }

    public interface Imputations {
        List<Imputation> imputations(UUID tenantId);

        void remplacer(UUID tenantId, List<Imputation> imputations);
    }

    public interface ParametresCompta {
        Optional<Parametres> parametres(UUID tenantId);

        void sauvegarder(Parametres p);
    }

    /** Numéros d'écriture sans trou : le compteur reste verrouillé jusqu'à la fin de la transaction. */
    public interface Numeros {
        long prochain(UUID tenantId, String journal, int annee);
    }
}
