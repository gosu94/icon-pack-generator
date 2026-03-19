package com.gosu.iconpackgenerator.singal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String url = String.format(
                "https://signal.callmebot.com/signal/send.php?phone=%s&apikey=%s&text=%s",
                phoneNumber,
                apiKey,
                encodedMessage
        );

        try {
            log.info("Sending Signal message via CallMeBot");
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Error sending Signal message: {}", e.getMessage(), e);
        }
    }
}
