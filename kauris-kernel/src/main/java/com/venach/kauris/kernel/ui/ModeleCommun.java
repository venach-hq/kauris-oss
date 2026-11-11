package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

/**
 * Pour tous les écrans : met le contexte commun dans le modèle (sous le nom {@code kauris}) et traduit les
 * refus du noyau en pages lisibles.
 */
@ControllerAdvice
public class ModeleCommun {

    public static final String PAGE_SANS_ORGANISATION = "/sans-organisation";

    private final ContexteCourant contexte;
    private final String pageSansOrganisation;

    public ModeleCommun(ContexteCourant contexte) {
        this(contexte, PAGE_SANS_ORGANISATION);
    }

    /**
     * @param pageSansOrganisation où envoyer une personne connectée qui n'est membre d'aucune organisation :
     *                             la page du noyau, ou celle de l'application (créer son organisation…)
     */
    public ModeleCommun(ContexteCourant contexte, String pageSansOrganisation) {
        if (pageSansOrganisation == null || !pageSansOrganisation.startsWith("/")
                || pageSansOrganisation.startsWith("//")) {
            throw new IllegalArgumentException("kauris.ui.page-sans-organisation doit être un chemin : "
                    + pageSansOrganisation);
        }
        this.contexte = contexte;
        this.pageSansOrganisation = pageSansOrganisation;
    }

    @ModelAttribute("kauris")
    public ContexteKauris kauris(HttpServletRequest requete) {
        return contexte.contexte(requete).orElse(null);
    }

    @ExceptionHandler(AucuneOrganisation.class)
    public String aucuneOrganisation() {
        return "redirect:" + pageSansOrganisation;
    }

    @ExceptionHandler(AccesRefuse.class)
    public ModelAndView accesRefuse(AccesRefuse refus, HttpServletRequest requete) {
        return refus(HttpStatus.FORBIDDEN, refus.getMessage(), requete);
    }

    @ExceptionHandler(ElementIntrouvable.class)
    public ModelAndView introuvable(ElementIntrouvable refus, HttpServletRequest requete) {
        return refus(HttpStatus.NOT_FOUND, refus.getMessage(), requete);
    }

    private ModelAndView refus(HttpStatus statut, String message, HttpServletRequest requete) {
        ModelAndView page = new ModelAndView("kauris/refus");
        page.setStatus(statut);
        page.addObject("kauris", contexte.contexte(requete).orElse(null));
        page.addObject("message", message);
        return page;
    }
}
