package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.UUID;

/**
 * Un compte du plan comptable de l'entreprise : le plan SYSCOHADA révisé, que l'entreprise complète (sous-comptes
 * de banque, de ventes…) et dont elle peut corriger les libellés.
 *
 * @param numero    de 2 à 12 chiffres, le premier entre 1 et 8 (classe)
 * @param lettrable vrai si ses lignes se lettrent (clients, fournisseurs, personnel…)
 * @param actif     faux pour un compte qui ne reçoit plus d'écriture (il reste dans les états)
 */
public record Compte(UUID tenantId, String numero, String libelle, boolean lettrable, boolean actif) {

    /** Où le compte apparaît dans les états. */
    public enum Nature {
        BILAN, CHARGE, PRODUIT
    }

    public Compte {
        numero = numero(numero);
        libelle = Textes.obligatoire(libelle, "Libellé du compte " + numero, 120);
    }

    /** Un numéro de compte valide, ou une erreur claire. */
    public static String numero(String saisie) {
        String n = saisie == null ? "" : saisie.strip().replace(" ", "");
        if (!n.matches("[1-8][0-9]{1,11}")) {
            throw new ErreurMetier("Numéro de compte « " + (saisie == null ? "" : saisie.strip())
                    + " » : de 2 à 12 chiffres, commençant par la classe (1 à 8).");
        }
        return n;
    }

    public int classe() {
        return numero.charAt(0) - '0';
    }

    public Nature nature() {
        return natureDe(numero);
    }

    public static Nature natureDe(String numero) {
        int classe = numero.charAt(0) - '0';
        if (classe <= 5) {
            return Nature.BILAN;
        }
        if (classe == 6) {
            return Nature.CHARGE;
        }
        if (classe == 7) {
            return Nature.PRODUIT;
        }
        // Classe 8 : 81, 83, 85, 87, 89 sont des charges ; 82, 84, 86, 88 des produits.
        int sous = numero.length() > 1 ? numero.charAt(1) - '0' : 1;
        return sous % 2 == 1 ? Nature.CHARGE : Nature.PRODUIT;
    }

    /** Vrai pour un compte de charges ou de produits (soldé à la clôture). */
    public boolean gestion() {
        return nature() != Nature.BILAN;
    }

    /** Les comptes de tiers se lettrent par défaut. */
    public static boolean lettrableParDefaut(String numero) {
        return numero.startsWith("40") || numero.startsWith("41") || numero.startsWith("42")
                || numero.startsWith("46") || numero.startsWith("47");
    }

    public Compte modifie(String nouveauLibelle, boolean nouveauLettrable, boolean nouvelActif) {
        return new Compte(tenantId, numero, nouveauLibelle, nouveauLettrable, nouvelActif);
    }
}
