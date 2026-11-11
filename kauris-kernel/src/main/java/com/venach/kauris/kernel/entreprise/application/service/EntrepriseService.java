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
import com.venach.kauris.kernel.entreprise.domain.CoordonneesBancaires;
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
            // Le thème se règle dans son propre écran : on garde celui qui est enregistré.
            ThemeDocument theme = consulter(acteur.tenantId()).theme();
            Entreprise entreprise = new Entreprise(acteur.tenantId(), s.denomination(), s.sigle(),
                    parmi(FormeJuridique.class, s.forme(), "Forme juridique"), capital(s.capital()), pays, s.ville(),
                    s.adresse(), s.boitePostale(), s.telephone(), s.email(), s.siteWeb(), s.identifiants(), devise,
                    autres, theme, new CoordonneesBancaires(s.banque(), s.titulaireCompte(), s.numeroCompte(),
                    s.bic(), s.agence()), s.mentions(), aUnLogo);
            entreprises.sauvegarder(entreprise);
            return entreprise;
        });
    }

    @Override
    public ThemeDocument lire(SaisieTheme s) {
        ThemeDocument.Modele modele = parmi(ThemeDocument.Modele.class, s.modele(), "Modèle de thème");
        if (modele != ThemeDocument.Modele.PERSONNALISE) {
            return ThemeDocument.du(modele);
        }
        return new ThemeDocument(modele, s.policeTitres(), s.policeTexte(), entier(s.taille()),
                s.couleurPrincipale(), s.couleurEntetes(), s.couleurTexte(),
                parmi(ThemeDocument.PositionLogo.class, s.logo(), "Place du logo"),
                parmi(ThemeDocument.StyleTitre.class, s.titre(), "Style du titre"));
    }

    @Override
    public ThemeDocument definirTheme(Acces acteur, SaisieTheme saisie) {
        exigerDeModifier(acteur);
        ThemeDocument theme = lire(saisie);
        transaction.executer(() -> entreprises.sauvegarder(consulter(acteur.tenantId()).avecTheme(theme)));
        return theme;
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

    /** Chiffres seuls, ou groupés par trois avec des espaces ou des points. */
    private static final java.util.regex.Pattern MONTANT =
            java.util.regex.Pattern.compile("\\d+|\\d{1,3}( \\d{3})+|\\d{1,3}(\\.\\d{3})+");

    /**
     * « 1 000 000 », « 1.000.000 » ou « 1000000 » ; vide = pas de capital. Les séparateurs ne valent qu'entre
     * groupes de trois chiffres : « 1 000 000,50 » est refusé, jamais lu comme 100 000 050.
     */
    private static Long capital(String saisi) {
        String propre = saisi == null ? "" : saisi.strip().replaceAll("[\\u00a0\\u202f]", " ");
        if (propre.isEmpty()) {
            return null;
        }
        if (!MONTANT.matcher(propre).matches() || propre.replaceAll("[ .]", "").length() > 15) {
            throw new ErreurMetier("Capital social : un montant en chiffres, sans décimales (par exemple 1 000 000).");
        }
        return Long.parseLong(propre.replaceAll("[ .]", ""));
    }

    private static int entier(String saisi) {
        try {
            return Integer.parseInt(saisi == null ? "" : saisi.strip());
        } catch (NumberFormatException illisible) {
            throw new ErreurMetier("Taille du texte : un nombre de points.");
        }
    }
}
