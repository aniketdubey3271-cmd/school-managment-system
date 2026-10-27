@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.sms.mobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sms.mobile.data.AssignmentRequest
import com.sms.mobile.data.AttendanceUpdate
import com.sms.mobile.data.GradeUpdate
import com.sms.mobile.data.Teaching
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val CLASSES = "t/classes"
private const val ASSIGNMENTS = "t/assignments"
private const val NOTICES = "t/notices"
private const val NEW_ASSIGNMENT = "t/new-assignment"

val TeacherTabs = listOf(
    NavTab(CLASSES, "Classes", Icons.Default.Home),
    NavTab(ASSIGNMENTS, "Assignments", Icons.AutoMirrored.Filled.List),
    NavTab(NOTICES, "Notices", Icons.Default.Notifications),
)

fun NavGraphBuilder.teacherGraph() {
    composable(CLASSES) { TeacherClassesScreen() }
    composable(ASSIGNMENTS) { TeacherAssignmentsScreen() }
    composable(NOTICES) {
        val api = LocalShell.current.api
        NoticesScreen(
            tab = NOTICES,
            fetch = { api.teacherAnnouncements() },
            canDelete = { it.mine },
            delete = { api.deleteTeacherAnnouncement(it) },
            targets = { api.teacherClasses().map { it.schoolClass } },
            post = { api.postTeacherAnnouncement(it) },
        )
    }
    screen("t/class/{classId}/students", "classId") { ClassStudentsScreen(it.long("classId")) }
    screen("t/class/{classId}/attendance", "classId") { AttendanceScreen(it.long("classId")) }
    screen("t/class/{classId}/subject/{subjectId}/grades", "classId", "subjectId") {
        GradesScreen(it.long("classId"), it.long("subjectId"))
    }
    screen("t/class/{classId}/report", "classId") {
        val classId = it.long("classId")
        val api = LocalShell.current.api
        ClassReportScreen { from, to -> api.teacherClassReport(classId, from.toString(), to.toString()) }
    }
    composable(NEW_ASSIGNMENT) { AssignmentFormScreen(null) }
    screen("t/assignment/{id}", "id") { TeacherAssignmentScreen(it.long("id")) }
    screen("t/assignment/{id}/edit", "id") { AssignmentFormScreen(it.long("id")) }
}

// ---- classes ----

