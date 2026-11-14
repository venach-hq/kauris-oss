package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.module.EntreeMenu;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pour tous les écrans : met le contexte commun dans le modèle (sous le nom {@code kauris}) et traduit toute
 * erreur en page lisible (ou en JSON pour les appels des scripts et des banques) : refus, élément introuvable,
 * règle métier, saisie mal formée, doublon, base indisponible. Une erreur inattendue reçoit un code (« ERR-7F3A2C »)
 * noté dans le journal avec tout son détail, que la personne peut donner à l'assistance.
 */
@ControllerAdvice
public class ModeleCommun {

    private static final Logger log = LoggerFactory.getLogger(ModeleCommun.class);
    private static final SecureRandom HASARD = new SecureRandom();

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

    @ExceptionHandler(ErreurMetier.class)
    public ModelAndView regle(ErreurMetier refus, HttpServletRequest requete) {
        return refus(HttpStatus.BAD_REQUEST, refus.getMessage(), requete);
    }

    /** Une saisie ou une adresse mal formée : paramètre absent, nombre ou date illisible, corps illisible. */
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.validation.BindException.class,
            IllegalArgumentException.class})
    public ModelAndView saisie(Exception e, HttpServletRequest requete) {
        if (e instanceof IllegalArgumentException) {
            log.warn("Saisie refusée {} {}", requete.getMethod(), requete.getRequestURI(), e);
        } else {
            log.info("Demande mal formée {} {} : {}", requete.getMethod(), requete.getRequestURI(), e.getMessage());
        }
        return refus(HttpStatus.BAD_REQUEST, "Une information manque ou est mal écrite (nombre, date, choix…). "
                + "Revenez en arrière, vérifiez la saisie et réessayez.", requete);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ModelAndView tropGros(Exception e, HttpServletRequest requete) {
        return refus(HttpStatus.PAYLOAD_TOO_LARGE, "Le fichier envoyé est trop gros. Réduisez-le (photo moins lourde, "
                + "PDF compressé) et réessayez.", requete);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ModelAndView methode(Exception e, HttpServletRequest requete) {
        return refus(HttpStatus.METHOD_NOT_ALLOWED, "Cette page ne s'ouvre pas de cette façon (lien ou formulaire "
                + "périmé). Revenez à la page précédente et recommencez.", requete);
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ModelAndView pageInconnue(Exception e, HttpServletRequest requete) {
        return refus(HttpStatus.NOT_FOUND, "Cette page n'existe pas (ou plus). Vérifiez l'adresse, ou passez par "
                + "le menu.", requete);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ModelAndView doublon(Exception e, HttpServletRequest requete) {
        log.warn("Contrainte de la base refusée {} {} : {}", requete.getMethod(), requete.getRequestURI(),
                e.getMessage());
        return refus(HttpStatus.CONFLICT, "Rien n'a été enregistré : ces informations existent déjà (un doublon : "
                + "même numéro, même nom, même référence…) ou sont trop longues. Vérifiez et réessayez.", requete);
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    public ModelAndView concurrence(Exception e, HttpServletRequest requete) {
        log.warn("Conflit d'accès simultané {} {} : {}", requete.getMethod(), requete.getRequestURI(), e.getMessage());
        return refus(HttpStatus.CONFLICT, "Quelqu'un d'autre modifiait la même chose au même moment. Rien n'a été "
                + "perdu : rechargez la page et recommencez.", requete);
    }

    @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.transaction.CannotCreateTransactionException.class})
    public ModelAndView baseIndisponible(Exception e, HttpServletRequest requete) {
        String code = code();
        log.error("{} : base de données indisponible pour {} {}", code, requete.getMethod(), requete.getRequestURI(),
                e);
        return erreur(HttpStatus.SERVICE_UNAVAILABLE, "Le service est momentanément indisponible (base de données). "
                + "Patientez une minute et réessayez.", code, requete);
    }

    /** Tout le reste : un code pour l'assistance, le détail dans le journal. */
    @ExceptionHandler(Exception.class)
    public ModelAndView inattendue(Exception e, HttpServletRequest requete) throws Exception {
        String nom = e.getClass().getName();
        if (nom.startsWith("org.springframework.security.")) {
            throw e;   // connexion et refus : Spring Security s'en charge
        }
        if (nom.endsWith("ClientAbortException") || nom.endsWith("AsyncRequestNotUsableException")) {
            return null;   // la personne a fermé la page : rien à répondre
        }
        if (e instanceof org.springframework.web.server.ResponseStatusException r) {
            HttpStatus statut = HttpStatus.resolve(r.getStatusCode().value());
            return refus(statut == null ? HttpStatus.BAD_REQUEST : statut,
                    r.getReason() == null ? "Demande refusée." : r.getReason(), requete);
        }
        String code = code();
        log.error("{} : erreur inattendue {} {}", code, requete.getMethod(), requete.getRequestURI(), e);
        return erreur(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue : l'opération n'a pas "
                + "abouti. Réessayez ; si elle revient, donnez ce code à l'assistance.", code, requete);
    }

    /** « ERR-7F3A2C » : court, à dicter au téléphone, retrouvé dans le journal. */
    static String code() {
        return "ERR-" + Integer.toHexString(0x100000 + HASARD.nextInt(0xF00000)).toUpperCase(Locale.ROOT);
    }

    /** Un script (fetch) ou un service (webhook) attend du JSON, pas une page. */
    static boolean attendJson(HttpServletRequest requete) {
        String accepte = requete.getHeader("Accept");
        String uri = requete.getRequestURI();
        return uri.contains("/webhooks/") || uri.contains("/api/")
                || (accepte != null && accepte.contains("application/json") && !accepte.contains("text/html"));
    }

    private ModelAndView erreur(HttpStatus statut, String message, String code, HttpServletRequest requete) {
        ModelAndView page = refus(statut, message, requete);
        page.addObject("code", code);
        return page;
    }

    private ModelAndView refus(HttpStatus statut, String message, HttpServletRequest requete) {
        if (attendJson(requete)) {
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("status", "error");
            corps.put("message", message);
            ModelAndView json = new ModelAndView(new MappingJackson2JsonView(), corps);
            json.setStatus(statut);
            return json;
        }
        ModelAndView page = new ModelAndView("kauris/refus");
        page.setStatus(statut);
        try {
            page.addObject("kauris", contexte.contexte(requete).orElse(null));
        } catch (RuntimeException e) {
            page.addObject("kauris", null);   // base indisponible : la page s'affiche quand même, sans menu
        }
        page.addObject("message", message);
        page.addObject("liens", liens);
        try {
            page.addObject("liensEnPlus", liensEnPlus(requete));
        } catch (RuntimeException e) {
            page.addObject("liensEnPlus", List.of());
        }
        page.addObject("statut", statut.value());
        return page;
    }
}
