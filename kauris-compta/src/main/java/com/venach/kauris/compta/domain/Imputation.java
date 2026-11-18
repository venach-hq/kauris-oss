package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le compte où s'écrit un rôle des opérations reçues des autres modules. La ligne sans précision vaut pour tout le
 * rôle ; une ligne avec précision (mode de paiement, compte de trésorerie, taux de TVA) la remplace dans ce cas.
 */
public record Imputation(UUID tenantId, Role role, String precision, String compte) {

    public Imputation {
        precision = precision == null ? "" : precision.strip();
        compte = Compte.numero(compte);
    }

    /** Les imputations proposées, conformes au plan SYSCOHADA. */
    public static final List<Imputation> DEFAUT = List.of(
            new Imputation(null, Role.CLIENT, "", "411000"),
            new Imputation(null, Role.FOURNISSEUR, "", "401000"),
            new Imputation(null, Role.VENTE_MARCHANDISES, "", "701000"),
            new Imputation(null, Role.VENTE_SERVICES, "", "706000"),
            new Imputation(null, Role.PRODUIT_DIVERS, "", "758000"),
            new Imputation(null, Role.PRODUIT_DIVERS, "EXCEDENT_CAISSE", "758100"),
            new Imputation(null, Role.ACHAT_MARCHANDISES, "", "601000"),
            new Imputation(null, Role.ACHAT_AUTRE, "", "605000"),
            new Imputation(null, Role.CHARGE_DIVERSE, "", "658000"),
            new Imputation(null, Role.CHARGE_DIVERSE, "MANQUANT_CAISSE", "658100"),
            new Imputation(null, Role.TVA_COLLECTEE, "", "443100"),
            new Imputation(null, Role.TVA_DEDUCTIBLE, "", "445200"),
            new Imputation(null, Role.TRESORERIE, "", "521000"),
            new Imputation(null, Role.TRESORERIE, "ESPECES", "571000"),
            new Imputation(null, Role.TRESORERIE, "MOBILE_MONEY", "552000"),
            new Imputation(null, Role.TRESORERIE, "CHEQUE", "513000"));

    /** Les modes de paiement, précisions de la trésorerie. */
    public static final List<String> MODES = List.of("ESPECES", "VIREMENT", "CHEQUE", "MOBILE_MONEY", "PISPI", "CARTE",
            "AUTRE");

    /** Libellés des précisions connues, pour l'écran. */
    public static final Map<String, String> PRECISIONS = Map.of(
            "ESPECES", "Espèces", "VIREMENT", "Virement", "CHEQUE", "Chèque", "MOBILE_MONEY", "Mobile Money",
            "PISPI", "PI-SPI", "CARTE", "Carte bancaire", "AUTRE", "Autre mode", "MANQUANT_CAISSE",
            "Manquants de caisse", "EXCEDENT_CAISSE", "Excédents de caisse", "FLOTTE", "Versements de la flotte");

    public Imputation pour(UUID tenant) {
        return new Imputation(tenant, role, precision, compte);
    }

    /**
     * Le compte pour ce rôle et cette précision : la ligne précise d'abord, puis la ligne générale. Une précision
     * peut donner plusieurs niveaux, du plus précis au plus général, séparés par « | » (« id-du-compte|ESPECES » :
     * le compte de dépôt s'il a son imputation, sinon le mode de paiement).
     */
    public static Optional<String> compte(List<Imputation> imputations, Role role, String precision) {
        String p = precision == null ? "" : precision.strip();
        for (String niveau : p.split("\\|")) {
            String n = niveau.strip();
            if (n.isEmpty()) {
                continue;
            }
            Optional<String> c = imputations.stream().filter(i -> i.role() == role && i.precision().equals(n))
                    .findFirst().map(Imputation::compte);
            if (c.isPresent()) {
                return c;
            }
        }
        return imputations.stream().filter(i -> i.role() == role && i.precision().isEmpty()).findFirst()
                .map(Imputation::compte);
    }
}
