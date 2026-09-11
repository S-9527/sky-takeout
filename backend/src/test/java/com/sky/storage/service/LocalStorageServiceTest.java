package com.sky.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sky.common.error.BusinessException;
import com.sky.storage.domain.StorageErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    private static final String BASE_URL = "http://localhost:8080/files";

    @TempDir
    Path tempDir;

    private LocalStorageService service(Path dir) {
        return new LocalStorageService(new StorageProperties("local", dir.toString(), BASE_URL + "/"));
    }

    @Test
    void storesFileUnderDateDirectoryAndReturnsPublicUrl() throws IOException {
        LocalStorageService service = service(tempDir);
        byte[] content = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = service.store("photo.PNG", "image/png", content);

        // 配置里的尾部斜杠会被归一化掉:路径部分不出现 //
        assertThat(stored.url()).startsWith(BASE_URL + "/").endsWith(".png");
        assertThat(stored.url().substring(BASE_URL.length())).doesNotContain("//");
        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(stored.contentType()).isEqualTo("image/png");

        String relative = stored.url().substring((BASE_URL + "/").length());
        Path written = tempDir.resolve(relative);
        assertThat(written).exists();
        assertThat(Files.readAllBytes(written)).isEqualTo(content);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service(tempDir).store("a.png", "image/png", new byte[0]))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_EMPTY_FILE));
        assertThatThrownBy(() -> service(tempDir).store("a.png", "image/png", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_EMPTY_FILE));
    }

    @Test
    void rejectsFileLargerThanFiveMegabytes() {
        byte[] tooLarge = new byte[(int) LocalStorageService.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service(tempDir).store("a.jpg", "image/jpeg", tooLarge))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_FILE_TOO_LARGE);
                    assertThat(ex.details()).isNotEmpty();
                });
        assertThat(LocalStorageService.MAX_FILE_SIZE_BYTES).isEqualTo(5L * 1024 * 1024);
    }

    /** 扩展名与内容类型**都要**在白名单里:只信一个都容易被绕过。 */
    @Test
    void rejectsExtensionOrContentTypeOutsideWhitelist() {
        byte[] content = {1, 2, 3};

        assertThatThrownBy(() -> service(tempDir).store("a.gif", "image/gif", content))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        assertThatThrownBy(() -> service(tempDir).store("a.png", "application/octet-stream", content))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        assertThatThrownBy(() -> service(tempDir).store("no-extension", "image/png", content))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        assertThatThrownBy(() -> service(tempDir).store("a.png", null, content))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
    }

    @Test
    void acceptsJpegAndWebp() {
        byte[] content = {9, 9, 9};

        assertThat(service(tempDir).store("a.jpeg", "image/jpeg", content).url()).endsWith(".jpeg");
        assertThat(service(tempDir).store("b.webp", "image/webp", content).url()).endsWith(".webp");
        assertThat(service(tempDir).store("c.jpg", "image/jpg", content).url()).endsWith(".jpg");
    }

    /** 落盘目录本身是个文件时写入必然失败 → 500 UPLOAD_STORE_FAILED(而不是把 IOException 漏成 500 通用码)。 */
    @Test
    void storeFailureIsTranslatedToContractCode() throws IOException {
        Path notADirectory = tempDir.resolve("occupied");
        Files.writeString(notADirectory, "i am a file");

        assertThatThrownBy(() -> service(notADirectory).store("a.png", "image/png", new byte[]{1}))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(StorageErrorCode.UPLOAD_STORE_FAILED));
    }

    @Test
    void propertiesFallBackToDocumentedDefaults() {
        StorageProperties properties = new StorageProperties(null, null, null);

        assertThat(properties.type()).isEqualTo("local");
        assertThat(properties.localDir()).isEqualTo("./data/upload");
        assertThat(properties.publicBaseUrl()).isEqualTo("http://localhost:8080/files");
    }
}
