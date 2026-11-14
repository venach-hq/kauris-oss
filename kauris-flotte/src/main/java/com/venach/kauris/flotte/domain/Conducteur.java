package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un conducteur. Le téléphone sert aux demandes de paiement et aux messages ; le garant répond pour lui.
 *
 * @param actif faux : archivé (il garde son historique, il ne prend plus de contrat)
 */
public record Conducteur(UUID id, UUID tenantId, String nom, String telephone, String pieceIdentite,
                         String permisNumero, String permisCategorie, LocalDate permisExpireLe, String garantNom,
                         String garantTelephone, boolean actif, String notes, Instant creeLe) {

    public Conducteur {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        nom = Textes.propre(nom, "Nom", 120, true);
        telephone = telephone(telephone, "Téléphone");
        pieceIdentite = Textes.propre(pieceIdentite, "Pièce d'identité", 60, false);
        permisNumero = Textes.propre(permisNumero, "Numéro de permis", 40, false);
        permisCategorie = Textes.propre(permisCategorie, "Catégorie de permis", 20, false);
        garantNom = Textes.propre(garantNom, "Garant", 120, false);
        garantTelephone = telephone(garantTelephone, "Téléphone du garant");
        notes = Textes.propre(notes, "Notes", 500, false);
    }

    /** Chiffres, espaces et « + » ; 8 à 15 chiffres. Vide permis. */
    static String telephone(String saisie, String champ) {
        String v = Textes.propre(saisie, champ, 25, false);
        if (v.isEmpty()) {
            return v;
        }
        String chiffres = v.replaceAll("[\\s.-]", "");
        if (!chiffres.matches("\\+?\\d{8,15}")) {
            throw new ErreurMetier(champ + " : 8 à 15 chiffres, avec l'indicatif si besoin (+228…).");
        }
        return chiffres;
    }

    /** Ce que l'écran saisit ; l'identité vient du service. */
    public record Saisie(String nom, String telephone, String pieceIdentite, String permisNumero,
                         String permisCategorie, LocalDate permisExpireLe, String garantNom, String garantTelephone,
                         boolean actif, String notes) {
    }

    public static Conducteur de(UUID id, UUID tenantId, Saisie s, Instant creeLe) {
        return new Conducteur(id, tenantId, s.nom(), s.telephone(), s.pieceIdentite(), s.permisNumero(),
                s.permisCategorie(), s.permisExpireLe(), s.garantNom(), s.garantTelephone(), s.actif(), s.notes(),
                creeLe);
    }

    public Saisie saisie() {
        return new Saisie(nom, telephone, pieceIdentite, permisNumero, permisCategorie, permisExpireLe, garantNom,
                garantTelephone, actif, notes);
    }

    public boolean permisExpire(LocalDate jour) {
        return permisExpireLe != null && permisExpireLe.isBefore(jour);
    }
}
