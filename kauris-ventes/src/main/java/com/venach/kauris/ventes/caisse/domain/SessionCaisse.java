package com.venach.kauris.ventes.caisse.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Une caisse ouverte par une personne : fond de caisse au départ, tickets encaissés, puis clôture avec les espèces
 * comptées et l'écart. Les comptes disent où chaque mode de paiement est inscrit en trésorerie (null s'il n'y en
 * a pas). Une personne n'a qu'une caisse ouverte à la fois.
 *
 * @param numero « CA-2026-0001 », sans trou par organisation et par année
 * @param fond   espèces mises dans le tiroir à l'ouverture (pas une recette)
 */
public record SessionCaisse(UUID id, UUID tenantId, String numero, UUID membreId, String caissier, LocalDate jour,
                           Instant ouverteLe, long fond, UUID compteEspeces, UUID compteMobile, UUID compteCarte,
                           boolean ouverte, Instant closeLe, String closePar, Long especesComptees, Long ecart,
                           String note) {

    public static final long FOND_MAX = 100_000_000L;

    public SessionCaisse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(numero);
        Objects.requireNonNull(membreId);
        Objects.requireNonNull(jour);
        Objects.requireNonNull(ouverteLe);
        caissier = caissier == null ? "" : caissier.strip();
        note = note == null ? "" : note.strip();
        if (fond < 0 || fond > FOND_MAX) {
            throw new ErreurMetier("Fond de caisse : un montant positif ou nul.");
        }
        if (note.length() > 300) {
            throw new ErreurMetier("Note de clôture : 300 caractères au plus.");
        }
    }

    public static SessionCaisse ouvrir(UUID tenantId, String numero, UUID membreId, String caissier, LocalDate jour,
                                       Instant maintenant, long fond, UUID compteEspeces, UUID compteMobile,
                                       UUID compteCarte) {
        return new SessionCaisse(UUID.randomUUID(), tenantId, numero, membreId, caissier, jour, maintenant, fond,
                compteEspeces, compteMobile, compteCarte, true, null, null, null, null, "");
    }

    /**
     * Clôture : les espèces comptées dans le tiroir, comparées à ce qui devrait y être.
     *
     * @param attendu fond + espèces encaissées
     */
    public SessionCaisse cloturer(long comptees, long attendu, String parQui, String noteDeCloture,
                                  Instant maintenant) {
        if (!ouverte) {
            throw new ErreurMetier("Cette caisse est déjà clôturée.");
        }
        if (comptees < 0) {
            throw new ErreurMetier("Espèces comptées : un montant positif ou nul.");
        }
        return new SessionCaisse(id, tenantId, numero, membreId, caissier, jour, ouverteLe, fond, compteEspeces,
                compteMobile, compteCarte, false, maintenant, parQui, comptees, comptees - attendu, noteDeCloture);
    }
}
