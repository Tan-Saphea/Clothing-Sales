package com.clothing.app.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;
    private final String folder;
    private final boolean configured;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.folder:Clothing App}") String folder) {
        this.folder = folder != null ? folder.trim() : "Clothing App";
        if (cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank()) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim(),
                    "secure", true
            ));
            this.configured = true;
            log.info("CloudinaryService initialized with cloud_name='{}', folder='{}'", cloudName.trim(), this.folder);
        } else {
            this.cloudinary = null;
            this.configured = false;
            log.warn("Cloudinary credentials incomplete. Falling back to local storage.");
        }
    }

    public boolean isConfigured() {
        return configured && cloudinary != null;
    }

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException("Cloudinary is not configured.");
        }

        Map<String, Object> params = new HashMap<>();
        if (folder != null && !folder.isBlank()) {
            params.put("folder", folder);
            params.put("asset_folder", folder);
        }
        params.put("resource_type", "image");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(file.getBytes(), params);

        String secureUrl = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");

        log.info("Uploaded image to Cloudinary successfully. public_id='{}', url='{}'", publicId, secureUrl);
        return Map.of(
                "success", true,
                "imageUrl", secureUrl,
                "fileName", publicId,
                "publicId", publicId,
                "format", result.getOrDefault("format", ""),
                "bytes", result.getOrDefault("bytes", 0)
        );
    }

    public Map<String, Object> uploadFile(File file) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException("Cloudinary is not configured.");
        }

        Map<String, Object> params = new HashMap<>();
        if (folder != null && !folder.isBlank()) {
            params.put("folder", folder);
            params.put("asset_folder", folder);
        }
        params.put("resource_type", "image");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(file, params);

        String secureUrl = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");

        log.info("Uploaded local file to Cloudinary: '{}' -> '{}'", file.getName(), secureUrl);
        return Map.of(
                "success", true,
                "imageUrl", secureUrl,
                "fileName", publicId,
                "publicId", publicId
        );
    }
}
