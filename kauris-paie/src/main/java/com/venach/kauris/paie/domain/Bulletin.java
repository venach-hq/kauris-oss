package com.venach.kauris.paie.domain;

import java.util.List;
import java.util.UUID;

/**
 * Un bulletin de paie : le calcul d'un mois pour un salarié, avec ce qu'il faut pour l'imprimer et le déclarer
 * (nom, poste, numéro social, mode de paiement tels qu'ils étaient ce mois-là). Brouillon tant que le mois est
 * ouvert ({@code numero} vide, {@code id} nul), figé et numéroté quand il est clos.
 */
public record Bulletin(UUID id, UUID tenantId, UUID periodeId, UUID salarieId, UUID contratId, String numero,
                       String matricule, String nomSalarie, String poste, String categorie, String numeroSocial,
                       String situation, int enfants, String famille, int joursPayes, long salaireBase,
                       ModePaiement modePaiement, String banque, String compte, RegimeFiscal regime, String regles,
                       CalculPaie.Resultat resultat) {

    public Bulletin {
        numero = numero == null ? "" : numero;
        famille = famille == null ? "" : famille;
        regime = regime == null ? RegimeFiscal.NORMAL : regime;
        regles = regles == null ? "" : regles;
    }

    public static Bulletin brouillon(UUID tenantId, UUID periodeId, Salarie s, ContratTravail c, int joursPayes,
                                     ReglesPaie regles, CalculPaie.Resultat r) {
        ReglesPaie.Impot i = regles.contenu().impot();
        String famille = switch (i.famille()) {
            case REDUCTION_PAR_DEMI_PART -> i.parts().de(s.foyer()).toPlainString().replace('.', ',') + " part(s)";
            case DEDUCTION_PAR_PERSONNE -> Math.min(s.foyer().personnesACharge(), i.personnesMax())
                    + " personne(s) à charge";
            case AUCUNE -> "";
        };
        return new Bulletin(null, tenantId, periodeId, s.id(), c.id(), "", s.matricule(), s.nomComplet(), c.poste(),
                c.categorie(), s.numeroSocial(), s.foyer().situation().libelle(), s.foyer().enfants(), famille,
                joursPayes, c.salaireBase(), s.modePaiement(), s.banque(), s.compte(), s.regime(), regles.nom(), r);
    }


    /** Le même bulletin, figé sous ce numéro. */
    public Bulletin figer(UUID nouvelId, String nouveauNumero) {
        return new Bulletin(nouvelId, tenantId, periodeId, salarieId, contratId, nouveauNumero, matricule, nomSalarie,
                poste, categorie, numeroSocial, situation, enfants, famille, joursPayes, salaireBase, modePaiement,
                banque, compte, regime, regles, resultat);
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
