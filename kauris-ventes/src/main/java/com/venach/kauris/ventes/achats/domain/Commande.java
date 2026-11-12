package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.Totaux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un bon de commande à un fournisseur. Brouillon modifiable ; émis, il prend son numéro (BC-AAAA-NNNN) et ne se
 * modifie plus. Les réceptions comptent ce qui est arrivé, ligne par ligne.
 */
public final class Commande {

    public enum Statut {
        BROUILLON("Brouillon"), ENVOYEE("Envoyée"), RECUE_EN_PARTIE("Reçue en partie"), RECUE("Reçue"),
        ANNULEE("Annulée");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Une ligne commandée ; {@code recue} : quantité déjà arrivée. */
    public record LigneCommande(UUID articleId, String designation, BigDecimal quantite, String unite,
                                long prixUnitaire, BigDecimal tauxTva, BigDecimal recue) {

        public LigneCommande {
            Ligne controle = new Ligne(articleId, designation, quantite, unite, prixUnitaire, BigDecimal.ZERO, tauxTva);
            designation = controle.designation();
            quantite = controle.quantite();
            unite = controle.unite();
            tauxTva = controle.tauxTva();
            recue = recue == null ? BigDecimal.ZERO : recue;
        }

        public Ligne ligne() {
            return new Ligne(articleId, designation, quantite, unite, prixUnitaire, BigDecimal.ZERO, tauxTva);
        }

        public BigDecimal reste() {
            return quantite.subtract(recue).max(BigDecimal.ZERO);
        }

        LigneCommande avecRecue(BigDecimal q) {
            return new LigneCommande(articleId, designation, quantite, unite, prixUnitaire, tauxTva, recue.add(q));
        }
    }

    public record Etat(UUID id, UUID tenantId, String numero, Statut statut, UUID fournisseurId,
                       String fournisseurNom, LocalDate date, LocalDate livraisonPrevue, String objet, String notes,
                       List<LigneCommande> lignes, UUID creePar, Instant creeLe, Instant emiseLe) {
    }

    private final UUID id;
    private final UUID tenantId;
    private final UUID creePar;
    private final Instant creeLe;
    private String numero;
    private Statut statut;
    private UUID fournisseurId;
    private String fournisseurNom;
    private LocalDate date;
    private LocalDate livraisonPrevue;
    private String objet;
    private String notes;
    private List<LigneCommande> lignes;
    private Instant emiseLe;

    private Commande(Etat e) {
        this.id = Objects.requireNonNull(e.id());
        this.tenantId = Objects.requireNonNull(e.tenantId());
        this.creePar = e.creePar();
        this.creeLe = Objects.requireNonNull(e.creeLe());
        this.numero = e.numero();
        this.statut = Objects.requireNonNull(e.statut());
        this.emiseLe = e.emiseLe();
        definir(e.fournisseurId(), e.fournisseurNom(), e.date(), e.livraisonPrevue(), e.objet(), e.notes(),
                e.lignes());
    }

    public static Commande brouillon(UUID tenantId, Fournisseur f, LocalDate date, LocalDate livraison, String objet,
                                     String notes, List<LigneCommande> lignes, UUID creePar, Instant maintenant) {
        exigerActif(f);
        return new Commande(new Etat(UUID.randomUUID(), tenantId, null, Statut.BROUILLON, f.id(), f.nom(), date,
                livraison, objet, notes, lignes, creePar, maintenant, null));
    }

    public static Commande reconstituer(Etat e) {
        return new Commande(e);
    }

    private static void exigerActif(Fournisseur f) {
        if (!f.actif()) {
            throw new ErreurMetier("Ce fournisseur est archivé : réactivez-le d'abord.");
        }
    }

    private void definir(UUID fournisseur, String nomFournisseur, LocalDate jour, LocalDate livraison, String texte,
                         String remarques, List<LigneCommande> nouvelles) {
        this.fournisseurId = Objects.requireNonNull(fournisseur);
        this.fournisseurNom = nomFournisseur == null ? "" : nomFournisseur;
        this.date = Objects.requireNonNull(jour, "date");
        if (livraison != null && livraison.isBefore(jour)) {
            throw new ErreurMetier("La livraison prévue ne peut pas précéder la date de la commande.");
        }
        this.livraisonPrevue = livraison;
        this.objet = Textes.texte(texte, "Objet", 200);
        this.notes = Textes.texte(remarques, "Notes", 1000);
        this.lignes = nouvelles == null ? List.of() : List.copyOf(nouvelles);
        if (this.lignes.size() > 200) {
            throw new ErreurMetier("Une commande a 200 lignes au plus.");
        }
    }

