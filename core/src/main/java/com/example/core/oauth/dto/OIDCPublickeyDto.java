package com.example.core.oauth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OIDCPublickeyDto {
    private String kid;
    private String alg;
    private String use;
    private String n;
    private String e;
}
