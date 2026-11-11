package com.venach.kauris.kernel.ui.organisation;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.module.PermissionsNoyau;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererRolesUseCase;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Écran « Rôles » : les quatre rôles de base, et ceux que le propriétaire crée pour les postes de son
 * entreprise en y cochant des permissions. Seul un propriétaire les modifie (contrôlé par le service).
 */
@Controller
@RequestMapping("/organisation/roles")
public class RolesController {

    private static final String PAGE = "/organisation/roles";

    /** Un rôle de base et ce qu'il donne, pour l'expliquer à l'écran. */
    public record RoleDeBase(String nom, String portee) {
    }

    private final ContexteCourant contexte;
    private final GererRolesUseCase roles;

    public RolesController(ContexteCourant contexte, GererRolesUseCase roles) {
        this.contexte = contexte;
        this.roles = roles;
    }

    @GetMapping
    public String roles(HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete, PermissionsNoyau.MEMBRES_CONSULTER);
        Map<String, List<Permission>> parGroupe = new LinkedHashMap<>();
        roles.permissions().forEach(p -> parGroupe.computeIfAbsent(p.groupe(), g -> new java.util.ArrayList<>()).add(p));
        model.addAttribute("permissions", parGroupe);
        model.addAttribute("personnalises", roles.lister(acces.tenantId()));
        model.addAttribute("proprietaire", acces.a(Role.PROPRIETAIRE));
        model.addAttribute("deBase", List.of(
                new RoleDeBase("Propriétaire", "Tout, y compris l'abonnement, les rôles et les autres propriétaires."),
                new RoleDeBase("Administrateur", "Toutes les permissions ; gère les établissements et les membres."),
                new RoleDeBase("Gestionnaire", "Voit et modifie les données, sans les réglages sensibles."),
                new RoleDeBase("Lecteur", "Voit tout ce qui se consulte, ne modifie rien.")));
        return "kauris/roles";
    }

    @PostMapping
    public String creer(@RequestParam String nom, @RequestParam(defaultValue = "") String description,
                        @RequestParam(required = false) Set<String> permissions, HttpServletRequest requete,
                        RedirectAttributes flash) {
        return agir(requete, flash, "Rôle créé. Donnez-le à un membre depuis l'écran Membres.",
                a -> roles.creer(a.tenantId(), a.membreId(), nom, description, permissions));
    }

    @PostMapping("/{id}")
    public String modifier(@PathVariable UUID id, @RequestParam String nom,
                           @RequestParam(defaultValue = "") String description,
                           @RequestParam(required = false) Set<String> permissions, HttpServletRequest requete,
                           RedirectAttributes flash) {
        return agir(requete, flash, "Rôle enregistré.",
                a -> roles.modifier(a.tenantId(), a.membreId(), id, nom, description, permissions));
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, "Rôle supprimé.", a -> roles.supprimer(a.tenantId(), a.membreId(), id));
    }

    private String agir(HttpServletRequest requete, RedirectAttributes flash, String succes, Consumer<Acces> action) {
        Acces acces = contexte.exiger(requete);
        try {
            action.accept(acces);
            flash.addFlashAttribute("succes", succes);
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE;
    }
}
