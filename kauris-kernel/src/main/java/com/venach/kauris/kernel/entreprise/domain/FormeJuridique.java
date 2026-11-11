package com.venach.kauris.kernel.entreprise.domain;

/** Formes juridiques du droit OHADA, commun aux huit pays, et quelques autres statuts courants. */
public enum FormeJuridique {

    EI("EI", "Entreprise individuelle", false),
    SARL("SARL", "Société à responsabilité limitée", true),
    SARLU("SARLU", "SARL unipersonnelle", true),
    SA("SA", "Société anonyme", true),
    SAU("SAU", "Société anonyme unipersonnelle", true),
    SAS("SAS", "Société par actions simplifiée", true),
    SASU("SASU", "SAS unipersonnelle", true),
    SNC("SNC", "Société en nom collectif", true),
    SCS("SCS", "Société en commandite simple", true),
    GIE("GIE", "Groupement d'intérêt économique", false),
    SCOOPS("SCOOPS", "Société coopérative simplifiée", true),
    COOP_CA("COOP-CA", "Société coopérative avec conseil d'administration", true),
    ASSOCIATION("Association", "Association ou ONG", false),
    ETABLISSEMENT_PUBLIC("Établissement public", "Établissement ou organisme public", false),
    AUTRE("", "Autre", false);

    private final String sigle;
    private final String libelle;
    private final boolean aUnCapital;

    FormeJuridique(String sigle, String libelle, boolean aUnCapital) {
        this.sigle = sigle;
        this.libelle = libelle;
        this.aUnCapital = aUnCapital;
    }

    /** Ce qu'on écrit après la dénomination : « SARL », « SA ». Vide pour « Autre ». */
    public String sigle() {
        return sigle;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai si la forme a un capital social, à mentionner sur les documents. */
    public boolean aUnCapital() {
        return aUnCapital;
    }
}
