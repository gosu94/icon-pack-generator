package com.gosu.icon_pack_generator.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import com.gosu.icon_pack_generator.IconPackGeneratorApplication

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@SpringBootTest(classes = [IconPackGeneratorApplication])
@ContextConfiguration
@Stepwise
class ImageProcessingServiceSpec extends Specification {

    @Autowired
    ImageProcessingService imageProcessingService

    @Shared
    Path outputDir

    @Shared
    BufferedImage testImage1

    @Shared
    BufferedImage testImage2

    @Shared
    BufferedImage synthetic3x3Grid

    def setupSpec() {
        // Create output directory
        outputDir = Paths.get("src/test/resources/images/output")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        // Load test images
        testImage1 = loadTestImage("testsub1.jpg")
        testImage2 = loadTestImage("testsub2.jpg")

        // Create a synthetic 3x3 grid for controlled testing
        synthetic3x3Grid = createSynthetic3x3Grid()
        saveImage(synthetic3x3Grid, "synthetic_3x3_grid.png")
    }

    def "should successfully load test images from resources"() {
        expect: "Test images are loaded correctly"
        testImage1 != null
        testImage2 != null
        testImage1.width > 0
        testImage1.height > 0
        testImage2.width > 0
        testImage2.height > 0

        and: "Images have expected properties"
        println "Test Image 1: ${testImage1.width}x${testImage1.height}"
        println "Test Image 2: ${testImage2.width}x${testImage2.height}"
    }

    def "should create synthetic 3x3 grid for testing"() {
        expect: "Synthetic grid is created with correct dimensions"
        synthetic3x3Grid != null
        synthetic3x3Grid.width == 900  // 3x3 * 300px each
        synthetic3x3Grid.height == 900
        synthetic3x3Grid.width % 3 == 0
        synthetic3x3Grid.height % 3 == 0

        and: "Grid has distinct content in each cell"
        verifyGridCellsHaveDistinctContent(synthetic3x3Grid)
    }

    def "should center icon with default target size"() {
        given: "A test image with off-center content"
        BufferedImage offCenterImage = createOffCenterTestImage()
        saveImage(offCenterImage, "off_center_original.png")

        and: "ImageProcessingService is available"
        assert imageProcessingService != null : "ImageProcessingService should be autowired"

        when: "Centering the image with default size"
        BufferedImage centeredIcon = imageProcessingService.centerIcon(offCenterImage, 0)

        then: "Image is centered successfully"
        centeredIcon != null
        centeredIcon.width > 0
        centeredIcon.height > 0

        and: "Save centered result for visual verification"
        saveImage(centeredIcon, "off_center_centered_default.png")
        println "Centered image (default): ${centeredIcon.width}x${centeredIcon.height}"
    }

    def "should center icon with specific target size"() {
        given: "A test image"
        BufferedImage originalImage = testImage1
        int targetSize = 512

        when: "Centering with specific target size"
        BufferedImage centeredIcon = imageProcessingService.centerIcon(originalImage, targetSize)

        then: "Image has correct target dimensions"
        centeredIcon != null
        centeredIcon.width == targetSize
        centeredIcon.height == targetSize

        and: "Save result for visual verification"
        saveImage(centeredIcon, "test1_centered_512.png")
        println "Centered image (512px): ${centeredIcon.width}x${centeredIcon.height}"
    }

    def "should center icon and return base64 string"() {
        given: "A test image"
        BufferedImage originalImage = testImage2
        int targetSize = 256

        when: "Centering and converting to base64"
        String base64Result = imageProcessingService.centerIconToBase64(originalImage, targetSize)

        then: "Base64 string is generated"
        base64Result != null
        !base64Result.isEmpty()
        base64Result.length() > 100  // Should be substantial

        and: "Base64 can be decoded back to image"
        byte[] imageBytes = Base64.decoder.decode(base64Result)
        BufferedImage decodedImage = ImageIO.read(new ByteArrayInputStream(imageBytes))
        decodedImage != null
        decodedImage.width == targetSize
        decodedImage.height == targetSize

        and: "Save decoded result for verification"
        saveImage(decodedImage, "test2_centered_base64_decoded_256.png")
    }

