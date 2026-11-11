package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.domain.Avis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Point d'arrivée des avis dans cette instance de l'application : ceux qui écoutent (les écrans ouverts,
 * par WebSocket) sont prévenus. Un auditeur en panne n'empêche pas les autres.
 */
public class DiffusionLocale {

    private final List<Consumer<Avis>> auditeurs = new CopyOnWriteArrayList<>();

    public void ecouter(Consumer<Avis> auditeur) {
        auditeurs.add(auditeur);
    }

    public void diffuser(Avis avis) {
        for (Consumer<Avis> auditeur : auditeurs) {
            try {
                auditeur.accept(avis);
            } catch (RuntimeException ignoree) {
                // L'avis n'est qu'un signal : la notification est en base et s'affichera à la page suivante.
            }
        }
    }
}
