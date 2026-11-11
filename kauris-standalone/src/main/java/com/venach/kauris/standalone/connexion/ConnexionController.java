package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase.Installation;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Connexion locale et premier démarrage. Tant qu'aucune organisation n'existe, tout mène à l'écran
 * d'installation ; ensuite cet écran est fermé pour toujours.
 */
@Controller
public class ConnexionController {

    private final GererComptesLocauxUseCase comptes;

    public ConnexionController(GererComptesLocauxUseCase comptes) {
        this.comptes = comptes;
    }

    @GetMapping(SecuriteLocaleConfig.PAGE_CONNEXION)
    public String connexion(Authentication connecte) {
        if (comptes.installationAFaire()) {
            return "redirect:" + SecuriteLocaleConfig.PAGE_INSTALLATION;
        }
        if (connecte != null && connecte.isAuthenticated() && !(connecte instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "connexion";
    }

    @GetMapping(SecuriteLocaleConfig.PAGE_INSTALLATION)
    public String installation(Model model) {
        if (!comptes.installationAFaire()) {
            return "redirect:" + SecuriteLocaleConfig.PAGE_CONNEXION;
        }
        model.addAttribute("saisie", new Installation("", "", "", "", ""));
        return "installation";
    }

    @PostMapping(SecuriteLocaleConfig.PAGE_INSTALLATION)
    public String installer(@RequestParam(defaultValue = "") String nomOrganisation,
                            @RequestParam(defaultValue = "") String nomEtablissement,
                            @RequestParam(defaultValue = "") String nom,
                            @RequestParam(defaultValue = "") String email,
                            @RequestParam(defaultValue = "") String motDePasse,
                            Model model) {
        if (!comptes.installationAFaire()) {
            return "redirect:" + SecuriteLocaleConfig.PAGE_CONNEXION;
        }
        try {
            comptes.installer(new Installation(nomOrganisation, nomEtablissement, nom, email, motDePasse));
        } catch (ErreurMetier refus) {
            // On réaffiche ce qui a été saisi, sauf le mot de passe.
            model.addAttribute("saisie", new Installation(nomOrganisation, nomEtablissement, nom, email, ""));
            model.addAttribute("erreur", refus.getMessage());
            return "installation";
        }
        return "redirect:" + SecuriteLocaleConfig.PAGE_CONNEXION + "?installe";
    }
}
