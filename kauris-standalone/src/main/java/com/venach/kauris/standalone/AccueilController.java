package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Page d'accueil : l'organisation courante et les modules installés, sur l'interface commune. */
@Controller
public class AccueilController {

    private final RegistreModules registre;
    private final ContexteCourant contexte;

    public AccueilController(RegistreModules registre, ContexteCourant contexte) {
        this.registre = registre;
        this.contexte = contexte;
    }

    @GetMapping("/")
    public String accueil(HttpServletRequest requete, Model model) {
        contexte.exiger(requete);
        model.addAttribute("modules", registre.tous());
        return "kauris/accueil";
    }
}
