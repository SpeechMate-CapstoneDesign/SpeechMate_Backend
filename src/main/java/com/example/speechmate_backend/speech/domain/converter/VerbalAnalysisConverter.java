package com.example.speechmate_backend.speech.domain.converter;

import com.example.speechmate_backend.speech.controller.dto.Silence;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.repository.VerbalAnalysisResultRepository;
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

        VerbalAnalysisResult result = speech.getVerbalAnalysisResult();

        // 2. 연결된 것이 없다면, 'speech_id'로 DB에서 '고아 레코드'가 있는지 조회
        if (result == null) {
            result = repository.findBySpeechId(speech.getId())
                    .orElseGet(VerbalAnalysisResult::new); // DB에도 없으면 'new'로 새로 생성
        }

        // 3. 'result' 객체의 필드 값 업데이트 (DB 저장 X, 메모리에서만)
        String silenceJson = objectMapper.writeValueAsString(silenceList);
        String fillerJson = objectMapper.writeValueAsString(fillerUsage);

        // (VerbalAnalysisResult에 만들어둔 update 메서드 호출)
        result.updateAnalysis(wordCnt, syllableCnt, silenceJson, fillerJson);

        speech.setVerbalAnalysisResult(result);
    }

    public void saveRepeatedWords(VerbalAnalysisResult verbalResult, Map<String, Integer> repeatedWords) throws JsonProcessingException {
        String repeatedWordsJson = objectMapper.writeValueAsString(repeatedWords);
        verbalResult.setRepeatedWordsJson(repeatedWordsJson);
        // 엔티티가 이미 트랜잭션 컨텍스트에 있으므로, 별도의 save() 호출 없이 변경사항이 반영됩니다.
    }
}

