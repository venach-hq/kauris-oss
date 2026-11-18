package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.List;
import java.util.UUID;

/**
 * Un journal. Ceux des autres modules (VE, AC, TR, PA) et les journaux d'opérations diverses (OD) et d'à-nouveaux
 * (AN) existent toujours ; l'entreprise peut en ajouter (un journal par banque, par exemple) et renommer les siens.
 */
public record Journal(UUID tenantId, String code, String libelle, boolean systeme) {

    public static final String A_NOUVEAUX = "AN";
    public static final String OPERATIONS_DIVERSES = "OD";

    public static final List<Journal> DEFAUT = List.of(
            new Journal(null, "VE", "Ventes", true),
            new Journal(null, "AC", "Achats", true),
            new Journal(null, "TR", "Trésorerie", true),
            new Journal(null, "PA", "Paie", true),
            new Journal(null, OPERATIONS_DIVERSES, "Opérations diverses", true),
            new Journal(null, A_NOUVEAUX, "À-nouveaux", true));

    public Journal {
        code = code == null ? "" : code.strip().toUpperCase();
        if (!code.matches("[A-Z][A-Z0-9]{1,3}")) {
            throw new ErreurMetier("Code du journal : 2 à 4 lettres ou chiffres, commençant par une lettre.");
        }
        libelle = Textes.obligatoire(libelle, "Nom du journal", 60);
    }

    public Journal pour(UUID tenant) {
        return new Journal(tenant, code, libelle, systeme);
    }

    /** Vrai pour le journal des à-nouveaux, alimenté seulement par la clôture. */
    public boolean aNouveaux() {
        return A_NOUVEAUX.equals(code);
    }
}
