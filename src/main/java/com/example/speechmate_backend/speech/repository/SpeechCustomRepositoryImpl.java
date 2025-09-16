package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.controller.SortType;
import com.example.speechmate_backend.speech.controller.dto.SpeechFeedDto;
import com.example.speechmate_backend.speech.domain.QAnalysisResult;
import com.example.speechmate_backend.speech.domain.QSpeech;
import com.example.speechmate_backend.speech.domain.Speech;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpeechCustomRepositoryImpl implements SpeechCustomRepository{

    private final JPAQueryFactory jpaQueryFactory;
    private final SpeechRepository speechRepository;

    QSpeech speech = QSpeech.speech;
    QAnalysisResult analysisResult = QAnalysisResult.analysisResult;

    @Override
    public List<SpeechFeedDto> findMyFeed(Long userId, Long lastSpeechId, int limit, SortType sortType) {
        SortType actualSortType = (sortType == null) ? SortType.LATEST : sortType;

        JPAQuery<SpeechFeedDto> query = jpaQueryFactory
                .select(Projections.constructor(SpeechFeedDto.class,
                        speech.id,
                        speech.title,
                        speech.createdAt, // 문자열 변환
                        speech.duration,
                        speech.fileType,
                        speech.FileUrl,
                        speech.presentationContext,
                        speech.audience,
                        speech.location
                ))
                .from(speech)
                .where(
                        speech.user.id.eq(userId),
                        // 커서 기반 조건 추가
                        createPagingCondition(lastSpeechId, sortType)
                )
                .limit(limit);

        // 정렬 로직 추가
        switch (actualSortType) {
            case LATEST:
                query.orderBy(speech.createdAt.desc(), speech.id.desc());
                break;
            case OLDEST:
                query.orderBy(speech.createdAt.asc(), speech.id.asc());
                break;
            case NAME:
                query.orderBy(speech.title.asc(), speech.id.asc());
                break;
        }

        return query.fetch();
    }

    private BooleanExpression ltSpeechId(Long lastSpeechId) {
        return lastSpeechId == null ? null : speech.id.lt(lastSpeechId); //마지막 스피치id보다 작은지
    }
    private BooleanExpression createPagingCondition(Long lastSpeechId, SortType sortType) {
        if (lastSpeechId == null) {
            return null;
        }

        // lastSpeechId에 해당하는 Speech 엔티티를 한 번만 조회하여 커서 값을 얻습니다.
        Speech lastSpeech = speechRepository.findById(lastSpeechId)
                .orElse(null);
        if (lastSpeech == null) {
            return null;
        }

        // 정렬 타입에 따라 적절한 BooleanExpression을 반환합니다.
        return switch (sortType) {
            case LATEST -> // 최신순 (createdAt 내림차순, id 내림차순)
                    speech.createdAt.lt(lastSpeech.getCreatedAt())
                            .or(speech.createdAt.eq(lastSpeech.getCreatedAt())
                                    .and(speech.id.lt(lastSpeechId)));
            case OLDEST -> // 오래된순 (createdAt 오름차순, id 오름차순)
                    speech.createdAt.gt(lastSpeech.getCreatedAt())
                            .or(speech.createdAt.eq(lastSpeech.getCreatedAt())
                                    .and(speech.id.gt(lastSpeechId)));
            case NAME -> // 이름순 (title 오름차순, id 오름차순)
                    speech.title.gt(lastSpeech.getTitle())
                            .or(speech.title.eq(lastSpeech.getTitle())
                                    .and(speech.id.gt(lastSpeechId)));
        };
    }

}
