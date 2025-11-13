package com.gosu.iconpackgenerator.domain.icons.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * SSE payload describing GIF generation progress.
 */
@Data
@Builder
public class GifProgressUpdate {

    private String gifRequestId;
    private String requestId;
    private int totalIcons;
    private int completedIcons;
    private String status;
    private String message;
    private String eventType;
    private String currentIconId;
    private List<GifGenerationResponse.GifAsset> gifs;

    public static GifProgressUpdate started(String gifRequestId, String requestId, int total) {
        return GifProgressUpdate.builder()
                .gifRequestId(gifRequestId)
                .requestId(requestId)
                .totalIcons(total)
                .completedIcons(0)
                .status("started")
                .message("Preparing GIF generation")
                .eventType("gif_progress")
                .gifs(Collections.emptyList())
                .build();
    }

    public static GifProgressUpdate progress(String gifRequestId, String requestId, int total, int completed,
                                             String message, GifGenerationResponse.GifAsset asset) {
        return GifProgressUpdate.builder()
                .gifRequestId(gifRequestId)
                .requestId(requestId)
                .totalIcons(total)
                .completedIcons(completed)
                .status("in_progress")
                .message(message)
                .eventType("gif_progress")
                .currentIconId(asset != null ? asset.getIconId() : null)
                .gifs(asset != null ? List.of(asset) : Collections.emptyList())
                .build();
    }

    public static GifProgressUpdate iconFailed(String gifRequestId, String requestId, int total, int completed,
                                               String iconId, String errorMessage) {
        return GifProgressUpdate.builder()
                .gifRequestId(gifRequestId)
                .requestId(requestId)
                .totalIcons(total)
                .completedIcons(completed)
                .status("partial_failure")
                .message(errorMessage)
                .eventType("gif_progress")
                .currentIconId(iconId)
                .gifs(Collections.emptyList())
                .build();
    }

    public static GifProgressUpdate completed(String gifRequestId, String requestId,
                                              List<GifGenerationResponse.GifAsset> assets, String message) {
        return GifProgressUpdate.builder()
                .gifRequestId(gifRequestId)
                .requestId(requestId)
                .status("completed")
                .message(message)
                .eventType("gif_complete")
                .gifs(assets)
                .totalIcons(assets != null ? assets.size() : 0)
                .completedIcons(assets != null ? assets.size() : 0)
                .build();
    }

    public static GifProgressUpdate failed(String gifRequestId, String requestId, String errorMessage) {
        return GifProgressUpdate.builder()
                .gifRequestId(gifRequestId)
                .requestId(requestId)
                .status("error")
                .message(errorMessage)
                .eventType("gif_error")
                .gifs(Collections.emptyList())
                .build();
    }
}
