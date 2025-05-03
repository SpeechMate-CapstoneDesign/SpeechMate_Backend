package com.example.speechmate_backend.oauth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OIDCPublickeyResponse {
    List<OIDCPublickeyDto> keys;
}
