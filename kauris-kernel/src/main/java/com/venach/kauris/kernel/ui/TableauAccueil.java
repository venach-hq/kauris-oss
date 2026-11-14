package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.module.SectionAccueil;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Le tableau de bord d'accueil : les sections de tous les modules installés. Une section en panne est sautée
 * (et notée dans le journal) : l'accueil s'affiche toujours.
 */
public class TableauAccueil {

    private static final Logger log = LoggerFactory.getLogger(TableauAccueil.class);

    private final ObjectProvider<SectionAccueil> sections;

    public TableauAccueil(ObjectProvider<SectionAccueil> sections) {
        this.sections = sections;
    }

    /** « samedi 14 novembre 2026 » */
    public static String jour(java.time.Clock horloge) {
        return java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH)
                .format(java.time.LocalDate.now(horloge));
    }

    public List<SectionAccueil.Section> sections(Acces acces) {
        List<SectionAccueil.Section> l = new ArrayList<>();
        for (SectionAccueil s : sections.orderedStream().toList()) {
            try {
                Optional<SectionAccueil.Section> section = s.pour(acces);
                section.ifPresent(l::add);
            } catch (RuntimeException e) {
                log.warn("Accueil : section {} sautée pour l'organisation {}", s.getClass().getSimpleName(),
                        acces.tenantId(), e);
            }
        }
        l.sort(Comparator.comparingInt(SectionAccueil.Section::ordre));
        return l;
    }
}
