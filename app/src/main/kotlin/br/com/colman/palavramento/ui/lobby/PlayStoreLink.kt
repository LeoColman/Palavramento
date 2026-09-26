// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

/**
 * The Play Store page of the app with this [packageName], the link a player's invite carries. The
 * web address, not a `market://` one: the invite goes to whatever the friend opens it in, a chat on
 * a desktop included, and Android hands this address to the Play Store app when it is there.
 */
fun playStoreLink(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"
