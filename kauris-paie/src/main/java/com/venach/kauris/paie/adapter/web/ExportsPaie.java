package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.PaysPaie;
import com.venach.kauris.paie.domain.PeriodePaie;

import java.util.ArrayList;
import java.util.List;

/**
 * Les tableaux d'un mois de paie : livre de paie, état des cotisations sociales, état de l'impôt et des taxes sur
 * les salaires, liste des virements. Chacun se télécharge en Excel, CSV ou PDF.
 */
final class ExportsPaie {

    private ExportsPaie() {
    }

    /** Les organismes sociaux du pays, dans l'ordre des colonnes. */
    static List<String> organismesSociaux(PaysPaie pays) {
        return pays == PaysPaie.TOGO ? List.of("CNSS", "AMU") : List.of("CNPS", "CMU");
    }

    /** Les organismes qui reçoivent des taxes de l'employeur sur les salaires (Côte d'Ivoire). */
    static List<String> organismesFiscauxEmployeur(PaysPaie pays) {
        return pays == PaysPaie.COTE_D_IVOIRE ? List.of("DGI", "FDFP") : List.of();
    }

    private static String precision(PeriodePaie p) {
        return p.libelle() + (p.close() ? "" : " (brouillon : mois non clôturé)");
    }

    static Tableau livre(PeriodePaie p, List<Bulletin> b) {
        List<List<Object>> l = new ArrayList<>();
        for (Bulletin x : b) {
            CalculPaie.Resultat r = x.resultat();
            l.add(List.of(x.numero().isEmpty() ? "—" : x.numero(), x.matricule(), x.nomSalarie(), x.poste(),
                    (long) x.joursPayes(), r.brut(), r.cotisationsSalarie(), r.impot(), r.retenues(), r.netAPayer(),
                    r.chargesEmployeur(), r.coutEmployeur()));
        }
        PaieService.Totaux t = PaieService.Totaux.de(b);
        return new Tableau("Livre de paie", precision(p), List.of("Bulletin", "Matricule", "Salarié", "Poste",
                "Jours", "Brut", "Cotisations salarié", p.pays().impot(), "Retenues", "Net à payer",
                "Charges employeur", "Coût total"), l,
                List.of("Total", "", t.salaries() + " salarié(s)", "", "", t.brut(), t.cotisationsSalarie(), t.impot(),
                        t.retenues(), t.net(), t.chargesEmployeur(), t.coutEmployeur()));
    }

    /** CNSS et AMU (Togo), CNPS et CMU (Côte d'Ivoire) : part du salarié, part de l'employeur, total à verser. */
    static Tableau sociale(PeriodePaie p, List<Bulletin> b) {
        List<String> org = organismesSociaux(p.pays());
        List<String> colonnes = new ArrayList<>(List.of("Matricule", "Salarié", "N° " + p.pays().caisseSociale(),
                "Brut soumis"));
        for (String o : org) {
            colonnes.add(o + " salarié");
            colonnes.add(o + " employeur");
        }
        colonnes.add("Total à verser");
        List<List<Object>> l = new ArrayList<>();
        long[] totaux = new long[org.size() * 2 + 2];
        for (Bulletin x : b) {
            CalculPaie.Resultat r = x.resultat();
            List<Object> ligne = new ArrayList<>(List.of(x.matricule(), x.nomSalarie(), x.numeroSocial(),
                    r.brutSoumis()));
            totaux[0] += r.brutSoumis();
            long total = 0;
            int i = 1;
            for (String o : org) {
                long s = r.salarieDe(o);
                long e = r.employeurDe(o);
                ligne.add(s);
                ligne.add(e);
                totaux[i++] += s;
                totaux[i++] += e;
                total += s + e;
            }
            ligne.add(total);
            totaux[i] += total;
            l.add(ligne);
        }
        List<Object> total = new ArrayList<>(List.of("Total", b.size() + " salarié(s)", ""));
        for (long x : totaux) {
            total.add(x);
        }
        return new Tableau("Cotisations sociales", precision(p) + " · " + String.join(" et ", org), colonnes, l,
                total);
    }

    /** IRPP retenu (Togo) ; ITS retenu et taxes de l'employeur sur les salaires (Côte d'Ivoire). */
    static Tableau fiscale(PeriodePaie p, List<Bulletin> b) {
        List<String> fisc = organismesFiscauxEmployeur(p.pays());
        boolean togo = p.pays() == PaysPaie.TOGO;
        List<String> colonnes = new ArrayList<>(List.of("Matricule", "Salarié", "Brut imposable"));
        if (togo) {
            colonnes.add("Revenu annuel imposable");
        }
        colonnes.add(p.pays().impot() + " retenu");
        for (String o : fisc) {
            colonnes.add(o.equals("DGI") ? "Impôt patronal" : "Taxes FDFP");
        }
        colonnes.add("Total à verser");
        List<List<Object>> l = new ArrayList<>();
        long brut = 0;
        long impot = 0;
        long[] parOrg = new long[fisc.size()];
        long general = 0;
        for (Bulletin x : b) {
            CalculPaie.Resultat r = x.resultat();
            List<Object> ligne = new ArrayList<>(List.of(x.matricule(), x.nomSalarie(), r.brutSoumis()));
            if (togo) {
                ligne.add(r.baseImpot());
            }
            ligne.add(r.impot());
            long total = r.impot();
            for (int i = 0; i < fisc.size(); i++) {
                long e = r.employeurDe(fisc.get(i));
                ligne.add(e);
                parOrg[i] += e;
                total += e;
            }
            ligne.add(total);
            brut += r.brutSoumis();
            impot += r.impot();
            general += total;
            l.add(ligne);
        }
        List<Object> total = new ArrayList<>(List.of("Total", b.size() + " salarié(s)", brut));
        if (togo) {
            total.add("");
        }
        total.add(impot);
        for (long x : parOrg) {
            total.add(x);
        }
        total.add(general);
        return new Tableau("Impôt sur les salaires", precision(p) + " · " + p.pays().administrationFiscale(),
                colonnes, l, total);
    }

    /** Les salaires à verser par virement ou Mobile Money : ce que la banque ou l'opérateur doit payer. */
    static Tableau virements(PeriodePaie p, List<Bulletin> b) {
        List<Bulletin> v = PaieService.aVirer(b);
        List<List<Object>> l = new ArrayList<>();
        String libelle = "Salaire " + p.libelle();
        for (Bulletin x : v) {
            l.add(List.of(x.matricule(), x.nomSalarie(), x.modePaiement().libelle(), x.banque(), x.compte(), x.net(),
                    libelle));
        }
        return new Tableau("Ordre de virement des salaires", precision(p), List.of("Matricule", "Bénéficiaire", "Mode",
                "Banque", "Compte ou numéro", "Montant", "Libellé"), l,
                List.of("Total", v.size() + " virement(s)", "", "", "", v.stream().mapToLong(Bulletin::net).sum(), ""));
    }
}
