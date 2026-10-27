@file:OptIn(ExperimentalMaterial3Api::class)

package com.sms.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sms.mobile.data.ApiClient
import com.sms.mobile.data.LoginRequest
import com.sms.mobile.data.Ref
import com.sms.mobile.data.SignupRequest

@Composable
fun AuthFlow(client: ApiClient) {
    var signingUp by rememberSaveable { mutableStateOf(false) }
    if (signingUp) {
        BackHandler { signingUp = false }
        SignupScreen(client, onDone = { signingUp = false })
    } else {
        LoginScreen(client, onSignup = { signingUp = true })
    }
}

@Composable
private fun LoginScreen(client: ApiClient, onSignup: () -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val actions = rememberActions()

    fun signIn() {
        error = null
        actions.run(onError = { error = it.message }) {
            client.store.save(client.api.login(LoginRequest(login.trim(), password)))
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "STUDENT MANAGEMENT SYSTEM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Sign in to your portal", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Use your school account to continue.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("Username or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { signIn() },
                enabled = !actions.busy && login.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (actions.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            TextButton(onClick = onSignup, modifier = Modifier.fillMaxWidth()) { Text("New here? Create an account") }
            Text(
                "Server: ${client.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Same rules as the website: students sign up freely, teachers and admins need an invitation code. */
@Composable
private fun SignupScreen(client: ApiClient, onDone: () -> Unit) {
    val options = rememberLoad { client.api.signupOptions() }
    var role by rememberSaveable { mutableStateOf("STUDENT") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rollNo by rememberSaveable { mutableStateOf("") }
    var classId by rememberSaveable { mutableStateOf<Long?>(null) }
    var specialization by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var teacherCode by rememberSaveable { mutableStateOf("") }
    var adminCode by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    val actions = rememberActions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create an account") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LoadContent(options, Modifier.padding(padding)) { opts ->
            val allowed = when (role) {
                "TEACHER" -> opts.teacherSignupEnabled
                "ADMIN" -> opts.adminSignupEnabled
                else -> true
            }
            val roleLabel = when (role) {
                "TEACHER" -> "teacher"
                "ADMIN" -> "administrator"
                else -> "student"
            }
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("I am registering as", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("STUDENT" to "Student", "TEACHER" to "Teacher", "ADMIN" to "Admin").forEach { (value, label) ->
                        FilterChip(
                            selected = role == value,
                            onClick = { role = value; errors = emptyMap(); formError = null },
                            label = { Text(label) },
                        )
                    }
                }
                if (role != "ADMIN") FormField("Full name", name, { name = it }, errors["name"])
                FormField("Username", username, { username = it }, errors["username"])
                FormField("Email", email, { email = it }, errors["email"], keyboardType = KeyboardType.Email)
                FormField("Password", password, { password = it }, errors["password"], hint = "At least 8 characters", password = true)

                when (role) {
                    "STUDENT" -> {
                        FormField("Roll number", rollNo, { rollNo = it }, errors["rollNo"])
                        if (opts.classes.isEmpty()) {
                            Text(
                                "No classes exist yet. Ask the administrator to add classes first.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Picker(
                                label = "Class and section",
                                options = opts.classes,
                                selected = opts.classes.find { it.id == classId },
                                text = Ref::name,
                                onSelect = { classId = it.id },
                                error = errors["classId"],
                            )
                        }
                    }
                    "TEACHER" -> {
                        FormField("Subject specialization", specialization, { specialization = it }, errors["subjectSpecialization"])
                        FormField("Contact number", contact, { contact = it }, errors["contact"], keyboardType = KeyboardType.Phone)
                        FormField("Teacher invitation code", teacherCode, { teacherCode = it }, errors["teacherCode"], password = true)
                        if (!opts.teacherSignupEnabled) {
                            Text("Teacher sign-up isn't enabled on this server.", color = MaterialTheme.colorScheme.error)
                        }
                        Text("After registering, an administrator assigns your classes and subjects.")
                    }
                    else -> {
                        FormField("Administrator invitation code", adminCode, { adminCode = it }, errors["adminCode"], password = true)
                        if (!opts.adminSignupEnabled) {
                            Text("Administrator sign-up isn't enabled on this server.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        errors = emptyMap()
                        formError = null
                        actions.run(onError = { errors = it.fieldErrors; formError = it.message }) {
                            client.api.signup(
                                SignupRequest(
                                    role = role,
                                    username = username.trim(),
                                    email = email.trim(),
                                    password = password,
                                    name = name.trim().ifBlank { null },
                                    rollNo = if (role == "STUDENT") rollNo.trim() else null,
                                    classId = if (role == "STUDENT") classId else null,
                                    subjectSpecialization = if (role == "TEACHER") specialization.trim().ifBlank { null } else null,
                                    contact = if (role == "TEACHER") contact.trim().ifBlank { null } else null,
                                    teacherCode = if (role == "TEACHER") teacherCode else null,
                                    adminCode = if (role == "ADMIN") adminCode else null,
                                ),
                            )
                            actions.toast("Account created. Sign in to continue.")
                            onDone()
                        }
                    },
                    enabled = allowed && !actions.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Create $roleLabel account") }
            }
        }
    }
}
