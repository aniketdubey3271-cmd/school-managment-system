@file:OptIn(ExperimentalMaterial3Api::class)

package com.sms.mobile.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.sms.mobile.data.ApiClient
import com.sms.mobile.data.ApiException
import com.sms.mobile.data.SmsApi
import com.sms.mobile.data.toApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

// ---- navigation shell ----

object Routes {
    const val PROFILE = "profile"
}

/** A bottom-navigation destination. */
data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** Navigation and API access for the signed-in screens. */
class Shell(val nav: NavHostController, val tabs: List<NavTab>, val client: ApiClient) {

    val api: SmsApi
        get() = client.api

    fun open(route: String) {
        nav.navigate(route)
    }

    fun back() {
        nav.popBackStack()
    }

    fun switchTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

val LocalShell = staticCompositionLocalOf<Shell> { error("No Shell provided") }

/**
 * A screen with a top bar. Tab screens (tab = their route) get the bottom navigation and an
 * account button; other screens get a back arrow.
 */
@Composable
fun AppScaffold(
    title: String,
    tab: String? = null,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val shell = LocalShell.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (tab == null) {
                        IconButton(onClick = { shell.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    actions()
                    if (onRefresh != null) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    }
                    if (tab != null) {
                        IconButton(onClick = { shell.open(Routes.PROFILE) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "My account")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (tab != null) {
                NavigationBar {
                    shell.tabs.forEach { t ->
                        NavigationBarItem(
                            selected = t.route == tab,
                            onClick = { if (t.route != tab) shell.switchTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

// ---- loading data ----

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class LoadState<T> internal constructor(private val trigger: MutableIntState) {

    var current: Load<T> by mutableStateOf<Load<T>>(Load.Loading)
        internal set

    val valueOrNull: T?
        get() = when (val c = current) {
            is Load.Ready -> c.value
            else -> null
        }

    /** Fetches again; the current data stays on screen until the new data arrives. */
    fun reload() {
        trigger.intValue++
    }
}

/** Runs [fetch] when the screen opens, whenever [keys] change, and on reload(). */
@Composable
fun <T> rememberLoad(vararg keys: Any?, fetch: suspend () -> T): LoadState<T> {
    val trigger = remember(*keys) { mutableIntStateOf(0) }
    val state = remember(*keys) { LoadState<T>(trigger) }
    LaunchedEffect(*keys, trigger.intValue) {
        if (state.current is Load.Failed) state.current = Load.Loading
        state.current = try {
            Load.Ready(fetch())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.toApiException().message ?: "Something went wrong.")
        }
    }
    return state
}

/** A spinner while loading, an error with a retry button, or the content. */
@Composable
fun <T> LoadContent(state: LoadState<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (val load = state.current) {
        is Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Load.Failed -> ErrorState(load.message, onRetry = { state.reload() }, modifier = modifier)
        is Load.Ready -> content(load.value)
    }
}

// ---- running actions ----

/** Runs one user action at a time (save, delete, upload) and reports failures. */
class Actions internal constructor(private val scope: CoroutineScope, private val context: Context) {

    var busy by mutableStateOf(false)
        private set

    fun run(onError: (ApiException) -> Unit = { toast(it.message) }, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.toApiException())
            } finally {
                busy = false
            }
        }
    }

    fun toast(message: String?) {
        Toast.makeText(context, message ?: "Something went wrong.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberActions(): Actions {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(scope, context) { Actions(scope, context) }
}

// ---- layout pieces ----

@Composable
fun ScreenList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun InfoCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
fun ListCard(title: String, subtitle: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) Modifier.clip(CardDefaults.shape).clickable(onClick = onClick) else Modifier
    Card(Modifier.fillMaxWidth().then(clickable)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun EmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- form fields ----

/** A text field showing the server's error for it, or else an optional hint. */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val support = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (support != null) { { Text(support) } } else null,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A read-only field that opens a dropdown of [options]. */
@Composable
fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(text) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            isError = error != null,
            supportingText = if (error != null) { { Text(error) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Default.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${value.format(DAY)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        // The date picker works in UTC midnight milliseconds.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun TimeField(value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("Time: ${value.format(TIME)}")
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

// ---- formatting ----

private val DAY = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy, HH:mm")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

fun formatDay(iso: String?): String =
    iso?.let { runCatching { LocalDate.parse(it.take(10)).format(DAY) }.getOrDefault(it) } ?: "-"

fun formatDayTime(iso: String?): String =
    iso?.let { runCatching { LocalDateTime.parse(it).format(DAY_TIME) }.getOrDefault(it) } ?: "-"

fun LocalDate.pretty(): String = format(DAY)

fun percentText(percent: Int?): String = percent?.let { "$it%" } ?: "-"

/** 42.0 -> "42", 42.5 -> "42.5" */
fun marksText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
