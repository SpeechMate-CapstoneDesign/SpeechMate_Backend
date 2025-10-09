package com.example.core.oauth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OIDCPublickeyResponse {
    List<OIDCPublickeyDto> keys;
}
