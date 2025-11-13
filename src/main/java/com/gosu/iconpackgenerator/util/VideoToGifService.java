package com.gosu.iconpackgenerator.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service for converting MP4 video files to GIF format
 */
@Service
@Slf4j
public class VideoToGifService {

    private static final int DEFAULT_FPS = 30;
    private static final int DEFAULT_SCALE_WIDTH = 512;

    /**
     * Converts an MP4 video file to GIF format
     *
     * @param videoFile The MP4 video file to convert
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(File videoFile) throws IOException {
        return convertMp4ToGif(videoFile, DEFAULT_FPS, DEFAULT_SCALE_WIDTH);
    }

    /**
     * Converts an MP4 video file to GIF format with custom parameters
     *
     * @param videoFile The MP4 video file to convert
     * @param fps Frames per second for the output GIF (default: 10)
     * @param scaleWidth Target width for scaling (height will be calculated to maintain aspect ratio)
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(File videoFile, int fps, int scaleWidth) throws IOException {
        if (videoFile == null || !videoFile.exists()) {
            throw new IllegalArgumentException("Video file does not exist: " + videoFile);
        }

        log.info("Converting video to GIF: {}, fps: {}, scaleWidth: {}", videoFile.getName(), fps, scaleWidth);

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();

            int videoFps = (int) grabber.getFrameRate();
            int videoWidth = grabber.getImageWidth();
            int videoHeight = grabber.getImageHeight();
            int totalFrames = grabber.getLengthInFrames();

            log.debug("Video properties: {}x{}, fps: {}, total frames: {}", 
                    videoWidth, videoHeight, videoFps, totalFrames);

            // Calculate frame skip to achieve target FPS
            int frameSkip = Math.max(1, videoFps / fps);
            int scaleHeight = (int) ((double) scaleWidth / videoWidth * videoHeight);

            Java2DFrameConverter converter = new Java2DFrameConverter();
            
            // Collect frames first
            List<BufferedImage> frames = new ArrayList<>();
            int frameCount = 0;
            Frame frame;

            while ((frame = grabber.grabFrame()) != null) {
                if (frame.image == null) {
                    continue;
                }

                // Skip frames to achieve target FPS
                if (frameCount % frameSkip != 0) {
                    frameCount++;
                    continue;
                }

                BufferedImage image = converter.convert(frame);
                if (image == null) {
                    frameCount++;
                    continue;
                }

                // Scale image if needed
                if (image.getWidth() != scaleWidth || image.getHeight() != scaleHeight) {
                    image = scaleImage(image, scaleWidth, scaleHeight);
                }

                // Create a deep copy to avoid frame reuse issues
                BufferedImage frameCopy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = frameCopy.createGraphics();
                try {
                    g2d.drawImage(image, 0, 0, null);
                } finally {
                    g2d.dispose();
                }

                frames.add(frameCopy);
                frameCount++;

                // Limit number of frames to prevent very large GIFs
                if (frames.size() >= 150) {
                    log.warn("Reached maximum frame limit (150), stopping conversion");
                    break;
                }
            }

            // Create GIF from collected frames
            byte[] gifData = createAnimatedGif(frames, fps);
            log.info("Successfully converted video to GIF: {} bytes, {} frames", gifData.length, frames.size());
            return gifData;

        } catch (Exception e) {
            log.error("Error converting video to GIF: {}", videoFile.getName(), e);
            throw new IOException("Failed to convert video to GIF: " + e.getMessage(), e);
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    log.warn("Error closing frame grabber", e);
                }
            }
        }
    }

    /**
     * Converts an MP4 video file from InputStream to GIF format
     *
     * @param videoInputStream The input stream containing MP4 video data
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(InputStream videoInputStream) throws IOException {
        return convertMp4ToGif(videoInputStream, DEFAULT_FPS, DEFAULT_SCALE_WIDTH);
    }

    /**
     * Converts an MP4 video file from InputStream to GIF format with custom parameters
     *
     * @param videoInputStream The input stream containing MP4 video data
     * @param fps Frames per second for the output GIF
     * @param scaleWidth Target width for scaling
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(InputStream videoInputStream, int fps, int scaleWidth) throws IOException {
        if (videoInputStream == null) {
            throw new IllegalArgumentException("Video input stream cannot be null");
        }

        // Create temporary file for FFmpegFrameGrabber (it requires a file)
        Path tempFile = Files.createTempFile("video_", ".mp4");
        try {
            Files.copy(videoInputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return convertMp4ToGif(tempFile.toFile(), fps, scaleWidth);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile, e);
            }
        }
    }

    /**
     * Converts an MP4 video file from byte array to GIF format
     *
     * @param videoData The byte array containing MP4 video data
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(byte[] videoData) throws IOException {
        return convertMp4ToGif(videoData, DEFAULT_FPS, DEFAULT_SCALE_WIDTH);
    }

    /**
     * Converts an MP4 video file from byte array to GIF format with custom parameters
     *
     * @param videoData The byte array containing MP4 video data
     * @param fps Frames per second for the output GIF
     * @param scaleWidth Target width for scaling
     * @return Byte array containing the GIF data
     * @throws IOException if an error occurs during conversion
     */
    public byte[] convertMp4ToGif(byte[] videoData, int fps, int scaleWidth) throws IOException {
        if (videoData == null || videoData.length == 0) {
            throw new IllegalArgumentException("Video data cannot be null or empty");
        }

        try (InputStream inputStream = new java.io.ByteArrayInputStream(videoData)) {
            return convertMp4ToGif(inputStream, fps, scaleWidth);
        }
    }

