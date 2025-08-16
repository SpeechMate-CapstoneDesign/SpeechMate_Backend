package com.example.speechmate_backend.oauth.dto;


import com.example.speechmate_backend.user.controller.ValidSkillSelection;
import com.example.speechmate_backend.user.domain.SkillType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@ValidSkillSelection
public record OnBoardingDto(

        @NotEmpty(message = "최소 하나의 발표 목표를 선택해야 합니다.")
        @Size(max = 4, message = "언어적 목표는 최대 2개까지 선택 가능.(최대 4개 선택)")
        List<SkillType> skill
) {

}
