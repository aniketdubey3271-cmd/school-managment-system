# Student Management System

A role-based school management web application built with Spring Boot, Spring Security, Thymeleaf, JPA, and SQLite. The application serves administrator, teacher, and student portals from one Spring Boot process.

## Requirements

- Java 21 JDK
- A terminal with access to the project directory
- Network access on the first Maven Wrapper run if Maven must be downloaded

Set `JAVA_HOME` to the installed JDK 21 directory and ensure its `bin` directory is on `PATH`. The Maven Wrapper (`mvnw` / `mvnw.cmd`) is included, so a separate Maven installation is not required.

## Setup

1. **Configure Java.** In PowerShell, point `JAVA_HOME` to your JDK 21 installation (replace the example path with the actual installation directory):

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   ```

   On macOS or Linux, set `JAVA_HOME` to the JDK 21 installation directory and add `$JAVA_HOME/bin` to `PATH`.

2. **Open the project root.** This is the directory containing `pom.xml` and `mvnw.cmd`.

3. **Configure the first administrator password before the first launch.** The application does not use a default password. In PowerShell:

   ```powershell
   $env:SMS_ADMIN_PASSWORD = "choose-a-strong-password"
   ```

   The default administrator username is `admin`; it can be changed with `SMS_ADMIN_USERNAME`. The account is created only when the database has no administrator. Changing these variables after an administrator exists does not reset that account's password.

4. **Start the application.** On Windows PowerShell:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   On macOS or Linux:

   ```bash
   export SMS_ADMIN_PASSWORD='choose-a-strong-password'
   ./mvnw spring-boot:run
   ```

5. **Sign in.** Open [http://localhost:8080/login](http://localhost:8080/login) and sign in with the configured administrator username and password.

6. **Create school users and records.** Use the administrator portal to set up teachers, students, classes, and subjects. Users are redirected to a dashboard according to their assigned role.

On first startup, Flyway creates and updates the SQLite schema. By default, the database file is `sms.db` in the working directory, and uploaded assignment files are stored under `uploads/`.

Demo records are enabled by default for local development. The application fills the database up to 21 students and 3 teachers without replacing existing records, creates the `BCA-SEM-5` class if needed, and assigns sample subjects to teachers. Set `SMS_DEMO_DATA_ENABLED=false` to disable demo seeding. Generated demo accounts use `SMS_DEMO_PASSWORD` (default: `DemoPass123!`); change it before sharing the app, and disable demo data outside local development.

Generated demo usernames use the `demo-teacher-NN` and `demo-student-NN` patterns. In the existing local database, the additional accounts are `demo-teacher-01` and `demo-teacher-02`, plus `demo-student-01` through `demo-student-20`.

## Configuration

Configuration can be supplied through environment variables; defaults are shown below.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:sqlite:./sms.db?foreign_keys=on` | JDBC URL for the SQLite database |
| `SMS_UPLOADS_DIR` | `./uploads` | Directory for uploaded assignment files |
| `SMS_SCHOOL_NAME` | `Student Management System` | School name printed on report cards and ID cards |
| `SMS_ADMIN_USERNAME` | `admin` | Username for the first seeded administrator |
| `SMS_ADMIN_EMAIL` | `admin@school.local` | Email for the first seeded administrator |
| `SMS_ADMIN_PASSWORD` | *(empty)* | Password for first-admin creation; must be set if no admin exists |

Uploads are limited to 10 MB. Accepted extensions are PDF, DOC, DOCX, TXT, JPG, JPEG, PNG, and ZIP. Uploaded files are stored under generated names and served through access-controlled application routes.

## Functionality

- **Administrator:** Manage teachers, students, classes, and subjects; publish announcements; view dashboards; generate reports, report cards, and ID cards.
- **Teacher:** View assigned classes and students; mark attendance; enter grades; create assignments and review submissions; publish announcements; view reports.
- **Student:** View profile, attendance, grades, assignments, and announcements; submit assignment files.
- **Authentication and access control:** Form-based sign-in, BCrypt password hashing, role-specific dashboard redirects, and route restrictions for administrator, teacher, and student areas.

## Application Flow

```mermaid
flowchart TD
    Browser[Web browser] --> Login[Spring Security login]
    Login -->|ADMIN| Admin[Administrator portal]
    Login -->|TEACHER| Teacher[Teacher portal]
    Login -->|STUDENT| Student[Student portal]
    Admin --> MVC[Spring MVC controllers]
    Teacher --> MVC
    Student --> MVC
    MVC --> Services[Application services]
    Services --> Repositories[Spring Data JPA repositories]
    Repositories --> SQLite[(SQLite database)]
    Flyway[Flyway migrations] --> SQLite
    Teacher --> Uploads[(Local assignment uploads)]
    Student --> Uploads
```

