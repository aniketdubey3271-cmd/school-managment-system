package com.sms.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sms.mobile.data.Ref
import java.time.LocalDate

private const val HOME = "a/home"
private const val STUDENTS = "a/students"
private const val CLASSES = "a/classes"
private const val NOTICES = "a/notices"
private const val TEACHERS = "a/teachers"
private const val REPORTS = "a/reports"

val AdminTabs = listOf(
    NavTab(HOME, "Overview", Icons.Default.Home),
    NavTab(STUDENTS, "Students", Icons.Default.Person),
    NavTab(CLASSES, "Classes", Icons.AutoMirrored.Filled.List),
    NavTab(NOTICES, "Notices", Icons.Default.Notifications),
)

fun NavGraphBuilder.adminGraph() {
    composable(HOME) { AdminHomeScreen() }
    composable(STUDENTS) { AdminStudentsScreen() }
    composable(CLASSES) { AdminClassesScreen() }
    composable(NOTICES) {
        val api = LocalShell.current.api
        NoticesScreen(
            tab = NOTICES,
            fetch = { api.adminAnnouncements() },
            canDelete = { true },
            delete = { api.deleteAdminAnnouncement(it) },
            targets = { api.adminClasses().map { it.schoolClass } },
            allowSchoolWide = true,
            post = { api.postAdminAnnouncement(it) },
        )
    }
    composable(TEACHERS) { AdminTeachersScreen() }
    composable(REPORTS) { SchoolAttendanceScreen() }
    screen("a/student/{id}", "id") { AdminStudentScreen(it.long("id")) }
    screen("a/class/{id}", "id") { AdminClassScreen(it.long("id")) }
    screen("a/class/{id}/report", "id") {
        val classId = it.long("id")
        val api = LocalShell.current.api
        ClassReportScreen { from, to -> api.adminClassReport(classId, from.toString(), to.toString()) }
    }
}