    /**
     * Creates an animated GIF from a list of frames
     */
    private byte[] createAnimatedGif(List<BufferedImage> frames, int fps) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }

        ByteArrayOutputStream gifOutputStream = new ByteArrayOutputStream();
        
        // Get GIF writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) {
            throw new IOException("No GIF writer available");
        }
        ImageWriter writer = writers.next();

        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(gifOutputStream)) {
            writer.setOutput(imageOutputStream);
            
            // Calculate delay in hundredths of a second (1 second = 100)
            int delayTime = Math.max(1, 100 / fps);
            
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            
            // Create ImageTypeSpecifier from the first frame
            ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromBufferedImageType(frames.get(0).getType());
            
            // Write first frame with stream metadata
            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(writeParam);
            writer.prepareWriteSequence(streamMetadata);
            
            // Write first frame
            IIOMetadata firstFrameMetadata = writer.getDefaultImageMetadata(imageType, writeParam);
            configureFrameMetadata(firstFrameMetadata, delayTime);
            writer.writeToSequence(new javax.imageio.IIOImage(frames.get(0), null, firstFrameMetadata), writeParam);
            
            // Write remaining frames
            for (int i = 1; i < frames.size(); i++) {
                IIOMetadata frameMetadata = writer.getDefaultImageMetadata(imageType, writeParam);
                configureFrameMetadata(frameMetadata, delayTime);
                writer.writeToSequence(new javax.imageio.IIOImage(frames.get(i), null, frameMetadata), writeParam);
            }
            
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        return gifOutputStream.toByteArray();
    }

    /**
     * Configures GIF frame metadata with delay time
     */
    private void configureFrameMetadata(IIOMetadata metadata, int delayTime) {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        if (metaFormatName == null) {
            return;
        }

        try {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            graphicsControlExtensionNode.setAttribute("delayTime", String.valueOf(delayTime));
            graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
            graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
            graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
            
            metadata.setFromTree(metaFormatName, root);
        } catch (Exception e) {
            log.warn("Failed to configure frame metadata", e);
        }
    }

    /**
     * Gets a child node with the given name, creating it if it doesn't exist
     */
    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }

    /**
     * Scales a BufferedImage to the target dimensions
     */
    private BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = scaledImage.createGraphics();
        try {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return scaledImage;
    }
}