## Mobile App API

The Android app (in `android/`) uses a JSON API under `/api/v1`, served by the same application. The website is unchanged: it keeps its own login page, session cookie and CSRF protection, and a mobile token does not sign anyone in to the website.

**Signing in.** `POST /api/v1/auth/login` with `{"login": "<username or email>", "password": "..."}` returns an access token (valid 15 minutes) and a refresh token (valid 30 days). Send the access token on every request as `Authorization: Bearer <accessToken>`. When it expires (HTTP 401), call `POST /api/v1/auth/refresh` with `{"refreshToken": "..."}` to get a new pair; each refresh token works once. `POST /api/v1/auth/logout` ends the session. Only SHA-256 hashes of tokens are stored (table `api_tokens`). Changing a password, or an administrator resetting it, signs the user out of the app on every device. A disabled account is refused on its next request.

**Sign-up** follows the website's rules: students register freely, while teachers and administrators need `SMS_TEACHER_SIGNUP_CODE` / `SMS_ADMIN_SIGNUP_CODE`. The codes stay on the server and are not built into the app.

| Area | Endpoints (all under `/api/v1`) |
| --- | --- |
| Auth (public) | `POST auth/login`, `POST auth/refresh`, `POST auth/logout`, `POST auth/signup`, `GET auth/signup-options` |
| Any signed-in user | `GET me`, `POST me/password` (returns a new token pair) |
| Student | `GET student/dashboard`, `profile`, `attendance`, `grades`, `announcements`, `assignments`, `assignments/{id}`; `POST student/assignments/{id}/submission` (multipart field `file`); `GET student/assignments/{id}/submission/file`, `student/report-card`, `student/id-card` |
| Teacher | `GET teacher/classes`, `teacher/teaching`, `teacher/classes/{id}/students`, `teacher/classes/{id}/report`; `GET`/`PUT teacher/classes/{id}/attendance`; `GET`/`PUT teacher/classes/{id}/subjects/{subjectId}/grades`; `GET`/`POST teacher/assignments`; `GET`/`PUT`/`DELETE teacher/assignments/{id}`; `GET teacher/assignments/{id}/submissions/{submissionId}/file`; `GET`/`POST teacher/announcements`, `DELETE teacher/announcements/{id}` |
| Admin | `GET admin/overview`, `admin/students`, `admin/students/{id}`, `admin/teachers`, `admin/teachers/{id}`, `admin/classes`, `admin/classes/{id}`, `admin/subjects`; `GET`/`POST admin/announcements`, `DELETE admin/announcements/{id}`; `GET admin/reports/attendance`, `admin/reports/classes/{id}`; `GET admin/students/{id}/report-card`, `admin/students/{id}/id-card` |

Adding and editing students, teachers, classes and subjects stays on the website. Access rules are the same as the website's: for example, only the class teacher marks attendance, and only a subject's teacher enters its marks.

**Errors** always have this shape:

```json
{"status": 400, "error": "Bad Request", "message": "Check the highlighted fields.", "fieldErrors": {"email": "must be a well-formed email address"}}
```

Statuses: `400` validation or business rule, `401` not signed in or token expired, `403` not allowed, `404` not found, `409` still in use, `413` file too large.

Deploy the backend behind HTTPS; release builds of the app refuse plain `http`.

## Android App

The app lives in [`android/`](android/). It is written in Kotlin with Jetpack Compose and Material 3, and covers student, teacher and admin users.

1. Install [Android Studio](https://developer.android.com/studio) and open the `android/` folder (**File > Open**). The first sync downloads Gradle and the Android libraries.
2. Set the backend address in `android/gradle.properties` (`sms.apiBaseUrl`). The default, `http://10.0.2.2:8080/`, is the computer running the Android emulator. For a phone on the same Wi-Fi, use `http://<this computer's LAN IP>:8080/`. For the APK given to the school, use the deployed `https://` address.
3. Start the backend, then run the app with **Run > Run 'app'**.
4. For a test APK, use **Build > Build App Bundle(s) / APK(s) > Build APK(s)**; it is written to `android/app/build/outputs/apk/debug/app-debug.apk`. For the APK handed to users, use **Build > Generate Signed App Bundle / APK > APK** with your own keystore. Keep the keystore safe, because updates must be signed with the same key.

## Run Tests

On Windows:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```

## Main Project Areas

- `src/main/java/com/sms/` - application code, separated into authentication, administration, teacher, student, reporting, and shared components
- `src/main/java/com/sms/api/` - the mobile app's JSON API
- `src/main/resources/templates/` - Thymeleaf pages for each portal
- `src/main/resources/db/migration/` - Flyway schema migrations
- `src/test/java/com/sms/` - application and feature tests
- `android/` - the Android app
