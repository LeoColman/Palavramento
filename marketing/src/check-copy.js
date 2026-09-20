#!/usr/bin/env node
// Checks every ad text in marketing/*.md against the character limit Google enforces.
//
// The markdown tables are the source of truth, not a copy of it: each table is preceded by
// an HTML comment saying its limit, and each row carries the text and its declared length.
// This script re-counts and fails when a text is over the limit or when the declared count
// drifted from the text, which is what happens when someone edits a headline and forgets
// the number next to it.
//
// Usage: marketing/src/check-copy.js
const fs = require('node:fs')
const path = require('node:path')

const docs = ['google-ads.md', 'play-store.md']
const marketing = path.join(__dirname, '..')

let checked = 0
const problems = []

for (const doc of docs) {
  const file = path.join(marketing, doc)
  if (!fs.existsSync(file)) continue

  let limit = null
  let fenced = null // collecting a ``` block that a marker introduced
  fs.readFileSync(file, 'utf8').split('\n').forEach((line, index) => {
    if (fenced) {
      if (line.startsWith('```')) {
        const actual = [...fenced.lines.join('\n')].length
        checked += 1
        if (actual > fenced.limit) {
          problems.push(`${doc}:${fenced.start}: bloco com ${actual} caracteres, limite ${fenced.limit}`)
        }
        fenced = null
        limit = null
      } else fenced.lines.push(line)
      return
    }

    const marker = line.match(/^<!--\s*limite:\s*(\d+)\s*-->$/)
    if (marker) {
      limit = Number(marker[1])
      return
    }
    if (limit === null) return

    if (line.startsWith('```')) {
      fenced = { limit, lines: [], start: index + 1 }
      return
    }

    // A row is "| text | count |". The header and the |---|---| separator are not.
    const row = line.match(/^\|\s*(.+?)\s*\|\s*(\d+)\s*\|$/)
    if (!row) {
      if (!line.startsWith('|')) limit = null
      return
    }

    const [, text, declared] = row
    // Count code points, not UTF-16 units: Google counts characters, and "ç" or "õ" is one.
    const actual = [...text].length
    const where = `${doc}:${index + 1}`
    checked += 1

    if (actual > limit) problems.push(`${where}: ${actual} caracteres, limite ${limit} -> ${text}`)
    else if (actual !== Number(declared)) {
      problems.push(`${where}: tabela diz ${declared}, texto tem ${actual} -> ${text}`)
    }
  })
}

if (problems.length) {
  console.error(`${problems.length} problema(s) em ${checked} textos:\n`)
  problems.forEach(p => console.error(`  ${p}`))
  process.exit(1)
}

console.log(`${checked} textos conferidos, todos dentro do limite.`)
