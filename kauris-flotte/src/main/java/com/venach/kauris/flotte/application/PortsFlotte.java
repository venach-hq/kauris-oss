package com.venach.kauris.flotte.application;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.JourFerie;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ce dont la flotte a besoin : son stockage, et deux branchements que l'application peut fournir. */
public final class PortsFlotte {

    private PortsFlotte() {
    }

    public interface Vehicules {
        void sauvegarder(Vehicule v);

        Optional<Vehicule> vehicule(UUID tenantId, UUID id);

        List<Vehicule> vehicules(UUID tenantId);
    }

    public interface Conducteurs {
        void sauvegarder(Conducteur c);

        Optional<Conducteur> conducteur(UUID tenantId, UUID id);

        List<Conducteur> conducteurs(UUID tenantId);
    }

    public interface Contrats {
        void sauvegarder(Contrat c);

        Optional<Contrat> contrat(UUID tenantId, UUID id);

        /** Le contrat, verrouillé jusqu'à la fin de la transaction. */
        Optional<Contrat> verrouillerContrat(UUID tenantId, UUID id);

        List<Contrat> contrats(UUID tenantId);
    }

    public interface Versements {
        void sauvegarder(Versement v);

        Optional<Versement> versement(UUID tenantId, UUID id);

        List<Versement> versementsDuContrat(UUID tenantId, UUID contratId);

        /** Tous les versements de l'organisation saisis entre ces dates (incluses), annulés compris. */
        List<Versement> versementsEntre(UUID tenantId, LocalDate du, LocalDate au);

        /** Un versement déjà reçu avec cette référence (pour ne pas enregistrer deux fois un paiement reçu). */
        Optional<Versement> parReference(UUID tenantId, String reference);
    }

    public interface Ajustements {
        void sauvegarder(Ajustement a);

        Optional<Ajustement> ajustement(UUID tenantId, UUID id);

        List<Ajustement> ajustementsDuContrat(UUID tenantId, UUID contratId);
    }

    public interface Feries {
        List<JourFerie> feries(UUID tenantId);

        void ajouter(JourFerie f);

        void retirer(UUID tenantId, LocalDate date);
    }

    public interface Documents {
        void sauvegarder(DocumentVehicule d);

        Optional<DocumentVehicule> document(UUID tenantId, UUID id);

        List<DocumentVehicule> documents(UUID tenantId);

        void supprimer(UUID tenantId, UUID id);
    }

    public interface Numeros {
        /** Rang suivant de ce préfixe (« CT », « VR ») et de cette année, sans trou ; verrouillé jusqu'à la fin. */
        long prochain(UUID tenantId, String prefixe, int annee);
    }

    /**
     * Où va l'argent des versements. Kauris Cloud l'inscrit dans sa trésorerie ; en mode autonome, rien à faire.
     */
    public interface Encaissements {
        /** Comptes où un versement peut arriver ; vide : pas de choix à faire. */
        List<Compte> comptes(UUID tenantId);

        void versementEnregistre(Contrat contrat, Vehicule vehicule, Conducteur conducteur, Versement v, UUID compteId);

        void versementAnnule(Contrat contrat, Versement v);

        record Compte(UUID id, String libelle) {
        }

        Encaissements AUCUN = new Encaissements() {
            @Override
            public List<Compte> comptes(UUID tenantId) {
                return List.of();
            }

            @Override
            public void versementEnregistre(Contrat c, Vehicule v, Conducteur d, Versement x, UUID compteId) {
            }

            @Override
            public void versementAnnule(Contrat c, Versement v) {
            }
        };
    }

    /**
     * Demander à un conducteur en retard de payer. Kauris Cloud envoie une demande de paiement PI-SPI ; sans
     * branchement, l'écran propose un message WhatsApp ou SMS tout prêt.
     */
    public interface Demandes {
        boolean disponible(UUID tenantId);

        /** Envoie la demande ; rend une phrase pour la caissière (« Demande envoyée au 90 00 00 00 »). */
        String demander(Acces acces, Contrat contrat, Conducteur conducteur, Vehicule vehicule, long montant,
                        String motif);

        Demandes AUCUN = new Demandes() {
            @Override
            public boolean disponible(UUID tenantId) {
                return false;
            }

            @Override
            public String demander(Acces a, Contrat c, Conducteur d, Vehicule v, long montant, String motif) {
                throw new ErreurMetier("La demande de paiement n'est pas disponible ici : envoyez le message.");
            }
        };
    }
}
