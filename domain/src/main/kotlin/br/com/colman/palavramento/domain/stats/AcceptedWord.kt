// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

/** One word a player scored during a round, as [RoundStatsCalculator] needs it. */
data class AcceptedWord(val score: Int, val length: Int, val acceptedAtEpochMs: Long)
