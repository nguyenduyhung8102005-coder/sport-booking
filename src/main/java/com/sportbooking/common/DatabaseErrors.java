package com.sportbooking.common;

/**
 * Nhận diện lỗi database theo TÊN constraint (lý do ta đặt tên cho mọi constraint từ V1).
 *
 * DataIntegrityViolationException của Spring chỉ nói "vi phạm ràng buộc", không nói ràng buộc nào.
 * Bên trong nó (chuỗi cause) có exception của Hibernate chứa tên constraint, hoặc ít nhất
 * message của PostgreSQL có tên đó: duplicate key value violates unique constraint "uk_...".
 */
public final class DatabaseErrors {

    private DatabaseErrors() {
    }

    public static boolean isConstraintViolation(Throwable error, String constraintName) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException hibernateError
                    && constraintName.equalsIgnoreCase(hibernateError.getConstraintName())) {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
