package com.example.speechmate_backend.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    /**
     * 특정 사용자에게 비어어적 분석 완료 알림을 비동기로 전송
     * 쿨다운 로직은 현재 적용 X
     */
    @Async
    public void sendAnalysisCompletedNotification(String fcmToken, Long speechId, String speechTitle) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("FCM 토큰이 없어 사용자에게 알림을 전송할 수 없습니다. (Speech ID: {})", speechId);
            return;
        }

        FcmNotificationType type = FcmNotificationType.ANALYSIS_COMPLETED;

        // 2. Notification (알림창에 표시되는 내용) 구성
        Notification notification = Notification.builder()
                .setTitle(type.getTitle())
                .setBody(type.getBody(speechTitle))
                .build();

        // 3. Data (클라이언트 앱에서 처리할 데이터) 구성
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putData("type", "non_verbal_analysis")
                .putData("speechId", String.valueOf(speechId))
                .putData("speechName", speechTitle)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[ASYNC] FCM 알림 전송 성공 (Speech ID: {}): {}", speechId, response);
        } catch (FirebaseMessagingException e) {
            log.error("[ASYNC] FCM 알림 전송 실패 (Speech ID: {}): {}", speechId, e.getMessage(), e);
        }
    }


}