    def "should crop 3x3 grid from synthetic image"() {
        given: "A synthetic 3x3 grid image"
        byte[] gridImageData = bufferedImageToByteArray(synthetic3x3Grid)

        when: "Cropping 9 icons from grid"
        List<String> croppedIcons = imageProcessingService.cropIconsFromGrid(gridImageData, 9)

        then: "9 icons are extracted"
        croppedIcons != null
        croppedIcons.size() == 9

        and: "Each icon is a valid base64 string"
        croppedIcons.each { iconBase64 ->
            assert iconBase64 != null
            assert !iconBase64.isEmpty()
            
            // Decode and verify each icon
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            assert iconImage != null
            assert iconImage.width > 0
            assert iconImage.height > 0
        }

        and: "Save cropped icons for visual verification"
        croppedIcons.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "synthetic_grid_icon_${index + 1}.png")
        }
        println "Successfully cropped ${croppedIcons.size()} icons from synthetic grid"
    }

    def "should crop and center icons from grid"() {
        given: "A synthetic 3x3 grid image"
        byte[] gridImageData = bufferedImageToByteArray(synthetic3x3Grid)
        int targetSize = 200

        when: "Cropping and centering 9 icons from grid"
        List<String> centeredIcons = imageProcessingService.cropIconsFromGrid(gridImageData, 9, true, targetSize)

        then: "9 centered icons are extracted"
        centeredIcons != null
        centeredIcons.size() == 9

        and: "Each centered icon has correct dimensions"
        centeredIcons.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            
            assert iconImage != null
            assert iconImage.width == targetSize
            assert iconImage.height == targetSize
            
            // Save for visual verification
            saveImage(iconImage, "synthetic_grid_centered_icon_${index + 1}_${targetSize}px.png")
        }
        println "Successfully cropped and centered ${centeredIcons.size()} icons to ${targetSize}px"
    }

    def "should handle mostly black image"() {
        given: "A mostly black image with some white content"
        BufferedImage blackImage = createMostlyBlackImage()
        saveImage(blackImage, "mostly_black_original.png")

        when: "Centering the black image"
        BufferedImage centeredBlack = imageProcessingService.centerIcon(blackImage, 300)

        then: "Black image is processed successfully"
        centeredBlack != null
        centeredBlack.width == 300
        centeredBlack.height == 300

        and: "Save result for verification"
        saveImage(centeredBlack, "mostly_black_centered_300.png")
        println "Successfully centered mostly black image"
    }

    def "should handle transparent image"() {
        given: "An image with transparent background and colored content"
        BufferedImage transparentImage = createTransparentImage()
        saveImage(transparentImage, "transparent_original.png")

        when: "Centering the transparent image"
        BufferedImage centeredTransparent = imageProcessingService.centerIcon(transparentImage, 400)

        then: "Transparent image is processed successfully"
        centeredTransparent != null
        centeredTransparent.width == 400
        centeredTransparent.height == 400

        and: "Save result for verification"
        saveImage(centeredTransparent, "transparent_centered_400.png")
        println "Successfully centered transparent image"
    }

    def "should handle edge case: null image"() {
        when: "Attempting to center null image"
        imageProcessingService.centerIcon(null, 100)

        then: "IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should handle edge case: empty image data"() {
        when: "Attempting to crop from empty data"
        imageProcessingService.cropIconsFromGrid(new byte[0], 9)

        then: "RuntimeException is thrown"
        thrown(RuntimeException)
    }

    def "should crop icons from real test images"() {
        given: "Real test images converted to byte arrays"
        byte[] testImageData1 = bufferedImageToByteArray(testImage1)
        byte[] testImageData2 = bufferedImageToByteArray(testImage2)

        when: "Cropping icons from test image 1"
        List<String> icons1 = imageProcessingService.cropIconsFromGrid(testImageData1, 9, true, 150)

        and: "Cropping icons from test image 2"
        List<String> icons2 = imageProcessingService.cropIconsFromGrid(testImageData2, 9, true, 150)

        then: "Icons are extracted from both images"
        icons1 != null && icons1.size() == 9
        icons2 != null && icons2.size() == 9

        and: "Save all cropped icons"
        icons1.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "testsub1_centered_icon_${index + 1}.png")
        }

        icons2.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "testsub2_centered_icon_${index + 1}.png")
        }

        println "Successfully cropped ${icons1.size() + icons2.size()} icons from real test images"
    }

    // Helper methods

    private BufferedImage loadTestImage(String filename) {
        InputStream inputStream = getClass().getResourceAsStream("/images/$filename")
        if (inputStream == null) {
            throw new RuntimeException("Could not load test image: $filename")
        }
        return ImageIO.read(inputStream)
    }

    private BufferedImage createSynthetic3x3Grid() {
        int cellSize = 300
        int gridSize = cellSize * 3
        BufferedImage grid = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB)
        Graphics2D g2d = grid.createGraphics()

        // Enable antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Colors for each cell
        Color[] colors = [
            Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.MAGENTA, Color.CYAN,
            Color.ORANGE, Color.PINK, Color.LIGHT_GRAY
        ]

        // Draw 3x3 grid with different content in each cell
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = col * cellSize
                int y = row * cellSize
                int cellIndex = row * 3 + col

                // Fill background
                g2d.setColor(Color.WHITE)
                g2d.fillRect(x, y, cellSize, cellSize)

                // Draw colored content
                g2d.setColor(colors[cellIndex])
                
                // Different shapes for each cell to make them distinctive
                switch (cellIndex) {
                    case 0: // Circle
                        g2d.fillOval(x + 50, y + 50, cellSize - 100, cellSize - 100)
                        break
                    case 1: // Rectangle
                        g2d.fillRect(x + 50, y + 50, cellSize - 100, cellSize - 100)
                        break
                    case 2: // Triangle
                        int[] xPoints = [x + (int)(cellSize/2), x + 50, x + cellSize - 50] as int[]
                        int[] yPoints = [y + 50, y + cellSize - 50, y + cellSize - 50] as int[]
                        g2d.fillPolygon(xPoints, yPoints, 3)
                        break
                    case 3: // Diamond
                        int[] xPoints2 = [x + (int)(cellSize/2), x + 50, x + (int)(cellSize/2), x + cellSize - 50] as int[]
                        int[] yPoints2 = [y + 50, y + (int)(cellSize/2), y + cellSize - 50, y + (int)(cellSize/2)] as int[]
                        g2d.fillPolygon(xPoints2, yPoints2, 4)
                        break
                    case 4: // Star (simplified)
                        g2d.fillOval(x + 100, y + 100, cellSize - 200, cellSize - 200)
                        g2d.fillRect(x + (int)(cellSize/2) - 10, y + 50, 20, cellSize - 100)
                        g2d.fillRect(x + 50, y + (int)(cellSize/2) - 10, cellSize - 100, 20)
                        break
                    case 5: // Cross
                        g2d.fillRect(x + (int)(cellSize/2) - 20, y + 50, 40, cellSize - 100)
                        g2d.fillRect(x + 50, y + (int)(cellSize/2) - 20, cellSize - 100, 40)
                        break
                    case 6: // Hexagon (simplified as octagon)
                        int[] xPoints3 = [x + 80, x + 120, x + 180, x + 220, x + 220, x + 180, x + 120, x + 80] as int[]
                        int[] yPoints3 = [y + 100, y + 70, y + 70, y + 100, y + 200, y + 230, y + 230, y + 200] as int[]
                        g2d.fillPolygon(xPoints3, yPoints3, 8)
                        break
                    case 7: // Concentric circles
                        g2d.fillOval(x + 50, y + 50, cellSize - 100, cellSize - 100)
                        g2d.setColor(Color.WHITE)
                        g2d.fillOval(x + 100, y + 100, cellSize - 200, cellSize - 200)
                        g2d.setColor(colors[cellIndex])
                        g2d.fillOval(x + 125, y + 125, cellSize - 250, cellSize - 250)
                        break
                    case 8: // Gradient effect (simplified)
                        for (int i = 0; i < cellSize - 100; i += 5) {
                            int alpha = (int)(255 * (1 - (double)i / (cellSize - 100)))
                            g2d.setColor(new Color(colors[cellIndex].red, colors[cellIndex].green, colors[cellIndex].blue, alpha))
                            g2d.fillOval(x + 50 + (int)(i/2), y + 50 + (int)(i/2), cellSize - 100 - i, cellSize - 100 - i)
                        }
                        break
                }

                // Add cell number for identification
                g2d.setColor(Color.BLACK)
                g2d.setFont(new Font("Arial", Font.BOLD, 24))
                g2d.drawString("${cellIndex + 1}", x + 10, y + 30)
            }
        }

        g2d.dispose()
        return grid
    }

    private BufferedImage createOffCenterTestImage() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        Graphics2D g2d = image.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // White background
        g2d.setColor(Color.WHITE)
        g2d.fillRect(0, 0, 400, 300)

        // Off-center blue circle
        g2d.setColor(Color.BLUE)
        g2d.fillOval(250, 50, 100, 100)  // Positioned in upper-right area

        g2d.dispose()
        return image
    }

    private BufferedImage createMostlyBlackImage() {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        Graphics2D g2d = image.createGraphics()

        // Black background
        g2d.setColor(Color.BLACK)
        g2d.fillRect(0, 0, 200, 200)

        // Small white content
        g2d.setColor(Color.WHITE)
        g2d.fillRect(80, 80, 40, 40)  // Small white square in center

        g2d.dispose()
        return image
    }

    private BufferedImage createTransparentImage() {
        BufferedImage image = new BufferedImage(250, 250, BufferedImage.TYPE_INT_ARGB)
        Graphics2D g2d = image.createGraphics()

        // Transparent background (default)
        g2d.setComposite(AlphaComposite.Clear)
        g2d.fillRect(0, 0, 250, 250)

        // Colored content
        g2d.setComposite(AlphaComposite.SrcOver)
        g2d.setColor(Color.RED)
        g2d.fillOval(50, 50, 150, 150)

        g2d.dispose()
        return image
    }

    private boolean verifyGridCellsHaveDistinctContent(BufferedImage grid) {
        int cellSize = grid.width / 3
        Set<Integer> centerPixelColors = new HashSet<>()

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int centerX = col * cellSize + (int)(cellSize / 2)
                int centerY = row * cellSize + (int)(cellSize / 2)
                int pixelColor = grid.getRGB(centerX, centerY)
                centerPixelColors.add(pixelColor)
            }
        }

        // Should have at least 5 different colors (some cells might have similar centers)
        return centerPixelColors.size() >= 5
    }

    private byte[] bufferedImageToByteArray(BufferedImage image) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            return outputStream.toByteArray()
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert BufferedImage to byte array", e)
        }
    }

    private void saveImage(BufferedImage image, String filename) {
        try {
            Path outputPath = outputDir.resolve(filename)
            ImageIO.write(image, "png", outputPath.toFile())
            println "Saved image: ${outputPath.toAbsolutePath()}"
        } catch (IOException e) {
            println "Failed to save image $filename: ${e.message}"
        }
    }

    def cleanupSpec() {
        println "Test completed. Check output images in: ${outputDir.toAbsolutePath()}"
        println "Generated images for visual verification of cropping and centering functionality."
    }
}
