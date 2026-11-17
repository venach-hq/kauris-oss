package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Le contrat de travail : poste, salaire de base et éléments fixes (primes, indemnités, avantages versés chaque
 * mois). Un salarié n'a qu'un contrat en cours ; un changement de salaire se fait en corrigeant le contrat, les
 * bulletins déjà clos gardent les montants de leur mois.
 *
 * @param finPrevue fin prévue d'un CDD (information, pour l'alerte)
 * @param sortie    dernier jour travaillé : le contrat est terminé après cette date
 */
public record ContratTravail(UUID id, UUID tenantId, UUID salarieId, String numero, TypeContrat type, String poste,
                             String categorie, LocalDate debut, LocalDate finPrevue, long salaireBase,
                             List<Element> elements, LocalDate sortie, String motifSortie, String notes,
                             Instant creeLe) {

    public static final int ELEMENTS_MAX = 20;

    /** Ce que l'on saisit pour créer ou corriger un contrat. */
    public record Saisie(TypeContrat type, String poste, String categorie, LocalDate debut, LocalDate finPrevue,
                         long salaireBase, List<Element> elements, String notes) {
    }

    public ContratTravail {
        if (type == null) {
            throw new ErreurMetier("Choisissez le type de contrat.");
        }
        poste = Textes.obligatoire(poste, "Poste", 80);
        categorie = Textes.facultatif(categorie, "Catégorie", 60);
        motifSortie = Textes.facultatif(motifSortie, "Motif de sortie", 200);
        notes = Textes.facultatif(notes, "Notes", 500);
        if (debut == null) {
            throw new ErreurMetier("Indiquez la date d'embauche.");
        }
        if (salaireBase <= 0 || salaireBase > Element.MONTANT_MAX) {
            throw new ErreurMetier("Salaire de base : un montant positif.");
        }
        if (finPrevue != null && finPrevue.isBefore(debut)) {
            throw new ErreurMetier("La fin prévue est avant le début du contrat.");
        }
        if (type.aUneFinPrevue() && finPrevue == null) {
            throw new ErreurMetier("Un " + type.name() + " a une date de fin prévue.");
        }
        if (sortie != null && sortie.isBefore(debut)) {
            throw new ErreurMetier("La date de sortie est avant le début du contrat.");
        }
        elements = List.copyOf(elements == null ? List.of() : elements);
        if (elements.size() > ELEMENTS_MAX) {
            throw new ErreurMetier("Au plus " + ELEMENTS_MAX + " éléments fixes par contrat.");
        }
        if (elements.stream().anyMatch(e -> e.type() == TypeElement.RETENUE)) {
            throw new ErreurMetier("Une retenue se saisit dans le mois où elle s'applique, pas dans le contrat.");
        }
    }

    public static ContratTravail de(UUID id, UUID tenantId, UUID salarieId, String numero, Saisie s, Instant creeLe) {
        return new ContratTravail(id, tenantId, salarieId, numero, s.type(), s.poste(), s.categorie(), s.debut(),
                s.finPrevue(), s.salaireBase(), s.elements(), null, "", s.notes(), creeLe);
    }

    /** Le même contrat avec d'autres conditions (sa sortie, s'il y en a une, reste). */
    public ContratTravail corriger(Saisie s) {
        return new ContratTravail(id, tenantId, salarieId, numero, s.type(), s.poste(), s.categorie(), s.debut(),
                s.finPrevue(), s.salaireBase(), s.elements(), sortie, motifSortie, s.notes(), creeLe);
    }

    public ContratTravail terminer(LocalDate dernierJour, String motif) {
        if (dernierJour == null) {
            throw new ErreurMetier("Indiquez le dernier jour travaillé.");
        }
        return new ContratTravail(id, tenantId, salarieId, numero, type, poste, categorie, debut, finPrevue,
                salaireBase, elements, dernierJour, motif, notes, creeLe);
    }

    public ContratTravail rouvrir() {
        return new ContratTravail(id, tenantId, salarieId, numero, type, poste, categorie, debut, finPrevue,
                salaireBase, elements, null, "", notes, creeLe);
    }

    public Saisie saisie() {
        return new Saisie(type, poste, categorie, debut, finPrevue, salaireBase, elements, notes);
    }

    /** Vrai si le contrat n'est pas terminé à cette date. */
    public boolean enCours(LocalDate jour) {
        return sortie == null || !sortie.isBefore(jour);
    }

    /**
     * Les jours payés dans ce mois, sur 30 : 30 pour un mois complet ; sinon le nombre de jours du calendrier sous
     * contrat (entrée ou sortie en cours de mois), 30 au plus ; 0 si le contrat ne touche pas le mois.
     */
    public int joursDans(YearMonth mois) {
        LocalDate premier = mois.atDay(1);
        LocalDate dernier = mois.atEndOfMonth();
        LocalDate du = debut.isAfter(premier) ? debut : premier;
        LocalDate au = sortie != null && sortie.isBefore(dernier) ? sortie : dernier;
        if (au.isBefore(du)) {
            return 0;
        }
        if (du.equals(premier) && au.equals(dernier)) {
            return CalculPaie.JOURS_DU_MOIS;
        }
        return (int) Math.min(CalculPaie.JOURS_DU_MOIS, ChronoUnit.DAYS.between(du, au) + 1);
    }
}
