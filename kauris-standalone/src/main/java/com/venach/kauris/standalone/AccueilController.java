package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.standalone.connexion.UtilisateurLocal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Page d'accueil provisoire : qui est connecté, dans quelle organisation, avec quels modules. */
@Controller
public class AccueilController {

    private final RegistreModules registre;
    private final ResoudreAccesUseCase acces;

    public AccueilController(RegistreModules registre, ResoudreAccesUseCase acces) {
        this.registre = registre;
        this.acces = acces;
    }

    @GetMapping("/")
    public String accueil(@AuthenticationPrincipal UtilisateurLocal personne, Model model) {
        model.addAttribute("email", personne.getUsername());
        model.addAttribute("acces", acces.accesDe(personne.identite()));
        model.addAttribute("modules", registre.tous());
        return "accueil";
    }
}
