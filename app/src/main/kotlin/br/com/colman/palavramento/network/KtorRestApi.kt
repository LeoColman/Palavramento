// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RefreshRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** [RestApi] over Ktor, following `Rest.kt`'s endpoints and JSON bodies. */
class KtorRestApi(private val client: HttpClient, private val baseUrl: String) : RestApi {

  override suspend fun guestAuth(request: GuestAuthRequest): AuthTokens =
    client.post("$baseUrl/auth/guest") {
      contentType(ContentType.Application.Json)
      setBody(request)
    }.body()

  override suspend fun refresh(request: RefreshRequest): AuthTokens =
    client.post("$baseUrl/auth/refresh") {
      contentType(ContentType.Application.Json)
      setBody(request)
    }.body()

  override suspend fun register(request: RegisterRequest, guestAccessToken: String?): AuthTokens =
    client.post("$baseUrl/auth/register") {
      contentType(ContentType.Application.Json)
      guestAccessToken?.let { bearerAuth(it) }
      setBody(request)
    }.body()

  override suspend fun login(request: LoginRequest, guestAccessToken: String?): AuthTokens =
    client.post("$baseUrl/auth/login") {
      contentType(ContentType.Application.Json)
      guestAccessToken?.let { bearerAuth(it) }
      setBody(request)
    }.body()

  override suspend fun playerProfile(accessToken: String): PlayerProfile =
    client.get("$baseUrl/players/me") {
      bearerAuth(accessToken)
    }.body()

  override suspend fun lifetimeStats(accessToken: String): LifetimeStats =
    client.get("$baseUrl/players/me/stats") {
      bearerAuth(accessToken)
    }.body()

  override suspend fun roundHistory(accessToken: String, limit: Int): List<RoundHistoryEntry> =
    client.get("$baseUrl/players/me/rounds") {
      bearerAuth(accessToken)
      parameter("limit", limit)
    }.body()
}
