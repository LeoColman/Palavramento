// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.colman.palavramento.ui.about.AboutScreen
import br.com.colman.palavramento.ui.auth.LoginScreen
import br.com.colman.palavramento.ui.history.HistoryScreen
import br.com.colman.palavramento.ui.lobby.LobbyScreen
import br.com.colman.palavramento.ui.room.RoomScreen

@Composable
fun PalavramentoNavHost(navController: NavHostController = rememberNavController()) {
  NavHost(navController = navController, startDestination = Routes.Lobby) {
    composable(Routes.Lobby) {
      LobbyScreen(
        onPlayClicked = { navController.navigate(Routes.Room) },
        onLoginClicked = { navController.navigate(Routes.Login) },
        onHistoryClicked = { navController.navigate(Routes.History) },
      )
    }
    composable(Routes.Room) {
      RoomScreen(
        onLeaveRoom = { navController.popBackStack() },
        onOpenAbout = { navController.navigate(Routes.About) },
      )
    }
    composable(Routes.About) {
      AboutScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.Login) {
      LoginScreen(
        onBack = { navController.popBackStack() },
        onAuthenticated = { navController.popBackStack() },
      )
    }
    composable(Routes.History) {
      HistoryScreen(onBack = { navController.popBackStack() })
    }
  }
}
