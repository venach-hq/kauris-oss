package com.venach.kauris.kernel.ui;

/**
 * Les liens montrés sur chaque écran : le guide d'utilisation et le code source. Kauris est sous licence AGPL-3.0 :
 * qui modifie Kauris et le met à disposition de ses utilisateurs par le réseau doit leur offrir son code source ;
 * {@code codeSource} est alors l'adresse de sa version.
 *
 * @param guide      dossier du guide d'utilisation ; une page s'y trouve sous « nom.md »
 * @param codeSource dépôt du code source de la version en service
 */
public record LiensKauris(String guide, String codeSource) {

    public static final String GUIDE = "https://github.com/venach-hq/kauris-oss/blob/main/docs/guide";
    public static final String CODE_SOURCE = "https://github.com/venach-hq/kauris-oss";

    public LiensKauris {
        guide = propre(guide, GUIDE);
        codeSource = propre(codeSource, CODE_SOURCE);
    }

    private static String propre(String valeur, String parDefaut) {
        String v = valeur == null || valeur.isBlank() ? parDefaut : valeur.strip();
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            throw new IllegalArgumentException("Lien d'aide ou de code source invalide : " + v);
        }
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    /** Une page du guide : {@code page("ventes")} donne « …/guide/ventes.md ». */
    public String page(String nom) {
        return guide + "/" + nom + ".md";
    }
}
