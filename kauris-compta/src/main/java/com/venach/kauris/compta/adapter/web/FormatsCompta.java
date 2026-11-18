package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Devise;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Mises en forme et lectures des écrans de la comptabilité (montants sans décimales, dates à la française). */
public final class FormatsCompta {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")
            .withZone(ZoneOffset.UTC);
    private static final String[] MOIS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre"};

    private final Devise devise;

    public FormatsCompta(Devise devise) {
        this.devise = devise == null ? Devise.XOF : devise;
    }

    public String montant(long m) {
        return devise.formater(m);
    }

    /** « 25 000 » sans la monnaie (tableaux). */
    public String nombre(long m) {
        String s = devise.formater(m);
        String fin = " " + devise.symbole();
        return s.endsWith(fin) ? s.substring(0, s.length() - fin.length()) : s;
    }

    /** Un nombre, ou rien pour zéro (colonnes débit et crédit). */
    public String nombreOuRien(long m) {
        return m == 0 ? "" : nombre(m);
    }

    /** Un solde : « 25 000 D » ou « 25 000 C ». */
    public String solde(long s) {
        return s == 0 ? "0" : nombre(Math.abs(s)) + (s > 0 ? " D" : " C");
    }

    public String devise() {
        return devise.symbole();
    }

    public String jour(LocalDate d) {
        return d == null ? "" : JOUR.format(d);
    }

    /** « novembre 2026 ». */
    public String mois(YearMonth m) {
        return m == null ? "" : MOIS[m.getMonthValue() - 1] + " " + m.getYear();
    }

    public String instant(Instant i) {
        return i == null ? "" : HEURE.format(i);
    }

    // ------------------------------------------------------------------ lectures

    /** Un montant entier ; vide = 0. */
    public static long lireMontant(String saisie, String champ) {
        String s = saisie == null ? "" : saisie.strip().replace(' ', ' ').replace(' ', ' ');
        if (s.isEmpty()) {
            return 0;
        }
        if (!s.matches("\\d+|\\d{1,3}( \\d{3})+|\\d{1,3}(\\.\\d{3})+")) {
            throw new ErreurMetier(champ + " : un montant entier, par exemple 5 000.");
        }
        long v = Long.parseLong(s.replaceAll("[ .]", ""));
        if (v > 1_000_000_000_000L) {
            throw new ErreurMetier(champ + " : montant trop grand.");
        }
        return v;
    }

    /** Une date (aaaa-mm-jj ou jj/mm/aaaa) ; {@code parDefaut} si vide. */
    public static LocalDate lireDate(String saisie, String champ, LocalDate parDefaut) {
        if (saisie == null || saisie.isBlank()) {
            return parDefaut;
        }
        String s = saisie.strip();
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s, JOUR);
            } catch (DateTimeParseException e2) {
                throw new ErreurMetier(champ + " : date invalide (jj/mm/aaaa).");
            }
        }
    }

    public static YearMonth lireMois(String saisie, String champ, YearMonth parDefaut) {
        if (saisie == null || saisie.isBlank()) {
            return parDefaut;
        }
        try {
            return YearMonth.parse(saisie.strip());
        } catch (DateTimeParseException e) {
            throw new ErreurMetier(champ + " : mois invalide (aaaa-mm).");
        }
    }

    public static int lireAnnee(String saisie, int parDefaut) {
        if (saisie == null || saisie.isBlank()) {
            return parDefaut;
        }
        try {
            int a = Integer.parseInt(saisie.strip());
            if (a < 1990 || a > 2200) {
                throw new ErreurMetier("Année invalide.");
            }
            return a;
        } catch (NumberFormatException e) {
            throw new ErreurMetier("Année invalide.");
        }
    }

    public static UUID lireId(String saisie) {
        try {
            return UUID.fromString(saisie == null ? "" : saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier("Identifiant invalide.");
        }
    }

    public static <E extends Enum<E>> E lireChoix(Class<E> type, String saisie, String champ) {
        try {
            return Enum.valueOf(type, saisie == null ? "" : saisie.strip());
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier(champ + " : choix invalide.");
        }
    }
}
