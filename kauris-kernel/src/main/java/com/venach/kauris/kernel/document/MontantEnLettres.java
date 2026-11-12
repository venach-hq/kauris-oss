package com.venach.kauris.kernel.document;

import com.venach.kauris.kernel.entreprise.domain.Devise;

/**
 * Un montant en toutes lettres, comme on l'écrit sur un reçu ou un chèque : « deux cent cinquante mille francs
 * CFA », « quatre-vingt-un mille », « soixante et onze ». Orthographe traditionnelle (traits d'union entre
 * dizaines et unités seulement).
 */
public final class MontantEnLettres {

    private static final String[] UNITES = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit",
            "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit",
            "dix-neuf"};
    private static final String[] DIZAINES = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante",
            "soixante", "quatre-vingt", "quatre-vingt"};

    private MontantEnLettres() {
    }

    /** « vingt-cinq mille francs CFA ». */
    public static String francsCfa(long montant) {
        return montant(montant, Devise.XOF);
    }

    /**
     * Le montant suivi du nom de la monnaie : « vingt-cinq mille francs CFA », « cent douze euros ». Montant en
     * unités entières de la monnaie.
     */
    public static String montant(long montant, Devise devise) {
        boolean pluriel = Math.abs(montant) > 1;
        String nom = switch (devise) {
            case XOF, XAF -> pluriel ? "francs CFA" : "franc CFA";
            case GNF -> pluriel ? "francs guinéens" : "franc guinéen";
            case CHF -> pluriel ? "francs suisses" : "franc suisse";
            case EUR -> pluriel ? "euros" : "euro";
            case USD -> pluriel ? "dollars américains" : "dollar américain";
            default -> devise.code();
        };
        // « un million de francs », mais « un million deux cents francs »
        long absolu = Math.abs(montant);
        boolean de = absolu >= 1_000_000 && absolu % 1_000_000 == 0;
        return enLettres(montant) + (de ? " de " : " ") + nom;
    }

    public static String enLettres(long nombre) {
        if (nombre == 0) {
            return "zéro";
        }
        if (nombre < 0) {
            return "moins " + enLettres(-nombre);
        }
        StringBuilder texte = new StringBuilder();
        long milliards = nombre / 1_000_000_000L;
        long millions = (nombre / 1_000_000L) % 1000;
        long milliers = (nombre / 1000) % 1000;
        int reste = (int) (nombre % 1000);
        if (milliards > 0) {
            ajouter(texte, enLettres(milliards) + (milliards > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) {
            ajouter(texte, centaines((int) millions, false) + (millions > 1 ? " millions" : " million"));
        }
        if (milliers > 0) {
            // « mille » est invariable, et « un mille » se dit « mille » ; « cent » ne prend pas de s devant mille.
            ajouter(texte, milliers == 1 ? "mille" : centaines((int) milliers, true) + " mille");
        }
        if (reste > 0) {
            ajouter(texte, centaines(reste, false));
        }
        return texte.toString();
    }

    private static void ajouter(StringBuilder texte, String partie) {
        if (!texte.isEmpty()) {
            texte.append(' ');
        }
        texte.append(partie);
    }

    /** 1 à 999. {@code devantMille} : « quatre-vingt mille », « deux cent mille » (sans s). */
    private static String centaines(int n, boolean devantMille) {
        int c = n / 100;
        int r = n % 100;
        StringBuilder texte = new StringBuilder();
        if (c > 0) {
            texte.append(c == 1 ? "cent" : UNITES[c] + " cent");
            if (c > 1 && r == 0 && !devantMille) {
                texte.append('s');
            }
        }
        if (r > 0) {
            if (!texte.isEmpty()) {
                texte.append(' ');
            }
            texte.append(dizaines(r, devantMille));
        }
        return texte.toString();
    }

    private static String dizaines(int n, boolean devantMille) {
        if (n < 20) {
            return UNITES[n];
        }
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {
            // soixante-dix… soixante-dix-neuf, quatre-vingt-dix… quatre-vingt-dix-neuf ; « soixante et onze »
            String base = DIZAINES[d];
            int fin = 10 + u;
            return d == 7 && u == 1 ? base + " et onze" : base + "-" + UNITES[fin];
        }
        if (d == 8) {
            return u == 0 ? (devantMille ? "quatre-vingt" : "quatre-vingts") : "quatre-vingt-" + UNITES[u];
        }
        if (u == 0) {
            return DIZAINES[d];
        }
        return u == 1 ? DIZAINES[d] + " et un" : DIZAINES[d] + "-" + UNITES[u];
    }
}
