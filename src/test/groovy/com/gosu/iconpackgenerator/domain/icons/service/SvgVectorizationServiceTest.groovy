package com.gosu.iconpackgenerator.domain.icons.service

import com.gosu.iconpackgenerator.domain.ai.RecraftVectorizeModelService
import com.gosu.iconpackgenerator.domain.vectorization.SvgVectorizationService
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class SvgVectorizationServiceTest extends Specification {

    private static final Path ICONS_DIR = Paths.get("src/test/resources/icons")
    private static final Path OUTPUT_DIR = ICONS_DIR.resolve("output")

    private static List<Path> listIconFiles(String globPattern) {
        List<Path> result = []
        Files.newDirectoryStream(ICONS_DIR, globPattern).withCloseable { stream ->
            stream.each { result << it }
        }
        return result
    }

    private static void writeBytes(Path path, byte[] data) {
        Files.createDirectories(path.getParent())
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    def "prepareIconForVectorization replaces transparent pixels in PNG fixtures"() {
        given:
        def service = new SvgVectorizationService(Mock(RecraftVectorizeModelService))
        List<Path> pngFiles = listIconFiles("*.png")
        assert !pngFiles.isEmpty()

        when:
        Files.createDirectories(OUTPUT_DIR)
        List<BufferedImage> processedImages = pngFiles.collect { Path path ->
            byte[] input = Files.readAllBytes(path)
            byte[] output = service.prepareImageForVectorization(input, path.fileName.toString())
            String fileName = path.fileName.toString().replaceAll("(?i)\\.png\$", "")
            Path outPath = OUTPUT_DIR.resolve("${fileName}_prepared.png")
            writeBytes(outPath, output)
            ImageIO.read(new ByteArrayInputStream(output))
        }

        then:
        processedImages.each { BufferedImage image ->
            assert image != null
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = (image.getRGB(x, y) >> 24) & 0xFF
                    assert alpha == 255
                }
            }
        }
    }

    def "sanitizeVectorizedSvg removes background rectangles from SVG fixtures"() {
        given:
        def service = new SvgVectorizationService(Mock(RecraftVectorizeModelService))
        List<Path> svgFiles = listIconFiles("*.svg")
        assert !svgFiles.isEmpty()

        when:
        Files.createDirectories(OUTPUT_DIR)
        List<String> sanitizedContents = svgFiles.collect { Path path ->
            byte[] input = Files.readAllBytes(path)
            byte[] sanitized = service.sanitizeVectorizedSvg(input, path.fileName.toString())
            String fileName = path.fileName.toString().replaceAll("(?i)\\.svg\$", "")
            Path outPath = OUTPUT_DIR.resolve("${fileName}_sanitized.svg")
            writeBytes(outPath, sanitized)
            new String(sanitized, StandardCharsets.UTF_8)
        }

        then:
        sanitizedContents.each { String content ->
            assert content.contains('fill="none"')
            assert !content.contains('fill="#808080"')
            assert !content.contains('fill="rgb(16, 16, 16)"')
            assert !content.contains('fill="rgb(129, 128, 127)"')
            assert content.toLowerCase(Locale.ROOT).contains('<path')
        }
    }

}
