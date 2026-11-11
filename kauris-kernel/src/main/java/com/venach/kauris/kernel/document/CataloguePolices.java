package com.venach.kauris.kernel.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Les polices installées sur le serveur, lues une fois au démarrage dans un dossier de fichiers TrueType
 * nommés « Famille-Style.ttf » (Poppins-SemiBoldItalic.ttf, Lato-Regular.ttf, Inter-Regular-italic.ttf…).
 * Les polices de base du PDF (Helvetica, Times, Courier) sont toujours là, sans fichier.
 *
 * <p>Sans dossier, ou s'il est vide, les documents utilisent les polices de base : rien n'échoue.
 */
public final class CataloguePolices {

    private static final Logger log = LoggerFactory.getLogger(CataloguePolices.class);

    /** Les trois polices que tout lecteur PDF connaît. */
    public static final List<String> DE_BASE = List.of("Helvetica", "Times", "Courier");

    /** Un fichier de police : sa graisse (100 à 900) et s'il est italique. */
    public record Fichier(String nom, Path chemin, int graisse, boolean italique) {
    }

    /** Une famille : « PlayfairDisplay » s'affiche « Playfair Display ». */
    public record Police(String famille, String libelle, List<Fichier> fichiers) {

        public boolean deBase() {
            return fichiers.isEmpty();
        }
    }

    private static final Map<String, Integer> GRAISSES = new LinkedHashMap<>();

    static {
        // Du plus long au plus court : « ExtraBold » avant « Bold ».
        GRAISSES.put("extralight", 200);
        GRAISSES.put("ultralight", 200);
        GRAISSES.put("extrabold", 800);
        GRAISSES.put("ultrabold", 800);
        GRAISSES.put("semibold", 600);
        GRAISSES.put("demibold", 600);
        GRAISSES.put("hairline", 100);
        GRAISSES.put("regular", 400);
        GRAISSES.put("medium", 500);
        GRAISSES.put("normal", 400);
        GRAISSES.put("black", 900);
        GRAISSES.put("heavy", 900);
        GRAISSES.put("light", 300);
        GRAISSES.put("thin", 100);
        GRAISSES.put("bold", 700);
        GRAISSES.put("book", 400);
    }

    private final Map<String, Police> parFamille;
    private final Map<String, Fichier> parNomDeFichier;

    private CataloguePolices(Map<String, Police> parFamille) {
        this.parFamille = parFamille;
        Map<String, Fichier> fichiers = new LinkedHashMap<>();
        parFamille.values().forEach(p -> p.fichiers().forEach(f -> fichiers.put(f.nom(), f)));
        this.parNomDeFichier = fichiers;
    }

    /** Aucune police installée : seulement celles de base. */
    public static CataloguePolices vide() {
        return depuis(null);
    }

    /** Lit le dossier ; un dossier absent ou illisible donne le catalogue de base, avec un avertissement. */
    public static CataloguePolices depuis(Path dossier) {
        Map<String, List<Fichier>> familles = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (dossier != null) {
            if (!Files.isDirectory(dossier)) {
                log.warn("Dossier de polices introuvable : {}. Les documents utiliseront Helvetica, Times et "
                        + "Courier.", dossier);
            } else {
                try (Stream<Path> fichiers = Files.list(dossier)) {
                    fichiers.filter(Files::isRegularFile).sorted().forEach(f -> lire(f).ifPresent(lu ->
                            familles.computeIfAbsent(lu.famille, k -> new ArrayList<>()).add(lu.fichier)));
                } catch (IOException illisible) {
                    log.warn("Dossier de polices illisible : {} ({})", dossier, illisible.getMessage());
                }
            }
        }
        Map<String, Police> catalogue = new LinkedHashMap<>();
        familles.forEach((famille, fichiers) -> {
            fichiers.sort(Comparator.comparing(Fichier::italique).thenComparing(Fichier::graisse));
            catalogue.put(famille, new Police(famille, libelle(famille), List.copyOf(fichiers)));
        });
        for (String base : DE_BASE) {
            catalogue.putIfAbsent(base, new Police(base, base, List.of()));
        }
        if (dossier != null) {
            log.info("Polices des documents : {}", String.join(", ", catalogue.keySet()));
        }
        return new CataloguePolices(catalogue);
    }

    private record Lu(String famille, Fichier fichier) {
    }

    /** « Lato-BoldItalic.ttf » → famille Lato, graisse 700, italique. Rien pour un fichier qui n'en est pas un. */
    static Optional<Lu> lire(Path chemin) {
        String nom = chemin.getFileName().toString();
        String bas = nom.toLowerCase(Locale.ROOT);
        if (!(bas.endsWith(".ttf") || bas.endsWith(".otf")) || !estUnePolice(chemin)) {
            return Optional.empty();
        }
        String base = nom.substring(0, nom.length() - 4);
        int tiret = base.indexOf('-');
        String famille = (tiret > 0 ? base.substring(0, tiret) : base).strip();
        String style = (tiret > 0 ? base.substring(tiret + 1) : "Regular").toLowerCase(Locale.ROOT)
                .replace("-", "").replace("_", "").replace(" ", "");
        if (!famille.matches("[A-Za-z][A-Za-z0-9]{0,39}")) {
            return Optional.empty();
        }
        boolean italique = style.contains("italic") || style.contains("oblique");
        String reste = style.replace("italic", "").replace("oblique", "");
        int graisse = 400;
        for (Map.Entry<String, Integer> g : GRAISSES.entrySet()) {
            if (reste.startsWith(g.getKey())) {
                graisse = g.getValue();
                break;
            }
        }
        return Optional.of(new Lu(famille, new Fichier(nom, chemin, graisse, italique)));
    }

    /** Les quatre premiers octets d'une police TrueType ou OpenType. */
    private static boolean estUnePolice(Path chemin) {
        try (InputStream entree = Files.newInputStream(chemin)) {
            byte[] tete = entree.readNBytes(4);
            if (tete.length < 4) {
                return false;
            }
            String texte = new String(tete, java.nio.charset.StandardCharsets.ISO_8859_1);
            return (tete[0] == 0 && tete[1] == 1 && tete[2] == 0 && tete[3] == 0) || texte.equals("true")
                    || texte.equals("OTTO");
        } catch (IOException illisible) {
            return false;
        }
    }

    /** « PlayfairDisplay » → « Playfair Display », « OpenSans » → « Open Sans ». */
    static String libelle(String famille) {
        return famille.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    public List<Police> toutes() {
        return List.copyOf(parFamille.values());
    }

    public List<String> familles() {
        return List.copyOf(parFamille.keySet());
    }

    public boolean connait(String famille) {
        return parFamille.containsKey(famille);
    }

    public Optional<Police> police(String famille) {
        return Optional.ofNullable(parFamille.get(famille));
    }

    /** Un fichier du catalogue par son nom exact : rien d'autre du disque n'est servi. */
    public Optional<Fichier> fichier(String nom) {
        return Optional.ofNullable(parNomDeFichier.get(nom));
    }
}