@Composable
private fun AdminHomeScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.adminOverview() }

    AppScaffold("Overview", tab = HOME, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { o ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Students", o.students.toString(), Modifier.weight(1f))
                    StatCard("Teachers", o.teachers.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Classes", o.classes.toString(), Modifier.weight(1f))
                    StatCard("Subjects", o.subjects.toString(), Modifier.weight(1f))
                }
                ListCard("Teachers", "Everyone who teaches, with their subjects", onClick = { shell.open(TEACHERS) })
                ListCard("Attendance report", "Attendance for every class over a date range", onClick = { shell.open(REPORTS) })
                Text(
                    "Adding or editing students, teachers, classes and subjects is done on the website.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ALL_CLASSES = Ref(-1, "All classes")

@Composable
private fun AdminStudentsScreen() {
    val shell = LocalShell.current
    var classId by rememberSaveable { mutableStateOf<Long?>(null) }
    val classes = rememberLoad { shell.api.adminClasses() }
    val students = rememberLoad(classId) { shell.api.adminStudents(classId) }

    AppScaffold("Students", tab = STUDENTS, onRefresh = { students.reload() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            classes.valueOrNull?.let { list ->
                val options = listOf(ALL_CLASSES) + list.map { it.schoolClass }
                Picker(
                    label = "Class",
                    options = options,
                    selected = options.find { it.id == (classId ?: ALL_CLASSES.id) },
                    text = Ref::name,
                    onSelect = { classId = it.id.takeIf { id -> id != ALL_CLASSES.id } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LoadContent(students) { list ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (list.isEmpty()) item { EmptyState("No students found.") }
                    items(list, key = { it.id }) { s ->
                        ListCard(
                            title = s.name + if (s.enabled) "" else " (disabled)",
                            subtitle = "${s.schoolClass?.name ?: "No class"} · Roll ${s.rollNo} · ${s.username}",
                            onClick = { shell.open("a/student/${s.id}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStudentScreen(id: Long) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val data = rememberLoad(id) { shell.api.adminStudent(id) }
    val actions = rememberActions()

    AppScaffold("Student") { padding ->
        LoadContent(data, Modifier.padding(padding)) { s ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(s.name) {
                    InfoRow("Student ID", s.studentId)
                    InfoRow("Class", s.schoolClass?.name)
                    InfoRow("Roll number", s.rollNo)
                    InfoRow("Date of birth", formatDay(s.dob))
                    InfoRow("Address", s.address)
                    InfoRow("Parent contact", s.parentContact)
                    InfoRow("Username", s.username)
                    InfoRow("Email", s.email)
                    InfoRow("Account", if (s.enabled) "Active" else "Disabled")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { actions.run { openDownload(context, shell.api.adminReportCard(id), "report-card-${s.username}.pdf") } },
                        enabled = !actions.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Report card") }
                    OutlinedButton(
                        onClick = { actions.run { openDownload(context, shell.api.adminIdCard(id), "id-card-${s.username}.pdf") } },
                        enabled = !actions.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("ID card") }
                }
            }
        }
    }
}

@Composable
private fun AdminTeachersScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.adminTeachers() }

    AppScaffold("Teachers", onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { teachers ->
            ScreenList(padding) {
                if (teachers.isEmpty()) item { EmptyState("No teachers yet.") }
                items(teachers, key = { it.id }) { t ->
                    ListCard(
                        title = t.name + if (t.enabled) "" else " (disabled)",
                        subtitle = listOfNotNull(t.subjectSpecialization, t.contact, t.email).joinToString(" · "),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminClassesScreen() {
    val shell = LocalShell.current
    val data = rememberLoad { shell.api.adminClasses() }

    AppScaffold("Classes", tab = CLASSES, onRefresh = { data.reload() }) { padding ->
        LoadContent(data, Modifier.padding(padding)) { classes ->
            ScreenList(padding) {
                if (classes.isEmpty()) item { EmptyState("No classes yet. Add them on the website.") }
                items(classes, key = { it.schoolClass.id }) { c ->
                    ListCard(
                        title = c.schoolClass.name,
                        subtitle = "Class teacher: ${c.classTeacher?.name ?: "none"}",
                        trailing = "${c.studentCount} students",
                        onClick = { shell.open("a/class/${c.schoolClass.id}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminClassScreen(id: Long) {
    val shell = LocalShell.current
    val data = rememberLoad(id) { shell.api.adminClass(id) }

    AppScaffold(data.valueOrNull?.schoolClass?.name ?: "Class") { padding ->
        LoadContent(data, Modifier.padding(padding)) { c ->
            ScreenList(padding) {
                item {
                    InfoCard("Class ${c.schoolClass.name}") {
                        InfoRow("Class teacher", c.classTeacher?.name ?: "None")
                        InfoRow("Students", c.students.size.toString())
                    }
                }
                item {
                    Button(onClick = { shell.open("a/class/$id/report") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Class report")
                    }
                }
                item { SectionTitle("Subjects and teachers") }
                if (c.subjectTeachers.isEmpty()) item { EmptyState("No subjects assigned yet.") }
                // Keys must be unique across the whole list, so each kind of row gets a prefix.
                items(c.subjectTeachers, key = { "teaching-${it.id}" }) { t ->
                    ListCard(title = t.subject.name, subtitle = t.teacher?.name ?: "-")
                }
                item { SectionTitle("Students") }
                if (c.students.isEmpty()) item { EmptyState("No students in this class yet.") }
                items(c.students, key = { "student-${it.id}" }) { s ->
                    ListCard(title = s.name, subtitle = "Roll ${s.rollNo}", onClick = { shell.open("a/student/${s.id}") })
                }
            }
        }
    }
}

@Composable
private fun SchoolAttendanceScreen() {
    val shell = LocalShell.current
    var to by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29)) }
    val data = rememberLoad(from, to) { shell.api.schoolAttendance(from.toString(), to.toString()) }

    AppScaffold("Attendance report") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField("From", from, { from = it }, Modifier.weight(1f))
                DateField("To", to, { to = it }, Modifier.weight(1f))
            }
            LoadContent(data) { report ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (report.classes.isEmpty()) item { EmptyState("No classes yet.") }
                    items(report.classes, key = { it.schoolClass.id }) { row ->
                        ListCard(
                            title = row.schoolClass.name,
                            subtitle = "${row.tally.present} present, ${row.tally.absent} absent",
                            trailing = percentText(row.tally.percent),
                            onClick = { shell.open("a/class/${row.schoolClass.id}/report") },
                        )
                    }
                }
            }
        }
    }
}
