package com.venach.kauris.paie.adapter.persistence;

import com.venach.kauris.paie.application.PortsPaie;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.Foyer;
import com.venach.kauris.paie.domain.ModePaiement;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.Parametres;
import com.venach.kauris.paie.domain.PaysPaie;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.SituationFamiliale;
import com.venach.kauris.paie.domain.TypeContrat;
import com.venach.kauris.paie.domain.TypeElement;
import com.venach.kauris.paie.domain.Variables;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stockage de la paie en SQL (migration db/migration/paie). Requêtes natives, toujours filtrées par organisation ;
 * les instants sont des millisecondes UTC.
 */
public class StockagePaie implements PortsPaie.Salaries, PortsPaie.Contrats, PortsPaie.Periodes,
        PortsPaie.VariablesDuMois, PortsPaie.Bulletins, PortsPaie.Corrections, PortsPaie.Numeros {

    private final EntityManager em;

    public StockagePaie(EntityManager em) {
        this.em = em;
    }

    private Query q(String sql, Object... parametres) {
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < parametres.length; i++) {
            q.setParameter(i + 1, valeur(parametres[i]));
        }
        return q;
    }

    private static Object valeur(Object o) {
        if (o instanceof UUID u) {
            return u.toString();
        }
        if (o instanceof LocalDate d) {
            return Date.valueOf(d);
        }
        if (o instanceof Instant i) {
            return i.toEpochMilli();
        }
        if (o instanceof YearMonth m) {
            return m.toString();
        }
        if (o instanceof Enum<?> e) {
            return e.name();
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> lignes(String sql, Object... parametres) {
        return q(sql, parametres).getResultList();
    }

    // ------------------------------------------------------------------ conversions

    private static UUID uuid(Object o) {
        return o == null ? null : UUID.fromString(o.toString());
    }

    private static LocalDate jour(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d.toLocalDate();
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (o instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        throw new IllegalStateException("Date inattendue : " + o.getClass());
    }

    private static long entier(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static int petit(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof BigDecimal b ? b.stripTrailingZeros() : new BigDecimal(o.toString()).stripTrailingZeros();
    }

    private static Instant instant(Object o) {
        return o == null ? null : Instant.ofEpochMilli(((Number) o).longValue());
    }

    private static boolean vrai(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && ((Number) o).intValue() != 0;
    }

    private static String texte(Object o) {
        return o == null ? "" : o.toString();
    }

    /** Les identifiants (UUID, sans guillemets possibles) d'une liste, pour un « in (…) ». */
    private static String dans(List<String> ids) {
        return ids.stream().map(x -> "'" + x.replace("'", "") + "'").collect(Collectors.joining(","));
    }

    private static <T> List<List<T>> lots(List<T> ids) {
        List<List<T>> lots = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            lots.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return lots;
    }

    // ------------------------------------------------------------------ salariés

    private static final String SALARIE = "select id, tenant_id, matricule, nom, prenoms, naissance, telephone, email,"
            + " adresse, piece_identite, numero_social, situation, enfants, conjoint_a_charge, expatrie, mode_paiement,"
            + " banque, compte, actif, notes, cree_le from paie_salarie";

    @Override
    public void sauvegarder(Salarie s) {
        q("delete from paie_salarie where id = ?1 and tenant_id = ?2", s.id(), s.tenantId()).executeUpdate();
        q("insert into paie_salarie (id, tenant_id, matricule, nom, prenoms, naissance, telephone, email, adresse,"
                        + " piece_identite, numero_social, situation, enfants, conjoint_a_charge, expatrie, mode_paiement,"
                        + " banque, compte, actif, notes, cree_le) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10,"
                        + " ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21)",
                s.id(), s.tenantId(), s.matricule(), s.nom(), s.prenoms(), s.naissance(), s.telephone(), s.email(),
                s.adresse(), s.pieceIdentite(), s.numeroSocial(), s.foyer().situation(), s.foyer().enfants(),
                s.foyer().conjointACharge(), s.expatrie(), s.modePaiement(), s.banque(), s.compte(), s.actif(),
                s.notes(), s.creeLe()).executeUpdate();
    }

    @Override
    public Optional<Salarie> salarie(UUID tenantId, UUID id) {
        return lignes(SALARIE + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockagePaie::salarie).findFirst();
    }

    @Override
    public List<Salarie> salaries(UUID tenantId) {
        return lignes(SALARIE + " where tenant_id = ?1", tenantId).stream().map(StockagePaie::salarie).toList();
    }

    private static Salarie salarie(Object[] l) {
        return new Salarie(uuid(l[0]), uuid(l[1]), texte(l[2]), texte(l[3]), texte(l[4]), jour(l[5]), texte(l[6]),
                texte(l[7]), texte(l[8]), texte(l[9]), texte(l[10]),
                new Foyer(SituationFamiliale.valueOf(texte(l[11])), petit(l[12]), vrai(l[13])), vrai(l[14]),
                ModePaiement.valueOf(texte(l[15])), texte(l[16]), texte(l[17]), vrai(l[18]), texte(l[19]),
                instant(l[20]));
    }

    // ------------------------------------------------------------------ contrats

    private static final String CONTRAT = "select id, tenant_id, salarie_id, numero, type, poste, categorie, debut,"
            + " fin_prevue, salaire_base, sortie, motif_sortie, notes, cree_le from paie_contrat";

    @Override
    public void sauvegarder(ContratTravail c) {
        q("delete from paie_contrat_element where contrat_id = ?1", c.id()).executeUpdate();
        q("delete from paie_contrat where id = ?1 and tenant_id = ?2", c.id(), c.tenantId()).executeUpdate();
        q("insert into paie_contrat (id, tenant_id, salarie_id, numero, type, poste, categorie, debut, fin_prevue,"
                        + " salaire_base, sortie, motif_sortie, notes, cree_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
                c.id(), c.tenantId(), c.salarieId(), c.numero(), c.type(), c.poste(), c.categorie(), c.debut(),
                c.finPrevue(), c.salaireBase(), c.sortie(), c.motifSortie(), c.notes(), c.creeLe()).executeUpdate();
        int rang = 0;
        for (Element e : c.elements()) {
            q("insert into paie_contrat_element (contrat_id, rang, libelle, type, montant) values (?1, ?2, ?3, ?4, ?5)",
                    c.id(), rang++, e.libelle(), e.type(), e.montant()).executeUpdate();
        }
    }

    @Override
    public Optional<ContratTravail> contrat(UUID tenantId, UUID id) {
        return contrats(CONTRAT + " where tenant_id = ?1 and id = ?2", tenantId, id).stream().findFirst();
    }

    @Override
    public List<ContratTravail> contrats(UUID tenantId) {
        return contrats(CONTRAT + " where tenant_id = ?1", tenantId);
    }

    private List<ContratTravail> contrats(String sql, Object... p) {
        List<Object[]> l = lignes(sql, p);
        if (l.isEmpty()) {
            return List.of();
        }
        Map<String, List<Element>> elements = new HashMap<>();
        for (List<String> lot : lots(l.stream().map(x -> x[0].toString()).toList())) {
            lignes("select contrat_id, libelle, type, montant from paie_contrat_element where contrat_id in ("
                    + dans(lot) + ") order by contrat_id, rang").forEach(e -> elements.computeIfAbsent(e[0].toString(),
                    k -> new ArrayList<>()).add(new Element(texte(e[1]), TypeElement.valueOf(texte(e[2])),
                    entier(e[3]))));
        }
        return l.stream().map(x -> new ContratTravail(uuid(x[0]), uuid(x[1]), uuid(x[2]), texte(x[3]),
                TypeContrat.valueOf(texte(x[4])), texte(x[5]), texte(x[6]), jour(x[7]), jour(x[8]), entier(x[9]),
                elements.getOrDefault(x[0].toString(), List.of()), jour(x[10]), texte(x[11]), texte(x[12]),
                instant(x[13]))).toList();
    }

    // ------------------------------------------------------------------ mois de paie

    private static final String PERIODE = "select id, tenant_id, mois, pays, clos, clos_le, clos_par, paye_le, cree_le"
            + " from paie_periode";

    @Override
    public void sauvegarder(PeriodePaie p) {
        q("delete from paie_periode where id = ?1 and tenant_id = ?2", p.id(), p.tenantId()).executeUpdate();
        q("insert into paie_periode (id, tenant_id, mois, pays, clos, clos_le, clos_par, paye_le, cree_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)", p.id(), p.tenantId(), p.mois(), p.pays(),
                p.close(), p.closeLe(), p.closePar(), p.payeLe(), p.creeLe()).executeUpdate();
    }

    @Override
    public Optional<PeriodePaie> periode(UUID tenantId, UUID id) {
        return lignes(PERIODE + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockagePaie::periode).findFirst();
    }

    @Override
    public Optional<PeriodePaie> verrouiller(UUID tenantId, UUID id) {
        return lignes(PERIODE + " where tenant_id = ?1 and id = ?2 for update", tenantId, id).stream()
                .map(StockagePaie::periode).findFirst();
    }

    @Override
    public Optional<PeriodePaie> parMois(UUID tenantId, YearMonth mois) {
        return lignes(PERIODE + " where tenant_id = ?1 and mois = ?2", tenantId, mois).stream()
                .map(StockagePaie::periode).findFirst();
    }

    @Override
    public List<PeriodePaie> periodes(UUID tenantId) {
        return lignes(PERIODE + " where tenant_id = ?1", tenantId).stream().map(StockagePaie::periode).toList();
    }

    private static PeriodePaie periode(Object[] l) {
        return new PeriodePaie(uuid(l[0]), uuid(l[1]), YearMonth.parse(texte(l[2]).strip()),
                PaysPaie.valueOf(texte(l[3])), vrai(l[4]), instant(l[5]), texte(l[6]), jour(l[7]), instant(l[8]));
    }

    // ------------------------------------------------------------------ éléments du mois

    @Override
    public void sauvegarder(Variables v) {
        q("delete from paie_variable_element where periode_id = ?1 and salarie_id = ?2", v.periodeId(), v.salarieId())
                .executeUpdate();
        q("delete from paie_variables where periode_id = ?1 and salarie_id = ?2 and tenant_id = ?3", v.periodeId(),
                v.salarieId(), v.tenantId()).executeUpdate();
        if (v.vides()) {
            return;
        }
        q("insert into paie_variables (tenant_id, periode_id, salarie_id, jours_absence) values (?1, ?2, ?3, ?4)",
                v.tenantId(), v.periodeId(), v.salarieId(), v.joursAbsence()).executeUpdate();
        int rang = 0;
        for (Element e : v.elements()) {
            q("insert into paie_variable_element (periode_id, salarie_id, rang, libelle, type, montant)"
                    + " values (?1, ?2, ?3, ?4, ?5, ?6)", v.periodeId(), v.salarieId(), rang++, e.libelle(), e.type(),
                    e.montant()).executeUpdate();
        }
    }

    @Override
    public List<Variables> variables(UUID tenantId, UUID periodeId) {
        List<Object[]> l = lignes("select salarie_id, jours_absence from paie_variables where tenant_id = ?1"
                + " and periode_id = ?2", tenantId, periodeId);
        if (l.isEmpty()) {
            return List.of();
        }
        Map<String, List<Element>> elements = new HashMap<>();
        lignes("select salarie_id, libelle, type, montant from paie_variable_element where periode_id = ?1"
                + " order by salarie_id, rang", periodeId).forEach(e -> elements.computeIfAbsent(e[0].toString(),
                k -> new ArrayList<>()).add(new Element(texte(e[1]), TypeElement.valueOf(texte(e[2])), entier(e[3]))));
        return l.stream().map(x -> new Variables(tenantId, periodeId, uuid(x[0]), petit(x[1]),
                elements.getOrDefault(x[0].toString(), List.of()))).toList();
    }

    // ------------------------------------------------------------------ bulletins

    private static final String BULLETIN = "select id, tenant_id, periode_id, salarie_id, contrat_id, numero, matricule,"
            + " nom_salarie, poste, categorie, numero_social, situation, enfants, parts, jours_payes, salaire_base,"
            + " mode_paiement, banque, compte, brut, brut_soumis, cotisations_salarie, impot, retenues,"
            + " avantages_nature, net_a_payer, charges_employeur, base_impot, detail_impot from paie_bulletin";

    @Override
    public void ajouter(Bulletin b) {
        CalculPaie.Resultat r = b.resultat();
        q("insert into paie_bulletin (id, tenant_id, periode_id, salarie_id, contrat_id, numero, matricule, nom_salarie,"
                        + " poste, categorie, numero_social, situation, enfants, parts, jours_payes, salaire_base,"
                        + " mode_paiement, banque, compte, brut, brut_soumis, cotisations_salarie, impot, retenues,"
                        + " avantages_nature, net_a_payer, charges_employeur, base_impot, detail_impot) values (?1, ?2,"
                        + " ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21, ?22,"
                        + " ?23, ?24, ?25, ?26, ?27, ?28, ?29)",
                b.id(), b.tenantId(), b.periodeId(), b.salarieId(), b.contratId(), b.numero(), b.matricule(),
                b.nomSalarie(), b.poste(), b.categorie(), b.numeroSocial(), b.situation(), b.enfants(), b.parts(),
                b.joursPayes(), b.salaireBase(), b.modePaiement(), b.banque(), b.compte(), r.brut(), r.brutSoumis(),
                r.cotisationsSalarie(), r.impot(), r.retenues(), r.avantagesNature(), r.netAPayer(),
                r.chargesEmployeur(), r.baseImpot(), tronquer(r.detailImpot(), 400)).executeUpdate();
        int rang = 0;
        for (CalculPaie.Ligne l : r.lignes()) {
            q("insert into paie_bulletin_ligne (bulletin_id, rang, code, libelle, nature, organisme, base, taux_salarie,"
                            + " gain, salarie, taux_employeur, employeur) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9,"
                            + " ?10, ?11, ?12)", b.id(), rang++, l.code(), tronquer(l.libelle(), 120), l.nature(),
                    l.organisme(), l.base(), l.tauxSalarie(), l.gain(), l.salarie(), l.tauxEmployeur(), l.employeur())
                    .executeUpdate();
        }
    }

    private static String tronquer(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public List<Bulletin> bulletins(UUID tenantId, UUID periodeId) {
        return bulletins(BULLETIN + " where tenant_id = ?1 and periode_id = ?2", tenantId, periodeId);
    }

    @Override
    public Optional<Bulletin> bulletin(UUID tenantId, UUID id) {
        return bulletins(BULLETIN + " where tenant_id = ?1 and id = ?2", tenantId, id).stream().findFirst();
    }

    @Override
    public List<Bulletin> bulletinsDuSalarie(UUID tenantId, UUID salarieId) {
        return bulletins(BULLETIN + " where tenant_id = ?1 and salarie_id = ?2", tenantId, salarieId);
    }

    @Override
    public void supprimer(UUID tenantId, UUID periodeId) {
        List<String> ids = lignes("select id, numero from paie_bulletin where tenant_id = ?1 and periode_id = ?2",
                tenantId, periodeId).stream().map(x -> x[0].toString()).toList();
        for (List<String> lot : lots(ids)) {
            q("delete from paie_bulletin_ligne where bulletin_id in (" + dans(lot) + ")").executeUpdate();
        }
        q("delete from paie_bulletin where tenant_id = ?1 and periode_id = ?2", tenantId, periodeId).executeUpdate();
    }

    private List<Bulletin> bulletins(String sql, Object... p) {
        List<Object[]> l = lignes(sql, p);
        if (l.isEmpty()) {
            return List.of();
        }
        Map<String, List<CalculPaie.Ligne>> parBulletin = new HashMap<>();
        for (List<String> lot : lots(l.stream().map(x -> x[0].toString()).toList())) {
            lignes("select bulletin_id, code, libelle, nature, organisme, base, taux_salarie, gain, salarie,"
                    + " taux_employeur, employeur from paie_bulletin_ligne where bulletin_id in (" + dans(lot)
                    + ") order by bulletin_id, rang").forEach(x -> parBulletin.computeIfAbsent(x[0].toString(),
                    k -> new ArrayList<>()).add(new CalculPaie.Ligne(texte(x[1]), texte(x[2]),
                    CalculPaie.Nature.valueOf(texte(x[3])), texte(x[4]), entier(x[5]), decimal(x[6]), entier(x[7]),
                    entier(x[8]), decimal(x[9]), entier(x[10]))));
        }
        return l.stream().map(x -> {
            CalculPaie.Resultat r = new CalculPaie.Resultat(parBulletin.getOrDefault(x[0].toString(), List.of()),
                    entier(x[19]), entier(x[20]), entier(x[21]), entier(x[22]), entier(x[23]), entier(x[24]),
                    entier(x[25]), entier(x[26]), entier(x[27]), texte(x[28]));
            return new Bulletin(uuid(x[0]), uuid(x[1]), uuid(x[2]), uuid(x[3]), uuid(x[4]), texte(x[5]), texte(x[6]),
                    texte(x[7]), texte(x[8]), texte(x[9]), texte(x[10]), texte(x[11]), petit(x[12]), decimal(x[13]),
                    petit(x[14]), entier(x[15]), ModePaiement.valueOf(texte(x[16])), texte(x[17]), texte(x[18]), r);
        }).toList();
    }

    // ------------------------------------------------------------------ corrections des paramètres

    @Override
    public List<Parametres.Correction> corrections(UUID tenantId) {
        List<Parametres.Correction> c = new ArrayList<>();
        for (Object[] l : lignes("select parametre, depuis, valeur, note from paie_correction where tenant_id = ?1",
                tenantId)) {
            // Un paramètre retiré du code est ignoré, sans faire échouer la paie.
            Parametre.parCode(texte(l[0])).ifPresent(p -> c.add(new Parametres.Correction(tenantId, p, jour(l[1]),
                    decimal(l[2]), texte(l[3]))));
        }
        return c;
    }

    @Override
    public void ajouter(Parametres.Correction c) {
        q("insert into paie_correction (tenant_id, parametre, depuis, valeur, note) values (?1, ?2, ?3, ?4, ?5)",
                c.tenantId(), c.parametre(), c.depuis(), c.valeur(), c.note()).executeUpdate();
    }

    @Override
    public void retirer(UUID tenantId, Parametre parametre, LocalDate depuis) {
        q("delete from paie_correction where tenant_id = ?1 and parametre = ?2 and depuis = ?3", tenantId, parametre,
                depuis).executeUpdate();
    }

    // ------------------------------------------------------------------ numéros

    @Override
    public long prochain(UUID tenantId, String prefixe, int annee) {
        q("insert into paie_compteur (tenant_id, prefixe, annee, dernier) values (?1, ?2, ?3, 1)"
                + " on duplicate key update dernier = dernier + 1", tenantId, prefixe, annee).executeUpdate();
        return entier(q("select dernier from paie_compteur where tenant_id = ?1 and prefixe = ?2 and annee = ?3",
                tenantId, prefixe, annee).getSingleResult());
    }
}
