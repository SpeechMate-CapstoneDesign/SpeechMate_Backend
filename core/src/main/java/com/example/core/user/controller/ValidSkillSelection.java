package com.example.core.user.controller;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SkillSelectionValidator.class) // <<-- 여기에 검증기를 지정합니다.
@Documented
public @interface ValidSkillSelection {
    String message() default "언어적/비언어적 목표는 각각 최대 2개까지 선택 가능합니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
