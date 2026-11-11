package com.venach.kauris.kernel.entreprise.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "kernel_entreprise")
public class EntrepriseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "denomination", length = 120, nullable = false)
    String denomination;

    @Column(name = "sigle", length = 60, nullable = false)
    String sigle;

    @Column(name = "forme_juridique", length = 30, nullable = false)
    String formeJuridique;

    @Column(name = "capital")
    Long capital;

    @Column(name = "pays", length = 2, nullable = false)
    String pays;

    @Column(name = "ville", length = 60, nullable = false)
    String ville;

    @Column(name = "adresse", length = 190, nullable = false)
    String adresse;

    @Column(name = "boite_postale", length = 60, nullable = false)
    String boitePostale;

    @Column(name = "telephone", length = 60, nullable = false)
    String telephone;

    @Column(name = "email", length = 190, nullable = false)
    String email;

    @Column(name = "site_web", length = 190, nullable = false)
    String siteWeb;

    @Column(name = "devise", length = 3, nullable = false)
    String devise;

    /** Codes ISO séparés par des virgules. */
    @Column(name = "autres_devises", length = 100, nullable = false)
    String autresDevises;

    @Column(name = "theme_modele", length = 20, nullable = false)
    String themeModele;

    @Column(name = "police_titres", length = 40, nullable = false)
    String policeTitres;

    @Column(name = "police_texte", length = 40, nullable = false)
    String policeTexte;

    @Column(name = "taille_texte", nullable = false)
    int tailleTexte;

    @Column(name = "couleur_principale", length = 7, nullable = false)
    String couleurPrincipale;

    @Column(name = "couleur_texte", length = 7, nullable = false)
    String couleurTexte;

    @Column(name = "couleur_entetes", length = 7, nullable = false)
    String couleurEntetes;

    @Column(name = "position_logo", length = 10, nullable = false)
    String positionLogo;

    @Column(name = "style_titre", length = 12, nullable = false)
    String styleTitre;

    @Column(name = "banque", length = 80, nullable = false)
    String banque;

    @Column(name = "banque_titulaire", length = 120, nullable = false)
    String banqueTitulaire;

    @Column(name = "banque_compte", length = 60, nullable = false)
    String banqueCompte;

    @Column(name = "banque_bic", length = 20, nullable = false)
    String banqueBic;

    @Column(name = "banque_agence", length = 120, nullable = false)
    String banqueAgence;

    @Column(name = "mentions", length = 300, nullable = false)
    String mentions;

    protected EntrepriseEntity() {
    }
}
