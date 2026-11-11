package com.venach.kauris.kernel.entreprise.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Les monnaies qu'une entreprise de l'UEMOA rencontre : la sienne, celles des voisins, celles du commerce. */
public enum Devise {

    XOF("Franc CFA (UEMOA)", "F CFA", 0),
    XAF("Franc CFA (CEMAC)", "FCFA", 0),
    EUR("Euro", "€", 2),
    USD("Dollar américain", "$", 2),
    GBP("Livre sterling", "£", 2),
    CHF("Franc suisse", "CHF", 2),
    CAD("Dollar canadien", "$ CA", 2),
    CNY("Yuan chinois", "¥", 2),
    NGN("Naira nigérian", "₦", 2),
    GHS("Cedi ghanéen", "GH₵", 2),
    GNF("Franc guinéen", "FG", 0),
    MAD("Dirham marocain", "DH", 2),
    CVE("Escudo cap-verdien", "Esc", 2),
    GMD("Dalasi gambien", "D", 2),
    MRU("Ouguiya mauritanien", "UM", 2);

    private final String nom;
    private final String symbole;
    private final int decimales;

    Devise(String nom, String symbole, int decimales) {
        this.nom = nom;
        this.symbole = symbole;
        this.decimales = decimales;
    }

    /** Code ISO 4217. */
    public String code() {
        return name();
    }

    public String nom() {
        return nom;
    }

    public String symbole() {
        return symbole;
    }

    public int decimales() {
        return decimales;
    }

    /** « 25 000 F CFA », « 1 250,50 € » : milliers séparés par une espace insécable, virgule décimale. */
    public String formaterDecimal(BigDecimal montant) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.ROOT);
        symboles.setGroupingSeparator('\u00a0');
        symboles.setDecimalSeparator(',');
        String motif = decimales == 0 ? "#,##0" : "#,##0." + "0".repeat(decimales);
        return new DecimalFormat(motif, symboles).format(montant.setScale(decimales, RoundingMode.HALF_UP))
                + "\u00a0" + symbole;
    }

    public String formater(long montant) {
        return formaterDecimal(BigDecimal.valueOf(montant));
    }
}
