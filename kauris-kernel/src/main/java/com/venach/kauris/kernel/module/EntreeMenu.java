package com.venach.kauris.kernel.module;

/**
 * Une entrée du menu de l'interface commune, et son grand bouton sur le tableau de bord.
 *
 * @param chemin     adresse dans l'application, qui commence par « / »
 * @param permission code de la permission qu'il faut pour voir l'entrée ; nul = tous les membres
 * @param categorie  la ligne du tableau de bord où se range le bouton
 * @param couleur    couleur du bouton (« #1D4ED8 ») ; foncée, pour que le texte blanc reste lisible
 * @param icone      nom de l'icône (ventes, caisse, achats, stock, flotte, paie, compta, tresorerie, organisation,
 *                   membres, roles, entreprise, profil, compte, abonnement, console, module), du fichier
 *                   /kauris/icones.svg
 */
public record EntreeMenu(String libelle, String chemin, String permission, CategorieModule categorie,
                         String couleur, String icone) {

    public static final String COULEUR_PAR_DEFAUT = "#475569";

    public EntreeMenu {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Libellé du menu requis");
        }
        if (chemin == null || !chemin.startsWith("/") || chemin.startsWith("//")) {
            throw new IllegalArgumentException("Chemin de menu invalide : " + chemin);
        }
        categorie = categorie == null ? CategorieModule.AUTRES : categorie;
        couleur = couleur == null || !couleur.matches("#[0-9A-Fa-f]{6}") ? COULEUR_PAR_DEFAUT : couleur;
        icone = icone == null || !icone.matches("[a-z]{2,20}") ? "module" : icone;
    }

    /** Une entrée sans précision de rangement : ligne « Autres », couleur neutre. */
    public EntreeMenu(String libelle, String chemin, String permission) {
        this(libelle, chemin, permission, CategorieModule.AUTRES, null, null);
    }

    /** Une entrée visible de tous les membres. */
    public EntreeMenu(String libelle, String chemin) {
        this(libelle, chemin, null);
    }
}
