// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

/** The two tabs of the post-round screen: Resultados (dossier 6.3) and Placar (dossier 6.4). */
enum class PostRoundTab {
  Results,
  Leaderboard,
}

/**
 * Whether [ResultsAndLeaderboardScreen] shows Placar on its own, without the player touching a tab.
 *
 * The screen opens on Resultados and flips to Placar halfway through the intermission (dossier 1.4:
 * 25 seconds between rounds), so both halves of the post-round information get seen without anyone
 * having to remember the second tab exists.
 *
 * [intermissionMs] is the whole wait, measured once when `LobbyState` first tells the client when the
 * next round starts; zero means it is not known yet, and then nothing is flipped. [leaderboardReady]
 * is false until the `Leaderboard` message arrives, since flipping to an empty tab would be worse
 * than staying put. [playerChoseTab] turns the whole thing off for the rest of the intermission: once
 * the player picks a tab themselves, the screen stops moving under their finger.
 */
fun shouldAutoShowLeaderboard(
  remainingMs: Long,
  intermissionMs: Long,
  leaderboardReady: Boolean,
  playerChoseTab: Boolean,
): Boolean = !playerChoseTab && leaderboardReady && intermissionMs > 0 && remainingMs <= intermissionMs / 2
