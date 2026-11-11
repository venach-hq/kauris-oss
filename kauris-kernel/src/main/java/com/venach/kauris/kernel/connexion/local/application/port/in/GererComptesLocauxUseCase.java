package com.venach.kauris.kernel.connexion.local.application.port.in;

import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;

import java.util.Optional;
import java.util.UUID;

/** Comptes du mode de connexion « local ». */
public interface GererComptesLocauxUseCase {

    record Installation(String nomOrganisation, String nomEtablissement, String nom, String email,
                        String motDePasse) {
    }

    /**
     * Ce qu'il faut pour vérifier une connexion.
     *
     * @param actif faux si la personne n'est plus membre actif d'aucune organisation : connexion refusée
     */
    record CompteConnectable(UUID compteId, String email, String empreinteMotDePasse, boolean actif) {
    }

    /** Le compte de cette adresse, s'il existe ; une adresse mal formée ne trouve rien. */
    Optional<CompteConnectable> pourConnexion(String email);

    /** Vrai tant qu'aucune organisation n'existe : l'écran d'installation est alors ouvert à tous. */
    boolean installationAFaire();

    /**
     * Premier démarrage : crée l'organisation, son établissement et le compte de son propriétaire.
     * Refusé dès qu'une organisation existe.
     */
    OrganisationOuverte installer(Installation installation);

    /**
     * Donne un compte (mot de passe initial) à un membre invité ; il devient actif.
     * Réservé aux propriétaires et administrateurs.
     */
    void creerComptePourMembre(UUID tenantId, UUID acteurId, UUID membreId, String motDePasse);

    /** La personne connectée change son propre mot de passe. */
    void changerMotDePasse(UUID compteId, String actuel, String nouveau);
}
