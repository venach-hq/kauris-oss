package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.module.EntreeMenu;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Des liens que l'application ajoute en haut de chaque page pour certaines personnes, hors des modules et des
 * permissions d'une organisation (par exemple la console d'exploitation de Kauris Cloud). L'application en
 * déclare un bean ; sans bean, rien ne s'ajoute.
 */
@FunctionalInterface
public interface LiensEnPlus {

    /** Les liens de la personne qui fait cette requête ; vide le plus souvent. */
    List<EntreeMenu> pour(HttpServletRequest requete);
}
