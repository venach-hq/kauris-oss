package com.venach.kauris.ventes.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.DocumentVente.Contenu;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import com.venach.kauris.ventes.domain.StatutDocument;
import com.venach.kauris.ventes.domain.TypeDocument;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.venach.kauris.ventes.application.PermissionsVentes.CATALOGUE;
import static com.venach.kauris.ventes.application.PermissionsVentes.CONSULTER;
import static com.venach.kauris.ventes.application.PermissionsVentes.EMETTRE;
import static com.venach.kauris.ventes.application.PermissionsVentes.ENCAISSER;
import static com.venach.kauris.ventes.application.PermissionsVentes.REGLAGES;
import static com.venach.kauris.ventes.application.PermissionsVentes.SAISIR;
import static com.venach.kauris.ventes.application.PermissionsVentes.exiger;

/**
 * Les ventes : clients, catalogue, devis, factures, avoirs et règlements. Chaque opération vérifie la permission
 * de la personne et ne touche qu'aux informations de son organisation.
 */
public class VentesService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final Ports.Clients clients;
    private final Ports.Articles articles;
    private final Ports.Documents documents;
    private final Ports.Reglements reglements;
    private final Ports.Reglages reglages;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public VentesService(Ports.Clients clients, Ports.Articles articles, Ports.Documents documents,
                         Ports.Reglements reglements, Ports.Reglages reglages, UniteDeTravail transaction,
                         Clock horloge) {
        this.clients = clients;
        this.articles = articles;
        this.documents = documents;
        this.reglements = reglements;
        this.reglages = reglages;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    // ================================================================== réglages

    public ReglagesVentes reglages(Acces a) {
        exiger(a, CONSULTER);
        return reglages.reglages(a.tenantId()).orElseGet(() -> ReglagesVentes.parDefaut(a.tenantId()));
    }

    public void definirReglages(Acces a, boolean assujettiTva, BigDecimal tauxTva, String mentionTva,
                                int delaiPaiement, int validiteDevis, String conditions) {
        exiger(a, REGLAGES);
        ReglagesVentes r = new ReglagesVentes(a.tenantId(), assujettiTva, tauxTva, mentionTva, delaiPaiement,
                validiteDevis, conditions);
        if (!assujettiTva && r.mentionTva().isBlank()) {
            throw new ErreurMetier("Indiquez la mention imprimée sur les factures sans TVA (par exemple « TVA non "
                    + "applicable »).");
        }
        transaction.executer(() -> reglages.sauvegarder(r));
    }

    // ================================================================== clients

    /** Les clients, par nom ; {@code recherche} filtre sur le nom, le contact, le téléphone, l'email, le NIF. */
    public List<Client> clients(Acces a, String recherche, boolean avecArchives) {
        exiger(a, CONSULTER);
        String cherche = normaliser(recherche);
        return clients.clients(a.tenantId()).stream()
                .filter(c -> avecArchives || c.actif())
                .filter(c -> cherche.isEmpty() || normaliser(String.join(" ", c.nom(), c.etat().contact(),
                        c.telephone(), c.email(), c.nif(), c.ville())).contains(cherche))
                .sorted(Comparator.comparing(c -> normaliser(c.nom())))
                .toList();
    }

    public Client client(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouverClient(a.tenantId(), id);
    }

    public Client creerClient(Acces a, Client.Etat saisie) {
        exiger(a, SAISIR);
        Client c = Client.creer(a.tenantId(), saisie, Instant.now(horloge));
        verifierDoublon(a.tenantId(), c);
        transaction.executer(() -> clients.sauvegarder(c));
        return c;
    }

    public void modifierClient(Acces a, UUID id, Client.Etat saisie) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            Client c = trouverClient(a.tenantId(), id);
            c.definir(saisie);
            verifierDoublon(a.tenantId(), c);
            clients.sauvegarder(c);
        });
    }

    private void verifierDoublon(UUID tenantId, Client c) {
        boolean doublon = clients.clients(tenantId).stream()
                .filter(autre -> !autre.id().equals(c.id()) && autre.actif())
                .anyMatch(autre -> normaliser(autre.nom()).equals(normaliser(c.nom()))
                        || (!c.nif().isEmpty() && autre.nif().equalsIgnoreCase(c.nif())));
        if (doublon) {
            throw new ErreurMetier("Un client actif porte déjà ce nom ou ce NIF.");
        }
    }

    public void archiverClient(Acces a, UUID id) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            Client c = trouverClient(a.tenantId(), id);
            c.archiver();
            clients.sauvegarder(c);
        });
    }

    public void reactiverClient(Acces a, UUID id) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            Client c = trouverClient(a.tenantId(), id);
            c.reactiver();
            verifierDoublon(a.tenantId(), c);
            clients.sauvegarder(c);
        });
    }

    private Client trouverClient(UUID tenantId, UUID id) {
        return clients.client(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Client introuvable."));
    }

    // ================================================================== catalogue

    public List<Article> articles(Acces a, boolean avecArchives) {
        exiger(a, CONSULTER);
        return articles.articles(a.tenantId()).stream()
                .filter(x -> avecArchives || x.actif())
                .sorted(Comparator.comparing((Article x) -> normaliser(x.designation())))
                .toList();
    }

    public Article article(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouverArticle(a.tenantId(), id);
    }

    public Article creerArticle(Acces a, Article.Etat saisie) {
        exiger(a, CATALOGUE);
        Article x = Article.creer(a.tenantId(), saisie, Instant.now(horloge));
        verifierReference(a.tenantId(), x);
        transaction.executer(() -> articles.sauvegarder(x));
        return x;
    }

    public void modifierArticle(Acces a, UUID id, Article.Etat saisie) {
        exiger(a, CATALOGUE);
        transaction.executer(() -> {
            Article x = trouverArticle(a.tenantId(), id);
            x.definir(saisie);
            verifierReference(a.tenantId(), x);
            articles.sauvegarder(x);
        });
    }

    public void archiverArticle(Acces a, UUID id, boolean archiver) {
        exiger(a, CATALOGUE);
        transaction.executer(() -> {
            Article x = trouverArticle(a.tenantId(), id);
            if (archiver) {
                x.archiver();
            } else {
                x.reactiver();
                verifierReference(a.tenantId(), x);
            }
            articles.sauvegarder(x);
        });
    }

    private void verifierReference(UUID tenantId, Article x) {
        if (x.reference().isEmpty()) {
            return;
        }
        boolean doublon = articles.articles(tenantId).stream()
                .anyMatch(autre -> !autre.id().equals(x.id()) && autre.actif()
                        && autre.reference().equalsIgnoreCase(x.reference()));
        if (doublon) {
            throw new ErreurMetier("Un article actif porte déjà la référence « " + x.reference() + " ».");
        }
    }

    private Article trouverArticle(UUID tenantId, UUID id) {
        return articles.article(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Article introuvable."));
    }

    // ================================================================== documents : lecture

    public List<DocumentVente> documents(Acces a, Ports.Filtre filtre) {
        exiger(a, CONSULTER);
        return documents.documents(a.tenantId(), filtre);
    }

    public DocumentVente document(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouverDocument(a.tenantId(), id);
    }

    /** Les documents faits à partir de celui-ci : facture d'un devis, avoirs d'une facture. */
    public List<DocumentVente> issusDe(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return documents.issusDe(a.tenantId(), id);
    }

    private DocumentVente trouverDocument(UUID tenantId, UUID id) {
        return documents.document(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Document introuvable."));
    }

    private DocumentVente verrouillerDocument(UUID tenantId, UUID id) {
        return documents.verrouiller(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Document introuvable."));
    }

    // ================================================================== documents : brouillons

    /**
     * Ce qu'un nouveau document propose : date du jour, échéance selon le délai du client ou la validité des
     * devis.
     */
    public Contenu propose(Acces a, TypeDocument type, UUID clientId) {
        exiger(a, CONSULTER);
        ReglagesVentes r = reglages(a);
        LocalDate jour = aujourdHui();
        LocalDate echeance = switch (type) {
            case DEVIS -> jour.plusDays(r.validiteDevis());
            case FACTURE -> {
                int delai = clientId == null ? r.delaiPaiement()
                        : clients.client(a.tenantId(), clientId).map(Client::delaiPaiement).orElse(r.delaiPaiement());
                yield jour.plusDays(delai);
            }
            case AVOIR -> null;
        };
        return new Contenu(jour, echeance, "", "", List.of(), BigDecimal.ZERO, "");
    }

    public DocumentVente creerBrouillon(Acces a, TypeDocument type, UUID clientId, Contenu contenu) {
        exiger(a, SAISIR);
        if (type == TypeDocument.AVOIR) {
            throw new ErreurMetier("Un avoir se fait à partir de la facture qu'il corrige.");
        }
        Client c = trouverClient(a.tenantId(), clientId);
        DocumentVente d = DocumentVente.brouillon(a.tenantId(), type, c, controler(a, contenu), null, a.membreId(),
                Instant.now(horloge));
        transaction.executer(() -> documents.sauvegarder(d));
        return d;
    }

    public void modifierBrouillon(Acces a, UUID id, UUID clientId, Contenu contenu) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            DocumentVente d = verrouillerDocument(a.tenantId(), id);
            d.modifier(trouverClient(a.tenantId(), clientId), controler(a, contenu));
            documents.sauvegarder(d);
        });
    }

    public void supprimerBrouillon(Acces a, UUID id) {
        exiger(a, SAISIR);
        transaction.executer(() -> {
            DocumentVente d = verrouillerDocument(a.tenantId(), id);
            if (!d.modifiable()) {
                throw new ErreurMetier("Un document émis ne se supprime pas"
                        + (d.type() == TypeDocument.FACTURE ? " : corrigez-le par un avoir." : "."));
            }
            documents.supprimer(a.tenantId(), id);
        });
    }

    /** Une facture brouillon reprenant les lignes d'un devis. */
    public DocumentVente facturerDevis(Acces a, UUID devisId) {
        exiger(a, SAISIR);
        return transaction.executer(() -> {
            DocumentVente devis = verrouillerDocument(a.tenantId(), devisId);
            if (!devis.facturable()) {
                throw new ErreurMetier("Seul un devis émis ou accepté se transforme en facture.");
            }
            documents.issusDe(a.tenantId(), devisId).stream().findFirst().ifPresent(f -> {
                throw new ErreurMetier("Ce devis a déjà sa facture : " + f.intitule() + ".");
            });
            Client c = trouverClient(a.tenantId(), devis.client().id());
            Contenu propose = propose(a, TypeDocument.FACTURE, c.id());
            Contenu de = devis.contenu();
            Contenu contenu = new Contenu(propose.date(), propose.echeance(), de.objet(), de.reference(), de.lignes(),
                    de.remiseGlobale(), de.notes());
            DocumentVente f = DocumentVente.brouillon(a.tenantId(), TypeDocument.FACTURE, c, contenu, devis,
                    a.membreId(), Instant.now(horloge));
            documents.sauvegarder(f);
            return f;
        });
    }

    /** Un avoir brouillon qui reprend toutes les lignes de la facture : on retire ce qui ne s'annule pas. */
    public DocumentVente avoirSur(Acces a, UUID factureId) {
        exiger(a, SAISIR);
        return transaction.executer(() -> {
            DocumentVente facture = verrouillerDocument(a.tenantId(), factureId);
            if (facture.type() != TypeDocument.FACTURE || facture.statut() != StatutDocument.EMIS) {
                throw new ErreurMetier("Un avoir se fait à partir d'une facture émise.");
            }
            if (facture.creditable() <= 0) {
                throw new ErreurMetier("Cette facture est déjà entièrement annulée par des avoirs.");
            }
            Client c = trouverClient(a.tenantId(), facture.client().id());
            Contenu de = facture.contenu();
            Contenu contenu = new Contenu(aujourdHui(), null, "Avoir sur la facture " + facture.numero(),
                    de.reference(), de.lignes(), de.remiseGlobale(), "");
            DocumentVente avoir = DocumentVente.brouillon(a.tenantId(), TypeDocument.AVOIR, c, contenu, facture,
                    a.membreId(), Instant.now(horloge));
            documents.sauvegarder(avoir);
            return avoir;
        });
    }

    /**
     * Lignes saisies : un article archivé ou d'une autre organisation n'est pas accepté ; une entreprise qui ne
     * facture pas la TVA n'en met sur aucune ligne.
     */
    private Contenu controler(Acces a, Contenu contenu) {
        ReglagesVentes r = reglages(a);
        List<Ligne> lignes = new ArrayList<>();
        for (Ligne l : contenu.lignes()) {
            if (l.articleId() != null && articles.article(a.tenantId(), l.articleId()).isEmpty()) {
                throw new ErreurMetier("Article inconnu sur la ligne « " + l.designation() + " ».");
            }
            lignes.add(r.assujettiTva() ? l : new Ligne(l.articleId(), l.designation(), l.quantite(), l.unite(),
                    l.prixUnitaire(), l.remise(), BigDecimal.ZERO));
        }
        return new Contenu(contenu.date(), contenu.echeance(), contenu.objet(), contenu.reference(), lignes,
                contenu.remiseGlobale(), contenu.notes());
    }

    // ================================================================== documents : émission et suite

    /**
     * Émet un brouillon : numéro suivant de l'année de sa date, sans trou ; client et mentions figés. Les dates
     * suivent les numéros : un document ne peut pas être daté avant le dernier émis du même type, ni dans le futur.
     */
    public DocumentVente emettre(Acces a, UUID id) {
        exiger(a, EMETTRE);
        return transaction.executer(() -> {
            DocumentVente d = verrouillerDocument(a.tenantId(), id);
            if (!d.modifiable()) {
                throw new ErreurMetier("Ce document est déjà émis.");
            }
            LocalDate date = d.contenu().date();
            if (date.isAfter(aujourdHui())) {
                throw new ErreurMetier("Un document ne s'émet pas avec une date future.");
            }
            ReglagesVentes r = reglages(a);
            Client c = trouverClient(a.tenantId(), d.client().id());
            DocumentVente origine = d.origineId() == null ? null : verrouillerDocument(a.tenantId(), d.origineId());
            if (d.type() == TypeDocument.FACTURE && origine != null && !origine.facturable()) {
                throw new ErreurMetier("Le devis " + origine.numero() + " est « "
                        + origine.statut().libelle().toLowerCase() + " » : il ne se facture plus.");
            }
            verifierOrdreDesDates(a.tenantId(), d.type(), date);
            // Le compteur reste verrouillé jusqu'à la fin de la transaction : deux émissions simultanées passent
            // l'une après l'autre, et la date se revérifie une fois le compteur obtenu.
            long rang = documents.prochainRang(a.tenantId(), d.type(), date.getYear());
            verifierOrdreDesDates(a.tenantId(), d.type(), date);
            d.emettre(d.type().numero(date.getYear(), rang), c, r.assujettiTva() ? "" : r.mentionTva(),
                    r.conditions(), Instant.now(horloge));
            if (origine != null && d.type() == TypeDocument.FACTURE) {
                origine.marquerFacture();
                documents.sauvegarder(origine);
            }
            if (origine != null && d.type() == TypeDocument.AVOIR) {
                origine.crediter(d.totaux().totalTtc());
                documents.sauvegarder(origine);
            }
            documents.sauvegarder(d);
            return d;
        });
    }

    private void verifierOrdreDesDates(UUID tenantId, TypeDocument type, LocalDate date) {
        documents.derniereDateEmise(tenantId, type).filter(date::isBefore).ifPresent(derniere -> {
            throw new ErreurMetier("La date doit être au moins le " + JOUR.format(derniere) + ", date du dernier "
                    + type.libelle().toLowerCase() + " émis : la numérotation suit l'ordre des dates.");
        });
    }

    public void accepterDevis(Acces a, UUID id) {
        changerDevis(a, id, DocumentVente::accepter);
    }

    public void refuserDevis(Acces a, UUID id) {
        changerDevis(a, id, DocumentVente::refuser);
    }

    public void annulerDevis(Acces a, UUID id) {
        changerDevis(a, id, DocumentVente::annulerDevis);
    }

    private void changerDevis(Acces a, UUID id, java.util.function.Consumer<DocumentVente> action) {
        exiger(a, EMETTRE);
        transaction.executer(() -> {
            DocumentVente d = verrouillerDocument(a.tenantId(), id);
            action.accept(d);
            documents.sauvegarder(d);
        });
    }

    // ================================================================== règlements

    public List<Reglement> reglements(Acces a, UUID factureId) {
        exiger(a, CONSULTER);
        return reglements.reglementsDe(a.tenantId(), factureId).stream()
                .sorted(Comparator.comparing(Reglement::date).thenComparing(Reglement::creeLe)).toList();
    }

    public Reglement encaisser(Acces a, UUID factureId, LocalDate date, long montant, ModeReglement mode,
                               String reference) {
        exiger(a, ENCAISSER);
        if (date == null || date.isAfter(aujourdHui())) {
            throw new ErreurMetier("Date du règlement : aujourd'hui ou avant.");
        }
        if (mode == null) {
            throw new ErreurMetier("Choisissez le mode de règlement.");
        }
        return transaction.executer(() -> {
            DocumentVente f = verrouillerDocument(a.tenantId(), factureId);
            f.encaisser(montant);
            if (date.isBefore(f.contenu().date())) {
                throw new ErreurMetier("Le règlement ne peut pas précéder la date de la facture.");
            }
            Reglement r = Reglement.nouveau(a.tenantId(), factureId, date, montant, mode, reference, a.membreId(),
                    Instant.now(horloge));
            reglements.sauvegarder(r);
            documents.sauvegarder(f);
            return r;
        });
    }

    public void annulerReglement(Acces a, UUID reglementId, String motif) {
        exiger(a, ENCAISSER);
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("Indiquez pourquoi le règlement est annulé.");
        }
        transaction.executer(() -> {
            Reglement r = reglements.reglement(a.tenantId(), reglementId)
                    .orElseThrow(() -> new ElementIntrouvable("Règlement introuvable."));
            DocumentVente f = verrouillerDocument(a.tenantId(), r.factureId());
            Reglement annule = r.annuler(motif);
            f.desencaisser(r.montant());
            reglements.sauvegarder(annule);
            documents.sauvegarder(f);
        });
    }

    // ================================================================== suivi

    /** Les chiffres du tableau de bord. */
    public record Bilan(long factureMoisHt, long encaisseMois, long resteDu, long enRetard, int facturesEnRetard,
                        List<DocumentVente> devisEnAttente, List<DocumentVente> dernieres) {
    }

    public Bilan bilan(Acces a) {
        exiger(a, CONSULTER);
        LocalDate jour = aujourdHui();
        LocalDate debutMois = jour.withDayOfMonth(1);
        UUID t = a.tenantId();
        List<DocumentVente> duMois = documents.documents(t,
                new Ports.Filtre(null, null, debutMois, jour, null, false, 0));
        long facture = 0;
        for (DocumentVente d : duMois) {
            if (d.statut() == StatutDocument.BROUILLON) {
                continue;
            }
            if (d.type() == TypeDocument.FACTURE) {
                facture += d.totaux().totalHt();
            } else if (d.type() == TypeDocument.AVOIR) {
                facture -= d.totaux().totalHt();
            }
        }
        long encaisse = reglements.reglementsEntre(t, debutMois, jour).stream()
                .filter(r -> !r.annule()).mapToLong(Reglement::montant).sum();
        List<DocumentVente> impayes = impayes(a);
        long resteDu = impayes.stream().mapToLong(DocumentVente::resteAPayer).sum();
        List<DocumentVente> retard = impayes.stream()
                .filter(d -> d.situation(jour) == DocumentVente.Situation.EN_RETARD).toList();
        List<DocumentVente> devis = documents.documents(t,
                        new Ports.Filtre(TypeDocument.DEVIS, null, null, null, null, false, 0)).stream()
                .filter(d -> d.statut() == StatutDocument.EMIS || d.statut() == StatutDocument.ACCEPTE)
                .limit(10).toList();
        List<DocumentVente> dernieres = documents.documents(t,
                new Ports.Filtre(TypeDocument.FACTURE, null, null, null, null, false, 10));
        return new Bilan(facture, encaisse, resteDu, retard.stream().mapToLong(DocumentVente::resteAPayer).sum(),
                retard.size(), devis, dernieres);
    }

    /** Les factures émises qui ont un reste à payer, de la plus ancienne échéance à la plus récente. */
    public List<DocumentVente> impayes(Acces a) {
        exiger(a, CONSULTER);
        return documents.documents(a.tenantId(), new Ports.Filtre(TypeDocument.FACTURE, null, null, null, null, true,
                        0)).stream()
                .filter(d -> d.resteAPayer() > 0)
                .sorted(Comparator.comparing((DocumentVente d) -> d.contenu().echeance(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Ce que chaque client doit, du plus gros montant au plus petit. */
    public record DuParClient(UUID clientId, String nom, long montant, long enRetard, int factures) {
    }

    public List<DuParClient> duParClient(Acces a) {
        LocalDate jour = aujourdHui();
        Map<UUID, DuParClient> parClient = new LinkedHashMap<>();
        for (DocumentVente d : impayes(a)) {
            boolean retard = d.situation(jour) == DocumentVente.Situation.EN_RETARD;
            parClient.merge(d.client().id(),
                    new DuParClient(d.client().id(), d.client().nom(), d.resteAPayer(), retard ? d.resteAPayer() : 0, 1),
                    (x, y) -> new DuParClient(x.clientId(), x.nom(), x.montant() + y.montant(),
                            x.enRetard() + y.enRetard(), x.factures() + y.factures()));
        }
        return parClient.values().stream().sorted(Comparator.comparingLong(DuParClient::montant).reversed()).toList();
    }

    // ================================================================== relevé de compte client

    /** Une ligne du relevé : une facture (débit), un avoir ou un règlement (crédit). */
    public record LigneReleve(LocalDate date, String piece, String libelle, long debit, long credit, long solde,
                              LocalDate echeance) {
    }

    /** Le relevé d'un client sur une période : solde au début, opérations, solde à la fin (positif = il doit). */
    public record Releve(Client client, LocalDate du, LocalDate au, long soldeInitial, List<LigneReleve> lignes,
                         long totalDebit, long totalCredit, long soldeFinal) {
    }

    public Releve releve(Acces a, UUID clientId, LocalDate du, LocalDate au) {
        exiger(a, CONSULTER);
        if (du == null || au == null || au.isBefore(du)) {
            throw new ErreurMetier("Période du relevé invalide.");
        }
        Client c = trouverClient(a.tenantId(), clientId);
        List<DocumentVente> pieces = documents.documents(a.tenantId(),
                        new Ports.Filtre(null, clientId, null, au, null, false, 0)).stream()
                .filter(d -> d.statut() != StatutDocument.BROUILLON && d.type() != TypeDocument.DEVIS)
                .toList();
        record Operation(LocalDate date, String piece, String libelle, long debit, long credit, LocalDate echeance,
                         int ordre) {
        }
        List<Operation> operations = new ArrayList<>();
        for (DocumentVente d : pieces) {
            long ttc = d.totaux().totalTtc();
            String libelle = d.contenu().objet().isEmpty() ? d.type().libelle() : d.contenu().objet();
            if (d.type() == TypeDocument.FACTURE) {
                operations.add(new Operation(d.contenu().date(), d.numero(), libelle, ttc, 0, d.contenu().echeance(),
                        0));
                for (Reglement r : reglements.reglementsDe(a.tenantId(), d.id())) {
                    if (!r.annule() && !r.date().isAfter(au)) {
                        operations.add(new Operation(r.date(), d.numero(), "Règlement " + r.mode().libelle()
                                .toLowerCase() + (r.reference().isEmpty() ? "" : " (" + r.reference() + ")"), 0,
                                r.montant(), null, 2));
                    }
                }
            } else {
                operations.add(new Operation(d.contenu().date(), d.numero(), libelle, 0, ttc, null, 1));
            }
        }
        operations.sort(Comparator.comparing(Operation::date).thenComparingInt(Operation::ordre)
                .thenComparing(Operation::piece));
        long solde = 0;
        long debit = 0;
        long credit = 0;
        List<LigneReleve> lignes = new ArrayList<>();
        for (Operation o : operations) {
            if (o.date().isBefore(du)) {
                solde += o.debit() - o.credit();
                continue;
            }
            lignes.add(new LigneReleve(o.date(), o.piece(), o.libelle(), o.debit(), o.credit(), 0, o.echeance()));
        }
        long initial = solde;
        List<LigneReleve> avecSolde = new ArrayList<>();
        for (LigneReleve l : lignes) {
            solde += l.debit() - l.credit();
            debit += l.debit();
            credit += l.credit();
            avecSolde.add(new LigneReleve(l.date(), l.piece(), l.libelle(), l.debit(), l.credit(), solde,
                    l.echeance()));
        }
        return new Releve(c, du, au, initial, avecSolde, debit, credit, solde);
    }

    // ================================================================== outils

    /** Minuscules sans accents ni espaces en trop, pour comparer et chercher. */
    static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
