package com.venach.kauris.paie;

import com.venach.kauris.paie.application.PortsPaie;
import com.venach.kauris.paie.domain.Avance;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.Variables;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le stockage de la paie en mémoire, pour les tests. */
public class PaieEnMemoire implements PortsPaie.Salaries, PortsPaie.Contrats, PortsPaie.Periodes,
        PortsPaie.VariablesDuMois, PortsPaie.Bulletins, PortsPaie.Regles, PortsPaie.Avances, PortsPaie.Numeros {

    final Map<UUID, Salarie> salaries = new LinkedHashMap<>();
    final Map<UUID, ContratTravail> contrats = new LinkedHashMap<>();
    final Map<UUID, PeriodePaie> periodes = new LinkedHashMap<>();
    final Map<String, Variables> variables = new LinkedHashMap<>();
    final Map<UUID, Bulletin> bulletins = new LinkedHashMap<>();
    final Map<UUID, ReglesPaie> regles = new LinkedHashMap<>();
    final Map<UUID, Avance> avances = new LinkedHashMap<>();
    final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public void sauvegarder(Salarie s) {
        salaries.put(s.id(), s);
    }

    @Override
    public Optional<Salarie> salarie(UUID t, UUID id) {
        return Optional.ofNullable(salaries.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public List<Salarie> salaries(UUID t) {
        return salaries.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }

    @Override
    public void sauvegarder(ContratTravail c) {
        contrats.put(c.id(), c);
    }

    @Override
    public Optional<ContratTravail> contrat(UUID t, UUID id) {
        return Optional.ofNullable(contrats.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public List<ContratTravail> contrats(UUID t) {
        return contrats.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }

    @Override
    public void sauvegarder(PeriodePaie p) {
        periodes.put(p.id(), p);
    }

    @Override
    public Optional<PeriodePaie> periode(UUID t, UUID id) {
        return Optional.ofNullable(periodes.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public Optional<PeriodePaie> verrouiller(UUID t, UUID id) {
        return periode(t, id);
    }

    @Override
    public Optional<PeriodePaie> parMois(UUID t, YearMonth mois) {
        return periodes.values().stream().filter(x -> x.tenantId().equals(t) && x.mois().equals(mois)).findFirst();
    }

    @Override
    public List<PeriodePaie> periodes(UUID t) {
        return periodes.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }

    @Override
    public void sauvegarder(Variables v) {
        String cle = v.periodeId() + "/" + v.salarieId();
        if (v.vides()) {
            variables.remove(cle);
        } else {
            variables.put(cle, v);
        }
    }

    @Override
    public List<Variables> variables(UUID t, UUID periodeId) {
        return variables.values().stream().filter(x -> x.tenantId().equals(t) && x.periodeId().equals(periodeId))
                .toList();
    }

    @Override
    public void ajouter(Bulletin b) {
        bulletins.put(b.id(), b);
    }

    @Override
    public List<Bulletin> bulletins(UUID t, UUID periodeId) {
        return bulletins.values().stream().filter(x -> x.tenantId().equals(t) && x.periodeId().equals(periodeId))
                .toList();
    }

    @Override
    public Optional<Bulletin> bulletin(UUID t, UUID id) {
        return Optional.ofNullable(bulletins.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public List<Bulletin> bulletinsDuSalarie(UUID t, UUID salarieId) {
        return bulletins.values().stream().filter(x -> x.tenantId().equals(t) && x.salarieId().equals(salarieId))
                .toList();
    }

    @Override
    public void supprimer(UUID t, UUID periodeId) {
        bulletins.values().removeIf(x -> x.tenantId().equals(t) && x.periodeId().equals(periodeId));
    }

    @Override
    public void sauvegarder(ReglesPaie r) {
        regles.put(r.id(), r);
    }

    @Override
    public Optional<ReglesPaie> regles(UUID t, UUID id) {
        return Optional.ofNullable(regles.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public List<ReglesPaie> toutes(UUID t) {
        return regles.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }

    @Override
    public void supprimerRegles(UUID t, UUID id) {
        regles(t, id).ifPresent(x -> regles.remove(id));
    }

    @Override
    public void sauvegarder(Avance a) {
        avances.put(a.id(), a);
    }

    @Override
    public Optional<Avance> avance(UUID t, UUID id) {
        return Optional.ofNullable(avances.get(id)).filter(x -> x.tenantId().equals(t));
    }

    @Override
    public List<Avance> avances(UUID t) {
        return avances.values().stream().filter(x -> x.tenantId().equals(t)).toList();
    }

    @Override
    public long prochain(UUID t, String prefixe, int annee) {
        return compteurs.merge(t + "/" + prefixe + "/" + annee, 1L, Long::sum);
    }
}
