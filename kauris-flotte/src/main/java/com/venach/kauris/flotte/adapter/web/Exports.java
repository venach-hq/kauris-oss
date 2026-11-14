package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Les tableaux exportables de la flotte (CSV, Excel, PDF). */
final class Exports {

    private Exports() {
    }

    static Tableau situation(List<FlotteService.LigneCaisse> lignes, FormatsFlotte f, LocalDate jour) {
        List<List<Object>> l = new ArrayList<>();
        long retard = 0;
        long aEncaisser = 0;
        for (FlotteService.LigneCaisse x : lignes) {
            Compte.Situation s = x.compte();
            l.add(ligne(x.vehicule().immatriculation(), x.conducteur().nom(), x.conducteur().telephone(),
                    x.contrat().numero(), s.etat().libelle(), s.tarifDuJour(), f.jour(s.payeJusquAu()),
                    (long) s.joursEnRetard(), s.montantEnRetard(), s.aEncaisser()));
            retard += s.montantEnRetard();
            aEncaisser += s.aEncaisser();
        }
        return new Tableau("Situation des conducteurs", "au " + f.jour(jour),
                List.of("Véhicule", "Conducteur", "Téléphone", "Contrat", "État", "Par jour", "Payé jusqu'au",
                        "Jours de retard", "Retard", "À encaisser"),
                l, List.of("Total", "", "", "", "", "", "", "", retard, aEncaisser));
    }

    static Tableau versements(List<Versement> liste, Map<UUID, String> contrats, FormatsFlotte f, LocalDate du,
                              LocalDate au) {
        List<List<Object>> l = new ArrayList<>();
        long total = 0;
        for (Versement v : liste.stream().sorted(Comparator.comparing(Versement::date)
                .thenComparing(Versement::numero)).toList()) {
            l.add(ligne(v.numero(), f.jour(v.date()), contrats.getOrDefault(v.contratId(), ""), v.mode().libelle(),
                    v.reference(), v.saisiPar(), v.annule() ? "Annulé : " + v.motifAnnulation() : "", v.montant()));
            if (!v.annule()) {
                total += v.montant();
            }
        }
        return new Tableau("Versements", du.equals(au) ? "du " + f.jour(du) : "du " + f.jour(du) + " au " + f.jour(au),
                List.of("Reçu", "Date", "Véhicule · conducteur", "Mode", "Référence", "Saisi par", "Annulation",
                        "Montant"), l, List.of("Total (hors annulés)", "", "", "", "", "", "", total));
    }

    static Tableau releve(Contrat c, Vehicule v, Conducteur d, Compte.Situation s, FormatsFlotte f) {
        List<List<Object>> l = new ArrayList<>();
        for (Compte.Ligne x : s.releve()) {
            l.add(ligne(f.jour(x.date()), x.libelle(), x.du(), x.paye(), x.solde()));
        }
        return new Tableau("Relevé " + c.numero(), v.immatriculation() + " · " + d.nom(),
                List.of("Date", "Opération", "Dû", "Payé", "Solde"), l,
                List.of("Total", s.etat().libelle(), s.totalDu(), s.totalPaye(), s.solde()));
    }

    static Tableau vehicules(List<Vehicule> liste, FormatsFlotte f) {
        List<List<Object>> l = new ArrayList<>();
        for (Vehicule v : liste.stream().sorted(Comparator.comparing(Vehicule::immatriculation)).toList()) {
            List<Object> r = new ArrayList<>(ligne(v.immatriculation(), v.type().libelle(),
                    (v.marque() + " " + v.modele()).strip(), v.couleur(), v.annee() == null ? "" : v.annee().toString(),
                    v.chassis(), f.jour(v.miseEnService())));
            r.add(v.prixAchat() == null ? "" : v.prixAchat());
            r.add(v.referencePaiement());
            r.add(v.statut().libelle());
            l.add(r);
        }
        return new Tableau("Véhicules", liste.size() + " véhicule(s)", List.of("Immatriculation", "Type", "Modèle",
                "Couleur", "Année", "Châssis", "Mise en service", "Prix d'achat", "Paiement", "Statut"), l, null);
    }

    static Tableau conducteurs(List<Conducteur> liste, FormatsFlotte f) {
        List<List<Object>> l = new ArrayList<>();
        for (Conducteur d : liste.stream().sorted(Comparator.comparing(Conducteur::nom)).toList()) {
            l.add(ligne(d.nom(), d.telephone(), d.pieceIdentite(), d.permisNumero(), d.permisCategorie(),
                    f.jour(d.permisExpireLe()), d.garantNom(), d.garantTelephone(), d.actif() ? "Actif" : "Archivé"));
        }
        return new Tableau("Conducteurs", liste.size() + " conducteur(s)", List.of("Nom", "Téléphone", "Pièce",
                "Permis", "Catégorie", "Permis expire le", "Garant", "Tél. garant", "Statut"), l, null);
    }

    static Tableau papiers(List<DocumentVehicule> liste, List<Vehicule> vehicules, FormatsFlotte f, LocalDate jour) {
        Map<UUID, String> immat = vehicules.stream().collect(Collectors.toMap(Vehicule::id,
                Vehicule::immatriculation));
        List<List<Object>> l = new ArrayList<>();
        long total = 0;
        for (DocumentVehicule d : liste.stream().sorted(Comparator.comparing(DocumentVehicule::expireLe,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            String etat = d.expire(jour) ? "Expiré" : d.aRenouveler(jour) ? "À renouveler" : "Valide";
            List<Object> r = new ArrayList<>(ligne(immat.getOrDefault(d.vehiculeId(), ""), d.type().libelle(),
                    d.reference(), f.jour(d.emisLe()), f.jour(d.expireLe()), etat));
            r.add(d.cout() == null ? "" : d.cout());
            l.add(r);
            total += d.cout() == null ? 0 : d.cout();
        }
        return new Tableau("Papiers des véhicules", "au " + f.jour(jour), List.of("Véhicule", "Papier", "Référence",
                "Émis le", "Expire le", "État", "Coût"), l, List.of("Total", "", "", "", "", "", total));
    }

    /** Une ligne qui accepte les cases vides (null). */
    static List<Object> ligne(Object... cases) {
        return java.util.Arrays.asList(cases);
    }

    static <T> Map<UUID, T> parId(List<T> l, Function<T, UUID> id) {
        return l.stream().collect(Collectors.toMap(id, x -> x));
    }
}
