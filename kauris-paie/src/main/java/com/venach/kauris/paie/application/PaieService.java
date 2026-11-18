package com.venach.kauris.paie.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.paie.domain.Avance;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.ReglesPaie;
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
 * La paie : règles de paie datées, salariés, contrats, avances, mois de paie, bulletins. Un mois ouvert se recalcule
 * à chaque lecture à partir des règles en vigueur, des contrats et des éléments du mois ; sa clôture fige et
 * numérote les bulletins.
 */
public class PaieService {

    /** Un salarié avec son contrat en cours (nul s'il n'en a pas). */
    public record LigneSalarie(Salarie salarie, ContratTravail contrat) {
    }

    /** Un mois de paie avec ses totaux et les règles appliquées (nulles si aucune). */
    public record Mois(PeriodePaie periode, List<Bulletin> bulletins, Totaux totaux, ReglesPaie regles) {
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
    public record Alertes(List<LigneSalarie> finsDeContrat, List<Salarie> sansContrat, List<PeriodePaie> aCloturer,
                          boolean sansRegles) {
        public int total() {
            return finsDeContrat.size() + sansContrat.size();
        }
    }

    /** Un calcul qui échoue pour un salarié (retenues trop fortes…) : le mois s'affiche, avec le problème. */
    public record Probleme(Salarie salarie, String message) {
    }

    /** Une avance avec ce qui en a déjà été remboursé sur les bulletins clos. */
    public record SuiviAvance(Avance avance, long rembourse) {
        public long reste() {
            return avance.annulee() ? 0 : Math.max(0, avance.montant() - rembourse);
        }
    }

    /** Fin de CDD signalée ce nombre de jours à l'avance. */
    public static final int PREAVIS_JOURS = 30;

