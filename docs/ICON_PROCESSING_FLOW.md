# Icon Processing Flow

This document explains how generated icon grid images are converted into individual icon files.

The main implementation lives in:
- `IconGenerationService`
- `ImageProcessingService`
- `GridBoundaryDetectionService`
- `IconArtifactCleanupService`
- `IconCenteringService`

## High-Level Flow

For normal text-based icon generation:

1. `IconGenerationService` builds a 3x3 icon-grid prompt.
2. The selected model generates one grid image.
3. The generated grid image is passed to `ImageProcessingService#cropIconsFromGrid`.
4. The service detects the 3x3 grid boundaries.
5. Each grid cell is cropped into a separate icon.
6. Optional artifact cleanup removes stray pixels from neighboring cells.
7. Optional centering places the icon on a square canvas.
8. Each icon is encoded as base64 PNG.
9. `IconGenerationService` creates `GeneratedIcon` DTOs and persistence stores them.

The regular dashboard generation path uses:

```java
imageProcessingService.cropIconsFromGrid(imageData, 9, false)
```

That means:
- 9 icons are expected.
- icons are centered to the default target size.
- background removal inside `ImageProcessingService` is disabled.
- artifact cleanup is enabled.

The default icon target size is:

```java
ImageProcessingService.ICON_TARGET_SIZE = 300
```

## Model-Specific Input State

Different model services may return different background conditions before cropping.

Standard and Pro OpenAI image services request transparent backgrounds directly from the model.

Pro+ / GPT-2 uses `gpt-image-2`, which does not reliably generate transparent-background images directly. `Gpt2ModelService` therefore runs an additional Ideogram remove-background step before returning the generated grid image to the processing pipeline.

For Pro+:

1. `Gpt2ModelService` calls OpenAI `gpt-image-2`.
2. The generated image is extracted from the OpenAI response.
3. `IdeoGramRemoveBackGroundService#removeBackground` converts the grid to transparent background.
4. The background-removed grid is returned to `IconGenerationService`.
5. Cropping runs on that already-processed transparent grid.

This ordering matters because grid detection prefers transparent separators.

## cropIconsFromGrid

Main method:

```java
ImageProcessingService#cropIconsFromGrid(
    byte[] imageData,
    int iconCount,
    boolean centerIcons,
    int targetSize,
    boolean removeBackground,
    boolean cleanupArtifacts
)
```

Responsibilities:
- validate image bytes
- optionally remove background from the whole grid
- parse the image into `BufferedImage`
- remove solid outer frames if detected
- log diagnostics
- detect grid boundaries
- crop cells
- clean edge artifacts
- center icons
- encode each icon as base64 PNG
- log processing timing

## Optional Background Removal

`ImageProcessingService` can remove background before cropping when `removeBackground=true`.

Before doing that, it checks whether the image already appears transparent by sampling the image border. If enough sampled border pixels are transparent, background removal is skipped.

In normal generation, this flag is usually `false` because the model service is expected to return an image in the correct state:
- Standard/Pro request transparent backgrounds from OpenAI.
- Pro+ performs Ideogram background removal inside `Gpt2ModelService`.

The optional background-removal path is mostly useful for flows that provide images which may not already have usable transparency.

## Solid Frame Detection

After parsing the grid image, `ImageProcessingService` calls `detectAndRemoveSolidFrame`.

This step handles image-to-image model artifacts where the whole grid may be wrapped in a solid frame. If a frame is detected, the service crops it away before grid boundary detection.

The goal is to prevent the outer border from shifting the expected 3x3 grid coordinates.

## Grid Boundary Detection

Grid boundaries are detected by `GridBoundaryDetectionService#detectGridBounds`.

It returns a `GridBounds` object containing:
- x ranges for the three columns
- y ranges for the three rows
- whether both axes had perfect transparent separators

The detector tries several strategies in order.

### 1. Transparent Boundary Detection

The preferred path looks for transparent vertical and horizontal separator lines near one-third and two-thirds of the image.

For each axis:
1. Calculate ideal boundaries at `dimension / 3` and `dimension * 2 / 3`.
2. Search around those ideal positions.
3. Prefer fully transparent lines.
4. Fall back to highly transparent lines.
5. Validate that sections have acceptable spacing.

Line transparency treats pixels as separator/background when they are:
- transparent, or
- near-white and opaque

This lets the detector work with both transparent gaps and white divider gaps.

If both separator lines for an axis are perfectly transparent, the axis is marked as perfect.

### 2. Content Boundary Detection

If transparent boundary detection fails for an axis, the detector analyzes content density.

