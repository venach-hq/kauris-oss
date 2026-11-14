package com.venach.kauris.kernel.module;

import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;
import java.util.Optional;

/**
 * Ce qu'un module montre sur le tableau de bord d'accueil : quelques chiffres utiles et des raccourcis. Un module
 * en déclare un bean ; il rend vide quand la personne n'a pas la permission de voir ses chiffres.
 */
@FunctionalInterface
public interface SectionAccueil {

    enum Ton { NEUTRE, BON, ALERTE }

    /** Un chiffre : « Reste à encaisser · 250 000 F CFA · dont 80 000 en retard », avec son lien. */
    record Chiffre(String libelle, String valeur, String detail, String lien, Ton ton) {

        public Chiffre {
            detail = detail == null ? "" : detail;
            ton = ton == null ? Ton.NEUTRE : ton;
        }

        public static Chiffre de(String libelle, String valeur, String lien) {
            return new Chiffre(libelle, valeur, "", lien, Ton.NEUTRE);
        }
    }

    /** Un bouton vers une action fréquente : « Nouvelle facture », « Ouvrir ma caisse ». */
    record Raccourci(String libelle, String lien) {
    }

    /** Une section du tableau de bord, rangée par {@code ordre} croissant. */
    record Section(String titre, String lien, int ordre, List<Chiffre> chiffres, List<Raccourci> raccourcis) {

        public Section {
            chiffres = List.copyOf(chiffres);
            raccourcis = List.copyOf(raccourcis);
        }

        public boolean alerte() {
            return chiffres.stream().anyMatch(c -> c.ton() == Ton.ALERTE);
        }
    }

    Optional<Section> pour(Acces acces);
}
