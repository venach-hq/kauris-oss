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

/*
 * Images envoyées par un formulaire marqué data-reduire-image="1920" : une photo trop grande est réduite dans le
 * navigateur (1920 px de large au plus, JPEG) avant l'envoi. Les photos de téléphone passent ainsi sous la limite
 * du serveur et l'accueil s'affiche vite. Si le navigateur ne sait pas faire, le fichier part tel quel.
 */
(function () {
    'use strict';

    var formulaires = document.querySelectorAll('form[data-reduire-image]');
    Array.prototype.forEach.call(formulaires, function (formulaire) {
        formulaire.addEventListener('submit', function (evenement) {
            var champ = formulaire.querySelector('input[type=file]');
            var fichier = champ && champ.files && champ.files[0];
            var largeurMax = parseInt(formulaire.dataset.reduireImage, 10) || 1920;
            if (formulaire.dataset.reduit === 'oui' || !fichier || !/^image\/(png|jpeg|webp)$/.test(fichier.type)
                || fichier.size < 600 * 1024 || !window.createImageBitmap || !window.DataTransfer) {
                return;
            }
            evenement.preventDefault();
            createImageBitmap(fichier).then(function (image) {
                var echelle = Math.min(1, largeurMax / image.width);
                var toile = document.createElement('canvas');
                toile.width = Math.round(image.width * echelle);
                toile.height = Math.round(image.height * echelle);
                toile.getContext('2d').drawImage(image, 0, 0, toile.width, toile.height);
                toile.toBlob(function (reduit) {
                    if (reduit && reduit.size < fichier.size) {
                        var transfert = new DataTransfer();
                        transfert.items.add(new File([reduit], 'fond.jpg', {type: 'image/jpeg'}));
                        champ.files = transfert.files;
                    }
                    formulaire.dataset.reduit = 'oui';
                    formulaire.submit();
                }, 'image/jpeg', 0.85);
            }).catch(function () {
                formulaire.dataset.reduit = 'oui';
                formulaire.submit();
            });
        });
    });
})();
