package com.venach.kauris.kernel.ui.organisation;

import com.venach.kauris.kernel.accueil.application.port.in.GererFondAccueilUseCase;
import com.venach.kauris.kernel.accueil.domain.FondAccueil;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;

/**
 * L'image de fond du tableau de bord, choisie dans « Mon profil ». Le membre est toujours celui de la session :
 * aucun identifiant ne vient de la requête.
 */
@Controller
public class FondAccueilController {

    private static final String PAGE = "/profil";

    private final ContexteCourant contexte;
    private final GererFondAccueilUseCase fonds;

    public FondAccueilController(ContexteCourant contexte, GererFondAccueilUseCase fonds) {
        this.contexte = contexte;
        this.fonds = fonds;
    }

    @PostMapping("/profil/fond")
    public String definir(@RequestParam("fichier") MultipartFile fichier, HttpServletRequest requete,
                          RedirectAttributes flash) throws IOException {
        Acces acces = contexte.exiger(requete);
        try {
            if (fichier.getSize() > FondAccueil.TAILLE_MAX) {
                throw new ErreurMetier(FondAccueil.MESSAGE_TROP_LOURD);
            }
            fonds.definir(acces, fichier.getBytes());
            flash.addFlashAttribute("succes", "Image de fond enregistrée : elle s'affiche sur votre accueil.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE;
    }

    @PostMapping("/profil/fond/retirer")
    public String retirer(HttpServletRequest requete, RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        fonds.retirer(acces);
        flash.addFlashAttribute("succes", "Image retirée : l'accueil reprend son fond par défaut.");
        return "redirect:" + PAGE;
    }

    /**
     * L'image du membre connecté. L'adresse porte sa version (« ?v=… ») : le navigateur peut la garder longtemps,
     * une nouvelle image change d'adresse.
     */
    @GetMapping("/profil/fond")
    public ResponseEntity<byte[]> image(HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete);
        return fonds.fond(acces)
                .map(fond -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(fond.typeMime()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(fond.contenu()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
