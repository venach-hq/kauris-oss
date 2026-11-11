package com.venach.kauris.kernel.document;

import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;

/**
 * Tout ce que le gabarit commun ({@code kauris/document}) met autour du contenu d'un document : en-tête de
 * l'entreprise, titre, pied légal, bas de page, thème. Fabriqué par {@link ProducteurDocuments}.
 *
 * @param logo      le logo en adresse « data: » (le même à l'écran et dans le PDF), ou null
 * @param feuille   les règles CSS : mise en page commune, thème, et à l'écran les polices du serveur
 * @param precision ce qui suit le titre dans le bas de page : une période, un numéro (peut être vide)
 * @param editeLe   « 09/10/2026 à 20:50 »
 * @param lienPdf   à l'écran, l'adresse de la version PDF (bouton « Télécharger le PDF ») ; null sinon
 * @param pdf       vrai pour le rendu PDF
 */
public record Habillage(Entreprise entreprise, ThemeDocument theme, String logo, String feuille, String titre,
                        String precision, String editeLe, String lienPdf, boolean pdf) {

    /** Le bas de chaque page : « ATLANTIS SA — LIVRE-JOURNAL du … — édité le … ». */
    public String basDePage() {
        StringBuilder ligne = new StringBuilder(entreprise.raisonSociale()).append(" — ").append(titre);
        if (precision != null && !precision.isBlank()) {
            ligne.append(' ').append(precision);
        }
        return ligne.append(" — édité le ").append(editeLe).toString();
    }

    public boolean logoAGauche() {
        return theme.logo() == ThemeDocument.PositionLogo.GAUCHE;
    }
}
