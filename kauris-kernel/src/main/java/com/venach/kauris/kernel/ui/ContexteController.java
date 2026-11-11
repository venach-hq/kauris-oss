package com.venach.kauris.kernel.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** Changement d'organisation, et page affichée à qui n'en a aucune. */
@Controller
public class ContexteController {

    private final ContexteCourant contexte;

    public ContexteController(ContexteCourant contexte) {
        this.contexte = contexte;
    }

    @PostMapping("/organisation/choisir")
    public String choisir(@RequestParam UUID organisation, HttpServletRequest requete) {
        contexte.choisir(requete, organisation);
        return "redirect:/";
    }

    @GetMapping(ModeleCommun.PAGE_SANS_ORGANISATION)
    public String sansOrganisation(HttpServletRequest requete) {
        boolean membre = contexte.contexte(requete).map(c -> c.acces() != null).orElse(false);
        return membre ? "redirect:/" : "kauris/sans-organisation";
    }
}
