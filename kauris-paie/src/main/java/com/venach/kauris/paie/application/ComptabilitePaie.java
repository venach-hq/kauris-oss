package com.venach.kauris.paie.application;

import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Ligne;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.EcrituresPaie;
import com.venach.kauris.paie.domain.ModePaiement;
import com.venach.kauris.paie.domain.PeriodePaie;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Les opérations que la paie transmet à la comptabilité : un bulletin clôturé (journal de paie, avec les comptes
 * des règles de paie) et le paiement du salaire (trésorerie). Les comptes de personnel (42…) portent le salarié en
 * tiers, pour lettrer le salaire dû avec son paiement.
 */
public final class ComptabilitePaie {

    public static final String ORIGINE = "paie";

    private ComptabilitePaie() {
    }

    public static String tiers(Bulletin b) {
        return "SAL:" + b.salarieId();
    }

    public static String reference(Bulletin b) {
        return "BULLETIN:" + b.id();
    }

    public static String referencePaiement(Bulletin b) {
        return "PAIEMENT:" + b.id();
    }

    public static OperationComptable bulletin(PeriodePaie p, Bulletin b) {
        List<Ligne> lignes = new ArrayList<>();
        for (EcrituresPaie.Ligne l : EcrituresPaie.de(p, List.of(b))) {
            Ligne x = new Ligne(Role.COMPTE, "", l.compte(), "", "", l.libelle(), l.debit(), l.credit());
            lignes.add(l.compte().startsWith("42") ? x.pour(tiers(b), b.nomSalarie()) : x);
        }
        return new OperationComptable(b.tenantId(), ORIGINE, reference(b), p.mois().atEndOfMonth(),
                OperationComptable.Journal.PAIE, b.numero(), tronque("Paie " + p.libelle() + " · " + b.nomSalarie()),
                lignes);
    }

    /** Le salaire payé : le net à payer soldé contre la trésorerie (selon le mode de paiement du salarié). */
    public static OperationComptable paiement(PeriodePaie p, Bulletin b, LocalDate jour) {
        String mode = switch (b.modePaiement() == null ? ModePaiement.VIREMENT : b.modePaiement()) {
            case ESPECES -> "ESPECES";
            case MOBILE_MONEY -> "MOBILE_MONEY";
            case CHEQUE -> "CHEQUE";
            default -> "VIREMENT";
        };
        String compteNet = b.resultat().compteNet() == null || b.resultat().compteNet().isEmpty() ? "422000"
                : b.resultat().compteNet();
        return new OperationComptable(b.tenantId(), ORIGINE, referencePaiement(b), jour,
                OperationComptable.Journal.TRESORERIE, b.numero(), tronque("Salaire " + p.libelle() + " · "
                + b.nomSalarie()), List.of(
                new Ligne(Role.COMPTE, "", compteNet, tiers(b), b.nomSalarie(), "Net à payer", b.net(), 0),
                Ligne.credit(Role.TRESORERIE, mode, b.net())));
    }

    private static String tronque(String s) {
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
