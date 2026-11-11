package com.venach.kauris.kernel.entreprise.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/**
 * Le logo de l'entreprise. Seuls PNG et JPEG sont acceptés, reconnus à leurs premiers octets et non au nom
 * du fichier : un fichier déguisé n'est jamais renvoyé à un navigateur comme une image.
 */
public record Logo(byte[] contenu, String typeMime) {

    public static final int TAILLE_MAX = 512 * 1024;

    public static Logo depuis(byte[] contenu) {
        if (contenu == null || contenu.length == 0) {
            throw new ErreurMetier("Choisissez un fichier image.");
        }
        if (contenu.length > TAILLE_MAX) {
            throw new ErreurMetier("Logo trop lourd : 512 Ko au plus.");
        }
        if (commencePar(contenu, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new Logo(contenu.clone(), "image/png");
        }
        if (commencePar(contenu, 0xFF, 0xD8, 0xFF)) {
            return new Logo(contenu.clone(), "image/jpeg");
        }
        throw new ErreurMetier("Format non reconnu : le logo doit être une image PNG ou JPEG.");
    }

    private static boolean commencePar(byte[] contenu, int... signature) {
        if (contenu.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((contenu[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
