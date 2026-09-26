// The game's submission rules, transcribed from
// domain/src/main/kotlin/br/com/colman/palavramento/domain/submission/SubmissionValidator.kt so the
// playable ad accepts, rejects and scores a trace exactly as the server would. Kept free of the DOM
// so teste-motor.js can check it against every word the real solver found (node teste-motor.js).
//
// The board is the round's own board_json: 16 tiles of { letters, value }, value already carrying
// the round's theme. A tile's options are letters.split('/'): "A/S" plays as A or as S, a digraph
// "QU" is one option of two letters. The words are the round's whole solution, keyed by the
// normalized (upper-case, unaccented) form, which is what the board spells.

const GRID = 4
const MINIMUM_LENGTH = 3 // domain/.../mutator/Mutator.kt DefaultMinimumLength

function areAdjacent(a, b) {
  const rows = Math.abs(Math.floor(a / GRID) - Math.floor(b / GRID))
  const cols = Math.abs((a % GRID) - (b % GRID))
  return a !== b && rows <= 1 && cols <= 1
}

// Path.isValidOn: not empty, in bounds, no tile twice, every step to a neighbour.
function isValidPath(board, path) {
  if (path.length === 0) return false
  if (!path.every(i => Number.isInteger(i) && i >= 0 && i < board.length)) return false
  if (new Set(path).size !== path.length) return false
  return path.every((to, k) => k === 0 || areAdjacent(path[k - 1], to))
}

// Path.spellings: the cartesian product of every tile's options, in order.
function spellings(board, path) {
  let results = ['']
  for (const index of path) {
    const options = board[index].letters.split('/')
    results = results.flatMap(prefix => options.map(option => prefix + option))
  }
  return results
}

// SubmissionValidator.validate, same checks in the same order, each short-circuiting the rest.
function validate(board, words, found, path) {
  if (!isValidPath(board, path)) return { rejected: 'InvalidPath' }
  const longEnough = spellings(board, path).filter(s => s.length >= MINIMUM_LENGTH)
  if (longEnough.length === 0) return { rejected: 'TooShort' }
  const inLexicon = longEnough.filter(s => words.has(s))
  if (inLexicon.length === 0) return { rejected: 'NotAWord' }
  const fresh = inLexicon.find(s => !found.has(s))
  if (fresh === undefined) return { rejected: 'AlreadyFound' }
  const score = path.reduce((sum, i) => sum + board[i].value, 0)
  return { normalized: fresh, display: words.get(fresh), score }
}

if (typeof module !== 'undefined') module.exports = { areAdjacent, isValidPath, spellings, validate, MINIMUM_LENGTH }
