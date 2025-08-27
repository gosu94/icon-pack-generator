package com.gosu.iconpackgenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FrontendBuilder {

    public static void main(String[] args) {
        try {
            buildFrontend();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error building or deploying frontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void buildFrontend() throws IOException, InterruptedException {
        String staticDir = "src/main/resources/static";
        String frontendOutDir = "frontend/out";

        // 1. Delete files in resources/static/ directory
        System.out.println("Deleting files in: " + staticDir);
        deleteFiles(staticDir);

        // 2. Run frontend build script
        System.out.println("Running frontend build script (npm run build)...");
        runNpmBuild("frontend");

        // 3. Move files from podcast-monitor-frontend/out/ to resources/static/
        System.out.println("Moving files from " + frontendOutDir + " to " + staticDir);
        moveFiles(frontendOutDir, staticDir);

        System.out.println("Frontend build and deployment completed successfully.");
    }

    private static void deleteFiles(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Directory not found or not a directory: " + directoryPath);
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                // Check if the file is the "pictures" directory
                if (file.isDirectory() && (file.getName().equals("icons") || file.getName().equals("images") || file.getName().equals("ffmpeg"))) {
                    System.out.println("Skipping deletion of 'pictures' directory: " + file.getAbsolutePath());
                    continue; // Skip to the next file
                }

                if (file.isDirectory()) {
                    deleteFiles(file.getAbsolutePath()); // Recursive call for subdirectories
                }
                if (!file.delete()) {
                    System.err.println("Failed to delete file: " + file.getAbsolutePath());
                    throw new IOException("Failed to delete file: " + file.getAbsolutePath());
                } else {
                    System.out.println("Deleted: " + file.getAbsolutePath());
                }
            }
        }
    }

    private static void runNpmBuild(String frontendDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
//        processBuilder.directory(new File(frontendDirectory));
        processBuilder.command("bash", "-c",
                "docker run --rm -v \"$(pwd):/app\" -w /app/frontend node:18-alpine sh -c \"yarn install && yarn build\"");

        Process process = processBuilder.start();

        // Print output to standard out
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        // Print errors to standard error
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String errorLine;
        while ((errorLine = errorReader.readLine()) != null) {
            System.err.println(errorLine);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("npm run build exited with code: " + exitCode);
        }
    }

    private static void moveFiles(String sourceDirectory, String targetDirectory) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        Path targetPath = Paths.get(targetDirectory);

        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IOException("Source directory does not exist: " + sourceDirectory);
        }

        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }

        Files.walk(sourcePath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(sourceFile -> {
                    Path targetFile = targetPath.resolve(sourcePath.relativize(sourceFile));
                    try {
                        Files.createDirectories(targetFile.getParent());
                        Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Moved: " + sourceFile + " to " + targetFile);
                    } catch (IOException e) {
                        System.err.println("Failed to move file: " + sourceFile + " to " + targetFile);
                        e.printStackTrace();
                    }
                });
    }
}