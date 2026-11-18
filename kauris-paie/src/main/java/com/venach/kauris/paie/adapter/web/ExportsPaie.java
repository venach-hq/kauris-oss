package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.EcrituresPaie;
import com.venach.kauris.paie.domain.PeriodePaie;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Les tableaux d'un mois de paie : livre de paie, état des cotisations par organisme, état de l'impôt et des taxes
 * sur les salaires, ordre de virement, écritures comptables. Les organismes et l'impôt viennent des bulletins (donc
 * des règles du pays) : rien n'est écrit pour un pays en particulier. Chacun se télécharge en Excel, CSV ou PDF.
 */
final class ExportsPaie {

    private ExportsPaie() {
    }

    /** Les organismes sociaux présents sur les bulletins, dans l'ordre. */
    static List<String> organismesSociaux(List<Bulletin> b) {
        Set<String> o = new LinkedHashSet<>();
        b.forEach(x -> o.addAll(x.resultat().organismesSociaux()));
        return List.copyOf(o);
    }

    /** Les organismes des taxes de l'employeur présents sur les bulletins. */
    static List<String> organismesTaxes(List<Bulletin> b) {
        Set<String> o = new LinkedHashSet<>();
        b.forEach(x -> o.addAll(x.resultat().organismesTaxes()));
        return List.copyOf(o);
    }

    /** Le nom de l'impôt du mois (IRPP, ITS…). */
    static String impot(List<Bulletin> b) {
        return b.stream().map(x -> x.resultat().impotNom()).filter(x -> !x.isEmpty()).findFirst().orElse("Impôt");
    }

    private static String precision(PeriodePaie p) {
        return p.libelle() + " · " + p.pays().nom() + (p.close() ? "" : " (brouillon : mois non clôturé)");
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
                "Jours", "Brut", "Cotisations salarié", impot(b), "Retenues", "Net à payer", "Charges employeur",
                "Coût total"), l,
                List.of("Total", "", t.salaries() + " salarié(s)", "", "", t.brut(), t.cotisationsSalarie(), t.impot(),
                        t.retenues(), t.net(), t.chargesEmployeur(), t.coutEmployeur()));
    }

    /** Par organisme social : part du salarié, part de l'employeur, total à verser. */
    static Tableau sociale(PeriodePaie p, List<Bulletin> b) {
        List<String> org = organismesSociaux(b);
        List<String> colonnes = new ArrayList<>(List.of("Matricule", "Salarié", "N° sécurité sociale", "Brut soumis"));
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
        return new Tableau("Cotisations sociales", precision(p) + (org.isEmpty() ? "" : " · " + String.join(", ",
                org)), colonnes, l, total);
    }

    /** Les impôts forfaitaires retenus (TRIMF…), par libellé de code, présents sur les bulletins. */
    static List<String> forfaits(List<Bulletin> b) {
        Set<String> o = new LinkedHashSet<>();
        b.forEach(x -> x.lignes().stream().filter(l -> l.nature() == CalculPaie.Nature.IMPOT
                && !l.code().equals(x.resultat().impotNom())).forEach(l -> o.add(l.code())));
        return List.copyOf(o);
    }

    private static long montantDe(CalculPaie.Resultat r, String code) {
        return r.lignes().stream().filter(l -> l.nature() == CalculPaie.Nature.IMPOT && l.code().equals(code))
                .mapToLong(CalculPaie.Ligne::salarie).sum();
    }

    /** L'impôt retenu, les impôts forfaitaires et les taxes de l'employeur sur les salaires par organisme. */
    static Tableau fiscale(PeriodePaie p, List<Bulletin> b) {
        List<String> taxes = organismesTaxes(b);
        List<String> forfaits = forfaits(b);
        String impot = impot(b);
        List<String> colonnes = new ArrayList<>(List.of("Matricule", "Salarié", "Brut imposable", "Revenu imposable",
                impot + " retenu"));
        forfaits.forEach(x -> colonnes.add(x + " retenu"));
        for (String o : taxes) {
            colonnes.add("Taxes " + o);
        }
        colonnes.add("Total à verser");
        List<List<Object>> l = new ArrayList<>();
        int n = forfaits.size() + taxes.size();
        long[] t = new long[n + 3];
        for (Bulletin x : b) {
            CalculPaie.Resultat r = x.resultat();
            long principal = montantDe(r, r.impotNom());
            List<Object> ligne = new ArrayList<>(List.of(x.matricule(), x.nomSalarie(), r.brutSoumis(), r.baseImpot(),
                    principal));
            long total = principal;
            t[0] += r.brutSoumis();
            t[1] += principal;
            for (int i = 0; i < n; i++) {
                long e = i < forfaits.size() ? montantDe(r, forfaits.get(i))
                        : r.employeurDe(taxes.get(i - forfaits.size()));
                ligne.add(e);
                t[2 + i] += e;
                total += e;
            }
            ligne.add(total);
            t[t.length - 1] += total;
            l.add(ligne);
        }
        List<Object> total = new ArrayList<>(List.of("Total", b.size() + " salarié(s)", t[0], "", t[1]));
        for (int i = 0; i < n; i++) {
            total.add(t[2 + i]);
        }
        total.add(t[t.length - 1]);
        return new Tableau("Impôt et taxes sur les salaires", precision(p), colonnes, l, total);
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

    /** Les écritures de paie (journal des opérations diverses), à importer dans la comptabilité. */
    static Tableau ecritures(PeriodePaie p, List<Bulletin> b) {
        DateTimeFormatter jour = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<List<Object>> l = new ArrayList<>();
        long debit = 0;
        long credit = 0;
        for (EcrituresPaie.Ligne x : EcrituresPaie.de(p, b)) {
            l.add(List.of(jour.format(x.date()), x.journal(), x.piece(), x.compte(), x.libelle(), x.debit(),
                    x.credit()));
            debit += x.debit();
            credit += x.credit();
        }
        return new Tableau("Écritures de paie", precision(p), List.of("Date", "Journal", "Pièce", "Compte", "Libellé",
                "Débit", "Crédit"), l, List.of("Total", "", "", "", "", debit, credit));
    }
}
