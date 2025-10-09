package com.example.core.speech.domain.converter;

import com.example.core.speech.controller.dto.Silence;
import com.example.core.speech.domain.Speech;
import com.example.core.speech.domain.VerbalAnalysisResult;
import com.example.core.speech.repository.VerbalAnalysisResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VerbalAnalysisConverter {

    private final VerbalAnalysisResultRepository repository;
    private final ObjectMapper objectMapper;

    public void saveAnalysis(Speech speech,
                             long wordCnt,
                             long syllableCnt,
                             List<Silence> silenceList,
                             Map<String, List<Integer>> fillerUsage) throws JsonProcessingException {

        VerbalAnalysisResult result = new VerbalAnalysisResult();
        result.setSpeech(speech);
        result.setWordCnt(wordCnt);
        result.setSyllableCnt(syllableCnt);

        // JSON 직렬화
        result.setSilenceJson(objectMapper.writeValueAsString(silenceList));
        result.setFillerJson(objectMapper.writeValueAsString(fillerUsage));

        repository.save(result);
        speech.setVerbalAnalysisResult(result);
    }

    public void saveRepeatedWords(VerbalAnalysisResult verbalResult, Map<String, Integer> repeatedWords) throws JsonProcessingException {
        String repeatedWordsJson = objectMapper.writeValueAsString(repeatedWords);
        verbalResult.setRepeatedWordsJson(repeatedWordsJson);
        // 엔티티가 이미 트랜잭션 컨텍스트에 있으므로, 별도의 save() 호출 없이 변경사항이 반영됩니다.
    }
}

