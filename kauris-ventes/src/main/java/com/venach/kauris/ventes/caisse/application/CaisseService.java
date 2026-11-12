package com.venach.kauris.ventes.caisse.application;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.application.VentesService.PaiementComptoir;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.ModeReglement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.venach.kauris.ventes.application.PermissionsVentes.CAISSE;
import static com.venach.kauris.ventes.application.PermissionsVentes.ENCAISSER;
import static com.venach.kauris.ventes.application.PermissionsVentes.exiger;

/**
 * La caisse : vente rapide au comptoir. Chaque personne ouvre sa caisse avec un fond, encaisse des tickets
 * (facture du jour émise et réglée : stock et trésorerie suivent), puis la clôture en comptant ses espèces.
 * Une caisse se clôture le jour où elle a été ouverte : on ne vend pas sur la caisse de la veille.
 */
public class CaisseService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    /** Ce que le client donne : espèces reçues (0 = le compte juste), Mobile Money et carte. */
    public record Encaissement(long especesRecues, long mobile, String referenceMobile, long carte) {
    }

    /** Les chiffres d'une caisse. {@code attendu} : espèces qui devraient être dans le tiroir. */
    public record Bilan(int tickets, long total, long especes, long mobile, long carte, long rendu, long attendu) {
    }

    private final VentesService ventes;
    private final PortsCaisse.Sessions sessions;
    private final PortsCaisse.Tickets tickets;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public CaisseService(VentesService ventes, PortsCaisse.Sessions sessions, PortsCaisse.Tickets tickets,
                         UniteDeTravail transaction, Clock horloge) {
        this.ventes = ventes;
        this.sessions = sessions;
        this.tickets = tickets;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(horloge);
    }

    /** La caisse ouverte de la personne, s'il y en a une. */
    public Optional<SessionCaisse> ouverte(Acces a) {
        exiger(a, CAISSE);
        return sessions.ouverteDe(a.tenantId(), a.membreId());
    }

    /** La dernière caisse de la personne : ses comptes servent de choix par défaut à l'ouverture suivante. */
    public Optional<SessionCaisse> derniere(Acces a) {
        exiger(a, CAISSE);
        return sessions.sessions(a.tenantId(), a.membreId(), 1).stream().findFirst();
    }

    public List<Ports.Encaissements.Compte> comptes(Acces a) {
        exiger(a, CAISSE);
        return ventes.comptesDEncaissement(a);
    }

    public SessionCaisse ouvrir(Acces a, long fond, UUID compteEspeces, UUID compteMobile, UUID compteCarte) {
        exiger(a, CAISSE);
        List<Ports.Encaissements.Compte> comptes = ventes.comptesDEncaissement(a);
        if (!comptes.isEmpty() && compteEspeces == null) {
            throw new ErreurMetier("Choisissez le compte où sont inscrites les espèces de la caisse.");
        }
        for (UUID id : new UUID[]{compteEspeces, compteMobile, compteCarte}) {
            if (id != null && comptes.stream().noneMatch(c -> c.id().equals(id))) {
                throw new ErreurMetier("Ce compte ne reçoit pas de règlement.");
            }
        }
        LocalDate jour = aujourdHui();
        return transaction.executer(() -> {
            sessions.ouverteDe(a.tenantId(), a.membreId()).ifPresent(s -> {
                throw new ErreurMetier("Votre caisse " + s.numero() + " est déjà ouverte.");
            });
            long rang = sessions.prochainNumero(a.tenantId(), jour.getYear());
            SessionCaisse s = SessionCaisse.ouvrir(a.tenantId(), String.format("CA-%d-%04d", jour.getYear(), rang),
                    a.membreId(), a.nomMembre(), jour, Instant.now(horloge), fond, compteEspeces, compteMobile,
                    compteCarte);
            sessions.sauvegarder(s);
            return s;
        });
    }

    /**
     * Encaisse un ticket sur la caisse ouverte de la personne. Les prix viennent du catalogue ; ce qui n'est payé
     * ni par Mobile Money ni par carte l'est en espèces, et la monnaie à rendre est calculée.
     *
     * @param clientId null : le client comptoir
     */
    public TicketCaisse vendre(Acces a, UUID clientId, Map<UUID, BigDecimal> quantites, BigDecimal remise,
                               Encaissement e) {
        exiger(a, CAISSE);
        SessionCaisse s = sessions.ouverteDe(a.tenantId(), a.membreId())
                .orElseThrow(() -> new ErreurMetier("Ouvrez votre caisse avant de vendre."));
        if (!s.jour().equals(aujourdHui())) {
            throw new ErreurMetier("Votre caisse est ouverte depuis le " + JOUR.format(s.jour())
                    + " : clôturez-la, puis ouvrez celle du jour.");
        }
        if (e.mobile() < 0 || e.carte() < 0 || e.especesRecues() < 0) {
            throw new ErreurMetier("Les montants payés sont positifs.");
        }
        UUID client = clientId != null ? clientId : ventes.clientComptoir(a).id();
        long total = ventes.apercuComptoir(a, quantites, remise).totalTtc();
        long autres = e.mobile() + e.carte();
        if (autres > total) {
            throw new ErreurMetier("Mobile Money et carte (" + autres + ") dépassent le total du ticket (" + total
                    + ").");
        }
        long especes = total - autres;
        long recu = especes == 0 ? 0 : (e.especesRecues() == 0 ? especes : e.especesRecues());
        if (recu < especes) {
            throw new ErreurMetier("Il manque " + (especes - recu) + " en espèces (à payer en espèces : " + especes
                    + ").");
        }
        if (recu - especes > 10_000_000L) {
            throw new ErreurMetier("Espèces reçues : vérifiez le montant saisi.");
        }
        if (e.mobile() > 0 && (e.referenceMobile() == null || e.referenceMobile().isBlank())) {
            throw new ErreurMetier("Indiquez la référence de la transaction Mobile Money.");
        }
        List<PaiementComptoir> paiements = new ArrayList<>();
        if (especes > 0) {
            paiements.add(new PaiementComptoir(ModeReglement.ESPECES, especes, s.numero(), s.compteEspeces()));
        }
        if (e.mobile() > 0) {
            paiements.add(new PaiementComptoir(ModeReglement.MOBILE_MONEY, e.mobile(), e.referenceMobile().strip(),
                    s.compteMobile()));
        }
        if (e.carte() > 0) {
            paiements.add(new PaiementComptoir(ModeReglement.CARTE, e.carte(), s.numero(), s.compteCarte()));
        }
        long recuFinal = recu;
        return transaction.executer(() -> {
            SessionCaisse verrouillee = sessions.verrouillerSession(a.tenantId(), s.id())
                    .filter(SessionCaisse::ouverte)
                    .orElseThrow(() -> new ErreurMetier("Votre caisse vient d'être clôturée : rouvrez-la."));
            DocumentVente facture = ventes.vendreAuComptoir(a, client, quantites, remise, paiements);
            TicketCaisse t = new TicketCaisse(UUID.randomUUID(), a.tenantId(), verrouillee.id(), facture.id(),
                    facture.numero(), Instant.now(horloge), facture.totaux().totalTtc(), especes, e.mobile(),
                    e.carte(), recuFinal, recuFinal - especes, e.mobile() > 0 ? e.referenceMobile() : "");
            tickets.sauvegarder(t);
            return t;
        });
    }

    /** Clôture : par la personne de la caisse, ou par quelqu'un qui peut encaisser (caissier absent). */
    public SessionCaisse cloturer(Acces a, UUID sessionId, long especesComptees, String note) {
        exiger(a, CAISSE);
        return transaction.executer(() -> {
            SessionCaisse s = sessions.verrouillerSession(a.tenantId(), sessionId)
                    .orElseThrow(() -> new ElementIntrouvable("Caisse introuvable."));
            verifierAcces(a, s);
            SessionCaisse close = s.cloturer(especesComptees, bilan(s).attendu(), a.nomMembre(), note,
                    Instant.now(horloge));
            sessions.sauvegarder(close);
            return close;
        });
    }

    public SessionCaisse session(Acces a, UUID id) {
        exiger(a, CAISSE);
        SessionCaisse s = sessions.session(a.tenantId(), id)
                .orElseThrow(() -> new ElementIntrouvable("Caisse introuvable."));
        verifierAcces(a, s);
        return s;
    }

    /** Ses caisses ; toutes celles de l'organisation pour qui peut encaisser. */
    public List<SessionCaisse> sessions(Acces a, int limite) {
        exiger(a, CAISSE);
        return sessions.sessions(a.tenantId(), a.peut(ENCAISSER) ? null : a.membreId(), limite);
    }

    public List<TicketCaisse> tickets(Acces a, SessionCaisse s) {
        verifierAcces(a, s);
        return tickets.ticketsDe(a.tenantId(), s.id()).stream()
                .sorted(java.util.Comparator.comparing(TicketCaisse::creeLe)).toList();
    }

    public Bilan bilan(Acces a, SessionCaisse s) {
        verifierAcces(a, s);
        return bilan(s);
    }

    private Bilan bilan(SessionCaisse s) {
        List<TicketCaisse> liste = tickets.ticketsDe(s.tenantId(), s.id());
        long total = 0;
        long especes = 0;
        long mobile = 0;
        long carte = 0;
        long rendu = 0;
        for (TicketCaisse t : liste) {
            total += t.total();
            especes += t.especes();
            mobile += t.mobile();
            carte += t.carte();
            rendu += t.rendu();
        }
        return new Bilan(liste.size(), total, especes, mobile, carte, rendu, s.fond() + especes);
    }

    /** Le ticket d'une facture vendue en caisse. */
    public TicketCaisse ticket(Acces a, UUID documentId) {
        exiger(a, CAISSE);
        TicketCaisse t = tickets.ticketDuDocument(a.tenantId(), documentId)
                .orElseThrow(() -> new ElementIntrouvable("Ticket introuvable."));
        verifierAcces(a, sessions.session(a.tenantId(), t.sessionId()).orElseThrow());
        return t;
    }

    private static void verifierAcces(Acces a, SessionCaisse s) {
        exiger(a, CAISSE);
        if (!s.membreId().equals(a.membreId()) && !a.peut(ENCAISSER)) {
            throw new AccesRefuse("Cette caisse est celle de " + s.caissier() + ".");
        }
    }
}
