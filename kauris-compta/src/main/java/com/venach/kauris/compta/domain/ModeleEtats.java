package com.venach.kauris.compta.domain;

import java.util.List;
import java.util.Map;

/**
 * Les modèles d'états financiers SYSCOHADA révisé : le système normal (bilan AD…DZ, compte de résultat TA…XI) et
 * le système minimal de trésorerie (états simplifiés). Les comptes sont rattachés aux rubriques par préfixe : le plus
 * long préfixe qui correspond l'emporte (6031 avant 603 avant 60).
 *
 * @param resultat     rubriques du compte de résultat
 * @param comptesResultat comptes de charges et de produits → rubrique
 * @param actif        rubriques de l'actif
 * @param passif       rubriques du passif
 * @param comptesBilan comptes de bilan à sens fixe → rubrique
 * @param selonSolde   comptes de tiers et de trésorerie : rubrique d'actif si le solde est débiteur, de passif sinon
 * @param refResultat  rubrique du résultat net dans le compte de résultat
 * @param refResultatPassif rubrique du passif qui reçoit le résultat de l'exercice
 */
public record ModeleEtats(String nom, List<Rubrique> resultat, Map<String, String> comptesResultat,
                          List<Rubrique> actif, List<Rubrique> passif, Map<String, String> comptesBilan,
                          Map<String, String[]> selonSolde, String refResultat, String refResultatPassif,
                          String chargeParDefaut, String produitParDefaut, String[] bilanParDefaut) {

    /** Une ligne d'un état ; {@code formule} : somme signée de références (« +XB+TE-RA ») pour un total. */
    public record Rubrique(String ref, String libelle, Nature nature, String formule) {
        public boolean total() {
            return formule != null;
        }
    }

    public enum Nature { PRODUIT, CHARGE, TOTAL, ACTIF, PASSIF }

    /** Le plus long préfixe de la table qui correspond au numéro, ou null. */
    public static <T> T rattacher(String numero, Map<String, T> table) {
        for (int n = numero.length(); n >= 1; n--) {
            T v = table.get(numero.substring(0, n));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** Les amortissements et dépréciations : en déduction de l'actif (colonne « amortissements »). */
    public static boolean amortissement(String numero) {
        return numero.startsWith("28") || numero.startsWith("29") || numero.startsWith("39")
                || numero.startsWith("49") || numero.startsWith("59");
    }

    private static Rubrique r(String ref, String libelle, Nature nature) {
        return new Rubrique(ref, libelle, nature, null);
    }

    private static Rubrique t(String ref, String libelle, String formule) {
        return new Rubrique(ref, libelle, Nature.TOTAL, formule);
    }

    // ================================================================== système normal

    private static final List<Rubrique> COMPTE_DE_RESULTAT = List.of(
            r("TA", "Ventes de marchandises", Nature.PRODUIT),
            r("RA", "Achats de marchandises", Nature.CHARGE),
            r("RB", "Variation de stocks de marchandises", Nature.CHARGE),
            t("XA", "MARGE COMMERCIALE", "+TA-RA-RB"),
            r("TB", "Ventes de produits fabriqués", Nature.PRODUIT),
            r("TC", "Travaux, services vendus", Nature.PRODUIT),
            r("TD", "Produits accessoires", Nature.PRODUIT),
            t("XB", "CHIFFRE D'AFFAIRES", "+TA+TB+TC+TD"),
            r("TE", "Production stockée (ou déstockage)", Nature.PRODUIT),
            r("TF", "Production immobilisée", Nature.PRODUIT),
            r("TG", "Subventions d'exploitation", Nature.PRODUIT),
            r("TH", "Autres produits", Nature.PRODUIT),
            r("TI", "Transferts de charges d'exploitation", Nature.PRODUIT),
            r("RC", "Achats de matières premières et fournitures liées", Nature.CHARGE),
            r("RD", "Variation de stocks de matières premières et fournitures liées", Nature.CHARGE),
            r("RE", "Autres achats", Nature.CHARGE),
            r("RF", "Variation de stocks d'autres approvisionnements", Nature.CHARGE),
            r("RG", "Transports", Nature.CHARGE),
            r("RH", "Services extérieurs", Nature.CHARGE),
            r("RI", "Impôts et taxes", Nature.CHARGE),
            r("RJ", "Autres charges", Nature.CHARGE),
            t("XC", "VALEUR AJOUTÉE", "+XB+TE+TF+TG+TH+TI-RA-RB-RC-RD-RE-RF-RG-RH-RI-RJ"),
            r("RK", "Charges de personnel", Nature.CHARGE),
            t("XD", "EXCÉDENT BRUT D'EXPLOITATION", "+XC-RK"),
            r("TJ", "Reprises d'amortissements, provisions et dépréciations", Nature.PRODUIT),
            r("RL", "Dotations aux amortissements, aux provisions et dépréciations", Nature.CHARGE),
            t("XE", "RÉSULTAT D'EXPLOITATION", "+XD+TJ-RL"),
            r("TK", "Revenus financiers et assimilés", Nature.PRODUIT),
            r("TL", "Reprises de provisions et dépréciations financières", Nature.PRODUIT),
            r("TM", "Transferts de charges financières", Nature.PRODUIT),
            r("RM", "Frais financiers et charges assimilées", Nature.CHARGE),
            r("RN", "Dotations aux provisions et aux dépréciations financières", Nature.CHARGE),
            t("XF", "RÉSULTAT FINANCIER", "+TK+TL+TM-RM-RN"),
            t("XG", "RÉSULTAT DES ACTIVITÉS ORDINAIRES", "+XE+XF"),
            r("TN", "Produits des cessions d'immobilisations", Nature.PRODUIT),
            r("TO", "Autres produits HAO", Nature.PRODUIT),
            r("RO", "Valeurs comptables des cessions d'immobilisations", Nature.CHARGE),
            r("RP", "Autres charges HAO", Nature.CHARGE),
            t("XH", "RÉSULTAT HORS ACTIVITÉS ORDINAIRES", "+TN+TO-RO-RP"),
            r("RQ", "Participation des travailleurs", Nature.CHARGE),
            r("RS", "Impôts sur le résultat", Nature.CHARGE),
            t("XI", "RÉSULTAT NET", "+XG+XH-RQ-RS"));

    private static final Map<String, String> COMPTES_RESULTAT = Map.ofEntries(
            Map.entry("701", "TA"), Map.entry("601", "RA"), Map.entry("6031", "RB"),
            Map.entry("702", "TB"), Map.entry("703", "TB"), Map.entry("704", "TB"),
            Map.entry("705", "TC"), Map.entry("706", "TC"), Map.entry("709", "TC"), Map.entry("707", "TD"),
            Map.entry("73", "TE"), Map.entry("72", "TF"), Map.entry("71", "TG"), Map.entry("75", "TH"),
            Map.entry("781", "TI"),
            Map.entry("602", "RC"), Map.entry("6032", "RD"),
            Map.entry("604", "RE"), Map.entry("605", "RE"), Map.entry("608", "RE"), Map.entry("609", "RE"),
            Map.entry("6033", "RF"), Map.entry("603", "RF"),
            Map.entry("61", "RG"), Map.entry("62", "RH"), Map.entry("63", "RH"), Map.entry("64", "RI"),
            Map.entry("65", "RJ"), Map.entry("66", "RK"),
            Map.entry("791", "TJ"), Map.entry("798", "TJ"), Map.entry("799", "TJ"),
            Map.entry("68", "RL"), Map.entry("691", "RL"),
            Map.entry("77", "TK"), Map.entry("797", "TL"), Map.entry("787", "TM"),
            Map.entry("67", "RM"), Map.entry("687", "RN"), Map.entry("697", "RN"),
            Map.entry("82", "TN"), Map.entry("84", "TO"), Map.entry("86", "TO"), Map.entry("88", "TO"),
            Map.entry("81", "RO"), Map.entry("83", "RP"), Map.entry("85", "RP"),
            Map.entry("87", "RQ"), Map.entry("89", "RS"));

    private static final List<Rubrique> ACTIF = List.of(
            r("AE", "Frais de développement et de prospection", Nature.ACTIF),
            r("AF", "Brevets, licences, logiciels et droits similaires", Nature.ACTIF),
            r("AG", "Fonds commercial et droit au bail", Nature.ACTIF),
            r("AH", "Autres immobilisations incorporelles", Nature.ACTIF),
            t("AD", "IMMOBILISATIONS INCORPORELLES", "+AE+AF+AG+AH"),
            r("AJ", "Terrains", Nature.ACTIF),
            r("AK", "Bâtiments", Nature.ACTIF),
            r("AL", "Aménagements, agencements et installations", Nature.ACTIF),
            r("AM", "Matériel, mobilier et actifs biologiques", Nature.ACTIF),
            r("AN", "Matériel de transport", Nature.ACTIF),
            t("AI", "IMMOBILISATIONS CORPORELLES", "+AJ+AK+AL+AM+AN"),
            r("AP", "Avances et acomptes versés sur immobilisations", Nature.ACTIF),
            r("AR", "Titres de participation", Nature.ACTIF),
            r("AS", "Autres immobilisations financières", Nature.ACTIF),
            t("AQ", "IMMOBILISATIONS FINANCIÈRES", "+AR+AS"),
            t("AZ", "TOTAL ACTIF IMMOBILISÉ", "+AD+AI+AP+AQ"),
            r("BA", "Actif circulant HAO", Nature.ACTIF),
            r("BB", "Stocks et encours", Nature.ACTIF),
            r("BH", "Fournisseurs, avances versées", Nature.ACTIF),
            r("BI", "Clients", Nature.ACTIF),
            r("BJ", "Autres créances", Nature.ACTIF),
            t("BG", "CRÉANCES ET EMPLOIS ASSIMILÉS", "+BH+BI+BJ"),
            t("BK", "TOTAL ACTIF CIRCULANT", "+BA+BB+BG"),
            r("BQ", "Titres de placement", Nature.ACTIF),
            r("BR", "Valeurs à encaisser", Nature.ACTIF),
            r("BS", "Banques, chèques postaux, caisse et assimilés", Nature.ACTIF),
            t("BT", "TOTAL TRÉSORERIE-ACTIF", "+BQ+BR+BS"),
            r("BU", "Écart de conversion-Actif", Nature.ACTIF),
            t("BZ", "TOTAL GÉNÉRAL ACTIF", "+AZ+BK+BT+BU"));

    private static final List<Rubrique> PASSIF = List.of(
            r("CA", "Capital", Nature.PASSIF),
            r("CB", "Apporteurs capital non appelé (-)", Nature.PASSIF),
            r("CD", "Primes liées au capital social", Nature.PASSIF),
            r("CE", "Écarts de réévaluation", Nature.PASSIF),
            r("CF", "Réserves indisponibles", Nature.PASSIF),
            r("CG", "Réserves libres", Nature.PASSIF),
            r("CH", "Report à nouveau (+ ou -)", Nature.PASSIF),
            r("CJ", "Résultat net de l'exercice (bénéfice + ou perte -)", Nature.PASSIF),
            r("CL", "Subventions d'investissement", Nature.PASSIF),
            r("CM", "Provisions réglementées", Nature.PASSIF),
            t("CP", "TOTAL CAPITAUX PROPRES ET RESSOURCES ASSIMILÉES", "+CA+CB+CD+CE+CF+CG+CH+CJ+CL+CM"),
            r("DA", "Emprunts et dettes financières diverses", Nature.PASSIF),
            r("DB", "Dettes de location-acquisition", Nature.PASSIF),
            r("DC", "Provisions pour risques et charges", Nature.PASSIF),
            t("DD", "TOTAL DETTES FINANCIÈRES ET RESSOURCES ASSIMILÉES", "+DA+DB+DC"),
            t("DF", "TOTAL RESSOURCES STABLES", "+CP+DD"),
            r("DH", "Dettes circulantes HAO", Nature.PASSIF),
            r("DI", "Clients, avances reçues", Nature.PASSIF),
            r("DJ", "Fournisseurs d'exploitation", Nature.PASSIF),
            r("DK", "Dettes fiscales et sociales", Nature.PASSIF),
            r("DM", "Autres dettes", Nature.PASSIF),
            r("DN", "Provisions pour risques à court terme", Nature.PASSIF),
            t("DP", "TOTAL PASSIF CIRCULANT", "+DH+DI+DJ+DK+DM+DN"),
            r("DQ", "Banques, crédits d'escompte", Nature.PASSIF),
            r("DR", "Banques, établissements financiers et crédits de trésorerie", Nature.PASSIF),
            t("DT", "TOTAL TRÉSORERIE-PASSIF", "+DQ+DR"),
            r("DV", "Écart de conversion-Passif", Nature.PASSIF),
            t("DZ", "TOTAL GÉNÉRAL PASSIF", "+DF+DP+DT+DV"));

    private static final Map<String, String> COMPTES_BILAN = Map.ofEntries(
            // Actif immobilisé (brut) — les 28/29 correspondants viennent en amortissements
            Map.entry("211", "AE"), Map.entry("212", "AF"), Map.entry("213", "AF"), Map.entry("214", "AF"),
            Map.entry("215", "AG"), Map.entry("216", "AG"), Map.entry("217", "AH"), Map.entry("218", "AH"), Map.entry("219", "AH"),
            Map.entry("201", "AE"),
            Map.entry("22", "AJ"), Map.entry("231", "AK"), Map.entry("232", "AK"), Map.entry("233", "AK"), Map.entry("237", "AK"),
            Map.entry("239", "AK"), Map.entry("234", "AL"), Map.entry("235", "AL"), Map.entry("238", "AL"),
            Map.entry("24", "AM"), Map.entry("245", "AN"), Map.entry("25", "AP"), Map.entry("26", "AR"), Map.entry("27", "AS"),
            Map.entry("281", "AH"), Map.entry("2811", "AE"), Map.entry("2812", "AF"), Map.entry("2813", "AF"), Map.entry("2814", "AF"),
            Map.entry("2815", "AG"), Map.entry("2816", "AG"), Map.entry("282", "AJ"), Map.entry("283", "AK"),
            Map.entry("2834", "AL"), Map.entry("2835", "AL"), Map.entry("2838", "AL"), Map.entry("284", "AM"), Map.entry("2845", "AN"),
            Map.entry("291", "AH"), Map.entry("292", "AJ"), Map.entry("293", "AK"), Map.entry("294", "AM"), Map.entry("295", "AP"),
            Map.entry("296", "AR"), Map.entry("297", "AS"),
            // Actif circulant
            Map.entry("485", "BA"), Map.entry("488", "BA"),
            Map.entry("3", "BB"),
            Map.entry("50", "BQ"), Map.entry("51", "BR"), Map.entry("590", "BQ"),
            Map.entry("478", "BU"),
            // Capitaux propres et ressources stables
            Map.entry("101", "CA"), Map.entry("102", "CA"), Map.entry("103", "CA"), Map.entry("104", "CA"), Map.entry("108", "CA"),
            Map.entry("109", "CB"), Map.entry("105", "CD"), Map.entry("106", "CE"),
            Map.entry("111", "CF"), Map.entry("112", "CF"), Map.entry("113", "CF"), Map.entry("118", "CG"),
            Map.entry("12", "CH"), Map.entry("13", "CJ"), Map.entry("130", "CH"), Map.entry("14", "CL"), Map.entry("15", "CM"),
            Map.entry("16", "DA"), Map.entry("18", "DA"), Map.entry("17", "DB"), Map.entry("19", "DC"),
            // Passif circulant et trésorerie à sens fixe
            Map.entry("481", "DH"), Map.entry("482", "DH"), Map.entry("484", "DH"),
            Map.entry("499", "DN"), Map.entry("599", "DN"),
            Map.entry("564", "DQ"), Map.entry("565", "DQ"), Map.entry("561", "DR"), Map.entry("566", "DR"),
            Map.entry("479", "DV"));

    private static final Map<String, String[]> COMPTES_SELON_SOLDE = Map.ofEntries(
            Map.entry("409", new String[]{"BH", "DJ"}),
            Map.entry("40", new String[]{"BH", "DJ"}),
            Map.entry("419", new String[]{"BI", "DI"}),
            Map.entry("41", new String[]{"BI", "DI"}),
            Map.entry("491", new String[]{"BI", "BI"}),
            Map.entry("42", new String[]{"BJ", "DK"}),
            Map.entry("43", new String[]{"BJ", "DK"}),
            Map.entry("44", new String[]{"BJ", "DK"}),
            Map.entry("45", new String[]{"BJ", "DM"}),
            Map.entry("46", new String[]{"BJ", "DM"}),
            Map.entry("47", new String[]{"BJ", "DM"}),
            Map.entry("49", new String[]{"BJ", "BJ"}),
            Map.entry("52", new String[]{"BS", "DR"}),
            Map.entry("53", new String[]{"BS", "DR"}),
            Map.entry("54", new String[]{"BS", "DR"}),
            Map.entry("55", new String[]{"BS", "DR"}),
            Map.entry("57", new String[]{"BS", "DR"}),
            Map.entry("58", new String[]{"BS", "DR"}),
            Map.entry("59", new String[]{"BS", "BS"}));

    public static final ModeleEtats NORMAL = new ModeleEtats("Système normal", COMPTE_DE_RESULTAT, COMPTES_RESULTAT,
            ACTIF, PASSIF, COMPTES_BILAN, COMPTES_SELON_SOLDE, "XI", "CJ", "RJ", "TH", new String[]{"BJ", "DM"});

    // ================================================================== système minimal de trésorerie

    public static final ModeleEtats MINIMAL = new ModeleEtats("Système minimal de trésorerie", List.of(
            r("RA", "Ventes de marchandises", Nature.PRODUIT),
            r("RB", "Ventes de produits fabriqués, travaux et services", Nature.PRODUIT),
            r("RC", "Autres recettes", Nature.PRODUIT),
            t("RT", "TOTAL DES RECETTES", "+RA+RB+RC"),
            r("DA", "Achats de marchandises", Nature.CHARGE),
            r("DB", "Achats de matières, fournitures et autres achats", Nature.CHARGE),
            r("DC", "Transports et services extérieurs", Nature.CHARGE),
            r("DD", "Impôts et taxes", Nature.CHARGE),
            r("DE", "Charges de personnel", Nature.CHARGE),
            r("DF", "Frais financiers", Nature.CHARGE),
            r("DG", "Autres charges", Nature.CHARGE),
            t("DT", "TOTAL DES DÉPENSES", "+DA+DB+DC+DD+DE+DF+DG"),
            t("RN", "RÉSULTAT DE L'EXERCICE", "+RT-DT")),
            Map.ofEntries(Map.entry("701", "RA"), Map.entry("70", "RB"), Map.entry("7", "RC"), Map.entry("82", "RC"),
                    Map.entry("84", "RC"), Map.entry("86", "RC"), Map.entry("88", "RC"),
                    Map.entry("601", "DA"), Map.entry("6031", "DA"), Map.entry("60", "DB"), Map.entry("61", "DC"),
                    Map.entry("62", "DC"), Map.entry("63", "DC"), Map.entry("64", "DD"), Map.entry("66", "DE"),
                    Map.entry("67", "DF"), Map.entry("6", "DG"), Map.entry("8", "DG")),
            List.of(
                    r("AA", "Immobilisations", Nature.ACTIF),
                    r("AB", "Stocks", Nature.ACTIF),
                    r("AC", "Créances", Nature.ACTIF),
                    r("AD", "Trésorerie (caisse, banque, Mobile Money)", Nature.ACTIF),
                    t("AZ", "TOTAL ACTIF", "+AA+AB+AC+AD")),
            List.of(
                    r("PA", "Capital et compte de l'exploitant", Nature.PASSIF),
                    r("PB", "Résultat de l'exercice", Nature.PASSIF),
                    r("PC", "Emprunts", Nature.PASSIF),
                    r("PD", "Dettes", Nature.PASSIF),
                    r("PE", "Trésorerie-passif (découverts)", Nature.PASSIF),
                    t("PZ", "TOTAL PASSIF", "+PA+PB+PC+PD+PE")),
            Map.ofEntries(Map.entry("1", "PA"), Map.entry("13", "PB"), Map.entry("130", "PA"), Map.entry("16", "PC"),
                    Map.entry("17", "PC"), Map.entry("18", "PC"), Map.entry("19", "PD"), Map.entry("2", "AA"),
                    Map.entry("3", "AB"), Map.entry("50", "AD"), Map.entry("51", "AD"), Map.entry("561", "PE"),
                    Map.entry("564", "PE"), Map.entry("565", "PE"), Map.entry("566", "PE")),
            Map.ofEntries(Map.entry("4", new String[]{"AC", "PD"}), Map.entry("5", new String[]{"AD", "PE"})),
            "RN", "PB", "DG", "RC", new String[]{"AC", "PD"});

    public static ModeleEtats pour(Parametres.Systeme s) {
        return s == Parametres.Systeme.MINIMAL ? MINIMAL : NORMAL;
    }

    public Rubrique rubrique(String ref) {
        for (List<Rubrique> l : List.of(resultat, actif, passif)) {
            for (Rubrique r : l) {
                if (r.ref().equals(ref)) {
                    return r;
                }
            }
        }
        return null;
    }
}
