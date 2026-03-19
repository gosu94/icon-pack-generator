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
        String url = String.format(
                "https://signal.callmebot.com/signal/send.php?phone=%s&apikey=%s&text=%s",
                phoneNumber,
                apiKey,
                formatMessageForCallMeBot(message)
        );

        try {
            log.info("Sending Signal message via CallMeBot");
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Error sending Signal message: {}", e.getMessage(), e);
        }
    }

    private String formatMessageForCallMeBot(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        return message
                .replace("%", "%25")
                .replace("&", "%26")
                .replace("#", "%23")
                .replace("+", "%2B")
                .replace("=", "%3D")
                .replace("\r\n", "%0A")
                .replace("\n", "%0A")
                .replace(" ", "+");
    }
}
