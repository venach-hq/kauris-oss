package com.venach.kauris.kernel.entreprise.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.ArrayList;
import java.util.List;

/**
 * Le compte bancaire que l'entreprise imprime au pied de ses documents, pour être payée.
 *
 * @param compte numéro du compte : IBAN, RIB ou numéro de compte, tel que la banque l'écrit
 */
public record CoordonneesBancaires(String banque, String titulaire, String compte, String bic, String agence) {

    public static final CoordonneesBancaires AUCUNE = new CoordonneesBancaires("", "", "", "", "");

    public CoordonneesBancaires {
        banque = texte(banque, "Banque", 80);
        titulaire = texte(titulaire, "Titulaire du compte", 120);
        compte = texte(compte, "Numéro de compte", 60);
        bic = texte(bic, "Code BIC / SWIFT", 20).toUpperCase();
        agence = texte(agence, "Agence", 120);
    }

    private static String texte(String valeur, String champ, int max) {
        String propre = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
        if (propre.length() > max) {
            throw new ErreurMetier(champ + " : " + max + " caractères au plus.");
        }
        return propre;
    }

    public boolean renseignees() {
        return !banque.isEmpty() || !compte.isEmpty();
    }

    /** « Ecobank Togo - Titulaire : … - Compte : … - BIC/SWIFT : … - Agence : … », avec ce qui est rempli. */
    public String ligne() {
        List<String> parties = new ArrayList<>();
        if (!banque.isEmpty()) {
            parties.add(banque);
        }
        if (!titulaire.isEmpty()) {
            parties.add("Titulaire : " + titulaire);
        }
        if (!compte.isEmpty()) {
            parties.add((compte.matches("[A-Z]{2}\\d{2}.*") ? "IBAN : " : "Compte : ") + compte);
        }
        if (!bic.isEmpty()) {
            parties.add("BIC/SWIFT : " + bic);
        }
        if (!agence.isEmpty()) {
            parties.add("Agence : " + agence);
        }
        return String.join(" - ", parties);
    }
}
