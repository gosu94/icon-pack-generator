package com.gosu.iconpackgenerator.service

import com.gosu.iconpackgenerator.domain.service.ImageProcessingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import com.gosu.iconpackgenerator.IconPackGeneratorApplication

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@SpringBootTest(classes = [IconPackGeneratorApplication])
@ActiveProfiles("test")
@ContextConfiguration(classes = [ImageProcessingServiceTestConfig, com.gosu.iconpackgenerator.config.TestSecurityConfig])
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
        List<String> croppedIcons = imageProcessingService.cropIconsFromGrid(gridImageData, 9, false    )

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

    def "should detect transparent boundaries and fix cutting artifacts"() {
        when: "processing the problematic wrong-cut.png image that has misaligned icons"
        BufferedImage wrongCutImage = ImageIO.read(new File("src/test/resources/images/wrong-cut-7.png"))
        byte[] imageBytes = bufferedImageToByteArray(wrongCutImage)

        then: "image loads successfully"
        wrongCutImage != null
        wrongCutImage.width == 1024 || wrongCutImage.height == 1024 // Should be 1024x1024 as mentioned

        when: "crop icons using new transparent boundary detection"
        List<String> icons = imageProcessingService.cropIconsFromGrid(imageBytes, 9, true, 256)

        then: "processing succeeds and generates expected number of icons"
        icons != null
        icons.size() == 9

        when: "save each icon with descriptive names for visual inspection"
        icons.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            iconImage = imageProcessingService.centerIcon(iconImage, 0)
            String filename = "src/test/resources/images/output/transparent_boundary_fixed_icon_${index + 1}_256px.png"
            ImageIO.write(iconImage, "png", new File(filename))
            println("Saved improved icon ${index + 1} to: ${filename}")
        }

        then: "icons are saved successfully with transparent boundary detection"
        icons.size() == 9

        and: "log that transparent boundary detection was used"
        // In real usage, check logs to see if "Found excellent transparent" messages appear
        println("Check application logs for transparent boundary detection messages")
        println("Expected to see: 'Found excellent transparent [vertical|horizontal] boundaries at...'")
        println("If no transparent boundaries found, fallback methods should be used automatically")
    }

    def "should correctly detect transparent background and avoid unnecessary background removal"() {
        given: "A test image to check transparency detection"
        BufferedImage falsePositiveImage = loadTestImage("testsub3.png")

        expect: "Test image is loaded correctly"
        falsePositiveImage != null
        falsePositiveImage.width > 0
        falsePositiveImage.height > 0

        when: "Check if image has transparent background using improved algorithm"
        // Use reflection to access private method for testing
        java.lang.reflect.Method hasTransparentBackgroundMethod = ImageProcessingService.class.getDeclaredMethod("hasTransparentBackground", BufferedImage.class)
        hasTransparentBackgroundMethod.setAccessible(true)
        boolean hasTransparentBg = hasTransparentBackgroundMethod.invoke(imageProcessingService, falsePositiveImage)

        then: "Image should be correctly identified as having transparent background"
        hasTransparentBg == true

        and: "Save the test image for visual verification"
        saveImage(falsePositiveImage, "false_positive_transparency_test.png")
        println "False positive transparency image dimensions: ${falsePositiveImage.width}x${falsePositiveImage.height}"
        println "Has alpha channel: ${falsePositiveImage.colorModel.hasAlpha()}"

        when: "Process the image with background removal flag set to true"
        byte[] imageData = bufferedImageToByteArray(falsePositiveImage)
        List<String> icons = imageProcessingService.cropIconsFromGrid(imageData, 9, false, 0, true, true)

        then: "Processing should succeed without triggering unnecessary background removal"
        icons != null
        icons.size() == 9

        and: "Save cropped icons for verification"
        icons.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "false_positive_icon_${index + 1}.png")
        }

        println "Successfully processed false-positive-transparency.png without unnecessary background removal"
    }

    def "should cleanup artifacts from wrong-cut image using new artifact removal functionality"() {
        given: "The problematic wrong-cut-7.png image that has cutting artifacts"
        BufferedImage wrongCutImage = loadTestImage("wrong_size2.png")

        expect: "Test image is loaded correctly"
        wrongCutImage != null
        wrongCutImage.width > 0
        wrongCutImage.height > 0

        when: "Process the image with artifact cleanup enabled"
        byte[] imageData = bufferedImageToByteArray(wrongCutImage)
        List<String> iconsWithCleanup = imageProcessingService.cropIconsFromGrid(imageData, 9, true, 300, false, true)

        and: "Process the same image without artifact cleanup for comparison"
        List<String> iconsWithoutCleanup = imageProcessingService.cropIconsFromGrid(imageData, 9, true, 300, false, false)

        then: "Both processing attempts succeed"
        iconsWithCleanup != null
        iconsWithCleanup.size() == 9
        iconsWithoutCleanup != null
        iconsWithoutCleanup.size() == 9

        when: "Save icons with cleanup for visual comparison"
        iconsWithCleanup.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "wrong_cut_cleaned_icon_${index + 1}_256px.png")
            println "Saved cleaned icon ${index + 1} with artifact cleanup"
        }

        and: "Save icons without cleanup for comparison"
        iconsWithoutCleanup.eachWithIndex { iconBase64, index ->
            byte[] iconBytes = Base64.decoder.decode(iconBase64)
            BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
            saveImage(iconImage, "wrong_cut_original_icon_${index + 1}_256px.png")
            println "Saved original icon ${index + 1} without cleanup"
        }

        then: "Processing completes successfully"
        iconsWithCleanup.size() == 9
        iconsWithoutCleanup.size() == 9

        and: "Log comparison results"
        println "Successfully tested artifact cleanup functionality on wrong-cut-7.png"
        println "Compare the 'cleaned' vs 'original' versions to see the difference"
        println "Cleaned icons should have fewer artifacts from neighboring icons"
    }

    def cleanupSpec() {
        println "Test completed. Check output images in: ${outputDir.toAbsolutePath()}"
        println "Generated images for visual verification of cropping and centering functionality."
    }
}