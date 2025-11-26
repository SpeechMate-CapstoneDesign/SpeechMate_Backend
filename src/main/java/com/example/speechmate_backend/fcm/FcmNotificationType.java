package com.example.speechmate_backend.fcm;

public enum FcmNotificationType {

    // [TYPE_KEY] (알림 제목, 본문 템플릿 - %s는 dynamic argument)
    ANALYSIS_COMPLETED("비언어적 요소 분석 완료!", "'%s' 스피치 비언어적 분석이 끝났어요. 개선 포인트를 확인해보세요!");

    private final String title;
    private final String bodyFormat;

    FcmNotificationType(String title, String bodyFormat) {
        this.title = title;
        this.bodyFormat = bodyFormat;
    }

    public String getTitle() {
        return title;
    }

    /**
     * 알림 본문 템플릿에 동적 인자를 주입하여 완성된 문자열을 반환합니다.
     * @param args 본문에 삽입할 동적 값들 (예: speechTitle)
     * @return 포맷된 알림 본문
     */
    public String getBody(String... args) {
        return String.format(this.bodyFormat, (Object[]) args);
    }
}