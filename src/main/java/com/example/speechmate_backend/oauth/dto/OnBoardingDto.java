package com.example.speechmate_backend.oauth.dto;


import com.example.speechmate_backend.domain.SkillType;

import java.util.List;

public record OnBoardingDto(
        List<SkillType> skill
) {


}
