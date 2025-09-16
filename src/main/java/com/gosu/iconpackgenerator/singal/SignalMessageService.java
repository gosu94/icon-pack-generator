package com.gosu.iconpackgenerator.singal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SignalMessageService {

    @Value("${callmebot.phone}")
    private String phoneNumber;

    @Value("${callmebot.apikey}")
    private String apiKey;

    @Value("${callmebot.enabled}")
    private Boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendSignalMessage(String message) {
        if (!enabled) {
            return;
        }
        String baseUrl = "https://signal.callmebot.com/signal/send.php";

        String url = String.format("%s?phone=%s&apikey=%s&text=%s",
                baseUrl,
                phoneNumber,
                apiKey,
                message.replace(" ", "+")
        );

        try {
            log.info("Sending Signal message via CallMeBot: {}", url);
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Error sending Signal message: {}", e.getMessage(), e);
        }
    }
}
