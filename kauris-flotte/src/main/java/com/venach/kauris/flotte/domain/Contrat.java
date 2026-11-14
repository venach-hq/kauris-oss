package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Le contrat qui confie un véhicule à un conducteur. Ce que le conducteur doit se déduit du contrat, jour par jour
 * (voir {@link Compte}) : rien n'est pré-rempli.
 *
 * @param tarifs     montant par jour travaillé, à partir d'une date : changer le montant n'efface pas le passé
 * @param joursRepos jours de la semaine sans versement (souvent le dimanche)
 * @param pauses     périodes sans versement (véhicule au garage, suspension) ; celles « à la charge du conducteur »
 *                   restent dues
 * @param prixTotal  location-vente seulement : total à verser pour devenir propriétaire
 * @param fin        dernier jour du contrat (terminé ou résilié) ; null s'il court encore
 */
public record Contrat(UUID id, UUID tenantId, String numero, UUID vehiculeId, UUID conducteurId, TypeContrat type,
                      LocalDate debut, LocalDate fin, List<Tarif> tarifs, Set<DayOfWeek> joursRepos,
                      List<Pause> pauses, Long prixTotal, StatutContrat statut, String motifFin, String notes,
                      Instant creeLe) {

    /** Montant d'un jour travaillé, à partir de {@code depuis}. */
    public record Tarif(LocalDate depuis, long montant) {
        public Tarif {
            Objects.requireNonNull(depuis);
            Textes.montant(montant, "Versement journalier", false);
        }
    }

    /**
     * Une période sans versement. {@code au} null : en cours. {@code aChargeDuConducteur} : la période reste due
     * (panne par sa faute, par exemple) ; elle est notée pour mémoire.
     */
    public record Pause(UUID id, LocalDate du, LocalDate au, String motif, boolean aChargeDuConducteur) {
        public Pause {
            Objects.requireNonNull(id);
            Objects.requireNonNull(du);
            motif = Textes.propre(motif, "Motif", 200, true);
            if (au != null && au.isBefore(du)) {
                throw new ErreurMetier("La fin de la période est avant son début.");
            }
        }

        public boolean couvre(LocalDate jour) {
            return !jour.isBefore(du) && (au == null || !jour.isAfter(au));
        }
    }

    public Contrat {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(numero);
        if (vehiculeId == null || conducteurId == null) {
            throw new ErreurMetier("Choisissez le véhicule et le conducteur.");
        }
        if (type == null) {
            throw new ErreurMetier("Choisissez le type de contrat.");
        }
        if (debut == null) {
            throw new ErreurMetier("Date de début obligatoire.");
        }
        tarifs = tarifs == null ? List.of() : tarifs.stream().sorted(Comparator.comparing(Tarif::depuis)).toList();
        joursRepos = joursRepos == null || joursRepos.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(joursRepos));
        pauses = pauses == null ? List.of() : pauses.stream().sorted(Comparator.comparing(Pause::du)).toList();
        statut = statut == null ? StatutContrat.EN_COURS : statut;
        motifFin = Textes.propre(motifFin, "Motif", 300, false);
        notes = Textes.propre(notes, "Notes", 500, false);
        if (type.versements()) {
            if (tarifs.isEmpty()) {
                throw new ErreurMetier("Indiquez le versement journalier.");
            }
            if (tarifs.get(0).depuis().isAfter(debut)) {
                throw new ErreurMetier("Le premier versement journalier doit valoir dès le début du contrat.");
            }
            if (joursRepos.size() == 7) {
                throw new ErreurMetier("Il faut au moins un jour travaillé dans la semaine.");
            }
        } else {
            tarifs = List.of();
            joursRepos = Set.of();
        }
        if (type == TypeContrat.LOCATION_VENTE) {
            if (prixTotal == null || prixTotal <= 0) {
                throw new ErreurMetier("Location-vente : indiquez le total à verser pour devenir propriétaire.");
            }
            Textes.montant(prixTotal, "Total à verser", false);
        } else {
            prixTotal = null;
        }
        if (fin != null && fin.isBefore(debut)) {
            throw new ErreurMetier("La fin du contrat est avant son début.");
        }
        if (statut == StatutContrat.EN_COURS && fin != null) {
            throw new ErreurMetier("Un contrat en cours n'a pas de date de fin.");
        }
        for (int i = 1; i < tarifs.size(); i++) {
            if (!tarifs.get(i).depuis().isAfter(tarifs.get(i - 1).depuis())) {
                throw new ErreurMetier("Deux montants journaliers commencent le même jour.");
            }
        }
    }

    public boolean enCours() {
        return statut == StatutContrat.EN_COURS;
    }

    /** Le montant d'un jour travaillé à cette date (le dernier tarif commencé), 0 sans versement. */
    public long tarif(LocalDate jour) {
        long m = 0;
        for (Tarif t : tarifs) {
            if (!t.depuis().isAfter(jour)) {
                m = t.montant();
            }
        }
        return m;
    }

    public long tarifActuel(LocalDate jour) {
        return tarif(jour.isBefore(debut) ? debut : jour);
    }

    /** La pause en cours ce jour, s'il y en a une. */
    public java.util.Optional<Pause> pause(LocalDate jour) {
        return pauses.stream().filter(p -> p.couvre(jour)).findFirst();
    }

    // ------------------------------------------------------------------ changements (nouveau contrat rendu)

    /** Nouveau montant journalier à partir d'une date ; les jours d'avant gardent l'ancien. */
    public Contrat changerTarif(LocalDate depuis, long montant) {
        exigerEnCours();
        if (!type.versements()) {
            throw new ErreurMetier("Ce contrat n'a pas de versement.");
        }
        if (depuis.isBefore(debut)) {
            throw new ErreurMetier("Le nouveau montant ne peut pas commencer avant le contrat.");
        }
        List<Tarif> t = new ArrayList<>(tarifs.stream().filter(x -> !x.depuis().equals(depuis)).toList());
        t.add(new Tarif(depuis, montant));
        return avec(t, joursRepos, pauses);
    }

    public Contrat changerJoursRepos(Set<DayOfWeek> jours) {
        exigerEnCours();
        return avec(tarifs, jours, pauses);
    }

    public Contrat ajouterPause(Pause p) {
        exigerEnCours();
        if (p.du().isBefore(debut)) {
            throw new ErreurMetier("La période commence avant le contrat.");
        }
        for (Pause x : pauses) {
            boolean avant = p.au() != null && p.au().isBefore(x.du());
            boolean apres = x.au() != null && x.au().isBefore(p.du());
            boolean chevauche = !avant && !apres;
            if (chevauche) {
                throw new ErreurMetier("Cette période chevauche celle du " + x.du() + " (" + x.motif() + ").");
            }
        }
        List<Pause> l = new ArrayList<>(pauses);
        l.add(p);
        return avec(tarifs, joursRepos, l);
    }

    /** Termine une pause en cours au jour donné (reprise le lendemain). */
    public Contrat finirPause(UUID pauseId, LocalDate dernierJour) {
        List<Pause> l = new ArrayList<>();
        boolean trouvee = false;
        for (Pause p : pauses) {
            if (p.id().equals(pauseId)) {
                trouvee = true;
                l.add(new Pause(p.id(), p.du(), dernierJour, p.motif(), p.aChargeDuConducteur()));
            } else {
                l.add(p);
            }
        }
        if (!trouvee) {
            throw new ErreurMetier("Période introuvable.");
        }
        return avec(tarifs, joursRepos, l);
    }

    public Contrat retirerPause(UUID pauseId) {
        List<Pause> l = pauses.stream().filter(p -> !p.id().equals(pauseId)).toList();
        if (l.size() == pauses.size()) {
            throw new ErreurMetier("Période introuvable.");
        }
        return avec(tarifs, joursRepos, l);
    }

    public Contrat modifierNotes(String n) {
        return new Contrat(id, tenantId, numero, vehiculeId, conducteurId, type, debut, fin, tarifs, joursRepos,
                pauses, prixTotal, statut, motifFin, n, creeLe);
    }

    /**
     * Fin du contrat ; dernier jour dû inclus. {@code resiliation} : rupture avant terme ; sinon fin normale
     * (location-vente soldée, fin d'une location).
     */
    public Contrat terminer(LocalDate dernierJour, boolean resiliation, String motif) {
        exigerEnCours();
        if (dernierJour == null) {
            throw new ErreurMetier("Indiquez le dernier jour du contrat.");
        }
        if (resiliation && (motif == null || motif.isBlank())) {
            throw new ErreurMetier("Indiquez le motif de la résiliation.");
        }
        return new Contrat(id, tenantId, numero, vehiculeId, conducteurId, type, debut, dernierJour, tarifs,
                joursRepos, pauses, prixTotal, resiliation ? StatutContrat.RESILIE : StatutContrat.TERMINE, motif,
                notes, creeLe);
    }

    /** Annule une fin saisie par erreur : le contrat repart. */
    public Contrat rouvrir() {
        if (enCours()) {
            throw new ErreurMetier("Ce contrat est déjà en cours.");
        }
        return new Contrat(id, tenantId, numero, vehiculeId, conducteurId, type, debut, null, tarifs, joursRepos,
                pauses, prixTotal, StatutContrat.EN_COURS, "", notes, creeLe);
    }

    /** Corrige le début, le prix total ou le premier montant (erreur de saisie). */
    public Contrat corriger(LocalDate nouveauDebut, Long nouveauPrixTotal) {
        if (nouveauDebut == null) {
            throw new ErreurMetier("Indiquez le premier jour du contrat.");
        }
        List<Tarif> t = new ArrayList<>(tarifs);
        if (!t.isEmpty() && (t.get(0).depuis().equals(debut) || t.get(0).depuis().isAfter(nouveauDebut))) {
            t.set(0, new Tarif(nouveauDebut, t.get(0).montant()));
        }
        return new Contrat(id, tenantId, numero, vehiculeId, conducteurId, type, nouveauDebut, fin, t, joursRepos,
                pauses, nouveauPrixTotal, statut, motifFin, notes, creeLe);
    }

    private Contrat avec(List<Tarif> t, Set<DayOfWeek> repos, List<Pause> p) {
        return new Contrat(id, tenantId, numero, vehiculeId, conducteurId, type, debut, fin, t, repos, p, prixTotal,
                statut, motifFin, notes, creeLe);
    }

    private void exigerEnCours() {
        if (!enCours()) {
            throw new ErreurMetier("Ce contrat est " + statut.libelle().toLowerCase() + " : rouvrez-le d'abord.");
        }
    }
}
