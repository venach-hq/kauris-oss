package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class KaurisStandaloneApplicationTest {

    @Autowired
    private RegistreModules registre;

    @Test
    void demarreAvecLeNoyau() {
        assertTrue(registre.present("kernel"));
    }
}
