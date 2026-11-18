package com.venach.kauris.compta.application;

import com.venach.kauris.compta.domain.Balance;
import com.venach.kauris.compta.domain.Cloture;
import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.EtatTva;
import com.venach.kauris.compta.domain.EtatsFinanciers;
import com.venach.kauris.compta.domain.Exercice;
import com.venach.kauris.compta.domain.Imputation;
import com.venach.kauris.compta.domain.Journal;
import com.venach.kauris.compta.domain.Lettrage;
import com.venach.kauris.compta.domain.LigneEcriture;
import com.venach.kauris.compta.domain.ModeleEtats;
import com.venach.kauris.compta.domain.Mouvement;
import com.venach.kauris.compta.domain.OperationRecue;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.compta.domain.PlanSyscohada;
import com.venach.kauris.compta.domain.Traduction;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.venach.kauris.compta.application.PermissionsCompta.CONSULTER;
import static com.venach.kauris.compta.application.PermissionsCompta.PARAMETRER;
import static com.venach.kauris.compta.application.PermissionsCompta.SAISIR;
import static com.venach.kauris.compta.application.PermissionsCompta.VALIDER;
import static com.venach.kauris.compta.application.PermissionsCompta.exiger;

/**
 * La comptabilité SYSCOHADA : plan, journaux, exercices, écritures (brouillard, validation, contre-passation),
 * opérations reçues des autres modules, lettrage, grand livre, balance, états financiers, TVA et clôture.
 * <p>
 * Tout se prépare au premier usage : plan SYSCOHADA, journaux, imputations, système normal.
 */
public class ComptaService {

    private final PortsCompta.Comptes comptes;
    private final PortsCompta.Journaux journaux;
    private final PortsCompta.Exercices exercices;
    private final PortsCompta.Ecritures ecritures;
    private final PortsCompta.Operations operations;
    private final PortsCompta.Imputations imputations;
    private final PortsCompta.ParametresCompta parametres;
    private final PortsCompta.Numeros numeros;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public ComptaService(PortsCompta.Comptes comptes, PortsCompta.Journaux journaux, PortsCompta.Exercices exercices,
                         PortsCompta.Ecritures ecritures, PortsCompta.Operations operations,
                         PortsCompta.Imputations imputations, PortsCompta.ParametresCompta parametres,
                         PortsCompta.Numeros numeros, UniteDeTravail transaction, Clock horloge) {
        this.comptes = comptes;
        this.journaux = journaux;
        this.exercices = exercices;
        this.ecritures = ecritures;
        this.operations = operations;
        this.imputations = imputations;
        this.parametres = parametres;
        this.numeros = numeros;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    private Instant maintenant() {
        return Instant.now(horloge);
    }

    // ================================================================== mise en place

    /** Prépare la comptabilité de l'entreprise au premier usage ; ne fait rien ensuite. */
    public Parametres preparer(UUID tenantId) {
        Optional<Parametres> p = parametres.parametres(tenantId);
        if (p.isPresent()) {
            return p.get();
        }
        return transaction.executer(() -> parametres.parametres(tenantId).orElseGet(() -> {
            for (PlanSyscohada.Ligne l : PlanSyscohada.COMPTES) {
                comptes.sauvegarder(new Compte(tenantId, l.numero(), l.libelle(),
                        Compte.lettrableParDefaut(l.numero()), true));
            }
            Journal.DEFAUT.forEach(j -> journaux.sauvegarder(j.pour(tenantId)));
            imputations.remplacer(tenantId, Imputation.DEFAUT.stream().map(i -> i.pour(tenantId)).toList());
            Parametres d = Parametres.defaut(tenantId);
            parametres.sauvegarder(d);
            return d;
        }));
    }

    public Parametres parametres(Acces a) {
        exiger(a, CONSULTER);
        return preparer(a.tenantId());
    }

    public void definirParametres(Acces a, Parametres.Systeme systeme, boolean validationAutomatique) {
        exiger(a, PARAMETRER);
        preparer(a.tenantId());
        parametres.sauvegarder(new Parametres(a.tenantId(), systeme, validationAutomatique));
    }

    // ================================================================== plan comptable

    public List<Compte> plan(Acces a) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return comptes.comptes(a.tenantId());
    }

    public Map<String, String> libelles(UUID tenantId) {
        Map<String, String> l = new HashMap<>();
        comptes.comptes(tenantId).forEach(c -> l.put(c.numero(), c.libelle()));
        return l;
    }

    public Compte ajouterCompte(Acces a, String numero, String libelle, boolean lettrable) {
        exiger(a, PARAMETRER);
        preparer(a.tenantId());
        String n = Compte.numero(numero);
        if (comptes.compte(a.tenantId(), n).isPresent()) {
            throw new ErreurMetier("Le compte " + n + " existe déjà.");
        }
        Compte c = new Compte(a.tenantId(), n, libelle, lettrable, true);
        comptes.sauvegarder(c);
        return c;
    }

