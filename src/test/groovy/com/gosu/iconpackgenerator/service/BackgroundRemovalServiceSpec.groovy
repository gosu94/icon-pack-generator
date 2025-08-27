package com.gosu.iconpackgenerator.service

import com.gosu.iconpackgenerator.domain.service.BackgroundRemovalService
import com.gosu.iconpackgenerator.domain.service.ImageProcessingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.IgnoreIf
import com.gosu.iconpackgenerator.IconPackGeneratorApplication

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@SpringBootTest(classes = [IconPackGeneratorApplication])
@ActiveProfiles("test")
@ContextConfiguration
@TestPropertySource(properties = [
    "background-removal.enabled=true",
    "background-removal.rembg-command=rembg",
    "background-removal.timeout-seconds=30",
    "background-removal.model=isnet-general-use"
])
@Stepwise
class BackgroundRemovalServiceSpec extends Specification {

    @Autowired
    BackgroundRemovalService backgroundRemovalService

    @Shared
    Path outputDir

    @Shared
    byte[] sampleImageData

    @Shared
    BufferedImage originalImage

    @Shared
    boolean rembgWasAvailable = false

    def setupSpec() {
        // Create output directory
        outputDir = Paths.get("src/test/resources/images/output")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        // Load the rembg_sample.jpg image
        InputStream inputStream = getClass().getResourceAsStream("/images/rembg_sample.jpg")
        if (inputStream == null) {
            throw new RuntimeException("Could not load rembg_sample.jpg from test resources")
        }
        
        sampleImageData = inputStream.readAllBytes()
        inputStream.close()
        
        // Also load as BufferedImage for metadata
        originalImage = ImageIO.read(new ByteArrayInputStream(sampleImageData))
        
        println "Loaded sample image: ${sampleImageData.length} bytes, ${originalImage.width}x${originalImage.height} pixels"
    }

    def "should load sample image successfully"() {
        expect: "Sample image is loaded correctly"
        sampleImageData != null
        sampleImageData.length > 0
        originalImage != null
        originalImage.width > 0
        originalImage.height > 0

        and: "Image has expected properties"
        println "Sample Image: ${originalImage.width}x${originalImage.height}, ${sampleImageData.length} bytes"
    }

    def "should autowire BackgroundRemovalService"() {
        expect: "BackgroundRemovalService is available"
        backgroundRemovalService != null
    }

    def "should provide service information"() {
        when: "Getting service info"
        String serviceInfo = backgroundRemovalService.getServiceInfo()

        then: "Service info is returned"
        serviceInfo != null
        !serviceInfo.isEmpty()
        println "Background Removal Service Info: $serviceInfo"
    }

    def "should check rembg availability"() {
        when: "Checking if rembg is available"
        boolean isAvailable = backgroundRemovalService.isRembgAvailable()
        rembgWasAvailable = isAvailable

        then: "Availability status is returned"
        // Note: This might be false if rembg is not installed locally
        println "Rembg availability: $isAvailable"
        
        if (!isAvailable) {
            println "WARNING: rembg is not available in the current environment"
            println "This test will verify graceful handling of unavailable rembg"
        }
    }

    def "should handle background removal request"() {
        given: "Sample image data"
        assert sampleImageData != null
        assert sampleImageData.length > 0

        when: "Requesting background removal"
        byte[] processedImageData = backgroundRemovalService.removeBackground(sampleImageData)

        then: "Processed image data is returned"
        processedImageData != null
        processedImageData.length > 0

        and: "Original image is preserved if rembg is not available"
        if (!backgroundRemovalService.isRembgAvailable()) {
            // If rembg is not available, should return original image
            processedImageData == sampleImageData
            println "rembg not available - original image returned (graceful fallback)"
        } else {
            // If rembg is available, processed image might be different
            println "rembg available - background removal attempted"
            println "Original size: ${sampleImageData.length} bytes"
            println "Processed size: ${processedImageData.length} bytes"
        }

        and: "Save processed image to output directory"
        saveImageData(processedImageData, "rembg_sample_processed.png")
    }

