package com.venach.kauris.kernel.organisation.application.port.out;

import java.util.UUID;

/**
 * Ce que l'organisation a le droit de contenir. Le noyau n'en fixe aucune : c'est l'application qui décide
 * (dans Kauris Cloud, la formule d'abonnement). Sans réponse de l'application, tout est illimité.
 */
public interface LimitesOrganisation {

    /**
     * @param etablissementsOuverts nombre maximal d'établissements ouverts en même temps
     * @param membres               nombre maximal de membres invités ou actifs (les désactivés ne comptent pas)
     */
    record Limites(int etablissementsOuverts, int membres) {

        public static final Limites AUCUNE = new Limites(Integer.MAX_VALUE, Integer.MAX_VALUE);

        public Limites {
            if (etablissementsOuverts < 1 || membres < 1) {
                throw new IllegalArgumentException("Une organisation a au moins un établissement et un membre");
            }
        }
    }

    Limites de(UUID tenantId);

    /** Aucune limite. */
    LimitesOrganisation AUCUNE = tenantId -> Limites.AUCUNE;
}