    public void modifier(Fournisseur f, LocalDate jour, LocalDate livraison, String texte, String remarques,
                         List<LigneCommande> nouvelles) {
        if (statut != Statut.BROUILLON) {
            throw new ErreurMetier("Une commande envoyée ne se modifie plus : annulez-la et refaites-en une.");
        }
        exigerActif(f);
        definir(f.id(), f.nom(), jour, livraison, texte, remarques, nouvelles);
    }

    public void emettre(String numeroAttribue, Fournisseur f, Instant maintenant) {
        if (statut != Statut.BROUILLON) {
            throw new ErreurMetier("Cette commande est déjà envoyée.");
        }
        if (lignes.isEmpty()) {
            throw new ErreurMetier("Ajoutez au moins une ligne avant d'envoyer la commande.");
        }
        exigerActif(f);
        this.numero = Objects.requireNonNull(numeroAttribue);
        this.fournisseurNom = f.nom();
        this.statut = Statut.ENVOYEE;
        this.emiseLe = maintenant;
    }

    /**
     * Ajoute ce qui est arrivé, par rang de ligne. Recevoir plus que commandé est permis (le fournisseur a livré
     * plus) ; la commande est reçue quand chaque ligne l'est entièrement.
     */
    public void recevoir(List<BigDecimal> quantitesParLigne) {
        if (statut != Statut.ENVOYEE && statut != Statut.RECUE_EN_PARTIE) {
            throw new ErreurMetier("Seule une commande envoyée se réceptionne.");
        }
        if (quantitesParLigne.size() != lignes.size()) {
            throw new IllegalArgumentException("Nombre de lignes reçues incohérent");
        }
        List<LigneCommande> apres = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            BigDecimal q = quantitesParLigne.get(i) == null ? BigDecimal.ZERO : quantitesParLigne.get(i);
            if (q.signum() < 0) {
                throw new ErreurMetier("Une quantité reçue ne peut pas être négative.");
            }
            apres.add(lignes.get(i).avecRecue(q));
        }
        this.lignes = List.copyOf(apres);
        boolean tout = lignes.stream().allMatch(l -> l.reste().signum() == 0);
        this.statut = tout ? Statut.RECUE : Statut.RECUE_EN_PARTIE;
    }

    /** Une commande envoyée sans rien de reçu s'annule ; reçue en partie, elle se clôt (le reste n'arrivera pas). */
    public void annulerOuSolder() {
        switch (statut) {
            case ENVOYEE -> statut = Statut.ANNULEE;
            case RECUE_EN_PARTIE -> statut = Statut.RECUE;
            default -> throw new ErreurMetier("Cette commande est " + statut.libelle().toLowerCase()
                    + " : rien à annuler.");
        }
    }

    public Totaux totaux() {
        return Totaux.de(lignes.stream().map(LigneCommande::ligne).toList(), BigDecimal.ZERO);
    }

    public boolean modifiable() {
        return statut == Statut.BROUILLON;
    }

    public boolean receptionnable() {
        return statut == Statut.ENVOYEE || statut == Statut.RECUE_EN_PARTIE;
    }

    public String intitule() {
        return numero == null ? "Commande (brouillon)" : "Commande " + numero;
    }

    public Etat etat() {
        return new Etat(id, tenantId, numero, statut, fournisseurId, fournisseurNom, date, livraisonPrevue, objet,
                notes, lignes, creePar, creeLe, emiseLe);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String numero() { return numero; }

    public Statut statut() { return statut; }

    public UUID fournisseurId() { return fournisseurId; }

    public String fournisseurNom() { return fournisseurNom; }

    public LocalDate date() { return date; }

    public LocalDate livraisonPrevue() { return livraisonPrevue; }

    public String objet() { return objet; }

    public String notes() { return notes; }

    public List<LigneCommande> lignes() { return lignes; }

    public UUID creePar() { return creePar; }

    public Instant creeLe() { return creeLe; }
}
