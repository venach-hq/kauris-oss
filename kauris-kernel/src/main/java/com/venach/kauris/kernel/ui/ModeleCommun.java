package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.module.EntreeMenu;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * Pour tous les écrans : met le contexte commun dans le modèle (sous le nom {@code kauris}) et traduit les
 * refus du noyau en pages lisibles.
 */
@ControllerAdvice
public class ModeleCommun {

    public static final String PAGE_SANS_ORGANISATION = "/sans-organisation";

    private final ContexteCourant contexte;
    private final String pageSansOrganisation;
    private final LiensKauris liens;
    private final List<LiensEnPlus> liensEnPlus;

    public ModeleCommun(ContexteCourant contexte) {
        this(contexte, PAGE_SANS_ORGANISATION);
    }

    public ModeleCommun(ContexteCourant contexte, String pageSansOrganisation) {
        this(contexte, pageSansOrganisation, new LiensKauris(null, null));
    }

    /**
     * @param pageSansOrganisation où envoyer une personne connectée qui n'est membre d'aucune organisation :
     *                             la page du noyau, ou celle de l'application (créer son organisation…)
     */
    public ModeleCommun(ContexteCourant contexte, String pageSansOrganisation, LiensKauris liens) {
        this(contexte, pageSansOrganisation, liens, List.of());
    }

    public ModeleCommun(ContexteCourant contexte, String pageSansOrganisation, LiensKauris liens,
                        List<LiensEnPlus> liensEnPlus) {
        if (pageSansOrganisation == null || !pageSansOrganisation.startsWith("/")
                || pageSansOrganisation.startsWith("//")) {
            throw new IllegalArgumentException("kauris.ui.page-sans-organisation doit être un chemin : "
                    + pageSansOrganisation);
        }
        this.contexte = contexte;
        this.pageSansOrganisation = pageSansOrganisation;
        this.liens = liens;
        this.liensEnPlus = List.copyOf(liensEnPlus);
    }

    @ModelAttribute("kauris")
    public ContexteKauris kauris(HttpServletRequest requete) {
        return contexte.contexte(requete).orElse(null);
    }

    @ModelAttribute("liens")
    public LiensKauris liens() {
        return liens;
    }

    @ModelAttribute("liensEnPlus")
    public List<EntreeMenu> liensEnPlus(HttpServletRequest requete) {
        return liensEnPlus.stream().flatMap(l -> l.pour(requete).stream()).toList();
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
        page.addObject("liens", liens);
        page.addObject("liensEnPlus", liensEnPlus(requete));
        return page;
    }
}