    private final PortsPaie.Salaries salaries;
    private final PortsPaie.Contrats contrats;
    private final PortsPaie.Periodes periodes;
    private final PortsPaie.VariablesDuMois variables;
    private final PortsPaie.Bulletins bulletins;
    private final PortsPaie.Regles regles;
    private final PortsPaie.Avances avances;
    private final PortsPaie.Numeros numeros;
    private final PortsPaie.PaysEntreprise paysEntreprise;
    private final PortsPaie.Versements versements;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public PaieService(PortsPaie.Salaries salaries, PortsPaie.Contrats contrats, PortsPaie.Periodes periodes,
                       PortsPaie.VariablesDuMois variables, PortsPaie.Bulletins bulletins, PortsPaie.Regles regles,
                       PortsPaie.Avances avances, PortsPaie.Numeros numeros, PortsPaie.PaysEntreprise paysEntreprise,
                       PortsPaie.Versements versements, UniteDeTravail transaction, Clock horloge) {
        this.salaries = salaries;
        this.contrats = contrats;
        this.periodes = periodes;
        this.variables = variables;
        this.bulletins = bulletins;
        this.regles = regles;
        this.avances = avances;
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

    /** Le pays de l'entreprise (fiche de l'entreprise). */
    public Optional<Pays> pays(UUID tenantId) {
        return paysEntreprise.pays(tenantId);
    }

    private Pays paysExige(UUID tenantId) {
        return pays(tenantId).orElseThrow(() -> new ErreurMetier("Indiquez le pays sur la fiche de l'entreprise."));
    }

    // ================================================================== règles de paie

    /** Toutes les versions des règles, du pays de l'entreprise d'abord, les plus récentes en premier. */
    public List<ReglesPaie> regles(Acces a) {
        exiger(a, CONSULTER);
        Pays p = pays(a.tenantId()).orElse(null);
        return regles.toutes(a.tenantId()).stream()
                .sorted(Comparator.comparing((ReglesPaie r) -> r.pays() != p).thenComparing(r -> r.pays().nom())
                        .thenComparing(ReglesPaie::depuis, Comparator.reverseOrder())).toList();
    }

    public ReglesPaie reglesParId(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return regles.regles(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Règles introuvables."));
    }

    /** Les règles en vigueur pour ce pays à la fin de ce mois : la version la plus récente déjà commencée. */
    public Optional<ReglesPaie> reglesDuMois(UUID tenantId, Pays pays, YearMonth mois) {
        LocalDate fin = mois.atEndOfMonth();
        return regles.toutes(tenantId).stream().filter(r -> r.pays() == pays && !r.depuis().isAfter(fin))
                .max(Comparator.comparing(ReglesPaie::depuis));
    }

    /** Le modèle recommandé dont viennent ces règles, pour comparer et afficher les recommandations. */
    public Optional<ModelesRecommandes.Modele> recommandation(ReglesPaie r) {
        return ModelesRecommandes.parCode(r.modele());
    }

    /**
     * Active les règles recommandées pour un pays (ou un modèle vide à compléter) : une copie que l'entreprise
     * corrige ensuite à sa guise.
     */
    public ReglesPaie activerModele(Acces a, String code, Pays pays, LocalDate depuis) {
        exiger(a, PARAMETRER);
        ModelesRecommandes.Modele m = ModelesRecommandes.VIDE.equals(code)
                ? ModelesRecommandes.vide(pays == null ? paysExige(a.tenantId()) : pays,
                depuis == null ? aujourdHui() : depuis)
                : ModelesRecommandes.parCode(code).orElseThrow(() -> new ErreurMetier("Modèle de règles inconnu."));
        LocalDate effet = (depuis == null ? m.depuis() : depuis).withDayOfMonth(1);
        ReglesPaie r = new ReglesPaie(UUID.randomUUID(), a.tenantId(), m.pays(), effet, m.nom(), m.code(),
                m.contenu(), m.notes(), maintenant(), a.nomMembre());
        return transaction.executer(() -> {
            verifierDateLibre(a.tenantId(), r.pays(), r.depuis(), null);
            regles.sauvegarder(r);
            return r;
        });
    }

    /** Une nouvelle version, copie d'une autre, à partir d'un mois (loi de finances, nouveau taux). */
    public ReglesPaie nouvelleVersion(Acces a, UUID sourceId, LocalDate depuis) {
        exiger(a, PARAMETRER);
        ReglesPaie source = reglesParId(a, sourceId);
        if (depuis == null) {
            throw new ErreurMetier("Indiquez le mois d'effet de la nouvelle version.");
        }
        ReglesPaie r = new ReglesPaie(UUID.randomUUID(), a.tenantId(), source.pays(), depuis.withDayOfMonth(1),
                source.nom(), source.modele(), source.contenu(), source.notes(), maintenant(), a.nomMembre());
        return transaction.executer(() -> {
            verifierDateLibre(a.tenantId(), r.pays(), r.depuis(), null);
            regles.sauvegarder(r);
            return r;
        });
    }

    /** Enregistre les règles corrigées. Les mois déjà clos ne bougent pas ; les mois ouverts suivent. */
    public ReglesPaie enregistrerRegles(Acces a, UUID id, String nom, LocalDate depuis, ReglesPaie.Contenu contenu,
                                        String notes) {
        exiger(a, PARAMETRER);
        return transaction.executer(() -> {
            ReglesPaie avant = regles.regles(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Règles introuvables."));
            ReglesPaie r = avant.avec(depuis == null ? avant.depuis() : depuis.withDayOfMonth(1), nom, contenu, notes,
                    a.nomMembre(), maintenant());
            verifierDateLibre(a.tenantId(), r.pays(), r.depuis(), r.id());
            regles.sauvegarder(r);
            return r;
        });
    }

    public void supprimerRegles(Acces a, UUID id) {
        exiger(a, PARAMETRER);
        transaction.executer(() -> {
            ReglesPaie r = regles.regles(a.tenantId(), id)
                    .orElseThrow(() -> new ElementIntrouvable("Règles introuvables."));
            boolean ouvertSansAutre = periodes.periodes(a.tenantId()).stream().filter(p -> !p.close()
                    && p.pays() == r.pays()).anyMatch(p -> reglesDuMois(a.tenantId(), r.pays(), p.mois())
                    .filter(x -> x.id().equals(r.id())).isPresent()
                    && regles.toutes(a.tenantId()).stream().noneMatch(x -> !x.id().equals(r.id())
                    && x.pays() == r.pays() && !x.depuis().isAfter(p.mois().atEndOfMonth())));
            if (ouvertSansAutre) {
                throw new ErreurMetier("Un mois en préparation n'aurait plus de règles : clôturez-le ou créez "
                        + "d'abord une autre version.");
            }
            regles.supprimerRegles(a.tenantId(), id);
        });
    }

    private void verifierDateLibre(UUID tenantId, Pays pays, LocalDate depuis, UUID sauf) {
        if (regles.toutes(tenantId).stream().anyMatch(x -> x.pays() == pays && x.depuis().equals(depuis)
                && !x.id().equals(sauf))) {
            throw new ErreurMetier("Des règles pour " + pays.nom() + " commencent déjà en "
                    + PeriodePaie.libelle(YearMonth.from(depuis)) + " : corrigez-les plutôt.");
        }
    }

    // ================================================================== salariés

    public List<LigneSalarie> salaries(Acces a) {
        exiger(a, CONSULTER);
        Map<UUID, ContratTravail> enCours = contratsEnCours(a.tenantId(), aujourdHui());
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
            if (saisie.debut() == null) {
                throw new ErreurMetier("Indiquez la date d'embauche.");
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

    // ================================================================== avances et prêts

    public List<SuiviAvance> avances(Acces a, UUID salarieId) {
        salarie(a, salarieId);
        return avances.avances(a.tenantId()).stream().filter(x -> x.salarieId().equals(salarieId))
                .sorted(Comparator.comparing(Avance::accordeeLe).reversed())
                .map(x -> new SuiviAvance(x, rembourseAvant(a.tenantId(), x, null))).toList();
    }

    public Avance accorderAvance(Acces a, UUID salarieId, Avance.Type type, String libelle, long montant,
                                 long mensualite, YearMonth premierMois, String note) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            Salarie s = salaries.salarie(a.tenantId(), salarieId)
                    .orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
            if (premierMois != null) {
                periodes.parMois(a.tenantId(), premierMois).filter(PeriodePaie::close).ifPresent(p -> {
                    throw new ErreurMetier("La paie de " + p.libelle() + " est close : la première retenue doit "
                            + "tomber sur un mois ouvert.");
                });
            }
            Avance x = new Avance(UUID.randomUUID(), a.tenantId(), s.id(), type, libelle, montant, mensualite,
                    premierMois, aujourdHui(), false, note, maintenant(), a.nomMembre());
            avances.sauvegarder(x);
            return x;
        });
    }

    /** Arrête les retenues d'une avance (soldée autrement, accordée par erreur). Ce qui a été retenu reste. */
    public Avance annulerAvance(Acces a, UUID avanceId) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            Avance x = avances.avance(a.tenantId(), avanceId)
                    .orElseThrow(() -> new ElementIntrouvable("Avance introuvable."));
            Avance annulee = x.annuler();
            avances.sauvegarder(annulee);
            return annulee;
        });
    }

