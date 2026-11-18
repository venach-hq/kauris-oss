package com.venach.kauris.compta.domain;

import java.util.UUID;

/**
 * Les choix comptables de l'entreprise.
 *
 * @param systeme               système normal, ou système minimal de trésorerie (très petites entreprises)
 * @param validationAutomatique vrai pour valider tout de suite les écritures reçues des autres modules ; faux pour
 *                              les laisser au brouillard, à vérifier
 */
public record Parametres(UUID tenantId, Systeme systeme, boolean validationAutomatique) {

    public enum Systeme {
        NORMAL("Système normal",
                "Comptabilité d'engagement complète : bilan, compte de résultat. Pour les PME."),
        MINIMAL("Système minimal de trésorerie",
                "Recettes et dépenses, et états simplifiés : pour les très petites entreprises (chiffre d'affaires "
                        + "sous 60 millions pour le négoce, 40 pour l'artisanat, 30 pour les services).");

        private final String libelle;
        private final String explication;

        Systeme(String libelle, String explication) {
            this.libelle = libelle;
            this.explication = explication;
        }

        public String libelle() {
            return libelle;
        }

        public String explication() {
            return explication;
        }
    }

    public Parametres {
        systeme = systeme == null ? Systeme.NORMAL : systeme;
    }

    public static Parametres defaut(UUID tenantId) {
        return new Parametres(tenantId, Systeme.NORMAL, false);
    }

    public boolean minimal() {
        return systeme == Systeme.MINIMAL;
    }
}
