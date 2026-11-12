package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Les réglages de vente d'une entreprise.
 *
 * @param assujettiTva     faux pour une entreprise qui ne facture pas la TVA (régime de la taxe professionnelle
 *                         unique, micro-entreprise…) : ses documents n'ont pas de TVA et portent {@code mentionTva}
 * @param tauxTva          taux proposé sur les nouveaux articles et lignes (18 dans la plupart des pays UEMOA)
 * @param mentionTva       par exemple « TVA non applicable » ; imprimée quand l'entreprise n'est pas assujettie
 * @param delaiPaiement    échéance par défaut, en jours (0 = comptant)
 * @param validiteDevis    durée de validité par défaut d'un devis, en jours
 * @param conditions       conditions de paiement imprimées sous les factures (pénalités, escompte…)
 */
public record ReglagesVentes(UUID tenantId, boolean assujettiTva, BigDecimal tauxTva, String mentionTva,
                             int delaiPaiement, int validiteDevis, String conditions) {

    public ReglagesVentes {
        tauxTva = Ligne.pourcentage(tauxTva, "Taux de TVA par défaut");
        mentionTva = Client.texte(mentionTva, "Mention de TVA", 200);
        conditions = Client.texte(conditions, "Conditions de paiement", 1000);
        if (delaiPaiement < 0 || delaiPaiement > 365) {
            throw new ErreurMetier("Délai de paiement : entre 0 et 365 jours.");
        }
        if (validiteDevis < 1 || validiteDevis > 365) {
            throw new ErreurMetier("Validité des devis : entre 1 et 365 jours.");
        }
    }

    public static ReglagesVentes parDefaut(UUID tenantId) {
        return new ReglagesVentes(tenantId, true, BigDecimal.valueOf(18), "TVA non applicable", 30, 30, "");
    }

    /** Le taux à proposer : 0 si l'entreprise ne facture pas la TVA. */
    public BigDecimal tauxPropose() {
        return assujettiTva ? tauxTva : BigDecimal.ZERO.setScale(2);
    }
}
