package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.module.SectionAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PermissionsPaie;
import com.venach.kauris.paie.domain.PeriodePaie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** La paie sur le tableau de bord : salariés sous contrat, dernière masse salariale, mois à clôturer, alertes. */
public class SectionAccueilPaie implements SectionAccueil {

    private final PaieService paie;
    private final GererEntrepriseUseCase entreprises;

    public SectionAccueilPaie(PaieService paie, GererEntrepriseUseCase entreprises) {
        this.paie = paie;
        this.entreprises = entreprises;
    }

    @Override
    public Optional<Section> pour(Acces a) {
        if (!a.peut(PermissionsPaie.CONSULTER)) {
            return Optional.empty();
        }
        FormatsPaie f = new FormatsPaie(entreprises.consulter(a.tenantId()).devise());
        long sousContrat = paie.salaries(a).stream().filter(l -> l.contrat() != null).count();
        List<Raccourci> r = new ArrayList<>();
        if (sousContrat == 0) {
            if (a.peut(PermissionsPaie.PREPARER)) {
                r.add(new Raccourci("Ajouter un salarié", "/paie/salaries/nouveau"));
            }
            return Optional.of(new Section("Paie", "/paie", 60, List.of(Chiffre.de("Salariés sous contrat", "0",
                    "/paie/salaries")), r));
        }
        PaieService.Alertes al = paie.alertes(a);
        List<Chiffre> c = new ArrayList<>();
        if (al.sansRegles()) {
            c.add(new Chiffre("Règles de paie", "à activer", "aucune règle pour ce mois", "/paie/regles", Ton.ALERTE));
        }
        c.add(Chiffre.de("Salariés sous contrat", String.valueOf(sousContrat), "/paie/salaries"));
        List<PeriodePaie> periodes = paie.periodes(a);
        if (!periodes.isEmpty()) {
            PeriodePaie dernier = periodes.get(0);
            PaieService.Totaux t = paie.mois(a, dernier.id()).totaux();
            c.add(new Chiffre("Masse salariale " + dernier.libelle(), f.montant(t.brut()), "coût total "
                    + f.montant(t.coutEmployeur()), "/paie/mois/" + dernier.id(), Ton.NEUTRE));
        }
        c.add(new Chiffre("Mois à clôturer", String.valueOf(al.aCloturer().size()), al.aCloturer().isEmpty()
                ? "rien en retard" : "le plus ancien : " + al.aCloturer().get(0).libelle(), "/paie",
                al.aCloturer().isEmpty() ? Ton.BON : Ton.ALERTE));
        if (al.total() > 0) {
            c.add(new Chiffre("Contrats à suivre", String.valueOf(al.total()), "fins de CDD, salariés sans contrat",
                    "/paie/salaries", Ton.ALERTE));
        }
        r.add(new Raccourci("Paie du mois", "/paie"));
        return Optional.of(new Section("Paie", "/paie", 60, c, r));
    }
}
