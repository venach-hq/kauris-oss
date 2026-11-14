package com.venach.kauris.flotte.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Le compte du conducteur, calculé à partir du contrat : rien n'est saisi jour par jour.
 * <ul>
 *   <li>Chaque jour travaillé est dû au tarif du jour, sauf jour de repos, jour férié déclaré ou pause qui n'est pas
 *       à sa charge. En location-vente, plus rien n'est dû une fois le total atteint.</li>
 *   <li>Avances, amendes et dommages à sa charge s'ajoutent ; une remise se retranche.</li>
 *   <li>Chaque paiement, de n'importe quel montant, rembourse le plus ancien dû d'abord. Un paiement d'avance couvre
 *       les jours suivants.</li>
 * </ul>
 * Le jour même n'est pas « en retard » : le conducteur verse souvent le soir.
 */
public final class Compte {

    private static final int HORIZON_JOURS = 3700;

    public enum Etat {
        A_JOUR("À jour"), EN_AVANCE("En avance"), EN_RETARD("En retard"), EN_PAUSE("En pause"),
        SOLDE("Soldé : véhicule payé"), TERMINE("Contrat terminé"), SANS_VERSEMENT("Sans versement");

        private final String libelle;

        Etat(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Une ligne du relevé, comme un relevé de compte : dû (débit), payé (crédit), solde après la ligne. */
    public record Ligne(LocalDate date, String libelle, long du, long paye, long solde) {
    }

    /** Un jour du calendrier du conducteur. */
    public record JourCalendrier(LocalDate date, String statut, String detail, long du, long paye) {
        /** PAYE, PARTIEL, IMPAYE, AUJOURDHUI, A_VENIR, REPOS, FERIE, PAUSE, HORS_CONTRAT */
    }

    /**
     * La situation du compte à une date.
     *
     * @param solde           payé moins dû (négatif : le conducteur doit)
     * @param payeJusquAu     dernier jour dû entièrement couvert ; null si aucun
     * @param joursEnRetard   jours dus avant aujourd'hui pas (entièrement) payés
     * @param montantEnRetard ce qui reste dû sur les jours et sommes d'avant aujourd'hui
     * @param resteAujourdhui ce qui reste à verser pour aujourd'hui
     */
    public record Situation(Etat etat, long totalDu, long totalPaye, long solde, LocalDate payeJusquAu,
                            int joursEnRetard, long montantEnRetard, long attenduAujourdhui, long resteAujourdhui,
                            long tarifDuJour, Long prixTotal, long verseSurPrix, long resteSurPrix,
                            int versementsRestants, LocalDate finEstimee, Optional<Contrat.Pause> pause,
                            List<Ligne> releve, List<JourCalendrier> calendrier) {

        /** Ce qu'il faut demander au conducteur aujourd'hui : son retard et le versement du jour. */
        public long aEncaisser() {
            return Math.max(0, montantEnRetard + resteAujourdhui);
        }

        public boolean enRetard() {
            return etat == Etat.EN_RETARD;
        }
    }

    private record Du(LocalDate date, String libelle, long montant, boolean versement) {
    }

    private Compte() {
    }

    /** Pourquoi ce jour n'est pas dû (repos, férié, pause), ou vide s'il est dû. */
    public static Optional<String> pasDu(Contrat c, Map<LocalDate, String> feries, LocalDate jour) {
        if (jour.isBefore(c.debut()) || (c.fin() != null && jour.isAfter(c.fin()))) {
            return Optional.of("HORS_CONTRAT");
        }
        if (c.joursRepos().contains(jour.getDayOfWeek())) {
            return Optional.of("REPOS");
        }
        if (feries.containsKey(jour)) {
            return Optional.of("FERIE");
        }
        Optional<Contrat.Pause> p = c.pause(jour);
        if (p.isPresent() && !p.get().aChargeDuConducteur()) {
            return Optional.of("PAUSE");
        }
        return Optional.empty();
    }

    public static Situation calculer(Contrat c, Map<LocalDate, String> feries, List<Versement> versements,
                                     List<Ajustement> ajustements, LocalDate aujourdhui) {
        Optional<Contrat.Pause> pauseDuJour = c.pause(aujourdhui).filter(p -> !p.aChargeDuConducteur());
        // ---- les jours dus jusqu'à aujourd'hui (ou la fin du contrat), plafonnés au prix en location-vente
        List<Du> dus = new ArrayList<>();
        long cumulJours = 0;
        if (c.type().versements()) {
            LocalDate dernier = c.fin() != null && c.fin().isBefore(aujourdhui) ? c.fin() : aujourdhui;
            for (LocalDate j = c.debut(); !j.isAfter(dernier); j = j.plusDays(1)) {
                if (pasDu(c, feries, j).isPresent()) {
                    continue;
                }
                long m = plafonner(c, c.tarif(j), cumulJours);
                if (m <= 0) {
                    break;
                }
                cumulJours += m;
                dus.add(new Du(j, "Versement du " + jour(j), m, true));
            }
        }
        for (Ajustement a : ajustements) {
            if (!a.annule() && a.type().du()) {
                dus.add(new Du(a.date(), a.type().libelle() + " : " + a.motif(), a.montant(), false));
            }
        }
        // Le plus ancien d'abord ; le même jour, le versement avant les autres sommes.
        dus.sort(Comparator.comparing(Du::date).thenComparing(d -> !d.versement()));

        long credits = 0;
        for (Versement v : versements) {
            if (!v.annule()) {
                credits += v.montant();
            }
        }
        for (Ajustement a : ajustements) {
            if (!a.annule() && !a.type().du()) {
                credits += a.montant();
            }
        }
        long totalPaye = credits;

        // ---- répartition : chaque somme due prend sur les paiements, dans l'ordre
        long reste = credits;
        long totalDu = 0;
        long verseSurPrix = 0;
        LocalDate payeJusquAu = null;
        boolean continu = true;
        int joursEnRetard = 0;
        long montantEnRetard = 0;
        long attenduAujourdhui = 0;
        long resteAujourdhui = 0;
        Map<LocalDate, long[]> parJour = new HashMap<>();
        for (Du d : dus) {
            totalDu += d.montant();
            long couvert = Math.min(reste, d.montant());
            reste -= couvert;
            long manque = d.montant() - couvert;
            if (d.versement()) {
                verseSurPrix += couvert;
                parJour.put(d.date(), new long[]{d.montant(), couvert});
                if (continu && manque == 0) {
                    payeJusquAu = d.date();
                } else {
                    continu = false;
                }
                if (d.date().equals(aujourdhui)) {
                    attenduAujourdhui = d.montant();
                    resteAujourdhui = manque;
                } else if (manque > 0 && d.date().isBefore(aujourdhui)) {
                    joursEnRetard++;
                }
            }
            if (manque > 0 && d.date().isBefore(aujourdhui)) {
                montantEnRetard += manque;
            }
        }

        // ---- paiement d'avance : il couvre les jours dus suivants (dans les limites du contrat)
        LocalDate apres = dus.stream().filter(Du::versement).map(Du::date).max(Comparator.naturalOrder())
                .orElse(c.debut().minusDays(1));
        if (apres.isBefore(aujourdhui)) {
            apres = aujourdhui;
        }
        if (c.type().versements() && reste > 0 && continu && c.enCours()) {
            LocalDate j = apres.plusDays(1);
            for (int i = 0; i < HORIZON_JOURS && reste > 0; i++, j = j.plusDays(1)) {
                if (pasDu(c, feries, j).isPresent()) {
                    continue;
                }
                long m = plafonner(c, c.tarif(j), cumulJours);
                if (m <= 0 || reste < m) {
                    break;
                }
                reste -= m;
                cumulJours += m;
                verseSurPrix += m;
                payeJusquAu = j;
            }
        }

        // ---- location-vente : ce qui reste, combien de versements, quand ce sera fini en payant chaque jour
        long resteSurPrix = 0;
        int versementsRestants = 0;
        LocalDate finEstimee = null;
        if (c.type() == TypeContrat.LOCATION_VENTE) {
            resteSurPrix = Math.max(0, c.prixTotal() - verseSurPrix);
            long aCouvrir = resteSurPrix;
            LocalDate j = aujourdhui;
            boolean aujourdhuiDejaPaye = resteAujourdhui == 0 && attenduAujourdhui > 0;
            if (aujourdhuiDejaPaye || (payeJusquAu != null && !payeJusquAu.isBefore(aujourdhui))) {
                j = (payeJusquAu != null && payeJusquAu.isAfter(aujourdhui) ? payeJusquAu : aujourdhui).plusDays(1);
            }
            for (int i = 0; i < HORIZON_JOURS && aCouvrir > 0; i++, j = j.plusDays(1)) {
                if (c.joursRepos().contains(j.getDayOfWeek()) || feries.containsKey(j)
                        || (c.pause(j).isPresent() && !c.pause(j).get().aChargeDuConducteur())) {
                    continue;
                }
                long m = c.tarif(j);
                aCouvrir -= Math.min(aCouvrir, m);
                versementsRestants++;
                finEstimee = j;
            }
        }

        // ---- état
        Compte.Etat etat;
        if (!c.type().versements()) {
            etat = Etat.SANS_VERSEMENT;
        } else if (c.type() == TypeContrat.LOCATION_VENTE && resteSurPrix == 0) {
            etat = Etat.SOLDE;
        } else if (montantEnRetard > 0) {
            etat = Etat.EN_RETARD;
        } else if (!c.enCours()) {
            etat = Etat.TERMINE;
        } else if (pauseDuJour.isPresent()) {
            etat = Etat.EN_PAUSE;
        } else if (payeJusquAu != null && payeJusquAu.isAfter(aujourdhui)) {
            etat = Etat.EN_AVANCE;
        } else {
            etat = Etat.A_JOUR;
        }

        return new Situation(etat, totalDu, totalPaye, totalPaye - totalDu, payeJusquAu, joursEnRetard,
                montantEnRetard, attenduAujourdhui, resteAujourdhui, c.type().versements() ? c.tarifActuel(aujourdhui)
                : 0, c.prixTotal(), verseSurPrix, resteSurPrix, versementsRestants, finEstimee, pauseDuJour,
                releve(dus, versements, ajustements), calendrier(c, feries, parJour, aujourdhui, payeJusquAu));
    }

    /** En location-vente, le dernier jour ne doit que ce qui manque pour atteindre le total. */
    private static long plafonner(Contrat c, long tarif, long dejaDu) {
        if (c.type() != TypeContrat.LOCATION_VENTE) {
            return tarif;
        }
        return Math.max(0, Math.min(tarif, c.prixTotal() - dejaDu));
    }

    private static List<Ligne> releve(List<Du> dus, List<Versement> versements, List<Ajustement> ajustements) {
        record Mouvement(LocalDate date, int ordre, String libelle, long du, long paye) {
        }
        List<Mouvement> m = new ArrayList<>();
        dus.forEach(d -> m.add(new Mouvement(d.date(), 0, d.libelle(), d.montant(), 0)));
        versements.stream().filter(v -> !v.annule()).forEach(v -> m.add(new Mouvement(v.date(), 1,
                "Paiement " + v.numero() + " (" + v.mode().libelle()
                        + (v.reference().isEmpty() ? "" : " " + v.reference()) + ")", 0, v.montant())));
        ajustements.stream().filter(a -> !a.annule() && !a.type().du()).forEach(a -> m.add(new Mouvement(a.date(), 1,
                a.type().libelle() + " : " + a.motif(), 0, a.montant())));
        m.sort(Comparator.comparing(Mouvement::date).thenComparing(Mouvement::ordre));
        List<Ligne> lignes = new ArrayList<>();
        long solde = 0;
        for (Mouvement x : m) {
            solde += x.paye() - x.du();
            lignes.add(new Ligne(x.date(), x.libelle(), x.du(), x.paye(), solde));
        }
        return lignes;
    }

    /** Les 5 dernières semaines et la suivante, jour par jour. */
    private static List<JourCalendrier> calendrier(Contrat c, Map<LocalDate, String> feries,
                                                   Map<LocalDate, long[]> parJour, LocalDate aujourdhui,
                                                   LocalDate payeJusquAu) {
        List<JourCalendrier> jours = new ArrayList<>();
        LocalDate debut = aujourdhui.minusDays(34);
        while (debut.getDayOfWeek() != DayOfWeek.MONDAY) {
            debut = debut.minusDays(1);
        }
        LocalDate fin = aujourdhui.plusDays(7);
        while (fin.getDayOfWeek() != DayOfWeek.SUNDAY) {
            fin = fin.plusDays(1);
        }
        for (LocalDate j = debut; !j.isAfter(fin); j = j.plusDays(1)) {
            Optional<String> non = pasDu(c, feries, j);
            if (non.isPresent()) {
                String detail = switch (non.get()) {
                    case "FERIE" -> feries.get(j);
                    case "PAUSE" -> c.pause(j).map(Contrat.Pause::motif).orElse("");
                    case "REPOS" -> "Repos";
                    default -> "";
                };
                jours.add(new JourCalendrier(j, non.get(), detail, 0, 0));
                continue;
            }
            long[] dp = parJour.get(j);
            if (dp == null) {
                boolean couvertDavance = payeJusquAu != null && !j.isAfter(payeJusquAu) && j.isAfter(aujourdhui);
                jours.add(new JourCalendrier(j, couvertDavance ? "PAYE" : (j.isAfter(aujourdhui) ? "A_VENIR"
                        : "HORS_CONTRAT"), couvertDavance ? "Payé d'avance" : "", c.tarif(j), 0));
                continue;
            }
            String statut = dp[1] >= dp[0] ? "PAYE" : j.equals(aujourdhui) ? "AUJOURDHUI" : dp[1] > 0 ? "PARTIEL"
                    : "IMPAYE";
            jours.add(new JourCalendrier(j, statut, "", dp[0], dp[1]));
        }
        return jours;
    }

    /** « lundi 14/11 » */
    public static String jour(LocalDate j) {
        return j.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " "
                + String.format("%02d/%02d", j.getDayOfMonth(), j.getMonthValue());
    }

    /** Les jours de repos lisibles : « dimanche », « samedi et dimanche ». */
    public static String repos(Set<DayOfWeek> jours) {
        List<String> noms = jours.stream().sorted().map(d -> d.getDisplayName(TextStyle.FULL, Locale.FRENCH)).toList();
        if (noms.isEmpty()) {
            return "aucun";
        }
        return noms.size() == 1 ? noms.get(0) : String.join(", ", noms.subList(0, noms.size() - 1)) + " et "
                + noms.get(noms.size() - 1);
    }
}
