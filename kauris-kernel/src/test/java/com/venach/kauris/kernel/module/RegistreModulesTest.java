package com.venach.kauris.kernel.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistreModulesTest {

    private static ModuleKauris module(String code) {
        return new ModuleKauris() {
            public String code() { return code; }
            public String nom() { return code; }
            public String version() { return "1.0.0"; }
        };
    }

    @Test
    void modulesTriesParCode() {
        RegistreModules r = new RegistreModules(List.of(module("rh-paie-tg"), new ModuleNoyau("0.1.0")));
        assertEquals(List.of("kernel", "rh-paie-tg"), r.tous().stream().map(ModuleKauris::code).toList());
        assertTrue(r.present("rh-paie-tg"));
    }

    @Test
    void doublonRefuse() {
        assertThrows(IllegalStateException.class,
                () -> new RegistreModules(List.of(module("flotte"), module("flotte"))));
    }

    @Test
    void codeInvalideRefuse() {
        assertThrows(IllegalStateException.class, () -> new RegistreModules(List.of(module("Paie TG"))));
        assertThrows(IllegalStateException.class, () -> new RegistreModules(List.of(module(null))));
    }
}
