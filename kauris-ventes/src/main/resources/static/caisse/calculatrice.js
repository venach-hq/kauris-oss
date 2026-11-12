/*
 * Calculatrice de la caisse : grandes touches, clavier, mémoire, TVA, marge, remise, monnaie à rendre, arrondi.
 * La logique (Calcul, Outils) ne touche pas à la page : elle se teste seule avec node.
 * Une calculatrice se place dans la page avec le fragment « caisse/commun :: calculatrice ».
 */
(function (global) {
    'use strict';

    /** Arrondi des erreurs de virgule flottante (0,1 + 0,2 = 0,3). */
    function net(x) {
        if (!isFinite(x)) { return x; }
        return Math.round(x * 1e9) / 1e9;
    }

    /** Une calculatrice de poche : saisie, opération en attente, mémoire, ruban des calculs. */
    function Calcul() {
        this.saisie = '0';       // ce qui est affiché, en texte (point décimal)
        this.acc = null;         // premier terme
        this.op = null;          // + - * /
        this.nouveau = true;     // la prochaine touche chiffre remplace l'affichage
        this.memoire = 0;
        this.ruban = [];         // derniers calculs, pour s'y retrouver
        this.erreur = false;
    }

    Calcul.SYMBOLES = {'+': '+', '-': '−', '*': '×', '/': '÷'};

    Calcul.prototype.valeur = function () {
        return parseFloat(this.saisie);
    };

    Calcul.prototype.poser = function (x) {
        if (!isFinite(x)) {
            this.erreur = true;
            this.saisie = '0';
        } else {
            this.saisie = String(net(x));
        }
        this.nouveau = true;
    };

    Calcul.prototype.chiffre = function (d) {
        if (this.erreur) { this.effacer(); }
        this.apresPourcent = false;
        if (this.nouveau) {
            this.saisie = d === '00' ? '0' : d;
            this.nouveau = false;
        } else if (this.saisie === '0') {
            this.saisie = d === '00' ? '0' : d;
        } else if (this.saisie.replace(/[-.]/g, '').length < 15) {
            this.saisie += d;
        }
    };

    Calcul.prototype.virgule = function () {
        if (this.erreur) { this.effacer(); }
        if (this.nouveau) {
            this.saisie = '0.';
            this.nouveau = false;
        } else if (this.saisie.indexOf('.') < 0) {
            this.saisie += '.';
        }
    };

    Calcul.prototype.calculer = function (a, op, b) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': return b === 0 ? NaN : a / b;
        }
        return b;
    };

    Calcul.prototype.operateur = function (op) {
        if (this.erreur) { return; }
        if (this.op !== null && (!this.nouveau || this.apresPourcent)) {
            var r = this.calculer(this.acc, this.op, this.valeur());
            this.ruban.push(Calcul.texte(this.acc) + ' ' + Calcul.SYMBOLES[this.op] + ' ' + Calcul.texte(this.valeur())
                + ' = ' + Calcul.texte(r));
            this.poser(r);
        }
        this.acc = this.valeur();
        this.op = op;
        this.nouveau = true;
        this.apresPourcent = false;
    };

    Calcul.prototype.egal = function () {
        if (this.erreur || this.op === null) { return; }
        var b = this.valeur();
        var r = this.calculer(this.acc, this.op, b);
        this.noter(Calcul.texte(this.acc) + ' ' + Calcul.SYMBOLES[this.op] + ' ' + Calcul.texte(b), r);
        this.acc = null;
        this.op = null;
        this.apresPourcent = false;
        this.poser(r);
    };

    /** Comme une calculatrice de bureau : 200 + 10 % = 220 ; 200 × 10 % = 20 ; 15 % seul = 0,15. */
    Calcul.prototype.pourcent = function () {
        if (this.erreur) { return; }
        var b = this.valeur();
        if (this.op === '+' || this.op === '-') {
            this.poser(this.acc * b / 100);
        } else if (this.op !== null) {
            this.poser(b / 100);
        } else {
            this.poser(b / 100);
        }
        this.apresPourcent = true;   // la touche suivante (= ou opération) termine le calcul
    };

    Calcul.prototype.effacer = function () {
        this.saisie = '0';
        this.acc = null;
        this.op = null;
        this.nouveau = true;
        this.erreur = false;
        this.apresPourcent = false;
    };

    Calcul.prototype.effacerSaisie = function () {
        this.saisie = '0';
        this.nouveau = true;
        this.erreur = false;
    };

    Calcul.prototype.retour = function () {
        if (this.nouveau || this.erreur) { return; }
        this.saisie = this.saisie.length > 1 ? this.saisie.slice(0, -1) : '0';
        if (this.saisie === '-' || this.saisie === '') { this.saisie = '0'; }
    };

    Calcul.prototype.signe = function () {
        if (this.erreur || this.saisie === '0') { return; }
        this.saisie = this.saisie.charAt(0) === '-' ? this.saisie.slice(1) : '-' + this.saisie;
    };

    Calcul.prototype.memoirePlus = function () { this.memoire = net(this.memoire + this.valeur()); this.nouveau = true; };
    Calcul.prototype.memoireMoins = function () { this.memoire = net(this.memoire - this.valeur()); this.nouveau = true; };
    Calcul.prototype.memoireRappel = function () { this.poser(this.memoire); };
    Calcul.prototype.memoireEffacer = function () { this.memoire = 0; };

    /** Applique une fonction à l'affichage (TVA, arrondi…) et la note au ruban. */
    Calcul.prototype.appliquer = function (libelle, fonction) {
        if (this.erreur) { return; }
        var x = this.valeur();
        var r = fonction(x);
        this.noter(libelle + ' ' + Calcul.texte(x), r);
        this.poser(r);
    };

    /** Met une valeur venue d'ailleurs (un résultat d'assistant) à l'affichage. */
    Calcul.prototype.reprendre = function (x) {
        this.effacer();
        this.poser(x);
    };

    Calcul.prototype.noter = function (calcul, resultat) {
        this.ruban.push(calcul + ' = ' + Calcul.texte(resultat));
        if (this.ruban.length > 30) { this.ruban.shift(); }
    };

    /** 1234567.5 → « 1 234 567,5 » (espaces fines insécables comme en français). */
    Calcul.texte = function (x) {
        if (!isFinite(x)) { return 'Erreur'; }
        var negatif = x < 0;
        var parties = String(net(Math.abs(x))).split('.');
        var entier = parties[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return (negatif ? '−' : '') + entier + (parties.length > 1 ? ',' + parties[1] : '');
    };

    /** L'affichage pendant la saisie : garde la virgule finale et les zéros tapés. */
    Calcul.prototype.ecran = function () {
        if (this.erreur) { return 'Erreur'; }
        var s = this.saisie;
        var negatif = s.charAt(0) === '-';
        if (negatif) { s = s.slice(1); }
        var p = s.split('.');
        var entier = p[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return (negatif ? '−' : '') + entier + (p.length > 1 ? ',' + p[1] : '');
    };

    Calcul.prototype.attente = function () {
        return this.op === null ? '' : Calcul.texte(this.acc) + ' ' + Calcul.SYMBOLES[this.op];
    };

    // ------------------------------------------------------------------ outils de commerce

    var Outils = {
        /** HT → TTC */
        ajouterTva: function (ht, taux) { return net(ht * (1 + taux / 100)); },
        /** TTC → HT */
        retirerTva: function (ttc, taux) { return net(ttc / (1 + taux / 100)); },
        /** Montant de TVA contenu dans un prix TTC. */
        tvaDansTtc: function (ttc, taux) { return net(ttc - ttc / (1 + taux / 100)); },
        /** Montant de TVA sur un prix HT. */
        tvaSurHt: function (ht, taux) { return net(ht * taux / 100); },

        /** Prix de vente HT pour un taux de marge (sur le coût) ou un taux de marque (sur le prix de vente). */
        prixDeVente: function (cout, taux, surPrixDeVente) {
            if (surPrixDeVente) {
                return taux >= 100 ? NaN : net(cout / (1 - taux / 100));
            }
            return net(cout * (1 + taux / 100));
        },

        /** Marge d'un article : montant, taux de marge (sur coût), taux de marque (sur vente), coefficient. */
        marge: function (cout, vente) {
            var m = vente - cout;
            return {
                marge: net(m),
                tauxMarge: cout === 0 ? NaN : net(m / cout * 100),
                tauxMarque: vente === 0 ? NaN : net(m / vente * 100),
                coefficient: cout === 0 ? NaN : net(vente / cout)
            };
        },

        /** Prix remisé et montant de la remise. */
        remise: function (prix, taux) {
            var r = net(prix * taux / 100);
            return {remise: r, prix: net(prix - r)};
        },

        /** Le taux d'une remise : du prix de départ au prix final. */
        tauxDeRemise: function (avant, apres) {
            return avant === 0 ? NaN : net((avant - apres) / avant * 100);
        },

        /** Arrondi à un multiple (5 F : la plus petite pièce) ; sens : 'proche', 'haut' ou 'bas'. */
        arrondir: function (x, pas, sens) {
            if (!pas) { return x; }
            var q = x / pas;
            var r = sens === 'haut' ? Math.ceil(net(q)) : sens === 'bas' ? Math.floor(net(q)) : Math.round(q);
            return net(r * pas);
        },

        /** Billets et pièces du franc CFA. */
        COUPURES: [10000, 5000, 2000, 1000, 500, 250, 200, 100, 50, 25, 10, 5],

        /** Monnaie à rendre et comment la rendre en billets et pièces (le reste sous 5 F ne se rend pas). */
        monnaie: function (aPayer, recu) {
            var rendu = net(recu - aPayer);
            var detail = [];
            var reste = Math.floor(rendu);
            if (rendu > 0) {
                Outils.COUPURES.forEach(function (c) {
                    var n = Math.floor(reste / c);
                    if (n > 0) {
                        detail.push({coupure: c, nombre: n});
                        reste -= n * c;
                    }
                });
            }
            return {rendu: rendu, detail: detail, reste: rendu > 0 ? reste : 0};
        }
    };

    var exporte = {Calcul: Calcul, Outils: Outils};
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exporte;
    }
    global.KaurisCalculatrice = exporte;

    // ------------------------------------------------------------------ dans la page

    if (typeof document === 'undefined') {
        return;
    }

    function nombre(champ) {
        var v = (champ.value || '').replace(/[\s  ]/g, '').replace(',', '.');
        var x = parseFloat(v);
        return isNaN(x) ? 0 : x;
    }

    function brancher(boite) {
        var c = new Calcul();
        var ecran = boite.querySelector('[data-c="ecran"]');
        var attente = boite.querySelector('[data-c="attente"]');
        var memoire = boite.querySelector('[data-c="memoire"]');
        var ruban = boite.querySelector('[data-c="ruban"]');
        var taux = boite.querySelector('[data-c="taux"]');
        var pas = boite.querySelector('[data-c="pas"]');

        function afficher() {
            ecran.textContent = c.ecran();
            attente.textContent = c.attente();
            memoire.textContent = c.memoire !== 0 ? 'M ' + Calcul.texte(c.memoire) : '';
            ruban.innerHTML = '';
            c.ruban.slice(-8).forEach(function (ligne) {
                var li = document.createElement('li');
                li.textContent = ligne;
                ruban.appendChild(li);
            });
            ruban.scrollTop = ruban.scrollHeight;
        }

        function t() {
            return nombre(taux);
        }

        var actions = {
            'c': function () { c.effacer(); },
            'ce': function () { c.effacerSaisie(); },
            'retour': function () { c.retour(); },
            'signe': function () { c.signe(); },
            'virgule': function () { c.virgule(); },
            'egal': function () { c.egal(); },
            'pourcent': function () { c.pourcent(); },
            'mc': function () { c.memoireEffacer(); },
            'mr': function () { c.memoireRappel(); },
            'm+': function () { c.memoirePlus(); },
            'm-': function () { c.memoireMoins(); },
            '+tva': function () { c.appliquer('TTC (TVA ' + t() + ' %) de', function (x) { return Outils.ajouterTva(x, t()); }); },
            '-tva': function () { c.appliquer('HT (TVA ' + t() + ' %) de', function (x) { return Outils.retirerTva(x, t()); }); },
            'tva-ttc': function () { c.appliquer('TVA ' + t() + ' % comprise dans', function (x) { return Outils.tvaDansTtc(x, t()); }); },
            'tva-ht': function () { c.appliquer('TVA ' + t() + ' % sur', function (x) { return Outils.tvaSurHt(x, t()); }); },
            'copier': function () {
                try {
                    global.navigator.clipboard.writeText(c.saisie.replace('.', ','));
                } catch (e) { /* presse-papiers indisponible */ }
            },
            'arrondir': function () {
                var p = nombre(pas);
                c.appliquer('Arrondi à ' + p + ' de', function (x) { return Outils.arrondir(x, p, 'proche'); });
            }
        };

        boite.addEventListener('click', function (e) {
            var b = e.target.closest('button');
            if (!b || !boite.contains(b)) { return; }
            if (b.dataset.chiffre) {
                c.chiffre(b.dataset.chiffre);
            } else if (b.dataset.op) {
                c.operateur(b.dataset.op);
            } else if (b.dataset.action && actions[b.dataset.action]) {
                actions[b.dataset.action]();
            } else if (b.dataset.reprendre) {
                var sortie = boite.querySelector('[data-sortie="' + b.dataset.reprendre + '"]');
                if (sortie && sortie.dataset.valeur) {
                    c.reprendre(parseFloat(sortie.dataset.valeur));
                }
            } else {
                return;
            }
            e.preventDefault();
            afficher();
        });

        // Clavier : chiffres, opérations, Entrée, Retour arrière, Échap. Pas quand on écrit dans un champ.
        document.addEventListener('keydown', function (e) {
            if (boite.offsetParent === null || e.ctrlKey || e.metaKey || e.altKey) { return; }
            var cible = e.target;
            if (cible && (cible.tagName === 'INPUT' || cible.tagName === 'TEXTAREA' || cible.tagName === 'SELECT')) {
                return;
            }
            var k = e.key;
            if (/^[0-9]$/.test(k)) { c.chiffre(k); }
            else if (k === ',' || k === '.') { c.virgule(); }
            else if (k === '+' || k === '-' || k === '*' || k === '/') { c.operateur(k); }
            else if (k === 'Enter' || k === '=') { c.egal(); }
            else if (k === '%') { c.pourcent(); }
            else if (k === 'Backspace') { c.retour(); }
            else if (k === 'Escape' || k === 'Delete') { c.effacer(); }
            else { return; }
            e.preventDefault();
            afficher();
        });

        // ---- assistants : chaque résultat peut revenir à l'écran de la calculatrice
        function sortir(nom, x, texte) {
            var s = boite.querySelector('[data-sortie="' + nom + '"]');
            if (!s) { return; }
            s.dataset.valeur = isFinite(x) ? String(x) : '';
            s.textContent = isFinite(x) ? (texte || Calcul.texte(x)) : '—';
        }

        function champ(nom) {
            return nombre(boite.querySelector('[data-entree="' + nom + '"]'));
        }

        function assistants() {
            // Prix de vente à partir du coût
            var cout = champ('cout');
            var tauxVoulu = champ('taux-voulu');
            var surVente = (boite.querySelector('[data-entree="base"]') || {}).value === 'vente';
            var pv = Outils.prixDeVente(cout, tauxVoulu, surVente);
            sortir('pv-ht', pv);
            sortir('pv-ttc', Outils.ajouterTva(pv, t()));
            // Marge d'un article
            var m = Outils.marge(champ('m-cout'), champ('m-vente'));
            sortir('m-marge', m.marge);
            sortir('m-taux-marge', m.tauxMarge, Calcul.texte(Math.round(m.tauxMarge * 100) / 100) + ' %');
            sortir('m-taux-marque', m.tauxMarque, Calcul.texte(Math.round(m.tauxMarque * 100) / 100) + ' %');
            sortir('m-coef', m.coefficient, Calcul.texte(Math.round(m.coefficient * 1000) / 1000));
            // Remise
            var r = Outils.remise(champ('r-prix'), champ('r-taux'));
            sortir('r-remise', r.remise);
            sortir('r-net', r.prix);
            var tr = Outils.tauxDeRemise(champ('r-avant'), champ('r-apres'));
            sortir('r-taux-trouve', tr, Calcul.texte(Math.round(tr * 100) / 100) + ' %');
            // Monnaie
            var mo = Outils.monnaie(champ('mo-du'), champ('mo-recu'));
            sortir('mo-rendu', mo.rendu);
            var detail = boite.querySelector('[data-c="coupures"]');
            if (detail) {
                detail.textContent = mo.detail.map(function (d) {
                    return d.nombre + ' × ' + Calcul.texte(d.coupure);
                }).join(' · ') + (mo.reste > 0 ? ' (+ ' + mo.reste + ' F non rendus)' : '');
            }
        }

        boite.addEventListener('input', function (e) {
            if (e.target.matches('[data-entree], [data-c="taux"]')) { assistants(); }
        });
        boite.addEventListener('change', function (e) {
            if (e.target.matches('[data-entree]')) { assistants(); }
        });

        afficher();
        assistants();
        boite.kaurisCalcul = c;
    }

    function demarrer() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-calculatrice]'), brancher);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', demarrer);
    } else {
        demarrer();
    }
})(typeof window !== 'undefined' ? window : globalThis);
