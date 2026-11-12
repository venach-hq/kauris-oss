package com.venach.kauris.ventes.stock.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.TypeDocument;
import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.MouvementStock;
import com.venach.kauris.ventes.stock.domain.Position;
import com.venach.kauris.ventes.stock.domain.TypeMouvement;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.venach.kauris.ventes.stock.application.PermissionsStock.CONSULTER;
import static com.venach.kauris.ventes.stock.application.PermissionsStock.DEPOTS;
import static com.venach.kauris.ventes.stock.application.PermissionsStock.MOUVEMENTER;
import static com.venach.kauris.ventes.stock.application.PermissionsStock.exiger;

/**
 * Le stock des articles suivis : dépôts, entrées et sorties, transferts, inventaires, valeur au coût moyen
 * pondéré. Les factures émises font sortir le stock du dépôt principal, les avoirs l'y font revenir.
 */
public class StockService implements Ports.Stock {

    private final Ports.Articles articles;
    private final PortsStock.Depots depots;
    private final PortsStock.Stocks stocks;
    private final PortsStock.Mouvements mouvements;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public StockService(Ports.Articles articles, PortsStock.Depots depots, PortsStock.Stocks stocks,
                        PortsStock.Mouvements mouvements, UniteDeTravail transaction, Clock horloge) {
        this.articles = articles;
        this.depots = depots;
        this.stocks = stocks;
        this.mouvements = mouvements;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    // ================================================================== dépôts

    /** Les dépôts, le principal d'abord ; le premier, « Magasin principal », est créé à la première visite. */
    public List<Depot> depots(Acces a, boolean avecArchives) {
        exiger(a, CONSULTER);
        return assurerDepots(a.tenantId()).stream().filter(d -> avecArchives || d.actif()).toList();
    }

    private List<Depot> assurerDepots(UUID tenantId) {
        List<Depot> tous = depots.depots(tenantId);
        if (tous.isEmpty()) {
            Depot premier = Depot.nouveau(tenantId, Depot.NOM_PAR_DEFAUT, true, Instant.now(horloge));
            transaction.executer(() -> depots.sauvegarder(premier));
            tous = List.of(premier);
        }
        return tous.stream().sorted(Comparator.comparing((Depot d) -> !d.principal())
                .thenComparing(d -> normaliser(d.nom()))).toList();
    }

    private Depot principal(UUID tenantId) {
        return assurerDepots(tenantId).stream().filter(Depot::principal).findFirst()
                .orElseGet(() -> assurerDepots(tenantId).stream().filter(Depot::actif).findFirst()
                        .orElseThrow(() -> new ErreurMetier("Aucun dépôt actif : réactivez-en un.")));
    }

    public Depot creerDepot(Acces a, String nom) {
        exiger(a, DEPOTS);
        assurerDepots(a.tenantId());
        Depot d = Depot.nouveau(a.tenantId(), nom, false, Instant.now(horloge));
        verifierNom(a.tenantId(), d);
        transaction.executer(() -> depots.sauvegarder(d));
        return d;
    }

    public void renommerDepot(Acces a, UUID id, String nom) {
        exiger(a, DEPOTS);
        transaction.executer(() -> {
            Depot d = depot(a.tenantId(), id);
            d.renommer(nom);
            verifierNom(a.tenantId(), d);
            depots.sauvegarder(d);
        });
    }

    /** Le dépôt principal reçoit le stock des ventes et des avoirs. */
    public void rendrePrincipal(Acces a, UUID id) {
        exiger(a, DEPOTS);
        transaction.executer(() -> {
            Depot nouveau = depot(a.tenantId(), id);
            if (!nouveau.actif()) {
                throw new ErreurMetier("Un dépôt archivé ne peut pas être le principal.");
            }
            for (Depot d : depots.depots(a.tenantId())) {
                d.devenirPrincipal(d.id().equals(id));
                depots.sauvegarder(d);
            }
        });
    }

    public void archiverDepot(Acces a, UUID id, boolean archiver) {
        exiger(a, DEPOTS);
        transaction.executer(() -> {
            Depot d = depot(a.tenantId(), id);
            if (archiver) {
                if (d.principal()) {
                    throw new ErreurMetier("Choisissez d'abord un autre dépôt principal.");
                }
                boolean garni = stocks.duDepot(a.tenantId(), id).values().stream().anyMatch(q -> q.signum() != 0);
                if (garni) {
                    throw new ErreurMetier("Ce dépôt contient encore du stock : transférez-le ou faites son "
                            + "inventaire à zéro avant de l'archiver.");
                }
                d.archiver();
            } else {
                d.reactiver();
            }
            depots.sauvegarder(d);
        });
    }

    private void verifierNom(UUID tenantId, Depot d) {
        if (depots.depots(tenantId).stream().anyMatch(x -> !x.id().equals(d.id())
                && normaliser(x.nom()).equals(normaliser(d.nom())))) {
            throw new ErreurMetier("Un dépôt porte déjà ce nom.");
        }
    }

    private Depot depot(UUID tenantId, UUID id) {
        return depots.depot(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Dépôt introuvable."));
    }

    private Depot depotActif(UUID tenantId, UUID id) {
        Depot d = depot(tenantId, id);
        if (!d.actif()) {
            throw new ErreurMetier("Le dépôt « " + d.nom() + " » est archivé.");
        }
        return d;
    }

    // ================================================================== état du stock

    /**
     * Une ligne de l'état du stock.
     *
     * @param quantite      quantité dans le dépôt demandé, ou totale
     * @param quantiteTotale tous dépôts
     * @param valeur        valeur de {@code quantite} au CMUP
     * @param bas           sous le seuil d'alerte (ou négatif)
     */
    public record LigneEtat(Article article, BigDecimal quantite, BigDecimal quantiteTotale, long cmup, long valeur,
                            boolean bas) {
    }

    public record Etat(List<LigneEtat> lignes, long valeurTotale, int alertes, Depot depot) {
    }

    /** L'état du stock des articles suivis, pour un dépôt ou pour tous ({@code depotId} nul). */
    public Etat etat(Acces a, UUID depotId, String recherche, boolean basSeulement) {
        exiger(a, CONSULTER);
        Depot depot = depotId == null ? null : depot(a.tenantId(), depotId);
        Map<UUID, Position> positions = new LinkedHashMap<>();
        stocks.positions(a.tenantId()).forEach(p -> positions.put(p.articleId(), p));
        Map<UUID, BigDecimal> dansDepot = depot == null ? Map.of() : stocks.duDepot(a.tenantId(), depot.id());
        String cherche = normaliser(recherche);
        List<LigneEtat> lignes = new ArrayList<>();
        long total = 0;
        int alertes = 0;
        for (Article x : articles.articles(a.tenantId())) {
            if (!x.suiviStock() || (!x.actif() && !positions.containsKey(x.id()))) {
                continue;
            }
            if (!cherche.isEmpty() && !normaliser(x.designation() + " " + x.reference()).contains(cherche)) {
                continue;
            }
            Position p = positions.getOrDefault(x.id(), Position.vide(a.tenantId(), x.id()));
            BigDecimal q = (depot == null ? p.quantite() : dansDepot.getOrDefault(x.id(), BigDecimal.ZERO))
                    .setScale(3, java.math.RoundingMode.HALF_UP);
            long valeur = depot == null ? p.valeur() : Position.valeur(q, p.cmup());
            boolean bas = p.quantite().signum() < 0
                    || (x.seuilAlerte() != null && p.quantite().compareTo(x.seuilAlerte()) <= 0);
            if (bas) {
                alertes++;
            }
            if (basSeulement && !bas) {
                continue;
            }
            lignes.add(new LigneEtat(x, q, p.quantite(), p.cmup(), valeur, bas));
            total += valeur;
        }
        lignes.sort(Comparator.comparing(l -> normaliser(l.article().designation())));
        return new Etat(lignes, total, alertes, depot);
    }

    /** La fiche d'un article : position, quantités par dépôt, mouvements de la période. */
    public record Fiche(Article article, Position position, Map<Depot, BigDecimal> parDepot,
                        List<MouvementStock> mouvements) {
    }

    public Fiche fiche(Acces a, UUID articleId, LocalDate du, LocalDate au) {
        exiger(a, CONSULTER);
        Article x = article(a.tenantId(), articleId);
        Position p = stocks.positions(a.tenantId()).stream().filter(s -> s.articleId().equals(articleId))
                .findFirst().orElse(Position.vide(a.tenantId(), articleId));
        Map<UUID, BigDecimal> quantites = stocks.parDepot(a.tenantId(), articleId);
        Map<Depot, BigDecimal> parDepot = new LinkedHashMap<>();
        for (Depot d : assurerDepots(a.tenantId())) {
            BigDecimal q = quantites.getOrDefault(d.id(), BigDecimal.ZERO).setScale(3, java.math.RoundingMode.HALF_UP);
            if (d.actif() || q.signum() != 0) {
                parDepot.put(d, q);
            }
        }
        return new Fiche(x, p, parDepot, mouvements.mouvements(a.tenantId(), articleId, null, du, au, 500));
    }

    public List<MouvementStock> mouvements(Acces a, UUID depotId, LocalDate du, LocalDate au, int limite) {
        exiger(a, CONSULTER);
        return mouvements.mouvements(a.tenantId(), null, depotId, du, au, limite);
    }

    /** Les articles suivis, pour les formulaires. */
    public List<Article> articlesSuivis(Acces a) {
        exiger(a, CONSULTER);
        return articles.articles(a.tenantId()).stream().filter(x -> x.actif() && x.suiviStock())
                .sorted(Comparator.comparing(x -> normaliser(x.designation()))).toList();
    }

    private Article article(UUID tenantId, UUID id) {
        return articles.article(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Article introuvable."));
    }

    // ================================================================== mouvements saisis

    /**
     * Une entrée, une sortie ou un stock initial saisi à la main.
     *
     * @param coutUnitaire coût d'achat unitaire d'une entrée ; ignoré pour une sortie (CMUP)
     */
    public MouvementStock saisir(Acces a, TypeMouvement type, UUID articleId, UUID depotId, BigDecimal quantite,
                                 long coutUnitaire, LocalDate date, String libelle, String reference) {
        exiger(a, MOUVEMENTER);
        if (!type.manuel()) {
            throw new ErreurMetier("Ce mouvement ne se saisit pas à la main.");
        }
        LocalDate jour = controlerDate(date);
        return transaction.executer(() -> {
            Article x = articleSuivi(a.tenantId(), articleId);
            Depot d = depotActif(a.tenantId(), depotId);
            return bouger(a.tenantId(), type, x, d, quantite, coutUnitaire, jour,
                    libelle == null || libelle.isBlank() ? type.libelle() : libelle, reference, null, null,
                    a.membreId());
        });
    }

    /** Déplace une quantité d'un dépôt à un autre ; la valeur totale ne change pas. */
    public void transferer(Acces a, UUID articleId, UUID deDepot, UUID versDepot, BigDecimal quantite,
                           LocalDate date, String libelle) {
        exiger(a, MOUVEMENTER);
        if (deDepot.equals(versDepot)) {
            throw new ErreurMetier("Choisissez deux dépôts différents.");
        }
        LocalDate jour = controlerDate(date);
        transaction.executer(() -> {
            Article x = articleSuivi(a.tenantId(), articleId);
            Depot de = depotActif(a.tenantId(), deDepot);
            Depot vers = depotActif(a.tenantId(), versDepot);
            UUID lien = UUID.randomUUID();
            String texte = libelle == null || libelle.isBlank() ? "Transfert " + de.nom() + " → " + vers.nom() : libelle;
            bouger(a.tenantId(), TypeMouvement.TRANSFERT_SORTIE, x, de, quantite, 0, jour, texte, "", null, lien,
                    a.membreId());
            bouger(a.tenantId(), TypeMouvement.TRANSFERT_ENTREE, x, vers, quantite, 0, jour, texte, "", null, lien,
                    a.membreId());
        });
    }

    /**
     * L'inventaire d'un dépôt : pour chaque article compté, l'écart avec la quantité enregistrée devient un
     * ajustement. Les articles non comptés ne changent pas.
     *
     * @return nombre d'ajustements
     */
    public int inventorier(Acces a, UUID depotId, Map<UUID, BigDecimal> comptes, LocalDate date, String reference) {
        exiger(a, MOUVEMENTER);
        LocalDate jour = controlerDate(date);
        return transaction.executer(() -> {
            Depot d = depotActif(a.tenantId(), depotId);
            Map<UUID, BigDecimal> enregistre = stocks.duDepot(a.tenantId(), d.id());
            int ajustements = 0;
            for (Map.Entry<UUID, BigDecimal> e : comptes.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if (e.getValue().signum() < 0) {
                    throw new ErreurMetier("Une quantité comptée ne peut pas être négative.");
                }
                Article x = articleSuivi(a.tenantId(), e.getKey());
                BigDecimal ecart = e.getValue().subtract(enregistre.getOrDefault(x.id(), BigDecimal.ZERO));
                if (ecart.signum() == 0) {
                    continue;
                }
                Position p = stocks.verrouiller(a.tenantId(), x.id()).orElse(Position.vide(a.tenantId(), x.id()));
                bouger(a.tenantId(), ecart.signum() > 0 ? TypeMouvement.AJUSTEMENT_PLUS : TypeMouvement.AJUSTEMENT_MOINS,
                        x, d, ecart.abs(), p.cmup(), jour, "Inventaire " + d.nom(), reference, null, null,
                        a.membreId());
                ajustements++;
            }
            return ajustements;
        });
    }

    private LocalDate controlerDate(LocalDate date) {
        LocalDate jour = date == null ? aujourdHui() : date;
        if (jour.isAfter(aujourdHui())) {
            throw new ErreurMetier("La date d'un mouvement ne peut pas être dans le futur.");
        }
        return jour;
    }

    private Article articleSuivi(UUID tenantId, UUID articleId) {
        Article x = article(tenantId, articleId);
        if (!x.suiviStock()) {
            throw new ErreurMetier("« " + x.designation() + " » n'est pas suivi en stock (voir le catalogue).");
        }
        return x;
    }

    // ================================================================== factures et avoirs

    /**
     * Une facture émise fait sortir du dépôt principal les articles suivis qu'elle contient ; un avoir les y fait
     * revenir, au coût moyen du moment. Dans la transaction de l'émission ; un document ne bouge le stock qu'une
     * fois.
     */
    @Override
    public void documentEmis(DocumentVente document) {
        if (document.type() == TypeDocument.DEVIS) {
            return;
        }
        UUID tenantId = document.tenantId();
        if (!mouvements.duDocument(tenantId, document.id()).isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> quantites = new LinkedHashMap<>();
        for (Ligne l : document.contenu().lignes()) {
            if (l.articleId() != null) {
                quantites.merge(l.articleId(), l.quantite(), BigDecimal::add);
            }
        }
        if (quantites.isEmpty()) {
            return;
        }
        boolean facture = document.type() == TypeDocument.FACTURE;
        Depot d = principal(tenantId);
        for (Map.Entry<UUID, BigDecimal> e : quantites.entrySet()) {
            Optional<Article> x = articles.article(tenantId, e.getKey()).filter(Article::suiviStock);
            if (x.isEmpty()) {
                continue;
            }
            bouger(tenantId, facture ? TypeMouvement.VENTE : TypeMouvement.RETOUR_VENTE, x.get(), d, e.getValue(), 0,
                    document.contenu().date(), (facture ? "Facture " : "Avoir ") + document.numero() + " · "
                            + document.client().nom(), document.numero(), document.id(), null, document.creePar());
        }
    }

    // ================================================================== le mouvement lui-même

    private MouvementStock bouger(UUID tenantId, TypeMouvement type, Article x, Depot d, BigDecimal quantite,
                                  long coutSaisi, LocalDate date, String libelle, String reference, UUID documentId,
                                  UUID lienId, UUID creePar) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new ErreurMetier("Quantité : un nombre positif.");
        }
        Position avant = stocks.verrouiller(tenantId, x.id()).orElse(Position.vide(tenantId, x.id()));
        long cout;
        Position apres;
        switch (type) {
            case TRANSFERT_SORTIE, TRANSFERT_ENTREE -> {
                cout = avant.cmup();
                apres = avant;
            }
            case RETOUR_VENTE, AJUSTEMENT_PLUS -> {
                cout = avant.cmup();
                apres = avant.entrer(quantite, cout);
            }
            default -> {
                if (type.entree()) {
                    cout = coutSaisi;
                    apres = avant.entrer(quantite, cout);
                } else {
                    cout = avant.cmup();
                    apres = avant.sortir(quantite);
                }
            }
        }
        MouvementStock m = new MouvementStock(UUID.randomUUID(), tenantId,
                mouvements.prochainNumero(tenantId, date.getYear()), date, type, x.id(), d.id(), quantite, cout,
                Position.valeur(quantite, cout), libelle, reference, documentId, lienId, creePar,
                Instant.now(horloge));
        mouvements.sauvegarder(m);
        stocks.sauvegarder(apres);
        stocks.ajouter(tenantId, x.id(), d.id(), m.effet());
        return m;
    }

    static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
