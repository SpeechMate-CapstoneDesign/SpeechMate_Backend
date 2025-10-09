package com.example.core.user.service;

import com.example.core.common.exception.UserAlreadyExistException;
import com.example.core.common.exception.UserNotFoundException;
import com.example.core.config.redis.RedisUtil;
import com.example.core.config.security.JwtUtil;
import com.example.core.user.controller.dto.TokenReissueResponse;
import com.example.core.user.domain.OauthInfo;
import com.example.core.user.domain.User;
import com.example.core.user.domain.UserSkill;
import com.example.core.user.repository.UserRepository;
import com.example.core.user.repository.UserSkillRepository;
import com.example.core.oauth.dto.AfterOauthSignupDto;
import com.example.core.oauth.dto.OauthLoginResponse;
import com.example.core.oauth.helper.KakaoOauthHelper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
}
