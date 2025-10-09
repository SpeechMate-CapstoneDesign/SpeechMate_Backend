package com.example.core.speech.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeechMetadataRequestDto(
        @NotBlank(message = "발표 이름은 필수 항목입니다.") // null, "", " " 모두 허용하지 않음
        @Size(max = 100, message = "발표 이름은 100자를 초과할 수 없습니다.")
        String title,
        @Size(max = 255, message = "발표 상황은 255자를 초과할 수 없습니다.")
        String presentationContext,
        @Size(max = 255, message = "청중 정보는 255자를 초과할 수 없습니다.")
        String audience,
        @Size(max = 255, message = "장소 정보는 255자를 초과할 수 없습니다.")
        String location
) {
}
