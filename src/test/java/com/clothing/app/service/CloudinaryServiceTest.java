package com.clothing.app.service;

import com.clothing.app.controller.FileUploadController;
import com.clothing.app.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryServiceTest {

    @Test
    void whenCredentialsIncomplete_serviceIsNotConfigured() {
        CloudinaryService service = new CloudinaryService("", "", "", "Clothing App");
        assertFalse(service.isConfigured());

        CloudinaryService partial = new CloudinaryService("cloud", "key", "", "Clothing App");
        assertFalse(partial.isConfigured());
    }

    @Test
    void whenCredentialsProvided_serviceIsConfigured() {
        CloudinaryService service = new CloudinaryService("v2w9olom", "773841939818133", "4tGcttjWgtnbW9jHV2KuZQa3vBM", "Clothing App");
        assertTrue(service.isConfigured());
    }

    @Test
    void fileUploadController_rejectsEmptyFile() {
        CloudinaryService mockCloud = mock(CloudinaryService.class);
        ProductRepository mockProdRepo = mock(ProductRepository.class);
        FileUploadController controller = new FileUploadController(mockCloud, mockProdRepo);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        ResponseEntity<?> response = controller.uploadImage(emptyFile);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void fileUploadController_rejectsNonImageFile() {
        CloudinaryService mockCloud = mock(CloudinaryService.class);
        ProductRepository mockProdRepo = mock(ProductRepository.class);
        FileUploadController controller = new FileUploadController(mockCloud, mockProdRepo);

        MockMultipartFile textFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello world".getBytes());
        ResponseEntity<?> response = controller.uploadImage(textFile);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void fileUploadController_delegatesToCloudinaryWhenConfigured() throws IOException {
        CloudinaryService mockCloud = mock(CloudinaryService.class);
        ProductRepository mockProdRepo = mock(ProductRepository.class);
        when(mockCloud.isConfigured()).thenReturn(true);
        when(mockCloud.upload(any())).thenReturn(Map.of(
                "success", true,
                "imageUrl", "https://res.cloudinary.com/v2w9olom/image/upload/v1/Clothing%20App/prod_123.jpg",
                "fileName", "Clothing App/prod_123"
        ));

        FileUploadController controller = new FileUploadController(mockCloud, mockProdRepo);

        // Valid PNG header (8 bytes)
        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile pngFile = new MockMultipartFile("file", "product.png", "image/png", validPng);

        ResponseEntity<?> response = controller.uploadImage(pngFile);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("https://res.cloudinary.com/v2w9olom/image/upload/v1/Clothing%20App/prod_123.jpg", body.get("imageUrl"));

        verify(mockCloud).upload(any());
    }

    @Test
    void fileUploadController_fallsBackToLocalWhenCloudinaryNotConfigured() throws IOException {
        CloudinaryService mockCloud = mock(CloudinaryService.class);
        ProductRepository mockProdRepo = mock(ProductRepository.class);
        when(mockCloud.isConfigured()).thenReturn(false);

        FileUploadController controller = new FileUploadController(mockCloud, mockProdRepo);

        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile pngFile = new MockMultipartFile("file", "local.png", "image/png", validPng);

        ResponseEntity<?> response = controller.uploadImage(pngFile);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((String) body.get("imageUrl")).startsWith("/uploads/prod_"));

        verify(mockCloud, never()).upload(any());
    }

    @Test
    void fileUploadController_fallsBackToLocalWhenCloudinaryUploadThrowsException() throws IOException {
        CloudinaryService mockCloud = mock(CloudinaryService.class);
        ProductRepository mockProdRepo = mock(ProductRepository.class);
        when(mockCloud.isConfigured()).thenReturn(true);
        when(mockCloud.upload(any())).thenThrow(new RuntimeException("Cloudinary network timeout"));

        FileUploadController controller = new FileUploadController(mockCloud, mockProdRepo);

        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile pngFile = new MockMultipartFile("file", "fallback.png", "image/png", validPng);

        ResponseEntity<?> response = controller.uploadImage(pngFile);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((String) body.get("imageUrl")).startsWith("/uploads/prod_"));
        assertTrue((Boolean) body.get("success"));

        verify(mockCloud).upload(any());
    }
}
