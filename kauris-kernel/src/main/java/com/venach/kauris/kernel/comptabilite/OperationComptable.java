package com.venach.kauris.kernel.comptabilite;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une opération qu'un module transmet à la comptabilité : une facture émise, un règlement, une paie clôturée… Elle
 * est déjà équilibrée (total des débits = total des crédits). Chaque ligne dit son <em>rôle</em> (client, vente de
 * marchandises, TVA collectée, trésorerie…) : la comptabilité choisit le compte selon ses imputations, que
 * l'entreprise peut modifier. Une ligne peut aussi imposer son compte (la paie a ses propres comptes).
 * <p>
 * Le couple {@code origine} + {@code reference} identifie l'opération : la comptabilité ne la reçoit qu'une fois.
 *
 * @param origine   module d'origine : « ventes », « achats », « caisse », « paie », « flotte »…
 * @param reference identifiant stable de l'opération dans son module (« FACTURE:uuid », « REGLEMENT:uuid »…)
 * @param piece     numéro de la pièce justificative (numéro de facture, de bulletin…), pour le lettrage
 */
public record OperationComptable(UUID tenantId, String origine, String reference, LocalDate date, Journal journal,
                                 String piece, String libelle, List<Ligne> lignes) {

    /** Le journal où l'opération s'écrit. */
    public enum Journal {
        VENTES("VE", "Ventes"),
        ACHATS("AC", "Achats"),
        TRESORERIE("TR", "Trésorerie"),
        PAIE("PA", "Paie"),
        OPERATIONS_DIVERSES("OD", "Opérations diverses");

        private final String code;
        private final String libelle;

        Journal(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }
    }

    /**
     * Le rôle d'une ligne : ce qu'elle représente, sans numéro de compte. La comptabilité de l'entreprise associe un
     * compte à chaque rôle (et, si besoin, à chaque précision : le taux de TVA, le mode de paiement, le compte de
     * trésorerie).
     */
    public enum Role {
        CLIENT("Clients"),
        FOURNISSEUR("Fournisseurs"),
        VENTE_MARCHANDISES("Ventes de marchandises"),
        VENTE_SERVICES("Services vendus"),
        PRODUIT_DIVERS("Produits divers"),
        ACHAT_MARCHANDISES("Achats de marchandises"),
        ACHAT_AUTRE("Autres achats et charges"),
        CHARGE_DIVERSE("Charges diverses"),
        TVA_COLLECTEE("TVA collectée"),
        TVA_DEDUCTIBLE("TVA déductible"),
        TRESORERIE("Trésorerie"),
        COMPTE("Compte imposé par le module");

        private final String libelle;

        Role(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /**
     * Une ligne : un montant au débit ou au crédit.
     *
     * @param precision précise le rôle : mode de paiement ou identifiant du compte de trésorerie pour
     *                  {@link Role#TRESORERIE}, taux pour la TVA ; vide sinon
     * @param compte    compte imposé (rôle {@link Role#COMPTE}) ; vide sinon
     * @param tiers     identifiant du tiers (« CLI:uuid », « FRS:uuid », « SAL:uuid ») ; vide sinon
     * @param tiersNom  nom du tiers, pour les comptes auxiliaires
     */
    public record Ligne(Role role, String precision, String compte, String tiers, String tiersNom, String libelle,
                        long debit, long credit) {

        public Ligne {
            if (role == null) {
                throw new ErreurMetier("Ligne comptable sans rôle.");
            }
            precision = precision == null ? "" : precision.strip();
            compte = compte == null ? "" : compte.strip();
            tiers = tiers == null ? "" : tiers.strip();
            tiersNom = tiersNom == null ? "" : tiersNom.strip();
            libelle = libelle == null ? "" : libelle.strip();
            if (debit < 0 || credit < 0 || (debit > 0 && credit > 0)) {
                throw new ErreurMetier("Une ligne comptable porte un montant positif, au débit ou au crédit.");
            }
            if (role == Role.COMPTE && compte.isEmpty()) {
                throw new ErreurMetier("Ligne comptable sans compte.");
            }
        }

        public static Ligne debit(Role role, String precision, long montant) {
            return new Ligne(role, precision, "", "", "", "", montant, 0);
        }

        public static Ligne credit(Role role, String precision, long montant) {
            return new Ligne(role, precision, "", "", "", "", 0, montant);
        }

        /** La même ligne, pour ce tiers. */
        public Ligne pour(String tiers, String nom) {
            return new Ligne(role, precision, compte, tiers, nom, libelle, debit, credit);
        }

        /** La même ligne, avec ce libellé. */
        public Ligne libellee(String texte) {
            return new Ligne(role, precision, compte, tiers, tiersNom, texte, debit, credit);
        }

        /** La précision d'un taux de TVA : « 18 ». */
        public static String taux(BigDecimal taux) {
            return taux == null ? "" : taux.stripTrailingZeros().toPlainString();
        }
    }

    public OperationComptable {
        if (tenantId == null || date == null || journal == null) {
            throw new ErreurMetier("Opération comptable incomplète.");
        }
        origine = texte(origine, "Origine");
        reference = texte(reference, "Référence");
        piece = piece == null ? "" : piece.strip();
        libelle = libelle == null ? "" : libelle.strip();
        // Les lignes à zéro ne s'écrivent pas.
        lignes = (lignes == null ? List.<Ligne>of() : lignes).stream().filter(l -> l.debit() > 0 || l.credit() > 0)
                .toList();
        if (lignes.isEmpty()) {
            throw new ErreurMetier("Opération comptable sans montant.");
        }
        long d = lignes.stream().mapToLong(Ligne::debit).sum();
        long c = lignes.stream().mapToLong(Ligne::credit).sum();
        if (d != c) {
            throw new ErreurMetier("Opération comptable déséquilibrée (" + d + " au débit, " + c + " au crédit).");
        }
    }

    private static String texte(String v, String champ) {
        String s = v == null ? "" : v.strip();
        if (s.isEmpty() || s.length() > 100) {
            throw new ErreurMetier(champ + " de l'opération comptable : 1 à 100 caractères.");
        }
        return s;
    }

    /** Le total au débit (égal au total au crédit). */
    public long montant() {
        return lignes.stream().mapToLong(Ligne::debit).sum();
    }
}
