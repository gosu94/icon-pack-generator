package com.gosu.iconpackgenerator.domain.icons.service

import com.gosu.iconpackgenerator.singal.SignalMessageService
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Gpt2GridCroppingSpec extends Specification {

    private static final Path FIXTURE_PATH = Paths.get("src/test/resources/icons/gpt-2-background-removed.png")
    private static final Path OUTPUT_DIR = Paths.get("src/test/resources/images/output/gpt2-grid-cropping")

    ImageProcessingService imageProcessingService = new ImageProcessingService(
            Mock(SignalMessageService),
            Mock(BackgroundRemovalService),
            new IconCenteringService(),
            new GridBoundaryDetectionService(),
            new IconArtifactCleanupService()
    )

    def "crops GPT-2 background-removed 3x3 grid into consistent complete icons"() {
        given:
        byte[] imageData = Files.readAllBytes(FIXTURE_PATH)
        Files.createDirectories(OUTPUT_DIR)

        when: "running the production GPT-2 cropping path"
        List<String> centeredIcons = imageProcessingService.cropIconsFromGrid(
                imageData,
                9,
                true,
                ImageProcessingService.ICON_TARGET_SIZE,
                false,
                true
        )

        and: "running the same grid-boundary detection without centering for crop consistency checks"
        List<String> rawCrops = imageProcessingService.cropIconsFromGrid(
                imageData,
                9,
                false,
                0,
                false,
                false
        )

        then:
        centeredIcons.size() == 9
        rawCrops.size() == 9

        and: "production outputs are all valid standard-size icons"
        centeredIcons.eachWithIndex { String iconBase64, int index ->
            BufferedImage icon = decodeBase64Png(iconBase64)
            assert icon != null
            assert icon.width == ImageProcessingService.ICON_TARGET_SIZE
            assert icon.height == ImageProcessingService.ICON_TARGET_SIZE
            assert visibleBounds(icon) != null
            writeImage(icon, "centered-icon-${index + 1}.png")
        }

        and: "raw crops are valid and available for visual comparison"
        List<BufferedImage> rawImages = rawCrops.collect { decodeBase64Png(it) }
        rawImages.eachWithIndex { BufferedImage icon, int index ->
            assert icon != null
            assert visibleBounds(icon) != null
            writeImage(icon, "raw-crop-${index + 1}.png")
        }

        and: "centered outputs keep the visible tile inside the canvas"
        centeredIcons.collect { decodeBase64Png(it) }.eachWithIndex { BufferedImage icon, int index ->
            Rectangle bounds = visibleBounds(icon)
            assert bounds != null
            assert bounds.x > 0 : "centered icon ${index + 1} touches left canvas edge"
            assert bounds.y > 0 : "centered icon ${index + 1} touches top canvas edge"
            assert bounds.x + bounds.width < icon.width : "centered icon ${index + 1} touches right canvas edge"
            assert bounds.y + bounds.height < icon.height : "centered icon ${index + 1} touches bottom canvas edge"
            assert countNearWhiteOpaquePixels(icon) > 1_000 : "centered icon ${index + 1} lost its white tile/background content"
        }
    }

    def "centering keeps white tile content when image background is transparent"() {
        given:
        BufferedImage tileIcon = createTransparentWhiteTileIcon()

        when:
        BufferedImage centered = imageProcessingService.centerIcon(tileIcon, ImageProcessingService.ICON_TARGET_SIZE)

        then:
        centered.width == ImageProcessingService.ICON_TARGET_SIZE
        centered.height == ImageProcessingService.ICON_TARGET_SIZE

        and: "the white rounded tile is still present after centering"
        countNearWhiteOpaquePixels(centered) > 20_000

        and: "the full tile, not only the blue foreground mark, drives content bounds"
        Rectangle bounds = visibleBounds(centered)
        bounds.width > 180
        bounds.height > 180

        cleanup:
        Files.createDirectories(OUTPUT_DIR)
        writeImage(tileIcon, "synthetic-white-tile-source.png")
        writeImage(centered, "synthetic-white-tile-centered.png")
    }

    private static BufferedImage decodeBase64Png(String base64) {
        byte[] bytes = Base64.decoder.decode(base64)
        return ImageIO.read(new ByteArrayInputStream(bytes))
    }

    private static Rectangle visibleBounds(BufferedImage image) {
        int minX = image.width
        int minY = image.height
        int maxX = -1
        int maxY = -1

        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                int argb = image.getRGB(x, y)
                int alpha = (argb >> 24) & 0xFF
                if (alpha <= 10) {
                    continue
                }

                minX = Math.min(minX, x)
                minY = Math.min(minY, y)
                maxX = Math.max(maxX, x)
                maxY = Math.max(maxY, y)
            }
        }

        if (maxX < minX || maxY < minY) {
            return null
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private static int countNearWhiteOpaquePixels(BufferedImage image) {
        int count = 0
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                int argb = image.getRGB(x, y)
                int alpha = (argb >> 24) & 0xFF
                int red = (argb >> 16) & 0xFF
                int green = (argb >> 8) & 0xFF
                int blue = argb & 0xFF
                if (alpha > 200 && red > 240 && green > 240 && blue > 240) {
                    count++
                }
            }
        }
        return count
    }

    private static BufferedImage createTransparentWhiteTileIcon() {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB)
        Graphics2D graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setComposite(AlphaComposite.Clear)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.setComposite(AlphaComposite.SrcOver)

            graphics.setColor(Color.WHITE)
            graphics.fillRoundRect(50, 50, 200, 200, 40, 40)

            graphics.setColor(new Color(0, 110, 220))
            graphics.fillRoundRect(120, 120, 60, 60, 16, 16)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private static void writeImage(BufferedImage image, String fileName) {
        ImageIO.write(image, "png", OUTPUT_DIR.resolve(fileName).toFile())
    }
}
