package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.TableauAccueil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Page d'accueil : le tableau de bord des modules (chiffres utiles et raccourcis), sur l'interface commune. */
@Controller
public class AccueilController {

    private final RegistreModules registre;
    private final ContexteCourant contexte;
    private final TableauAccueil tableau;
    private final java.time.Clock horloge;

    public AccueilController(RegistreModules registre, ContexteCourant contexte, TableauAccueil tableau,
                             java.time.Clock horloge) {
        this.registre = registre;
        this.contexte = contexte;
        this.tableau = tableau;
        this.horloge = horloge;
    }

    @GetMapping("/")
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete);
        model.addAttribute("modules", registre.tous());
        model.addAttribute("sections", tableau.sections(a));
        model.addAttribute("aujourdhui", TableauAccueil.jour(horloge));
        return "kauris/accueil";
    }
}
