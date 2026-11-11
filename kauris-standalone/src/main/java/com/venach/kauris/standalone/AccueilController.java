package com.venach.kauris.standalone;

import com.venach.kauris.kernel.module.RegistreModules;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Page d'accueil provisoire (phase 0) : l'interface commune arrive en phase 1. */
@RestController
public class AccueilController {

    private final RegistreModules registre;

    public AccueilController(RegistreModules registre) {
        this.registre = registre;
    }

    @GetMapping("/")
    public Map<String, Object> accueil() {
        List<Map<String, String>> modules = registre.tous().stream()
                .map(m -> Map.of("code", m.code(), "nom", m.nom(), "version", m.version()))
                .toList();
        return Map.of("application", "Kauris (autonome)", "modules", modules);
    }
}
