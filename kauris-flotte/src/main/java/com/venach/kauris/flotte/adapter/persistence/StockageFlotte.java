package com.venach.kauris.flotte.adapter.persistence;

import com.venach.kauris.flotte.application.PortsFlotte;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.JourFerie;
import com.venach.kauris.flotte.domain.ModeVersement;
import com.venach.kauris.flotte.domain.StatutContrat;
import com.venach.kauris.flotte.domain.StatutVehicule;
import com.venach.kauris.flotte.domain.TypeContrat;
import com.venach.kauris.flotte.domain.TypeDocument;
import com.venach.kauris.flotte.domain.TypeVehicule;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stockage de la flotte en SQL (migration db/migration/flotte). Requêtes natives, toujours filtrées par
 * organisation ; les instants sont des millisecondes UTC.
 */
public class StockageFlotte implements PortsFlotte.Vehicules, PortsFlotte.Conducteurs, PortsFlotte.Contrats,
        PortsFlotte.Versements, PortsFlotte.Ajustements, PortsFlotte.Feries, PortsFlotte.Documents,
        PortsFlotte.Numeros {

    private final EntityManager em;

    public StockageFlotte(EntityManager em) {
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

    private static Long entier(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer petit(Object o) {
        return o == null ? null : ((Number) o).intValue();
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

    // ------------------------------------------------------------------ véhicules

    private static final String VEHICULE = "select id, tenant_id, type, immatriculation, marque, modele, couleur, annee,"
            + " chassis, mise_en_service, prix_achat, compteur, reference_paiement, statut, notes, cree_le"
            + " from flotte_vehicule";

    @Override
    public void sauvegarder(Vehicule v) {
        q("delete from flotte_vehicule where id = ?1 and tenant_id = ?2", v.id(), v.tenantId()).executeUpdate();
        q("insert into flotte_vehicule (id, tenant_id, type, immatriculation, marque, modele, couleur, annee, chassis,"
                        + " mise_en_service, prix_achat, compteur, reference_paiement, statut, notes, cree_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
                v.id(), v.tenantId(), v.type(), v.immatriculation(), v.marque(), v.modele(), v.couleur(), v.annee(),
                v.chassis(), v.miseEnService(), v.prixAchat(), v.compteur(), v.referencePaiement(), v.statut(),
                v.notes(), v.creeLe()).executeUpdate();
    }

    @Override
    public Optional<Vehicule> vehicule(UUID tenantId, UUID id) {
        return lignes(VEHICULE + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageFlotte::vehicule).findFirst();
    }

    @Override
    public List<Vehicule> vehicules(UUID tenantId) {
        return lignes(VEHICULE + " where tenant_id = ?1", tenantId).stream().map(StockageFlotte::vehicule).toList();
    }

    private static Vehicule vehicule(Object[] l) {
        return new Vehicule(uuid(l[0]), uuid(l[1]), TypeVehicule.valueOf(texte(l[2])), texte(l[3]), texte(l[4]),
                texte(l[5]), texte(l[6]), petit(l[7]), texte(l[8]), jour(l[9]), entier(l[10]), entier(l[11]),
                texte(l[12]), StatutVehicule.valueOf(texte(l[13])), texte(l[14]), instant(l[15]));
    }

    // ------------------------------------------------------------------ conducteurs

    private static final String CONDUCTEUR = "select id, tenant_id, nom, telephone, piece_identite, permis_numero,"
            + " permis_categorie, permis_expire_le, garant_nom, garant_telephone, actif, notes, cree_le"
            + " from flotte_conducteur";

    @Override
    public void sauvegarder(Conducteur c) {
        q("delete from flotte_conducteur where id = ?1 and tenant_id = ?2", c.id(), c.tenantId()).executeUpdate();
        q("insert into flotte_conducteur (id, tenant_id, nom, telephone, piece_identite, permis_numero,"
                        + " permis_categorie, permis_expire_le, garant_nom, garant_telephone, actif, notes, cree_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
                c.id(), c.tenantId(), c.nom(), c.telephone(), c.pieceIdentite(), c.permisNumero(), c.permisCategorie(),
                c.permisExpireLe(), c.garantNom(), c.garantTelephone(), c.actif(), c.notes(), c.creeLe())
                .executeUpdate();
    }

    @Override
    public Optional<Conducteur> conducteur(UUID tenantId, UUID id) {
        return lignes(CONDUCTEUR + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageFlotte::conducteur).findFirst();
    }

    @Override
    public List<Conducteur> conducteurs(UUID tenantId) {
        return lignes(CONDUCTEUR + " where tenant_id = ?1", tenantId).stream().map(StockageFlotte::conducteur)
                .toList();
    }

    private static Conducteur conducteur(Object[] l) {
        return new Conducteur(uuid(l[0]), uuid(l[1]), texte(l[2]), texte(l[3]), texte(l[4]), texte(l[5]),
                texte(l[6]), jour(l[7]), texte(l[8]), texte(l[9]), vrai(l[10]), texte(l[11]), instant(l[12]));
    }

    // ------------------------------------------------------------------ contrats

    private static final String CONTRAT = "select id, tenant_id, numero, vehicule_id, conducteur_id, type, debut, fin,"
            + " jours_repos, prix_total, statut, motif_fin, notes, cree_le from flotte_contrat";

    @Override
    public void sauvegarder(Contrat c) {
        q("delete from flotte_contrat_tarif where contrat_id = ?1", c.id()).executeUpdate();
        q("delete from flotte_contrat_pause where contrat_id = ?1", c.id()).executeUpdate();
        q("delete from flotte_contrat where id = ?1 and tenant_id = ?2", c.id(), c.tenantId()).executeUpdate();
        q("insert into flotte_contrat (id, tenant_id, numero, vehicule_id, conducteur_id, type, debut, fin,"
                        + " jours_repos, prix_total, statut, motif_fin, notes, cree_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
                c.id(), c.tenantId(), c.numero(), c.vehiculeId(), c.conducteurId(), c.type(), c.debut(), c.fin(),
                c.joursRepos().stream().sorted().map(Enum::name).collect(Collectors.joining(",")), c.prixTotal(),
                c.statut(), c.motifFin(), c.notes(), c.creeLe()).executeUpdate();
        for (Contrat.Tarif t : c.tarifs()) {
            q("insert into flotte_contrat_tarif (contrat_id, depuis, montant) values (?1, ?2, ?3)", c.id(),
                    t.depuis(), t.montant()).executeUpdate();
        }
        for (Contrat.Pause p : c.pauses()) {
            q("insert into flotte_contrat_pause (id, contrat_id, du, au, motif, a_charge)"
                    + " values (?1, ?2, ?3, ?4, ?5, ?6)", p.id(), c.id(), p.du(), p.au(), p.motif(),
                    p.aChargeDuConducteur()).executeUpdate();
        }
    }

    @Override
    public Optional<Contrat> contrat(UUID tenantId, UUID id) {
        return contrats(CONTRAT + " where tenant_id = ?1 and id = ?2", tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<Contrat> verrouillerContrat(UUID tenantId, UUID id) {
        return contrats(CONTRAT + " where tenant_id = ?1 and id = ?2 for update", tenantId, id).stream().findFirst();
    }

    @Override
    public List<Contrat> contrats(UUID tenantId) {
        return contrats(CONTRAT + " where tenant_id = ?1", tenantId);
    }

    private List<Contrat> contrats(String sql, Object... p) {
        List<Object[]> l = lignes(sql, p);
        if (l.isEmpty()) {
            return List.of();
        }
        List<String> ids = l.stream().map(x -> x[0].toString()).toList();
        Map<String, List<Contrat.Tarif>> tarifs = new HashMap<>();
        Map<String, List<Contrat.Pause>> pauses = new HashMap<>();
        for (List<String> lot : lots(ids)) {
            String in = lot.stream().map(x -> "'" + x.replace("'", "") + "'").collect(Collectors.joining(","));
            lignes("select contrat_id, depuis, montant from flotte_contrat_tarif where contrat_id in (" + in + ")")
                    .forEach(t -> tarifs.computeIfAbsent(t[0].toString(), k -> new ArrayList<>())
                            .add(new Contrat.Tarif(jour(t[1]), entier(t[2]))));
            lignes("select contrat_id, id, du, au, motif, a_charge from flotte_contrat_pause where contrat_id in ("
                    + in + ")").forEach(t -> pauses.computeIfAbsent(t[0].toString(), k -> new ArrayList<>())
                    .add(new Contrat.Pause(uuid(t[1]), jour(t[2]), jour(t[3]), texte(t[4]), vrai(t[5]))));
        }
        return l.stream().map(x -> {
            String id = x[0].toString();
            String repos = texte(x[8]);
            Set<DayOfWeek> jours = repos.isEmpty() ? EnumSet.noneOf(DayOfWeek.class)
                    : Arrays.stream(repos.split(",")).map(DayOfWeek::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
            return new Contrat(uuid(x[0]), uuid(x[1]), texte(x[2]), uuid(x[3]), uuid(x[4]),
                    TypeContrat.valueOf(texte(x[5])), jour(x[6]), jour(x[7]), tarifs.getOrDefault(id, List.of()), jours,
                    pauses.getOrDefault(id, List.of()), entier(x[9]), StatutContrat.valueOf(texte(x[10])),
                    texte(x[11]), texte(x[12]), instant(x[13]));
        }).toList();
    }

    /** Les identifiants (UUID, sans guillemets possibles) par paquets de 500 pour les « in (…) ». */
    private static List<List<String>> lots(List<String> ids) {
        List<List<String>> lots = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            lots.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return lots;
    }

    // ------------------------------------------------------------------ versements

    private static final String VERSEMENT = "select id, tenant_id, contrat_id, numero, jour, montant, mode, reference,"
            + " saisi_par, saisi_le, annule, motif_annulation, annule_par, annule_le from flotte_versement";

    @Override
    public void sauvegarder(Versement v) {
        q("delete from flotte_versement where id = ?1 and tenant_id = ?2", v.id(), v.tenantId()).executeUpdate();
        q("insert into flotte_versement (id, tenant_id, contrat_id, numero, jour, montant, mode, reference, saisi_par,"
                        + " saisi_le, annule, motif_annulation, annule_par, annule_le)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
                v.id(), v.tenantId(), v.contratId(), v.numero(), v.date(), v.montant(), v.mode(), v.reference(),
                v.saisiPar(), v.saisiLe(), v.annule(), v.motifAnnulation(), v.annulePar(), v.annuleLe())
                .executeUpdate();
    }

    @Override
    public Optional<Versement> versement(UUID tenantId, UUID id) {
        return lignes(VERSEMENT + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageFlotte::versement).findFirst();
    }

    @Override
    public List<Versement> versementsDuContrat(UUID tenantId, UUID contratId) {
        return lignes(VERSEMENT + " where tenant_id = ?1 and contrat_id = ?2", tenantId, contratId).stream()
                .map(StockageFlotte::versement).toList();
    }

    @Override
    public List<Versement> versementsEntre(UUID tenantId, LocalDate du, LocalDate au) {
        return lignes(VERSEMENT + " where tenant_id = ?1 and jour between ?2 and ?3", tenantId, du, au).stream()
                .map(StockageFlotte::versement).toList();
    }

    @Override
    public Optional<Versement> parReference(UUID tenantId, String reference) {
        return lignes(VERSEMENT + " where tenant_id = ?1 and reference = ?2 order by annule", tenantId, reference)
                .stream().map(StockageFlotte::versement).findFirst();
    }

    private static Versement versement(Object[] l) {
        return new Versement(uuid(l[0]), uuid(l[1]), uuid(l[2]), texte(l[3]), jour(l[4]), entier(l[5]),
                ModeVersement.valueOf(texte(l[6])), texte(l[7]), texte(l[8]), instant(l[9]), vrai(l[10]),
                texte(l[11]), texte(l[12]), instant(l[13]));
    }

    // ------------------------------------------------------------------ ajustements

    private static final String AJUSTEMENT = "select id, tenant_id, contrat_id, jour, type, montant, motif, saisi_par,"
            + " saisi_le, annule, motif_annulation from flotte_ajustement";

    @Override
    public void sauvegarder(Ajustement a) {
        q("delete from flotte_ajustement where id = ?1 and tenant_id = ?2", a.id(), a.tenantId()).executeUpdate();
        q("insert into flotte_ajustement (id, tenant_id, contrat_id, jour, type, montant, motif, saisi_par, saisi_le,"
                        + " annule, motif_annulation) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
                a.id(), a.tenantId(), a.contratId(), a.date(), a.type(), a.montant(), a.motif(), a.saisiPar(),
                a.saisiLe(), a.annule(), a.motifAnnulation()).executeUpdate();
    }

    @Override
    public Optional<Ajustement> ajustement(UUID tenantId, UUID id) {
        return lignes(AJUSTEMENT + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageFlotte::ajustement).findFirst();
    }

    @Override
    public List<Ajustement> ajustementsDuContrat(UUID tenantId, UUID contratId) {
        return lignes(AJUSTEMENT + " where tenant_id = ?1 and contrat_id = ?2", tenantId, contratId).stream()
                .map(StockageFlotte::ajustement).toList();
    }

    private static Ajustement ajustement(Object[] l) {
        return new Ajustement(uuid(l[0]), uuid(l[1]), uuid(l[2]), jour(l[3]), Ajustement.Type.valueOf(texte(l[4])),
                entier(l[5]), texte(l[6]), texte(l[7]), instant(l[8]), vrai(l[9]), texte(l[10]));
    }

    // ------------------------------------------------------------------ jours fériés

    @Override
    public List<JourFerie> feries(UUID tenantId) {
        return lignes("select tenant_id, jour, libelle from flotte_ferie where tenant_id = ?1", tenantId).stream()
                .map(l -> new JourFerie(uuid(l[0]), jour(l[1]), texte(l[2]))).toList();
    }

    @Override
    public void ajouter(JourFerie f) {
        q("insert into flotte_ferie (tenant_id, jour, libelle) values (?1, ?2, ?3)", f.tenantId(), f.date(),
                f.libelle()).executeUpdate();
    }

    @Override
    public void retirer(UUID tenantId, LocalDate date) {
        q("delete from flotte_ferie where tenant_id = ?1 and jour = ?2", tenantId, date).executeUpdate();
    }

    // ------------------------------------------------------------------ papiers

    private static final String DOCUMENT = "select id, tenant_id, vehicule_id, type, reference, emis_le, expire_le,"
            + " cout, notes from flotte_document";

    @Override
    public void sauvegarder(DocumentVehicule d) {
        q("delete from flotte_document where id = ?1 and tenant_id = ?2", d.id(), d.tenantId()).executeUpdate();
        q("insert into flotte_document (id, tenant_id, vehicule_id, type, reference, emis_le, expire_le, cout, notes)"
                        + " values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)", d.id(), d.tenantId(), d.vehiculeId(),
                d.type(), d.reference(), d.emisLe(), d.expireLe(), d.cout(), d.notes()).executeUpdate();
    }

    @Override
    public Optional<DocumentVehicule> document(UUID tenantId, UUID id) {
        return lignes(DOCUMENT + " where tenant_id = ?1 and id = ?2", tenantId, id).stream()
                .map(StockageFlotte::document).findFirst();
    }

    @Override
    public List<DocumentVehicule> documents(UUID tenantId) {
        return lignes(DOCUMENT + " where tenant_id = ?1", tenantId).stream().map(StockageFlotte::document).toList();
    }

    @Override
    public void supprimer(UUID tenantId, UUID id) {
        q("delete from flotte_document where id = ?1 and tenant_id = ?2", id, tenantId).executeUpdate();
    }

    private static DocumentVehicule document(Object[] l) {
        return new DocumentVehicule(uuid(l[0]), uuid(l[1]), uuid(l[2]), TypeDocument.valueOf(texte(l[3])), texte(l[4]),
                jour(l[5]), jour(l[6]), entier(l[7]), texte(l[8]));
    }

    // ------------------------------------------------------------------ numéros

    @Override
    public long prochain(UUID tenantId, String prefixe, int annee) {
        q("insert into flotte_compteur (tenant_id, prefixe, annee, dernier) values (?1, ?2, ?3, 1)"
                + " on duplicate key update dernier = dernier + 1", tenantId, prefixe, annee).executeUpdate();
        return entier(q("select dernier from flotte_compteur where tenant_id = ?1 and prefixe = ?2 and annee = ?3",
                tenantId, prefixe, annee).getSingleResult());
    }
}
