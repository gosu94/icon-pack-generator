package com.gosu.iconpackgenerator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppConfigController {

    private final AIServicesConfig aiServicesConfig;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAppConfig() {
        return ResponseEntity.ok(Map.of(
                "proPlusEnabled", aiServicesConfig.isGpt2Enabled()
        ));
    }
}
