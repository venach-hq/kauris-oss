package com.venach.kauris.flotte;

import com.venach.kauris.flotte.application.PortsFlotte;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.JourFerie;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le stockage de la flotte en mémoire, pour les tests. */
public class FlotteEnMemoire implements PortsFlotte.Vehicules, PortsFlotte.Conducteurs, PortsFlotte.Contrats,
        PortsFlotte.Versements, PortsFlotte.Ajustements, PortsFlotte.Feries, PortsFlotte.Documents,
        PortsFlotte.Numeros {

    final Map<UUID, Vehicule> vehicules = new LinkedHashMap<>();
    final Map<UUID, Conducteur> conducteurs = new LinkedHashMap<>();
    final Map<UUID, Contrat> contrats = new LinkedHashMap<>();
    final Map<UUID, Versement> versements = new LinkedHashMap<>();
    final Map<UUID, Ajustement> ajustements = new LinkedHashMap<>();
    final Map<String, JourFerie> feries = new LinkedHashMap<>();
    final Map<UUID, DocumentVehicule> documents = new LinkedHashMap<>();
    final Map<String, Long> compteurs = new HashMap<>();

    private static <T> Optional<T> du(Map<UUID, T> m, UUID id, java.util.function.Function<T, UUID> tenant, UUID t) {
        return Optional.ofNullable(m.get(id)).filter(x -> tenant.apply(x).equals(t));
    }

    @Override public void sauvegarder(Vehicule v) { vehicules.put(v.id(), v); }
    @Override public Optional<Vehicule> vehicule(UUID t, UUID id) { return du(vehicules, id, Vehicule::tenantId, t); }
    @Override public List<Vehicule> vehicules(UUID t) {
        return vehicules.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }
    @Override public void sauvegarder(Conducteur c) { conducteurs.put(c.id(), c); }
    @Override public Optional<Conducteur> conducteur(UUID t, UUID id) {
        return du(conducteurs, id, Conducteur::tenantId, t);
    }
    @Override public List<Conducteur> conducteurs(UUID t) {
        return conducteurs.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }
    @Override public void sauvegarder(Contrat c) { contrats.put(c.id(), c); }
    @Override public Optional<Contrat> contrat(UUID t, UUID id) { return du(contrats, id, Contrat::tenantId, t); }
    @Override public Optional<Contrat> verrouillerContrat(UUID t, UUID id) { return contrat(t, id); }
    @Override public List<Contrat> contrats(UUID t) {
        return contrats.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }
    @Override public void sauvegarder(Versement v) { versements.put(v.id(), v); }
    @Override public Optional<Versement> versement(UUID t, UUID id) { return du(versements, id, Versement::tenantId, t); }
    @Override public List<Versement> versementsDuContrat(UUID t, UUID c) {
        return versements.values().stream().filter(x -> x.tenantId().equals(t) && x.contratId().equals(c)).toList();
    }
    @Override public List<Versement> versementsEntre(UUID t, LocalDate du, LocalDate au) {
        return versements.values().stream().filter(x -> x.tenantId().equals(t) && !x.date().isBefore(du)
                && !x.date().isAfter(au)).toList();
    }
    @Override public Optional<Versement> parReference(UUID t, String ref) {
        return versements.values().stream().filter(x -> x.tenantId().equals(t) && x.reference().equals(ref))
                .findFirst();
    }
    @Override public void sauvegarder(Ajustement a) { ajustements.put(a.id(), a); }
    @Override public Optional<Ajustement> ajustement(UUID t, UUID id) {
        return du(ajustements, id, Ajustement::tenantId, t);
    }
    @Override public List<Ajustement> ajustementsDuContrat(UUID t, UUID c) {
        return ajustements.values().stream().filter(x -> x.tenantId().equals(t) && x.contratId().equals(c)).toList();
    }
    @Override public List<JourFerie> feries(UUID t) {
        return feries.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }
    @Override public void ajouter(JourFerie f) { feries.put(f.tenantId() + "/" + f.date(), f); }
    @Override public void retirer(UUID t, LocalDate d) { feries.remove(t + "/" + d); }
    @Override public void sauvegarder(DocumentVehicule d) { documents.put(d.id(), d); }
    @Override public Optional<DocumentVehicule> document(UUID t, UUID id) {
        return du(documents, id, DocumentVehicule::tenantId, t);
    }
    @Override public List<DocumentVehicule> documents(UUID t) {
        return documents.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }
    @Override public void supprimer(UUID t, UUID id) { documents.remove(id); }
    @Override public long prochain(UUID t, String prefixe, int annee) {
        return compteurs.merge(t + prefixe + annee, 1L, Long::sum);
    }
}