    public void modifierCompte(Acces a, String numero, String libelle, boolean lettrable, boolean actif) {
        exiger(a, PARAMETRER);
        Compte c = comptes.compte(a.tenantId(), Compte.numero(numero))
                .orElseThrow(() -> new ElementIntrouvable("Compte introuvable."));
        comptes.sauvegarder(c.modifie(libelle, lettrable, actif));
    }

    /** Le compte, créé avec le libellé du plan (ou de son parent) s'il manque : un module peut avoir ses comptes. */
    private Compte compteOuCree(UUID tenantId, String numero) {
        return comptes.compte(tenantId, numero).orElseGet(() -> {
            String libelle = PlanSyscohada.libelle(numero);
            Compte c = new Compte(tenantId, numero, libelle.isEmpty() ? "Compte " + numero : libelle,
                    Compte.lettrableParDefaut(numero), true);
            comptes.sauvegarder(c);
            return c;
        });
    }

    // ================================================================== journaux

    public List<Journal> journaux(Acces a) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return journaux.journaux(a.tenantId());
    }

    public void enregistrerJournal(Acces a, String code, String libelle) {
        exiger(a, PARAMETRER);
        preparer(a.tenantId());
        Journal nouveau = new Journal(a.tenantId(), code, libelle, false);
        Optional<Journal> existant = journaux.journaux(a.tenantId()).stream()
                .filter(j -> j.code().equals(nouveau.code())).findFirst();
        journaux.sauvegarder(new Journal(a.tenantId(), nouveau.code(), nouveau.libelle(),
                existant.map(Journal::systeme).orElse(false)));
    }

    private Journal journal(UUID tenantId, String code) {
        String c = code == null ? "" : code.strip().toUpperCase();
        return journaux.journaux(tenantId).stream().filter(j -> j.code().equals(c)).findFirst()
                .orElseThrow(() -> new ErreurMetier("Journal « " + c + " » inconnu."));
    }

    // ================================================================== exercices

    public List<Exercice> exercices(Acces a) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        List<Exercice> l = new ArrayList<>(exercices.exercices(a.tenantId()));
        if (l.stream().noneMatch(e -> e.annee() == aujourdHui().getYear())
                && l.stream().noneMatch(e -> e.annee() > aujourdHui().getYear())) {
            l.add(Exercice.ouvert(a.tenantId(), aujourdHui().getYear()));
        }
        l.sort(Comparator.comparingInt(Exercice::annee).reversed());
        return l;
    }

    /** L'exercice ouvert de cette date, créé s'il n'existe pas encore ; une erreur s'il est clos. */
    private Exercice exerciceOuvert(UUID tenantId, LocalDate date) {
        int annee = date.getYear();
        Optional<Exercice> e = exercices.exercice(tenantId, annee);
        if (e.isPresent()) {
            if (e.get().clos()) {
                throw new ErreurMetier("L'exercice " + annee + " est clos : plus aucune écriture n'y entre.");
            }
            return e.get();
        }
        if (exercices.exercices(tenantId).stream().anyMatch(x -> x.clos() && x.annee() > annee)) {
            throw new ErreurMetier("L'exercice " + annee + " précède un exercice clos.");
        }
        Exercice nouveau = Exercice.ouvert(tenantId, annee);
        exercices.sauvegarder(nouveau);
        return nouveau;
    }

    // ================================================================== opérations reçues des autres modules

    /**
     * Reçoit une opération d'un autre module, dans sa transaction. Une opération déjà reçue est ignorée ; une
     * opération qui ne peut pas s'écrire reste « à revoir » : elle ne bloque jamais l'opération d'origine.
     */
    public void recevoir(OperationComptable o) {
        UUID t = o.tenantId();
        Parametres p = preparer(t);
        if (operations.operation(t, o.origine(), o.reference()).isPresent()) {
            return;
        }
        OperationRecue recue = new OperationRecue(UUID.randomUUID(), o, OperationRecue.Statut.A_REVOIR, "", null,
                maintenant(), null, "");
        operations.sauvegarder(ecrire(recue, p));
    }

    private OperationRecue ecrire(OperationRecue recue, Parametres p) {
        OperationComptable o = recue.operation();
        UUID t = o.tenantId();
        try {
            exerciceOuvert(t, o.date());
            List<LigneEcriture> lignes = Traduction.lignes(o, imputations.imputations(t));
            for (LigneEcriture l : lignes) {
                Compte c = compteOuCree(t, l.compte());
                if (!c.actif()) {
                    throw new ErreurMetier("Le compte " + c.numero() + " est désactivé : changez l'imputation ou "
                            + "réactivez-le.");
                }
            }
            Ecriture e = Ecriture.brouillard(t, journal(t, o.journal().code()).code(), o.date(), o.piece(),
                    o.libelle().isEmpty() ? o.journal().libelle() : o.libelle(), lignes, o.origine(),
                    o.reference(), null, maintenant(), "");
            if (p.validationAutomatique()) {
                e = numeroter(e, "validation automatique");
            }
            ecritures.sauvegarder(e);
            return recue.ecrite(e.id());
        } catch (ErreurMetier erreur) {
            return recue.aRevoir(erreur.getMessage());
        }
    }

    /** Annule une opération reçue : supprime son brouillard, ou passe l'écriture inverse à cette date. */
    public void annulerOperation(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
        Parametres p = preparer(tenantId);
        Optional<OperationRecue> trouvee = operations.operation(tenantId, origine, reference);
        if (trouvee.isEmpty()) {
            return;
        }
        OperationRecue o = trouvee.get();
        String texte = motif == null || motif.isBlank() ? "annulation dans le module d'origine" : motif.strip();
        if (o.statut() != OperationRecue.Statut.ECRITE || o.ecritureId() == null) {
            operations.sauvegarder(o.avecStatut(OperationRecue.Statut.ANNULEE, "", null, date, texte));
            return;
        }
        Optional<Ecriture> e = ecritures.ecriture(tenantId, o.ecritureId());
        if (e.isEmpty() || !e.get().validee()) {
            e.ifPresent(x -> ecritures.supprimer(tenantId, x.id()));
            operations.sauvegarder(o.avecStatut(OperationRecue.Statut.ANNULEE, "", null, date, texte));
            return;
        }
        try {
            Ecriture inverse = contrePassation(e.get(), date, texte, p.validationAutomatique());
            operations.sauvegarder(o.avecStatut(OperationRecue.Statut.ANNULEE, "", inverse.id(), date, texte));
        } catch (ErreurMetier erreur) {
            operations.sauvegarder(o.avecStatut(OperationRecue.Statut.ANNULATION_A_REVOIR, erreur.getMessage(),
                    o.ecritureId(), date, texte));
        }
    }

    public List<OperationRecue> aRevoir(Acces a) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return operations.aRevoir(a.tenantId());
    }

    /** Retente une opération à revoir (après avoir complété les imputations, rouvert l'exercice…). */
    public OperationRecue reessayer(Acces a, UUID id) {
        exiger(a, SAISIR);
        return transaction.executer(() -> {
            Parametres p = preparer(a.tenantId());
            OperationRecue o = operations.parId(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Opération introuvable."));
            OperationRecue r;
            if (o.statut() == OperationRecue.Statut.A_REVOIR) {
                r = ecrire(o, p);
            } else if (o.statut() == OperationRecue.Statut.ANNULATION_A_REVOIR) {
                Ecriture e = ecritures.ecriture(a.tenantId(), o.ecritureId())
                        .orElseThrow(() -> new ElementIntrouvable("Écriture introuvable."));
                LocalDate jour = o.annuleeLe().getYear() < aujourdHui().getYear() ? aujourdHui() : o.annuleeLe();
                try {
                    Ecriture inverse = contrePassation(e, jour, o.motifAnnulation(), p.validationAutomatique());
                    r = o.avecStatut(OperationRecue.Statut.ANNULEE, "", inverse.id(), jour, o.motifAnnulation());
                } catch (ErreurMetier erreur) {
                    r = o.avecStatut(o.statut(), erreur.getMessage(), o.ecritureId(), o.annuleeLe(),
                            o.motifAnnulation());
                }
            } else {
                throw new ErreurMetier("Cette opération n'est pas à revoir.");
            }
            operations.sauvegarder(r);
            return r;
        });
    }

    /** Écarte une opération à revoir : elle ne sera pas comptabilisée (elle reste visible, marquée ignorée). */
    public void ignorer(Acces a, UUID id) {
        exiger(a, VALIDER);
        transaction.executer(() -> {
            OperationRecue o = operations.parId(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Opération introuvable."));
            if (o.statut() != OperationRecue.Statut.A_REVOIR
                    && o.statut() != OperationRecue.Statut.ANNULATION_A_REVOIR) {
                throw new ErreurMetier("Cette opération n'est pas à revoir.");
            }
            operations.sauvegarder(o.avecStatut(OperationRecue.Statut.IGNOREE, "Écartée par " + a.nomMembre(),
                    o.ecritureId(), o.annuleeLe(), o.motifAnnulation()));
        });
    }

    // ================================================================== imputations

    public List<Imputation> imputations(Acces a) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return imputations.imputations(a.tenantId());
    }

    /** Les précisions de trésorerie déjà reçues (modes de paiement, comptes de dépôt). */
    public List<String> precisionsRecues(Acces a) {
        exiger(a, CONSULTER);
        return operations.precisionsDeTresorerie(a.tenantId());
    }

    public void enregistrerImputations(Acces a, List<Imputation> liste) {
        exiger(a, PARAMETRER);
        preparer(a.tenantId());
        Map<String, Imputation> uniques = new LinkedHashMap<>();
        for (Imputation i : liste) {
            uniques.put(i.role() + "|" + i.precision(), i.pour(a.tenantId()));
        }
        for (OperationComptable.Role r : OperationComptable.Role.values()) {
            if (r != OperationComptable.Role.COMPTE && !uniques.containsKey(r + "|")) {
                throw new ErreurMetier("Indiquez le compte de « " + r.libelle() + " ».");
            }
        }
        transaction.executer(() -> {
            for (Imputation i : uniques.values()) {
                compteOuCree(a.tenantId(), i.compte());
            }
            imputations.remplacer(a.tenantId(), List.copyOf(uniques.values()));
        });
    }

    // ================================================================== écritures

    public List<Ecriture> ecritures(Acces a, PortsCompta.Filtre filtre) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return ecritures.ecritures(a.tenantId(), filtre);
    }

    public Ecriture ecriture(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouver(a.tenantId(), id);
    }

    private Ecriture trouver(UUID tenantId, UUID id) {
        return ecritures.ecriture(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Écriture introuvable."));
    }

    public Optional<Ecriture> inverseDe(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return ecritures.inverseDe(a.tenantId(), id);
    }

    /** Une écriture saisie à la main, au brouillard. */
    public Ecriture saisir(Acces a, String journal, LocalDate date, String piece, String libelle,
                           List<LigneEcriture> lignes) {
        exiger(a, SAISIR);
        preparer(a.tenantId());
        return transaction.executer(() -> {
            Journal j = verifierSaisie(a.tenantId(), journal, date, lignes);
            Ecriture e = Ecriture.brouillard(a.tenantId(), j.code(), date, piece, libelle,
                    avecTiers(a.tenantId(), lignes), Ecriture.SAISIE,
                    "", null, maintenant(), a.nomMembre());
            ecritures.sauvegarder(e);
            return e;
        });
    }

    public Ecriture corriger(Acces a, UUID id, String journal, LocalDate date, String piece, String libelle,
                             List<LigneEcriture> lignes) {
        exiger(a, SAISIR);
        return transaction.executer(() -> {
            Ecriture e = trouver(a.tenantId(), id);
            if (e.validee()) {
                throw new ErreurMetier("Une écriture validée ne se modifie plus : contre-passez-la.");
            }
            exerciceOuvert(a.tenantId(), e.date());
            Journal j = verifierSaisie(a.tenantId(), journal, date, lignes);
            Ecriture c = e.corrigee(j.code(), date, piece, libelle, avecTiers(a.tenantId(), lignes));
            ecritures.sauvegarder(c);
            return c;
        });
    }

    /**
     * Un tiers saisi par son nom reprend l'identifiant du même tiers déjà présent sur le compte (client ou
     * fournisseur venu d'un autre module), pour que le lettrage et la balance par tiers les rapprochent.
     */
    private List<LigneEcriture> avecTiers(UUID tenantId, List<LigneEcriture> lignes) {
        List<LigneEcriture> r = new ArrayList<>();
        Map<String, String> connus = new HashMap<>();
        for (LigneEcriture l : lignes) {
            if (!l.tiers().isEmpty() || l.tiersNom().isEmpty()) {
                r.add(l);
                continue;
            }
            String cle = l.compte() + "|" + l.tiersNom().toLowerCase();
            String id = connus.computeIfAbsent(cle, k -> ecritures.mouvementsDuCompte(tenantId, l.compte(),
                    LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31)).stream()
                    .filter(m -> !m.tiers().isEmpty() && m.tiersNom().equalsIgnoreCase(l.tiersNom()))
                    .map(Mouvement::tiers).findFirst()
                    .orElse("NOM:" + l.tiersNom().toLowerCase().replaceAll("\\s+", " ")));
            r.add(l.pour(id.length() > 60 ? id.substring(0, 60) : id, l.tiersNom()));
        }
        return r;
    }

    private Journal verifierSaisie(UUID tenantId, String code, LocalDate date, List<LigneEcriture> lignes) {
        if (date == null) {
            throw new ErreurMetier("Indiquez la date de l'écriture.");
        }
        Journal j = journal(tenantId, code);
        if (j.aNouveaux()) {
            throw new ErreurMetier("Le journal des à-nouveaux se remplit à la clôture de l'exercice précédent.");
        }
        exerciceOuvert(tenantId, date);
        for (LigneEcriture l : lignes) {
            Compte c = comptes.compte(tenantId, l.compte()).orElseThrow(() -> new ErreurMetier(
                    "Le compte " + l.compte() + " n'existe pas : ajoutez-le au plan comptable."));
            if (!c.actif()) {
                throw new ErreurMetier("Le compte " + c.numero() + " est désactivé.");
            }
        }
        return j;
    }

    public void supprimer(Acces a, UUID id) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            Ecriture e = trouver(a.tenantId(), id);
            if (e.validee()) {
                throw new ErreurMetier("Une écriture validée ne se supprime pas : contre-passez-la.");
            }
            exerciceOuvert(a.tenantId(), e.date());
            ecritures.supprimer(a.tenantId(), id);
            if (!e.saisie() && !e.reference().isEmpty()) {
                operations.operation(a.tenantId(), e.origine(), e.reference()).ifPresent(o -> {
                    if (o.statut() == OperationRecue.Statut.ECRITE) {
                        operations.sauvegarder(o.aRevoir("Écriture supprimée du brouillard par " + a.nomMembre()
                                + " : réessayez ou ignorez."));
                    }
                });
            }
        });
    }

    /** Valide une écriture : elle reçoit son numéro et ne change plus. */
    public Ecriture valider(Acces a, UUID id) {
        exiger(a, VALIDER);
        return transaction.executer(() -> {
            Ecriture e = trouver(a.tenantId(), id);
            Ecriture v = numeroter(e, a.nomMembre());
            ecritures.sauvegarder(v);
            return v;
        });
    }

    /** Valide tout le brouillard jusqu'à cette date, par ordre de date. */
    public int validerBrouillard(Acces a, LocalDate jusqua) {
        exiger(a, VALIDER);
        return transaction.executer(() -> {
            int n = 0;
            for (Ecriture e : ecritures.brouillard(a.tenantId(), LocalDate.of(1900, 1, 1), jusqua)) {
                Optional<Exercice> x = exercices.exercice(a.tenantId(), e.date().getYear());
                if (x.isPresent() && x.get().clos()) {
                    continue;
                }
                ecritures.sauvegarder(numeroter(e, a.nomMembre()));
                n++;
            }
            return n;
        });
    }

    private Ecriture numeroter(Ecriture e, String par) {
        exerciceOuvert(e.tenantId(), e.date());
        long rang = numeros.prochain(e.tenantId(), e.journal(), e.date().getYear());
        return e.validee(Ecriture.numero(e.journal(), e.date().getYear(), rang), maintenant(), par);
    }

    /** Contre-passe une écriture validée à cette date (l'écriture inverse est validée tout de suite). */
    public Ecriture contrePasser(Acces a, UUID id, LocalDate date, String motif) {
        exiger(a, VALIDER);
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("Indiquez pourquoi l'écriture est contre-passée.");
        }
        return transaction.executer(() -> {
            Ecriture e = trouver(a.tenantId(), id);
            if (!e.validee()) {
                throw new ErreurMetier("Une écriture au brouillard se corrige ou se supprime.");
            }
            if (Journal.A_NOUVEAUX.equals(e.journal())) {
                throw new ErreurMetier("Les à-nouveaux se refont en rouvrant l'exercice précédent.");
            }
            return contrePassation(e, date == null ? aujourdHui() : date, motif.strip(), true);
        });
    }

    private Ecriture contrePassation(Ecriture e, LocalDate date, String motif, boolean valider) {
        if (ecritures.inverseDe(e.tenantId(), e.id()).isPresent()) {
            throw new ErreurMetier("L'écriture " + e.numero() + " est déjà contre-passée.");
        }
        if (date.isBefore(e.date())) {
            throw new ErreurMetier("La contre-passation ne peut pas précéder l'écriture.");
        }
        Ecriture inverse = e.inverse(date, motif, e.reference().isEmpty() ? "" : "ANNULATION:" + e.reference(),
                maintenant(), "");
        exerciceOuvert(e.tenantId(), date);
        if (valider) {
            inverse = numeroter(inverse, "contre-passation");
        }
        ecritures.sauvegarder(inverse);
        return inverse;
    }

    /** Une recette ou une dépense (système minimal de trésorerie), au brouillard du journal de trésorerie. */
    public Ecriture recetteOuDepense(Acces a, boolean recette, LocalDate date, String compteTresorerie,
                                     String compteNature, long montant, String libelle, String piece) {
        exiger(a, SAISIR);
        if (montant <= 0) {
            throw new ErreurMetier("Indiquez un montant positif.");
        }
        String tresorerie = Compte.numero(compteTresorerie);
        String nature = Compte.numero(compteNature);
        if (!tresorerie.startsWith("5")) {
            throw new ErreurMetier("Choisissez la caisse, la banque ou le compte Mobile Money.");
        }
        List<LigneEcriture> lignes = recette
                ? List.of(LigneEcriture.debit(tresorerie, montant, libelle), LigneEcriture.credit(nature, montant,
                libelle))
                : List.of(LigneEcriture.debit(nature, montant, libelle), LigneEcriture.credit(tresorerie, montant,
                libelle));
        return saisir(a, "TR", date, piece, libelle, lignes);
    }

    // ================================================================== lettrage

    /** Les lignes d'un compte de tiers, pour le lettrage. */
    public List<Mouvement> lignesALettrer(Acces a, String compte, boolean avecLettrees) {
        exiger(a, CONSULTER);
        String n = Compte.numero(compte);
        return ecritures.mouvementsDuCompte(a.tenantId(), n, LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31))
                .stream().filter(m -> m.validee() && (avecLettrees || !m.lettree())).toList();
    }

    /** Lettre ces lignes ({@code ecritureId:rang}) ensemble ; renvoie le code. */
    public String lettrer(Acces a, String compte, List<String> lignes) {
        exiger(a, SAISIR);
        String n = Compte.numero(compte);
        return transaction.executer(() -> {
            Map<String, Mouvement> parCle = new HashMap<>();
            lignesALettrer(a, n, true).forEach(m -> parCle.put(m.ecritureId() + ":" + m.rang(), m));
            List<Mouvement> choisies = new ArrayList<>();
            for (String cle : lignes) {
                Mouvement m = parCle.get(cle);
                if (m == null) {
                    throw new ErreurMetier("Une des lignes choisies n'est pas sur le compte " + n + ".");
                }
                choisies.add(m);
            }
            Lettrage.verifier(choisies);
            String code = Lettrage.prochain(ecritures.lettrages(a.tenantId(), n));
            appliquer(a.tenantId(), choisies, code);
            return code;
        });
    }

    public void delettrer(Acces a, String compte, String code) {
        exiger(a, SAISIR);
        String n = Compte.numero(compte);
        transaction.executer(() -> {
            List<Mouvement> l = lignesALettrer(a, n, true).stream().filter(m -> code.equals(m.lettrage())).toList();
            if (l.isEmpty()) {
                throw new ErreurMetier("Aucune ligne lettrée « " + code + " » sur le compte " + n + ".");
            }
            appliquer(a.tenantId(), l, "");
        });
    }

    /** Lettre automatiquement le compte (facture et règlements de même pièce, ou tout un tiers soldé). */
    public int lettrerAutomatiquement(Acces a, String compte) {
        exiger(a, SAISIR);
        String n = Compte.numero(compte);
        return transaction.executer(() -> {
            List<List<Mouvement>> groupes = Lettrage.automatique(lignesALettrer(a, n, false));
            Set<String> utilises = new java.util.HashSet<>(ecritures.lettrages(a.tenantId(), n));
            for (List<Mouvement> g : groupes) {
                String code = Lettrage.prochain(utilises);
                utilises.add(code);
                appliquer(a.tenantId(), g, code);
            }
            return groupes.size();
        });
    }

    private void appliquer(UUID tenantId, List<Mouvement> lignes, String code) {
        Map<UUID, List<Integer>> parEcriture = lignes.stream().collect(Collectors.groupingBy(Mouvement::ecritureId,
                Collectors.mapping(Mouvement::rang, Collectors.toList())));
        parEcriture.forEach((id, rangs) -> {
            Ecriture e = trouver(tenantId, id);
            List<LigneEcriture> l = new ArrayList<>(e.lignes());
            for (int r : rangs) {
                l.set(r, l.get(r).lettree(code));
            }
            ecritures.sauvegarder(e.avecLignes(l));
        });
    }

    // ================================================================== grand livre, balance

    /** Le grand livre d'un compte sur une période : report, lignes et solde progressif. */
    public record GrandLivre(Compte compte, LocalDate du, LocalDate au, long report, List<LigneLivre> lignes,
                             long totalDebit, long totalCredit) {
        public long solde() {
            return report + totalDebit - totalCredit;
        }
    }

    public record LigneLivre(Mouvement mouvement, long solde) {
    }

    public GrandLivre grandLivre(Acces a, String compte, LocalDate du, LocalDate au, String tiers) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        String n = Compte.numero(compte);
        Compte c = comptes.compte(a.tenantId(), n).orElseThrow(() -> new ElementIntrouvable("Compte introuvable."));
        LocalDate debutExercice = LocalDate.of(du.getYear(), 1, 1);
        long report = 0;
        long d = 0;
        long cr = 0;
        List<LigneLivre> lignes = new ArrayList<>();
        for (Mouvement m : ecritures.mouvementsDuCompte(a.tenantId(), n, debutExercice, au)) {
            if (tiers != null && !tiers.isBlank() && !tiers.equals(m.tiers())) {
                continue;
            }
            if (m.aNouveau() || m.date().isBefore(du)) {
                report += m.solde();
                continue;
            }
            d += m.debit();
            cr += m.credit();
            lignes.add(new LigneLivre(m, report + d - cr));
        }
        return new GrandLivre(c, du, au, report, lignes, d, cr);
    }

    /** La balance générale d'une période de l'exercice (brouillard compris si demandé). */
    public Balance balance(Acces a, LocalDate du, LocalDate au, boolean avecBrouillard) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        return Balance.parCompte(mouvementsExercice(a.tenantId(), du, au, avecBrouillard), du, au,
                libelles(a.tenantId()));
    }

    /** La balance d'un compte de tiers, par tiers. */
    public Balance balanceTiers(Acces a, String compte, LocalDate du, LocalDate au, boolean avecBrouillard) {
        exiger(a, CONSULTER);
        String n = Compte.numero(compte);
        Map<String, String> noms = new HashMap<>();
        List<Mouvement> l = mouvementsExercice(a.tenantId(), du, au, avecBrouillard).stream()
                .filter(m -> m.compte().equals(n)).toList();
        l.forEach(m -> noms.put(m.tiers(), m.tiers().isEmpty() ? "Sans tiers" : m.tiersNom()));
        return Balance.de(l, du, au, Mouvement::tiers, noms);
    }

    private List<Mouvement> mouvementsExercice(UUID tenantId, LocalDate du, LocalDate au, boolean avecBrouillard) {
        if (du.getYear() != au.getYear() || au.isBefore(du)) {
            throw new ErreurMetier("La période tient dans un exercice : du 1er janvier au 31 décembre au plus.");
        }
        return ecritures.mouvements(tenantId, LocalDate.of(du.getYear(), 1, 1), au).stream()
                .filter(m -> avecBrouillard || m.validee()).toList();
    }

    // ================================================================== états financiers

    /** Les états de l'exercice et de l'exercice précédent. */
    public record Etats(int annee, LocalDate au, EtatsFinanciers exercice, EtatsFinanciers precedent,
                        boolean avecBrouillard, int brouillard) {
    }

    public Etats etats(Acces a, int annee, LocalDate au, boolean avecBrouillard) {
        exiger(a, CONSULTER);
        Parametres p = preparer(a.tenantId());
        ModeleEtats m = ModeleEtats.pour(p.systeme());
        LocalDate fin = au == null || au.getYear() != annee ? LocalDate.of(annee, 12, 31) : au;
        LocalDate debut = LocalDate.of(annee, 1, 1);
        Balance n = Balance.parCompte(mouvementsExercice(a.tenantId(), debut, fin, avecBrouillard), debut, fin,
                Map.of());
        LocalDate debutAvant = LocalDate.of(annee - 1, 1, 1);
        LocalDate finAvant = LocalDate.of(annee - 1, 12, 31);
        Balance avant = Balance.parCompte(mouvementsExercice(a.tenantId(), debutAvant, finAvant, true), debutAvant,
                finAvant, Map.of());
        int brouillard = ecritures.brouillard(a.tenantId(), debut, fin).size();
        return new Etats(annee, fin, EtatsFinanciers.de(m, n.soldes()), EtatsFinanciers.de(m, avant.soldes()),
                avecBrouillard, brouillard);
    }

    // ================================================================== TVA

    public EtatTva tva(Acces a, YearMonth mois) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        List<Mouvement> l = ecritures.mouvements(a.tenantId(), LocalDate.of(mois.getYear(), 1, 1), mois.atEndOfMonth());
        long credit = l.stream().filter(m -> m.date().isBefore(mois.atDay(1))
                && m.compte().equals(EtatTva.COMPTE_CREDIT)).mapToLong(Mouvement::solde).sum();
        return EtatTva.de(mois, l, credit);
    }

    /** L'écriture de liquidation de la TVA du mois (au brouillard du journal OD), une seule fois par mois. */
    public Ecriture liquiderTva(Acces a, YearMonth mois) {
        exiger(a, VALIDER);
        return transaction.executer(() -> {
            String reference = "TVA:" + mois;
            Optional<Ecriture> deja = ecritures.parReference(a.tenantId(), EtatTva.ORIGINE_LIQUIDATION, reference);
            if (deja.isPresent() && ecritures.inverseDe(a.tenantId(), deja.get().id()).isEmpty()) {
                throw new ErreurMetier("La TVA de " + mois + " est déjà liquidée (écriture "
                        + (deja.get().numero().isEmpty() ? "au brouillard" : deja.get().numero()) + ").");
            }
            EtatTva t = tva(a, mois);
            List<LigneEcriture> lignes = t.liquidation();
            if (lignes.size() < 2) {
                throw new ErreurMetier("Pas de TVA à liquider en " + mois + ".");
            }
            for (LigneEcriture l : lignes) {
                compteOuCree(a.tenantId(), l.compte());
            }
            exerciceOuvert(a.tenantId(), mois.atEndOfMonth());
            Ecriture e = Ecriture.brouillard(a.tenantId(), Journal.OPERATIONS_DIVERSES, mois.atEndOfMonth(), "TVA "
                    + mois, "Liquidation de la TVA de " + mois, lignes, EtatTva.ORIGINE_LIQUIDATION,
                    deja.isPresent() ? reference + ":" + UUID.randomUUID().toString().substring(0, 8) : reference,
                    null, maintenant(), a.nomMembre());
            ecritures.sauvegarder(e);
            return e;
        });
    }

    // ================================================================== clôture

    /** Ce qui empêche de clôturer l'exercice ; vide s'il peut l'être. */
    public List<String> obstaclesCloture(Acces a, int annee) {
        exiger(a, CONSULTER);
        preparer(a.tenantId());
        List<String> o = new ArrayList<>();
        Optional<Exercice> e = exercices.exercice(a.tenantId(), annee);
        if (e.isPresent() && e.get().clos()) {
            o.add("L'exercice " + annee + " est déjà clos.");
            return o;
        }
        exercices.exercices(a.tenantId()).stream().filter(x -> x.annee() < annee && !x.clos()).findFirst()
                .ifPresent(x -> o.add("Clôturez d'abord l'exercice " + x.annee() + "."));
        if (!LocalDate.of(annee, 12, 31).isBefore(aujourdHui())) {
            o.add("L'exercice " + annee + " n'est pas terminé.");
        }
        int brouillard = ecritures.brouillard(a.tenantId(), LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31))
                .size();
        if (brouillard > 0) {
            o.add(brouillard + " écriture(s) au brouillard : validez-les ou supprimez-les.");
        }
        long revoir = operations.aRevoir(a.tenantId()).stream()
                .filter(x -> x.operation().date().getYear() == annee).count();
        if (revoir > 0) {
            o.add(revoir + " opération(s) de " + annee + " à revoir : écrivez-les ou ignorez-les.");
        }
        return o;
    }

    /**
     * Clôture l'exercice : plus aucune écriture n'y entre, et l'écriture d'à-nouveaux du 1er janvier suivant reprend
     * les soldes de bilan et le résultat (en instance d'affectation).
     */
    public Ecriture cloturer(Acces a, int annee) {
        exiger(a, VALIDER);
        return transaction.executer(() -> {
            List<String> obstacles = obstaclesCloture(a, annee);
            if (!obstacles.isEmpty()) {
                throw new ErreurMetier(String.join(" ", obstacles));
            }
            UUID t = a.tenantId();
            Exercice e = exercices.exercice(t, annee).orElseGet(() -> Exercice.ouvert(t, annee));
            List<Mouvement> m = ecritures.mouvements(t, e.debut(), e.fin());
            List<LigneEcriture> lignes = Cloture.aNouveaux(m, annee + 1,
                    numero -> comptes.compte(t, numero).map(Compte::lettrable).orElse(false));
            Exercice suivant = exercices.exercice(t, annee + 1).orElseGet(() -> Exercice.ouvert(t, annee + 1));
            exercices.sauvegarder(suivant);
            supprimerANouveaux(t, annee + 1);
            Ecriture an = null;
            if (lignes.size() >= 2) {
                for (LigneEcriture l : lignes) {
                    compteOuCree(t, l.compte());
                }
                an = Ecriture.brouillard(t, Journal.A_NOUVEAUX, suivant.debut(), "AN " + (annee + 1),
                        "À-nouveaux de l'exercice " + annee, lignes, "cloture", "AN:" + (annee + 1), null,
                        maintenant(), a.nomMembre()).validee(Ecriture.numero(Journal.A_NOUVEAUX, annee + 1, 1),
                        maintenant(), a.nomMembre());
                ecritures.sauvegarder(an);
            }
            exercices.sauvegarder(e.cloture(maintenant(), a.nomMembre()));
            return an;
        });
    }

    /** Rouvre un exercice clos (si le suivant ne l'est pas) : ses à-nouveaux sont retirés, à refaire à la clôture. */
    public void rouvrir(Acces a, int annee) {
        exiger(a, VALIDER);
        transaction.executer(() -> {
            UUID t = a.tenantId();
            Exercice e = exercices.exercice(t, annee).filter(Exercice::clos)
                    .orElseThrow(() -> new ErreurMetier("L'exercice " + annee + " n'est pas clos."));
            if (exercices.exercice(t, annee + 1).map(Exercice::clos).orElse(false)) {
                throw new ErreurMetier("Rouvrez d'abord l'exercice " + (annee + 1) + ".");
            }
            supprimerANouveaux(t, annee + 1);
            exercices.sauvegarder(e.rouvert());
        });
    }

    private void supprimerANouveaux(UUID t, int annee) {
        ecritures.parReference(t, "cloture", "AN:" + annee).ifPresent(x -> ecritures.supprimer(t, x.id()));
    }

    // ================================================================== accueil

    public record Resume(Parametres parametres, int brouillard, int aRevoir, Exercice exercice, long tresorerie,
                         long clients, long fournisseurs, long resultat) {
    }

    public Resume resume(Acces a) {
        exiger(a, CONSULTER);
        Parametres p = preparer(a.tenantId());
        int annee = aujourdHui().getYear();
        LocalDate debut = LocalDate.of(annee, 1, 1);
        List<Mouvement> m = ecritures.mouvements(a.tenantId(), debut, LocalDate.of(annee, 12, 31));
        long tresorerie = m.stream().filter(x -> x.compte().startsWith("5")).mapToLong(Mouvement::solde).sum();
        long clients = m.stream().filter(x -> x.compte().startsWith("41")).mapToLong(Mouvement::solde).sum();
        long fournisseurs = -m.stream().filter(x -> x.compte().startsWith("40")).mapToLong(Mouvement::solde).sum();
        long resultat = Cloture.resultat(m.stream().filter(x -> !x.aNouveau()).toList());
        Exercice e = exercices.exercice(a.tenantId(), annee).orElseGet(() -> Exercice.ouvert(a.tenantId(), annee));
        return new Resume(p, ecritures.brouillard(a.tenantId(), LocalDate.of(1900, 1, 1),
                LocalDate.of(9999, 12, 31)).size(), operations.aRevoir(a.tenantId()).size(), e, tresorerie,
                clients, fournisseurs, resultat);
    }
}
