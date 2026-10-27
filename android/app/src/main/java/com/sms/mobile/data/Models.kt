package com.sms.mobile.data

// JSON shapes of the SMS backend's /api/v1 endpoints. Dates arrive as ISO strings
// ("2026-10-27", "2026-10-27T17:00:00") and are formatted for display in the UI.

// ---- errors and auth ----

data class ApiError(
    val status: Int?,
    val error: String?,
    val message: String?,
    val fieldErrors: Map<String, String>?,
)

data class LoginRequest(val login: String, val password: String)

data class RefreshRequest(val refreshToken: String)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String?,
    val expiresIn: Long,
    val user: UserInfo,
)

/** role is ADMIN, TEACHER or STUDENT. */
data class UserInfo(val id: Long, val username: String, val email: String, val role: String)

data class SignupRequest(
    val role: String,
    val username: String,
    val email: String,
    val password: String,
    val name: String?,
    val rollNo: String?,
    val classId: Long?,
    val subjectSpecialization: String?,
    val contact: String?,
    val teacherCode: String?,
    val adminCode: String?,
)

data class SignupOptions(val classes: List<Ref>, val teacherSignupEnabled: Boolean, val adminSignupEnabled: Boolean)

data class PasswordChange(val currentPassword: String, val newPassword: String, val confirmPassword: String)

data class Me(val user: UserInfo, val student: StudentInfo?, val teacher: TeacherInfo?)

// ---- shared ----

/** A class, subject or teacher reference: id plus display name (e.g. "10-A"). */
data class Ref(val id: Long, val name: String)

data class ExamType(val code: String, val label: String)

data class StudentInfo(
    val id: Long,
    val studentId: String?,
    val name: String,
    val rollNo: String,
    val schoolClass: Ref?,
    val dob: String?,
    val address: String?,
    val parentContact: String?,
    val username: String,
    val email: String,
    val enabled: Boolean,
)

data class TeacherInfo(
    val id: Long,
    val name: String,
    val subjectSpecialization: String?,
    val contact: String?,
    val username: String,
    val email: String,
    val enabled: Boolean,
)

data class ClassStudent(val id: Long, val name: String, val rollNo: String)

data class Announcement(
    val id: Long,
    val title: String,
    val content: String,
    val targetClass: Ref?,
    val author: String,
    val createdAt: String,
    val mine: Boolean,
)

/** targetClassId null = whole school (admins only). */
data class NewAnnouncement(val title: String, val content: String, val targetClassId: Long?)

/** percent is null when nothing was recorded. */
data class Tally(val present: Long, val absent: Long, val percent: Int?)

// ---- student ----

data class AttendanceRecord(val date: String, val status: String)

data class StudentAttendance(val summary: Tally, val records: List<AttendanceRecord>)

data class Grade(val subject: Ref, val marks: Double, val maxMarks: Double, val percent: Int)

data class ExamGrades(val exam: ExamType, val grades: List<Grade>)

data class Assignment(
    val id: Long,
    val title: String,
    val description: String?,
    val schoolClass: Ref,
    val subject: Ref?,
    val teacher: Ref?,
    val dueDate: String,
    val pastDue: Boolean,
)

data class Submission(val id: Long, val originalFilename: String, val submittedAt: String)

data class StudentDashboard(
    val student: StudentInfo,
    val attendance: Tally,
    val pendingAssignments: Long,
    val latestAnnouncements: List<Announcement>,
)

data class StudentAssignment(val assignment: Assignment, val submission: Submission?)

data class StudentAssignmentDetail(
    val assignment: Assignment,
    val submission: Submission?,
    val allowedFileTypes: List<String>,
    val maxFileBytes: Long,
)

// ---- teacher ----

data class MyClass(val schoolClass: Ref, val classTeacher: Boolean, val subjects: List<Ref>)

/** One class+subject pair; a new assignment is created for one of these by id. */
data class Teaching(val id: Long, val schoolClass: Ref, val subject: Ref, val teacher: Ref?)

data class AttendanceEntry(val student: ClassStudent, val status: String)

data class AttendanceSheet(
    val schoolClass: Ref,
    val date: String,
    val alreadyMarked: Boolean,
    val students: List<AttendanceEntry>,
)

/** statuses: studentId -> PRESENT or ABSENT. */
data class AttendanceUpdate(val date: String, val statuses: Map<Long, String>)

data class GradeEntry(val student: ClassStudent, val marks: Double?)

data class GradeSheet(
    val schoolClass: Ref,
    val subject: Ref,
    val exam: ExamType,
    val maxMarks: Double,
    val students: List<GradeEntry>,
    val examTypes: List<ExamType>,
)

/** marks: studentId -> marks; students left out keep their current marks. */
data class GradeUpdate(val exam: String, val maxMarks: Double, val marks: Map<Long, Double>)

data class TeacherAssignmentRow(val assignment: Assignment, val submitted: Long, val classSize: Long)

data class SubmissionRow(val student: ClassStudent, val submission: Submission?)

data class TeacherAssignmentDetail(val assignment: Assignment, val submissions: List<SubmissionRow>)

/** teachingId is required when creating and ignored when editing. dueDate is ISO local date-time. */
data class AssignmentRequest(val teachingId: Long?, val title: String, val description: String?, val dueDate: String)

// ---- reports ----

data class StudentAttendanceRow(val student: ClassStudent, val tally: Tally)

data class ExamStat(val exam: ExamType, val averagePercent: Int, val graded: Int)

data class SubjectPerformance(val subject: Ref, val exams: List<ExamStat>)

data class ClassReport(
    val schoolClass: Ref,
    val from: String,
    val to: String,
    val attendance: Tally,
    val students: List<StudentAttendanceRow>,
    val exams: List<ExamType>,
    val subjects: List<SubjectPerformance>,
)

data class ClassAttendanceRow(val schoolClass: Ref, val tally: Tally)

data class SchoolAttendanceReport(val from: String, val to: String, val classes: List<ClassAttendanceRow>)

// ---- admin ----

data class AdminOverview(val students: Long, val teachers: Long, val classes: Long, val subjects: Long)

data class ClassSummary(val schoolClass: Ref, val classTeacher: Ref?, val studentCount: Long)

data class ClassDetail(
    val schoolClass: Ref,
    val classTeacher: Ref?,
    val students: List<ClassStudent>,
    val subjectTeachers: List<Teaching>,
)
