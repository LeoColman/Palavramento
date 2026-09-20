// Fills a local Palavramento room with plausible players, so the emulator screenshots show a
// real leaderboard instead of a room of one. Only ever points at a local server: it reads the
// round's solution straight out of the development database to know where the words are.
//
// Uso:
//   REPO=$PWD MINUTES=30 node marketing/src/jogadores-de-teste.js
//
// Variáveis:
//   REPO           raiz do repositório, onde o docker-compose.yml está (obrigatória)
//   BASE           servidor (padrão http://localhost:8080)
//   MINUTES        por quanto tempo jogar (padrão 12)
//   HERO           nome da conta que o emulador vai usar; quando definida, essa conta joga
//                  junto e acumula o histórico que a tela de lobby mostra
//   HERO_EMAIL     e-mail dessa conta
//   HERO_PASSWORD  senha dessa conta
//   HERO_SKILL     fração da solução que ela acha (padrão 0.022)
const { execFileSync } = require('node:child_process')

const BASE = process.env.BASE || 'http://localhost:8080'
const WS = BASE.replace('http', 'ws') + '/ws/multiplayer'

async function post(path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method: 'POST', headers, body: JSON.stringify(body) })
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`)
  return res.json()
}

function roundWords(roundId) {
  const sql = `select word, score, path_json from round_words where round_id='${roundId}' order by score desc`
  const out = execFileSync('docker', [
    'compose', 'exec', '-T', 'postgres',
    'psql', '-U', 'palavramento', '-d', 'palavramento', '-At', '-F', '|', '-c', sql,
  ], { cwd: process.env.REPO, encoding: 'utf8' })
  return out.trim().split('\n').filter(Boolean).map(line => {
    const [word, score, path] = line.split('|')
    return { word, score: Number(score), path: JSON.parse(path) }
  })
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

// Each bot gets a skill level: the fraction of the solution it will find, and how fast.
class Bot {
  constructor(tokens, skill) {
    this.tokens = tokens
    this.skill = skill
    this.done = false
  }

  connect() {
    this.ws = new WebSocket(WS)
    this.ws.onopen = () => this.ws.send(JSON.stringify({ type: 'JoinRoom', languageCode: 'pt-BR', sessionToken: this.tokens.accessToken }))
    this.ws.onmessage = e => this.onMessage(JSON.parse(e.data))
    this.ws.onerror = e => console.error(this.tokens.displayName, 'ws error', e.message)
  }

  onMessage(msg) {
    if (msg.type === 'RoundStart') this.play(msg)
    if (msg.type === 'Leaderboard') {
      const me = msg.self
      console.log(`  ${this.tokens.displayName}: rank ${me.rank}/${msg.totalPlayers}, ${me.score} pts, ${me.words} palavras`)
    }
  }

  async play(round) {
    if (this.playing === round.roundId) return
    this.playing = round.roundId
    const all = roundWords(round.roundId)
    // Longer words first for the high scorers, a shuffled middle for everyone else: a bot that
    // submits the solution in score order finishes with a suspiciously perfect word list.
    const pool = all.filter(w => w.word.length >= 3)
    const take = Math.max(3, Math.round(pool.length * this.skill.fraction))
    const picks = []
    const bias = this.skill.fraction
    for (const w of pool) {
      if (picks.length >= take) break
      if (Math.random() < 0.35 + bias) picks.push(w)
    }
    const msLeft = round.endsAt - Date.now()
    const gap = Math.max(250, (msLeft * 0.75) / Math.max(picks.length, 1))
    for (const w of picks) {
      if (Date.now() > round.endsAt - 500) break
      this.ws.send(JSON.stringify({ type: 'SubmitWord', roundId: round.roundId, path: w.path, clientTimestamp: Date.now() }))
      await sleep(gap * (0.6 + Math.random() * 0.8))
    }
  }
}

const ROSTER = [
  ['Marina',   0.030],
  ['Rafa_TD',  0.024],
  ['bia.moraes', 0.019],
  ['Gustavo Reis', 0.015],
  ['ju',       0.012],
  ['Caio',     0.009],
  ['nanda_91', 0.007],
  ['Pedro H.', 0.005],
]

async function main() {
  const bots = []
  if (process.env.HERO) {
    // The account the emulator logs into: it needs a play history for the lobby stats panel.
    const guest = await post('/auth/guest', { displayName: process.env.HERO })
    const tokens = await post(
      '/auth/register',
      { email: process.env.HERO_EMAIL, password: process.env.HERO_PASSWORD, displayName: process.env.HERO },
      guest.accessToken,
    ).catch(() => post('/auth/login', { email: process.env.HERO_EMAIL, password: process.env.HERO_PASSWORD }))
    bots.push(new Bot(tokens, { fraction: Number(process.env.HERO_SKILL || 0.022) }))
  }
  for (const [name, fraction] of ROSTER) {
    const tokens = await post('/auth/guest', { displayName: name })
    bots.push(new Bot(tokens, { fraction }))
  }
  bots.forEach(b => b.connect())
  console.log(`${bots.length} bots conectados`)
  const minutes = Number(process.env.MINUTES || 12)
  await sleep(minutes * 60 * 1000)
  process.exit(0)
}

main()
