package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.application.PermissionsCompta;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.module.SectionAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** La comptabilité sur le tableau de bord : trésorerie, résultat de l'année, brouillard et opérations à revoir. */
public class SectionAccueilCompta implements SectionAccueil {

    private final ComptaService compta;
    private final GererEntrepriseUseCase entreprises;

    public SectionAccueilCompta(ComptaService compta, GererEntrepriseUseCase entreprises) {
        this.compta = compta;
        this.entreprises = entreprises;
    }

    @Override
    public Optional<Section> pour(Acces a) {
        if (!a.peut(PermissionsCompta.CONSULTER)) {
            return Optional.empty();
        }
        FormatsCompta f = new FormatsCompta(entreprises.consulter(a.tenantId()).devise());
        ComptaService.Resume r = compta.resume(a);
        List<Chiffre> c = new ArrayList<>();
        c.add(new Chiffre("Résultat " + r.exercice().annee(), f.montant(r.resultat()), "écritures de l'année",
                "/compta/etats", r.resultat() >= 0 ? Ton.BON : Ton.ALERTE));
        c.add(Chiffre.de("Trésorerie (comptes 5)", f.montant(r.tresorerie()), "/compta/balance"));
        if (r.aRevoir() > 0) {
            c.add(new Chiffre("Opérations à revoir", String.valueOf(r.aRevoir()), "reçues des autres modules",
                    "/compta/operations", Ton.ALERTE));
        }
        if (r.brouillard() > 0) {
            c.add(new Chiffre("Au brouillard", String.valueOf(r.brouillard()), "écritures à valider",
                    "/compta/ecritures?statut=BROUILLARD", Ton.NEUTRE));
        }
        List<Raccourci> raccourcis = new ArrayList<>();
        raccourcis.add(new Raccourci("Comptabilité", "/compta"));
        if (a.peut(PermissionsCompta.SAISIR)) {
            raccourcis.add(r.parametres().minimal() ? new Raccourci("Recette ou dépense", "/compta/recettes-depenses")
                    : new Raccourci("Saisir une écriture", "/compta/ecritures/nouvelle"));
        }
        return Optional.of(new Section("Comptabilité", "/compta", 70, c, raccourcis));
    }
}
