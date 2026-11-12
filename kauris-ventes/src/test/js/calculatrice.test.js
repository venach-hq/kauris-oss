// Tests de la logique de la calculatrice de caisse (node, sans navigateur) : make verify les lance.
const {Calcul, Outils} = require(require('path').join(__dirname, '../../main/resources/static/caisse/calculatrice.js'));
const assert = require('assert');
function tape(c, s) { for (const k of s.split(' ')) {
  if (/^\d+$/.test(k)) for (const d of k) c.chiffre(d);
  else if (k===',') c.virgule(); else if ('+-*/'.includes(k)) c.operateur(k);
  else if (k==='=') c.egal(); else if (k==='%') c.pourcent(); else if (k==='±') c.signe(); else if (k==='<') c.retour();
  else throw k; } return c.valeur(); }
assert.strictEqual(tape(new Calcul(), '0 , 1 + 0 , 2 ='), 0.3);
assert.strictEqual(tape(new Calcul(), '2 + 3 * 4 ='), 20, 'calculatrice de poche : de gauche à droite');
assert.strictEqual(tape(new Calcul(), '200 + 10 % ='), 220);
assert.strictEqual(tape(new Calcul(), '200 - 10 % ='), 180);
assert.strictEqual(tape(new Calcul(), '200 * 10 % ='), 20);
assert.strictEqual(tape(new Calcul(), '200 + 10 % + 5 ='), 225);
assert.strictEqual(tape(new Calcul(), '15 %'), 0.15);
assert.strictEqual(tape(new Calcul(), '123 <'), 12);
assert.strictEqual(tape(new Calcul(), '5 ± * 2 ='), -10);
let c = new Calcul(); tape(c, '1 / 0 ='); assert.strictEqual(c.ecran(), 'Erreur'); tape(c, '7'); assert.strictEqual(c.valeur(), 7);
c = new Calcul(); tape(c, '1500'); c.memoirePlus(); tape(c, '500'); c.memoirePlus(); c.memoireRappel(); assert.strictEqual(c.valeur(), 2000);
c = new Calcul(); tape(c, '1234567 , 5'); assert.strictEqual(c.ecran(), '1 234 567,5');
c = new Calcul(); tape(c, '10000'); c.appliquer('x', x => Outils.ajouterTva(x, 18)); assert.strictEqual(c.valeur(), 11800);
assert.strictEqual(Outils.retirerTva(11800, 18), 10000);
assert.strictEqual(Outils.tvaDansTtc(11800, 18), 1800);
assert.strictEqual(Outils.tvaSurHt(10000, 18), 1800);
assert.strictEqual(Outils.prixDeVente(800, 25, false), 1000);
assert.strictEqual(Outils.prixDeVente(750, 25, true), 1000);
assert.ok(isNaN(Outils.prixDeVente(750, 100, true)));
const m = Outils.marge(800, 1000);
assert.deepStrictEqual(m, {marge: 200, tauxMarge: 25, tauxMarque: 20, coefficient: 1.25});
assert.deepStrictEqual(Outils.remise(25000, 10), {remise: 2500, prix: 22500});
assert.strictEqual(Outils.tauxDeRemise(25000, 20000), 20);
assert.strictEqual(Outils.arrondir(1183, 5, 'proche'), 1185);
assert.strictEqual(Outils.arrondir(1182, 5, 'proche'), 1180);
assert.strictEqual(Outils.arrondir(1181, 25, 'haut'), 1200);
const mo = Outils.monnaie(3540, 10000);
assert.strictEqual(mo.rendu, 6460);
assert.deepStrictEqual(mo.detail, [{coupure:5000,nombre:1},{coupure:1000,nombre:1},{coupure:250,nombre:1},{coupure:200,nombre:1},{coupure:10,nombre:1}]);
assert.strictEqual(Outils.monnaie(3542, 4000).reste, 3);
console.log('calculatrice : tout est bon');
