package com.example.speechmate_backend.user.service;

import com.example.speechmate_backend.common.exception.UserAlreadyExistException;
import com.example.speechmate_backend.common.exception.UserNotFoundException;
import com.example.speechmate_backend.config.redis.RedisUtil;
import com.example.speechmate_backend.config.security.JwtUtil;
import com.example.speechmate_backend.user.controller.dto.TokenReissueResponse;
import com.example.speechmate_backend.user.domain.OauthInfo;
import com.example.speechmate_backend.user.domain.User;
import com.example.speechmate_backend.user.domain.UserSkill;
import com.example.speechmate_backend.user.repository.UserRepository;
import com.example.speechmate_backend.user.repository.UserSkillRepository;
import com.example.speechmate_backend.oauth.dto.AfterOauthSignupDto;
import com.example.speechmate_backend.oauth.dto.OauthLoginResponse;
import com.example.speechmate_backend.oauth.helper.KakaoOauthHelper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final KakaoOauthHelper kakaoOauthHelper;
    private final UserSkillRepository userSkillRepository;

    public OauthLoginResponse loginUser(OauthInfo oauthInfo) {
        Optional<User> userOptional = userRepository.findByOauthInfo(oauthInfo);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            return generateLoginResponse(user, false);
        } else {
            return OauthLoginResponse.builder()
                    .isNewUser(true)
                    .build();
        }

    }

    private OauthLoginResponse generateLoginResponse(User user, boolean isNewUser) {
        String access = jwtUtil.createJwt(user.getId(),"access", 1);
        String refresh = jwtUtil.createJwt(user.getId(), "refresh", 24 * 7);

        String accessExpiry = jwtUtil.getExpiryFormatted(access);
        String refreshExpiry = jwtUtil.getExpiryFormatted(refresh);

        redisUtil.storeRefreshToken(user.getId().toString(), refresh, 24*7);

        return OauthLoginResponse.builder()
                .userId(user.getId())
                .isNewUser(isNewUser)
                .access(access)
                .accessExpiredAt(accessExpiry)
                .refresh(refresh)
                .refreshExpiredAt(refreshExpiry)
                .build();

    }

    @Transactional
    public OauthLoginResponse signupKakaoWhenFirstOauthLogin(AfterOauthSignupDto afterOauthSignupDto) {
        OauthInfo oauthInfo = kakaoOauthHelper.getOauthInfoByKakaoIdToken(afterOauthSignupDto.idToken());
        //이미 있는 유저면 에러
        Optional<User> byOauthInfo = userRepository.findByOauthInfo(oauthInfo);
        if(byOauthInfo.isPresent()){
            throw UserAlreadyExistException.EXCEPTION;
        }
        User user = User.builder()
                .oauthInfo(oauthInfo)
                .build();
        userRepository.save(user);
        List<UserSkill> userSkills = afterOauthSignupDto.onBoardingDto().skill().stream()
                .map(s -> new UserSkill(user, s))
                .toList();


        userSkillRepository.saveAll(userSkills);
        return generateLoginResponse(user, true);
    }

    public TokenReissueResponse reissueToken(@NotBlank(message = "refresh token is required") String refreshToken) {
        return jwtUtil.reissueToken(refreshToken);
    }

    public void logout(Long userId) {
        redisUtil.deleteRefreshToken(userId.toString());
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                        .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        userRepository.delete(user);

        redisUtil.deleteRefreshToken(userId.toString());
    }

    @Transactional
    public void registerFcmToken(Long userId, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("User ID {}의 FCM 토큰이 빈 값이므로 업데이트를 건너뜁니다.", userId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.EXCEPTION);

        user.setFcmToken(fcmToken);
        // @Transactional 이므로 save() 호출 없이 트랜잭션 종료 시 업데이트됨.
        log.info("User ID {}의 FCM 토큰이 업데이트되었습니다.", userId);
    }
}
