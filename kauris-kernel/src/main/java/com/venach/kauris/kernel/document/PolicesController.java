package com.venach.kauris.kernel.document;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

/** Sert au navigateur les fichiers du catalogue de polices (aperçu des documents), et seulement eux. */
@Controller
public class PolicesController {

    private final CataloguePolices polices;

    public PolicesController(CataloguePolices polices) {
        this.polices = polices;
    }

    @GetMapping("/kauris/polices/{nom:.+}")
    public ResponseEntity<Resource> police(@PathVariable String nom) {
        return polices.fichier(nom)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(nom.toLowerCase().endsWith(".otf")
                                ? "font/otf" : "font/ttf"))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .<Resource>body(new FileSystemResource(f.chemin())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
