package com.venach.kauris.paie.application;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.paie.domain.Avance;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.Variables;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ce dont la paie a besoin : son stockage, le pays de l'entreprise, et le versement des salaires. */
public final class PortsPaie {

    private PortsPaie() {
    }

    public interface Salaries {
        void sauvegarder(Salarie s);

        Optional<Salarie> salarie(UUID tenantId, UUID id);

        List<Salarie> salaries(UUID tenantId);
    }

    public interface Contrats {
        void sauvegarder(ContratTravail c);

        Optional<ContratTravail> contrat(UUID tenantId, UUID id);

        List<ContratTravail> contrats(UUID tenantId);
    }

    public interface Periodes {
        void sauvegarder(PeriodePaie p);

        Optional<PeriodePaie> periode(UUID tenantId, UUID id);

        /** Le mois, verrouillé jusqu'à la fin de la transaction (clôture, réouverture). */
        Optional<PeriodePaie> verrouiller(UUID tenantId, UUID id);

        Optional<PeriodePaie> parMois(UUID tenantId, YearMonth mois);

        List<PeriodePaie> periodes(UUID tenantId);
    }

    public interface VariablesDuMois {
        /** Enregistre (ou efface, si elles sont vides) les éléments du mois d'un salarié. */
        void sauvegarder(Variables v);

        List<Variables> variables(UUID tenantId, UUID periodeId);
    }

    public interface Bulletins {
        void ajouter(Bulletin b);

        List<Bulletin> bulletins(UUID tenantId, UUID periodeId);

        Optional<Bulletin> bulletin(UUID tenantId, UUID id);

        /** Les bulletins figés d'un salarié, tous mois confondus. */
        List<Bulletin> bulletinsDuSalarie(UUID tenantId, UUID salarieId);

        void supprimer(UUID tenantId, UUID periodeId);
    }

    /** Les règles de paie de l'entreprise, toutes versions et tous pays. */
    public interface Regles {
        void sauvegarder(ReglesPaie r);

        Optional<ReglesPaie> regles(UUID tenantId, UUID id);

        List<ReglesPaie> toutes(UUID tenantId);

        void supprimerRegles(UUID tenantId, UUID id);
    }

    public interface Avances {
        void sauvegarder(Avance a);

        Optional<Avance> avance(UUID tenantId, UUID id);

        List<Avance> avances(UUID tenantId);
    }

    public interface Numeros {
        /** Rang suivant de ce préfixe et de cette année, sans trou ; verrouillé jusqu'à la fin de la transaction. */
        long prochain(UUID tenantId, String prefixe, int annee);
    }

    /** Le pays de l'entreprise, lu sur sa fiche (noyau). */
    @FunctionalInterface
    public interface PaysEntreprise {
        Optional<Pays> pays(UUID tenantId);
    }

    /**
     * Le versement des salaires. Sans branchement, la paie produit un ordre de virement à remettre à la banque ;
     * Kauris Cloud peut y brancher un paiement des salaires (PI-SPI). Un module open source n'appelle jamais lui-même
     * un prestataire de paiement.
     */
    public interface Versements {

        /** Vrai si les salaires peuvent partir d'ici ; sinon l'écran propose l'ordre de virement. */
        boolean disponible(UUID tenantId);

        /** Lance le versement des bulletins à payer ; rend une phrase pour l'écran. */
        String verser(Acces acces, PeriodePaie periode, List<Bulletin> bulletins);

        Versements MANUEL = new Versements() {
            @Override
            public boolean disponible(UUID tenantId) {
                return false;
            }

            @Override
            public String verser(Acces acces, PeriodePaie periode, List<Bulletin> bulletins) {
                throw new ErreurMetier("Le versement automatique n'est pas disponible ici : téléchargez l'ordre de "
                        + "virement et remettez-le à votre banque.");
            }
        };
    }
}
