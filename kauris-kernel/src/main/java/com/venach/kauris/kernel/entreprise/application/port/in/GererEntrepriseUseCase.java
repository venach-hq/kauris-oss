package com.venach.kauris.kernel.entreprise.application.port.in;

import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les paramètres de l'entreprise. Les lire est libre pour le code (tout document en a besoin) ; les modifier
 * demande la permission {@code entreprise.modifier}.
 */
public interface GererEntrepriseUseCase {

    /** Ce que le formulaire envoie : des textes, contrôlés par le service. */
    record Saisie(String denomination, String sigle, String forme, String capital, String pays, String ville,
                  String adresse, String boitePostale, String telephone, String email, String siteWeb,
                  Map<String, String> identifiants, String devise, Set<String> autresDevises, String police,
                  String taille, String couleurPrincipale, String couleurTexte, String mentions) {
    }

    /** Les paramètres de l'entreprise ; tant que rien n'est saisi, des valeurs de départ. */
    Entreprise consulter(UUID tenantId);

    Entreprise enregistrer(Acces acteur, Saisie saisie);

    Optional<Logo> logo(UUID tenantId);

    void definirLogo(Acces acteur, byte[] contenu);

    void retirerLogo(Acces acteur);
}
