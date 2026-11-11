/*
 * Interface commune de Kauris : notifications en direct. Aucune dépendance.
 * Le serveur pousse un avis par WebSocket ; on l'affiche quelques secondes et on relit le compteur.
 * Si le canal tombe, on réessaie de plus en plus tard : sans lui, tout reste visible à la page suivante.
 */
(function () {
    'use strict';

    var zone = document.getElementById('k-avis');
    if (!zone || !('WebSocket' in window)) {
        return;
    }
    var compteur = document.getElementById('k-non-lues');
    var attente = 1000;
    var ATTENTE_MAX = 60000;
    var DUREE_AFFICHAGE = 8000;

    function adresseDuCanal() {
        var adresse = new URL(zone.dataset.canal, window.location.href);
        adresse.protocol = adresse.protocol === 'https:' ? 'wss:' : 'ws:';
        return adresse.toString();
    }

    function relireLeCompteur() {
        fetch(zone.dataset.compteur, {credentials: 'same-origin', headers: {Accept: 'application/json'}})
            .then(function (reponse) { return reponse.ok ? reponse.json() : null; })
            .then(function (donnees) {
                if (!donnees || !compteur || typeof donnees.nonLues !== 'number') {
                    return;
                }
                compteur.textContent = String(donnees.nonLues);
                compteur.classList.toggle('k-vide', donnees.nonLues === 0);
            })
            .catch(function () { /* le compteur sera juste à la page suivante */ });
    }

    /* Le texte vient du serveur mais reste traité comme du texte : jamais comme du HTML. */
    function afficher(avis) {
        var lien = document.createElement('a');
        lien.href = zone.dataset.liste;
        var titre = document.createElement('strong');
        titre.textContent = String(avis.titre || 'Notification');
        var message = document.createElement('span');
        message.textContent = String(avis.message || '');
        lien.appendChild(titre);
        lien.appendChild(message);
        zone.appendChild(lien);
        window.setTimeout(function () { lien.remove(); }, DUREE_AFFICHAGE);
    }

    function ouvrir() {
        var canal;
        try {
            canal = new WebSocket(adresseDuCanal());
        } catch (erreur) {
            return;
        }
        canal.onopen = function () { attente = 1000; };
        canal.onmessage = function (evenement) {
            var avis;
            try {
                avis = JSON.parse(evenement.data);
            } catch (erreur) {
                return;
            }
            afficher(avis);
            relireLeCompteur();
        };
        canal.onclose = function () {
            window.setTimeout(ouvrir, attente);
            attente = Math.min(attente * 2, ATTENTE_MAX);
        };
    }

    ouvrir();
})();
