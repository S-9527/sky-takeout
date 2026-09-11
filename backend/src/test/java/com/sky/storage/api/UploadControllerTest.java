package com.sky.storage.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.sky.common.error.BusinessException;
import com.sky.storage.domain.StorageErrorCode;
import com.sky.storage.service.StorageService;
import com.sky.storage.service.StoredFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadControllerTest {

    private final StorageService storageService = mock(StorageService.class);
    private final UploadController controller = new UploadController(storageService);

    @Test
    void uploadDelegatesAndMapsResult() {
        when(storageService.store(eq("dish.png"), eq("image/png"), any()))
                .thenReturn(new StoredFile("http://localhost:8080/files/20250101/abc.png", 3L, "image/png"));

        var response = controller.upload(new MockMultipartFile("file", "dish.png", "image/png", new byte[]{1, 2, 3}));

        assertThat(response.url()).isEqualTo("http://localhost:8080/files/20250101/abc.png");
        assertThat(response.size()).isEqualTo(3L);
        assertThat(response.contentType()).isEqualTo("image/png");
        verify(storageService).store(eq("dish.png"), eq("image/png"), any());
    }

    /** 字段缺失也走契约里的 UPLOAD_EMPTY_FILE,而不是 Spring 的参数缺失异常。 */
    @Test
    void missingOrEmptyFileIsRejectedWithContractCode() {
        assertThatThrownBy(() -> controller.upload(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_EMPTY_FILE));
        assertThatThrownBy(() -> controller.upload(new MockMultipartFile("file", new byte[0])))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_EMPTY_FILE));
    }

    @Test
    void unreadableUploadIsReportedAsStoreFailure() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> controller.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_STORE_FAILED));
    }

    /** 超过容器上限的请求在进入方法体前就被拒了,控制器要把它翻译成契约里的 400。 */
    @Test
    void containerLevelSizeLimitBecomesContractBadRequest() {
        var response = controller.handleTooLarge(new MaxUploadSizeExceededException(10 * 1024 * 1024));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UPLOAD_FILE_TOO_LARGE");
    }
}
