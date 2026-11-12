package com.venach.kauris.ventes.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Devise;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mise en forme et lecture des montants, quantités, pourcentages et dates des ventes, dans la monnaie de
 * l'entreprise (montants entiers : décision 38, les décimales sont refusées plutôt que mal lues).
 */
public final class FormatsVentes {

    public static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter JOUR_HEURE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRENCH).withZone(ZoneOffset.UTC);
    /** Chiffres seuls, ou groupés par trois avec des espaces ou des points. */
    private static final Pattern MONTANT = Pattern.compile("\\d+|\\d{1,3}( \\d{3})+|\\d{1,3}(\\.\\d{3})+");
    /** Un nombre avec au plus trois décimales, virgule ou point : « 2 », « 2,5 », « 0.125 ». */
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,12}([.,]\\d{1,3})?");

    private final Devise devise;

    public FormatsVentes(Devise devise) {
        this.devise = devise == null ? Devise.XOF : devise;
    }

    public Devise devise() {
        return devise;
    }

    /** « 1 250 000 F CFA ». */
    public String montant(long valeur) {
        return devise.formater(valeur);
    }

    /** « 1 250 000 », sans la monnaie (colonnes de tableaux). */
    public String nombre(long valeur) {
        String avec = devise.formater(valeur);
        return avec.substring(0, avec.length() - devise.symbole().length() - 1);
    }

    /** « 2 », « 2,5 », « 18 % » sans zéros inutiles. */
    public String decimal(BigDecimal valeur) {
        if (valeur == null) {
            return "";
        }
        BigDecimal propre = valeur.stripTrailingZeros();
        return (propre.scale() < 0 ? propre.setScale(0) : propre).toPlainString().replace('.', ',');
    }

    public String pourcent(BigDecimal valeur) {
        return decimal(valeur) + " %";
    }

    public String jour(LocalDate date) {
        return date == null ? "" : JOUR.format(date);
    }

    public String instant(Instant quand) {
        return quand == null ? "" : JOUR_HEURE.format(quand);
    }

    // ------------------------------------------------------------------ lecture des formulaires

    /** « 1 000 000 », « 1.000.000 » ou « 1000000 » → 1000000 ; « 25 000,50 » est refusé. */
    public static long lireMontant(String saisi, String champ) {
        String propre = saisi == null ? "" : saisi.strip().replaceAll("[\\u00a0\\u202f]", " ");
        if (!MONTANT.matcher(propre).matches()) {
            throw new ErreurMetier(champ + " : un montant en chiffres, sans décimales (par exemple 25 000).");
        }
        String chiffres = propre.replaceAll("[ .]", "");
        if (chiffres.length() > 13) {
            throw new ErreurMetier(champ + " : montant trop grand.");
        }
        return Long.parseLong(chiffres);
    }

    /** Quantité ou pourcentage : « 2,5 » ou « 2.5 » ; vide → la valeur par défaut. */
    public static BigDecimal lireDecimal(String saisi, String champ, BigDecimal parDefaut) {
        String propre = saisi == null ? "" : saisi.strip().replaceAll("[\\s\\u00a0\\u202f%]", "");
        if (propre.isEmpty()) {
            return parDefaut;
        }
        if (!DECIMAL.matcher(propre).matches()) {
            throw new ErreurMetier(champ + " : un nombre, avec au plus trois décimales (par exemple 2,5).");
        }
        return new BigDecimal(propre.replace(',', '.'));
    }

    public static int lireEntier(String saisi, String champ, int parDefaut) {
        String propre = saisi == null ? "" : saisi.strip();
        if (propre.isEmpty()) {
            return parDefaut;
        }
        if (!propre.matches("\\d{1,4}")) {
            throw new ErreurMetier(champ + " : un nombre entier.");
        }
        return Integer.parseInt(propre);
    }

    /** Date du formulaire (aaaa-mm-jj) ; vide → null. */
    public static LocalDate lireDate(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(saisie.strip());
        } catch (DateTimeParseException e) {
            throw new ErreurMetier("Date illisible : " + saisie);
        }
    }

    /** Identifiant facultatif du formulaire ; vide → null. */
    public static UUID lireId(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier("Choix inconnu.");
        }
    }

    public static <E extends Enum<E>> E lireChoix(Class<E> type, String saisie, String champ) {
        try {
            return Enum.valueOf(type, saisie == null ? "" : saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier(champ + " : choix inconnu.");
        }
    }
}
