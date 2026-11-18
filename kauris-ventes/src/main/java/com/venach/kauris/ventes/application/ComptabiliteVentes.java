package com.venach.kauris.ventes.application;

import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Ligne;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.Totaux;
import com.venach.kauris.ventes.domain.TypeDocument;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Les opérations que les ventes transmettent à la comptabilité (par le noyau) : facture et avoir émis (journal des
 * ventes), règlement encaissé (trésorerie). Les ventes de biens et de prestations sont séparées ; une ligne sans
 * article compte comme une prestation.
 */
public final class ComptabiliteVentes {

    public static final String ORIGINE = "ventes";

    private ComptabiliteVentes() {
    }

    public static String tiers(UUID clientId) {
        return "CLI:" + clientId;
    }

    /** La facture ou l'avoir émis. */
    public static OperationComptable document(DocumentVente d, Function<UUID, Optional<Article>> articles) {
        Totaux t = d.totaux();
        long biens = 0;
        long services = 0;
        for (com.venach.kauris.ventes.domain.Ligne l : d.contenu().lignes()) {
            boolean bien = l.articleId() != null && articles.apply(l.articleId())
                    .map(a -> a.type() == Article.Type.BIEN).orElse(false);
            if (bien) {
                biens += l.montantHt();
            } else {
                services += l.montantHt();
            }
        }
        // La remise globale se répartit en proportion ; l'arrondi va sur les prestations (ou les biens).
        BigDecimal remise = d.contenu().remiseGlobale() == null ? BigDecimal.ZERO : d.contenu().remiseGlobale();
        biens -= BigDecimal.valueOf(biens).multiply(remise).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        if (services > 0 || biens == 0) {
            services = t.totalHt() - biens;
        } else {
            biens = t.totalHt();
        }
        boolean avoir = d.type() == TypeDocument.AVOIR;
        String nom = d.client().nom();
        List<Ligne> lignes = new ArrayList<>();
        lignes.add(sens(!avoir, Role.CLIENT, "", t.totalTtc()).pour(tiers(d.client().id()), nom));
        lignes.add(sens(avoir, Role.VENTE_MARCHANDISES, "", biens));
        lignes.add(sens(avoir, Role.VENTE_SERVICES, "", services));
        for (Totaux.LigneTva x : t.tva()) {
            lignes.add(sens(avoir, Role.TVA_COLLECTEE, Ligne.taux(x.taux()), x.montant()));
        }
        String piece = avoir && d.origineNumero() != null && !d.origineNumero().isBlank() ? d.origineNumero()
                : d.numero();
        String libelle = (avoir ? "Avoir " + d.numero() + (piece.equals(d.numero()) ? "" : " sur " + piece)
                : "Facture " + d.numero()) + " · " + nom;
        return new OperationComptable(d.tenantId(), ORIGINE, (avoir ? "AVOIR:" : "FACTURE:") + d.id(),
                d.contenu().date(), OperationComptable.Journal.VENTES, piece, tronque(libelle), lignes);
    }

    /** Le règlement encaissé : trésorerie au débit (compte de dépôt, sinon mode), client au crédit. */
    public static OperationComptable reglement(DocumentVente facture, Reglement r) {
        String precision = (r.compteId() == null ? "" : r.compteId() + "|") + r.mode().name();
        String libelle = "Règlement " + facture.numero() + " · " + facture.client().nom() + " (" + r.mode().libelle()
                + (r.reference() == null || r.reference().isBlank() ? "" : " " + r.reference()) + ")";
        return new OperationComptable(r.tenantId(), ORIGINE, reference(r), r.date(),
                OperationComptable.Journal.TRESORERIE, facture.numero(), tronque(libelle), List.of(
                Ligne.debit(Role.TRESORERIE, precision, r.montant()),
                Ligne.credit(Role.CLIENT, "", r.montant()).pour(tiers(facture.client().id()),
                        facture.client().nom())));
    }

    public static String reference(Reglement r) {
        return "REGLEMENT:" + r.id();
    }

    private static Ligne sens(boolean debit, Role role, String precision, long montant) {
        return debit ? Ligne.debit(role, precision, montant) : Ligne.credit(role, precision, montant);
    }

    private static String tronque(String s) {
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
