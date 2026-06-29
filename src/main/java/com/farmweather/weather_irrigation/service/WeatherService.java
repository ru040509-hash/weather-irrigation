package com.farmweather.weather_irrigation.service;

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

    public String getIrrigationAdvice(int nx, int ny) {

        // 1. 발표시각 계산
        String[] baseTimes = {"0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"};
        LocalDateTime now = LocalDateTime.now();
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = "0200";

        int currentHour = now.getHour();
        for (String t : baseTimes) {
            int tHour = Integer.parseInt(t.substring(0, 2));
            if (currentHour >= tHour) {
                baseTime = t;
            }
        }

        // 2. API 호출
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

        // 4. 관개 판단
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

        // 5. 결과 반환
        if (rainExpected) {
            return "관개 보류 권장 🌧️ - 강수확률 최대 " + maxPop + "%, 평균기온 " + String.format("%.1f", avgTemp) + "도";
        } else if (avgTemp >= 28) {
            return "관개 권장 ☀️ - 강수확률 낮음 " + maxPop + "%, 고온 " + String.format("%.1f", avgTemp) + "도";
        } else {
            return "관개 불필요 🌤️ - 강수확률 " + maxPop + "%, 평균기온 " + String.format("%.1f", avgTemp) + "도";
        }
    }
}