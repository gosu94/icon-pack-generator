package com.gosu.iconpackgenerator.domain.letters.controller;

import com.gosu.iconpackgenerator.domain.icons.dto.IconExportRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.IconGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.service.FileStorageService;
import com.gosu.iconpackgenerator.domain.icons.service.IconExportService;
import com.gosu.iconpackgenerator.domain.letters.controller.api.LetterPackExportControllerAPI;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackExportRequest;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterPackExportRequest.LetterIconPayload;
import com.gosu.iconpackgenerator.domain.letters.dto.LetterGalleryExportRequest;
import com.gosu.iconpackgenerator.domain.letters.entity.GeneratedLetterIcon;
import com.gosu.iconpackgenerator.domain.letters.repository.GeneratedLetterIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LetterPackExportController implements LetterPackExportControllerAPI {

    private static final List<String> ALLOWED_FORMATS = List.of("png", "webp");

    private final IconExportService iconExportService;
    private final GeneratedLetterIconRepository generatedLetterIconRepository;
    private final FileStorageService fileStorageService;

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportLetterPack(LetterPackExportRequest request,
                                                   @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized letter pack export attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();
        log.info("Processing letter pack export for request {} by {}", request.getRequestId(), user.getEmail());

        List<IconGenerationResponse.GeneratedIcon> icons = loadIconsForExport(request);
        if (icons.isEmpty()) {
            log.error("No letter icons available for export for request {}", request.getRequestId());
            return ResponseEntity.notFound().build();
        }

        List<String> formats = filterFormats(request.getFormats());
        IconExportRequest exportRequest = new IconExportRequest();
        exportRequest.setRequestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString());
        exportRequest.setServiceName("letters");
        exportRequest.setGenerationIndex(1);
        exportRequest.setIcons(icons);
        exportRequest.setFormats(formats);
        exportRequest.setVectorizeSvg(false);

        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest);
            String safeRequestId = exportRequest.getRequestId().replaceAll("[^a-zA-Z0-9\\-]", "");
            String fileName = String.format("letter-pack-%s.zip", safeRequestId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            return ResponseEntity.ok().headers(headers).body(zipData);
        } catch (Exception e) {
            log.error("Failed to export letter pack for request {}", request.getRequestId(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to export letter pack".getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<IconGenerationResponse.GeneratedIcon> loadIconsForExport(LetterPackExportRequest request) {
        if (request.getIcons() != null && !request.getIcons().isEmpty()) {
            return mapPayloadToGeneratedIcons(request.getIcons());
        }

        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            return List.of();
        }

        List<GeneratedLetterIcon> storedIcons = generatedLetterIconRepository
                .findByRequestIdOrderBySequenceIndexAsc(request.getRequestId());
        List<IconGenerationResponse.GeneratedIcon> mapped = new ArrayList<>();

        for (GeneratedLetterIcon storedIcon : storedIcons) {
            try {
                byte[] data = fileStorageService.readLetterIcon(storedIcon.getFilePath());
                IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
                icon.setId(storedIcon.getLetter() + "-" + storedIcon.getId());
                icon.setBase64Data(Base64.getEncoder().encodeToString(data));
                icon.setDescription("Letter " + storedIcon.getLetter());
                icon.setGridPosition(storedIcon.getSequenceIndex() != null ? storedIcon.getSequenceIndex() : 0);
                icon.setServiceSource("letters");
                mapped.add(icon);
            } catch (IOException e) {
                log.error("Failed to read letter icon file {}", storedIcon.getFilePath(), e);
            }
        }

        return mapped;
    }

    private List<IconGenerationResponse.GeneratedIcon> mapPayloadToGeneratedIcons(List<LetterIconPayload> payloads) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        for (int i = 0; i < payloads.size(); i++) {
            LetterIconPayload payload = payloads.get(i);
            if (payload.getBase64Data() == null || payload.getBase64Data().isBlank()) {
                continue;
            }
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(payload.getLetter() + "-" + i);
            icon.setBase64Data(payload.getBase64Data());
            icon.setDescription("Letter " + payload.getLetter());
            icon.setGridPosition(i);
            icon.setServiceSource("letters");
            icons.add(icon);
        }
        return icons;
    }

    private List<String> filterFormats(List<String> requestedFormats) {
        if (requestedFormats == null || requestedFormats.isEmpty()) {
            return new ArrayList<>(ALLOWED_FORMATS);
        }
        List<String> filtered = new ArrayList<>();
        for (String format : requestedFormats) {
            if (format == null) continue;
            String normalized = format.trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_FORMATS.contains(normalized) && !filtered.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        if (filtered.isEmpty()) {
            filtered.add("png");
        }
        return filtered;
    }

    @Override
    @ResponseBody
    public ResponseEntity<byte[]> exportLettersFromGallery(@Valid LetterGalleryExportRequest request,
                                                           @AuthenticationPrincipal OAuth2User principal) {
        if (!(principal instanceof CustomOAuth2User customUser)) {
            log.warn("Unauthorized letter gallery export attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not authenticated".getBytes(StandardCharsets.UTF_8));
        }

        User user = customUser.getUser();
        log.info("Processing gallery letter export for user {}", user.getEmail());

        List<String> filePaths = request.getLetterFilePaths();
        if (filePaths == null || filePaths.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("No letter file paths provided".getBytes(StandardCharsets.UTF_8));
        }

        List<GeneratedLetterIcon> storedIcons = generatedLetterIconRepository.findByFilePathIn(filePaths);
        if (storedIcons.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<IconGenerationResponse.GeneratedIcon> iconsToExport = new ArrayList<>();
        for (GeneratedLetterIcon storedIcon : storedIcons) {
            try {
                byte[] data = fileStorageService.readLetterIcon(storedIcon.getFilePath());
                IconGenerationResponse.GeneratedIcon iconDto = new IconGenerationResponse.GeneratedIcon();
                iconDto.setId(storedIcon.getLetter() + "-" + storedIcon.getId());
                iconDto.setBase64Data(Base64.getEncoder().encodeToString(data));
                iconDto.setDescription("Letter " + storedIcon.getLetter());
                iconDto.setGridPosition(storedIcon.getSequenceIndex() != null ? storedIcon.getSequenceIndex() : 0);
                iconDto.setServiceSource("letters");
                iconsToExport.add(iconDto);
            } catch (IOException e) {
                log.error("Failed to read letter icon file {}", storedIcon.getFilePath(), e);
            }
        }

        if (iconsToExport.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to load letter icons for export".getBytes(StandardCharsets.UTF_8));
        }

        IconExportRequest exportRequest = new IconExportRequest();
        exportRequest.setIcons(iconsToExport);
        exportRequest.setRequestId("letter-gallery-" + UUID.randomUUID().toString().substring(0, 8));
        exportRequest.setServiceName("letters");
        exportRequest.setGenerationIndex(1);
        exportRequest.setFormats(filterFormats(request.getFormats()));
        exportRequest.setVectorizeSvg(false);

        try {
            byte[] zipData = iconExportService.createIconPackZip(exportRequest);
            String fileName = String.format("letter-pack-gallery-%s.zip", exportRequest.getRequestId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(zipData.length);

            return ResponseEntity.ok().headers(headers).body(zipData);
        } catch (Exception e) {
            log.error("Failed to export letter pack from gallery", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to export letter pack".getBytes(StandardCharsets.UTF_8));
        }
    }
}
