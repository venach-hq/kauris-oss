package com.venach.kauris.paie.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Un bulletin de paie : le calcul d'un mois pour un salarié, avec ce qu'il faut pour l'imprimer et le déclarer
 * (nom, poste, numéro social, mode de paiement tels qu'ils étaient ce mois-là). Brouillon tant que le mois est
 * ouvert ({@code numero} vide, {@code id} nul), figé et numéroté quand il est clos.
 */
public record Bulletin(UUID id, UUID tenantId, UUID periodeId, UUID salarieId, UUID contratId, String numero,
                       String matricule, String nomSalarie, String poste, String categorie, String numeroSocial,
                       String situation, int enfants, BigDecimal parts, int joursPayes, long salaireBase,
                       ModePaiement modePaiement, String banque, String compte, CalculPaie.Resultat resultat) {

    public Bulletin {
        numero = numero == null ? "" : numero;
    }

    public static Bulletin brouillon(UUID tenantId, UUID periodeId, Salarie s, ContratTravail c, int joursPayes,
                                     CalculPaie.Resultat r) {
        return new Bulletin(null, tenantId, periodeId, s.id(), c.id(), "", s.matricule(), s.nomComplet(), c.poste(),
                c.categorie(), s.numeroSocial(), s.foyer().situation().libelle(), s.foyer().enfants(),
                s.foyer().parts(), joursPayes, c.salaireBase(), s.modePaiement(), s.banque(), s.compte(), r);
    }

    /** Le même bulletin, figé sous ce numéro. */
    public Bulletin figer(UUID nouvelId, String nouveauNumero) {
        return new Bulletin(nouvelId, tenantId, periodeId, salarieId, contratId, nouveauNumero, matricule, nomSalarie,
                poste, categorie, numeroSocial, situation, enfants, parts, joursPayes, salaireBase, modePaiement,
                banque, compte, resultat);
    }

    public boolean brouillon() {
        return numero.isEmpty();
    }

    public List<CalculPaie.Ligne> lignes() {
        return resultat.lignes();
    }

    public long net() {
        return resultat.netAPayer();
    }
}
