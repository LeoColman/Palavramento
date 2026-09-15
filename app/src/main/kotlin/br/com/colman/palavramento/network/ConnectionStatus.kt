// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

/**
 * Whether [MultiplayerSession] currently has a live, handshaken connection (task brief 5: a visible
 * "Reconectando..." banner while the socket is down). Deliberately separate from
 * [br.com.colman.palavramento.state.MatchUiState]: that state keeps the last known room screen
 * (Waiting/InRound/PostRound) across a drop so nothing found so far disappears from view, while this
 * flag is what tells the UI to overlay the banner on top of whichever screen that is.
 */
enum class ConnectionStatus {
  Connected,
  Reconnecting,
}
