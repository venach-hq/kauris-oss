package com.venach.kauris.ventes.adapter.web;

import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.module.SectionAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.achats.application.AchatsService;
import com.venach.kauris.ventes.achats.application.PermissionsAchats;
import com.venach.kauris.ventes.application.PermissionsVentes;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.caisse.application.CaisseService;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.stock.application.PermissionsStock;
import com.venach.kauris.ventes.stock.application.StockService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.venach.kauris.kernel.module.SectionAccueil.Chiffre;
import static com.venach.kauris.kernel.module.SectionAccueil.Raccourci;
import static com.venach.kauris.kernel.module.SectionAccueil.Section;
import static com.venach.kauris.kernel.module.SectionAccueil.Ton;

/** Les sections du tableau de bord d'accueil : ventes, caisse, stock et achats. */
public final class SectionsAccueilVentes {

    private SectionsAccueilVentes() {
    }

    private static FormatsVentes formats(GererEntrepriseUseCase entreprises, Acces a) {
        return new FormatsVentes(entreprises.consulter(a.tenantId()).devise());
    }

    /** Ventes : facturé et encaissé du mois, reste à encaisser (et retard), devis en attente. */
    public static SectionAccueil ventes(VentesService ventes, GererEntrepriseUseCase entreprises) {
        return a -> {
            if (!a.peut(PermissionsVentes.CONSULTER)) {
                return Optional.empty();
            }
            FormatsVentes f = formats(entreprises, a);
            VentesService.Bilan b = ventes.bilan(a);
            List<Chiffre> c = new ArrayList<>();
            c.add(Chiffre.de("Facturé ce mois (HT)", f.montant(b.factureMoisHt()), "/ventes"));
            c.add(new Chiffre("Encaissé ce mois", f.montant(b.encaisseMois()), "", "/ventes", Ton.BON));
            c.add(new Chiffre("Reste à encaisser", f.montant(b.resteDu()), b.facturesEnRetard() == 0
                    ? "aucune facture en retard" : "dont " + f.montant(b.enRetard()) + " en retard ("
                    + b.facturesEnRetard() + " facture" + (b.facturesEnRetard() > 1 ? "s" : "") + ")",
                    "/ventes/impayes", b.facturesEnRetard() > 0 ? Ton.ALERTE : Ton.NEUTRE));
            c.add(Chiffre.de("Devis en attente", String.valueOf(b.devisEnAttente().size()),
                    "/ventes/documents?type=DEVIS"));
            List<Raccourci> r = new ArrayList<>();
            if (a.peut(PermissionsVentes.SAISIR)) {
                r.add(new Raccourci("Nouvelle facture", "/ventes/documents/nouveau?type=FACTURE"));
                r.add(new Raccourci("Nouveau devis", "/ventes/documents/nouveau?type=DEVIS"));
            }
            r.add(new Raccourci("Impayés", "/ventes/impayes"));
            return Optional.of(new Section("Ventes", "/ventes", 10, c, r));
        };
    }

    /** Caisse : ma caisse du jour (ouverte ou non), ses ventes et les espèces attendues. */
    public static SectionAccueil caisse(CaisseService caisse, GererEntrepriseUseCase entreprises) {
        return a -> {
            if (!a.peut(PermissionsVentes.CAISSE)) {
                return Optional.empty();
            }
            FormatsVentes f = formats(entreprises, a);
            Optional<SessionCaisse> ouverte = caisse.ouverte(a);
            List<Chiffre> c = new ArrayList<>();
            List<Raccourci> r = new ArrayList<>();
            if (ouverte.isPresent()) {
                SessionCaisse s = ouverte.get();
                CaisseService.Bilan b = caisse.bilan(a, s);
                boolean perimee = !s.jour().equals(caisse.aujourdHui());
                c.add(new Chiffre("Ma caisse", s.numero(), perimee ? "ouverte depuis un autre jour : à clôturer"
                        : "ouverte", "/caisse", perimee ? Ton.ALERTE : Ton.BON));
                c.add(Chiffre.de("Ventes de la caisse", f.montant(b.total()), "/caisse"));
                c.add(new Chiffre("Tickets", String.valueOf(b.tickets()), "", "/caisse", Ton.NEUTRE));
                c.add(new Chiffre("Espèces attendues", f.montant(b.attendu()), "fond compris", "/caisse/cloture",
                        Ton.NEUTRE));
                r.add(new Raccourci("Vendre", "/caisse"));
                r.add(new Raccourci("Clôturer", "/caisse/cloture"));
            } else {
                c.add(Chiffre.de("Ma caisse", "Fermée", "/caisse"));
                r.add(new Raccourci("Ouvrir ma caisse", "/caisse"));
            }
            r.add(new Raccourci("Calculatrice", "/caisse/calculatrice"));
            return Optional.of(new Section("Caisse", "/caisse", 20, c, r));
        };
    }

    /** Stock : valeur au coût moyen et articles sous le seuil. */
    public static SectionAccueil stock(StockService stock, GererEntrepriseUseCase entreprises) {
        return a -> {
            if (!a.peut(PermissionsStock.CONSULTER)) {
                return Optional.empty();
            }
            FormatsVentes f = formats(entreprises, a);
            StockService.Etat e = stock.etat(a, null, null, false);
            List<Chiffre> c = List.of(
                    Chiffre.de("Valeur du stock", f.montant(e.valeurTotale()), "/stock"),
                    Chiffre.de("Articles suivis", String.valueOf(e.lignes().size()), "/stock"),
                    new Chiffre("Stock bas", String.valueOf(e.alertes()), e.alertes() == 0 ? "rien à commander"
                            : "à réapprovisionner", "/stock?bas=true", e.alertes() > 0 ? Ton.ALERTE : Ton.BON));
            List<Raccourci> r = a.peut(PermissionsStock.MOUVEMENTER)
                    ? List.of(new Raccourci("Entrée ou sortie", "/stock/mouvement"),
                    new Raccourci("Inventaire", "/stock/inventaire")) : List.of();
            return Optional.of(new Section("Stock", "/stock", 30, c, r));
        };
    }

    /** Achats : dettes fournisseurs, retard, échéances de la semaine, commandes en cours. */
    public static SectionAccueil achats(AchatsService achats, GererEntrepriseUseCase entreprises) {
        return a -> {
            if (!a.peut(PermissionsAchats.CONSULTER)) {
                return Optional.empty();
            }
            FormatsVentes f = formats(entreprises, a);
            AchatsService.Bilan b = achats.bilan(a);
            List<Chiffre> c = List.of(
                    Chiffre.de("À payer aux fournisseurs", f.montant(b.aPayer()), "/achats"),
                    new Chiffre("En retard", f.montant(b.enRetard()), b.facturesEnRetard() + " facture(s)", "/achats",
                            b.facturesEnRetard() > 0 ? Ton.ALERTE : Ton.NEUTRE),
                    Chiffre.de("À payer sous 7 jours", f.montant(b.echeance7Jours()), "/achats"),
                    Chiffre.de("Commandes en cours", String.valueOf(b.enCours().size()), "/achats/commandes"));
            List<Raccourci> r = a.peut(PermissionsAchats.COMMANDER)
                    ? List.of(new Raccourci("Nouvelle commande", "/achats/commandes/nouvelle")) : List.of();
            return Optional.of(new Section("Achats", "/achats", 40, c, r));
        };
    }
}
