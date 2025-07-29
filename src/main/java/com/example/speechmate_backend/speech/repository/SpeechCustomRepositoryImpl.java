package com.example.speechmate_backend.speech.repository;

import com.example.speechmate_backend.speech.controller.dto.SpeechAnalysisResponseDto;
import com.example.speechmate_backend.speech.domain.QAnalysisResult;
import com.example.speechmate_backend.speech.domain.QSpeech;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpeechCustomRepositoryImpl implements SpeechCustomRepository{

    private final JPAQueryFactory jpaQueryFactory;

    QSpeech speech = QSpeech.speech;
    QAnalysisResult analysisResult = QAnalysisResult.analysisResult;

    @Override
    public List<SpeechAnalysisResponseDto> findNextSpeeches(Long userId, Long lastSpeechId, int limit) {
        return jpaQueryFactory
                .select(Projections.constructor(SpeechAnalysisResponseDto.class,
                        speech.id,
                        speech.createdAt,
                        speech.FileUrl,
                        speech.content,
                        analysisResult.summary,
                        analysisResult.keywords,
                        analysisResult.improvementPoints,
                        analysisResult.logicalCoherenceScore,
                        analysisResult.feedback,
                        analysisResult.scoreExplanation,
                        analysisResult.expectedQuestions
                ))
                .from(speech)
                .join(speech.analysisResult)
                .where(
                        speech.user.id.eq(userId),
                        speech.id.gt(lastSpeechId)
                )
                .orderBy(speech.id.asc())
                .limit(limit)
                .fetch();
    }
}
