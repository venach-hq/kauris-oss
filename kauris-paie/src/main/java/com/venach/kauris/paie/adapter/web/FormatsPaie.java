package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.PeriodePaie;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Mises en forme et lectures des écrans de la paie (montants sans décimales, dates et taux à la française). */
public final class FormatsPaie {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")
            .withZone(ZoneOffset.UTC);

    private final Devise devise;

    public FormatsPaie(Devise devise) {
        this.devise = devise == null ? Devise.XOF : devise;
    }

    public String montant(long m) {
        return devise.formater(m);
    }

    /** « 25 000 » sans la monnaie (tableaux). */
    public String nombre(long m) {
        String s = devise.formater(m);
        String fin = " " + devise.symbole();
        return s.endsWith(fin) ? s.substring(0, s.length() - fin.length()) : s;
    }

    /** Un nombre ou rien (case vide d'un bulletin). */
    public String nombreOuRien(long m) {
        return m == 0 ? "" : nombre(m);
    }

    /** « 6,3 % » ; vide si nul. */
    public String taux(BigDecimal t) {
        return t == null ? "" : t.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    /** « 2,5 » (parts). */
    public String decimal(BigDecimal d) {
        return d == null ? "" : d.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** La valeur d'un paramètre selon son unité : « 12,5 % », « 70 000 F CFA », « 6 ». */
    public String parametre(Parametre p, BigDecimal v) {
        return switch (p.unite()) {
            case TAUX -> taux(v);
            case MONTANT -> v.signum() == 0 ? "aucun" : montant(v.longValue());
            case NOMBRE -> v.stripTrailingZeros().toPlainString();
        };
    }

    public String devise() {
        return devise.symbole();
    }

    public String jour(LocalDate d) {
        return d == null ? "" : JOUR.format(d);
    }

    /** « novembre 2026 ». */
    public String mois(YearMonth m) {
        return m == null ? "" : PeriodePaie.libelle(m);
    }

    public String moisDe(LocalDate d) {
        return d == null ? "" : PeriodePaie.libelle(YearMonth.from(d));
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

    public static int lireEntier(String saisie, String champ, int parDefaut) {
        if (saisie == null || saisie.isBlank()) {
            return parDefaut;
        }
        try {
            return Integer.parseInt(saisie.strip());
        } catch (NumberFormatException e) {
            throw new ErreurMetier(champ + " : un nombre entier.");
        }
    }

    /** « 12,5 », « 12.5 », « 70 000 ». */
    public static BigDecimal lireDecimal(String saisie, String champ) {
        String s = saisie == null ? "" : saisie.strip().replace(' ', ' ').replace(" ", "").replace(',', '.')
                .replace("%", "");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ErreurMetier(champ + " : un nombre, par exemple 12,5.");
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

    /** « 2026-11 » (champ « month » du navigateur) ou « 11/2026 ». */
    public static YearMonth lireMois(String saisie, String champ) {
        String s = saisie == null ? "" : saisie.strip();
        try {
            return YearMonth.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return YearMonth.parse(s, DateTimeFormatter.ofPattern("MM/yyyy"));
            } catch (DateTimeParseException e2) {
                throw new ErreurMetier(champ + " : mois invalide (mm/aaaa).");
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
