package com.venach.kauris.compta.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venach.kauris.compta.application.PortsCompta;
import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.Exercice;
import com.venach.kauris.compta.domain.Imputation;
import com.venach.kauris.compta.domain.Journal;
import com.venach.kauris.compta.domain.LigneEcriture;
import com.venach.kauris.compta.domain.Mouvement;
import com.venach.kauris.compta.domain.OperationRecue;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stockage de la comptabilité en SQL (migration db/migration/compta). Requêtes natives, toujours filtrées par
 * organisation ; les instants sont des millisecondes UTC.
 */
public class StockageCompta implements PortsCompta.Comptes, PortsCompta.Journaux, PortsCompta.Exercices,
        PortsCompta.Ecritures, PortsCompta.Operations, PortsCompta.Imputations, PortsCompta.ParametresCompta,
        PortsCompta.Numeros {

    /** Une opération reçue, en JSON (la date en texte : pas de module de dates nécessaire). */
    record OperationJson(UUID tenantId, String origine, String reference, String date, String journal, String piece,
                         String libelle, List<OperationComptable.Ligne> lignes) {
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final EntityManager em;

    public StockageCompta(EntityManager em) {
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
        if (o instanceof Enum<?> e) {
            return e.name();
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> lignes(String sql, Object... parametres) {
        return q(sql, parametres).getResultList();
    }

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

    // ------------------------------------------------------------------ paramètres

    @Override
    public Optional<Parametres> parametres(UUID tenantId) {
        return lignes("select systeme, validation_auto from compta_parametres where tenant_id = ?1", tenantId)
                .stream().findFirst().map(l -> new Parametres(tenantId, Parametres.Systeme.valueOf(texte(l[0])),
                        vrai(l[1])));
    }

    @Override
    public void sauvegarder(Parametres p) {
        q("insert into compta_parametres (tenant_id, systeme, validation_auto) values (?1, ?2, ?3)"
                + " on duplicate key update systeme = ?2, validation_auto = ?3", p.tenantId(), p.systeme(),
                p.validationAutomatique()).executeUpdate();
    }

    // ------------------------------------------------------------------ comptes

    private static final String COMPTE = "select tenant_id, numero, libelle, lettrable, actif from compta_compte";

    private static Compte compte(Object[] l) {
        return new Compte(uuid(l[0]), texte(l[1]), texte(l[2]), vrai(l[3]), vrai(l[4]));
    }

    @Override
    public List<Compte> comptes(UUID tenantId) {
        return lignes(COMPTE + " where tenant_id = ?1 order by numero", tenantId).stream()
                .map(StockageCompta::compte).toList();
    }

    @Override
    public Optional<Compte> compte(UUID tenantId, String numero) {
        return lignes(COMPTE + " where tenant_id = ?1 and numero = ?2", tenantId, numero).stream()
                .map(StockageCompta::compte).findFirst();
    }

    @Override
    public void sauvegarder(Compte c) {
        q("insert into compta_compte (tenant_id, numero, libelle, lettrable, actif) values (?1, ?2, ?3, ?4, ?5)"
                + " on duplicate key update libelle = ?3, lettrable = ?4, actif = ?5", c.tenantId(), c.numero(),
                c.libelle(), c.lettrable(), c.actif()).executeUpdate();
    }

    // ------------------------------------------------------------------ journaux

    @Override
    public List<Journal> journaux(UUID tenantId) {
        return lignes("select tenant_id, code, libelle, systeme from compta_journal where tenant_id = ?1"
                + " order by systeme desc, code", tenantId).stream()
                .map(l -> new Journal(uuid(l[0]), texte(l[1]), texte(l[2]), vrai(l[3]))).toList();
    }

    @Override
    public void sauvegarder(Journal j) {
        q("insert into compta_journal (tenant_id, code, libelle, systeme) values (?1, ?2, ?3, ?4)"
                + " on duplicate key update libelle = ?3", j.tenantId(), j.code(), j.libelle(), j.systeme())
                .executeUpdate();
    }

    // ------------------------------------------------------------------ exercices

    private static final String EXERCICE = "select tenant_id, annee, clos, clos_le, clos_par from compta_exercice";

    private static Exercice exercice(Object[] l) {
        return new Exercice(uuid(l[0]), (int) entier(l[1]), vrai(l[2]), instant(l[3]), texte(l[4]));
    }

    @Override
    public List<Exercice> exercices(UUID tenantId) {
        return lignes(EXERCICE + " where tenant_id = ?1 order by annee", tenantId).stream()
                .map(StockageCompta::exercice).toList();
    }

    @Override
    public Optional<Exercice> exercice(UUID tenantId, int annee) {
        return lignes(EXERCICE + " where tenant_id = ?1 and annee = ?2", tenantId, annee).stream()
                .map(StockageCompta::exercice).findFirst();
    }

    @Override
    public void sauvegarder(Exercice e) {
        q("insert into compta_exercice (tenant_id, annee, clos, clos_le, clos_par) values (?1, ?2, ?3, ?4, ?5)"
                + " on duplicate key update clos = ?3, clos_le = ?4, clos_par = ?5", e.tenantId(), e.annee(),
                e.clos(), e.closLe() == null ? null : e.closLe().toEpochMilli(), e.closPar()).executeUpdate();
    }

    // ------------------------------------------------------------------ écritures

    private static final String ECRITURE = "select id, tenant_id, journal, numero, date_ecriture, piece, libelle,"
            + " statut, origine, reference, inverse_de, cree_le, cree_par, validee_le, validee_par"
            + " from compta_ecriture";

    private List<Ecriture> avecLignes(List<Object[]> entetes) {
        if (entetes.isEmpty()) {
            return List.of();
        }
        Map<String, List<LigneEcriture>> lignes = new LinkedHashMap<>();
        List<String> ids = entetes.stream().map(l -> texte(l[0])).toList();
        for (int i = 0; i < ids.size(); i += 500) {
            List<String> lot = ids.subList(i, Math.min(ids.size(), i + 500));
            String dans = lot.stream().map(x -> "'" + x.replace("'", "") + "'").collect(Collectors.joining(","));
            for (Object[] l : lignes("select ecriture_id, compte, tiers, tiers_nom, libelle, debit, credit, lettrage"
                    + " from compta_ligne where ecriture_id in (" + dans + ") order by ecriture_id, rang")) {
                lignes.computeIfAbsent(texte(l[0]), k -> new ArrayList<>()).add(new LigneEcriture(texte(l[1]),
                        texte(l[2]), texte(l[3]), texte(l[4]), entier(l[5]), entier(l[6]), texte(l[7])));
            }
        }
        List<Ecriture> e = new ArrayList<>();
        for (Object[] l : entetes) {
            e.add(new Ecriture(uuid(l[0]), uuid(l[1]), texte(l[2]), texte(l[3]), jour(l[4]), texte(l[5]),
                    texte(l[6]), lignes.getOrDefault(texte(l[0]), List.of()), Ecriture.Statut.valueOf(texte(l[7])),
                    texte(l[8]), texte(l[9]), uuid(l[10]), instant(l[11]), texte(l[12]), instant(l[13]),
                    texte(l[14])));
        }
        return e;
    }

    @Override
    public Optional<Ecriture> ecriture(UUID tenantId, UUID id) {
        return avecLignes(lignes(ECRITURE + " where tenant_id = ?1 and id = ?2", tenantId, id)).stream().findFirst();
    }

    @Override
    public void sauvegarder(Ecriture e) {
        q("insert into compta_ecriture (id, tenant_id, journal, numero, date_ecriture, piece, libelle, statut,"
                        + " origine, reference, inverse_de, cree_le, cree_par, validee_le, validee_par) values (?1, ?2,"
                        + " ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15) on duplicate key update"
                        + " journal = ?3, numero = ?4, date_ecriture = ?5, piece = ?6, libelle = ?7, statut = ?8,"
                        + " validee_le = ?14, validee_par = ?15",
                e.id(), e.tenantId(), e.journal(), e.numero().isEmpty() ? null : e.numero(), e.date(), e.piece(),
                e.libelle(), e.statut(), e.origine(), e.reference(), e.inverseDe(), e.creeLe(), e.creePar(),
                e.valideeLe() == null ? null : e.valideeLe().toEpochMilli(), e.valideePar()).executeUpdate();
        q("delete from compta_ligne where ecriture_id = ?1", e.id()).executeUpdate();
        int rang = 0;
        for (LigneEcriture l : e.lignes()) {
            q("insert into compta_ligne (ecriture_id, rang, tenant_id, compte, tiers, tiers_nom, libelle, debit,"
                    + " credit, lettrage) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)", e.id(), rang++,
                    e.tenantId(), l.compte(), l.tiers(), l.tiersNom(), l.libelle(), l.debit(), l.credit(),
                    l.lettrage()).executeUpdate();
        }
    }

    @Override
    public void supprimer(UUID tenantId, UUID id) {
        q("delete from compta_ligne where tenant_id = ?1 and ecriture_id = ?2", tenantId, id).executeUpdate();
        q("delete from compta_ecriture where tenant_id = ?1 and id = ?2", tenantId, id).executeUpdate();
    }

    @Override
    public List<Ecriture> ecritures(UUID tenantId, PortsCompta.Filtre f) {
        StringBuilder sql = new StringBuilder(ECRITURE + " where tenant_id = ?1");
        List<Object> p = new ArrayList<>(List.of(tenantId));
        if (f.journal() != null && !f.journal().isBlank()) {
            p.add(f.journal());
            sql.append(" and journal = ?").append(p.size());
        }
        if (f.du() != null) {
            p.add(f.du());
            sql.append(" and date_ecriture >= ?").append(p.size());
        }
        if (f.au() != null) {
            p.add(f.au());
            sql.append(" and date_ecriture <= ?").append(p.size());
        }
        if (f.statut() != null) {
            p.add(f.statut());
            sql.append(" and statut = ?").append(p.size());
        }
        if (f.recherche() != null && !f.recherche().isBlank()) {
            p.add("%" + f.recherche().strip().replace("%", "").replace("_", "") + "%");
            int n = p.size();
            sql.append(" and (libelle like ?").append(n).append(" or piece like ?").append(n)
                    .append(" or numero like ?").append(n).append(" or id in (select ecriture_id from compta_ligne")
                    .append(" where tenant_id = ?1 and (compte like ?").append(n).append(" or tiers_nom like ?")
                    .append(n).append(")))");
        }
        sql.append(" order by date_ecriture desc, numero desc, cree_le desc limit ")
                .append(Math.max(1, Math.min(f.limite(), 5000)));
        return avecLignes(lignes(sql.toString(), p.toArray()));
    }

    private static final String MOUVEMENT = "select l.ecriture_id, l.rang, e.journal, e.numero, e.date_ecriture,"
            + " e.piece, case when l.libelle = '' then e.libelle else l.libelle end, l.compte, l.tiers, l.tiers_nom,"
            + " l.debit, l.credit, l.lettrage, e.statut, e.origine from compta_ligne l join compta_ecriture e"
            + " on e.id = l.ecriture_id";

    private static Mouvement mouvement(Object[] l) {
        return new Mouvement(uuid(l[0]), (int) entier(l[1]), texte(l[2]), texte(l[3]), jour(l[4]), texte(l[5]),
                texte(l[6]), texte(l[7]), texte(l[8]), texte(l[9]), entier(l[10]), entier(l[11]), texte(l[12]),
                Ecriture.Statut.VALIDEE.name().equals(texte(l[13])), texte(l[14]));
    }

    @Override
    public List<Mouvement> mouvements(UUID tenantId, LocalDate du, LocalDate au) {
        return lignes(MOUVEMENT + " where e.tenant_id = ?1 and e.date_ecriture between ?2 and ?3"
                + " order by e.date_ecriture, e.numero, e.cree_le, l.rang", tenantId, du, au).stream()
                .map(StockageCompta::mouvement).toList();
    }

    @Override
    public List<Mouvement> mouvementsDuCompte(UUID tenantId, String compte, LocalDate du, LocalDate au) {
        return lignes(MOUVEMENT + " where l.tenant_id = ?1 and l.compte = ?2 and e.date_ecriture between ?3 and ?4"
                + " order by e.date_ecriture, e.numero, e.cree_le, l.rang", tenantId, compte, du, au).stream()
                .map(StockageCompta::mouvement).toList();
    }

    @Override
    public List<Ecriture> brouillard(UUID tenantId, LocalDate du, LocalDate au) {
        return avecLignes(lignes(ECRITURE + " where tenant_id = ?1 and statut = 'BROUILLARD'"
                + " and date_ecriture between ?2 and ?3 order by date_ecriture, cree_le", tenantId, du, au));
    }

    @Override
    public Optional<Ecriture> inverseDe(UUID tenantId, UUID id) {
        return avecLignes(lignes(ECRITURE + " where tenant_id = ?1 and inverse_de = ?2", tenantId, id)).stream()
                .findFirst();
    }

    @Override
    public Optional<Ecriture> parReference(UUID tenantId, String origine, String reference) {
        return avecLignes(lignes(ECRITURE + " where tenant_id = ?1 and origine = ?2 and reference = ?3"
                + " order by cree_le desc limit 1", tenantId, origine, reference)).stream().findFirst();
    }

    @Override
    public Set<String> lettrages(UUID tenantId, String compte) {
        Set<String> s = new HashSet<>();
        for (Object o : q("select distinct lettrage from compta_ligne where tenant_id = ?1 and compte = ?2"
                + " and lettrage <> ''", tenantId, compte).getResultList()) {
            s.add(texte(o));
        }
        return s;
    }

    // ------------------------------------------------------------------ opérations reçues

    private static final String OPERATION = "select id, contenu, statut, message, ecriture_id, recue_le, annulee_le,"
            + " motif from compta_operation";

    private static OperationRecue operation(Object[] l) {
        try {
            OperationJson j = JSON.readValue(texte(l[1]), OperationJson.class);
            OperationComptable o = new OperationComptable(j.tenantId(), j.origine(), j.reference(),
                    LocalDate.parse(j.date()), OperationComptable.Journal.valueOf(j.journal()), j.piece(),
                    j.libelle(), j.lignes());
            return new OperationRecue(uuid(l[0]), o, OperationRecue.Statut.valueOf(texte(l[2])), texte(l[3]),
                    uuid(l[4]), instant(l[5]), jour(l[6]), texte(l[7]));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Opération comptable illisible : " + l[0], e);
        }
    }

    @Override
    public Optional<OperationRecue> operation(UUID tenantId, String origine, String reference) {
        return lignes(OPERATION + " where tenant_id = ?1 and origine = ?2 and reference = ?3", tenantId, origine,
                reference).stream().map(StockageCompta::operation).findFirst();
    }

    @Override
    public Optional<OperationRecue> parId(UUID tenantId, UUID id) {
        return lignes(OPERATION + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageCompta::operation).findFirst();
    }

    @Override
    public void sauvegarder(OperationRecue r) {
        OperationComptable o = r.operation();
        String contenu;
        try {
            contenu = JSON.writeValueAsString(new OperationJson(o.tenantId(), o.origine(), o.reference(),
                    o.date().toString(), o.journal().name(), o.piece(), o.libelle(), o.lignes()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        q("insert into compta_operation (id, tenant_id, origine, reference, date_operation, contenu, statut, message,"
                        + " ecriture_id, recue_le, annulee_le, motif) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10,"
                        + " ?11, ?12) on duplicate key update statut = ?7, message = ?8, ecriture_id = ?9,"
                        + " annulee_le = ?11, motif = ?12",
                r.id(), o.tenantId(), o.origine(), o.reference(), o.date(), contenu, r.statut(), r.message(),
                r.ecritureId(), r.recueLe(), r.annuleeLe(), r.motifAnnulation()).executeUpdate();
    }

    @Override
    public List<OperationRecue> aRevoir(UUID tenantId) {
        return lignes(OPERATION + " where tenant_id = ?1 and statut in ('A_REVOIR', 'ANNULATION_A_REVOIR')"
                + " order by date_operation, recue_le", tenantId).stream().map(StockageCompta::operation).toList();
    }

    @Override
    public List<String> precisionsDeTresorerie(UUID tenantId) {
        Set<String> s = new java.util.TreeSet<>();
        for (Object o : q("select contenu from compta_operation where tenant_id = ?1 and contenu like"
                + " '%\"TRESORERIE\"%' order by recue_le desc limit 2000", tenantId).getResultList()) {
            try {
                OperationJson j = JSON.readValue(texte(o), OperationJson.class);
                j.lignes().stream().filter(l -> l.role() == OperationComptable.Role.TRESORERIE
                        && !l.precision().isEmpty()).forEach(l -> {
                    for (String niveau : l.precision().split("\\|")) {
                        if (!niveau.isBlank()) {
                            s.add(niveau.strip());
                        }
                    }
                });
            } catch (JsonProcessingException e) {
                // Une opération illisible ne cache pas les autres.
            }
        }
        return List.copyOf(s);
    }

    // ------------------------------------------------------------------ imputations

    @Override
    public List<Imputation> imputations(UUID tenantId) {
        return lignes("select role, detail, compte from compta_imputation where tenant_id = ?1 order by role, detail",
                tenantId).stream().map(l -> new Imputation(tenantId, OperationComptable.Role.valueOf(texte(l[0])),
                texte(l[1]), texte(l[2]))).toList();
    }

    @Override
    public void remplacer(UUID tenantId, List<Imputation> liste) {
        q("delete from compta_imputation where tenant_id = ?1", tenantId).executeUpdate();
        for (Imputation i : liste) {
            q("insert into compta_imputation (tenant_id, role, detail, compte) values (?1, ?2, ?3, ?4)", tenantId,
                    i.role(), i.precision(), i.compte()).executeUpdate();
        }
    }

    // ------------------------------------------------------------------ numéros

    @Override
    public long prochain(UUID tenantId, String journal, int annee) {
        q("insert into compta_compteur (tenant_id, journal, annee, dernier) values (?1, ?2, ?3, 1)"
                + " on duplicate key update dernier = dernier + 1", tenantId, journal, annee).executeUpdate();
        return entier(q("select dernier from compta_compteur where tenant_id = ?1 and journal = ?2 and annee = ?3",
                tenantId, journal, annee).getSingleResult());
    }
}
