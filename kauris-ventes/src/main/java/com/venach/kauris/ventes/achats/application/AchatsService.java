package com.venach.kauris.ventes.achats.application;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.Commande.LigneCommande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.stock.application.StockService;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.venach.kauris.ventes.achats.application.PermissionsAchats.COMMANDER;
import static com.venach.kauris.ventes.achats.application.PermissionsAchats.CONSULTER;
import static com.venach.kauris.ventes.achats.application.PermissionsAchats.PAYER;
import static com.venach.kauris.ventes.achats.application.PermissionsAchats.RECEVOIR;
import static com.venach.kauris.ventes.achats.application.PermissionsAchats.exiger;

/**
 * Les achats : fournisseurs, bons de commande, réceptions (qui font entrer le stock), factures fournisseurs et leurs
 * règlements, dettes.
 */
public class AchatsService {

    private final PortsAchats.Fournisseurs fournisseurs;
    private final PortsAchats.Commandes commandes;
    private final PortsAchats.Receptions receptions;
    private final PortsAchats.Factures factures;
    private final PortsAchats.Reglements reglements;
    private final PortsAchats.Numeros numeros;
    private final PortsAchats.Decaissements decaissements;
    private final Ports.Articles articles;
    private final Ports.Reglages reglages;
    private final StockService stock;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public AchatsService(PortsAchats.Fournisseurs fournisseurs, PortsAchats.Commandes commandes,
                         PortsAchats.Receptions receptions, PortsAchats.Factures factures,
                         PortsAchats.Reglements reglements, PortsAchats.Numeros numeros,
                         PortsAchats.Decaissements decaissements, Ports.Articles articles, Ports.Reglages reglages,
                         StockService stock, UniteDeTravail transaction, Clock horloge) {
        this.reglages = reglages;
        this.fournisseurs = fournisseurs;
        this.commandes = commandes;
        this.receptions = receptions;
        this.factures = factures;
        this.reglements = reglements;
        this.numeros = numeros;
        this.decaissements = decaissements;
        this.articles = articles;
        this.stock = stock;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    // ================================================================== fournisseurs

    public List<Fournisseur> fournisseurs(Acces a, String recherche, boolean avecArchives) {
        exiger(a, CONSULTER);
        String cherche = normaliser(recherche);
        return fournisseurs.fournisseurs(a.tenantId()).stream()
                .filter(f -> avecArchives || f.actif())
                .filter(f -> cherche.isEmpty() || normaliser(String.join(" ", f.nom(), f.contact(), f.telephone(),
                        f.email(), f.nif(), f.ville())).contains(cherche))
                .sorted(Comparator.comparing(f -> normaliser(f.nom()))).toList();
    }

    public Fournisseur fournisseur(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouverFournisseur(a.tenantId(), id);
    }

    public Fournisseur creerFournisseur(Acces a, Fournisseur.Etat saisie) {
        exiger(a, COMMANDER);
        Fournisseur f = Fournisseur.creer(a.tenantId(), saisie, Instant.now(horloge));
        verifierDoublon(a.tenantId(), f);
        transaction.executer(() -> fournisseurs.sauvegarder(f));
        return f;
    }

    public void modifierFournisseur(Acces a, UUID id, Fournisseur.Etat saisie) {
        exiger(a, COMMANDER);
        transaction.executer(() -> {
            Fournisseur f = trouverFournisseur(a.tenantId(), id);
            f.definir(saisie);
            verifierDoublon(a.tenantId(), f);
            fournisseurs.sauvegarder(f);
        });
    }

    public void archiverFournisseur(Acces a, UUID id, boolean archiver) {
        exiger(a, COMMANDER);
        transaction.executer(() -> {
            Fournisseur f = trouverFournisseur(a.tenantId(), id);
            f.archiver(archiver);
            if (!archiver) {
                verifierDoublon(a.tenantId(), f);
            }
            fournisseurs.sauvegarder(f);
        });
    }

    private void verifierDoublon(UUID tenantId, Fournisseur f) {
        boolean doublon = fournisseurs.fournisseurs(tenantId).stream()
                .filter(x -> !x.id().equals(f.id()) && x.actif())
                .anyMatch(x -> normaliser(x.nom()).equals(normaliser(f.nom()))
                        || (!f.nif().isEmpty() && x.nif().equalsIgnoreCase(f.nif())));
        if (doublon) {
            throw new ErreurMetier("Un fournisseur actif porte déjà ce nom ou ce NIF.");
        }
    }

    private Fournisseur trouverFournisseur(UUID tenantId, UUID id) {
        return fournisseurs.fournisseur(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Fournisseur introuvable."));
    }

    // ================================================================== catalogue

    /** Les articles (pas les prestations) que l'on peut commander. */
    public List<com.venach.kauris.ventes.domain.Article> articles(Acces a) {
        exiger(a, CONSULTER);
        return articles.articles(a.tenantId()).stream()
                .filter(x -> x.actif() && x.type() == com.venach.kauris.ventes.domain.Article.Type.BIEN)
                .sorted(Comparator.comparing(x -> normaliser(x.designation()))).toList();
    }

    /** Le taux de TVA proposé sur une nouvelle ligne (réglages des ventes ; 0 si l'entreprise ne facture pas la TVA). */
    public BigDecimal tauxPropose(Acces a) {
        exiger(a, CONSULTER);
        return reglages.reglages(a.tenantId())
                .orElseGet(() -> com.venach.kauris.ventes.domain.ReglagesVentes.parDefaut(a.tenantId())).tauxPropose();
    }

    // ================================================================== commandes

    public List<Commande> commandes(Acces a, UUID fournisseurId) {
        exiger(a, CONSULTER);
        return commandes.commandes(a.tenantId(), fournisseurId, 300);
    }

    public Commande commande(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return trouverCommande(a.tenantId(), id);
    }

    public Commande creerCommande(Acces a, UUID fournisseurId, LocalDate date, LocalDate livraison, String objet,
                                  String notes, List<LigneCommande> lignes) {
        exiger(a, COMMANDER);
        Fournisseur f = trouverFournisseur(a.tenantId(), fournisseurId);
        Commande c = Commande.brouillon(a.tenantId(), f, date, livraison, objet, notes, controler(a, lignes),
                a.membreId(), Instant.now(horloge));
        transaction.executer(() -> commandes.sauvegarder(c));
        return c;
    }

    public void modifierCommande(Acces a, UUID id, UUID fournisseurId, LocalDate date, LocalDate livraison,
                                 String objet, String notes, List<LigneCommande> lignes) {
        exiger(a, COMMANDER);
        transaction.executer(() -> {
            Commande c = verrouillerCommande(a.tenantId(), id);
            c.modifier(trouverFournisseur(a.tenantId(), fournisseurId), date, livraison, objet, notes,
                    controler(a, lignes));
            commandes.sauvegarder(c);
        });
    }

    public void supprimerBrouillon(Acces a, UUID id) {
        exiger(a, COMMANDER);
        transaction.executer(() -> {
            Commande c = verrouillerCommande(a.tenantId(), id);
            if (!c.modifiable()) {
                throw new ErreurMetier("Une commande envoyée ne se supprime pas : annulez-la.");
            }
            commandes.supprimerCommande(a.tenantId(), id);
        });
    }

    public Commande envoyer(Acces a, UUID id) {
        exiger(a, COMMANDER);
        return transaction.executer(() -> {
            Commande c = verrouillerCommande(a.tenantId(), id);
            if (c.date().isAfter(aujourdHui())) {
                throw new ErreurMetier("Une commande ne s'envoie pas avec une date future.");
            }
            Fournisseur f = trouverFournisseur(a.tenantId(), c.fournisseurId());
            if (!c.modifiable()) {
                throw new ErreurMetier("Cette commande est déjà envoyée.");
            }
            if (c.lignes().isEmpty()) {
                throw new ErreurMetier("Ajoutez au moins une ligne avant d'envoyer la commande.");
            }
            c.emettre(numeros.prochain(a.tenantId(), "BC", c.date().getYear()), f, Instant.now(horloge));
            commandes.sauvegarder(c);
            return c;
        });
    }

    public void annulerOuSolder(Acces a, UUID id) {
        exiger(a, COMMANDER);
        transaction.executer(() -> {
            Commande c = verrouillerCommande(a.tenantId(), id);
            c.annulerOuSolder();
            commandes.sauvegarder(c);
        });
    }

    private List<LigneCommande> controler(Acces a, List<LigneCommande> lignes) {
        for (LigneCommande l : lignes) {
            if (l.articleId() != null && articles.article(a.tenantId(), l.articleId()).isEmpty()) {
                throw new ErreurMetier("Article inconnu sur la ligne « " + l.designation() + " ».");
            }
        }
        return lignes;
    }

    private Commande trouverCommande(UUID tenantId, UUID id) {
        return commandes.commande(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Commande introuvable."));
    }

    private Commande verrouillerCommande(UUID tenantId, UUID id) {
        return commandes.verrouillerCommande(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Commande introuvable."));
    }

    // ================================================================== réceptions

    public List<Reception> receptions(Acces a, UUID commandeId) {
        exiger(a, CONSULTER);
        return receptions.deLaCommande(a.tenantId(), commandeId);
    }

    public Reception reception(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return receptions.reception(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Réception introuvable."));
    }

    /**
     * Réceptionne une livraison : quantités reçues par ligne de la commande (vide ou zéro = rien), dans un dépôt. Les
     * articles suivis entrent en stock au prix de la commande.
     */
    public Reception recevoir(Acces a, UUID commandeId, List<BigDecimal> quantites, UUID depotId, LocalDate date,
                              String bonDeLivraison) {
        exiger(a, RECEVOIR);
        LocalDate jour = date == null ? aujourdHui() : date;
        if (jour.isAfter(aujourdHui())) {
            throw new ErreurMetier("La date de réception ne peut pas être dans le futur.");
        }
        return transaction.executer(() -> {
            Commande c = verrouillerCommande(a.tenantId(), commandeId);
            if (!c.receptionnable()) {
                throw new ErreurMetier("Seule une commande envoyée se réceptionne.");
            }
            if (jour.isBefore(c.date())) {
                throw new ErreurMetier("La réception ne peut pas précéder la commande.");
            }
            if (depotId == null) {
                throw new ErreurMetier("Choisissez le dépôt où la marchandise arrive.");
            }
            List<BigDecimal> parLigne = new ArrayList<>();
            List<Reception.LigneRecue> recues = new ArrayList<>();
            for (int i = 0; i < c.lignes().size(); i++) {
                BigDecimal q = i < quantites.size() && quantites.get(i) != null ? quantites.get(i) : BigDecimal.ZERO;
                parLigne.add(q);
                LigneCommande l = c.lignes().get(i);
                if (q.signum() > 0) {
                    recues.add(new Reception.LigneRecue(i, l.articleId(), l.designation(), q, l.unite(),
                            l.prixUnitaire()));
                }
            }
            String numero = numeros.prochain(a.tenantId(), "BR", jour.getYear());
            Reception r = new Reception(UUID.randomUUID(), a.tenantId(), numero, c.id(), c.numero(), c.fournisseurId(),
                    c.fournisseurNom(), jour, depotId, bonDeLivraison, recues, a.membreId(), Instant.now(horloge));
            c.recevoir(parLigne);
            for (Reception.LigneRecue l : recues) {
                if (l.articleId() != null) {
                    stock.entrerAchat(a.tenantId(), l.articleId(), depotId, l.quantite(), l.coutUnitaire(), jour,
                            "Réception " + numero + " · " + c.fournisseurNom(), numero, r.id(), a.membreId());
                }
            }
            receptions.sauvegarder(r);
            commandes.sauvegarder(c);
            return r;
        });
    }

    // ================================================================== factures fournisseurs

    public List<FactureFournisseur> factures(Acces a, UUID fournisseurId) {
        exiger(a, CONSULTER);
        return factures.factures(a.tenantId(), fournisseurId, 300);
    }

    public FactureFournisseur facture(Acces a, UUID id) {
        exiger(a, CONSULTER);
        return factures.facture(a.tenantId(), id).orElseThrow(() -> new ElementIntrouvable("Facture introuvable."));
    }

    /** Enregistre une facture reçue ; l'échéance par défaut suit le délai du fournisseur. */
    public FactureFournisseur enregistrerFacture(Acces a, UUID fournisseurId, String reference, LocalDate date,
                                                 LocalDate echeance, String objet, long ht, long tva, UUID commandeId) {
        exiger(a, PAYER);
        if (date == null || date.isAfter(aujourdHui())) {
            throw new ErreurMetier("Date de la facture : aujourd'hui ou avant.");
        }
        return transaction.executer(() -> {
            Fournisseur f = trouverFournisseur(a.tenantId(), fournisseurId);
            Commande c = commandeId == null ? null : trouverCommande(a.tenantId(), commandeId);
            if (reference != null && factures.existe(a.tenantId(), f.id(), reference.strip())) {
                throw new ErreurMetier("La facture « " + reference.strip() + " » de ce fournisseur est déjà "
                        + "enregistrée.");
            }
            LocalDate limite = echeance != null ? echeance : date.plusDays(f.delaiPaiement());
            FactureFournisseur x = FactureFournisseur.enregistrer(a.tenantId(),
                    numeros.prochain(a.tenantId(), "FF", date.getYear()), f, reference, date, limite, objet, ht, tva, c,
                    a.membreId(), Instant.now(horloge));
            factures.sauvegarder(x);
            return x;
        });
    }

    public void annulerFacture(Acces a, UUID id) {
        exiger(a, PAYER);
        transaction.executer(() -> {
            FactureFournisseur x = verrouillerFacture(a.tenantId(), id);
            x.annuler();
            factures.sauvegarder(x);
        });
    }

    private FactureFournisseur verrouillerFacture(UUID tenantId, UUID id) {
        return factures.verrouillerFacture(tenantId, id).orElseThrow(() -> new ElementIntrouvable("Facture introuvable."));
    }

    // ================================================================== règlements

    public List<ReglementFournisseur> reglements(Acces a, UUID factureId) {
        exiger(a, CONSULTER);
        return reglements.deLaFacture(a.tenantId(), factureId).stream()
                .sorted(Comparator.comparing(ReglementFournisseur::date).thenComparing(ReglementFournisseur::creeLe))
                .toList();
    }

    public List<PortsAchats.Decaissements.Compte> comptesDeDecaissement(Acces a) {
        exiger(a, CONSULTER);
        return decaissements.comptes(a.tenantId());
    }

    public List<PortsAchats.Decaissements.Action> actionsDeDecaissement(Acces a, FactureFournisseur f) {
        exiger(a, CONSULTER);
        if (!a.peut(PAYER) || f.reste() <= 0) {
            return List.of();
        }
        return decaissements.actions(a, f);
    }

    public ReglementFournisseur payer(Acces a, UUID factureId, LocalDate date, long montant, ModeReglement mode,
                                      String reference, UUID compteId) {
        exiger(a, PAYER);
        if (date == null || date.isAfter(aujourdHui())) {
            throw new ErreurMetier("Date du règlement : aujourd'hui ou avant.");
        }
        if (mode == null) {
            throw new ErreurMetier("Choisissez le mode de règlement.");
        }
        List<PortsAchats.Decaissements.Compte> comptes = decaissements.comptes(a.tenantId());
        if (compteId == null && !comptes.isEmpty()) {
            throw new ErreurMetier("Choisissez le compte d'où l'argent est parti.");
        }
        if (compteId != null && comptes.stream().noneMatch(c -> c.id().equals(compteId))) {
            throw new ErreurMetier("Ce compte ne paie pas les fournisseurs.");
        }
        return transaction.executer(() -> enregistrerReglement(a.tenantId(), factureId, date, montant, mode, reference,
                compteId, a.membreId()));
    }

    /**
     * Un paiement constaté par l'application elle-même (payé par PI-SPI depuis la trésorerie), ramené au reste à
     * payer ; rien si la facture est soldée.
     */
    public java.util.Optional<ReglementFournisseur> payerAutomatiquement(UUID tenantId, UUID factureId, LocalDate date,
                                                                         long montant, ModeReglement mode,
                                                                         String reference, UUID compteId) {
        return transaction.executer(() -> {
            FactureFournisseur x = verrouillerFacture(tenantId, factureId);
            long retenu = Math.min(montant, x.reste());
            if (retenu <= 0) {
                return java.util.Optional.<ReglementFournisseur>empty();
            }
            return java.util.Optional.of(enregistrerReglement(tenantId, factureId,
                    date.isBefore(x.date()) ? x.date() : date, retenu, mode, reference, compteId, null));
        });
    }

    private ReglementFournisseur enregistrerReglement(UUID tenantId, UUID factureId, LocalDate date, long montant,
                                                      ModeReglement mode, String reference, UUID compteId,
                                                      UUID creePar) {
        FactureFournisseur x = verrouillerFacture(tenantId, factureId);
        if (date.isBefore(x.date())) {
            throw new ErreurMetier("Le règlement ne peut pas précéder la date de la facture.");
        }
        x.payer(montant);
        ReglementFournisseur r = new ReglementFournisseur(UUID.randomUUID(), tenantId, factureId, date, montant, mode,
                reference, compteId, false, "", creePar, Instant.now(horloge));
        reglements.sauvegarder(r);
        factures.sauvegarder(x);
        decaissements.reglementEnregistre(x, r);
        return r;
    }

    public void annulerReglement(Acces a, UUID reglementId, String motif) {
        exiger(a, PAYER);
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("Indiquez pourquoi le règlement est annulé.");
        }
        transaction.executer(() -> {
            ReglementFournisseur r = reglements.reglement(a.tenantId(), reglementId)
                    .orElseThrow(() -> new ElementIntrouvable("Règlement introuvable."));
            FactureFournisseur x = verrouillerFacture(a.tenantId(), r.factureId());
            ReglementFournisseur annule = r.annuler(motif);
            x.retirer(r.montant());
            reglements.sauvegarder(annule);
            factures.sauvegarder(x);
            decaissements.reglementAnnule(x, annule);
        });
    }

    // ================================================================== dettes et tableau de bord

    /** Les factures fournisseurs à payer, de la plus ancienne échéance à la plus récente. */
    public List<FactureFournisseur> dettes(Acces a) {
        exiger(a, CONSULTER);
        return factures.factures(a.tenantId(), null, 0).stream().filter(f -> f.reste() > 0)
                .sorted(Comparator.comparing(FactureFournisseur::echeance,
                        Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    public record Bilan(long aPayer, long enRetard, int facturesEnRetard, long echeance7Jours,
                        List<Commande> enCours) {
    }

    public Bilan bilan(Acces a) {
        List<FactureFournisseur> dettes = dettes(a);
        LocalDate jour = aujourdHui();
        long aPayer = dettes.stream().mapToLong(FactureFournisseur::reste).sum();
        List<FactureFournisseur> retard = dettes.stream()
                .filter(f -> f.echeance() != null && f.echeance().isBefore(jour)).toList();
        long semaine = dettes.stream().filter(f -> f.echeance() != null && !f.echeance().isBefore(jour)
                && !f.echeance().isAfter(jour.plusDays(7))).mapToLong(FactureFournisseur::reste).sum();
        List<Commande> enCours = commandes.commandes(a.tenantId(), null, 0).stream()
                .filter(Commande::receptionnable).limit(20).toList();
        return new Bilan(aPayer, retard.stream().mapToLong(FactureFournisseur::reste).sum(), retard.size(), semaine,
                enCours);
    }

    static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
