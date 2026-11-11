package com.venach.kauris.kernel.module;

/**
 * Une permission : le droit de voir ou de modifier une partie des informations de l'entreprise. Chaque module
 * déclare les siennes ; le propriétaire les coche dans les rôles qu'il crée.
 *
 * @param code    identifiant stable, « module.action » : {@code entreprise.modifier}, {@code paie.consulter}
 * @param libelle ce que la permission autorise, en une phrase lisible par le propriétaire
 * @param groupe  titre sous lequel elle est rangée à l'écran (en général le nom du module)
 */
public record Permission(String code, String libelle, String groupe, Niveau niveau) {

    /** Ce que la permission ouvre ; fixe aussi quels rôles de base l'ont d'office. */
    public enum Niveau {
        /** Voir sans modifier : tous les rôles de base. */
        CONSULTER,
        /** Saisir et modifier : gestionnaire et au-dessus. */
        MODIFIER,
        /** Réglages sensibles : propriétaire et administrateur seulement, ou un rôle qui la coche. */
        ADMINISTRER
    }

    public Permission {
        if (code == null || !code.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+") || code.length() > 60) {
            throw new IllegalArgumentException("Code de permission invalide : " + code);
        }
        if (libelle == null || libelle.isBlank() || groupe == null || groupe.isBlank() || niveau == null) {
            throw new IllegalArgumentException("Permission incomplète : " + code);
        }
    }

    public static Permission consulter(String code, String libelle, String groupe) {
        return new Permission(code, libelle, groupe, Niveau.CONSULTER);
    }

    public static Permission modifier(String code, String libelle, String groupe) {
        return new Permission(code, libelle, groupe, Niveau.MODIFIER);
    }

    public static Permission administrer(String code, String libelle, String groupe) {
        return new Permission(code, libelle, groupe, Niveau.ADMINISTRER);
    }
}
