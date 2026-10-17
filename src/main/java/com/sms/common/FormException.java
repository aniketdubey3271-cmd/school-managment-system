package com.sms.common;

import org.springframework.validation.BindingResult;

/** A business-rule failure to show on a form; field is null for a form-wide error. */
public class FormException extends RuntimeException {

    private final String field;

    public FormException(String field, String message) {
        super(message);
        this.field = field;
    }

    public FormException(String message) {
        this(null, message);
    }

    public String getField() {
        return field;
    }

    public void addTo(BindingResult result) {
        if (field == null) {
            result.reject("invalid", getMessage());
        } else {
            result.rejectValue(field, "invalid", getMessage());
        }
    }
}
