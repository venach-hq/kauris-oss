package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un salarié de l'entreprise : son identité, sa famille (qui compte pour l'impôt), son numéro à la caisse sociale et
 * la façon dont il reçoit son salaire. Ce qu'il gagne est dans son contrat.
 *
 * @param numeroSocial numéro d'assuré à la CNSS (Togo) ou à la CNPS (Côte d'Ivoire)
 * @param compte       RIB ou IBAN pour un virement, numéro de téléphone pour le Mobile Money
 */
public record Salarie(UUID id, UUID tenantId, String matricule, String nom, String prenoms, LocalDate naissance,
                      String telephone, String email, String adresse, String pieceIdentite, String numeroSocial,
                      Foyer foyer, boolean expatrie, RegimeFiscal regime, ModePaiement modePaiement, String banque,
                      String compte, boolean actif, String notes, Instant creeLe) {

    /** Ce que l'on saisit sur la fiche. */
    public record Saisie(String nom, String prenoms, LocalDate naissance, String telephone, String email,
                         String adresse, String pieceIdentite, String numeroSocial, SituationFamiliale situation,
                         int enfants, boolean conjointACharge, int autresPersonnes, boolean expatrie,
                         RegimeFiscal regime, ModePaiement modePaiement, String banque, String compte, boolean actif,
                         String notes) {
    }

    public Salarie {
        nom = Textes.obligatoire(nom, "Nom", 80).toUpperCase();
        prenoms = Textes.facultatif(prenoms, "Prénoms", 120);
        telephone = Textes.facultatif(telephone, "Téléphone", 25);
        email = Textes.facultatif(email, "Email", 190).toLowerCase();
        adresse = Textes.facultatif(adresse, "Adresse", 190);
        pieceIdentite = Textes.facultatif(pieceIdentite, "Pièce d'identité", 60);
        numeroSocial = Textes.facultatif(numeroSocial, "Numéro de sécurité sociale", 40);
        banque = Textes.facultatif(banque, "Banque", 80);
        compte = Textes.facultatif(compte, "Compte", 60);
        notes = Textes.facultatif(notes, "Notes", 500);
        regime = regime == null ? RegimeFiscal.NORMAL : regime;
        if (foyer == null || modePaiement == null) {
            throw new ErreurMetier("Fiche du salarié incomplète.");
        }
        if (naissance != null && (naissance.isBefore(LocalDate.of(1900, 1, 1)) || naissance.isAfter(LocalDate.now()))) {
            throw new ErreurMetier("Date de naissance invalide.");
        }
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ErreurMetier("Adresse email invalide.");
        }
        if (modePaiement.parVirement() && compte.isEmpty() && actif) {
            throw new ErreurMetier(modePaiement == ModePaiement.VIREMENT
                    ? "Indiquez le numéro de compte bancaire, ou choisissez un autre mode de paiement."
                    : "Indiquez le numéro Mobile Money, ou choisissez un autre mode de paiement.");
        }
    }

    public static Salarie de(UUID id, UUID tenantId, String matricule, Saisie s, Instant creeLe) {
        return new Salarie(id, tenantId, matricule, s.nom(), s.prenoms(), s.naissance(), s.telephone(), s.email(),
                s.adresse(), s.pieceIdentite(), s.numeroSocial(),
                new Foyer(s.situation(), s.enfants(), s.conjointACharge(), s.autresPersonnes()), s.expatrie(),
                s.regime(), s.modePaiement(), s.banque(), s.compte(), s.actif(), s.notes(), creeLe);
    }

    public Saisie saisie() {
        return new Saisie(nom, prenoms, naissance, telephone, email, adresse, pieceIdentite, numeroSocial,
                foyer.situation(), foyer.enfants(), foyer.conjointACharge(), foyer.autresPersonnes(), expatrie, regime,
                modePaiement, banque, compte, actif, notes);
    }

    /** « MENSAH Awa Akosua ». */
    public String nomComplet() {
        return prenoms.isEmpty() ? nom : nom + " " + prenoms;
    }
}
