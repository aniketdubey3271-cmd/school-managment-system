package com.sms.school;

public enum ExamType {
    UNIT_TEST_1("Unit Test 1"),
    UNIT_TEST_2("Unit Test 2"),
    MIDTERM("Midterm"),
    FINAL("Final");

    private final String label;

    ExamType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