@Composable
private fun TeacherClassesScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.teacherClasses() }

    AppScaffold("My classes", tab = CLASSES, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { classes ->
            ScreenList(padding) {
                if (classes.isEmpty()) {
                    item { EmptyState("You have no classes yet. An administrator assigns classes and subjects.") }
                }
                items(classes, key = { it.schoolClass.id }) { c ->
                    val id = c.schoolClass.id
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.schoolClass.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                if (c.classTeacher) {
                                    Text("Class teacher", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                if (c.subjects.isEmpty()) "No subjects" else c.subjects.joinToString { it.name },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                OutlinedButton(onClick = { shell.open("t/class/$id/students") }) { Text("Students") }
                                if (c.classTeacher) {
                                    OutlinedButton(onClick = { shell.open("t/class/$id/attendance") }) { Text("Attendance") }
                                }
                                c.subjects.forEach { s ->
                                    OutlinedButton(onClick = { shell.open("t/class/$id/subject/${s.id}/grades") }) {
                                        Text("${s.name} marks")
                                    }
                                }
                                OutlinedButton(onClick = { shell.open("t/class/$id/report") }) { Text("Report") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassStudentsScreen(classId: Long) {
    val shell = LocalShell.current
    val data = rememberLoad(classId) { shell.api.classStudents(classId) }

    AppScaffold("Students") { padding ->
        LoadContent(data, Modifier.padding(padding)) { students ->
            ScreenList(padding) {
                if (students.isEmpty()) item { EmptyState("No students in this class yet.") }
                items(students, key = { it.id }) { s -> ListCard(title = s.name, subtitle = "Roll ${s.rollNo}") }
            }
        }
    }
}

// ---- attendance (class teacher) ----

@Composable
private fun AttendanceScreen(classId: Long) {
    val shell = LocalShell.current
    val actions = rememberActions()
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val sheet = rememberLoad(classId, date) { shell.api.attendanceSheet(classId, date.toString()) }
    val statuses = remember(classId, date) { mutableStateMapOf<Long, String>() }
    val loaded = sheet.valueOrNull
    LaunchedEffect(loaded) {
        if (loaded != null) {
            statuses.clear()
            loaded.students.forEach { statuses[it.student.id] = it.status }
        }
    }

    AppScaffold(loaded?.let { "Attendance · ${it.schoolClass.name}" } ?: "Attendance") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateField(
                "Date",
                date,
                { picked ->
                    if (picked.isAfter(LocalDate.now())) {
                        actions.toast("Attendance can't be marked for a future date.")
                    } else {
                        date = picked
                    }
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LoadContent(sheet) { s ->
                Column {
                    Text(
                        if (s.alreadyMarked) "Already marked for ${date.pretty()}. Saving updates it."
                        else "Not marked yet for ${date.pretty()}. Everyone starts as present.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { s.students.forEach { statuses[it.student.id] = "PRESENT" } }) {
                            Text("Mark all present")
                        }
                        Text("${statuses.values.count { it == "ABSENT" }} absent", style = MaterialTheme.typography.bodyMedium)
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (s.students.isEmpty()) item { EmptyState("No students in this class yet.") }
                        items(s.students, key = { it.student.id }) { e ->
                            val present = statuses[e.student.id] != "ABSENT"
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.student.name, style = MaterialTheme.typography.titleSmall)
                                        Text("Roll ${e.student.rollNo}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    FilterChip(
                                        selected = present,
                                        onClick = { statuses[e.student.id] = "PRESENT" },
                                        label = { Text("Present") },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilterChip(
                                        selected = !present,
                                        onClick = { statuses[e.student.id] = "ABSENT" },
                                        label = { Text("Absent") },
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            actions.run {
                                shell.api.saveAttendance(classId, AttendanceUpdate(date.toString(), statuses.toMap()))
                                actions.toast("Attendance saved for ${date.pretty()}.")
                                sheet.reload()
                            }
                        },
                        enabled = !actions.busy && s.students.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Save attendance") }
                }
            }
        }
    }
}

// ---- grades (subject teacher) ----

@Composable
private fun GradesScreen(classId: Long, subjectId: Long) {
    val shell = LocalShell.current
    val actions = rememberActions()
    var exam by rememberSaveable { mutableStateOf("UNIT_TEST_1") }
    val sheet = rememberLoad(classId, subjectId, exam) { shell.api.gradeSheet(classId, subjectId, exam) }
    val marks = remember(exam) { mutableStateMapOf<Long, String>() }
    var maxMarks by remember(exam) { mutableStateOf("") }
    val loaded = sheet.valueOrNull
    LaunchedEffect(loaded) {
        if (loaded != null && loaded.exam.code == exam) {
            marks.clear()
            loaded.students.forEach { e -> marks[e.student.id] = e.marks?.let(::marksText) ?: "" }
            maxMarks = marksText(loaded.maxMarks)
        }
    }

    AppScaffold(loaded?.let { "${it.subject.name} · ${it.schoolClass.name}" } ?: "Marks") { padding ->
        LoadContent(sheet, Modifier.padding(padding)) { s ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.examTypes.forEach { type ->
                        FilterChip(selected = type.code == exam, onClick = { exam = type.code }, label = { Text(type.label) })
                    }
                }
                OutlinedTextField(
                    value = maxMarks,
                    onValueChange = { maxMarks = it },
                    label = { Text("Max marks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Text(
                    "Leave a box empty to keep that student's current mark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (s.students.isEmpty()) item { EmptyState("No students in this class yet.") }
                    items(s.students, key = { it.student.id }) { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.student.name, style = MaterialTheme.typography.titleSmall)
                                Text("Roll ${e.student.rollNo}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = marks[e.student.id] ?: "",
                                onValueChange = { marks[e.student.id] = it },
                                singleLine = true,
                                placeholder = { Text("-") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(110.dp),
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val max = maxMarks.trim().toDoubleOrNull()
                        val entered = marks.filterValues { it.isNotBlank() }
                        val parsed = entered.mapValues { it.value.trim().toDoubleOrNull() }
                        when {
                            max == null -> actions.toast("Enter the max marks as a number.")
                            parsed.values.any { it == null } -> actions.toast("Marks must be numbers.")
                            else -> actions.run {
                                val values = parsed.mapValues { it.value ?: 0.0 }
                                shell.api.saveGrades(classId, subjectId, GradeUpdate(exam, max, values))
                                actions.toast("${s.exam.label} marks saved.")
                                sheet.reload()
                            }
                        }
                    },
                    enabled = !actions.busy && s.students.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Save marks") }
            }
        }
    }
}

// ---- assignments ----

@Composable
private fun TeacherAssignmentsScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.teacherAssignments() }

    AppScaffold(
        "Assignments",
        tab = ASSIGNMENTS,
        onRefresh = { data.reload() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { shell.open(NEW_ASSIGNMENT) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
            )
        },
    ) { padding ->
        LoadContent(data, Modifier.padding(padding)) { rows ->
            ScreenList(padding) {
                if (rows.isEmpty()) item { EmptyState("You haven't set any assignments yet.") }
                items(rows, key = { it.assignment.id }) { r ->
                    val a = r.assignment
                    ListCard(
                        title = a.title,
                        subtitle = "${a.schoolClass.name} · ${a.subject?.name ?: "-"} · due ${formatDayTime(a.dueDate)}",
                        trailing = "${r.submitted}/${r.classSize}",
                        onClick = { shell.open("t/assignment/${a.id}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherAssignmentScreen(id: Long) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val data = rememberLoad(id) { shell.api.teacherAssignment(id) }
    val actions = rememberActions()
    var confirmDelete by remember { mutableStateOf(false) }

    AppScaffold(
        "Assignment",
        onRefresh = { data.reload() },
        actions = {
            IconButton(onClick = { shell.open("t/assignment/$id/edit") }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        },
    ) { padding ->
        LoadContent(data, Modifier.padding(padding)) { d ->
            val a = d.assignment
            val submitted = d.submissions.count { it.submission != null }
            ScreenList(padding) {
                item {
                    InfoCard(a.title) {
                        InfoRow("Class", a.schoolClass.name)
                        InfoRow("Subject", a.subject?.name)
                        InfoRow("Due", formatDayTime(a.dueDate) + if (a.pastDue) " (closed)" else "")
                        if (!a.description.isNullOrBlank()) Text(a.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { SectionTitle("Submissions ($submitted of ${d.submissions.size})") }
                if (d.submissions.isEmpty()) item { EmptyState("No students in this class yet.") }
                items(d.submissions, key = { it.student.id }) { row ->
                    val submission = row.submission
                    ListCard(
                        title = row.student.name,
                        subtitle = if (submission == null) "Not submitted"
                        else "${submission.originalFilename} · ${formatDayTime(submission.submittedAt)}",
                        trailing = if (submission == null) null else "Open",
                        onClick = if (submission == null) null else {
                            {
                                actions.run {
                                    openDownload(context, shell.api.submissionFile(id, submission.id), submission.originalFilename)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete assignment?",
            text = "This can't be undone. Assignments that already have submissions can't be deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                actions.run(onError = { e ->
                    actions.toast(if (e.status == 409) "Students have already submitted work, so it can't be deleted." else e.message)
                }) {
                    shell.api.deleteAssignment(id)
                    actions.toast("Assignment deleted.")
                    shell.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Creates an assignment (id null) or edits one; class and subject can't change after creation. */
@Composable
private fun AssignmentFormScreen(id: Long?) {
    val shell = LocalShell.current
    val actions = rememberActions()
    val teaching = rememberLoad { shell.api.teaching() }
    val existing = if (id != null) rememberLoad(id) { shell.api.teacherAssignment(id) } else null
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var teachingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7)) }
    var dueTime by rememberSaveable { mutableStateOf(LocalTime.of(17, 0)) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val loaded = existing?.valueOrNull
    LaunchedEffect(loaded) {
        if (loaded != null && !prefilled) {
            val a = loaded.assignment
            val due = LocalDateTime.parse(a.dueDate)
            title = a.title
            description = a.description ?: ""
            dueDate = due.toLocalDate()
            dueTime = due.toLocalTime().withSecond(0).withNano(0)
            prefilled = true
        }
    }

    AppScaffold(if (id == null) "New assignment" else "Edit assignment") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (id == null) {
                when (val t = teaching.current) {
                    is Load.Ready -> if (t.value.isEmpty()) {
                        Text(
                            "You don't teach any subject yet, so you can't set assignments.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Picker(
                            label = "Class and subject",
                            options = t.value,
                            selected = t.value.find { it.id == teachingId },
                            text = { p: Teaching -> "${p.schoolClass.name} · ${p.subject.name}" },
                            onSelect = { teachingId = it.id },
                            error = errors["teachingId"],
                        )
                    }
                    is Load.Failed -> Text(t.message, color = MaterialTheme.colorScheme.error)
                    is Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } else {
                loaded?.let { d ->
                    Text(
                        "${d.assignment.schoolClass.name} · ${d.assignment.subject?.name ?: "-"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            FormField("Title", title, { title = it }, errors["title"])
            FormField("Description (optional)", description, { description = it }, errors["description"], singleLine = false, minLines = 4)
            Text("Due", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", dueDate, { dueDate = it }, Modifier.weight(1f))
                TimeField(dueTime, { dueTime = it }, Modifier.weight(1f))
            }
            errors["dueDate"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    errors = emptyMap()
                    actions.run(onError = { e ->
                        errors = e.fieldErrors
                        if (e.fieldErrors.isEmpty()) actions.toast(e.message)
                    }) {
                        val body = AssignmentRequest(
                            teachingId = teachingId,
                            title = title.trim(),
                            description = description.trim().ifBlank { null },
                            dueDate = LocalDateTime.of(dueDate, dueTime).toString(),
                        )
                        if (id == null) {
                            val created = shell.api.createAssignment(body)
                            actions.toast("Assignment created.")
                            shell.nav.navigate("t/assignment/${created.id}") {
                                popUpTo(NEW_ASSIGNMENT) { inclusive = true }
                            }
                        } else {
                            shell.api.updateAssignment(id, body)
                            actions.toast("Assignment updated.")
                            shell.back()
                        }
                    }
                },
                enabled = !actions.busy && title.isNotBlank() && (id != null || teachingId != null),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (id == null) "Create assignment" else "Save changes") }
        }
    }
}
