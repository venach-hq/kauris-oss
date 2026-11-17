package com.venach.kauris.paie.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.Parametres;
import com.venach.kauris.paie.domain.PaysPaie;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.Variables;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.venach.kauris.paie.application.PermissionsPaie.CLOTURER;
import static com.venach.kauris.paie.application.PermissionsPaie.CONSULTER;
import static com.venach.kauris.paie.application.PermissionsPaie.PARAMETRER;
import static com.venach.kauris.paie.application.PermissionsPaie.PREPARER;
import static com.venach.kauris.paie.application.PermissionsPaie.exiger;

/**
 * La paie : salariés, contrats, mois de paie, bulletins, paramètres. Un mois ouvert se recalcule à chaque lecture à
 * partir des contrats et des éléments du mois ; sa clôture fige et numérote les bulletins.
 */
public class PaieService {

    /** Un salarié avec son contrat en cours (nul s'il n'en a pas). */
    public record LigneSalarie(Salarie salarie, ContratTravail contrat) {
    }

    /** Un mois de paie avec ses totaux. */
    public record Mois(PeriodePaie periode, List<Bulletin> bulletins, Totaux totaux) {
    }

    /** Les totaux d'une liste de bulletins. */
    public record Totaux(int salaries, long brut, long cotisationsSalarie, long impot, long retenues, long net,
                         long chargesEmployeur, long coutEmployeur) {

        public static Totaux de(List<Bulletin> b) {
            return new Totaux(b.size(),
                    b.stream().mapToLong(x -> x.resultat().brut()).sum(),
                    b.stream().mapToLong(x -> x.resultat().cotisationsSalarie()).sum(),
                    b.stream().mapToLong(x -> x.resultat().impot()).sum(),
                    b.stream().mapToLong(x -> x.resultat().retenues()).sum(),
                    b.stream().mapToLong(Bulletin::net).sum(),
                    b.stream().mapToLong(x -> x.resultat().chargesEmployeur()).sum(),
                    b.stream().mapToLong(x -> x.resultat().coutEmployeur()).sum());
        }
    }

    /** Ce qui demande de l'attention : CDD qui finissent bientôt, salariés sans contrat, mois à clôturer. */
    public record Alertes(List<LigneSalarie> finsDeContrat, List<Salarie> sansContrat, List<PeriodePaie> aCloturer) {
        public int total() {
            return finsDeContrat.size() + sansContrat.size();
        }
    }

    /** Un calcul qui échoue pour un salarié (retenues trop fortes…) : le mois s'affiche, avec le problème. */
    public record Probleme(Salarie salarie, String message) {
    }

    /** Fin de CDD signalée ce nombre de jours à l'avance. */
    public static final int PREAVIS_JOURS = 30;