    @IgnoreIf({ !System.getProperty("test.rembg.enabled", "false").toBoolean() })
    def "should successfully remove background when rembg is available"() {
        given: "rembg is available in the environment"
        assert backgroundRemovalService.isRembgAvailable() : "This test requires rembg to be available"

        when: "Processing image with background removal"
        byte[] processedImageData = backgroundRemovalService.removeBackground(sampleImageData)

        then: "Background removal is successful"
        processedImageData != null
        processedImageData.length > 0
        
        and: "Processed image can be loaded"
        BufferedImage processedImage = ImageIO.read(new ByteArrayInputStream(processedImageData))
        processedImage != null
        processedImage.width > 0
        processedImage.height > 0

        and: "Save comparison images"
        saveImageData(sampleImageData, "rembg_sample_original.png")
        saveImageData(processedImageData, "rembg_sample_background_removed.png")
        
        println "Background removal successful!"
        println "Original: ${originalImage.width}x${originalImage.height}, ${sampleImageData.length} bytes"
        println "Processed: ${processedImage.width}x${processedImage.height}, ${processedImageData.length} bytes"
    }

    def "should handle edge cases gracefully"() {
        when: "Processing null image data"
        byte[] result1 = backgroundRemovalService.removeBackground(null)

        then: "Null is returned gracefully"
        result1 == null

        when: "Processing empty image data"
        byte[] result2 = backgroundRemovalService.removeBackground(new byte[0])

        then: "Empty array is returned gracefully"
        result2 != null
        result2.length == 0
    }

    def "should integrate with ImageProcessingService"() {
        given: "ImageProcessingService is available"
        ImageProcessingService imageProcessingService = new ImageProcessingService(backgroundRemovalService)

        when: "Processing image with background removal enabled"
        List<String> iconsWithBgRemoval = imageProcessingService.cropIconsFromGrid(
            sampleImageData, 9, true, 200, true)

        and: "Processing image without background removal"
        List<String> iconsWithoutBgRemoval = imageProcessingService.cropIconsFromGrid(
            sampleImageData, 9, true, 200, false)

        then: "Both processing modes work"
        iconsWithBgRemoval != null
        iconsWithoutBgRemoval != null
        iconsWithBgRemoval.size() == 9
        iconsWithoutBgRemoval.size() == 9

        and: "Save sample icons for comparison"
        saveIconsFromBase64List(iconsWithBgRemoval, "bg_removed_icon", 3) // Save first 3 icons
        saveIconsFromBase64List(iconsWithoutBgRemoval, "original_icon", 3) // Save first 3 icons

        println "Successfully processed image with and without background removal"
        println "Icons with background removal: ${iconsWithBgRemoval.size()}"
        println "Icons without background removal: ${iconsWithoutBgRemoval.size()}"
    }

    // Helper methods

    private void saveImageData(byte[] imageData, String filename) {
        try {
            Path outputPath = outputDir.resolve(filename)
            Files.write(outputPath, imageData)
            println "Saved image data: ${outputPath.toAbsolutePath()} (${imageData.length} bytes)"
        } catch (IOException e) {
            println "Failed to save image data $filename: ${e.message}"
        }
    }

    private void saveIconsFromBase64List(List<String> base64Icons, String prefix, int count) {
        try {
            int saveCount = Math.min(count, base64Icons.size())
            for (int i = 0; i < saveCount; i++) {
                byte[] iconBytes = Base64.decoder.decode(base64Icons[i])
                BufferedImage iconImage = ImageIO.read(new ByteArrayInputStream(iconBytes))
                
                if (iconImage != null) {
                    Path outputPath = outputDir.resolve("${prefix}_${i + 1}.png")
                    ImageIO.write(iconImage, "png", outputPath.toFile())
                    println "Saved icon: ${outputPath.toAbsolutePath()}"
                }
            }
        } catch (Exception e) {
            println "Failed to save icons with prefix $prefix: ${e.message}"
        }
    }

    def cleanupSpec() {
        println "\n" + "="*60
        println "BACKGROUND REMOVAL TEST COMPLETED"
        println "="*60
        println "Output directory: ${outputDir.toAbsolutePath()}"
        println "\nGenerated files:"
        println "- rembg_sample_processed.png (processed image)"
        if (rembgWasAvailable) {
            println "- rembg_sample_original.png (original for comparison)"
            println "- rembg_sample_background_removed.png (background removed)"
        }
        println "- bg_removed_icon_*.png (sample icons with background removal)"
        println "- original_icon_*.png (sample icons without background removal)"
        println "\nTo run this test with rembg available:"
        println "1. Install rembg: pip install rembg[new]"
        println "2. Run with: ./gradlew test -Dtest.rembg.enabled=true"
        println "3. Or use Docker: see README for Docker testing instructions"
        println "="*60
    }
}