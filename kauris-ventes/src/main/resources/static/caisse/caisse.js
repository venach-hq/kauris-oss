/*
 * Écran de vente de la caisse : choisir les articles, calculer le total comme la facture, encaisser et rendre la
 * monnaie. Le serveur recalcule tout ; ce script ne sert qu'à aller vite et à voir juste.
 */
(function (global) {
    'use strict';

    /** Arrondi au plus proche, demi vers le haut, comme BigDecimal HALF_UP sur des montants positifs. */
    function arrondi(x) {
        return Math.round(Math.round(x * 1e6) / 1e6);
    }

    /**
     * Les totaux d'un ticket, calculés comme Totaux.de en Java : HT de chaque ligne arrondi, regroupé par taux,
     * remise globale par taux, TVA arrondie par taux.
     *
     * @param lignes [{ht: prix HT unitaire, q: quantité, taux: taux de TVA}]
     * @param remise pourcentage de remise globale
     */
    function totaux(lignes, remise) {
        var parTaux = {};
        var lignesHt = 0;
        lignes.forEach(function (l) {
            var ht = arrondi(l.ht * Math.round(l.q * 1000) / 1000);
            lignesHt += ht;
            parTaux[l.taux] = (parTaux[l.taux] || 0) + ht;
        });
        var totalHt = 0;
        var totalTva = 0;
        var tva = [];
        Object.keys(parTaux).sort(function (a, b) { return a - b; }).forEach(function (t) {
            var ht = parTaux[t];
            var base = ht - arrondi(ht * (remise || 0) / 100);
            var montant = arrondi(base * parseFloat(t) / 100);
            tva.push({taux: parseFloat(t), base: base, montant: montant});
            totalHt += base;
            totalTva += montant;
        });
        return {lignesHt: lignesHt, remise: lignesHt - totalHt, totalHt: totalHt, tva: tva, totalTva: totalTva,
            totalTtc: totalHt + totalTva};
    }

    /** Ce qui reste à payer en espèces, et la monnaie à rendre. */
    function paiement(total, recu, mobile, carte) {
        var especes = total - mobile - carte;
        var donne = recu > 0 ? recu : Math.max(especes, 0);
        return {especes: especes, recu: donne, rendu: especes < 0 ? 0 : donne - especes,
            possible: especes >= 0 && donne >= especes};
    }

    var exporte = {totaux: totaux, paiement: paiement};
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exporte;
    }
    global.KaurisCaisse = exporte;

    if (typeof document === 'undefined') {
        return;
    }

    // ------------------------------------------------------------------ dans la page

    function texte(x) {
        return String(Math.round(x)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    }

    function lire(champ) {
        if (!champ) { return 0; }
        var v = (champ.value || '').replace(/[\s  .]/g, '');
        return /^\d+$/.test(v) ? parseInt(v, 10) : 0;
    }

    function lireDecimal(v) {
        var x = parseFloat(String(v || '').replace(',', '.'));
        return isNaN(x) ? 0 : x;
    }

    function demarrer() {
        var ecran = document.getElementById('c-vente');
        if (!ecran) { return; }
        var devise = ecran.dataset.devise || 'F CFA';
        var cherche = document.getElementById('c-cherche');
        var tuiles = Array.prototype.slice.call(document.querySelectorAll('.c-article'));
        var corps = document.getElementById('c-lignes');
        var vide = document.getElementById('c-vide');
        var form = document.getElementById('c-ticket');
        var remise = document.getElementById('c-remise');
        var recu = document.getElementById('c-especes');
        var mobile = document.getElementById('c-mobile');
        var carte = document.getElementById('c-carte');
        var bouton = document.getElementById('c-encaisser');
        var panier = [];   // {id, nom, ht, taux, ttc, q}

        function article(tuile) {
            return {id: tuile.dataset.id, nom: tuile.dataset.nom, ht: parseInt(tuile.dataset.ht, 10),
                taux: parseFloat(tuile.dataset.taux), ttc: parseInt(tuile.dataset.ttc, 10), q: 1};
        }

        function ajouter(tuile, quantite) {
            var a = article(tuile);
            var deja = panier.filter(function (l) { return l.id === a.id; })[0];
            if (deja) {
                deja.q = Math.round((deja.q + (quantite || 1)) * 1000) / 1000;
            } else {
                a.q = quantite || 1;
                panier.push(a);
            }
            dessiner();
        }

        function dessiner() {
            corps.innerHTML = '';
            panier.forEach(function (l, i) {
                var tr = document.createElement('tr');
                tr.innerHTML = '<td class="c-nom"></td>'
                    + '<td class="c-quantite"><button type="button" class="c-moins" aria-label="Un de moins">−</button>'
                    + '<input inputmode="decimal" aria-label="Quantité"><button type="button" class="c-plus" '
                    + 'aria-label="Un de plus">+</button></td><td class="c-montant"></td>'
                    + '<td><button type="button" class="c-retirer" aria-label="Retirer">✕</button></td>';
                tr.querySelector('.c-nom').textContent = l.nom;
                var champ = tr.querySelector('input');
                champ.value = String(l.q).replace('.', ',');
                tr.querySelector('.c-montant').textContent = texte(l.ttc * l.q);
                tr.querySelector('.c-moins').onclick = function () {
                    l.q = Math.round((l.q - 1) * 1000) / 1000;
                    if (l.q <= 0) { panier.splice(i, 1); }
                    dessiner();
                };
                tr.querySelector('.c-plus').onclick = function () {
                    l.q = Math.round((l.q + 1) * 1000) / 1000;
                    dessiner();
                };
                tr.querySelector('.c-retirer').onclick = function () {
                    panier.splice(i, 1);
                    dessiner();
                };
                champ.onchange = function () {
                    var q = Math.round(lireDecimal(champ.value) * 1000) / 1000;
                    if (q > 0) { l.q = q; } else { panier.splice(i, 1); }
                    dessiner();
                };
                corps.appendChild(tr);
            });
            vide.hidden = panier.length > 0;
            recalculer();
        }

        function recalculer() {
            var t = totaux(panier, lireDecimal(remise.value));
            var p = paiement(t.totalTtc, lire(recu), lire(mobile), lire(carte));
            document.getElementById('c-total').textContent = texte(t.totalTtc) + ' ' + devise;
            document.getElementById('c-detail').textContent = t.totalTva > 0
                ? 'dont TVA ' + texte(t.totalTva) + (t.remise > 0 ? ' · remise ' + texte(t.remise) + ' HT' : '')
                : (t.remise > 0 ? 'remise ' + texte(t.remise) : '');
            document.getElementById('c-a-payer').textContent = texte(Math.max(p.especes, 0));
            var rendu = document.getElementById('c-rendu');
            rendu.textContent = p.possible ? texte(p.rendu) + ' ' + devise
                : (p.especes < 0 ? 'trop payé par Mobile Money / carte' : 'il manque ' + texte(p.especes - p.recu));
            rendu.classList.toggle('c-manque', !p.possible);
            bouton.disabled = panier.length === 0 || !p.possible;
            Array.prototype.forEach.call(document.querySelectorAll('[data-billet]'), function (b) {
                b.hidden = panier.length === 0 || p.especes <= 0;
            });
        }

        // Recherche : filtre les tuiles ; Entrée ajoute l'article dont la référence correspond (lecteur de codes).
        cherche.addEventListener('input', function () {
            var q = cherche.value.trim().toLowerCase();
            tuiles.forEach(function (t) {
                t.hidden = q !== '' && (t.dataset.nom + ' ' + t.dataset.ref).toLowerCase().indexOf(q) < 0;
            });
        });
        cherche.addEventListener('keydown', function (e) {
            if (e.key !== 'Enter') { return; }
            e.preventDefault();
            var q = cherche.value.trim().toLowerCase();
            var exacte = tuiles.filter(function (t) { return t.dataset.ref.toLowerCase() === q && q !== ''; })[0];
            var visibles = tuiles.filter(function (t) { return !t.hidden; });
            var choisie = exacte || (visibles.length === 1 ? visibles[0] : null);
            if (choisie) {
                ajouter(choisie);
                cherche.value = '';
                cherche.dispatchEvent(new Event('input'));
            }
        });
        tuiles.forEach(function (t) {
            t.addEventListener('click', function () { ajouter(t); });
        });

        [remise, recu, mobile, carte].forEach(function (c) {
            if (c) { c.addEventListener('input', recalculer); }
        });

        // Billets reçus : « compte juste » ou un montant rond.
        Array.prototype.forEach.call(document.querySelectorAll('[data-billet]'), function (b) {
            b.addEventListener('click', function () {
                var t = totaux(panier, lireDecimal(remise.value));
                var reste = t.totalTtc - lire(mobile) - lire(carte);
                recu.value = b.dataset.billet === 'juste' ? String(reste) : b.dataset.billet;
                recalculer();
            });
        });

        document.getElementById('c-annuler').addEventListener('click', function () {
            if (panier.length && !window.confirm('Vider le ticket ?')) { return; }
            panier = [];
            [recu, mobile, carte].forEach(function (c) { if (c) { c.value = ''; } });
            remise.value = '';
            dessiner();
            cherche.focus();
        });

        // Impression automatique du ticket : choix gardé sur cet appareil.
        var auto = document.getElementById('c-impression');
        try {
            auto.checked = global.localStorage.getItem('kauris.caisse.impression') === 'oui';
        } catch (e) { /* stockage indisponible : on garde la case telle quelle */ }
        auto.addEventListener('change', function () {
            try {
                global.localStorage.setItem('kauris.caisse.impression', auto.checked ? 'oui' : 'non');
            } catch (e) { /* rien */ }
        });

        form.addEventListener('submit', function (e) {
            if (!panier.length) {
                e.preventDefault();
                return;
            }
            Array.prototype.forEach.call(form.querySelectorAll('input[data-ligne]'), function (x) { x.remove(); });
            panier.forEach(function (l) {
                [['article', l.id], ['quantite', String(l.q)]].forEach(function (p) {
                    var x = document.createElement('input');
                    x.type = 'hidden';
                    x.name = p[0];
                    x.value = p[1];
                    x.dataset.ligne = '1';
                    form.appendChild(x);
                });
            });
            bouton.disabled = true;   // pas de double encaissement
        });

        // La calculatrice s'ouvre à côté du ticket.
        var bascule = document.getElementById('c-bascule-calcul');
        var panneau = document.getElementById('c-panneau-calcul');
        if (bascule && panneau) {
            bascule.addEventListener('click', function () {
                panneau.hidden = !panneau.hidden;
                bascule.setAttribute('aria-expanded', String(!panneau.hidden));
            });
        }

        dessiner();
        cherche.focus();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', demarrer);
    } else {
        demarrer();
    }
})(typeof window !== 'undefined' ? window : globalThis);
