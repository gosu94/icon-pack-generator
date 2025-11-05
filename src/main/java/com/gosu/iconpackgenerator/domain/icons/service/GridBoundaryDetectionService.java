package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class GridBoundaryDetectionService {

    public GridBounds detectGridBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        log.debug("Detecting intelligent grid bounds for {}x{} image", width, height);

        BoundaryResult xResult = detectTransparentBoundariesWithQuality(image, true);
        BoundaryResult yResult = detectTransparentBoundariesWithQuality(image, false);

        int[] xBoundaries = xResult.boundaries;
        int[] yBoundaries = yResult.boundaries;
        boolean xHasPerfectTransparency = xResult.hasPerfectTransparency;
        boolean yHasPerfectTransparency = yResult.hasPerfectTransparency;

        if (xBoundaries == null) {
            xBoundaries = detectContentBoundaries(image, true);
            log.debug("No transparent x-boundaries found, falling back to content detection");
        } else {
            log.debug("Using transparent x boundaries: {} (perfect: {})",
                    Arrays.toString(xBoundaries), xHasPerfectTransparency);
        }

        if (yBoundaries == null) {
            yBoundaries = detectContentBoundaries(image, false);
            log.debug("No transparent y-boundaries found, falling back to content detection");
        } else {
            log.debug("Using transparent y boundaries: {} (perfect: {})",
                    Arrays.toString(yBoundaries), yHasPerfectTransparency);
        }

        if (xBoundaries == null) {
            xBoundaries = calculateImprovedDivision(width, 3);
            log.debug("Using improved division for x-axis: {}", Arrays.toString(xBoundaries));
        }

        if (yBoundaries == null) {
            yBoundaries = calculateImprovedDivision(height, 3);
            log.debug("Using improved division for y-axis: {}", Arrays.toString(yBoundaries));
        }

        int[] bufferedXBoundaries;
        int[] bufferedYBoundaries;

        if (xHasPerfectTransparency) {
            log.debug("SKIPPING all buffering for perfect transparent X boundaries");
            bufferedXBoundaries = xBoundaries;
        } else {
            boolean hasHighQualityX = false;
            if (xBoundaries != null && xBoundaries.length == 4) {
                int[] xSections = {xBoundaries[1] - xBoundaries[0], xBoundaries[2] - xBoundaries[1], xBoundaries[3] - xBoundaries[2]};
                int idealSection = width / 3;
                boolean xHasGoodSpacing = true;
                for (int section : xSections) {
                    double deviation = Math.abs(section - idealSection) / (double) idealSection;
                    if (deviation > 0.1) {
                        xHasGoodSpacing = false;
                        break;
                    }
                }
                hasHighQualityX = xHasGoodSpacing;
            }
            bufferedXBoundaries = applySmartBuffer(xBoundaries, width, true, hasHighQualityX);
        }

        if (yHasPerfectTransparency) {
            log.debug("SKIPPING all buffering for perfect transparent Y boundaries");
            bufferedYBoundaries = yBoundaries;
        } else {
            boolean hasHighQualityY = false;
            if (yBoundaries != null && yBoundaries.length == 4) {
                int[] ySections = {yBoundaries[1] - yBoundaries[0], yBoundaries[2] - yBoundaries[1], yBoundaries[3] - yBoundaries[2]};
                int idealSection = height / 3;
                boolean yHasGoodSpacing = true;
                for (int section : ySections) {
                    double deviation = Math.abs(section - idealSection) / (double) idealSection;
                    if (deviation > 0.1) {
                        yHasGoodSpacing = false;
                        break;
                    }
                }
                hasHighQualityY = yHasGoodSpacing;
            }
            bufferedYBoundaries = applySmartBuffer(yBoundaries, height, false, hasHighQualityY);
        }

        log.debug("Buffering results: X perfect={}, Y perfect={}", xHasPerfectTransparency, yHasPerfectTransparency);

        int[][] xBounds = new int[3][2];
        int[][] yBounds = new int[3][2];

        for (int i = 0; i < 3; i++) {
            xBounds[i][0] = bufferedXBoundaries[i];
            xBounds[i][1] = bufferedXBoundaries[i + 1];
            yBounds[i][0] = bufferedYBoundaries[i];
            yBounds[i][1] = bufferedYBoundaries[i + 1];
        }

        for (int i = 0; i < 3; i++) {
            log.debug("Column {}: x={} to {} (width={}) [buffered]", i, xBounds[i][0], xBounds[i][1], xBounds[i][1] - xBounds[i][0]);
            log.debug("Row {}: y={} to {} (height={}) [buffered]", i, yBounds[i][0], yBounds[i][1], yBounds[i][1] - yBounds[i][0]);
        }

        boolean overallPerfectTransparency = xHasPerfectTransparency && yHasPerfectTransparency;
        log.debug("Overall perfect transparency: {} (X: {}, Y: {})", overallPerfectTransparency, xHasPerfectTransparency, yHasPerfectTransparency);

        return new GridBounds(xBounds, yBounds, overallPerfectTransparency);
    }

    private int[] applySmartBuffer(int[] boundaries, int totalDimension, boolean isVertical, boolean hasHighQualityBoundaries) {
        int baseBuffer;
        if (hasHighQualityBoundaries) {
            baseBuffer = 0;
            log.debug("Using NO buffering (high-quality boundaries detected): base buffer = {}", baseBuffer);
        } else {
            baseBuffer = Math.max(2, Math.min(8, totalDimension / 150));
            log.debug("Using standard buffering (fallback boundaries): base buffer = {}", baseBuffer);
        }

        int[] bufferSizes = new int[4];
        bufferSizes[0] = 0;
        bufferSizes[1] = baseBuffer;
        if (hasHighQualityBoundaries) {
            bufferSizes[2] = baseBuffer;
        } else {
            bufferSizes[2] = isVertical ? baseBuffer : baseBuffer + 6;
        }
        bufferSizes[3] = 0;

        log.debug("Buffer sizes for {}: base={}, buffers=[{}, {}, {}, {}]",
                isVertical ? "vertical" : "horizontal", baseBuffer,
                bufferSizes[0], bufferSizes[1], bufferSizes[2], bufferSizes[3]);

        int[] bufferedBoundaries = new int[4];
        bufferedBoundaries[0] = boundaries[0];
        bufferedBoundaries[3] = boundaries[3];

        for (int i = 1; i <= 2; i++) {
            int originalPos = boundaries[i];
            int buffer = bufferSizes[i];

            int bufferedPos;
            if (i == 1) {
                bufferedPos = Math.max(bufferedBoundaries[0] + 1, originalPos - buffer);
            } else {
                if (!isVertical) {
                    bufferedPos = Math.max(bufferedBoundaries[1] + 1, originalPos - buffer);
                } else {
                    bufferedPos = Math.min(bufferedBoundaries[3] - 1, originalPos + buffer);
                }
            }

            int minDistance = Math.max(15, totalDimension / 30);

            if (i == 1) {
                bufferedPos = Math.min(bufferedPos, boundaries[2] - minDistance);
            } else {
                bufferedPos = Math.max(bufferedPos, bufferedBoundaries[1] + minDistance);
            }

            bufferedBoundaries[i] = bufferedPos;

            log.debug("Boundary {}: original={}, buffer={}, buffered={}",
                    i, originalPos, buffer, bufferedPos);
        }

        log.debug("Applied smart buffer to {} boundaries: {} -> {} (buffers: {})",
                isVertical ? "vertical" : "horizontal",
                Arrays.toString(boundaries),
                Arrays.toString(bufferedBoundaries),
                Arrays.toString(bufferSizes));

        for (int i = 0; i < 3; i++) {
            int originalSize = boundaries[i + 1] - boundaries[i];
            int bufferedSize = bufferedBoundaries[i + 1] - bufferedBoundaries[i];
            int sizeDiff = bufferedSize - originalSize;
            log.debug("Section {} ({}): {}px -> {}px ({}{}px)",
                    i, isVertical ? "col" : "row", originalSize, bufferedSize,
                    sizeDiff >= 0 ? "+" : "", sizeDiff);
        }

        return bufferedBoundaries;
    }

    private int[] detectTransparentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();

        log.debug("Detecting transparent {} boundaries for dimension {}",
                vertical ? "vertical" : "horizontal", dimension);

        int idealBoundary1 = dimension / 3;
        int idealBoundary2 = dimension * 2 / 3;
        int idealSectionSize = dimension / 3;

        int searchRange = Math.max(8, dimension / 20);

        int bestBoundary1 = findBestTransparentLine(image, vertical,
                Math.max(1, idealBoundary1 - searchRange),
                Math.min(dimension - 1, idealBoundary1 + searchRange));

        int minDistanceFromBoundary1 = Math.max(searchRange * 2, idealSectionSize - searchRange);
        int bestBoundary2 = findBestTransparentLine(image, vertical,
                Math.max(bestBoundary1 + minDistanceFromBoundary1, idealBoundary2 - searchRange),
                Math.min(dimension - 1, idealBoundary2 + searchRange));

        if (bestBoundary1 == -1 || bestBoundary2 == -1 || bestBoundary1 >= bestBoundary2) {
            log.debug("Could not find suitable transparent {} boundaries", vertical ? "vertical" : "horizontal");
            return null;
        }

        double transparencyThreshold = 0.9;
        double boundary1Transparency = calculateLineTransparency(image, vertical, bestBoundary1);
        double boundary2Transparency = calculateLineTransparency(image, vertical, bestBoundary2);

        boolean hasPerfectTransparency = boundary1Transparency >= 1.0 && boundary2Transparency >= 1.0;

        int section1Size = bestBoundary1 - 0;
        int section2Size = bestBoundary2 - bestBoundary1;
        int section3Size = dimension - bestBoundary2;

        double maxDeviationRatio = hasPerfectTransparency ? 0.25 : 0.05;

        double section1Deviation = Math.abs(section1Size - idealSectionSize) / (double) idealSectionSize;
        double section2Deviation = Math.abs(section2Size - idealSectionSize) / (double) idealSectionSize;
        double section3Deviation = Math.abs(section3Size - idealSectionSize) / (double) idealSectionSize;

        boolean spacingIsGood = section1Deviation <= maxDeviationRatio &&
                section2Deviation <= maxDeviationRatio &&
                section3Deviation <= maxDeviationRatio;

        if (!hasPerfectTransparency && (boundary1Transparency < transparencyThreshold || boundary2Transparency < transparencyThreshold)) {
            log.debug("Transparent {} boundaries don't meet quality threshold: {}%, {}% (need {}%)",
                    vertical ? "vertical" : "horizontal",
                    String.format("%.1f", boundary1Transparency * 100),
                    String.format("%.1f", boundary2Transparency * 100),
                    String.format("%.1f", transparencyThreshold * 100));
            return null;
        }

        if (!spacingIsGood) {
            String spacingType = hasPerfectTransparency ? "perfect transparency (relaxed spacing)" : "fallback boundaries";
            log.debug("Transparent {} boundaries ({}) have poor spacing: sections=[{}, {}, {}] (ideal={}), deviations=[{}%, {}%, {}%] (max={}%)",
                    vertical ? "vertical" : "horizontal", spacingType,
                    section1Size, section2Size, section3Size, idealSectionSize,
                    String.format("%.1f", section1Deviation * 100),
                    String.format("%.1f", section2Deviation * 100),
                    String.format("%.1f", section3Deviation * 100),
                    String.format("%.1f", maxDeviationRatio * 100));
            return null;
        }

        log.debug("Found excellent transparent {} boundaries at {} ({}% transparent) and {} ({}% transparent) with good spacing: sections=[{}, {}, {}]",
                vertical ? "vertical" : "horizontal",
                bestBoundary1, String.format("%.1f", boundary1Transparency * 100),
                bestBoundary2, String.format("%.1f", boundary2Transparency * 100),
                section1Size, section2Size, section3Size);

        return new int[]{0, bestBoundary1, bestBoundary2, dimension};
    }

    private BoundaryResult detectTransparentBoundariesWithQuality(BufferedImage image, boolean vertical) {
        int[] boundaries = detectTransparentBoundaries(image, vertical);

        if (boundaries == null) {
            return new BoundaryResult(null, false);
        }

        boolean hasPerfectTransparency = false;

        if (boundaries.length == 4) {
            boolean boundary1Perfect = isPerfectlyTransparentLine(image, vertical, boundaries[1]);
            boolean boundary2Perfect = isPerfectlyTransparentLine(image, vertical, boundaries[2]);

            hasPerfectTransparency = boundary1Perfect && boundary2Perfect;

            log.debug("Transparent {} boundaries perfect check: boundary1={}, boundary2={}, both perfect={}",
                    vertical ? "vertical" : "horizontal",
                    boundary1Perfect ? "PERFECT" : "partial",
                    boundary2Perfect ? "PERFECT" : "partial",
                    hasPerfectTransparency);
        }

        return new BoundaryResult(boundaries, hasPerfectTransparency);
    }

    private boolean isPerfectlyTransparentLine(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();

        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;

            if (alpha != 0) {
                return false;
            }
        }

        return true;
    }

    private int findBestTransparentLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        int bestPosition = findPerfectTransparentLine(image, vertical, startPos, endPos);

        if (bestPosition != -1) {
            log.debug("Found perfect transparent line at position {} with 100% transparency", bestPosition);
            return bestPosition;
        }

        log.debug("No 100% transparent lines found, falling back to 95% threshold");
        bestPosition = -1;
        double bestTransparency = 0.0;
        double minTransparency = 0.95;

        for (int pos = startPos; pos <= endPos; pos++) {
            double transparency = calculateLineTransparency(image, vertical, pos);

            if (transparency >= minTransparency && transparency > bestTransparency) {
                bestTransparency = transparency;
                bestPosition = pos;
            }
        }

        if (bestPosition != -1) {
            log.debug("Best transparent line at position {} with {}% transparency (fallback)",
                    bestPosition, String.format("%.1f", bestTransparency * 100));
        }

        return bestPosition;
    }

    private int findPerfectTransparentLine(BufferedImage image, boolean vertical, int startPos, int endPos) {
        int dimension = vertical ? image.getHeight() : image.getWidth();

        for (int pos = startPos; pos <= endPos; pos++) {
            boolean isPerfectlyTransparent = true;

            for (int i = 0; i < dimension; i++) {
                int rgb = vertical ? image.getRGB(pos, i) : image.getRGB(i, pos);
                int alpha = (rgb >> 24) & 0xFF;

                if (alpha != 0) {
                    isPerfectlyTransparent = false;
                    break;
                }
            }

            if (isPerfectlyTransparent) {
                log.debug("Found perfectly transparent {} line at position {}",
                        vertical ? "vertical" : "horizontal", pos);
                return pos;
            }
        }

        log.debug("No perfectly transparent {} lines found in range [{}, {}]",
                vertical ? "vertical" : "horizontal", startPos, endPos);
        return -1;
    }

    private double calculateLineTransparency(BufferedImage image, boolean vertical, int position) {
        int dimension = vertical ? image.getHeight() : image.getWidth();
        int transparentPixels = 0;
        int totalPixels = dimension;

        for (int i = 0; i < dimension; i++) {
            int rgb = vertical ? image.getRGB(position, i) : image.getRGB(i, position);
            int alpha = (rgb >> 24) & 0xFF;

            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;

            boolean isTransparent = alpha < 20;
            boolean isNearWhite = red > 240 && green > 240 && blue > 240 && alpha > 200;

            if (isTransparent || isNearWhite) {
                transparentPixels++;
            }
        }

        return (double) transparentPixels / totalPixels;
    }

    private int[] detectContentBoundaries(BufferedImage image, boolean vertical) {
        int dimension = vertical ? image.getWidth() : image.getHeight();
        int perpDimension = vertical ? image.getHeight() : image.getWidth();

        log.debug("Analyzing {} boundaries: dimension={}, perpDimension={}",
                vertical ? "vertical" : "horizontal", dimension, perpDimension);

        double[] contentDensity = new double[dimension];

        for (int pos = 0; pos < dimension; pos++) {
            int contentPixels = 0;

            for (int perpPos = 0; perpPos < perpDimension; perpPos++) {
                int rgb = vertical ? image.getRGB(pos, perpPos) : image.getRGB(perpPos, pos);

                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                boolean isTransparent = alpha < 50;
                boolean isWhite = red > 240 && green > 240 && blue > 240;
                boolean isBackground = isTransparent || isWhite;

                if (!isBackground) {
                    contentPixels++;
                }
            }

            contentDensity[pos] = (double) contentPixels / perpDimension;
        }

        List<Integer> gapCandidates = new ArrayList<>();

        double threshold = 0.05;

        for (int pos = 1; pos < dimension - 1; pos++) {
            if (contentDensity[pos] < threshold) {
                gapCandidates.add(pos);
            }
        }

        if (gapCandidates.size() < 2) {
            log.debug("No strict gaps found for {}, trying relaxed threshold in target ranges",
                    vertical ? "vertical" : "horizontal");

            double relaxedThreshold = 0.15;
            int targetBoundary1 = dimension / 3;
            int targetBoundary2 = dimension * 2 / 3;
            int searchRange = dimension / 10;

            for (int pos = Math.max(1, targetBoundary1 - searchRange);
                 pos < Math.min(dimension - 1, targetBoundary1 + searchRange); pos++) {
                if (contentDensity[pos] < relaxedThreshold) {
                    gapCandidates.add(pos);
                }
            }

            for (int pos = Math.max(1, targetBoundary2 - searchRange);
                 pos < Math.min(dimension - 1, targetBoundary2 + searchRange); pos++) {
                if (contentDensity[pos] < relaxedThreshold && !gapCandidates.contains(pos)) {
                    gapCandidates.add(pos);
                }
            }
        }

        if (gapCandidates.size() < 2) {
            log.debug("Could not detect natural {} boundaries - insufficient gap candidates ({})",
                    vertical ? "vertical" : "horizontal", gapCandidates.size());
            return null;
        }

        int targetBoundary1 = dimension / 3;
        int targetBoundary2 = dimension * 2 / 3;
        int idealSectionSize = dimension / 3;

        gapCandidates.sort((a, b) -> Double.compare(contentDensity[a], contentDensity[b]));

        int bestBoundary1 = -1;
        int bestBoundary2 = -1;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < gapCandidates.size() - 1; i++) {
            for (int j = i + 1; j < gapCandidates.size(); j++) {
                int candidate1 = gapCandidates.get(i);
                int candidate2 = gapCandidates.get(j);

                if (candidate1 > candidate2) {
                    int temp = candidate1;
                    candidate1 = candidate2;
                    candidate2 = temp;
                }

                int section1Size = candidate1 - 0;
                int section2Size = candidate2 - candidate1;
                int section3Size = dimension - candidate2;

                double dev1 = Math.abs(section1Size - idealSectionSize) / (double) idealSectionSize;
                double dev2 = Math.abs(section2Size - idealSectionSize) / (double) idealSectionSize;
                double dev3 = Math.abs(section3Size - idealSectionSize) / (double) idealSectionSize;

                double spacingScore = (dev1 + dev2 + dev3) / 3.0;
                double contentScore = (contentDensity[candidate1] + contentDensity[candidate2]) / 2.0;
                double totalScore = spacingScore * 0.7 + contentScore * 0.3;

                if (totalScore < bestScore && dev1 < 0.25 && dev2 < 0.25 && dev3 < 0.25) {
                    bestScore = totalScore;
                    bestBoundary1 = candidate1;
                    bestBoundary2 = candidate2;
                }
            }
        }

        if (bestBoundary1 == -1 || bestBoundary2 == -1 || bestBoundary1 >= bestBoundary2) {
            log.debug("Could not find good {} boundaries with acceptable spacing from {} candidates",
                    vertical ? "vertical" : "horizontal", gapCandidates.size());
            return null;
        }

        int section1Size = bestBoundary1 - 0;
        int section2Size = bestBoundary2 - bestBoundary1;
        int section3Size = dimension - bestBoundary2;

        log.debug("Detected natural {} boundaries at {} (content: {}%) and {} (content: {}%) for dimension {} with sections=[{}, {}, {}]",
                vertical ? "vertical" : "horizontal",
                bestBoundary1, String.format("%.1f", contentDensity[bestBoundary1] * 100),
                bestBoundary2, String.format("%.1f", contentDensity[bestBoundary2] * 100),
                dimension, section1Size, section2Size, section3Size);

        return new int[]{0, bestBoundary1, bestBoundary2, dimension};
    }

    private int[] calculateImprovedDivision(int totalSize, int divisions) {
        int baseSize = totalSize / divisions;
        int remainder = totalSize % divisions;

        log.debug("Calculating improved division: {} pixels / {} divisions = {} base + {} remainder",
                totalSize, divisions, baseSize, remainder);

        int[] boundaries = new int[divisions + 1];
        boundaries[0] = 0;

        for (int i = 1; i <= divisions; i++) {
            int extraPixel = (i <= remainder) ? 1 : 0;
            boundaries[i] = boundaries[i - 1] + baseSize + extraPixel;
        }

        boundaries[divisions] = totalSize;

        return boundaries;
    }

    public static class GridBounds {
        private final int[][] xBounds;
        private final int[][] yBounds;
        private final boolean hasPerfectTransparency;

        public GridBounds(int[][] xBounds, int[][] yBounds, boolean hasPerfectTransparency) {
            this.xBounds = xBounds;
            this.yBounds = yBounds;
            this.hasPerfectTransparency = hasPerfectTransparency;
        }

        public Rectangle getIconRectangle(int row, int col) {
            int x = xBounds[col][0];
            int y = yBounds[row][0];
            int width = xBounds[col][1] - xBounds[col][0];
            int height = yBounds[row][1] - yBounds[row][0];
            return new Rectangle(x, y, width, height);
        }

        public boolean hasPerfectTransparency() {
            return hasPerfectTransparency;
        }
    }

    private static class BoundaryResult {
        private final int[] boundaries;
        private final boolean hasPerfectTransparency;

        private BoundaryResult(int[] boundaries, boolean hasPerfectTransparency) {
            this.boundaries = boundaries;
            this.hasPerfectTransparency = hasPerfectTransparency;
        }
    }
}