    /** Ce qui a été retenu pour cette avance sur les bulletins clos (des mois avant {@code avant}, si donné). */
    private long rembourseAvant(UUID tenantId, Avance x, YearMonth avant) {
        Map<UUID, YearMonth> moisDe = periodes.periodes(tenantId).stream()
                .collect(Collectors.toMap(PeriodePaie::id, PeriodePaie::mois));
        return bulletins.bulletinsDuSalarie(tenantId, x.salarieId()).stream()
                .filter(b -> avant == null || moisDe.getOrDefault(b.periodeId(), avant).isBefore(avant))
                .mapToLong(b -> b.resultat().rembourse(x.code())).sum();
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

    public YearMonth moisCourant() {
        return YearMonth.from(aujourdHui());
    }

    public PeriodePaie ouvrirMois(Acces a, YearMonth mois) {
        exiger(a, PREPARER);
        Pays pays = paysExige(a.tenantId());
        if (mois == null) {
            throw new ErreurMetier("Choisissez le mois.");
        }
        if (mois.isAfter(moisCourant().plusMonths(1))) {
            throw new ErreurMetier("On prépare au plus la paie du mois prochain.");
        }
        if (reglesDuMois(a.tenantId(), pays, mois).isEmpty()) {
            throw new ErreurMetier("Aucune règle de paie pour " + pays.nom() + " en " + PeriodePaie.libelle(mois)
                    + " : activez les règles recommandées ou saisissez les vôtres (Règles de paie).");
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
        ReglesPaie r = reglesDuMois(a.tenantId(), p.pays(), p.mois()).orElse(null);
        List<Bulletin> b = p.close() ? figes(p) : calculer(p).bulletins();
        return new Mois(p, b, Totaux.de(b), r);
    }

    /** Les problèmes de calcul d'un mois ouvert (retenues trop fortes, règles manquantes…). */
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

    /** Les éléments du mois d'un salarié : absences, heures, primes, retenues. Remplace ce qui était saisi. */
    public Variables enregistrerVariables(Acces a, UUID periodeId, UUID salarieId, int joursAbsence,
                                          List<Variables.Heures> heures, List<Element> elements) {
        exiger(a, PREPARER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            if (p.close()) {
                throw new ErreurMetier("La paie de " + p.libelle() + " est close : rouvrez-la pour la modifier.");
            }
            Salarie s = salaries.salarie(a.tenantId(), salarieId)
                    .orElseThrow(() -> new ElementIntrouvable("Salarié introuvable."));
            Variables v = new Variables(a.tenantId(), p.id(), salarieId, joursAbsence, heures, elements);
            // Calcul immédiat : une retenue trop forte est refusée à la saisie, pas à la clôture.
            ReglesPaie r = reglesDuMois(a.tenantId(), p.pays(), p.mois()).orElse(null);
            Optional<ContratTravail> c = contratDuMois(a.tenantId(), salarieId, p.mois());
            if (r != null && c.isPresent()) {
                calculerUn(p, r, s, c.get(), v, avancesDues(a.tenantId(), s.id(), p.mois()));
            }
            variables.sauvegarder(v);
            return v;
        });
    }

    private Optional<ContratTravail> contratDuMois(UUID tenantId, UUID salarieId, YearMonth mois) {
        return contrats.contrats(tenantId).stream()
                .filter(c -> c.salarieId().equals(salarieId) && c.joursDans(mois) > 0)
                .max(Comparator.comparing(ContratTravail::debut));
    }

    /** Les échéances d'avances et de prêts de ce salarié pour ce mois, compte tenu des mois déjà clos. */
    private List<CalculPaie.AvanceDue> avancesDues(UUID tenantId, UUID salarieId, YearMonth mois) {
        List<CalculPaie.AvanceDue> dues = new ArrayList<>();
        avances.avances(tenantId).stream().filter(x -> x.salarieId().equals(salarieId))
                .sorted(Comparator.comparing(Avance::accordeeLe).thenComparing(Avance::creeLe))
                .forEach(x -> {
                    long voulu = x.aRetenir(mois, rembourseAvant(tenantId, x, mois));
                    if (voulu > 0) {
                        dues.add(new CalculPaie.AvanceDue(x.code(), x.type().libelle() + " " + x.libelle(), voulu));
                    }
                });
        return dues;
    }

    private record Calcul(List<Bulletin> bulletins, List<Probleme> problemes) {
    }

    private Calcul calculer(PeriodePaie p) {
        ReglesPaie r = reglesDuMois(p.tenantId(), p.pays(), p.mois()).orElse(null);
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
                    if (r == null) {
                        ko.add(new Probleme(s, "Aucune règle de paie pour " + p.pays().nom() + " ce mois-ci."));
                        return;
                    }
                    try {
                        ok.add(calculerUn(p, r, s, duMois.get(s.id()), v.getOrDefault(s.id(),
                                Variables.aucune(p.tenantId(), p.id(), s.id())), avancesDues(p.tenantId(), s.id(),
                                p.mois())));
                    } catch (ErreurMetier e) {
                        ko.add(new Probleme(s, e.getMessage()));
                    }
                });
        return new Calcul(ok, ko);
    }

    private static Bulletin calculerUn(PeriodePaie p, ReglesPaie r, Salarie s, ContratTravail c, Variables v,
                                       List<CalculPaie.AvanceDue> avances) {
        int jours = Math.max(0, c.joursDans(p.mois()) - v.joursAbsence());
        List<Element> elements = Stream.concat(c.elements().stream(), v.elements().stream()).toList();
        CalculPaie.Resultat res = CalculPaie.calculer(new CalculPaie.Entree(r.contenu(), c.salaireBase(), jours,
                v.heures(), elements, s.foyer(), s.expatrie(), s.regime(), avances));
        return Bulletin.brouillon(p.tenantId(), p.id(), s, c, jours, r, res);
    }

    /** Clôture : les bulletins sont figés et numérotés ; ils ne bougent plus, même si un contrat ou une règle change. */
    public PeriodePaie clore(Acces a, UUID periodeId) {
        exiger(a, CLOTURER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            if (p.close()) {
                throw new ErreurMetier("Ce mois est déjà clos.");
            }
            boolean anterieurOuvert = periodes.periodes(a.tenantId()).stream()
                    .anyMatch(x -> !x.close() && x.mois().isBefore(p.mois()));
            if (anterieurOuvert) {
                throw new ErreurMetier("Clôturez d'abord les mois précédents : les avances et prêts se suivent mois "
                        + "après mois.");
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

    /** Réouverture (avant paiement, et si aucun mois suivant n'est clos) : les bulletins sont recalculés. */
    public PeriodePaie rouvrir(Acces a, UUID periodeId) {
        exiger(a, CLOTURER);
        return transaction.executer(() -> {
            PeriodePaie p = periodes.verrouiller(a.tenantId(), periodeId)
                    .orElseThrow(() -> new ElementIntrouvable("Mois de paie introuvable."));
            PeriodePaie ouverte = p.rouvrir();
            boolean suivantClos = periodes.periodes(a.tenantId()).stream()
                    .anyMatch(x -> x.close() && x.mois().isAfter(p.mois()));
            if (suivantClos) {
                throw new ErreurMetier("Un mois suivant est clos : rouvrez d'abord le plus récent.");
            }
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
        boolean sansRegles = pays(a.tenantId()).map(p -> reglesDuMois(a.tenantId(), p, moisCourant()).isEmpty())
                .orElse(true);
        return new Alertes(fins, sans, aClore, sansRegles);
    }
}
