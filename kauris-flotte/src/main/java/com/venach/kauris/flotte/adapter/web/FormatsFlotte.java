package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.flotte.domain.Compte;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/** Mises en forme et lectures des écrans de la flotte (montants sans décimales, dates à la française). */
public final class FormatsFlotte {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter JOUR_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    private final Devise devise;

    public FormatsFlotte(Devise devise) {
        this.devise = devise == null ? Devise.XOF : devise;
    }

    public String montant(long m) {
        return devise.formater(m);
    }

    public String montant(Long m) {
        return m == null ? "" : devise.formater(m);
    }

    /** « 25 000 » sans la monnaie (tableaux). */
    public String nombre(long m) {
        String s = devise.formater(m);
        String fin = "\u00a0" + devise.symbole();
        return s.endsWith(fin) ? s.substring(0, s.length() - fin.length()) : s;
    }

    public String devise() {
        return devise.symbole();
    }

    public String jour(LocalDate d) {
        return d == null ? "" : JOUR.format(d);
    }

    public String jourLong(LocalDate d) {
        return d == null ? "" : JOUR_LONG.format(d);
    }

    public String jourCourt(LocalDate d) {
        return d == null ? "" : Compte.jour(d);
    }

    public String instant(Instant i) {
        return i == null ? "" : HEURE.format(i);
    }

    // ------------------------------------------------------------------ lectures

    /** « 25000 », « 25 000 », « 25.000 » ; pas de décimales (le franc CFA n'en a pas). */
    public static long lireMontant(String saisie, String champ) {
        String s = saisie == null ? "" : saisie.strip().replace(' ', ' ').replace(' ', ' ');
        if (!s.matches("\\d+|\\d{1,3}( \\d{3})+|\\d{1,3}(\\.\\d{3})+")) {
            throw new ErreurMetier(champ + " : un montant entier, par exemple 5 000.");
        }
        long v = Long.parseLong(s.replaceAll("[ .]", ""));
        if (v > 1_000_000_000L) {
            throw new ErreurMetier(champ + " : montant trop grand.");
        }
        return v;
    }

    public static Long lireMontantFacultatif(String saisie, String champ) {
        return saisie == null || saisie.isBlank() ? null : lireMontant(saisie, champ);
    }

    public static Integer lireEntier(String saisie, String champ) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(saisie.strip().replaceAll("[ .]", ""));
        } catch (NumberFormatException e) {
            throw new ErreurMetier(champ + " : un nombre entier.");
        }
    }

    public static LocalDate lireDate(String saisie, String champ) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        String s = saisie.strip();
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s, JOUR);   // 15/11/2026, tapé à la main
            } catch (DateTimeParseException e2) {
                throw new ErreurMetier(champ + " : date invalide (jj/mm/aaaa).");
            }
        }
    }

    public static UUID lireId(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier("Choix invalide.");
        }
    }

    public static <E extends Enum<E>> E lireChoix(Class<E> type, String saisie, String champ) {
        try {
            return Enum.valueOf(type, saisie == null ? "" : saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier(champ + " : choisissez dans la liste.");
        }
    }
}
