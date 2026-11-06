package com.sportbooking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation validate tự viết: LocalTime phải tròn mốc :00 hoặc :30 (vì slot là 30 phút).
 * Dùng giống @NotBlank: đặt lên field của DTO, Spring tự kiểm tra khi có @Valid.
 * Giá trị null được coi là hợp lệ -> kết hợp @NotNull nếu bắt buộc.
 */
@Documented
@Constraint(validatedBy = HalfHourTimeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfHourTime {

    String message() default "Thời gian phải tròn mốc :00 hoặc :30";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
