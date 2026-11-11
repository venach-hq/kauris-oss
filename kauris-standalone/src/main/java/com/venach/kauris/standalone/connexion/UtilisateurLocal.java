package com.venach.kauris.standalone.connexion;

import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase.CompteConnectable;
import com.venach.kauris.kernel.organisation.domain.Identite;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * La personne connectée par un compte local. Elle ne porte aucun droit : ses rôles dépendent de
 * l'organisation et se lisent dans le noyau à partir de son {@link #identite()}.
 */
public final class UtilisateurLocal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final UUID compteId;
    private final String email;
    private final boolean actif;
    private String empreinteMotDePasse;

    UtilisateurLocal(CompteConnectable compte) {
        this.compteId = compte.compteId();
        this.email = compte.email();
        this.actif = compte.actif();
        this.empreinteMotDePasse = compte.empreinteMotDePasse();
    }

    public UUID compteId() {
        return compteId;
    }

    public Identite identite() {
        return Identite.locale(compteId.toString());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return empreinteMotDePasse;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Faux si la personne n'est plus membre actif d'aucune organisation. */
    @Override
    public boolean isEnabled() {
        return actif;
    }

    /** L'empreinte ne reste pas dans la session une fois la connexion vérifiée. */
    @Override
    public void eraseCredentials() {
        this.empreinteMotDePasse = null;
    }
}
