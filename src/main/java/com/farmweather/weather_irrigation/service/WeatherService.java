package com.farmweather.weather_irrigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private static final String SERVICE_KEY = "b727957c46e93e46cf6ff590f71c55400dff7bb3e71a2e49a9acab393c4592b6";
    private static final String BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";

    // Claude API 키 (내일 선생님께 받으면 여기 입력)
    private static final String CLAUDE_API_KEY = "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";
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getIrrigationAdvice(int nx, int ny) {

        // 1. 발표시각 계산
        String[] baseTimes = {"0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"};
        LocalDateTime now = LocalDateTime.now();
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = "0200";

        int currentHour = now.getHour();
        for (String t : baseTimes) {
            int tHour = Integer.parseInt(t.substring(0, 2));
            if (currentHour >= tHour) baseTime = t;
        }

        // 2. 기상청 API 호출
        String url = UriComponentsBuilder.newInstance()
                .uri(java.net.URI.create(BASE_URL))
                .queryParam("serviceKey", SERVICE_KEY)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(false)
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();
        Map response = restTemplate.getForObject(url, Map.class);

        // 3. 응답 파싱
        Map responseBody = (Map) ((Map) response.get("response")).get("body");
        Map items = (Map) responseBody.get("items");
        List<Map> itemList = (List<Map>) items.get("item");

        // 4. 기상 데이터 추출
        int maxPop = 0;
        boolean rainExpected = false;
        double totalTemp = 0;
        int tempCount = 0;

        for (Map item : itemList) {
            String category = (String) item.get("category");
            String value = (String) item.get("fcstValue");

            if ("POP".equals(category)) {
                int pop = Integer.parseInt(value);
                if (pop > maxPop) maxPop = pop;
                if (pop >= 60) rainExpected = true;
            }
            if ("TMP".equals(category)) {
                totalTemp += Double.parseDouble(value);
                tempCount++;
            }
        }

        double avgTemp = tempCount > 0 ? totalTemp / tempCount : 0;

        // 5. Claude AI로 자연어 분석
        try {
            String claudeResult = askClaude(maxPop, avgTemp, rainExpected);
            return claudeResult;
        } catch (Exception e) {
            // Claude API 키 없을 때 기본 결과 반환
            if (rainExpected) {
                return "관개 보류 권장 🌧️ - 강수확률 최대 " + maxPop + "%, 평균기온 " + String.format("%.1f", avgTemp) + "도";
            } else if (avgTemp >= 28) {
                return "관개 권장 ☀️ - 강수확률 낮음 " + maxPop + "%, 고온 " + String.format("%.1f", avgTemp) + "도";
            } else {
                return "관개 불필요 🌤️ - 강수확률 " + maxPop + "%, 평균기온 " + String.format("%.1f", avgTemp) + "도";
            }
        }
    }

    private String askClaude(int maxPop, double avgTemp, boolean rainExpected) throws Exception {
        String prompt = String.format(
                "당신은 농업 전문가 AI 'Farmo'입니다. 아래 기상 데이터를 분석해서 오늘 농업용수(관개) 여부를 판단해주세요.\n\n" +
                        "- 강수확률 최대: %d%%\n" +
                        "- 평균기온: %.1f도\n" +
                        "- 비 예보 여부: %s\n\n" +
                        "반드시 첫 줄에 '관개 보류 권장', '관개 권장', '관개 불필요' 중 하나로 시작하고, " +
                        "이어서 강수확률과 기온 정보를 포함해 2줄 이내로 친절하게 설명해주세요.",
                maxPop, avgTemp, rainExpected ? "있음" : "없음"
        );

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "claude-sonnet-4-6",
                "max_tokens", 1000,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        Request request = new Request.Builder()
                .url(CLAUDE_API_URL)
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("x-api-key", CLAUDE_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonNode json = objectMapper.readTree(responseBody);
            return json.path("content").get(0).path("text").asText();
        }
    }
}