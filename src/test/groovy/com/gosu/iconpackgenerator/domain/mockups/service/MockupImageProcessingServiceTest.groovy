package com.gosu.iconpackgenerator.domain.mockups.service

import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

class MockupImageProcessingServiceTest extends Specification {

    MockupImageProcessingService service = new MockupImageProcessingService()

    def "extracts UI components from available mockup images"() {
        given:
        Path mockupDir = Path.of("src/test/resources/mockups")
        assert Files.exists(mockupDir) : "Mockup directory does not exist: ${mockupDir}"

        Path outputDir = mockupDir.resolve("output")
        Files.createDirectories(outputDir)
        Files.list(outputDir).withCloseable { stream ->
            stream.forEach { Files.deleteIfExists(it) }
        }

        List<Path> mockupFiles = []
        Files.newDirectoryStream(mockupDir, "*.png").withCloseable { stream ->
            stream.each { mockupFiles << it }
        }
        assert !mockupFiles.isEmpty() : "No mockup images available under ${mockupDir}"

        when:
        def extractionResults = mockupFiles.collect { Path mockup ->
            BufferedImage image = ImageIO.read(mockup.toFile())
            assert image != null : "Unable to read mockup image ${mockup.fileName}"

            def components = service.extractComponentsFromMockup(image)
            components.eachWithIndex { BufferedImage component, int index ->
                String basename = mockup.fileName.toString().replaceAll("(?i)\\.png\$", "")
                Path outputFile = outputDir.resolve(String.format("%s_component_%02d.png", basename, index + 1))
                ImageIO.write(component, "png", outputFile.toFile())
            }

            [file: mockup, components: components]
        }

        then:
        extractionResults.each { result ->
            assert !result.components.isEmpty() : "No UI components detected for ${result.file.fileName}"
            result.components.eachWithIndex { BufferedImage component, int index ->
                assert component.width >= 256 : "Component ${index + 1} of ${result.file.fileName} width ${component.width} < 256px"
                assert component.height >= 256 : "Component ${index + 1} of ${result.file.fileName} height ${component.height} < 256px"
            }
        }
    }
}
