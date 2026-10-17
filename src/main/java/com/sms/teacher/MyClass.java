package com.sms.teacher;

import java.util.List;

import com.sms.school.SchoolClass;
import com.sms.school.Subject;

/** A class as seen by one teacher: are they its class teacher, and which subjects they teach there. */
public record MyClass(SchoolClass schoolClass, boolean classTeacher, List<Subject> subjects) {
}
