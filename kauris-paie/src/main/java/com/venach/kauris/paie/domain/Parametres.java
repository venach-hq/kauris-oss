package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les valeurs des paramètres pour un mois donné : celles par défaut, remplacées par les corrections de l'entreprise
 * en vigueur à cette date (la plus récente l'emporte).
 */
public final class Parametres {

    /** Une correction de l'entreprise : à partir de {@code depuis}, ce paramètre vaut {@code valeur}. */
    public record Correction(UUID tenantId, Parametre parametre, LocalDate depuis, BigDecimal valeur, String note) {

        public Correction {
            if (parametre == null || depuis == null || valeur == null) {
                throw new ErreurMetier("Indiquez le paramètre, la date et la valeur.");
            }
            if (valeur.signum() < 0) {
                throw new ErreurMetier(parametre.libelle() + " : la valeur ne peut pas être négative.");
            }
            if (parametre.unite() == Parametre.Unite.TAUX && valeur.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ErreurMetier(parametre.libelle() + " : un taux ne dépasse pas 100 %.");
            }
            if (parametre.unite() != Parametre.Unite.TAUX && valeur.stripTrailingZeros().scale() > 0) {
                throw new ErreurMetier(parametre.libelle() + " : un nombre entier.");
            }
            note = note == null ? "" : note.strip();
            if (note.length() > 200) {
                throw new ErreurMetier("Note : 200 caractères au plus.");
            }
        }
    }

    private final Map<Parametre, BigDecimal> valeurs;

    private Parametres(Map<Parametre, BigDecimal> valeurs) {
        this.valeurs = valeurs;
    }

    /** Les valeurs par défaut, sans correction. */
    public static Parametres parDefaut() {
        return au(LocalDate.MIN, List.of());
    }

    /** Les valeurs en vigueur le jour {@code date}, compte tenu des corrections. */
    public static Parametres au(LocalDate date, Collection<Correction> corrections) {
        Map<Parametre, BigDecimal> v = new EnumMap<>(Parametre.class);
        for (Parametre p : Parametre.values()) {
            v.put(p, p.parDefaut());
        }
        corrections.stream().filter(c -> !c.depuis().isAfter(date))
                .sorted(Comparator.comparing(Correction::depuis))
                .forEach(c -> v.put(c.parametre(), c.valeur()));
        return new Parametres(v);
    }

    public BigDecimal valeur(Parametre p) {
        return valeurs.get(p);
    }

    public long montant(Parametre p) {
        return valeurs.get(p).longValue();
    }

    public int nombre(Parametre p) {
        return valeurs.get(p).intValue();
    }

    /** Vrai si la valeur diffère de celle par défaut. */
    public boolean corrige(Parametre p) {
        return valeurs.get(p).compareTo(p.parDefaut()) != 0;
    }
}
