// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.domain.stats.AcceptedWord
import br.com.colman.palavramento.domain.stats.LevelCurve
import br.com.colman.palavramento.domain.stats.RoundStatsCalculator
import br.com.colman.palavramento.server.plugins.JwtProviderName
import br.com.colman.palavramento.server.plugins.toVerifiedAccessToken
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRow
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.repository.SubmissionRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DefaultRoundsLimit = 50
private const val MaxRoundsLimit = 200

/** `/players/me*` (dossier §6.1/§6.3/§6.4, `Rest.kt`), all requiring a valid access token. */
fun Route.playerRoutes(
  playerRepository: PlayerRepository,
  playerStatsRepository: PlayerStatsRepository,
  roundResultRepository: RoundResultRepository,
  roundRepository: RoundRepository,
  submissionRepository: SubmissionRepository,
) {
  authenticate(JwtProviderName) {
    get("/players/me") {
      val token = call.principal<JWTPrincipal>()!!.toVerifiedAccessToken()
      val player = playerRepository.findById(token.playerId) ?: return@get call.respond(HttpStatusCode.NotFound)
      val totalXp = if (player.isGuest) 0L else playerStatsRepository.get(player.id)?.totalXp ?: 0L
      // LevelCurve itself is 0-indexed (levelForXp(0) == 0, dossier 9's xp_para_nivel(n) = 100 * n^1.5
      // is the XP needed to cross threshold n): a brand new player has crossed zero thresholds. The
      // lobby header (dossier 6.1) instead displays a 1-indexed level, like most games ("Nivel 1", not
      // "Nivel 0", at 0 XP), so the threshold count is shifted by one only here, at the REST boundary.
      val thresholdsCrossed = LevelCurve.levelForXp(totalXp.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
      val level = thresholdsCrossed + 1
      val xpForNextLevel = LevelCurve.xpForLevel(level).toLong()
      call.respond(PlayerProfile(player.id, player.displayName, player.isGuest, level, totalXp, xpForNextLevel))
    }

    get("/players/me/stats") {
      val token = call.principal<JWTPrincipal>()!!.toVerifiedAccessToken()
      if (token.isGuest) {
        call.respond(HttpStatusCode.Forbidden, ErrorBody("Guests have no lifetime stats (dossier 8)"))
        return@get
      }
      call.respond(playerStatsRepository.get(token.playerId).toLifetimeStats())
    }

    get("/players/me/rounds") {
      val token = call.principal<JWTPrincipal>()!!.toVerifiedAccessToken()
      val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MaxRoundsLimit)
        ?: DefaultRoundsLimit
      val results = roundResultRepository.findByPlayer(token.playerId, limit)
      val entries = results.mapNotNull { result ->
        buildHistoryEntry(result, roundRepository, submissionRepository, roundResultRepository)
      }
      call.respond(entries)
    }
  }
}

private suspend fun buildHistoryEntry(
  result: RoundResultRow,
  roundRepository: RoundRepository,
  submissionRepository: SubmissionRepository,
  roundResultRepository: RoundResultRepository,
): RoundHistoryEntry? {
  val record = roundRepository.findById(result.roundId) ?: return null
  val submissions = submissionRepository.findByRoundAndPlayer(result.roundId, result.playerId)
  val accepted = submissions.map { AcceptedWord(it.score, it.normalized.length, it.acceptedAt.toEpochMilli()) }
  // xp is overwritten with the value actually awarded (round_results.xp), so it never drifts from a
  // reconfigured XpFormula default computed here after the fact. secondsPerWord uses this player's
  // own entry time (ADR 0010: late join), not the round's own startsAt.
  val stats = RoundStatsCalculator.compute(result.enteredAt.toEpochMilli(), accepted).copy(xp = result.xp)
  val foundSet = submissions.map { it.normalized }.toSet()
  val words = roundRepository.loadSolution(result.roundId)
    .map { solved ->
      LabelledWord(
        solved.display,
        solved.score,
        solved.tier,
        solved.path,
        solved.normalized in foundSet
      )
    }
    .sortedByDescending { it.score }

  return RoundHistoryEntry(
    roundId = record.id,
    startsAt = record.startsAt.toEpochMilli(),
    board = record.board.tiles,
    mutator = record.mutator,
    themeTitle = record.themeTitle,
    themeSubtitle = record.themeSubtitle,
    maxScore = record.maxScore,
    maxWords = record.maxWords,
    stats = stats,
    rank = result.rank,
    totalPlayers = roundResultRepository.countPlayers(result.roundId),
    words = words,
  )
}

private fun PlayerStatsRow?.toLifetimeStats(): LifetimeStats {
  if (this == null) return LifetimeStats(0, 0, 0, null, 0, 0, 0, 0.0, 0.0, 0.0, null)
  return LifetimeStats(
    totalScore = totalScore,
    totalWords = totalWords,
    bestGameScore = bestGameScore,
    bestWord = bestWord,
    bestWordScore = bestWordScore,
    gamesCompleted = gamesCompleted,
    gamesPlayed = gamesPlayed,
    averageScore = if (gamesPlayed > 0) totalScore.toDouble() / gamesPlayed else 0.0,
    averageWords = if (gamesPlayed > 0) totalWords.toDouble() / gamesPlayed else 0.0,
    averagePointsPerWord = if (totalWords > 0) totalScore.toDouble() / totalWords else 0.0,
    bestRank = bestRank,
  )
}
