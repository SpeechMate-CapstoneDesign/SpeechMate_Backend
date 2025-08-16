package com.example.speechmate_backend.user.controller;

import com.example.speechmate_backend.oauth.dto.OnBoardingDto;
import com.example.speechmate_backend.user.domain.SkillType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class SkillSelectionValidator implements ConstraintValidator<ValidSkillSelection, OnBoardingDto> {
    @Override
    public boolean isValid(OnBoardingDto dto, ConstraintValidatorContext context) {
        if (dto == null || dto.skill() == null) {
            return true; // null 체크는 @NotEmpty 어노테이션에 맡깁니다.
        }

        List<SkillType> skills = dto.skill();

        long verbalCount = skills.stream()
                .filter(skill -> skill.getCategory() == SkillType.GoalCategory.VERBAL)
                .count();

        long nonVerbalCount = skills.stream()
                .filter(skill -> skill.getCategory() == SkillType.GoalCategory.NON_VERBAL)
                .count();

        // 언어적/비언어적 목표가 각각 2개 이하인지 확인
        boolean isValid = verbalCount <= 2 && nonVerbalCount <= 2;

        if (!isValid) {
            // 커스텀 메시지를 설정하고, 기본 메시지 비활성화
            context.disableDefaultConstraintViolation();
            if (verbalCount > 2) {
                context.buildConstraintViolationWithTemplate("언어적 목표는 최대 2개까지 선택 가능합니다.")
                        .addConstraintViolation();
            }
            if (nonVerbalCount > 2) {
                context.buildConstraintViolationWithTemplate("비언어적 목표는 최대 2개까지 선택 가능합니다.")
                        .addConstraintViolation();
            }
        }

        return isValid;
    }
}
