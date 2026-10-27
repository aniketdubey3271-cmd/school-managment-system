package com.sms.mobile.data

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** The backend's /api/v1 endpoints. Paths are relative to the configured base URL. */
interface SmsApi {

    // ---- auth ----

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    /** Blocking on purpose: called from OkHttp's authenticator thread. */
    @POST("api/v1/auth/refresh")
    fun refreshCall(@Body body: RefreshRequest): Call<TokenResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @POST("api/v1/auth/signup")
    suspend fun signup(@Body body: SignupRequest): UserInfo

    @GET("api/v1/auth/signup-options")
    suspend fun signupOptions(): SignupOptions

    @GET("api/v1/me")
    suspend fun me(): Me

    @POST("api/v1/me/password")
    suspend fun changePassword(@Body body: PasswordChange): TokenResponse

    // ---- student ----

    @GET("api/v1/student/dashboard")
    suspend fun studentDashboard(): StudentDashboard

    @GET("api/v1/student/attendance")
    suspend fun studentAttendance(): StudentAttendance

    @GET("api/v1/student/grades")
    suspend fun studentGrades(): List<ExamGrades>

    @GET("api/v1/student/announcements")
    suspend fun studentAnnouncements(): List<Announcement>

    @GET("api/v1/student/assignments")
    suspend fun studentAssignments(): List<StudentAssignment>

    @GET("api/v1/student/assignments/{id}")
    suspend fun studentAssignment(@Path("id") id: Long): StudentAssignmentDetail

    @Multipart
    @POST("api/v1/student/assignments/{id}/submission")
    suspend fun submit(@Path("id") id: Long, @Part file: MultipartBody.Part): StudentAssignmentDetail

    @Streaming
    @GET("api/v1/student/assignments/{id}/submission/file")
    suspend fun studentSubmissionFile(@Path("id") id: Long): ResponseBody

    @Streaming
    @GET("api/v1/student/report-card")
    suspend fun studentReportCard(): ResponseBody

    @Streaming
    @GET("api/v1/student/id-card")
    suspend fun studentIdCard(): ResponseBody

    // ---- teacher ----

    @GET("api/v1/teacher/classes")
    suspend fun teacherClasses(): List<MyClass>

    @GET("api/v1/teacher/teaching")
    suspend fun teaching(): List<Teaching>

    @GET("api/v1/teacher/classes/{classId}/students")
    suspend fun classStudents(@Path("classId") classId: Long): List<ClassStudent>

    @GET("api/v1/teacher/classes/{classId}/attendance")
    suspend fun attendanceSheet(@Path("classId") classId: Long, @Query("date") date: String?): AttendanceSheet

    @PUT("api/v1/teacher/classes/{classId}/attendance")
    suspend fun saveAttendance(@Path("classId") classId: Long, @Body body: AttendanceUpdate): AttendanceSheet

    @GET("api/v1/teacher/classes/{classId}/subjects/{subjectId}/grades")
    suspend fun gradeSheet(
        @Path("classId") classId: Long,
        @Path("subjectId") subjectId: Long,
        @Query("exam") exam: String?,
    ): GradeSheet

    @PUT("api/v1/teacher/classes/{classId}/subjects/{subjectId}/grades")
    suspend fun saveGrades(
        @Path("classId") classId: Long,
        @Path("subjectId") subjectId: Long,
        @Body body: GradeUpdate,
    ): GradeSheet

    @GET("api/v1/teacher/classes/{classId}/report")
    suspend fun teacherClassReport(
        @Path("classId") classId: Long,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): ClassReport

    @GET("api/v1/teacher/assignments")
    suspend fun teacherAssignments(): List<TeacherAssignmentRow>

    @POST("api/v1/teacher/assignments")
    suspend fun createAssignment(@Body body: AssignmentRequest): Assignment

    @GET("api/v1/teacher/assignments/{id}")
    suspend fun teacherAssignment(@Path("id") id: Long): TeacherAssignmentDetail

    @PUT("api/v1/teacher/assignments/{id}")
    suspend fun updateAssignment(@Path("id") id: Long, @Body body: AssignmentRequest): Assignment

    @DELETE("api/v1/teacher/assignments/{id}")
    suspend fun deleteAssignment(@Path("id") id: Long)

    @Streaming
    @GET("api/v1/teacher/assignments/{id}/submissions/{submissionId}/file")
    suspend fun submissionFile(@Path("id") id: Long, @Path("submissionId") submissionId: Long): ResponseBody

    @GET("api/v1/teacher/announcements")
    suspend fun teacherAnnouncements(): List<Announcement>

    @POST("api/v1/teacher/announcements")
    suspend fun postTeacherAnnouncement(@Body body: NewAnnouncement): Announcement

    @DELETE("api/v1/teacher/announcements/{id}")
    suspend fun deleteTeacherAnnouncement(@Path("id") id: Long)

    // ---- admin ----

    @GET("api/v1/admin/overview")
    suspend fun adminOverview(): AdminOverview

    @GET("api/v1/admin/students")
    suspend fun adminStudents(@Query("classId") classId: Long?): List<StudentInfo>

    @GET("api/v1/admin/students/{id}")
    suspend fun adminStudent(@Path("id") id: Long): StudentInfo

    @GET("api/v1/admin/teachers")
    suspend fun adminTeachers(): List<TeacherInfo>

    @GET("api/v1/admin/classes")
    suspend fun adminClasses(): List<ClassSummary>

    @GET("api/v1/admin/classes/{id}")
    suspend fun adminClass(@Path("id") id: Long): ClassDetail

    @GET("api/v1/admin/announcements")
    suspend fun adminAnnouncements(): List<Announcement>

    @POST("api/v1/admin/announcements")
    suspend fun postAdminAnnouncement(@Body body: NewAnnouncement): Announcement

    @DELETE("api/v1/admin/announcements/{id}")
    suspend fun deleteAdminAnnouncement(@Path("id") id: Long)

    @GET("api/v1/admin/reports/attendance")
    suspend fun schoolAttendance(@Query("from") from: String?, @Query("to") to: String?): SchoolAttendanceReport

    @GET("api/v1/admin/reports/classes/{id}")
    suspend fun adminClassReport(
        @Path("id") classId: Long,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): ClassReport

    @Streaming
    @GET("api/v1/admin/students/{id}/report-card")
    suspend fun adminReportCard(@Path("id") id: Long): ResponseBody

    @Streaming
    @GET("api/v1/admin/students/{id}/id-card")
    suspend fun adminIdCard(@Path("id") id: Long): ResponseBody
}
