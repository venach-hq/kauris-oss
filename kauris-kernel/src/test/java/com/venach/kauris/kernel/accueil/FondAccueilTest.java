package com.venach.kauris.kernel.accueil;

import com.venach.kauris.kernel.accueil.domain.FondAccueil;
import com.venach.kauris.kernel.commun.ErreurMetier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FondAccueilTest {

    @Test
    void lImageEstReconnueASesPremiersOctets() {
        assertEquals("image/png",
                FondAccueil.depuis(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}).typeMime());
        assertEquals("image/jpeg", FondAccueil.depuis(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).typeMime());
        assertEquals("image/webp",
                FondAccueil.depuis(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}).typeMime());
    }

    @Test
    void unFichierDeguiseOuTropLourdEstRefuse() {
        assertThrows(ErreurMetier.class, () -> FondAccueil.depuis("<svg onload=alert(1)>".getBytes()));
        assertThrows(ErreurMetier.class, () -> FondAccueil.depuis(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '}));
        assertThrows(ErreurMetier.class, () -> FondAccueil.depuis(new byte[0]));
        byte[] lourd = new byte[FondAccueil.TAILLE_MAX + 1];
        lourd[0] = (byte) 0xFF;
        lourd[1] = (byte) 0xD8;
        lourd[2] = (byte) 0xFF;
        assertThrows(ErreurMetier.class, () -> FondAccueil.depuis(lourd));
    }
}
