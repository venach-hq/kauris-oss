package com.venach.kauris.kernel.organisation.domain;

import java.util.regex.Pattern;

/**
 * Qui est la personne, selon le service qui l'a connectée : le fournisseur (« venach » pour
 * account.venach.com, « local » pour les comptes du mode autonome) et son identifiant stable chez lui.
 * Kauris ne sait rien d'autre de l'identité : ni mot de passe, ni profil.
 */
public record Identite(String fournisseur, String sujet) {

    public static final String LOCAL = "local";
    public static final String VENACH = "venach";
    public static final int SUJET_MAX = 190;

    private static final Pattern FOURNISSEUR = Pattern.compile("[a-z][a-z0-9-]{1,19}");

    public Identite {
        if (fournisseur == null || !FOURNISSEUR.matcher(fournisseur).matches()) {
            throw new IllegalArgumentException("Fournisseur d'identité invalide : " + fournisseur);
        }
        if (sujet == null || sujet.isBlank() || sujet.length() > SUJET_MAX) {
            throw new IllegalArgumentException("Identifiant de la personne absent ou trop long");
        }
    }

    public static Identite locale(String sujet) {
        return new Identite(LOCAL, sujet);
    }

    public static Identite venach(String sujet) {
        return new Identite(VENACH, sujet);
    }
}
