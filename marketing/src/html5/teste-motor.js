// Checks motor.js against the real solver: every word of every round in rodadas.json, traced along
// the path the server's solver recorded, has to be accepted with the score the server recorded.
// Plus one case per rejection reason. Run from the repository root:
//
//   node marketing/src/html5/teste-motor.js

const path = require('path')
const assert = require('assert')
const { validate, spellings } = require('./motor.js')
const rounds = require(path.join(__dirname, 'rodadas.json'))

let checked = 0
for (const round of rounds) {
  const words = new Map(round.words.map(([normalized, display]) => [normalized, display]))
  for (const [normalized, display, score, , wordPath] of round.words) {
    const result = validate(round.board, words, new Set(), wordPath)
    assert.ok(!result.rejected, `${round.theme}: ${normalized} rejected as ${result.rejected}`)
    assert.strictEqual(result.score, score, `${round.theme}: ${normalized} scored ${result.score}, server ${score}`)
    // A path over an "Uma ou outra" tile can spell more than one word; the solver's word is one of them.
    assert.ok(spellings(round.board, wordPath).includes(normalized), `${round.theme}: path does not spell ${normalized}`)
    assert.ok(words.has(result.normalized))
    checked++
  }
}

// One case per rejection, on the first round (a 4x4 whose tile 0 and tile 15 are not neighbours).
const round = rounds[0]
const words = new Map(round.words.map(([n, d]) => [n, d]))
const [first, , , , firstPath] = round.words[0]
assert.strictEqual(validate(round.board, words, new Set(), [0, 15]).rejected, 'InvalidPath')
assert.strictEqual(validate(round.board, words, new Set(), [0, 0]).rejected, 'InvalidPath')
assert.strictEqual(validate(round.board, words, new Set(), []).rejected, 'InvalidPath')
assert.strictEqual(validate(round.board, words, new Set([first]), firstPath).rejected, 'AlreadyFound')
const twoPlain = [0, 1]
if (spellings(round.board, twoPlain).every(s => s.length < 3)) {
  assert.strictEqual(validate(round.board, words, new Set(), twoPlain).rejected, 'TooShort')
}
// Some neighbouring triple that spells no word at all.
let notAWord = null
for (let a = 0; a < 16 && !notAWord; a++) for (let b = 0; b < 16 && !notAWord; b++) for (let c = 0; c < 16 && !notAWord; c++) {
  const p = [a, b, c]
  const r = validate(round.board, words, new Set(), p)
  if (r.rejected === 'NotAWord') notAWord = p
}
assert.ok(notAWord, 'expected at least one three-tile path that spells no word')

console.log(`${checked} palavras de ${rounds.length} rodadas aceitas com a pontuação do servidor; rejeições conferidas.`)
