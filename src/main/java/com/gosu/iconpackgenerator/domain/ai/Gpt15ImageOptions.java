package com.gosu.iconpackgenerator.domain.ai;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Gpt15ImageOptions {
    String imageSize;
    String background;
    String quality;
    String outputFormat;
    String inputFidelity;
}
