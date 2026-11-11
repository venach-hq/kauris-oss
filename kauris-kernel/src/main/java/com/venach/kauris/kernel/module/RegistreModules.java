package com.venach.kauris.kernel.module;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Modules présents dans l'application, par code. */
public final class RegistreModules {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,39}");

    private final Map<String, ModuleKauris> modules;

    /**
     * @throws IllegalStateException si un code est invalide ou déclaré deux fois (erreur de montage : on refuse
     *                               de démarrer plutôt que de mélanger deux modules)
     */
    public RegistreModules(List<ModuleKauris> declares) {
        Map<String, ModuleKauris> parCode = new LinkedHashMap<>();
        declares.stream().sorted(Comparator.comparing(ModuleKauris::code)).forEach(m -> {
            if (m.code() == null || !CODE.matcher(m.code()).matches()) {
                throw new IllegalStateException("Code de module invalide : « " + m.code()
                        + " » (minuscules, chiffres et tirets, 2 à 40 caractères)");
            }
            ModuleKauris existant = parCode.putIfAbsent(m.code(), m);
            if (existant != null) {
                throw new IllegalStateException("Module « " + m.code() + " » déclaré deux fois : "
                        + existant.getClass().getName() + " et " + m.getClass().getName());
            }
        });
        this.modules = Map.copyOf(parCode);
    }

    public List<ModuleKauris> tous() {
        return modules.values().stream().sorted(Comparator.comparing(ModuleKauris::code)).toList();
    }

    public Optional<ModuleKauris> parCode(String code) {
        return Optional.ofNullable(modules.get(code));
    }

    public boolean present(String code) {
        return modules.containsKey(code);
    }

    /** Le menu complet : le noyau d'abord, puis les modules par code. */
    public List<EntreeMenu> menu() {
        return tous().stream()
                .sorted(Comparator.comparing((ModuleKauris m) -> !"kernel".equals(m.code()))
                        .thenComparing(ModuleKauris::code))
                .flatMap(m -> m.menu().stream())
                .toList();
    }

    /** Toutes les permissions déclarées, le noyau d'abord ; un code déclaré deux fois est une erreur. */
    public List<Permission> permissions() {
        List<Permission> toutes = tous().stream()
                .sorted(Comparator.comparing((ModuleKauris m) -> !"kernel".equals(m.code()))
                        .thenComparing(ModuleKauris::code))
                .flatMap(m -> m.permissions().stream())
                .toList();
        long distinctes = toutes.stream().map(Permission::code).distinct().count();
        if (distinctes != toutes.size()) {
            throw new IllegalStateException("Une permission est déclarée par deux modules");
        }
        return toutes;
    }
}
