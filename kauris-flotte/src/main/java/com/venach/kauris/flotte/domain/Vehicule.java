package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Un véhicule de la flotte. Tout se corrige ; l'immatriculation reste unique dans l'organisation.
 *
 * @param referencePaiement où le conducteur paie ses versements pour ce véhicule (alias de paiement, numéro
 *                          Mobile Money…) ; vide si on paie seulement à la caisse
 * @param compteur          kilomètres (ou heures pour un engin), au dernier relevé
 */
public record Vehicule(UUID id, UUID tenantId, TypeVehicule type, String immatriculation, String marque, String modele,
                       String couleur, Integer annee, String chassis, LocalDate miseEnService, Long prixAchat,
                       Long compteur, String referencePaiement, StatutVehicule statut, String notes, Instant creeLe) {

    public Vehicule {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        if (type == null) {
            throw new ErreurMetier("Choisissez le type de véhicule.");
        }
        immatriculation = immatriculation(immatriculation);
        marque = Textes.propre(marque, "Marque", 60, false);
        modele = Textes.propre(modele, "Modèle", 60, false);
        couleur = Textes.propre(couleur, "Couleur", 30, false);
        chassis = Textes.propre(chassis, "Numéro de châssis", 40, false);
        referencePaiement = Textes.propre(referencePaiement, "Référence de paiement", 80, false);
        notes = Textes.propre(notes, "Notes", 500, false);
        statut = statut == null ? StatutVehicule.EN_SERVICE : statut;
        if (annee != null && (annee < 1950 || annee > 2100)) {
            throw new ErreurMetier("Année : entre 1950 et 2100.");
        }
        if (prixAchat != null) {
            Textes.montant(prixAchat, "Prix d'achat", true);
        }
        if (compteur != null && (compteur < 0 || compteur > 10_000_000L)) {
            throw new ErreurMetier("Compteur : un nombre positif.");
        }
    }

    /** « AB 1234 RT », « ab-1234-rt » → « AB-1234-RT » : majuscules, espaces et tirets unifiés. */
    static String immatriculation(String saisie) {
        String v = Textes.propre(saisie, "Immatriculation", 20, true).toUpperCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-").replaceAll("-{2,}", "-");
        if (!v.matches("[A-Z0-9][A-Z0-9-]*[A-Z0-9]|[A-Z0-9]")) {
            throw new ErreurMetier("Immatriculation : lettres, chiffres et tirets.");
        }
        return v;
    }

    /** Ce que l'écran saisit ; l'identité vient du service. */
    public record Saisie(TypeVehicule type, String immatriculation, String marque, String modele, String couleur,
                         Integer annee, String chassis, LocalDate miseEnService, Long prixAchat, Long compteur,
                         String referencePaiement, StatutVehicule statut, String notes) {
    }

    public static Vehicule de(UUID id, UUID tenantId, Saisie s, Instant creeLe) {
        return new Vehicule(id, tenantId, s.type(), s.immatriculation(), s.marque(), s.modele(), s.couleur(),
                s.annee(), s.chassis(), s.miseEnService(), s.prixAchat(), s.compteur(), s.referencePaiement(),
                s.statut(), s.notes(), creeLe);
    }

    public Saisie saisie() {
        return new Saisie(type, immatriculation, marque, modele, couleur, annee, chassis, miseEnService, prixAchat,
                compteur, referencePaiement, statut, notes);
    }

    public String libelle() {
        String nom = (marque + " " + modele).strip();
        return immatriculation + (nom.isEmpty() ? "" : " · " + nom);
    }
}
