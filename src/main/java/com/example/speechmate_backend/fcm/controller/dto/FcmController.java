package com.example.speechmate_backend.fcm.controller.dto;

import com.example.speechmate_backend.config.security.CustomUserDetails;
import com.example.speechmate_backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FCM", description = "FCM 토큰 등록")
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
@RestController
public class FcmController {

    private final UserService userService;

    @Operation(summary = "fcm 토큰 등록")
    @PostMapping("/register")
    public ResponseEntity<Void> registerFcmToken(
            @AuthenticationPrincipal CustomUserDetails userDetails, // JWT를 통해 인증된 사용자 ID
            @RequestBody FcmTokenRequest request
    ) {
        userService.registerFcmToken(userDetails.getUserId(), request.fcmToken());

        return ResponseEntity.ok().build();
    }
}
