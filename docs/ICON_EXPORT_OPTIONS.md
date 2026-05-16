# Icon Export Options

This document explains the icon export options available from the dashboard and gallery, plus the backend processing used for each option.

## Entry Points

Icon exports are handled by `IconExportController` and `IconExportService`.

Primary endpoints:
- `POST /export` exports icons from an active generation request.
- `POST /api/gallery/export` exports selected icons from the user's gallery.
- `POST /api/gallery/export-gifs` exports generated GIF icons from the gallery.

For generated icon exports, the request can either include explicit icon data or provide a `requestId`, `serviceName`, and `generationIndex`. If icon data is missing, `IconExportController` fetches all icons for that service and generation from `StreamingStateStore`.

For gallery exports, the request provides stored icon file paths. The controller reads those files from storage, converts them into export DTOs, and sends them through the same `IconExportService` ZIP pipeline.

## Export Request Options

Icon export requests use these main fields:

- `formats`: selected output formats, for example `png`, `webp`, and `ico`.
- `vectorizeSvg`: whether to include AI-vectorized SVG files.
- `hqUpscale`: whether to generate high-quality 1024x1024 raster outputs.
- `icons`: optional list of icon DTOs containing base64 image data.
- `requestId`, `serviceName`, `generationIndex`: used to recover icons from an active generation result when the request body does not include icons.

The dashboard export modal shows:
- PNG
- ICO
- WebP
- HQ Upscaled
- Vectorized SVG export

`HQ Upscaled` and `Vectorized SVG export` are mutually exclusive for icon exports in the UI.

## Default Raster Formats

If the backend receives no selected formats, it defaults to:
- SVG wrapper
- PNG
- ICO
- WebP, if WebP4j is available

When the user selects formats in the UI, only those selected raster formats are requested.

## PNG Export

Backend method: `IconExportService#createPngVersions`.

Process:
1. Decode the original icon PNG bytes.
2. Read the image with `ImageIO`.
3. Resize it to every target raster size.
4. Write each resized PNG into the ZIP under `png/`.

Standard PNG sizes:
- `32x32`
- `64x64`
- `128x128`
- `256x256`
- `512x512`

When `hqUpscale` is enabled, `1024x1024` is added to the PNG size list.

ZIP entries look like:

```text
png/01_icon_32x32.png
png/01_icon_64x64.png
png/01_icon_128x128.png
png/01_icon_256x256.png
png/01_icon_512x512.png
png/01_icon_1024x1024.png
```

The `1024x1024` entry uses the HQ-processed image when `hqUpscale` is enabled. Smaller PNG sizes are also resized from that HQ-processed working image in the current pipeline.

## WebP Export

Backend method: `IconExportService#createWebpVersions`.

Process:
1. Confirm WebP4j native support is available.
2. Decode the working icon image.
3. Resize it to every target raster size.
4. Encode each resized image through WebP4j.
5. Write each WebP into the ZIP under `webp/`.

Standard WebP sizes match PNG sizes:
- `32x32`
- `64x64`
- `128x128`
- `256x256`
- `512x512`

When `hqUpscale` is enabled, `1024x1024` is added.

If WebP4j is unavailable or a single WebP conversion fails, the service logs the failure and skips that WebP entry rather than failing the whole ZIP.

## ICO Export

Backend method: `IconExportService#createIcoVersion`.

Process:
1. Decode the working icon image.
2. Resize it into the configured ICO sizes.
3. Write a single ICO file containing multiple PNG-encoded image entries.
4. Store it under `ico/`.

ICO sizes:
- `32x32`
- `48x48`
- `64x64`
- `128x128`
- `256x256`

The ICO export uses the working icon image. If `hqUpscale` is enabled, the working image is the background-removed SeedVR/Ideogram result.

## SVG Wrapper Export

Backend method: `IconExportService#createSvgVersion`.

This is not the AI-vectorized SVG option. It creates a simple SVG file that embeds the original PNG as a base64 image.

Process:
1. Decode the original icon image.
2. Read its width and height.
3. Create an SVG document with an `<image>` element.
4. Embed the original PNG bytes as a `data:image/png;base64,...` URL.
5. Store it under `svg/`.

This option preserves the original raster look inside an SVG container. It is useful for workflows that expect an SVG file but does not make the image editable as vector paths.

