package com.sms.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val HOME = "s/home"
private const val ASSIGNMENTS = "s/assignments"
private const val GRADES = "s/grades"
private const val ATTENDANCE = "s/attendance"
private const val NOTICES = "s/notices"

val StudentTabs = listOf(
    NavTab(HOME, "Home", Icons.Default.Home),
    NavTab(ASSIGNMENTS, "Work", Icons.AutoMirrored.Filled.List),
    NavTab(GRADES, "Grades", Icons.Default.Star),
    NavTab(ATTENDANCE, "Attendance", Icons.Default.DateRange),
    NavTab(NOTICES, "Notices", Icons.Default.Notifications),
)

fun NavGraphBuilder.studentGraph() {
    composable(HOME) { StudentHomeScreen() }
    composable(ASSIGNMENTS) { StudentAssignmentsScreen() }
    composable(GRADES) { StudentGradesScreen() }
    composable(ATTENDANCE) { StudentAttendanceScreen() }
    composable(NOTICES) {
        val api = LocalShell.current.api
        NoticesScreen(tab = NOTICES, fetch = { api.studentAnnouncements() })
    }
    screen("s/assignment/{id}", "id") { StudentAssignmentScreen(it.long("id")) }
}

@Composable
private fun StudentHomeScreen() {
    val shell = LocalShell.current
    val context = LocalContext.current
    val data = rememberLoad { shell.api.studentDashboard() }
    val actions = rememberActions()

    AppScaffold("Home", tab = HOME, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { d ->
            ScreenList(padding) {
                item {
                    InfoCard("Hello, ${d.student.name}") {
                        InfoRow("Class", d.student.schoolClass?.name)
                        InfoRow("Roll number", d.student.rollNo)
                        InfoRow("Student ID", d.student.studentId)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Attendance", percentText(d.attendance.percent), Modifier.weight(1f))
                        StatCard("Assignments due", d.pendingAssignments.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { actions.run { openDownload(context, shell.api.studentReportCard(), "report-card.pdf") } },
                            enabled = !actions.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Report card") }
                        OutlinedButton(
                            onClick = { actions.run { openDownload(context, shell.api.studentIdCard(), "id-card.pdf") } },
                            enabled = !actions.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("ID card") }
                    }
                }
                item { SectionTitle("Latest notices") }
                if (d.latestAnnouncements.isEmpty()) item { EmptyState("No notices yet.") }
                items(d.latestAnnouncements, key = { it.id }) { AnnouncementCard(it) }
            }
        }
    }
}

@Composable
private fun StudentAssignmentsScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.studentAssignments() }

    AppScaffold("Assignments", tab = ASSIGNMENTS, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { rows ->
            ScreenList(padding) {
                if (rows.isEmpty()) item { EmptyState("No assignments for your class yet.") }
                items(rows, key = { it.assignment.id }) { row ->
                    val a = row.assignment
                    ListCard(
                        title = a.title,
                        subtitle = "${a.subject?.name ?: "-"} · due ${formatDayTime(a.dueDate)}",
                        trailing = when {
                            row.submission != null -> "Submitted"
                            a.pastDue -> "Closed"
                            else -> "To do"
                        },
                        onClick = { shell.open("s/assignment/${a.id}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentAssignmentScreen(id: Long) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val data = rememberLoad(id) { shell.api.studentAssignment(id) }
    val actions = rememberActions()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            actions.run {
                val maxBytes = data.valueOrNull?.maxFileBytes ?: (10L * 1024 * 1024)
                val file = readPickedFile(context, uri, maxBytes)
                shell.api.submit(id, file.toPart())
                actions.toast("Submitted ${file.name}.")
                data.reload()
            }
        }
    }

    AppScaffold("Assignment") { padding ->
        LoadContent(data, Modifier.padding(padding)) { d ->
            val a = d.assignment
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(a.title) {
                    InfoRow("Subject", a.subject?.name)
                    InfoRow("Teacher", a.teacher?.name)
                    InfoRow("Due", formatDayTime(a.dueDate))
                    if (!a.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(a.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InfoCard("Your submission") {
                    val submission = d.submission
                    if (submission == null) {
                        Text(if (a.pastDue) "Not submitted. Submissions are closed." else "Not submitted yet.")
                    } else {
                        InfoRow("File", submission.originalFilename)
                        InfoRow("Submitted", formatDayTime(submission.submittedAt))
                        TextButton(
                            onClick = {
                                actions.run {
                                    openDownload(context, shell.api.studentSubmissionFile(id), submission.originalFilename)
                                }
                            },
                            enabled = !actions.busy,
                        ) { Text("Open my file") }
                    }
                }
                if (!a.pastDue) {
                    Button(
                        onClick = {
                            val types = d.allowedFileTypes.mapNotNull { MIME_BY_EXTENSION[it] }.distinct()
                            picker.launch(types.ifEmpty { listOf("*/*") }.toTypedArray())
                        },
                        enabled = !actions.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (d.submission == null) "Choose a file and submit" else "Replace my file") }
                    Text(
                        "Allowed: ${d.allowedFileTypes.joinToString(", ")} · up to ${d.maxFileBytes / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actions.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StudentGradesScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.studentGrades() }

    AppScaffold("Grades", tab = GRADES, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { exams ->
            ScreenList(padding) {
                if (exams.isEmpty()) item { EmptyState("No marks recorded yet.") }
                items(exams, key = { it.exam.code }) { exam ->
                    InfoCard(exam.exam.label) {
                        exam.grades.forEach { g ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(g.subject.name, modifier = Modifier.weight(1f))
                                Text("${marksText(g.marks)} / ${marksText(g.maxMarks)}")
                                Spacer(Modifier.width(16.dp))
                                Text("${g.percent}%", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentAttendanceScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.studentAttendance() }

    AppScaffold("Attendance", tab = ATTENDANCE, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { att ->
            ScreenList(padding) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Attendance", percentText(att.summary.percent), Modifier.weight(1f))
                        StatCard("Present", att.summary.present.toString(), Modifier.weight(1f))
                        StatCard("Absent", att.summary.absent.toString(), Modifier.weight(1f))
                    }
                }
                if (att.records.isEmpty()) item { EmptyState("No attendance recorded yet.") }
                items(att.records, key = { it.date }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDay(r.date), modifier = Modifier.weight(1f))
                            val present = r.status == "PRESENT"
                            Text(
                                if (present) "Present" else "Absent",
                                fontWeight = FontWeight.Bold,
                                color = if (present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
