// Saisie d'un devis, d'une facture ou d'un avoir : ajout et retrait de lignes, choix d'un article du catalogue,
// totaux estimés. Le serveur refait tous les calculs : ce script n'est qu'une aide à la saisie.
(function () {
    'use strict';
    var formulaire = document.getElementById('v-edition');
    if (!formulaire) {
        return;
    }
    var corps = document.getElementById('v-corps');
    var symbole = formulaire.dataset.devise || '';

    function nombre(texte) {
        var propre = String(texte || '').replace(/[\s  %]/g, '');
        if (/^\d{1,3}(\.\d{3})+$/.test(propre)) {
            propre = propre.replace(/\./g, '');     // 1.000.000 : points de milliers
        }
        var n = parseFloat(propre.replace(',', '.'));
        return isNaN(n) ? 0 : n;
    }

    function formater(n) {
        return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' ' + symbole;
    }

    function champ(ligne, nom) {
        return ligne.querySelector('[name="ligne.' + nom + '"]');
    }

    function calculer() {
        var ht = 0;
        var tva = 0;
        corps.querySelectorAll('tr.v-ligne').forEach(function (ligne) {
            var designation = champ(ligne, 'designation').value.trim();
            var prixSaisi = champ(ligne, 'prix').value.trim();
            var cellule = ligne.querySelector('.v-total-ligne');
            if (!designation && !prixSaisi) {
                cellule.textContent = '—';
                return;
            }
            var quantite = champ(ligne, 'quantite').value.trim() === '' ? 1 : nombre(champ(ligne, 'quantite').value);
            var montant = Math.round(quantite * nombre(prixSaisi) * (100 - nombre(champ(ligne, 'remise').value)) / 100);
            var taux = champ(ligne, 'tva') ? nombre(champ(ligne, 'tva').value) : 0;
            cellule.textContent = formater(montant);
            ht += montant;
            tva += montant * taux / 100;
        });
        var remise = nombre(formulaire.querySelector('[name="remiseGlobale"]').value);
        ht = ht * (100 - remise) / 100;
        tva = tva * (100 - remise) / 100;
        document.getElementById('v-ht').textContent = formater(ht);
        var celluleTva = document.getElementById('v-tva');
        if (celluleTva) {
            celluleTva.textContent = formater(tva);
        }
        document.getElementById('v-ttc').textContent = formater(ht + Math.round(tva));
    }

    function choisirArticle(select) {
        var option = select.options[select.selectedIndex];
        if (!option || !option.value) {
            return;
        }
        var ligne = select.closest('tr');
        champ(ligne, 'designation').value = option.dataset.designation || '';
        champ(ligne, 'unite').value = option.dataset.unite || '';
        champ(ligne, 'prix').value = option.dataset.prix || '';
        if (champ(ligne, 'tva')) {
            champ(ligne, 'tva').value = option.dataset.tva || '';
        }
        if (!champ(ligne, 'quantite').value) {
            champ(ligne, 'quantite').value = '1';
        }
    }

    corps.addEventListener('change', function (e) {
        if (e.target.classList.contains('v-article')) {
            choisirArticle(e.target);
        }
        calculer();
    });
    corps.addEventListener('input', calculer);
    formulaire.querySelector('[name="remiseGlobale"]').addEventListener('input', calculer);

    corps.addEventListener('click', function (e) {
        if (!e.target.classList.contains('v-retirer')) {
            return;
        }
        var ligne = e.target.closest('tr');
        if (corps.querySelectorAll('tr.v-ligne').length > 1) {
            ligne.remove();
        } else {
            ligne.querySelectorAll('input').forEach(function (i) { i.value = ''; });
            ligne.querySelector('select').value = '';
        }
        calculer();
    });

    document.getElementById('v-ajouter').addEventListener('click', function () {
        var modele = corps.querySelector('tr.v-ligne:last-child');
        var copie = modele.cloneNode(true);
        copie.querySelectorAll('input').forEach(function (i) {
            i.value = i.name === 'ligne.tva' ? (formulaire.dataset.tva || '') : '';
        });
        copie.querySelector('select').value = '';
        copie.querySelector('.v-total-ligne').textContent = '—';
        corps.appendChild(copie);
        copie.querySelector('select').focus();
    });

    calculer();
})();