    private final PortsPaie.Salaries salaries;
    private final PortsPaie.Contrats contrats;
    private final PortsPaie.Periodes periodes;
    private final PortsPaie.VariablesDuMois variables;
    private final PortsPaie.Bulletins bulletins;
    private final PortsPaie.Corrections corrections;
    private final PortsPaie.Numeros numeros;
    private final PortsPaie.PaysEntreprise paysEntreprise;
    private final PortsPaie.Versements versements;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public PaieService(PortsPaie.Salaries salaries, PortsPaie.Contrats contrats, PortsPaie.Periodes periodes,
                       PortsPaie.VariablesDuMois variables, PortsPaie.Bulletins bulletins,
                       PortsPaie.Corrections corrections, PortsPaie.Numeros numeros,
                       PortsPaie.PaysEntreprise paysEntreprise, PortsPaie.Versements versements,
                       UniteDeTravail transaction, Clock horloge) {
        this.salaries = salaries;
        this.contrats = contrats;
        this.periodes = periodes;
        this.variables = variables;
        this.bulletins = bulletins;
        this.corrections = corrections;
        this.numeros = numeros;
        this.paysEntreprise = paysEntreprise;
        this.versements = versements;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    private Instant maintenant() {
        return Instant.now(horloge);
    }

    /** Le pays de paie de l'entreprise ; vide si la fiche de l'entreprise n'a pas de pays géré par la paie. */
    public Optional<PaysPaie> pays(UUID tenantId) {
        return paysEntreprise.pays(tenantId).flatMap(PaysPaie::de);
    }

    private PaysPaie paysExige(UUID tenantId) {
        return pays(tenantId).orElseThrow(() -> new ErreurMetier("La paie calcule pour le Togo et la Côte d'Ivoire : "
                + "indiquez l'un de ces pays sur la fiche de l'entreprise."));
    }

    // ================================================================== salariés

    public List<LigneSalarie> salaries(Acces a) {
        exiger(a, CONSULTER);
        LocalDate j = aujourdHui();
        Map<UUID, ContratTravail> enCours = contratsEnCours(a.tenantId(), j);
        return salaries.salaries(a.tenantId()).stream()
                .sorted(Comparator.comparing(Salarie::actif).reversed()
                        .thenComparing(Salarie::nomComplet, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new LigneSalarie(s, enCours.get(s.id()))).toList();
    }

    private Map<UUID, ContratTravail> contratsEnCours(UUID tenantId, LocalDate jour) {
        return contrats.contrats(tenantId).stream().filter(c -> c.enCours(jour))
                .collect(Collectors.toMap(ContratTravail::salarieId, Function.identity(),
                        (x, y) -> x.debut().isAfter(y.debut()) ? x : y));
    }

    public Salarie salarie(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return salaries.salarie(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
    }

    /** Crée ({@code id} nul) ou corrige la fiche d'un salarié. */
    public Salarie enregistrerSalarie(Acces a, UUID id, Salarie.Saisie saisie) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            Salarie avant = id == null ? null : salaries.salarie(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
            String matricule = avant != null ? avant.matricule()
                    : String.format("S-%04d", numeros.prochain(a.tenantId(), "SAL", 0));
            Salarie s = Salarie.de(avant == null ? UUID.randomUUID() : avant.id(), a.tenantId(), matricule, saisie,
                    avant == null ? maintenant() : avant.creeLe());
            if (!s.actif() && contratsEnCours(a.tenantId(), aujourdHui()).containsKey(s.id())) {
                throw new ErreurMetier(s.nomComplet() + " a un contrat en cours : indiquez sa sortie avant "
                        + "d'archiver sa fiche.");
            }
            if (!s.numeroSocial().isEmpty() && salaries.salaries(a.tenantId()).stream().anyMatch(x ->
                    !x.id().equals(s.id()) && x.numeroSocial().equalsIgnoreCase(s.numeroSocial()))) {
                throw new ErreurMetier("Un autre salarié a déjà le numéro de sécurité sociale " + s.numeroSocial()
                        + ".");
            }
            salaries.sauvegarder(s);
            return s;
        });
    }

    // ================================================================== contrats

    public List<ContratTravail> contratsDuSalarie(Acces a, UUID salarieId) {
        salarie(a, salarieId);
        return contrats.contrats(a.tenantId()).stream().filter(c -> c.salarieId().equals(salarieId))
                .sorted(Comparator.comparing(ContratTravail::debut).reversed()).toList();
    }

    public ContratTravail contrat(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return contrats.contrat(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
    }

    public ContratTravail creerContrat(Acces a, UUID salarieId, ContratTravail.Saisie saisie) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            Salarie s = salaries.salarie(a.tenantId(), salarieId)
                    .orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
            if (!s.actif()) {
                throw new ErreurMetier(s.nomComplet() + " est archivé : réactivez sa fiche d'abord.");
            }
            for (ContratTravail c : contrats.contrats(a.tenantId())) {
                if (c.salarieId().equals(salarieId) && (c.sortie() == null || !c.sortie().isBefore(saisie.debut()))) {
                    throw new ErreurMetier(s.nomComplet() + " a déjà un contrat (" + c.numero() + ") à cette date : "
                            + "indiquez sa sortie, ou corrigez-le.");
                }
            }
            verifierMoisOuvert(a.tenantId(), saisie.debut());
            int annee = saisie.debut().getYear();
            ContratTravail c = ContratTravail.de(UUID.randomUUID(), a.tenantId(), salarieId,
                    String.format("CTR-%d-%04d", annee, numeros.prochain(a.tenantId(), "CTR", annee)), saisie,
                    maintenant());
            contrats.sauvegarder(c);
            return c;
        });
    }

    /** Change les conditions du contrat. Les bulletins déjà clos ne bougent pas ; les mois ouverts suivent. */
    public ContratTravail corrigerContrat(Acces a, UUID id, ContratTravail.Saisie saisie) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            ContratTravail avant = contrats.contrat(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            ContratTravail c = avant.corriger(saisie);
            if (!c.debut().equals(avant.debut())) {
                verifierMoisOuvert(a.tenantId(), c.debut());
                verifierMoisOuvert(a.tenantId(), avant.debut());
            }
            contrats.sauvegarder(c);
            return c;
        });
    }

    /** Sortie du salarié : son dernier mois est payé au prorata des jours travaillés. */
    public ContratTravail terminerContrat(Acces a, UUID id, LocalDate dernierJour, String motif) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            ContratTravail avant = contrats.contrat(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            if (avant.sortie() != null) {
                throw new ErreurMetier("Ce contrat est déjà terminé.");
            }
            ContratTravail c = avant.terminer(dernierJour, motif);
            verifierMoisOuvert(a.tenantId(), dernierJour);
            contrats.sauvegarder(c);
            return c;
        });
    }

    public ContratTravail rouvrirContrat(Acces a, UUID id) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            ContratTravail avant = contrats.contrat(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Contrat introuvable."));
            if (avant.sortie() == null) {
                throw new ErreurMetier("Ce contrat est en cours.");
            }
            verifierMoisOuvert(a.tenantId(), avant.sortie());
            boolean autre = contrats.contrats(a.tenantId()).stream().anyMatch(c -> !c.id().equals(id)
                    && c.salarieId().equals(avant.salarieId()) && c.debut().isAfter(avant.debut()));
            if (autre) {
                throw new ErreurMetier("Ce salarié a un contrat plus récent : celui-ci ne se rouvre pas.");
            }
            ContratTravail c = avant.rouvrir();
            contrats.sauvegarder(c);
            return c;
        });
    }

    /** Un changement qui toucherait un mois clos est refusé : il faut d'abord rouvrir ce mois. */
    private void verifierMoisOuvert(UUID tenantId, LocalDate jour) {
        periodes.parMois(tenantId, YearMonth.from(jour)).filter(PeriodePaie::close).ifPresent(p -> {
            throw new ErreurMetier("La paie de " + p.libelle() + " est close : rouvrez ce mois avant ce changement.");
        });
    }

    // ================================================================== mois de paie

    public List<PeriodePaie> periodes(Acces a) {
        exiger(a, CONSULTER);
        return periodes.periodes(a.tenantId()).stream().sorted(Comparator.comparing(PeriodePaie::mois).reversed())
                .toList();
    }

    public PeriodePaie periode(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return periodes.periode(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
    }

    /** Le mois à préparer quand on n'en ouvre pas un autre : le mois en cours. */
    public YearMonth moisCourant() {
        return YearMonth.from(aujourdHui());
    }

    public PeriodePaie ouvrirMois(Acces a, YearMonth mois) {
        exiger(a, PREPARER);
        PaysPaie pays = paysExige(a.tenantId());
        if (mois == null) {
            throw new ErreurMetier("Choisissez le mois.");
        }
        if (mois.isAfter(moisCourant().plusMonths(1))) {
            throw new ErreurMetier("On prépare au plus la paie du mois prochain.");
        }
        return transaction.executer(() -> {
            if (periodes.parMois(a.tenantId(), mois).isPresent()) {
                throw new ErreurMetier("La paie de " + PeriodePaie.libelle(mois) + " existe déjà.");
            }
            PeriodePaie p = PeriodePaie.ouvrir(a.tenantId(), mois, pays, maintenant());
            periodes.sauvegarder(p);
            return p;
        });
    }

    /** Le mois et ses bulletins : figés s'il est clos, recalculés sinon (les salariés en erreur sont écartés). */
    public Mois mois(Acces a, UUID periodeId) {
        PeriodePaie p = periode(a, periodeId);
        List<Bulletin> b = p.close() ? figes(p) : calculer(p).bulletins();
        return new Mois(p, b, Totaux.de(b));
    }

    /** Les problèmes de calcul d'un mois ouvert (retenues trop fortes…). */
    public List<Probleme> problemes(Acces a, UUID periodeId) {
        PeriodePaie p = periode(a, periodeId);
        return p.close() ? List.of() : calculer(p).problemes();
    }

    private List<Bulletin> figes(PeriodePaie p) {
        return bulletins.bulletins(p.tenantId(), p.id()).stream()
                .sorted(Comparator.comparing(Bulletin::numero)).toList();
    }

    public Bulletin bulletin(Acces a, UUID bulletinId) {
        exiger(a, CONSULTER);
        return bulletins.bulletin(a.tenantId(), bulletinId)
                .orElseThrow(() -> new ElementIntrouvable("Bulletin introuvable."));
    }

    /** Le bulletin d'un salarié pour un mois : figé si le mois est clos, sinon le brouillon du moment. */
    public Bulletin bulletinDe(Acces a, UUID periodeId, UUID salarieId) {
        Mois m = mois(a, periodeId);
        return m.bulletins().stream().filter(b -> b.salarieId().equals(salarieId)).findFirst().orElseThrow(() ->
                new ElementIntrouvable("Pas de bulletin ce mois-ci pour ce salarié."));
    }

    public List<Bulletin> bulletinsDuSalarie(Acces a, UUID salarieId) {
        salarie(a, salarieId);
        Map<UUID, YearMonth> moisDe = periodes.periodes(a.tenantId()).stream()
                .collect(Collectors.toMap(PeriodePaie::id, PeriodePaie::mois));
        return bulletins.bulletinsDuSalarie(a.tenantId(), salarieId).stream()
                .sorted(Comparator.comparing((Bulletin b) -> moisDe.getOrDefault(b.periodeId(), YearMonth.of(1970, 1)))
                        .reversed()).toList();
    }

    public Variables variables(Acces a, UUID periodeId, UUID salarieId) {
        PeriodePaie p = periode(a, periodeId);
        salarie(a, salarieId);
        return variables.variables(a.tenantId(), p.id()).stream().filter(v -> v.salarieId().equals(salarieId))
                .findFirst().orElse(Variables.aucune(a.tenantId(), p.id(), salarieId));
    }

    /** Les éléments du mois d'un salarié : absences, primes, avances. Remplace ce qui était saisi. */
    public Variables enregistrerVariables(Acces a, UUID periodeId, UUID salarieId, int joursAbsence,
                                          List<Element> elements) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            if (p.close()) {
                throw new ErreurMetier("La paie de " + p.libelle() + " est close : rouvrez-la pour la modifier.");
            }
            salaries.salarie(a.tenantId(), salarieId).orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
            Variables v = new Variables(a.tenantId(), p.id(), salarieId, joursAbsence, elements);
            // On calcule tout de suite : une retenue trop forte est refusée à la saisie, pas à la clôture.
            Optional<ContratTravail> c = contratDuMois(a.tenantId(), salarieId, p.mois());
            Salarie s = salaries.salarie(a.tenantId(), salarieId).orElseThrow();
            c.ifPresent(x -> calculerUn(p, s, x, v, parametres(a.tenantId(), p.mois())));
            variables.sauvegarder(v);
            return v;
        });
    }

    /** Le contrat qui paie ce salarié ce mois-ci : le plus récent de ceux qui touchent le mois. */
    private Optional<ContratTravail> contratDuMois(UUID tenantId, UUID salarieId, YearMonth mois) {
        return contrats.contrats(tenantId).stream()
                .filter(c -> c.salarieId().equals(salarieId) && c.joursDans(mois) > 0)
                .max(Comparator.comparing(ContratTravail::debut));
    }

    private record Calcul(List<Bulletin> bulletins, List<Probleme> problemes) {
    }

    private Calcul calculer(PeriodePaie p) {
        Parametres parametres = parametres(p.tenantId(), p.mois());
        Map<UUID, Variables> v = variables.variables(p.tenantId(), p.id()).stream()
                .collect(Collectors.toMap(Variables::salarieId, Function.identity()));
        Map<UUID, Salarie> parId = salaries.salaries(p.tenantId()).stream()
                .collect(Collectors.toMap(Salarie::id, Function.identity()));
        Map<UUID, ContratTravail> duMois = contrats.contrats(p.tenantId()).stream()
                .filter(c -> c.joursDans(p.mois()) > 0 && parId.containsKey(c.salarieId()))
                .collect(Collectors.toMap(ContratTravail::salarieId, Function.identity(),
                        (x, y) -> x.debut().isAfter(y.debut()) ? x : y));
        List<Bulletin> ok = new ArrayList<>();
        List<Probleme> ko = new ArrayList<>();
        duMois.values().stream().map(c -> parId.get(c.salarieId()))
                .sorted(Comparator.comparing(Salarie::nomComplet, String.CASE_INSENSITIVE_ORDER))
                .forEach(s -> {
                    try {
                        ok.add(calculerUn(p, s, duMois.get(s.id()), v.getOrDefault(s.id(),
                                Variables.aucune(p.tenantId(), p.id(), s.id())), parametres));
                    } catch (ErreurMetier e) {
                        ko.add(new Probleme(s, e.getMessage()));
                    }
                });
        return new Calcul(ok, ko);
    }

    private static Bulletin calculerUn(PeriodePaie p, Salarie s, ContratTravail c, Variables v, Parametres parametres) {
        int jours = Math.max(0, c.joursDans(p.mois()) - v.joursAbsence());
        List<Element> elements = Stream.concat(c.elements().stream(), v.elements().stream()).toList();
        CalculPaie.Resultat r = CalculPaie.calculer(new CalculPaie.Entree(p.pays(), c.salaireBase(), jours, elements,
                s.foyer(), s.expatrie()), parametres);
        return Bulletin.brouillon(p.tenantId(), p.id(), s, c, jours, r);
    }

    /** Clôture : les bulletins sont figés et numérotés ; ils ne bougent plus, même si un contrat change. */
    public PeriodePaie clore(Acces a, UUID periodeId) {
        exiger(a, CLOTURER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            if (p.close()) {
                throw new ErreurMetier("Ce mois est déjà clos.");
            }
            Calcul c = calculer(p);
            if (!c.problemes().isEmpty()) {
                Probleme x = c.problemes().get(0);
                throw new ErreurMetier("Bulletin de " + x.salarie().nomComplet() + " : " + x.message());
            }
            if (c.bulletins().isEmpty()) {
                throw new ErreurMetier("Aucun salarié sous contrat en " + p.libelle() + " : rien à clôturer.");
            }
            int annee = p.mois().getYear();
            for (Bulletin b : c.bulletins()) {
                String numero = String.format("BP-%d%02d-%04d", annee, p.mois().getMonthValue(),
                        numeros.prochain(a.tenantId(), "BP", annee));
                bulletins.ajouter(b.figer(UUID.randomUUID(), numero));
            }
            PeriodePaie close = p.clore(a.nomMembre(), maintenant());
            periodes.sauvegarder(close);
            return close;
        });
    }

    /** Réouverture (avant paiement) : les bulletins figés sont effacés et le mois se recalcule. */
    public PeriodePaie rouvrir(Acces a, UUID periodeId) {
        exiger(a, CLOTURER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            PeriodePaie ouverte = p.rouvrir();
            bulletins.supprimer(a.tenantId(), p.id());
            periodes.sauvegarder(ouverte);
            return ouverte;
        });
    }

    /** Les salaires du mois ont été versés (virement remis à la banque, espèces remises). */
    public PeriodePaie marquerPaye(Acces a, UUID periodeId, LocalDate jour) {
        exiger(a, CLOTURER);
        LocalDate j = jour == null ? aujourdHui() : jour;
        if (j.isAfter(aujourdHui())) {
            throw new ErreurMetier("La date de paiement ne peut pas être dans le futur.");
        }
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            PeriodePaie payee = p.payer(j);
            periodes.sauvegarder(payee);
            return payee;
        });
    }

    /** Vrai si les salaires peuvent partir d'ici (Kauris Cloud) ; sinon l'écran propose le fichier de virement. */
    public boolean versementDisponible(Acces a) {
        exiger(a, CONSULTER);
        return versements.disponible(a.tenantId());
    }

    /** Lance le versement des salaires payés par virement ou Mobile Money d'un mois clos. */
    public String verser(Acces a, UUID periodeId) {
        exiger(a, CLOTURER);
        PeriodePaie p = periode(a, periodeId);
        if (!p.close()) {
            throw new ErreurMetier("Clôturez le mois avant de verser les salaires.");
        }
        if (p.payeLe() != null) {
            throw new ErreurMetier("Les salaires de ce mois sont déjà marqués comme payés.");
        }
        List<Bulletin> aVerser = aVirer(figes(p));
        if (aVerser.isEmpty()) {
            throw new ErreurMetier("Aucun salaire à verser par virement ou Mobile Money ce mois-ci.");
        }
        return versements.verser(a, p, aVerser);
    }

    /** Les bulletins payés par virement ou Mobile Money, avec un net positif. */
    public static List<Bulletin> aVirer(List<Bulletin> b) {
        return b.stream().filter(x -> x.modePaiement().parVirement() && x.net() > 0).toList();
    }

    // ================================================================== paramètres

    /** Les valeurs des paramètres pour ce mois, compte tenu des corrections de l'entreprise. */
    public Parametres parametres(UUID tenantId, YearMonth mois) {
        return Parametres.au(mois.atEndOfMonth(), corrections.corrections(tenantId));
    }

    public List<Parametres.Correction> corrections(Acces a) {
        exiger(a, CONSULTER);
        return corrections.corrections(a.tenantId()).stream()
                .sorted(Comparator.comparing((Parametres.Correction c) -> c.parametre().ordinal())
                        .thenComparing(Parametres.Correction::depuis)).toList();
    }

    /**
     * Corrige un paramètre à partir d'un mois. Un mois déjà clos à cette date ou après n'est pas touché : il faut
     * le rouvrir pour qu'il suive la correction.
     */
    public void corriger(Acces a, Parametres.Correction c) {
        exiger(a, PARAMETRER);
        if (!c.tenantId().equals(a.tenantId())) {
            throw new IllegalArgumentException("Organisation inattendue");
        }
        if (c.depuis().getDayOfMonth() != 1) {
            throw new ErreurMetier("Une correction s'applique à partir du premier jour d'un mois.");
        }
        transaction.executer(() -> {
            if (corrections.corrections(a.tenantId()).stream().anyMatch(x -> x.parametre() == c.parametre()
                    && x.depuis().equals(c.depuis()))) {
                corrections.retirer(a.tenantId(), c.parametre(), c.depuis());
            }
            corrections.ajouter(c);
        });
    }

    public void retirerCorrection(Acces a, Parametre parametre, LocalDate depuis) {
        exiger(a, PARAMETRER);
        transaction.executer(() -> corrections.retirer(a.tenantId(), parametre, depuis));
    }

    // ================================================================== alertes

    public Alertes alertes(Acces a) {
        exiger(a, CONSULTER);
        LocalDate j = aujourdHui();
        List<LigneSalarie> liste = salaries(a);
        List<LigneSalarie> fins = liste.stream().filter(l -> l.contrat() != null && l.contrat().sortie() == null
                        && l.contrat().finPrevue() != null && !l.contrat().finPrevue().isAfter(j.plusDays(PREAVIS_JOURS)))
                .sorted(Comparator.comparing(l -> l.contrat().finPrevue())).toList();
        List<Salarie> sans = liste.stream().filter(l -> l.salarie().actif() && l.contrat() == null)
                .map(LigneSalarie::salarie).toList();
        List<PeriodePaie> aClore = periodes.periodes(a.tenantId()).stream()
                .filter(p -> !p.close() && p.mois().isBefore(YearMonth.from(j)))
                .sorted(Comparator.comparing(PeriodePaie::mois)).toList();
        return new Alertes(fins, sans, aClore);
    }
}
