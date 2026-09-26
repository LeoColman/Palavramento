// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Test tag for the "Convidar" button, used by instrumented tests. */
const val LobbyInviteButtonTestTag = "lobbyInviteButton"

/**
 * Invites friends through Android's own share sheet, with the Play Store link in the message. The
 * multiplayer-only game is only as good as its room is full, so the lobby asks the one person
 * already playing to bring the next ones in.
 */
@Composable
fun InviteFriendsCard() {
  val colors = PalavramentoColors.current
  val context = LocalContext.current
  val message = stringResource(R.string.lobby_invite_message, playStoreLink(context.packageName))
  val chooserTitle = stringResource(R.string.lobby_invite_chooser_title)
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(R.string.lobby_invite_title), color = colors.textPrimary)
    Text(stringResource(R.string.lobby_invite_body), color = colors.textSecondary)
    Button(
      onClick = {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        context.startActivity(Intent.createChooser(send, chooserTitle))
      },
      modifier = Modifier.testTag(LobbyInviteButtonTestTag),
    ) {
      Text(stringResource(R.string.lobby_invite_button))
    }
  }
}
