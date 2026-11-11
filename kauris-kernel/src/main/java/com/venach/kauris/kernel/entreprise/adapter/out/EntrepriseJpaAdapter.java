package com.venach.kauris.kernel.entreprise.adapter.out;

import com.venach.kauris.kernel.entreprise.application.port.out.EntrepriseRepository;
import com.venach.kauris.kernel.entreprise.domain.CoordonneesBancaires;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.FormeJuridique;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stockage des paramètres de l'entreprise. Les identifiants légaux sont réécrits en entier à chaque
 * enregistrement. Le logo est lu et écrit en SQL direct : c'est un simple bloc d'octets, hors des entités.
 */
public class EntrepriseJpaAdapter implements EntrepriseRepository {

    private final EntityManager em;

    public EntrepriseJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Entreprise> trouver(UUID tenantId) {
        EntrepriseEntity e = em.find(EntrepriseEntity.class, tenantId);
        if (e == null) {
            return Optional.empty();
        }
        Map<String, String> identifiants = new LinkedHashMap<>();
        em.createQuery("select i from EntrepriseIdentifiantEntity i where i.tenantId = :tenant",
                        EntrepriseIdentifiantEntity.class)
                .setParameter("tenant", tenantId)
                .getResultList().forEach(i -> identifiants.put(i.code, i.valeur));
        Set<Devise> autres = EnumSet.noneOf(Devise.class);
        Arrays.stream(e.autresDevises.split(",")).map(String::strip).filter(code -> !code.isEmpty())
                .forEach(code -> autres.add(Devise.valueOf(code)));
        return Optional.of(new Entreprise(e.tenantId, e.denomination, e.sigle,
                FormeJuridique.valueOf(e.formeJuridique), e.capital, Pays.parCode(e.pays).orElseThrow(), e.ville,
                e.adresse, e.boitePostale, e.telephone, e.email, e.siteWeb, identifiants, Devise.valueOf(e.devise),
                autres, new ThemeDocument(ThemeDocument.Modele.valueOf(e.themeModele), e.policeTitres,
                e.policeTexte, e.tailleTexte, e.couleurPrincipale, e.couleurEntetes, e.couleurTexte,
                ThemeDocument.PositionLogo.valueOf(e.positionLogo), ThemeDocument.StyleTitre.valueOf(e.styleTitre)),
                new CoordonneesBancaires(e.banque, e.banqueTitulaire, e.banqueCompte, e.banqueBic, e.banqueAgence),
                e.mentions, aUnLogo(tenantId)));
    }

    @Override
    public void sauvegarder(Entreprise a) {
        EntrepriseEntity e = new EntrepriseEntity();
        e.tenantId = a.tenantId();
        e.denomination = a.denomination();
        e.sigle = a.sigle();
        e.formeJuridique = a.forme().name();
        e.capital = a.capital();
        e.pays = a.pays().code();
        e.ville = a.ville();
        e.adresse = a.adresse();
        e.boitePostale = a.boitePostale();
        e.telephone = a.telephone();
        e.email = a.email();
        e.siteWeb = a.siteWeb();
        e.devise = a.devise().code();
        e.autresDevises = a.autresDevises().stream().map(Devise::code).sorted().collect(Collectors.joining(","));
        ThemeDocument t = a.theme();
        e.themeModele = t.modele().name();
        e.policeTitres = t.policeTitres();
        e.policeTexte = t.policeTexte();
        e.tailleTexte = t.taille();
        e.couleurPrincipale = t.couleurPrincipale();
        e.couleurEntetes = t.couleurEntetes();
        e.couleurTexte = t.couleurTexte();
        e.positionLogo = t.logo().name();
        e.styleTitre = t.titre().name();
        e.banque = a.banque().banque();
        e.banqueTitulaire = a.banque().titulaire();
        e.banqueCompte = a.banque().compte();
        e.banqueBic = a.banque().bic();
        e.banqueAgence = a.banque().agence();
        e.mentions = a.mentions();
        em.merge(e);
        // L'entreprise doit exister en base avant ses identifiants (clé étrangère).
        em.flush();

        em.createQuery("delete from EntrepriseIdentifiantEntity i where i.tenantId = :tenant")
                .setParameter("tenant", a.tenantId())
                .executeUpdate();
        a.identifiants().forEach((code, valeur) -> {
            EntrepriseIdentifiantEntity i = new EntrepriseIdentifiantEntity();
            i.id = UUID.randomUUID();
            i.tenantId = a.tenantId();
            i.code = code;
            i.valeur = valeur;
            em.persist(i);
        });
    }

    // ------------------------------------------------------------------ logo

    private boolean aUnLogo(UUID tenantId) {
        return !em.createNativeQuery("select 1 from kernel_entreprise_logo where tenant_id = :tenant")
                .setParameter("tenant", tenantId.toString())
                .getResultList().isEmpty();
    }

    @Override
    public Optional<Logo> logo(UUID tenantId) {
        List<?> lignes = em.createNativeQuery(
                        "select type_mime, contenu from kernel_entreprise_logo where tenant_id = :tenant")
                .setParameter("tenant", tenantId.toString())
                .getResultList();
        if (lignes.isEmpty()) {
            return Optional.empty();
        }
        Object[] ligne = (Object[]) lignes.get(0);
        return Optional.of(new Logo((byte[]) ligne[1], (String) ligne[0]));
    }

    @Override
    public void enregistrerLogo(UUID tenantId, Logo logo) {
        retirerLogo(tenantId);
        em.createNativeQuery("insert into kernel_entreprise_logo (tenant_id, type_mime, contenu) "
                        + "values (:tenant, :type, :contenu)")
                .setParameter("tenant", tenantId.toString())
                .setParameter("type", logo.typeMime())
                .setParameter("contenu", logo.contenu())
                .executeUpdate();
    }

    @Override
    public void retirerLogo(UUID tenantId) {
        em.createNativeQuery("delete from kernel_entreprise_logo where tenant_id = :tenant")
                .setParameter("tenant", tenantId.toString())
                .executeUpdate();
    }
}