For each vertical or horizontal line:
1. Count non-background pixels.
2. Treat transparent pixels and near-white pixels as background.
3. Find low-density lines as gap candidates.
4. Pick two candidates near the expected one-third and two-thirds positions.
5. Reject candidates with unacceptable spacing.

This is a fallback for grids where separators are not truly transparent.

### 3. Improved Equal Division

If both transparent and content-based detection fail, the service divides the image evenly into three sections.

This is the last fallback:

```text
0 -> 1/3 -> 2/3 -> image edge
```

It distributes any remainder pixels across the earlier sections.

## Boundary Buffering

After boundary detection, the service may apply small buffers around internal boundaries.

Buffering exists to avoid including stray pixels from neighboring icons. It is conservative:
- perfect transparent boundaries skip buffering
- high-quality evenly spaced boundaries use no buffer
- fallback boundaries receive a small buffer

The result is converted into three column rectangles and three row rectangles.

Each icon rectangle is then:

```java
new Rectangle(xStart, yStart, xEnd - xStart, yEnd - yStart)
```

## Cropping

`ImageProcessingService#cropGrid3x3` iterates through the detected grid:

```text
row 0, col 0
row 0, col 1
row 0, col 2
row 1, col 0
...
row 2, col 2
```

For each cell:
1. Get the rectangle from `GridBounds`.
2. Crop with `BufferedImage#getSubimage`.
3. Optionally clean artifacts.
4. Optionally center the icon.
5. Encode to base64 PNG.

The current `18` icon path still uses one 3x3 crop and has a TODO for a second grid.

## Artifact Cleanup

`IconArtifactCleanupService#cleanupIconArtifacts` runs after each crop when enabled and when the grid did not have perfect transparent separators.

It is designed to remove stray fragments from neighboring cells.

Process:
1. Copy the cropped icon into an ARGB image.
2. Detect the largest connected foreground component.
3. Treat that largest component as the main icon.
4. Inspect outer edge bands and corners.
5. Make isolated or disconnected artifact pixels transparent.

If perfect transparent grid lines were detected, artifact cleanup is skipped because cross-cell leakage is unlikely.

## Icon Centering

`IconCenteringService#centerIcon` places each cropped icon on a square canvas.

Process:
1. Detect visible content bounds.
2. Crop to those bounds.
3. Create a transparent square canvas.
4. Scale the content to fit at most 90% of the canvas.
5. Draw the content centered.

If `targetSize <= 0`, the canvas uses the larger dimension of the cropped icon. In normal generation, the target is `300`.

### Transparent Images With White Icon Tiles

For transparent-background images, centering treats every non-transparent pixel as content, including white.

This is important for Pro+ / GPT-2 output after Ideogram background removal. Those images may contain white rounded app-icon tiles on top of a transparent grid background. The white tile is part of the icon and must not be discarded.

For non-transparent images, the centering service still ignores near-white pixels so it can remove plain white backgrounds.

## Output Encoding

Each final cropped/centered icon is converted to PNG and base64-encoded.

The generated response stores:
- `id`
- `base64Data`
- `description`
- `gridPosition`
- `serviceSource`

`gridPosition` follows the row-major crop order from `0` to `8`.

## Persistence

After generation succeeds, `IconGenerationService` persists icons through `IconPersistenceService`.

Trial mode has an additional two-step behavior:
1. Persist non-watermarked originals for internal/export recovery.
2. Apply trial watermark.
3. Persist watermarked versions for the user-facing gallery.

Regular paid generations persist once without watermarking.

## Generate More Flow

The "Generate More" path uses the same image processing service after model generation.

For Pro+ generate-more:
1. `IconGenerationController` calls `Gpt2ModelService#generateImageToImage`.
2. `Gpt2ModelService` removes background with Ideogram.
3. `IconGenerationController` calls:

```java
imageProcessingService.cropIconsFromGrid(
    newImageData,
    9,
    true,
    ImageProcessingService.ICON_TARGET_SIZE,
    false,
    true
)
```

So the generated-more Pro+ path also crops after Ideogram background removal.

## Diagnostics and Tests

`ImageProcessingService` logs a processing timing summary with:
- transparency check time
- background removal time
- solid frame detection time
- diagnostics time
- grid bounds detection time
- artifact cleanup time
- icon centering time

Relevant tests:
- `Gpt2GridCroppingSpec` covers Pro+ background-removed grid cropping and centering.
- `ImageProcessingServiceSpec` contains broader visual/debug-oriented image processing tests.

The Pro+ regression fixture is:

```text
src/test/resources/icons/gpt-2-background-removed.png
```

The focused test writes diagnostic output to:

```text
src/test/resources/images/output/gpt2-grid-cropping/
```

