package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.application.PermissionsFlotte;
import com.venach.kauris.flotte.domain.Versement;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.module.SectionAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** La flotte sur le tableau de bord d'accueil : encaissé et à encaisser aujourd'hui, retards, papiers. */
public class SectionAccueilFlotte implements SectionAccueil {

    private final FlotteService flotte;
    private final GererEntrepriseUseCase entreprises;

    public SectionAccueilFlotte(FlotteService flotte, GererEntrepriseUseCase entreprises) {
        this.flotte = flotte;
        this.entreprises = entreprises;
    }

    @Override
    public Optional<Section> pour(Acces a) {
        if (!a.peut(PermissionsFlotte.CONSULTER)) {
            return Optional.empty();
        }
        FormatsFlotte f = new FormatsFlotte(entreprises.consulter(a.tenantId()).devise());
        List<FlotteService.LigneCaisse> lignes = flotte.caisse(a);
        if (lignes.isEmpty() && flotte.vehicules(a).isEmpty()) {
            return Optional.of(new Section("Flotte", "/flotte", 50, List.of(Chiffre.de("Véhicules", "0",
                    "/flotte/vehicules")), a.peut(PermissionsFlotte.GERER)
                    ? List.of(new Raccourci("Ajouter un véhicule", "/flotte/vehicules/nouveau"))
                    : List.of()));
        }
        LocalDate jour = flotte.aujourdHui();
        long encaisse = flotte.journal(a, jour, jour).stream().filter(v -> !v.annule())
                .mapToLong(Versement::montant).sum();
        long aEncaisser = lignes.stream().mapToLong(l -> l.compte().aEncaisser()).sum();
        FlotteService.Alertes al = flotte.alertes(a);
        int papiers = al.papiers().size() + al.permis().size();
        List<Chiffre> c = new ArrayList<>();
        c.add(new Chiffre("Encaissé aujourd'hui", f.montant(encaisse), lignes.size() + " contrat(s) en cours",
                "/flotte/versements", Ton.BON));
        c.add(Chiffre.de("Reste à encaisser", f.montant(aEncaisser), "/flotte"));
        c.add(new Chiffre("Conducteurs en retard", String.valueOf(al.enRetard()), al.enRetard() == 0 ? "tout le monde"
                + " est à jour" : f.montant(al.montantEnRetard()) + " de retard", "/flotte?voir=retard",
                al.enRetard() > 0 ? Ton.ALERTE : Ton.BON));
        c.add(new Chiffre("Papiers et permis", String.valueOf(papiers), papiers == 0 ? "rien n'expire bientôt"
                : "à renouveler", "/flotte/papiers", papiers > 0 ? Ton.ALERTE : Ton.NEUTRE));
        List<Raccourci> r = new ArrayList<>();
        r.add(new Raccourci("Caisse de la flotte", "/flotte"));
        if (a.peut(PermissionsFlotte.ENCAISSER)) {
            r.add(new Raccourci("Jours fériés", "/flotte/feries"));
        }
        return Optional.of(new Section("Flotte", "/flotte", 50, c, r));
    }
}
