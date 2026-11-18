package com.venach.kauris.compta;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** La comptabilité en mémoire, pour les tests du service. */
public class ComptaEnMemoire implements PortsCompta.Comptes, PortsCompta.Journaux, PortsCompta.Exercices,
        PortsCompta.Ecritures, PortsCompta.Operations, PortsCompta.Imputations, PortsCompta.ParametresCompta,
        PortsCompta.Numeros {

    final Map<String, Compte> comptes = new java.util.TreeMap<>();
    final Map<String, Journal> journaux = new LinkedHashMap<>();
    final Map<String, Exercice> exercices = new HashMap<>();
    final Map<UUID, Ecriture> ecritures = new LinkedHashMap<>();
    final Map<UUID, OperationRecue> operations = new LinkedHashMap<>();
    final Map<UUID, List<Imputation>> imputations = new HashMap<>();
    final Map<UUID, Parametres> parametres = new HashMap<>();
    final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public List<Compte> comptes(UUID t) {
        return comptes.values().stream().filter(c -> c.tenantId().equals(t)).toList();
    }

    @Override
    public Optional<Compte> compte(UUID t, String n) {
        return Optional.ofNullable(comptes.get(t + n));
    }

    @Override
    public void sauvegarder(Compte c) {
        comptes.put(c.tenantId() + c.numero(), c);
    }

    @Override
    public List<Journal> journaux(UUID t) {
        return journaux.values().stream().filter(j -> j.tenantId().equals(t)).toList();
    }

    @Override
    public void sauvegarder(Journal j) {
        journaux.put(j.tenantId() + j.code(), j);
    }

    @Override
    public List<Exercice> exercices(UUID t) {
        return exercices.values().stream().filter(e -> e.tenantId().equals(t))
                .sorted(Comparator.comparingInt(Exercice::annee)).toList();
    }

    @Override
    public Optional<Exercice> exercice(UUID t, int annee) {
        return Optional.ofNullable(exercices.get(t + ":" + annee));
    }

    @Override
    public void sauvegarder(Exercice e) {
        exercices.put(e.tenantId() + ":" + e.annee(), e);
    }

    @Override
    public Optional<Ecriture> ecriture(UUID t, UUID id) {
        return Optional.ofNullable(ecritures.get(id)).filter(e -> e.tenantId().equals(t));
    }

    @Override
    public void sauvegarder(Ecriture e) {
        if (e.validee() && ecritures.values().stream().anyMatch(x -> !x.id().equals(e.id())
                && x.tenantId().equals(e.tenantId()) && x.numero().equals(e.numero()))) {
            throw new IllegalStateException("Numéro en double : " + e.numero());
        }
        ecritures.put(e.id(), e);
    }

    @Override
    public void supprimer(UUID t, UUID id) {
        ecritures.remove(id);
    }

    @Override
    public List<Ecriture> ecritures(UUID t, PortsCompta.Filtre f) {
        return ecritures.values().stream().filter(e -> e.tenantId().equals(t))
                .filter(e -> f.journal() == null || f.journal().isBlank() || e.journal().equals(f.journal()))
                .filter(e -> f.statut() == null || e.statut() == f.statut())
                .filter(e -> f.du() == null || !e.date().isBefore(f.du()))
                .filter(e -> f.au() == null || !e.date().isAfter(f.au()))
                .sorted(Comparator.comparing(Ecriture::date).reversed()).limit(f.limite()).toList();
    }

    private List<Mouvement> tous(UUID t) {
        List<Mouvement> m = new ArrayList<>();
        ecritures.values().stream().filter(e -> e.tenantId().equals(t))
                .sorted(Comparator.comparing(Ecriture::date).thenComparing(Ecriture::creeLe)).forEach(e -> {
                    for (int i = 0; i < e.lignes().size(); i++) {
                        LigneEcriture l = e.lignes().get(i);
                        m.add(new Mouvement(e.id(), i, e.journal(), e.numero(), e.date(), e.piece(),
                                l.libelle().isEmpty() ? e.libelle() : l.libelle(), l.compte(), l.tiers(),
                                l.tiersNom(), l.debit(), l.credit(), l.lettrage(), e.validee(), e.origine()));
                    }
                });
        return m;
    }

    @Override
    public List<Mouvement> mouvements(UUID t, LocalDate du, LocalDate au) {
        return tous(t).stream().filter(m -> !m.date().isBefore(du) && !m.date().isAfter(au)).toList();
    }

    @Override
    public List<Mouvement> mouvementsDuCompte(UUID t, String compte, LocalDate du, LocalDate au) {
        return mouvements(t, du, au).stream().filter(m -> m.compte().equals(compte)).toList();
    }

    @Override
    public List<Ecriture> brouillard(UUID t, LocalDate du, LocalDate au) {
        return ecritures.values().stream().filter(e -> e.tenantId().equals(t) && !e.validee()
                && !e.date().isBefore(du) && !e.date().isAfter(au)).sorted(Comparator.comparing(Ecriture::date))
                .toList();
    }

    @Override
    public Optional<Ecriture> inverseDe(UUID t, UUID id) {
        return ecritures.values().stream().filter(e -> e.tenantId().equals(t) && id.equals(e.inverseDe()))
                .findFirst();
    }

    @Override
    public Optional<Ecriture> parReference(UUID t, String origine, String reference) {
        return ecritures.values().stream().filter(e -> e.tenantId().equals(t) && e.origine().equals(origine)
                && e.reference().equals(reference)).reduce((a, b) -> b);
    }

    @Override
    public Set<String> lettrages(UUID t, String compte) {
        Set<String> s = new HashSet<>();
        tous(t).stream().filter(m -> m.compte().equals(compte) && m.lettree()).forEach(m -> s.add(m.lettrage()));
        return s;
    }

    @Override
    public Optional<OperationRecue> operation(UUID t, String origine, String reference) {
        return operations.values().stream().filter(o -> o.tenantId().equals(t)
                && o.operation().origine().equals(origine) && o.operation().reference().equals(reference))
                .findFirst();
    }

    @Override
    public Optional<OperationRecue> parId(UUID t, UUID id) {
        return Optional.ofNullable(operations.get(id)).filter(o -> o.tenantId().equals(t));
    }

    @Override
    public void sauvegarder(OperationRecue o) {
        operations.put(o.id(), o);
    }

    @Override
    public List<OperationRecue> aRevoir(UUID t) {
        return operations.values().stream().filter(o -> o.tenantId().equals(t)
                && (o.statut() == OperationRecue.Statut.A_REVOIR
                || o.statut() == OperationRecue.Statut.ANNULATION_A_REVOIR)).toList();
    }

    @Override
    public List<String> precisionsDeTresorerie(UUID t) {
        Set<String> s = new TreeSet<>();
        operations.values().stream().filter(o -> o.tenantId().equals(t)).forEach(o -> o.operation().lignes().stream()
                .filter(l -> l.role() == OperationComptable.Role.TRESORERIE && !l.precision().isEmpty())
                .forEach(l -> java.util.Arrays.stream(l.precision().split("\\|")).filter(x -> !x.isBlank())
                        .forEach(s::add)));
        return List.copyOf(s);
    }

    @Override
    public List<Imputation> imputations(UUID t) {
        return imputations.getOrDefault(t, List.of());
    }

    @Override
    public void remplacer(UUID t, List<Imputation> liste) {
        imputations.put(t, List.copyOf(liste));
    }

    @Override
    public Optional<Parametres> parametres(UUID t) {
        return Optional.ofNullable(parametres.get(t));
    }

    @Override
    public void sauvegarder(Parametres p) {
        parametres.put(p.tenantId(), p);
    }

    @Override
    public long prochain(UUID t, String journal, int annee) {
        return compteurs.merge(t + journal + annee, 1L, Long::sum);
    }
}
