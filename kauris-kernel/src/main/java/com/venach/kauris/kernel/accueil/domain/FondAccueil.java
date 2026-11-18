package com.venach.kauris.kernel.accueil.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/**
 * L'image de fond du tableau de bord d'un membre. PNG, JPEG ou WebP, reconnus à leurs premiers octets et non au
 * nom du fichier : un fichier déguisé n'est jamais renvoyé à un navigateur comme une image.
 */
public record FondAccueil(byte[] contenu, String typeMime) {

    public static final int TAILLE_MAX = 3 * 1024 * 1024;
    public static final String MESSAGE_TROP_LOURD = "Image trop lourde : 3 Mo au plus. Choisissez une photo plus légère.";

    public static FondAccueil depuis(byte[] contenu) {
        if (contenu == null || contenu.length == 0) {
            throw new ErreurMetier("Choisissez un fichier image.");
        }
        if (contenu.length > TAILLE_MAX) {
            throw new ErreurMetier(MESSAGE_TROP_LOURD);
        }
        if (commencePar(contenu, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new FondAccueil(contenu.clone(), "image/png");
        }
        if (commencePar(contenu, 0, 0xFF, 0xD8, 0xFF)) {
            return new FondAccueil(contenu.clone(), "image/jpeg");
        }
        if (commencePar(contenu, 0, 'R', 'I', 'F', 'F') && commencePar(contenu, 8, 'W', 'E', 'B', 'P')) {
            return new FondAccueil(contenu.clone(), "image/webp");
        }
        throw new ErreurMetier("Format non reconnu : l'image doit être en PNG, JPEG ou WebP.");
    }

    private static boolean commencePar(byte[] contenu, int depart, int... signature) {
        if (contenu.length < depart + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((contenu[depart + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
