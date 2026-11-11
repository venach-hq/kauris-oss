package com.venach.kauris.kernel.connexion.local.application.port.out;

/** Calcul et vérification de l'empreinte d'un mot de passe (jamais de mot de passe en clair en base). */
public interface EmpreinteMotDePasse {

    String calculer(String motDePasse);

    boolean correspond(String motDePasse, String empreinte);
}
