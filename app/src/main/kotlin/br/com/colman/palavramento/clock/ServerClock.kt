// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.clock

/**
 * Server-now estimate anchored on a clock-sync [offsetMs] (dossier 5.3): the round countdown must
 * never be driven off the wall clock, since a player can change it mid-round or it can jump around
 * daylight saving / NTP corrections.
 *
 * [elapsedRealtimeMs] is injected as a plain function so this class has no Android dependency and
 * stays unit-testable on the JVM; production code wires it to `android.os.SystemClock::elapsedRealtime`,
 * whose value only ever moves forward while the device is awake.
 */
class ServerClock(private val offsetMs: Long, private val elapsedRealtimeMs: () -> Long) {

  /** Best current estimate of the server's wall-clock time, in epoch milliseconds. */
  fun nowMs(): Long = elapsedRealtimeMs() + offsetMs
}
