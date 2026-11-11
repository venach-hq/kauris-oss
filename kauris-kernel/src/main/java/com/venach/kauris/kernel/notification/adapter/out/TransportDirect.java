package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.out.TransportNotifications;
import com.venach.kauris.kernel.notification.domain.Avis;

/** Transport par défaut : une seule instance, pas de courtier de messages (mode autonome). */
public class TransportDirect implements TransportNotifications {

    private final DiffusionLocale diffusion;

    public TransportDirect(DiffusionLocale diffusion) {
        this.diffusion = diffusion;
    }

    @Override
    public void publier(Avis avis) {
        diffusion.diffuser(avis);
    }
}