## Vectorized SVG Export

Backend methods:
- `SvgVectorizationService#vectorizeImages`
- `SvgVectorizationService#vectorizeImage`
- `SvgVectorizationService#prepareImageForVectorization`
- `RecraftVectorizeModelService#vectorizeImageBlocking`
- `SvgVectorizationService#sanitizeVectorizedSvg`

This is the premium editable-vector option.

Process:
1. `IconExportService` calls `SvgVectorizationService` only when `vectorizeSvg` is enabled.
2. `prepareImageForVectorization` reads the raster icon and checks for alpha.
3. If the icon has transparency, transparent pixels are flattened onto white:
   - fully transparent pixels become white
   - partially transparent pixels are alpha-blended against white
   - opaque pixels stay unchanged
4. The prepared PNG is sent to the Recraft vectorization model.
5. The returned SVG is sanitized.
6. Sanitized vector SVGs are stored under `vectorized-svg/`.

The sanitizer looks for full-canvas background rectangle paths with dark or neutral background fills and changes those fills to `none`. This removes artificial backgrounds that may appear during vectorization.

Important tradeoff:
- Vectorized SVG files are editable and scalable.
- Small details can be lost because the AI vectorization model approximates the raster image as paths.

## HQ Upscaled Export

Backend methods:
- `IconExportService#processIconsForHighQuality`
- `IconExportService#prepareImageForHighQualityUpscale`
- `SeedVrUpscaleService#upscaleImage`
- `IdeoGramRemoveBackGroundService#removeBackground`

This is the premium high-resolution raster option. It no longer uses SVG vectorization.

Process for each icon:
1. Read the original raster icon.
2. Resize it to `256x256`.
3. Flatten transparency onto a white temporary background:
   - fully transparent pixels become white
   - partially transparent pixels are blended against white
   - opaque pixels remain unchanged
4. Send the prepared `256x256` image to SeedVR with upscale factor `4.0`.
5. SeedVR returns an upscaled `1024x1024` image.
6. Send the upscaled image to Ideogram remove-background.
7. Normalize the Ideogram result to `1024x1024` PNG.
8. Use that transparent result as the working icon data for PNG, WebP, and ICO export.

White is used instead of a vivid temporary background to avoid introducing high-contrast color artifacts into intentionally transparent or semi-transparent icon elements before upscaling.

Current behavior:
- HQ adds a `1024x1024` raster size.
- PNG and WebP exports include the `1024x1024` size when selected.
- ICO export still uses the normal ICO size set, but its source image is the HQ-processed icon.
- If HQ processing fails for an icon, that icon falls back to the original raster data so the ZIP can still be created.

## Premium Coin Costs

Premium export costs are handled in `IconExportController`.

For generated icon exports:
- Vectorized SVG costs `ceil(iconCount / 9.0)` coins.
- HQ Upscaled costs `ceil(iconCount / 9.0)` coins.
- Total premium cost is the sum of enabled premium options.

For gallery icon exports:
- The same cost formula is used.

The frontend export modal mirrors this calculation:
- Vectorized SVG: `1` coin per 9 icons.
- HQ Upscaled: `1` coin per 9 icons.

If the user does not have enough coins, the controller returns HTTP `402 Payment Required`.

## ZIP Layout

Depending on selected options, an icon export ZIP can contain:

```text
svg/
png/
webp/
ico/
vectorized-svg/
```

Folder meaning:
- `svg/`: SVG wrapper files embedding the original raster image.
- `png/`: resized PNG raster outputs.
- `webp/`: resized WebP raster outputs.
- `ico/`: multi-size ICO files.
- `vectorized-svg/`: editable AI-vectorized SVG files.

## Error Handling

The export pipeline is designed to produce a ZIP when possible.

Format-level behavior:
- PNG failures are logged per icon.
- WebP failures are logged and skipped per size.
- ICO failures are logged per icon.
- Vectorized SVG failures are surfaced through the vectorization flow.
- HQ failures fall back to the original raster icon for that icon.

Controller-level behavior:
- unauthenticated users receive HTTP `401`
- missing icons receive HTTP `404`
- insufficient coins receive HTTP `402`
- unexpected ZIP creation failures receive HTTP `500`
