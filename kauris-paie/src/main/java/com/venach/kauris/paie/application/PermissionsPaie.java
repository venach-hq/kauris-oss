package com.venach.kauris.paie.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;

import java.util.List;

/**
 * Les permissions de la paie. Les salaires sont confidentiels : toutes sont au niveau « administrer », donc
 * réservées au propriétaire et à l'administrateur tant qu'ils ne les cochent pas dans un rôle (comptable, RH).
 * Leurs codes ne changent plus : les rôles personnalisés les enregistrent.
 */
public final class PermissionsPaie {

    public static final String CONSULTER = "paie.consulter";
    public static final String PREPARER = "paie.preparer";
    public static final String CLOTURER = "paie.cloturer";
    public static final String PARAMETRER = "paie.parametres.modifier";

    private static final String GROUPE = "Paie";

    public static final List<Permission> TOUTES = List.of(
            Permission.administrer(CONSULTER, "Voir les salariés, les contrats, les bulletins et les déclarations",
                    GROUPE),
            Permission.administrer(PREPARER, "Saisir les salariés, les contrats et les éléments du mois (absences, "
                    + "primes, avances)", GROUPE),
            Permission.administrer(CLOTURER, "Clôturer ou rouvrir un mois de paie, préparer le virement et marquer "
                    + "les salaires comme payés", GROUPE),
            Permission.administrer(PARAMETRER, "Corriger les taux et plafonds de cotisation et d'impôt", GROUPE));

    private PermissionsPaie() {
    }

    public static void exiger(Acces acces, String permission) {
        if (!acces.peut(permission)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas cette opération dans la paie.");
        }
    }

    /** Ce que la personne peut faire, pour afficher ou cacher les boutons. */
    public record Droits(boolean preparer, boolean cloturer, boolean parametrer) {
        public static Droits de(Acces a) {
            return new Droits(a.peut(PREPARER), a.peut(CLOTURER), a.peut(PARAMETRER));
        }
    }
}
