package com.sms.common;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;

/** A delete was refused because other rows still reference the record. */
public class InUseException extends RuntimeException {

    private static final int SQLITE_CONSTRAINT = 19;

    public InUseException(Throwable cause) {
        super("Record is still in use", cause);
    }

    /**
     * Flushes pending deletes; a foreign-key failure becomes InUseException.
     * SQLite's driver reports it as a generic JDBC error, so we check the error code.
     */
    public static void flushDeletes(JpaRepository<?, ?> repository) {
        try {
            repository.flush();
        } catch (DataAccessException e) {
            if (isConstraintViolation(e)) {
                throw new InUseException(e);
            }
            throw e;
        }
    }

    private static boolean isConstraintViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == SQLITE_CONSTRAINT) {
                return true;
            }
        }
        return e instanceof org.springframework.dao.DataIntegrityViolationException;
    }
}
