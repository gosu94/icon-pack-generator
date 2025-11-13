package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.ai.MinimaxVideoModelService;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGenerationRequest;
import com.gosu.iconpackgenerator.domain.icons.dto.GifGenerationResponse;
import com.gosu.iconpackgenerator.domain.icons.dto.GifProgressUpdate;
import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.domain.icons.repository.GeneratedIconRepository;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.util.FileStorageService;
import com.gosu.iconpackgenerator.util.VideoToGifService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GifGenerationService {

    public static final int COINS_PER_ICON = 2;
    private static final int DEFAULT_GIF_FPS = 24;
    private static final int DEFAULT_GIF_WIDTH = 300;
    private static final int ICON_TARGET_SIZE = 300;

    private final GeneratedIconRepository generatedIconRepository;
    private final FileStorageService fileStorageService;
    private final MinimaxVideoModelService minimaxVideoModelService;
    private final VideoToGifService videoToGifService;
    private final CoinManagementService coinManagementService;

    @Getter
    @RequiredArgsConstructor
    public static class GifJobContext {
        private final User user;
        private final GifGenerationRequest request;
        private final List<GeneratedIcon> selectedIcons;
        private final CoinManagementService.CoinDeductionResult coinResult;
        private final int totalCost;
    }

    /**
     * Validates the incoming request, fetches selected icons and deducts coins.
     */
    public GifJobContext prepareJob(GifGenerationRequest request, User user) {
        if (request.getIconIds() == null || request.getIconIds().isEmpty()) {
            throw new IllegalArgumentException("At least one icon must be selected.");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new IllegalArgumentException("Request ID is required.");
        }

        List<GeneratedIcon> icons = generatedIconRepository.findByRequestIdAndIconIdIn(
                request.getRequestId(),
                request.getIconIds()
        );

        if (icons.isEmpty()) {
            throw new IllegalArgumentException("Selected icons were not found.");
        }

        // Ensure the order matches the user's selection order
        Map<String, GeneratedIcon> iconMap = icons.stream()
                .collect(Collectors.toMap(GeneratedIcon::getIconId, icon -> icon));
        List<GeneratedIcon> orderedIcons = request.getIconIds().stream()
                .map(iconMap::get)
                .filter(Objects::nonNull)
                .toList();

        if (orderedIcons.size() != request.getIconIds().size()) {
            throw new IllegalArgumentException("One or more icons could not be located for this request.");
        }

        boolean userOwnsIcons = orderedIcons.stream()
                .allMatch(icon -> icon.getUser().getId().equals(user.getId()));
        if (!userOwnsIcons) {
            throw new SecurityException("Cannot generate GIFs for icons that do not belong to the current user.");
        }

        int totalCost = orderedIcons.size() * COINS_PER_ICON;
        CoinManagementService.CoinDeductionResult coinResult =
                coinManagementService.deductCoinsForGeneration(user, totalCost);
        if (!coinResult.isSuccess()) {
            throw new IllegalStateException(coinResult.getErrorMessage());
        }

        log.info("Reserved {} coin(s) (trial: {}) for GIF job of {} icons (request: {}, user: {}).",
                coinResult.getDeductedAmount(),
                coinResult.isUsedTrialCoins(),
                orderedIcons.size(),
                request.getRequestId(),
                user.getEmail());

        return new GifJobContext(user, request, orderedIcons, coinResult, totalCost);
    }

    /**
     * Processes the selected icons asynchronously and emits progress updates.
     */
    public CompletableFuture<GifGenerationResponse> processJob(
            String gifRequestId,
            GifJobContext context,
            Consumer<GifProgressUpdate> progressCallback) {

        List<GeneratedIcon> icons = context.getSelectedIcons();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<GifGenerationResponse.GifAsset> generatedAssets =
                Collections.synchronizedList(new ArrayList<>());

        progressCallback.accept(GifProgressUpdate.started(
                gifRequestId, context.getRequest().getRequestId(), icons.size()));

        List<CompletableFuture<Void>> tasks = icons.stream()
                .map(icon -> processSingleIcon(gifRequestId, context, icon, completed, failures,
                        generatedAssets, progressCallback))
                .toList();

        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .thenApply(voidResult -> buildSuccessResponse(
                        gifRequestId, context, failures.get(), generatedAssets, progressCallback))
                .exceptionally(error -> handleFailure(
                        gifRequestId, context, error, generatedAssets, progressCallback));
    }

    private CompletableFuture<Void> processSingleIcon(
            String gifRequestId,
            GifJobContext context,
            GeneratedIcon icon,
            AtomicInteger completed,
            AtomicInteger failures,
            List<GifGenerationResponse.GifAsset> generatedAssets,
            Consumer<GifProgressUpdate> progressCallback) {

        return CompletableFuture.supplyAsync(() -> readIconBytes(icon))
                .thenCompose(imageBytes -> {
                    byte[] preparedImage = prepareIconForAnimation(imageBytes);
                    String prompt = buildPrompt();
                    long videoStart = System.currentTimeMillis();
                    return minimaxVideoModelService.generateVideo(prompt, preparedImage)
                            .whenComplete((bytes, error) -> {
                                long duration = System.currentTimeMillis() - videoStart;
                                if (error == null) {
                                    log.info("Minimax video generation for icon {} completed in {} ms ({} bytes)",
                                            icon.getIconId(), duration, bytes != null ? bytes.length : 0);
                                } else {
                                    log.warn("Minimax video generation for icon {} failed after {} ms",
                                            icon.getIconId(), duration);
                                }
                            });
                })
                .thenCompose(videoBytes -> CompletableFuture.supplyAsync(() -> convertVideoToGif(videoBytes, icon.getIconId())))
                .thenApply(gifBytes -> persistGif(icon, gifBytes, context))
                .thenAccept(asset -> {
                    generatedAssets.add(asset);
                    int current = completed.incrementAndGet();
                    progressCallback.accept(GifProgressUpdate.progress(
                            gifRequestId,
                            context.getRequest().getRequestId(),
                            context.getSelectedIcons().size(),
                            current,
                            "Generated GIF for icon #" + safeLabel(icon),
                            asset));
                })
                .exceptionally(error -> {
                    failures.incrementAndGet();
                    log.error("Failed to generate GIF for icon {} (request: {})",
                            icon.getIconId(), context.getRequest().getRequestId(), error);
                    progressCallback.accept(GifProgressUpdate.iconFailed(
                            gifRequestId,
                            context.getRequest().getRequestId(),
                            context.getSelectedIcons().size(),
                            completed.get(),
                            icon.getIconId(),
                            "Failed to generate GIF for icon " + safeLabel(icon)
                    ));
                    return null;
                });
    }

    private GifGenerationResponse buildSuccessResponse(
            String gifRequestId,
            GifJobContext context,
            int failureCount,
            List<GifGenerationResponse.GifAsset> generatedAssets,
            Consumer<GifProgressUpdate> progressCallback) {

        GifGenerationResponse response = new GifGenerationResponse();
        response.setGifRequestId(gifRequestId);
        response.setRequestId(context.getRequest().getRequestId());
        response.setGifs(new ArrayList<>(generatedAssets));

        if (failureCount == 0) {
            response.setStatus("success");
            response.setMessage("All GIFs generated successfully.");
        } else if (!generatedAssets.isEmpty()) {
            response.setStatus("partial");
            response.setMessage("Some GIFs failed to generate. Please try again for the missing ones.");
        } else {
            response.setStatus("error");
            response.setMessage("Failed to generate GIFs. Coins have been refunded.");
            refundCoins(context);
        }

        progressCallback.accept(GifProgressUpdate.completed(
                gifRequestId,
                context.getRequest().getRequestId(),
                response.getGifs(),
                response.getMessage()
        ));

        return response;
    }

    private GifGenerationResponse handleFailure(
            String gifRequestId,
            GifJobContext context,
            Throwable error,
            List<GifGenerationResponse.GifAsset> generatedAssets,
            Consumer<GifProgressUpdate> progressCallback) {

        log.error("GIF generation job {} failed", gifRequestId, error);
        refundCoins(context);

        GifGenerationResponse response = new GifGenerationResponse();
        response.setGifRequestId(gifRequestId);
        response.setRequestId(context.getRequest().getRequestId());
        response.setStatus("error");
        response.setMessage("GIF generation failed: " + error.getMessage());
        response.setGifs(new ArrayList<>(generatedAssets));

        progressCallback.accept(GifProgressUpdate.failed(
                gifRequestId,
                context.getRequest().getRequestId(),
                response.getMessage()
        ));

        return response;
    }

    private void refundCoins(GifJobContext context) {
        CoinManagementService.CoinDeductionResult coinResult = context.getCoinResult();
        if (coinResult != null && coinResult.getDeductedAmount() > 0) {
            try {
                coinManagementService.refundCoins(
                        context.getUser(),
                        coinResult.getDeductedAmount(),
                        coinResult.isUsedTrialCoins()
                );
                log.info("Refunded {} coin(s) for failed GIF job (request: {}).",
                        coinResult.getDeductedAmount(), context.getRequest().getRequestId());
            } catch (Exception e) {
                log.error("Failed to refund coins for GIF job {}", context.getRequest().getRequestId(), e);
            }
        }
    }

    private byte[] readIconBytes(GeneratedIcon icon) {
        try {
            return fileStorageService.readIcon(icon.getFilePath());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read icon data for " + icon.getIconId(), e);
        }
    }

    private byte[] prepareIconForAnimation(byte[] iconBytes) {
        try {
            BufferedImage source = javax.imageio.ImageIO.read(new ByteArrayInputStream(iconBytes));
            if (source == null) {
                throw new IOException("Image format not supported.");
            }

            BufferedImage target = new BufferedImage(ICON_TARGET_SIZE, ICON_TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = target.createGraphics();
            try {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, ICON_TARGET_SIZE, ICON_TARGET_SIZE);

                double scale = Math.min(
                        (double) ICON_TARGET_SIZE / source.getWidth(),
                        (double) ICON_TARGET_SIZE / source.getHeight());
                int scaledWidth = (int) Math.round(source.getWidth() * scale);
                int scaledHeight = (int) Math.round(source.getHeight() * scale);
                int offsetX = (ICON_TARGET_SIZE - scaledWidth) / 2;
                int offsetY = (ICON_TARGET_SIZE - scaledHeight) / 2;
                g2d.drawImage(source, offsetX, offsetY, scaledWidth, scaledHeight, null);
            } finally {
                g2d.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(target, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare icon for GIF generation", e);
        }
    }

    private byte[] convertVideoToGif(byte[] videoBytes, String iconId) {
        try {
            long start = System.currentTimeMillis();
            byte[] gif = videoToGifService.convertMp4ToGif(videoBytes, DEFAULT_GIF_FPS, DEFAULT_GIF_WIDTH);
            long duration = System.currentTimeMillis() - start;
            log.info("Converted video to GIF for icon {} in {} ms ({} bytes)", iconId, duration, gif.length);
            return gif;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert video to GIF", e);
        }
    }

    private GifGenerationResponse.GifAsset persistGif(
            GeneratedIcon sourceIcon,
            byte[] gifBytes,
            GifJobContext context) {

        String gifIconId = "gif-" + UUID.randomUUID();
        String iconType = sourceIcon.getIconType();
        if (iconType == null) {
            iconType = (context.getRequest().getGenerationIndex() != null &&
                    context.getRequest().getGenerationIndex() == 1) ? "original" : "variation";
        }

        String fileName = buildGifFileName(sourceIcon, gifIconId);
        String base64Gif = Base64.getEncoder().encodeToString(gifBytes);
        String filePath = fileStorageService.saveIcon(
                context.getUser().getDirectoryPath(),
                sourceIcon.getRequestId(),
                iconType,
                fileName,
                base64Gif
        );

        GeneratedIcon gifEntity = new GeneratedIcon();
        gifEntity.setRequestId(sourceIcon.getRequestId());
        gifEntity.setIconId(gifIconId);
        gifEntity.setUser(context.getUser());
        gifEntity.setFileName(fileName);
        gifEntity.setFilePath(filePath);
        gifEntity.setServiceSource("minimax");
        gifEntity.setGridPosition(sourceIcon.getGridPosition());
        gifEntity.setGenerationIndex(sourceIcon.getGenerationIndex());
        gifEntity.setDescription(buildGifDescription());
        gifEntity.setTheme(sourceIcon.getTheme());
        gifEntity.setIconCount(1);
        gifEntity.setFileSize(fileStorageService.getFileSize(
                context.getUser().getDirectoryPath(),
                sourceIcon.getRequestId(),
                iconType,
                fileName));
        gifEntity.setIconType(iconType);
        gifEntity.setIsOriginalGrid(Boolean.FALSE);

        GeneratedIcon saved = generatedIconRepository.save(gifEntity);

        GifGenerationResponse.GifAsset asset = new GifGenerationResponse.GifAsset();
        asset.setId(saved.getId());
        asset.setIconId(saved.getIconId());
        asset.setFileName(saved.getFileName());
        asset.setFilePath(saved.getFilePath());
        asset.setIconType(saved.getIconType());
        asset.setServiceSource(saved.getServiceSource());
        asset.setGridPosition(saved.getGridPosition());
        asset.setGenerationIndex(saved.getGenerationIndex());

        return asset;
    }

    private String buildGifFileName(GeneratedIcon sourceIcon, String gifIconId) {
        String suffix = sourceIcon.getGridPosition() != null
                ? String.valueOf(sourceIcon.getGridPosition())
                : "gif";
        return String.format("%s_%s_%s.gif",
                sourceIcon.getServiceSource(),
                gifIconId.substring(0, 8),
                suffix);
    }

    private String buildGifDescription() {
        return "Animated GIF";
    }

    private String buildPrompt() {
        return """
                Animate this icon smoothly while preserving its original design, proportions, and framing.
                Keep the background transparent or white and do not change colors, style, or lighting.
                Generate a short looping animation (3–4 seconds) where the movement matches the natural action or function of the object shown in the icon.
                """;
    }

    private String safeLabel(GeneratedIcon icon) {
        return icon.getGridPosition() != null
                ? String.valueOf(icon.getGridPosition() + 1)
                : icon.getIconId().substring(0, Math.min(6, icon.getIconId().length()));
    }
}
