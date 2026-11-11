package com.venach.kauris.kernel.ui.organisation;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** « Mon profil » : le nom de la personne connectée, tel qu'il apparaît dans l'organisation courante. */
@Controller
public class ProfilController {

    private final ContexteCourant contexte;
    private final GererMembresUseCase membres;

    public ProfilController(ContexteCourant contexte, GererMembresUseCase membres) {
        this.contexte = contexte;
        this.membres = membres;
    }

    @GetMapping("/profil")
    public String profil(HttpServletRequest requete) {
        contexte.exiger(requete);
        return "kauris/profil";
    }

    /** Le membre modifié est toujours celui de la session : aucun identifiant ne vient du formulaire. */
    @PostMapping("/profil/nom")
    public String changerNom(@RequestParam String nom, HttpServletRequest requete, RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        try {
            membres.changerSonNom(acces.tenantId(), acces.membreId(), nom);
            flash.addFlashAttribute("succes", "Nom enregistré.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:/profil";
    }
}
