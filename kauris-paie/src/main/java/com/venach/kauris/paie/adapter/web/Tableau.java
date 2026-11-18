package com.venach.kauris.paie.adapter.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Un tableau à exporter : en CSV (Excel français : point-virgule, UTF-8 avec BOM), en Excel (.xlsx, écrit à la
 * main : une feuille, en-tête en gras, nombres en nombres) ou en PDF (vue « paie/doc-tableau »).
 *
 * @param lignes chaque cellule est un {@code String} ou un {@code Long} (montant, quantité)
 */
public record Tableau(String titre, String precision, List<String> colonnes, List<List<Object>> lignes,
                      List<Object> total) {

    public byte[] csv() {
        StringBuilder s = new StringBuilder("﻿");
        ligne(s, colonnes.stream().map(x -> (Object) x).toList());
        lignes.forEach(l -> ligne(s, l));
        if (total != null) {
            ligne(s, total);
        }
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void ligne(StringBuilder s, List<Object> cellules) {
        for (int i = 0; i < cellules.size(); i++) {
            if (i > 0) {
                s.append(';');
            }
            Object c = cellules.get(i);
            String v = c == null ? "" : c.toString();
            if (c instanceof Number) {
                s.append(v);
            } else {
                s.append('"').append(v.replace("\"", "\"\"")).append('"');
            }
        }
        s.append("\r\n");
    }

    public byte[] xlsx() {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
            fichier(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            fichier(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            fichier(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            String nom = xml(titre.length() > 31 ? titre.substring(0, 31) : titre).replaceAll("[\\[\\]:*?/\\\\]", " ");
            fichier(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""".formatted(nom));
            // Style 1 : gras (en-tête, total) ; style 2 : nombre « 25 000 ».
            fichier(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="4">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                    <xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                    <xf numFmtId="3" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>
                    </cellXfs>
                    <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                    </styleSheet>""");
            StringBuilder f = new StringBuilder("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""");
            int r = 1;
            rangee(f, r++, colonnes.stream().map(x -> (Object) x).toList(), true);
            for (List<Object> l : lignes) {
                rangee(f, r++, l, false);
            }
            if (total != null) {
                rangee(f, r, total, true);
            }
            f.append("</sheetData></worksheet>");
            fichier(zip, "xl/worksheets/sheet1.xml", f.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sortie.toByteArray();
    }

    private static void rangee(StringBuilder f, int r, List<Object> cellules, boolean gras) {
        f.append("<row r=\"").append(r).append("\">");
        for (int i = 0; i < cellules.size(); i++) {
            Object c = cellules.get(i);
            String ref = colonne(i) + r;
            if (c instanceof Number n) {
                f.append("<c r=\"").append(ref).append("\" s=\"").append(gras ? 3 : 2).append("\"><v>")
                        .append(n.longValue()).append("</v></c>");
            } else if (c != null && !c.toString().isEmpty()) {
                f.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(gras ? " s=\"1\"" : "")
                        .append("><is><t xml:space=\"preserve\">").append(xml(c.toString())).append("</t></is></c>");
            }
        }
        f.append("</row>");
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String colonne(int i) {
        StringBuilder s = new StringBuilder();
        for (int n = i + 1; n > 0; n = (n - 1) / 26) {
            s.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return s.toString();
    }

    private static String xml(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n') {
                        b.append(ch);
                    }
                }
            }
        }
        return b.toString();
    }

    private static void fichier(ZipOutputStream zip, String nom, String contenu) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
