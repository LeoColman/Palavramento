// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.navigation

/** Route names for [PalavramentoNavHost] (task brief flow: Lobby -> Room -> Lobby). */
object Routes {
  const val Lobby = "lobby"
  const val Room = "room"

  /** "Sobre" (task brief 6), reachable from the match settings sheet. */
  const val About = "about"

  /** Login/registro (dossier 8, task brief 4), reachable from the lobby's "Entrar" button. */
  const val Login = "login"

  /** Historico local (dossier 7, task brief 3), reachable from the lobby. */
  const val History = "history"
}
