package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Ce que le mode « local » ajoute aux écrans du noyau : créer le compte d'un invité, changer son mot de passe. */
@Controller
public class ComptesController {

    private final GererComptesLocauxUseCase comptes;
    private final ContexteCourant contexte;

    public ComptesController(GererComptesLocauxUseCase comptes, ContexteCourant contexte) {
        this.comptes = comptes;
        this.contexte = contexte;
    }

    @PostMapping("/organisation/membres/{id}/compte-local")
    public String creerCompte(@PathVariable UUID id, @RequestParam String motDePasse, HttpServletRequest requete,
                              RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        try {
            comptes.creerComptePourMembre(acces.tenantId(), acces.membreId(), id, motDePasse);
            flash.addFlashAttribute("succes", "Compte créé : transmettez-lui son mot de passe initial.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:/organisation/membres";
    }

    @GetMapping("/compte")
    public String compte() {
        return "compte";
    }

    @PostMapping("/compte/mot-de-passe")
    public String changerMotDePasse(@AuthenticationPrincipal UtilisateurLocal personne,
                                    @RequestParam String actuel, @RequestParam String nouveau,
                                    RedirectAttributes flash) {
        try {
            comptes.changerMotDePasse(personne.compteId(), actuel, nouveau);
            flash.addFlashAttribute("succes", "Mot de passe changé.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:/compte";
    }
}
