// Le total d'un ticket calculé par l'écran de caisse doit être celui de la facture (Totaux.de en Java).
const assert = require('assert');
const {totaux, paiement} = require(require('path').join(__dirname, '../../main/resources/static/caisse/caisse.js'));

// 4 pains à 250 HT et 2 laits à 1 000 HT, TVA 18 % : 3 000 HT + 540 = 3 540 (comme CaisseTest).
let t = totaux([{ht: 250, q: 4, taux: 18}, {ht: 1000, q: 2, taux: 18}], 0);
assert.strictEqual(t.totalTtc, 3540);
// Remise de 5 % : base 2 850, TVA 513.
t = totaux([{ht: 250, q: 4, taux: 18}, {ht: 1000, q: 2, taux: 18}], 5);
assert.deepStrictEqual([t.totalHt, t.totalTva, t.totalTtc], [2850, 513, 3363]);
// Deux taux, quantité décimale, arrondis demi vers le haut.
t = totaux([{ht: 1000, q: 0.333, taux: 18}, {ht: 125, q: 3, taux: 0}], 0);
assert.deepStrictEqual(t.tva.map(x => [x.taux, x.base, x.montant]), [[0, 375, 0], [18, 333, 60]]);
assert.strictEqual(t.totalTtc, 768);
t = totaux([{ht: 5, q: 0.5, taux: 0}], 0);
assert.strictEqual(t.totalTtc, 3, '2,5 s’arrondit à 3');

// Paiement : 1 000 en Mobile Money, 5 000 en espèces pour 3 540 → 2 460 rendus.
assert.deepStrictEqual(paiement(3540, 5000, 1000, 0), {especes: 2540, recu: 5000, rendu: 2460, possible: true});
assert.strictEqual(paiement(3540, 0, 0, 0).rendu, 0, 'rien saisi : le compte juste');
assert.strictEqual(paiement(3540, 2000, 0, 0).possible, false);
assert.strictEqual(paiement(3540, 0, 4000, 0).possible, false, 'trop payé par Mobile Money');
console.log('caisse : tout est bon');
