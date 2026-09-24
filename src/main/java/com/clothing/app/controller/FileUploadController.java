package com.clothing.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private static final String UPLOAD_DIR = "uploads";
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final com.clothing.app.service.CloudinaryService cloudinaryService;
    private final com.clothing.app.repository.ProductRepository productRepository;

    public FileUploadController(com.clothing.app.service.CloudinaryService cloudinaryService,
                                com.clothing.app.repository.ProductRepository productRepository) {
        this.cloudinaryService = cloudinaryService;
        this.productRepository = productRepository;
    }

    @PostMapping("/sync-images-to-cloudinary")
    public ResponseEntity<?> syncImagesToCloudinary() {
        if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cloudinary is not configured."));
        }

        Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            return ResponseEntity.ok(Map.of("message", "No local uploads directory found.", "syncedCount", 0));
        }

        java.util.List<Map<String, String>> synced = new java.util.ArrayList<>();
        int productsUpdated = 0;

        try (var stream = Files.list(uploadPath)) {
            java.util.List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path p : files) {
                try {
                    Map<String, Object> uploadResult = cloudinaryService.uploadFile(p.toFile());
                    String secureUrl = (String) uploadResult.get("imageUrl");
                    String localPath = "/uploads/" + p.getFileName().toString();
                    synced.add(Map.of("file", p.getFileName().toString(), "cloudinaryUrl", secureUrl));

                    int updated = productRepository.updateImageUrl(localPath, secureUrl);
                    productsUpdated += updated;
                } catch (Exception ex) {
                    // Log and proceed with remaining files
                }
            }
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error reading uploads: " + ex.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "syncedFiles", synced,
                "syncedCount", synced.size(),
                "productsUpdated", productsUpdated
        ));
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a valid image file."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image must be 5 MB or smaller."));
        }

        try {
            String extension = detectImageType(file);
            if (extension == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "The uploaded content is not a valid JPG, PNG, WEBP, or GIF image."));
            }

            // 1. Upload to Cloudinary if configured
            if (cloudinaryService != null && cloudinaryService.isConfigured()) {
                try {
                    Map<String, Object> result = cloudinaryService.upload(file);
                    return ResponseEntity.ok(result);
                } catch (Exception cloudEx) {
                    log.warn("Cloudinary upload failed ({}); falling back to local disk storage.", cloudEx.getMessage());
                }
            }

            // 2. Local storage fallback
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String cleanFileName = "prod_" + UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis() + "." + extension;
            Path targetPath = uploadPath.resolve(cleanFileName).normalize();
            if (!targetPath.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid upload target."));
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "/uploads/" + cleanFileName;
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imageUrl", publicUrl,
                    "fileName", cleanFileName
            ));
        } catch (Exception ex) {
            log.error("Failed to upload image: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save file: " + ex.getMessage(), "message", "Failed to save file: " + ex.getMessage()));
        }
    }

    private String detectImageType(MultipartFile file) throws IOException {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        }
        if (read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (read >= 8 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a) {
            return "png";
        }
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return "gif";
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
