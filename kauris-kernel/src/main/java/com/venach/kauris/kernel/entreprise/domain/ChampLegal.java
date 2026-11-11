package com.venach.kauris.kernel.entreprise.domain;

/**
 * Un identifiant que l'administration d'un pays attribue à une entreprise (numéro fiscal, employeur…).
 *
 * @param code    identifiant stable du champ, en minuscules : {@code nif}, {@code rccm}
 * @param sigle   ce qu'on écrit devant le numéro sur un document : « NIF », « RCCM »
 * @param libelle le nom complet, pour le formulaire
 * @param aide    à quoi il sert et qui le délivre
 */
public record ChampLegal(String code, String sigle, String libelle, String aide) {

    public static final int VALEUR_MAX = 60;
}
