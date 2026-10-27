package com.sms.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sms.mobile.data.PasswordChange
import com.sms.mobile.data.RefreshRequest

/** The signed-in user's details, password change and sign-out; the same for every role. */
@Composable
fun ProfileScreen() {
    val shell = LocalShell.current
    val me = rememberLoad { shell.api.me() }
    val actions = rememberActions()
    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    AppScaffold("My account") { padding ->
        LoadContent(me, Modifier.padding(padding)) { m ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard("Account") {
                    InfoRow("Username", m.user.username)
                    InfoRow("Email", m.user.email)
                    InfoRow("Role", m.user.role.lowercase().replaceFirstChar { it.uppercase() })
                }
                m.student?.let { s ->
                    InfoCard("Student") {
                        InfoRow("Name", s.name)
                        InfoRow("Student ID", s.studentId)
                        InfoRow("Class", s.schoolClass?.name)
                        InfoRow("Roll number", s.rollNo)
                        InfoRow("Date of birth", formatDay(s.dob))
                        InfoRow("Address", s.address)
                        InfoRow("Parent contact", s.parentContact)
                    }
                }
                m.teacher?.let { t ->
                    InfoCard("Teacher") {
                        InfoRow("Name", t.name)
                        InfoRow("Specialization", t.subjectSpecialization)
                        InfoRow("Contact", t.contact)
                    }
                }
                InfoCard("Change password") {
                    FormField("Current password", current, { current = it }, errors["currentPassword"], password = true)
                    FormField("New password", newPassword, { newPassword = it }, errors["newPassword"], password = true)
                    FormField("Confirm new password", confirm, { confirm = it }, errors["confirmPassword"], password = true)
                    Text(
                        "This also signs you out on your other devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            errors = emptyMap()
                            actions.run(onError = { e ->
                                errors = e.fieldErrors
                                if (e.fieldErrors.isEmpty()) actions.toast(e.message)
                            }) {
                                // The server ends every session and returns a new one for this phone.
                                shell.client.store.save(shell.api.changePassword(PasswordChange(current, newPassword, confirm)))
                                current = ""
                                newPassword = ""
                                confirm = ""
                                actions.toast("Password changed.")
                            }
                        },
                        enabled = !actions.busy && current.isNotEmpty() && newPassword.isNotEmpty(),
                    ) { Text("Change password") }
                }
                OutlinedButton(
                    onClick = {
                        actions.run {
                            val refresh = shell.client.store.current?.refreshToken
                            // Sign out on this phone even if the server can't be reached.
                            if (refresh != null) runCatching { shell.api.logout(RefreshRequest(refresh)) }
                            shell.client.store.clear()
                        }
                    },
                    enabled = !actions.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}
