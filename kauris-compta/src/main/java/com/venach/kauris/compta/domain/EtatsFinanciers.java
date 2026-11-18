package com.venach.kauris.compta.domain;

import com.venach.kauris.compta.domain.ModeleEtats.Nature;
import com.venach.kauris.compta.domain.ModeleEtats.Rubrique;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Le bilan et le compte de résultat, calculés à partir des soldes des comptes (débit − crédit) selon un modèle.
 * Le résultat de l'exercice (charges et produits non encore soldés) s'ajoute au passif.
 */
public record EtatsFinanciers(ModeleEtats modele, List<Ligne> resultat, List<Ligne> actif, List<Ligne> passif,
                              long resultatNet, long totalActif, long totalPassif,
                              Map<String, List<String>> comptesParRubrique) {

    /**
     * Une ligne d'un état. À l'actif : {@code brut}, {@code amortissements} et {@code net} ; ailleurs, seul
     * {@code net} compte.
     */
    public record Ligne(Rubrique rubrique, long brut, long amortissements, long net) {
        public boolean total() {
            return rubrique.total();
        }
    }

    public boolean equilibre() {
        return totalActif == totalPassif;
    }

    public static EtatsFinanciers de(ModeleEtats m, Map<String, Long> soldes) {
        Map<String, Long> net = new HashMap<>();
        Map<String, Long> brut = new HashMap<>();
        Map<String, Long> amort = new HashMap<>();
        Map<String, List<String>> comptes = new TreeMap<>();
        long resultat = 0;
        for (Map.Entry<String, Long> e : new TreeMap<>(soldes).entrySet()) {
            String compte = e.getKey();
            long solde = e.getValue();
            if (solde == 0) {
                continue;
            }
            String ref;
            switch (Compte.natureDe(compte)) {
                case CHARGE -> {
                    ref = defaut(ModeleEtats.rattacher(compte, m.comptesResultat()), m.chargeParDefaut());
                    // Un compte de charge rattaché à une rubrique de produits (609 en « autres achats »…) garde
                    // son signe : la rubrique additionne des montants signés.
                    net.merge(ref, signe(m, ref, solde), Long::sum);
                    resultat -= solde;
                }
                case PRODUIT -> {
                    ref = defaut(ModeleEtats.rattacher(compte, m.comptesResultat()), m.produitParDefaut());
                    net.merge(ref, signe(m, ref, solde), Long::sum);
                    resultat -= solde;
                }
                default -> {
                    ref = ModeleEtats.rattacher(compte, m.comptesBilan());
                    String[] selon = ModeleEtats.rattacher(compte, m.selonSolde());
                    if (ref == null || (selon != null && compte.length() > 0
                            && longueurPrefixe(compte, m.selonSolde()) > longueurPrefixe(compte, m.comptesBilan()))) {
                        String[] choix = selon != null ? selon : m.bilanParDefaut();
                        ref = solde > 0 || ModeleEtats.amortissement(compte) ? choix[0] : choix[1];
                    }
                    if (actif(m, ref)) {
                        if (ModeleEtats.amortissement(compte)) {
                            amort.merge(ref, -solde, Long::sum);
                        } else {
                            brut.merge(ref, solde, Long::sum);
                        }
                    } else {
                        net.merge(ref, -solde, Long::sum);
                    }
                }
            }
            comptes.computeIfAbsent(ref, k -> new ArrayList<>()).add(compte);
        }
        net.merge(m.refResultatPassif(), resultat, Long::sum);

        Map<String, Long> valeurs = new LinkedHashMap<>(net);
        List<Ligne> lignesActif = new ArrayList<>();
        Map<String, long[]> actifs = new HashMap<>();
        for (Rubrique r : m.actif()) {
            long[] v;
            if (r.total()) {
                v = new long[3];
                for (String[] terme : termes(r.formule())) {
                    long[] x = actifs.getOrDefault(terme[1], new long[3]);
                    int s = "-".equals(terme[0]) ? -1 : 1;
                    for (int i = 0; i < 3; i++) {
                        v[i] += s * x[i];
                    }
                }
            } else {
                long b = brut.getOrDefault(r.ref(), 0L);
                long a = amort.getOrDefault(r.ref(), 0L);
                v = new long[]{b, a, b - a};
            }
            actifs.put(r.ref(), v);
            lignesActif.add(new Ligne(r, v[0], v[1], v[2]));
        }
        List<Ligne> lignesPassif = calculer(m.passif(), valeurs);
        List<Ligne> lignesResultat = calculer(m.resultat(), valeurs);
        long totalActif = lignesActif.get(lignesActif.size() - 1).net();
        long totalPassif = lignesPassif.get(lignesPassif.size() - 1).net();
        long net0 = lignesResultat.stream().filter(l -> l.rubrique().ref().equals(m.refResultat())).findFirst()
                .map(Ligne::net).orElse(resultat);
        return new EtatsFinanciers(m, lignesResultat, lignesActif, lignesPassif, net0, totalActif, totalPassif,
                comptes);
    }

    private static String defaut(String ref, String parDefaut) {
        return ref == null ? parDefaut : ref;
    }

    /** Une charge s'additionne en positif dans une rubrique de charges, un produit en positif dans les produits. */
    private static long signe(ModeleEtats m, String ref, long solde) {
        Rubrique r = m.rubrique(ref);
        return r != null && r.nature() == Nature.PRODUIT ? -solde : solde;
    }

    private static boolean actif(ModeleEtats m, String ref) {
        return m.actif().stream().anyMatch(r -> r.ref().equals(ref));
    }

    private static int longueurPrefixe(String numero, Map<String, ?> table) {
        for (int n = numero.length(); n >= 1; n--) {
            if (table.containsKey(numero.substring(0, n))) {
                return n;
            }
        }
        return 0;
    }

    private static List<Ligne> calculer(List<Rubrique> rubriques, Map<String, Long> valeurs) {
        List<Ligne> lignes = new ArrayList<>();
        for (Rubrique r : rubriques) {
            long v;
            if (r.total()) {
                v = 0;
                for (String[] terme : termes(r.formule())) {
                    long x = valeurs.getOrDefault(terme[1], 0L);
                    v += "-".equals(terme[0]) ? -x : x;
                }
                valeurs.put(r.ref(), v);
            } else {
                v = valeurs.getOrDefault(r.ref(), 0L);
            }
            lignes.add(new Ligne(r, 0, 0, v));
        }
        return lignes;
    }

    /** « +XB+TE-RA » → [[+, XB], [+, TE], [-, RA]]. */
    static List<String[]> termes(String formule) {
        List<String[]> t = new ArrayList<>();
        int i = 0;
        while (i < formule.length()) {
            String s = String.valueOf(formule.charAt(i));
            int j = i + 1;
            while (j < formule.length() && formule.charAt(j) != '+' && formule.charAt(j) != '-') {
                j++;
            }
            t.add(new String[]{s, formule.substring(i + 1, j)});
            i = j;
        }
        return t;
    }

    /** La valeur nette d'une rubrique, quel que soit l'état. */
    public long valeur(String ref) {
        for (List<Ligne> l : List.of(resultat, actif, passif)) {
            for (Ligne x : l) {
                if (x.rubrique().ref().equals(ref)) {
                    return x.net();
                }
            }
        }
        return 0;
    }
}
