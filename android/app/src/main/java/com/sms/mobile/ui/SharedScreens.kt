package com.sms.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sms.mobile.data.Announcement
import com.sms.mobile.data.ApiException
import com.sms.mobile.data.ClassReport
import com.sms.mobile.data.NewAnnouncement
import com.sms.mobile.data.Ref
import java.time.LocalDate

// ---- announcements ----

@Composable
fun AnnouncementCard(a: Announcement, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
            Text(
                "${a.author} · ${a.targetClass?.name ?: "Whole school"} · ${formatDayTime(a.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(a.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The notices tab for any role. [targets] null = read-only (students); otherwise the classes
 * the user may post to, plus "Whole school" when [allowSchoolWide].
 */
@Composable
fun NoticesScreen(
    tab: String,
    fetch: suspend () -> List<Announcement>,
    canDelete: (Announcement) -> Boolean = { false },
    delete: suspend (Long) -> Unit = {},
    targets: (suspend () -> List<Ref>)? = null,
    allowSchoolWide: Boolean = false,
    post: suspend (NewAnnouncement) -> Unit = {},
) {
    val data = rememberLoad { fetch() }
    val actions = rememberActions()
    var composing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Announcement?>(null) }

    AppScaffold(
        "Notices",
        tab = tab,
        onRefresh = { data.reload() },
        floatingActionButton = {
            if (targets != null) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Post") },
                )
            }
        },
    ) { padding ->
        LoadContent(data, Modifier.padding(padding)) { list ->
            ScreenList(padding) {
                if (list.isEmpty()) item { EmptyState("No notices yet.") }
                items(list, key = { it.id }) { a ->
                    val onDelete: (() -> Unit)? = if (canDelete(a)) { { deleting = a } } else null
                    AnnouncementCard(a, onDelete)
                }
            }
        }
    }

    if (composing && targets != null) {
        PostNoticeDialog(
            loadTargets = targets,
            allowSchoolWide = allowSchoolWide,
            busy = actions.busy,
            onDismiss = { composing = false },
            onPost = { notice, showErrors ->
                actions.run(onError = showErrors) {
                    post(notice)
                    composing = false
                    actions.toast("Notice posted.")
                    data.reload()
                }
            },
        )
    }

    deleting?.let { a ->
        ConfirmDialog(
            title = "Delete notice?",
            text = "\"${a.title}\" will be removed for everyone.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = null
                actions.run {
                    delete(a.id)
                    actions.toast("Notice deleted.")
                    data.reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

private val WHOLE_SCHOOL = Ref(-1, "Whole school")

@Composable
private fun PostNoticeDialog(
    loadTargets: suspend () -> List<Ref>,
    allowSchoolWide: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPost: (NewAnnouncement, (ApiException) -> Unit) -> Unit,
) {
    val targets = rememberLoad { loadTargets() }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf(if (allowSchoolWide) WHOLE_SCHOOL else null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post a notice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Title", title, { title = it }, errors["title"])
                FormField("Message", content, { content = it }, errors["content"], singleLine = false, minLines = 4)
                when (val t = targets.current) {
                    is Load.Ready -> Picker(
                        label = "Send to",
                        options = (if (allowSchoolWide) listOf(WHOLE_SCHOOL) else emptyList()) + t.value,
                        selected = target,
                        text = Ref::name,
                        onSelect = { target = it },
                        error = errors["targetClassId"],
                    )
                    is Load.Failed -> Text(t.message, color = MaterialTheme.colorScheme.error)
                    is Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && title.isNotBlank() && content.isNotBlank(),
                onClick = {
                    errors = emptyMap()
                    message = null
                    val classId = target?.id?.takeIf { it != WHOLE_SCHOOL.id }
                    onPost(NewAnnouncement(title.trim(), content.trim(), classId)) { e ->
                        errors = e.fieldErrors
                        message = e.message
                    }
                },
            ) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- class report (teacher and admin) ----

/** Attendance and average marks for one class over a date range (default: the last 30 days). */
@Composable
fun ClassReportScreen(fetch: suspend (LocalDate, LocalDate) -> ClassReport) {
    var to by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29)) }
    val data = rememberLoad(from, to) { fetch(from, to) }

    AppScaffold(data.valueOrNull?.let { "Report · ${it.schoolClass.name}" } ?: "Class report") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField("From", from, { from = it }, Modifier.weight(1f))
                DateField("To", to, { to = it }, Modifier.weight(1f))
            }
            LoadContent(data) { r ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Attendance", percentText(r.attendance.percent), Modifier.weight(1f))
                            StatCard("Present / absent", "${r.attendance.present} / ${r.attendance.absent}", Modifier.weight(1f))
                        }
                    }
                    item { SectionTitle("Average marks by subject") }
                    if (r.subjects.isEmpty()) item { EmptyState("No marks recorded yet.") }
                    // Keys must be unique across the whole list, so each kind of row gets a prefix.
                    items(r.subjects, key = { "subject-${it.subject.id}" }) { p ->
                        InfoCard(p.subject.name) {
                            p.exams.forEach { e -> InfoRow(e.exam.label, "${e.averagePercent}%  (${e.graded} graded)") }
                        }
                    }
                    item { SectionTitle("Attendance by student") }
                    if (r.students.isEmpty()) item { EmptyState("No students in this class.") }
                    items(r.students, key = { "student-${it.student.id}" }) { s ->
                        ListCard(
                            title = s.student.name,
                            subtitle = "Roll ${s.student.rollNo} · ${s.tally.present} present, ${s.tally.absent} absent",
                            trailing = percentText(s.tally.percent),
                        )
                    }
                }
            }
        }
    }
}
