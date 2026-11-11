package com.venach.kauris.kernel.entreprise.domain;

import java.util.List;
import java.util.Optional;

/**
 * Les huit pays de l'UEMOA, avec les identifiants que leurs administrations attribuent aux entreprises et
 * qu'elles demandent sur les documents (factures, déclarations, bulletins de paie).
 * <p>
 * Partout : le RCCM (registre du commerce, droit OHADA) et un identifiant fiscal dont le nom change selon le
 * pays. S'y ajoutent l'organisme de sécurité sociale auprès duquel l'employeur est immatriculé, et quelques
 * particularités (COFI au Sénégal, IDU en Côte d'Ivoire). Ces listes suivent la pratique connue au moment de
 * l'écriture ; une administration peut en changer : c'est ici, et seulement ici, qu'on les met à jour.
 */
public enum Pays {

    BENIN("BJ", "Bénin", "+229", List.of(
            rccm(),
            new ChampLegal("ifu", "IFU", "Identifiant fiscal unique (IFU)",
                    "13 chiffres, délivré par la Direction générale des impôts."),
            new ChampLegal("cnss", "CNSS", "Numéro d'employeur CNSS", "Caisse nationale de sécurité sociale."),
            regime(), centre())),

    BURKINA_FASO("BF", "Burkina Faso", "+226", List.of(
            rccm(),
            new ChampLegal("ifu", "IFU", "Identifiant financier unique (IFU)",
                    "Délivré par la Direction générale des impôts."),
            new ChampLegal("cnss", "CNSS", "Numéro d'employeur CNSS", "Caisse nationale de sécurité sociale."),
            regime(),
            new ChampLegal("centre", "Division fiscale", "Division fiscale de rattachement", ""))),

    COTE_D_IVOIRE("CI", "Côte d'Ivoire", "+225", List.of(
            rccm(),
            new ChampLegal("ncc", "NCC", "Numéro de compte contribuable (NCC)",
                    "Délivré par la Direction générale des impôts."),
            new ChampLegal("idu", "IDU", "Identifiant unique (IDU)",
                    "Délivré à la création par le CEPICI ; il s'ajoute au NCC et au numéro CNPS."),
            new ChampLegal("cnps", "CNPS", "Numéro d'employeur CNPS", "Caisse nationale de prévoyance sociale."),
            regime(), centre())),

    GUINEE_BISSAU("GW", "Guinée-Bissau", "+245", List.of(
            rccm(),
            new ChampLegal("nif", "NIF", "Numéro d'identification fiscale (NIF)",
                    "Número de Identificação Fiscal, délivré par la Direction générale des contributions et impôts."),
            new ChampLegal("inss", "INSS", "Numéro d'employeur INSS", "Instituto Nacional de Segurança Social."),
            regime(), centre())),

    MALI("ML", "Mali", "+223", List.of(
            rccm(),
            new ChampLegal("nif", "NIF", "Numéro d'identification fiscale (NIF)",
                    "Délivré par la Direction générale des impôts."),
            new ChampLegal("inps", "INPS", "Numéro d'employeur INPS", "Institut national de prévoyance sociale."),
            regime(), centre())),

    NIGER("NE", "Niger", "+227", List.of(
            rccm(),
            new ChampLegal("nif", "NIF", "Numéro d'identification fiscale (NIF)",
                    "Délivré par la Direction générale des impôts."),
            new ChampLegal("cnss", "CNSS", "Numéro d'employeur CNSS", "Caisse nationale de sécurité sociale."),
            regime(), centre())),

    SENEGAL("SN", "Sénégal", "+221", List.of(
            rccm(),
            new ChampLegal("ninea", "NINEA", "NINEA",
                    "Numéro d'identification nationale des entreprises et des associations."),
            new ChampLegal("cofi", "COFI", "Code fiscal (COFI)", "Complète le NINEA auprès des impôts."),
            new ChampLegal("ipres", "IPRES", "Numéro d'employeur IPRES",
                    "Institution de prévoyance retraites du Sénégal."),
            new ChampLegal("css", "CSS", "Numéro d'employeur CSS", "Caisse de sécurité sociale."),
            regime(), centre())),

    TOGO("TG", "Togo", "+228", List.of(
            rccm(),
            new ChampLegal("nif", "NIF", "Numéro d'identification fiscale (NIF)",
                    "Délivré par l'Office togolais des recettes (OTR)."),
            new ChampLegal("cnss", "CNSS", "Numéro d'employeur CNSS", "Caisse nationale de sécurité sociale."),
            regime(), centre()));

    /** Les deux identifiants que tout document commercial porte : registre du commerce et numéro fiscal. */
    private static final int ESSENTIELS = 2;

    private final String code;
    private final String nom;
    private final String indicatif;
    private final List<ChampLegal> champs;

    Pays(String code, String nom, String indicatif, List<ChampLegal> champs) {
        this.code = code;
        this.nom = nom;
        this.indicatif = indicatif;
        this.champs = champs;
    }

    private static ChampLegal rccm() {
        return new ChampLegal("rccm", "RCCM", "Registre du commerce et du crédit mobilier (RCCM)",
                "Numéro d'immatriculation au greffe, commun aux pays de l'OHADA.");
    }

    private static ChampLegal regime() {
        return new ChampLegal("regime", "Régime fiscal", "Régime d'imposition",
                "Par exemple : réel, réel simplifié, synthétique.");
    }

    private static ChampLegal centre() {
        return new ChampLegal("centre", "Centre des impôts", "Centre ou service des impôts de rattachement", "");
    }

    /** Code ISO à deux lettres. */
    public String code() {
        return code;
    }

    public String nom() {
        return nom;
    }

    /** Indicatif téléphonique international. */
    public String indicatif() {
        return indicatif;
    }

    /** Les identifiants demandés dans ce pays, dans l'ordre où on les affiche. */
    public List<ChampLegal> champs() {
        return champs;
    }

    public Optional<ChampLegal> champ(String codeChamp) {
        return champs.stream().filter(c -> c.code().equals(codeChamp)).findFirst();
    }

    /** RCCM et identifiant fiscal : ceux qui figurent sur le pied de page de tout document. */
    public List<ChampLegal> champsEssentiels() {
        return champs.subList(0, ESSENTIELS);
    }

    /** L'identifiant fiscal du pays : IFU, NCC, NIF ou NINEA. */
    public ChampLegal identifiantFiscal() {
        return champs.get(1);
    }

    public static Optional<Pays> parCode(String code) {
        for (Pays pays : values()) {
            if (pays.code.equalsIgnoreCase(code)) {
                return Optional.of(pays);
            }
        }
        return Optional.empty();
    }
}
