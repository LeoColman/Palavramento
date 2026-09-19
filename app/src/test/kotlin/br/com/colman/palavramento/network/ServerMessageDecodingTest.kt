// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import android.util.Log
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

/**
 * What [KtorMultiplayerTransport] does with a frame from a server that is ahead of this build
 * (ADR 0018): the socket must survive it, because reconnecting only fetches the same frame again.
 */
class ServerMessageDecodingTest : FunSpec({

  // android.util.Log is unmocked on the plain JVM, and the skip path logs.
  beforeTest {
    mockkStatic(Log::class)
    every { Log.w(any(), any<String>()) } returns 0
  }
  afterTest { unmockkStatic(Log::class) }

  test("a known message decodes as itself") {
    val text = """{"type":"LobbyState","nextRoundStartsAt":1700000000000,"playersWaiting":3}"""

    decodeServerMessageOrNull(text) shouldBe ServerMessage.LobbyState(1_700_000_000_000, 3)
  }

  test("a message type this build has no case for arrives as Unknown, not as a dropped frame") {
    val text = """{"type":"RoundPaused","roundId":"round-1"}"""

    decodeServerMessageOrNull(text) shouldBe ServerMessage.Unknown
  }

  test("a frame this build cannot decode at all is skipped instead of failing the session") {
    // A LobbyState whose required playersWaiting is gone: nothing left to build the message from.
    val text = """{"type":"LobbyState","nextRoundStartsAt":1700000000000}"""

    decodeServerMessageOrNull(text) shouldBe null
  }

  test("a frame that is not even JSON is skipped") {
    decodeServerMessageOrNull("<html>502 Bad Gateway</html>") shouldBe null
  }
})
