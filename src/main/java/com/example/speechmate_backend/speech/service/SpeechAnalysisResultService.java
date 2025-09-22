package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.speech.controller.dto.AnalysisResultDto;
import com.example.speechmate_backend.speech.controller.dto.GptResponse;
import com.example.speechmate_backend.speech.controller.dto.Silence;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.domain.converter.VerbalAnalysisConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.*;

/*
* 대본 분석, 언어적 분석
* */
@Slf4j
@RequiredArgsConstructor
@Service
public class SpeechAnalysisResultService {

    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final VerbalAnalysisConverter verbalAnalysisConverter;

    AnalysisResult analyzeText(Speech speech, String text) {
        var outputParser = new BeanOutputConverter<>(GptResponse.class);

        // 프롬프트에 outputParser.getFormat()을 포함시켜 LLM이 JSON 형식을 따르도록 지시.
        String promptTemplateString = """
            당신은 세계 최고의 스피치 코치이자 텍스트 분석 전문가입니다.
            다음 발표 텍스트를 분석하고, 아래의 모든 항목에 대해 한국어로 구체적이고 전문적인 피드백을 제공해주세요.

            분석할 텍스트:
            ---
            {text}
            ---

            지시사항:
            - summary: 핵심 내용을 3~4개의 문장으로 간결하게 요약하세요.
            - keywords: 텍스트의 핵심 단어 5개를 쉼표로 구분된 하나의 문자열로 제공하세요.
            - improvementPoints: 개선점 3가지를 구체적으로 서술하세요. 이 항목은 JSON 배열(List<String>)로 응답해야 합니다.
            - feedback: 발표 전체에 대한 종합적인 피드백을 작성하세요. 발표자의 강점과 약점을 균형 있게 다루세요.
            - expectedQuestions: 청중이 발표를 들은 후 할 수 있는 질문을 3가지 예측해서 작성하세요. 이 항목은 JSON 배열(List<String>)로 응답해야 합니다.
            - repeatedWords: 텍스트에서 2회 이상 반복된 단어를 '단어' : '횟수' 형식의 JSON 객체로 제공해주세요.
            - 결과는 반드시 아래에 명시된 JSON 형식으로만 응답해야 합니다. 다른 설명은 절대 추가하지 마세요.
            
            {format}
            """;

        // {format}  <-- 여기에 BeanOutputConverter가 생성하는 JSON 형식 지시사항이 삽입


        // PromptTemplate 생성 시 format 지시사항을 포함.
        PromptTemplate promptTemplate = new PromptTemplate(promptTemplateString);
        Prompt prompt = promptTemplate.create(Map.of(
                "text", text,
                "format", outputParser.getFormat()
        ));

        System.out.println(prompt.getContents());


        ChatClient chatClient = chatClientBuilder.build();

        // 1. AI를 호출하여 JSON 응답을 문자열로 받기.
        String jsonResponse = chatClient.prompt(prompt)
                .call()
                .content();
        System.out.println("AI 응답: " + jsonResponse);

        // 2. 받은 문자열을 ObjectMapper로 직접 파싱.
        try {
            GptResponse gptResponse = objectMapper.readValue(jsonResponse, GptResponse.class);
            AnalysisResultDto dto = new AnalysisResultDto(
                    gptResponse.summary(),
                    gptResponse.keywords(),
                    gptResponse.improvementPoints(),
                    gptResponse.expectedQuestions(),
                    gptResponse.feedback()
            );
            VerbalAnalysisResult verbalResult = speech.getVerbalAnalysisResult();
            if (verbalResult != null) {
                verbalAnalysisConverter.saveRepeatedWords(verbalResult, gptResponse.repeatedWords());
            }

            return AnalysisResult.from(dto);
        } catch (Exception e) {
            // 파싱 실패 시 예외 처리
            throw new RuntimeException("AI 응답 파싱 실패: " + jsonResponse, e);
        }

    }

    /*
    * 침묵구간, 최종 content합쳐서 content저장,
    * */
    public String verbalanalyze(Speech speech, TranscriptionResponse transcriptionResponse) {
        StringBuilder contentsb = new StringBuilder();

        long wordCnt = 0;//어절 수
        long syllableCnt = 0;//음절 수(글자 수)
        List<Silence> silenceList = new LinkedList<>();

        Set<String> fillers = Set.of("아", "어", "음", "그", "저기", "막");
        Map<String, List<Integer>> fillerUsage = new HashMap<>();

        for(TranscriptionResponse.Utterance ut : transcriptionResponse.results().utterances()) {
            ////content 저장 O(n)
            if(ut.msg()!= null && !ut.msg().isEmpty()) {
                contentsb.append(ut.msg()).append(" ");
            }

            //공백 구간
            for(int i=0;i<ut.words().size();i++) {
                TranscriptionResponse.Word cur = ut.words().get(i);
                String txt = cur.text().trim();
                txt = txt.replaceAll("[,\\.?!]", ""); //불필요한 문자 제거
                wordCnt++; // 어절 수 증가
                syllableCnt += countSyllables(cur.text()); // 음절 수 (글자 수)

                //간투어 횟수 파악
                if(fillers.contains(txt)) {
                    fillerUsage.computeIfAbsent(txt, k -> new ArrayList<>())
                            .add(cur.start_at());
                }
                // 침묵 구간 계산 (마지막 단어 제외)
                if (i < ut.words().size() - 1) {
                    TranscriptionResponse.Word nxt = ut.words().get(i + 1);
                    long diff = nxt.start_at() - (cur.start_at() + cur.duration());
                    if (diff >= 1000) {
                        long st = cur.start_at() + cur.duration();
                        long en = nxt.start_at();
                        silenceList.add(new Silence(diff, st, en, cur.text(), nxt.text()));
                    }
                }
            }

        }
        String con = contentsb.toString().trim();
        speech.setContent(con);



        //mysql에 verbalAnalysis엔티티를 만들어 silenceList(공백구간), wordCnt, syllableCnt 저장해야함
        try {
            verbalAnalysisConverter.saveAnalysis(speech, wordCnt, syllableCnt, silenceList, fillerUsage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        /*log.info("content: {}", con);
        log.info("음절 수 : {}, 어절 수 : {}", syllableCnt, wordCnt);
        //System.out.println("침묵 구간: " + silenceList);
        for(Silence s : silenceList) {
            System.out.println("침묵 지속시간: " + s.getDuration()+ ", 시작 시간: " + s.getStartTime() + ", 끝 시간: " + s.getEndTime() + ", 시작 단어: " + s.getWordBefore()+ ", 다음 단어: " + s.getWordAfter());
            System.out.println();
        }

        for(Map.Entry<String, List<Integer>> f : fillerUsage.entrySet()) {
            System.out.println("간투어: [" + f.getKey() + "], 타임스탬프: " + f.getValue());
            System.out.println();
        }*/


        return con;

    }

    public static long countSyllables(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length(); // 모든 글자를 1음절로 계산
    }
}
