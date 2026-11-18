package com.venach.kauris.flotte.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.JourFerie;
import com.venach.kauris.flotte.domain.ModeVersement;
import com.venach.kauris.flotte.domain.StatutVehicule;
import com.venach.kauris.flotte.domain.TypeContrat;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.venach.kauris.flotte.application.PermissionsFlotte.CONSULTER;
import static com.venach.kauris.flotte.application.PermissionsFlotte.ENCAISSER;
import static com.venach.kauris.flotte.application.PermissionsFlotte.GERER;
import static com.venach.kauris.flotte.application.PermissionsFlotte.exiger;

/**
 * La flotte : véhicules, conducteurs, contrats, versements, jours fériés et papiers. Ce que doit un conducteur se
 * calcule à la demande ({@link Compte}) ; on n'enregistre que les faits (contrat, paiements, avances, pauses).
 */
public class FlotteService {

    /** Une ligne de l'écran de caisse : un contrat avec versements, son véhicule, son conducteur, son compte. */
    public record LigneCaisse(Contrat contrat, Vehicule vehicule, Conducteur conducteur, Compte.Situation compte) {
    }

    /** Ce qui demande de l'attention : papiers à renouveler, permis expirés, conducteurs en retard. */
    public record Alertes(List<DocumentVehicule> papiers, List<Conducteur> permis, long enRetard, long montantEnRetard) {
        public int total() {
            return papiers.size() + permis.size();
        }
    }

    private final PortsFlotte.Vehicules vehicules;
    private final PortsFlotte.Conducteurs conducteurs;
    private final PortsFlotte.Contrats contrats;
    private final PortsFlotte.Versements versements;
    private final PortsFlotte.Ajustements ajustements;
    private final PortsFlotte.Feries feries;
    private final PortsFlotte.Documents documents;
    private final PortsFlotte.Numeros numeros;
    private final PortsFlotte.Encaissements encaissements;
    private final PortsFlotte.Demandes demandes;
    private final UniteDeTravail transaction;
    private final Clock horloge;
    private Comptabilisation comptabilite = Comptabilisation.AUCUNE;

    /** Branche la comptabilité (par le noyau) : elle reçoit les versements encaissés et annulés. */
    public void brancherComptabilite(Comptabilisation c) {
        this.comptabilite = c == null ? Comptabilisation.AUCUNE : c;
    }

    /**
     * Un versement en comptabilité : trésorerie (compte de dépôt, sinon mode) contre un produit de services, avec la
     * précision « FLOTTE » pour l'imputer à part (location de véhicules) si l'entreprise le souhaite.
     */
    static OperationComptable operation(Versement v, Vehicule vehicule, Conducteur conducteur, UUID compteId) {
        String precision = (compteId == null ? "" : compteId + "|") + v.mode().name();
        String libelle = "Versement " + v.numero() + " · " + vehicule.immatriculation() + " · " + conducteur.nom();
        return new OperationComptable(v.tenantId(), "flotte", "VERSEMENT:" + v.id(), v.date(),
                OperationComptable.Journal.TRESORERIE, v.numero(), libelle.length() > 160 ? libelle.substring(0, 160)
                : libelle, List.of(
                OperationComptable.Ligne.debit(OperationComptable.Role.TRESORERIE, precision, v.montant()),
                OperationComptable.Ligne.credit(OperationComptable.Role.VENTE_SERVICES, "FLOTTE", v.montant())));
    }

