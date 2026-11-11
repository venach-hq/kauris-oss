package com.venach.kauris.kernel.entreprise.application.service;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.application.port.out.EntrepriseRepository;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.FormeJuridique;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument.Police;
import com.venach.kauris.kernel.module.PermissionsNoyau;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class EntrepriseService implements GererEntrepriseUseCase {

    private final EntrepriseRepository entreprises;
    private final OrganisationRepository organisations;
    private final UniteDeTravail transaction;

    public EntrepriseService(EntrepriseRepository entreprises, OrganisationRepository organisations,
                             UniteDeTravail transaction) {
        this.entreprises = entreprises;
        this.organisations = organisations;
        this.transaction = transaction;
    }

    @Override
    public Entreprise consulter(UUID tenantId) {
        return entreprises.trouver(tenantId).orElseGet(() -> Entreprise.parDefaut(tenantId,
                organisations.trouver(tenantId).map(Organisation::nom).orElse("Mon entreprise")));
    }

    @Override
    public Entreprise enregistrer(Acces acteur, Saisie s) {
        exigerDeModifier(acteur);
        return transaction.executer(() -> {
            Pays pays = Pays.parCode(s.pays()).orElseThrow(() -> new ErreurMetier("Pays inconnu."));
            Devise devise = devise(s.devise());
            Set<Devise> autres = EnumSet.noneOf(Devise.class);
            if (s.autresDevises() != null) {
                s.autresDevises().forEach(code -> autres.add(devise(code)));
            }
            boolean aUnLogo = entreprises.logo(acteur.tenantId()).isPresent();
            Entreprise entreprise = new Entreprise(acteur.tenantId(), s.denomination(), s.sigle(),
                    parmi(FormeJuridique.class, s.forme(), "Forme juridique"), capital(s.capital()), pays, s.ville(),
                    s.adresse(), s.boitePostale(), s.telephone(), s.email(), s.siteWeb(), s.identifiants(), devise,
                    autres, new ThemeDocument(parmi(Police.class, s.police(), "Police"), entier(s.taille()),
                    s.couleurPrincipale(), s.couleurTexte()), s.mentions(), aUnLogo);
            entreprises.sauvegarder(entreprise);
            return entreprise;
        });
    }

    @Override
    public Optional<Logo> logo(UUID tenantId) {
        return entreprises.logo(tenantId);
    }

    @Override
    public void definirLogo(Acces acteur, byte[] contenu) {
        exigerDeModifier(acteur);
        Logo logo = Logo.depuis(contenu);
        transaction.executer(() -> {
            // Le logo se rattache aux paramètres : on les crée s'ils n'existent pas encore.
            if (entreprises.trouver(acteur.tenantId()).isEmpty()) {
                entreprises.sauvegarder(consulter(acteur.tenantId()));
            }
            entreprises.enregistrerLogo(acteur.tenantId(), logo);
        });
    }

    @Override
    public void retirerLogo(Acces acteur) {
        exigerDeModifier(acteur);
        transaction.executer(() -> entreprises.retirerLogo(acteur.tenantId()));
    }

    private static void exigerDeModifier(Acces acteur) {
        if (!acteur.peut(PermissionsNoyau.ENTREPRISE_MODIFIER)) {
            throw new AccesRefuse("Vous n'avez pas la permission de modifier les paramètres de l'entreprise.");
        }
    }

    private static Devise devise(String code) {
        return parmi(Devise.class, code, "Monnaie");
    }

    private static <E extends Enum<E>> E parmi(Class<E> type, String code, String champ) {
        try {
            return Enum.valueOf(type, code == null ? "" : code.strip());
        } catch (IllegalArgumentException inconnu) {
            throw new ErreurMetier(champ + " : valeur inconnue.");
        }
    }

    /** « 1 000 000 », « 1.000.000 » ou « 1000000 » ; vide = pas de capital. */
    private static Long capital(String saisi) {
        String chiffres = saisi == null ? "" : saisi.replaceAll("[\\s\\u00a0.,]", "");
        if (chiffres.isEmpty()) {
            return null;
        }
        if (!chiffres.matches("\\d{1,15}")) {
            throw new ErreurMetier("Capital social : un montant en chiffres, sans décimales.");
        }
        return Long.parseLong(chiffres);
    }

    private static int entier(String saisi) {
        try {
            return Integer.parseInt(saisi == null ? "" : saisi.strip());
        } catch (NumberFormatException illisible) {
            throw new ErreurMetier("Taille du texte : un nombre de points.");
        }
    }
}
