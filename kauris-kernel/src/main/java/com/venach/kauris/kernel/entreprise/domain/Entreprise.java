package com.venach.kauris.kernel.entreprise.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Les paramètres de l'entreprise : qui elle est légalement, dans quelle monnaie elle travaille, et à quoi
 * ressemblent ses documents. Tous les modules y lisent ce qu'ils impriment en tête et en pied de page.
 *
 * @param capital       capital social, dans la devise principale ; nul s'il n'y en a pas ou n'est pas saisi
 * @param identifiants  code du champ légal du pays → numéro ; seuls les champs remplis y figurent
 * @param devise        la monnaie de l'entreprise (comptes, prix, capital)
 * @param autresDevises les autres monnaies qu'elle utilise (achats, ventes à l'étranger)
 * @param mentions      mention libre de pied de page (agrément, ordre professionnel, banque…)
 */
public record Entreprise(UUID tenantId, String denomination, String sigle, FormeJuridique forme, Long capital,
                         Pays pays, String ville, String adresse, String boitePostale, String telephone,
                         String email, String siteWeb, Map<String, String> identifiants, Devise devise,
                         Set<Devise> autresDevises, ThemeDocument theme, CoordonneesBancaires banque,
                         String mentions, boolean aUnLogo) {

    public static final int DENOMINATION_MAX = 120;
    public static final int COURT_MAX = 60;
    public static final int LONG_MAX = 190;
    public static final int MENTIONS_MAX = 300;

    /** Un numéro et le sigle sous lequel on l'écrit : « NIF » → « 1000123456 ». */
    public record Identifiant(String sigle, String libelle, String valeur) {
    }

    public Entreprise {
        Objects.requireNonNull(tenantId, "tenant requis");
        Objects.requireNonNull(pays, "pays requis");
        Objects.requireNonNull(forme, "forme juridique requise");
        Objects.requireNonNull(devise, "devise requise");
        Objects.requireNonNull(theme, "thème requis");
        banque = banque == null ? CoordonneesBancaires.AUCUNE : banque;
        denomination = Saisie.nom(denomination, "Dénomination", DENOMINATION_MAX);
        sigle = texte(sigle, "Sigle", COURT_MAX);
        ville = texte(ville, "Ville", COURT_MAX);
        adresse = texte(adresse, "Adresse", LONG_MAX);
        boitePostale = texte(boitePostale, "Boîte postale", COURT_MAX);
        telephone = texte(telephone, "Téléphone", COURT_MAX);
        siteWeb = texte(siteWeb, "Site web", LONG_MAX);
        mentions = texte(mentions, "Mentions", MENTIONS_MAX);
        email = email == null || email.isBlank() ? "" : Saisie.email(email);
        if (capital != null && capital < 0) {
            throw new ErreurMetier("Capital social : un montant positif.");
        }
        identifiants = identifiantsDuPays(pays, identifiants);
        Set<Devise> autres = autresDevises == null || autresDevises.isEmpty()
                ? EnumSet.noneOf(Devise.class) : EnumSet.copyOf(autresDevises);
        autres.remove(devise);
        autresDevises = Set.copyOf(autres);
    }

    /** Ce qu'on affiche tant que rien n'a été saisi : le nom de l'organisation, le Togo, le franc CFA. */
    public static Entreprise parDefaut(UUID tenantId, String nomOrganisation) {
        return new Entreprise(tenantId, nomOrganisation, "", FormeJuridique.AUTRE, null, Pays.TOGO, "", "", "", "",
                "", "", Map.of(), Devise.XOF, Set.of(), ThemeDocument.PAR_DEFAUT, CoordonneesBancaires.AUCUNE,
                "", false);
    }

    public Entreprise avecLogo(boolean present) {
        return new Entreprise(tenantId, denomination, sigle, forme, capital, pays, ville, adresse, boitePostale,
                telephone, email, siteWeb, identifiants, devise, autresDevises, theme, banque, mentions, present);
    }

    public Entreprise avecTheme(ThemeDocument nouveau) {
        return new Entreprise(tenantId, denomination, sigle, forme, capital, pays, ville, adresse, boitePostale,
                telephone, email, siteWeb, identifiants, devise, autresDevises, nouveau, banque, mentions, aUnLogo);
    }

    private static String texte(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
        if (propre.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return propre;
    }

    /** Ne garde que les champs que ce pays connaît, remplis, dans l'ordre du pays. */
    private static Map<String, String> identifiantsDuPays(Pays pays, Map<String, String> saisis) {
        Map<String, String> gardes = new LinkedHashMap<>();
        if (saisis != null) {
            for (ChampLegal champ : pays.champs()) {
                String valeur = texte(saisis.get(champ.code()), champ.sigle(), ChampLegal.VALEUR_MAX);
                if (!valeur.isEmpty()) {
                    gardes.put(champ.code(), valeur);
                }
            }
        }
        return java.util.Collections.unmodifiableMap(gardes);
    }

    public String identifiant(String codeChamp) {
        return identifiants.getOrDefault(codeChamp, "");
    }

    /** Les identifiants remplis, dans l'ordre du pays, avec leur sigle. */
    public List<Identifiant> identifiantsRemplis() {
        List<Identifiant> remplis = new ArrayList<>();
        for (ChampLegal champ : pays.champs()) {
            String valeur = identifiants.get(champ.code());
            if (valeur != null) {
                remplis.add(new Identifiant(champ.sigle(), champ.libelle(), valeur));
            }
        }
        return remplis;
    }

    /** Les deux identifiants de l'en-tête des documents : le numéro fiscal, puis le registre du commerce. */
    public List<Identifiant> identifiantsEnTete() {
        List<Identifiant> entete = new ArrayList<>();
        for (ChampLegal champ : pays.champsEssentiels().reversed()) {
            String valeur = identifiants.get(champ.code());
            if (valeur != null) {
                entete.add(new Identifiant(champ.sigle(), champ.libelle(), valeur));
            }
        }
        return entete;
    }

    /** « Boulangerie du Port SARL » : la dénomination suivie du sigle de sa forme. */
    public String raisonSociale() {
        return forme.sigle().isEmpty() || denomination.toUpperCase().endsWith(" " + forme.sigle().toUpperCase())
                ? denomination : denomination + " " + forme.sigle();
    }

    /** « 12 rue du Commerce, BP 1234, Lomé, Togo » avec ce qui est rempli. */
    public String adresseComplete() {
        List<String> parties = new ArrayList<>();
        if (!adresse.isEmpty()) {
            parties.add(adresse);
        }
        if (!boitePostale.isEmpty()) {
            parties.add(boitePostale.toUpperCase().startsWith("BP") ? boitePostale : "BP " + boitePostale);
        }
        if (!ville.isEmpty()) {
            parties.add(ville);
        }
        parties.add(pays.nom());
        return String.join(", ", parties);
    }

    /**
     * La ligne que le droit OHADA demande sur les documents d'une société : dénomination, forme, capital,
     * siège, registre du commerce et identifiant fiscal. Seul ce qui est rempli y figure.
     */
    public String mentionLegale() {
        List<String> parties = new ArrayList<>();
        StringBuilder societe = new StringBuilder(raisonSociale());
        if (forme.aUnCapital() && capital != null && capital > 0) {
            societe.append(" au capital de ").append(devise.formater(capital));
        }
        parties.add(societe.toString());
        parties.add("Siège : " + adresseComplete());
        for (ChampLegal champ : pays.champsEssentiels()) {
            String valeur = identifiants.get(champ.code());
            if (valeur != null) {
                parties.add(champ.sigle() + " " + valeur);
            }
        }
        if (!telephone.isEmpty()) {
            parties.add("Tél. " + telephone);
        }
        if (!email.isEmpty()) {
            parties.add(email);
        }
        return String.join(" · ", parties);
    }

    /**
     * La ligne légale du pied des documents : « ATLANTIS SA • Capital social : 150 000 000 F CFA • NIF : …
     * • RCCM : … », avec ce qui est rempli.
     */
    public String piedLegal() {
        List<String> parties = new ArrayList<>();
        parties.add(raisonSociale());
        if (forme.aUnCapital() && capital != null && capital > 0) {
            parties.add("Capital social : " + devise.formater(capital));
        }
        for (ChampLegal champ : pays.champsEssentiels().reversed()) {
            String valeur = identifiants.get(champ.code());
            if (valeur != null) {
                parties.add(champ.sigle() + " : " + valeur);
            }
        }
        return String.join(" \u2022 ", parties);
    }

    /** Ce qu'il manque pour qu'un document soit complet aux yeux de l'administration du pays. */
    public List<String> manques() {
        List<String> manques = new ArrayList<>();
        if (forme == FormeJuridique.AUTRE) {
            manques.add("la forme juridique");
        }
        if (forme.aUnCapital() && (capital == null || capital == 0)) {
            manques.add("le capital social");
        }
        if (adresse.isEmpty() && boitePostale.isEmpty()) {
            manques.add("l'adresse du siège");
        }
        if (ville.isEmpty()) {
            manques.add("la ville");
        }
        for (ChampLegal champ : pays.champsEssentiels()) {
            if (!identifiants.containsKey(champ.code())) {
                manques.add("le numéro " + champ.sigle());
            }
        }
        return manques;
    }
}