    public FlotteService(PortsFlotte.Vehicules vehicules, PortsFlotte.Conducteurs conducteurs,
                         PortsFlotte.Contrats contrats, PortsFlotte.Versements versements,
                         PortsFlotte.Ajustements ajustements, PortsFlotte.Feries feries,
                         PortsFlotte.Documents documents, PortsFlotte.Numeros numeros,
                         PortsFlotte.Encaissements encaissements, PortsFlotte.Demandes demandes,
                         UniteDeTravail transaction, Clock horloge) {
        this.vehicules = vehicules;
        this.conducteurs = conducteurs;
        this.contrats = contrats;
        this.versements = versements;
        this.ajustements = ajustements;
        this.feries = feries;
        this.documents = documents;
        this.numeros = numeros;
        this.encaissements = encaissements;
        this.demandes = demandes;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    private Instant maintenant() {
        return Instant.now(horloge);
    }

    // ================================================================== lecture

    public List<Vehicule> vehicules(Acces a) {
        exiger(a, CONSULTER);
        return vehicules.vehicules(a.tenantId()).stream().sorted(Comparator.comparing(Vehicule::immatriculation))
                .toList();
    }

    public Vehicule vehicule(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return vehicules.vehicule(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Véhicule introuvable."));
    }

    public List<Conducteur> conducteurs(Acces a) {
        exiger(a, CONSULTER);
        return conducteurs.conducteurs(a.tenantId()).stream()
                .sorted(Comparator.comparing(Conducteur::nom, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Conducteur conducteur(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return conducteurs.conducteur(a.tenantId(), id)
                .orElseThrow(() -> new ElementIntrouvable("Conducteur introuvable."));
    }

    public List<Contrat> contrats(Acces a) {
        exiger(a, CONSULTER);
        return contrats.contrats(a.tenantId()).stream().sorted(Comparator.comparing(Contrat::numero).reversed())
                .toList();
    }

    public Contrat contrat(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return contrats.contrat(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
    }

    public Map<LocalDate, String> feries(Acces a) {
        exiger(a, CONSULTER);
        return feriesDe(a.tenantId());
    }

    private Map<LocalDate, String> feriesDe(UUID tenantId) {
        return feries.feries(tenantId).stream().sorted(Comparator.comparing(JourFerie::date))
                .collect(Collectors.toMap(JourFerie::date, JourFerie::libelle, (x, y) -> x, LinkedHashMap::new));
    }

    /** Le compte du conducteur pour ce contrat, aujourd'hui. */
    public Compte.Situation compte(Acces a, UUID contratId) {
        Contrat c = contrat(a, contratId);
        return compteDe(c, feriesDe(a.tenantId()));
    }

    private Compte.Situation compteDe(Contrat c, Map<LocalDate, String> f) {
        return Compte.calculer(c, f, versements.versementsDuContrat(c.tenantId(), c.id()),
                ajustements.ajustementsDuContrat(c.tenantId(), c.id()), aujourdHui());
    }

    public List<Versement> versementsDuContrat(Acces a, UUID contratId) {
        contrat(a, contratId);
        return versements.versementsDuContrat(a.tenantId(), contratId).stream()
                .sorted(Comparator.comparing(Versement::saisiLe).reversed()).toList();
    }

    public List<Ajustement> ajustementsDuContrat(Acces a, UUID contratId) {
        contrat(a, contratId);
        return ajustements.ajustementsDuContrat(a.tenantId(), contratId).stream()
                .sorted(Comparator.comparing(Ajustement::date).reversed()).toList();
    }

    public Versement versement(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return versements.versement(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Versement introuvable."));
    }

    /**
     * L'écran de caisse : chaque contrat avec versements en cours (et ceux terminés qui doivent encore), les
     * retards en premier, du plus gros au plus petit.
     */
    public List<LigneCaisse> caisse(Acces a) {
        exiger(a, CONSULTER);
        Map<LocalDate, String> f = feriesDe(a.tenantId());
        Map<UUID, Vehicule> v = vehicules.vehicules(a.tenantId()).stream()
                .collect(Collectors.toMap(Vehicule::id, Function.identity()));
        Map<UUID, Conducteur> d = conducteurs.conducteurs(a.tenantId()).stream()
                .collect(Collectors.toMap(Conducteur::id, Function.identity()));
        return contrats.contrats(a.tenantId()).stream().filter(c -> c.type().versements())
                .map(c -> new LigneCaisse(c, v.get(c.vehiculeId()), d.get(c.conducteurId()), compteDe(c, f)))
                .filter(l -> l.vehicule() != null && l.conducteur() != null)
                .filter(l -> l.contrat().enCours() || l.compte().montantEnRetard() > 0)
                .sorted(Comparator.comparing((LigneCaisse l) -> -l.compte().montantEnRetard())
                        .thenComparing(l -> l.vehicule().immatriculation()))
                .toList();
    }

    public List<Versement> journal(Acces a, LocalDate du, LocalDate au) {
        exiger(a, CONSULTER);
        if (au.isBefore(du)) {
            throw new ErreurMetier("La fin de la période est avant son début.");
        }
        return versements.versementsEntre(a.tenantId(), du, au).stream()
                .sorted(Comparator.comparing(Versement::date).thenComparing(Versement::saisiLe)).toList();
    }

    public Alertes alertes(Acces a) {
        exiger(a, CONSULTER);
        LocalDate j = aujourdHui();
        Set<UUID> enService = vehicules.vehicules(a.tenantId()).stream()
                .filter(v -> v.statut() != StatutVehicule.CEDE).map(Vehicule::id).collect(Collectors.toSet());
        // Le dernier papier de chaque type par véhicule : un papier renouvelé fait oublier l'ancien.
        Map<String, DocumentVehicule> derniers = new LinkedHashMap<>();
        documents.documents(a.tenantId()).stream().filter(x -> x.expireLe() != null && enService.contains(x.vehiculeId()))
                .sorted(Comparator.comparing(DocumentVehicule::expireLe))
                .forEach(x -> derniers.put(x.vehiculeId() + "/" + x.type(), x));
        List<DocumentVehicule> papiers = derniers.values().stream().filter(x -> x.aRenouveler(j))
                .sorted(Comparator.comparing(DocumentVehicule::expireLe)).toList();
        List<Conducteur> permis = conducteurs.conducteurs(a.tenantId()).stream()
                .filter(c -> c.actif() && c.permisExpireLe() != null
                        && !c.permisExpireLe().isAfter(j.plusDays(DocumentVehicule.PREAVIS_JOURS))).toList();
        List<LigneCaisse> l = caisse(a);
        return new Alertes(papiers, permis, l.stream().filter(x -> x.compte().enRetard()).count(),
                l.stream().mapToLong(x -> x.compte().montantEnRetard()).sum());
    }

    public List<DocumentVehicule> documents(Acces a) {
        exiger(a, CONSULTER);
        return documents.documents(a.tenantId()).stream()
                .sorted(Comparator.comparing((DocumentVehicule x) -> x.expireLe() == null ? LocalDate.MAX : x.expireLe()))
                .toList();
    }

    public DocumentVehicule document(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return documents.document(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Document introuvable."));
    }

    // ================================================================== véhicules et conducteurs

    /** Crée ({@code id} null) ou corrige un véhicule. */
    public Vehicule enregistrerVehicule(Acces a, UUID id, Vehicule.Saisie saisie) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Vehicule v = id == null ? null : vehicules.vehicule(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Véhicule introuvable."));
            Vehicule nouveau = Vehicule.de(v == null ? UUID.randomUUID() : v.id(), a.tenantId(), saisie,
                    v == null ? maintenant() : v.creeLe());
            boolean pris = vehicules.vehicules(a.tenantId()).stream().anyMatch(x -> !x.id().equals(nouveau.id())
                    && x.immatriculation().equals(nouveau.immatriculation()));
            if (pris) {
                throw new ErreurMetier("Un véhicule porte déjà l'immatriculation " + nouveau.immatriculation() + ".");
            }
            if (!nouveau.referencePaiement().isEmpty()) {
                boolean refPrise = vehicules.vehicules(a.tenantId()).stream().anyMatch(x -> !x.id().equals(nouveau.id())
                        && x.referencePaiement().equalsIgnoreCase(nouveau.referencePaiement()));
                if (refPrise) {
                    throw new ErreurMetier("Cette référence de paiement est déjà celle d'un autre véhicule.");
                }
            }
            vehicules.sauvegarder(nouveau);
            return nouveau;
        });
    }

    /** Crée ({@code id} null) ou corrige un conducteur. */
    public Conducteur enregistrerConducteur(Acces a, UUID id, Conducteur.Saisie saisie) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Conducteur c = id == null ? null : conducteurs.conducteur(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Conducteur introuvable."));
            Conducteur nouveau = Conducteur.de(c == null ? UUID.randomUUID() : c.id(), a.tenantId(), saisie,
                    c == null ? maintenant() : c.creeLe());
            if (!nouveau.actif() && contrats.contrats(a.tenantId()).stream()
                    .anyMatch(x -> x.enCours() && x.conducteurId().equals(nouveau.id()))) {
                throw new ErreurMetier("Ce conducteur a un contrat en cours : terminez-le avant de l'archiver.");
            }
            conducteurs.sauvegarder(nouveau);
            return nouveau;
        });
    }

    // ================================================================== contrats

    public Contrat creerContrat(Acces a, UUID vehiculeId, UUID conducteurId, TypeContrat type, LocalDate debut,
                                long parJour, Set<DayOfWeek> repos, Long prixTotal, String notes) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Vehicule v = vehicules.vehicule(a.tenantId(), vehiculeId)
                    .orElseThrow(() -> new ErreurMetier("Choisissez le véhicule."));
            Conducteur d = conducteurs.conducteur(a.tenantId(), conducteurId)
                    .orElseThrow(() -> new ErreurMetier("Choisissez le conducteur."));
            if (!d.actif()) {
                throw new ErreurMetier(d.nom() + " est archivé.");
            }
            if (d.permisExpire(debut)) {
                throw new ErreurMetier("Le permis de " + d.nom() + " a expiré : mettez-le à jour sur sa fiche.");
            }
            if (v.statut() == StatutVehicule.CEDE || v.statut() == StatutVehicule.HORS_SERVICE) {
                throw new ErreurMetier("Ce véhicule est " + v.statut().libelle().toLowerCase() + ".");
            }
            for (Contrat x : contrats.contrats(a.tenantId())) {
                if (x.enCours() && x.vehiculeId().equals(vehiculeId)) {
                    throw new ErreurMetier("Ce véhicule a déjà un contrat en cours (" + x.numero() + ").");
                }
                if (x.enCours() && x.conducteurId().equals(conducteurId)) {
                    throw new ErreurMetier(d.nom() + " a déjà un contrat en cours (" + x.numero() + ").");
                }
            }
            int annee = debut.getYear();
            String numero = String.format("CT-%d-%04d", annee, numeros.prochain(a.tenantId(), "CT", annee));
            Contrat c = new Contrat(UUID.randomUUID(), a.tenantId(), numero, vehiculeId, conducteurId, type, debut,
                    null, type.versements() ? List.of(new Contrat.Tarif(debut, parJour)) : List.of(), repos,
                    List.of(), prixTotal, null, "", notes, maintenant());
            contrats.sauvegarder(c);
            return c;
        });
    }

    /** Toute correction d'un contrat passe ici : la fonction rend le contrat changé. */
    public Contrat changerContrat(Acces a, UUID contratId, Function<Contrat, Contrat> changement) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Contrat c = contrats.verrouillerContrat(a.tenantId(), contratId)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            Contrat nouveau = changement.apply(c);
            if (nouveau.enCours() && !c.enCours()) {
                for (Contrat x : contrats.contrats(a.tenantId())) {
                    if (!x.id().equals(c.id()) && x.enCours()
                            && (x.vehiculeId().equals(c.vehiculeId()) || x.conducteurId().equals(c.conducteurId()))) {
                        throw new ErreurMetier("Le véhicule ou le conducteur a un autre contrat en cours ("
                                + x.numero() + ").");
                    }
                }
            }
            contrats.sauvegarder(nouveau);
            return nouveau;
        });
    }

    /** Location-vente soldée : le contrat se termine et le véhicule est cédé au conducteur. */
    public Contrat transfererPropriete(Acces a, UUID contratId) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Contrat c = contrats.verrouillerContrat(a.tenantId(), contratId)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            if (c.type() != TypeContrat.LOCATION_VENTE || compteDe(c, feriesDe(a.tenantId())).etat()
                    != Compte.Etat.SOLDE) {
                throw new ErreurMetier("Le véhicule n'est pas encore payé en entier.");
            }
            Contrat fini = c.terminer(aujourdHui(), false, "Véhicule payé : propriété transférée au conducteur.");
            contrats.sauvegarder(fini);
            vehicules.vehicule(a.tenantId(), c.vehiculeId()).ifPresent(v -> {
                Vehicule.Saisie x = v.saisie();
                vehicules.sauvegarder(Vehicule.de(v.id(), v.tenantId(), new Vehicule.Saisie(x.type(),
                        x.immatriculation(), x.marque(), x.modele(), x.couleur(), x.annee(), x.chassis(),
                        x.miseEnService(), x.prixAchat(), x.compteur(), x.referencePaiement(), StatutVehicule.CEDE,
                        x.notes()), v.creeLe()));
            });
            return fini;
        });
    }

    // ================================================================== versements

    /** Les comptes où peut arriver l'argent (caisse, Mobile Money…) ; vide sans trésorerie branchée. */
    public List<PortsFlotte.Encaissements.Compte> comptesEncaissement(Acces a) {
        exiger(a, CONSULTER);
        return encaissements.comptes(a.tenantId());
    }

    /** La caissière encaisse un versement (n'importe quel montant). */
    public Versement encaisser(Acces a, UUID contratId, long montant, ModeVersement mode, String reference,
                               LocalDate date, UUID compteId) {
        exiger(a, ENCAISSER);
        LocalDate jour = date == null ? aujourdHui() : date;
        if (jour.isAfter(aujourdHui())) {
            throw new ErreurMetier("Un versement ne se date pas dans le futur.");
        }
        List<PortsFlotte.Encaissements.Compte> comptes = encaissements.comptes(a.tenantId());
        if (!comptes.isEmpty() && (compteId == null || comptes.stream().noneMatch(c -> c.id().equals(compteId)))) {
            throw new ErreurMetier("Choisissez le compte où arrive l'argent.");
        }
        return enregistrer(a.tenantId(), contratId, montant, mode, reference, jour, a.nomMembre(), compteId);
    }

    /** Le contrat en cours d'un véhicule, pour un paiement reçu sans caissière (alias du véhicule). */
    public Optional<Contrat> contratEnCours(UUID tenantId, UUID vehiculeId) {
        return contrats.contrats(tenantId).stream().filter(c -> c.enCours() && c.vehiculeId().equals(vehiculeId))
                .findFirst();
    }

    /** Un véhicule, sans contrôle d'accès (paiement reçu sans caissière). */
    public Optional<Vehicule> vehiculeDe(UUID tenantId, UUID vehiculeId) {
        return vehicules.vehicule(tenantId, vehiculeId);
    }

    /**
     * Un paiement reçu sans caissière (alias de paiement du véhicule, dans Kauris Cloud). Sans effet si un
     * versement porte déjà cette référence.
     */
    public Optional<Versement> recevoir(UUID tenantId, UUID contratId, long montant, ModeVersement mode,
                                        String reference, LocalDate date, UUID compteId) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Un paiement reçu a toujours une référence");
        }
        return transaction.executer(() -> versements.parReference(tenantId, reference.strip()).isPresent()
                ? Optional.<Versement>empty()
                : Optional.of(enregistrer(tenantId, contratId, montant, mode, reference, date, "Paiement reçu",
                compteId)));
    }

    private Versement enregistrer(UUID tenantId, UUID contratId, long montant, ModeVersement mode, String reference,
                                  LocalDate jour, String parQui, UUID compteId) {
        return transaction.executer(() -> {
            Contrat c = contrats.verrouillerContrat(tenantId, contratId)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            if (!c.type().versements()) {
                throw new ErreurMetier("Ce contrat n'a pas de versement.");
            }
            if (jour.isBefore(c.debut())) {
                throw new ErreurMetier("Ce versement est avant le début du contrat.");
            }
            String ref = reference == null ? "" : reference.strip();
            if (!ref.isEmpty() && versements.parReference(tenantId, ref).filter(x -> !x.annule()).isPresent()) {
                throw new ErreurMetier("Un versement porte déjà la référence " + ref + ".");
            }
            int annee = jour.getYear();
            String numero = String.format("VR-%d-%06d", annee, numeros.prochain(tenantId, "VR", annee));
            Versement v = new Versement(UUID.randomUUID(), tenantId, contratId, numero, jour, montant, mode, ref,
                    parQui, maintenant(), false, "", "", null);
            versements.sauvegarder(v);
            Vehicule vehicule = vehicules.vehicule(tenantId, c.vehiculeId()).orElseThrow();
            Conducteur conducteur = conducteurs.conducteur(tenantId, c.conducteurId()).orElseThrow();
            encaissements.versementEnregistre(c, vehicule, conducteur, v, compteId);
            if (v.montant() > 0) {
                comptabilite.enregistrer(operation(v, vehicule, conducteur, compteId));
            }
            return v;
        });
    }

    public Versement annulerVersement(Acces a, UUID versementId, String motif) {
        exiger(a, GERER);
        return transaction.executer(() -> {
            Versement v = versements.versement(a.tenantId(), versementId)
                    .orElseThrow(() -> new ElementIntrouvable("Versement introuvable."));
            Versement annule = v.annuler(motif, a.nomMembre(), maintenant());
            versements.sauvegarder(annule);
            contrats.contrat(a.tenantId(), v.contratId()).ifPresent(c -> encaissements.versementAnnule(c, annule));
            comptabilite.annuler(a.tenantId(), "flotte", "VERSEMENT:" + v.id(), aujourdHui().isBefore(v.date())
                    ? v.date() : aujourdHui(), "Versement annulé : " + annule.motifAnnulation());
            return annule;
        });
    }

    public Ajustement ajouterAjustement(Acces a, UUID contratId, Ajustement.Type type, long montant, String motif,
                                        LocalDate date) {
        exiger(a, GERER);
        Contrat c = contrat(a, contratId);
        if (!c.type().versements()) {
            throw new ErreurMetier("Ce contrat n'a pas de compte de versements.");
        }
        Ajustement x = new Ajustement(UUID.randomUUID(), a.tenantId(), contratId, date == null ? aujourdHui() : date,
                type, montant, motif, a.nomMembre(), maintenant(), false, "");
        transaction.executer(() -> {
            ajustements.sauvegarder(x);
            return x;
        });
        return x;
    }

    public void annulerAjustement(Acces a, UUID ajustementId, String motif) {
        exiger(a, GERER);
        transaction.executer(() -> {
            Ajustement x = ajustements.ajustement(a.tenantId(), ajustementId)
                    .orElseThrow(() -> new ElementIntrouvable("Introuvable."));
            ajustements.sauvegarder(x.annuler(motif));
            return null;
        });
    }

    // ================================================================== jours fériés

    public void declarerFerie(Acces a, LocalDate date, String libelle) {
        exiger(a, ENCAISSER);
        if (date == null) {
            throw new ErreurMetier("Indiquez la date.");
        }
        if (date.isAfter(aujourdHui().plusYears(2))) {
            throw new ErreurMetier("Déclarez les jours fériés au plus deux ans à l'avance.");
        }
        JourFerie f = new JourFerie(a.tenantId(), date, libelle);
        transaction.executer(() -> {
            if (feriesDe(a.tenantId()).containsKey(date)) {
                throw new ErreurMetier("Ce jour est déjà férié.");
            }
            feries.ajouter(f);
            return null;
        });
    }

    public void retirerFerie(Acces a, LocalDate date) {
        exiger(a, ENCAISSER);
        transaction.executer(() -> {
            feries.retirer(a.tenantId(), date);
            return null;
        });
    }

    // ================================================================== demande de paiement

    /** Vrai si la demande de paiement peut partir pour ce contrat ; sinon l'écran propose un message. */
    public boolean demandeDisponible(Acces a, UUID contratId) {
        return demandes.disponible(a.tenantId(), contrat(a, contratId));
    }

    /** Demande au conducteur de payer son retard et le versement du jour. */
    public String demanderPaiement(Acces a, UUID contratId) {
        exiger(a, ENCAISSER);
        Contrat c = contrat(a, contratId);
        Compte.Situation s = compteDe(c, feriesDe(a.tenantId()));
        if (s.aEncaisser() <= 0) {
            throw new ErreurMetier("Ce conducteur ne doit rien aujourd'hui.");
        }
        Conducteur d = conducteur(a, c.conducteurId());
        Vehicule v = vehicule(a, c.vehiculeId());
        if (d.telephone().isEmpty()) {
            throw new ErreurMetier("Ajoutez le téléphone de " + d.nom() + " sur sa fiche.");
        }
        return demandes.demander(a, c, d, v, s.aEncaisser(), "Versements " + v.immatriculation()
                + (s.joursEnRetard() > 0 ? " (" + s.joursEnRetard() + " jour(s) de retard)" : ""));
    }

    // ================================================================== papiers

    /** Crée ({@code id} null) ou corrige un papier. */
    public DocumentVehicule enregistrerDocument(Acces a, UUID id, DocumentVehicule.Saisie saisie) {
        exiger(a, GERER);
        vehicule(a, saisie.vehiculeId());
        if (id != null) {
            document(a, id);
        }
        DocumentVehicule d = DocumentVehicule.de(id == null ? UUID.randomUUID() : id, a.tenantId(), saisie);
        transaction.executer(() -> {
            documents.sauvegarder(d);
            return d;
        });
        return d;
    }

    public void supprimerDocument(Acces a, UUID id) {
        exiger(a, GERER);
        document(a, id);
        transaction.executer(() -> {
            documents.supprimer(a.tenantId(), id);
            return null;
        });
    }
}
