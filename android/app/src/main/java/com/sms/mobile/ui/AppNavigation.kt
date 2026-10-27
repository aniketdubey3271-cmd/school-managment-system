package com.sms.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sms.mobile.data.ApiClient

/** Login and sign-up when signed out; otherwise the screens for the user's role. */
@Composable
fun SmsRoot(client: ApiClient) {
    val session by client.store.session.collectAsState()
    val current = session
    if (current == null) {
        AuthFlow(client)
    } else {
        // A different user starts with fresh navigation.
        key(current.username, current.role) {
            SignedIn(client, current.role)
        }
    }
}

@Composable
private fun SignedIn(client: ApiClient, role: String) {
    val nav = rememberNavController()
    val tabs = when (role) {
        "STUDENT" -> StudentTabs
        "TEACHER" -> TeacherTabs
        else -> AdminTabs
    }
    val shell = remember(nav) { Shell(nav, tabs, client) }
    CompositionLocalProvider(LocalShell provides shell) {
        NavHost(navController = nav, startDestination = tabs.first().route) {
            composable(Routes.PROFILE) { ProfileScreen() }
            when (role) {
                "STUDENT" -> studentGraph()
                "TEACHER" -> teacherGraph()
                else -> adminGraph()
            }
        }
    }
}

/** A destination whose named path segments are Long ids. */
fun NavGraphBuilder.screen(route: String, vararg longArgs: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route, arguments = longArgs.map { name -> navArgument(name) { type = NavType.LongType } }) { entry ->
        content(entry)
    }
}

fun NavBackStackEntry.long(name: String): Long = arguments?.getLong(name) ?: 0L
