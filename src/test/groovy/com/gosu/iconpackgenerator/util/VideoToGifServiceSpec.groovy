package com.gosu.iconpackgenerator.util

import com.gosu.iconpackgenerator.IconPackGeneratorApplication
import com.gosu.iconpackgenerator.config.TestSecurityConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Shared

import javax.imageio.ImageIO
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@SpringBootTest(classes = [IconPackGeneratorApplication])
@ActiveProfiles("test")
@ContextConfiguration(classes = [TestSecurityConfig])
class VideoToGifServiceSpec extends Specification {

    @Autowired
    VideoToGifService videoToGifService

    @Shared
    File testVideoFile

    @Shared
    Path outputDir

    def setupSpec() {
        // Load test video from resources
        def videoResource = getClass().getResourceAsStream("/videos/test_video.mp4")
        if (videoResource == null) {
            throw new RuntimeException("Test video not found: /videos/test_video.mp4")
        }

        // Create temporary file for testing
        testVideoFile = File.createTempFile("test_video_", ".mp4")
        testVideoFile.deleteOnExit()
        videoResource.withStream { input ->
            testVideoFile.withOutputStream { output ->
                output << input
            }
        }

        // Create output directory for test results
        outputDir = Paths.get("src/test/resources/videos/output")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }
    }

    def "should convert MP4 video file to GIF with default parameters"() {
        given: "A test video file"
        assert testVideoFile != null
        assert testVideoFile.exists()
        assert testVideoFile.length() > 0

        when: "Converting video to GIF with default parameters"
        byte[] gifData = videoToGifService.convertMp4ToGif(testVideoFile)

        then: "GIF data is generated"
        gifData != null
        gifData.length > 0

        and: "GIF data starts with GIF signature"
        // GIF files start with "GIF87a" or "GIF89a"
        String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
        gifHeader.startsWith("GIF")

        and: "Save GIF for visual verification"
        Path outputGif = outputDir.resolve("test_video_default.gif")
        Files.write(outputGif, gifData)
        println "Saved GIF: ${outputGif.toAbsolutePath()} (${gifData.length} bytes)"
    }

    def "should convert MP4 video file to GIF with custom parameters"() {
        given: "A test video file and custom parameters"
        int fps = 15
        int scaleWidth = 320

        when: "Converting video to GIF with custom parameters"
        byte[] gifData = videoToGifService.convertMp4ToGif(testVideoFile, fps, scaleWidth)

        then: "GIF data is generated"
        gifData != null
        gifData.length > 0

        and: "GIF data starts with GIF signature"
        String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
        gifHeader.startsWith("GIF")

        and: "Save GIF for visual verification"
        Path outputGif = outputDir.resolve("test_video_custom_${fps}fps_${scaleWidth}px.gif")
        Files.write(outputGif, gifData)
        println "Saved GIF: ${outputGif.toAbsolutePath()} (${gifData.length} bytes)"
    }

    def "should convert MP4 video from InputStream to GIF"() {
        given: "A video input stream"
        def videoResource = getClass().getResourceAsStream("/videos/test_video.mp4")
        assert videoResource != null

        when: "Converting video from InputStream to GIF"
        byte[] gifData = videoToGifService.convertMp4ToGif(videoResource)

        then: "GIF data is generated"
        gifData != null
        gifData.length > 0

        and: "GIF data starts with GIF signature"
        String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
        gifHeader.startsWith("GIF")

        and: "Save GIF for visual verification"
        Path outputGif = outputDir.resolve("test_video_from_stream.gif")
        Files.write(outputGif, gifData)
        println "Saved GIF: ${outputGif.toAbsolutePath()} (${gifData.length} bytes)"
    }

    def "should convert MP4 video from byte array to GIF"() {
        given: "Video data as byte array"
        byte[] videoData = Files.readAllBytes(testVideoFile.toPath())
        assert videoData != null
        assert videoData.length > 0

        when: "Converting video from byte array to GIF"
        byte[] gifData = videoToGifService.convertMp4ToGif(videoData)

        then: "GIF data is generated"
        gifData != null
        gifData.length > 0

        and: "GIF data starts with GIF signature"
        String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
        gifHeader.startsWith("GIF")

        and: "Save GIF for visual verification"
        Path outputGif = outputDir.resolve("test_video_from_bytes.gif")
        Files.write(outputGif, gifData)
        println "Saved GIF: ${outputGif.toAbsolutePath()} (${gifData.length} bytes)"
    }

    def "should convert MP4 video from byte array with custom parameters"() {
        given: "Video data as byte array and custom parameters"
        byte[] videoData = Files.readAllBytes(testVideoFile.toPath())
        int fps = 12
        int scaleWidth = 480

        when: "Converting video from byte array to GIF with custom parameters"
        byte[] gifData = videoToGifService.convertMp4ToGif(videoData, fps, scaleWidth)

        then: "GIF data is generated"
        gifData != null
        gifData.length > 0

        and: "GIF data starts with GIF signature"
        String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
        gifHeader.startsWith("GIF")

        and: "Save GIF for visual verification"
        Path outputGif = outputDir.resolve("test_video_from_bytes_custom_${fps}fps_${scaleWidth}px.gif")
        Files.write(outputGif, gifData)
        println "Saved GIF: ${outputGif.toAbsolutePath()} (${gifData.length} bytes)"
    }

    def "should throw exception when video file does not exist"() {
        given: "A non-existent video file"
        File nonExistentFile = new File("non_existent_video.mp4")

        when: "Converting non-existent video to GIF"
        videoToGifService.convertMp4ToGif(nonExistentFile)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should throw exception when video input stream is null"() {
        when: "Converting null input stream to GIF"
        videoToGifService.convertMp4ToGif((InputStream) null)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should throw exception when video byte array is null"() {
        when: "Converting null byte array to GIF"
        videoToGifService.convertMp4ToGif((byte[]) null)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should throw exception when video byte array is empty"() {
        given: "An empty byte array"
        byte[] emptyData = new byte[0]

        when: "Converting empty byte array to GIF"
        videoToGifService.convertMp4ToGif(emptyData)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should handle different FPS values correctly"() {
        given: "Different FPS values to test"
        def fpsValues = [5, 10, 15, 20]

        expect: "Each FPS value produces valid GIF"
        fpsValues.each { fps ->
            byte[] gifData = videoToGifService.convertMp4ToGif(testVideoFile, fps, 320)
            assert gifData != null
            assert gifData.length > 0
            String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
            assert gifHeader.startsWith("GIF")
            
            Path outputGif = outputDir.resolve("test_video_${fps}fps.gif")
            Files.write(outputGif, gifData)
            println "Saved GIF with ${fps} fps: ${outputGif.toAbsolutePath()}"
        }
    }

    def "should handle different scale widths correctly"() {
        given: "Different scale widths to test"
        def scaleWidths = [160, 320, 640, 1280]

        expect: "Each scale width produces valid GIF"
        scaleWidths.each { scaleWidth ->
            byte[] gifData = videoToGifService.convertMp4ToGif(testVideoFile, 10, scaleWidth)
            assert gifData != null
            assert gifData.length > 0
            String gifHeader = new String(gifData, 0, Math.min(6, gifData.length))
            assert gifHeader.startsWith("GIF")
            
            Path outputGif = outputDir.resolve("test_video_${scaleWidth}px.gif")
            Files.write(outputGif, gifData)
            println "Saved GIF with ${scaleWidth}px width: ${outputGif.toAbsolutePath()}"
        }
    }

    def cleanupSpec() {
        println "Test completed. Check output GIFs in: ${outputDir.toAbsolutePath()}"
        println "Generated GIFs for visual verification of video to GIF conversion functionality."
    }
}

