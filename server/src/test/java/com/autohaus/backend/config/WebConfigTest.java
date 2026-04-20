package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    @Test
    void constructor_noDistFolder_doesNotThrow() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret-32-characters-long-test!!", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000");
        AppProperties props = new AppProperties(session, cors);

        assertDoesNotThrow(() -> new WebConfig(props));
    }

    @Test
    void constructor_nullOrigins_doesNotThrow() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret-32-characters-long-test!!", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(null);
        AppProperties props = new AppProperties(session, cors);

        assertDoesNotThrow(() -> new WebConfig(props));
    }

    @Test
    void constructor_emptyOrigins_doesNotThrow() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret-32-characters-long-test!!", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("");
        AppProperties props = new AppProperties(session, cors);

        WebConfig config = new WebConfig(props);
        assertNotNull(config);
    }

    @Test
    void constructor_nullCors_doesNotThrow() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties props = new AppProperties(session, null);

        assertDoesNotThrow(() -> new WebConfig(props));
    }

    @Test
    void addCorsMappings_withConfiguredOrigins() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000,http://localhost:4200");
        AppProperties props = new AppProperties(session, cors);
        WebConfig config = new WebConfig(props);

        CorsRegistry registry = new CorsRegistry();
        assertDoesNotThrow(() -> config.addCorsMappings(registry));
    }

    @Test
    void addCorsMappings_withNullCors_usesDefaults() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties props = new AppProperties(session, null);
        WebConfig config = new WebConfig(props);

        CorsRegistry registry = new CorsRegistry();
        assertDoesNotThrow(() -> config.addCorsMappings(registry));
    }

    @Test
    void addCorsMappings_withEmptyOrigins_usesDefaults() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("   ");
        AppProperties props = new AppProperties(session, cors);
        WebConfig config = new WebConfig(props);

        CorsRegistry registry = new CorsRegistry();
        assertDoesNotThrow(() -> config.addCorsMappings(registry));
    }

    @Test
    void inMemoryResource_properties() {
        WebConfig.InMemoryResource resource = new WebConfig.InMemoryResource("<html>test</html>");

        assertTrue(resource.exists());
        assertNull(resource.getURL());
        assertNull(resource.getURI());
        assertNull(resource.getFile());
        assertEquals("index.html", resource.getFilename());
        assertEquals("In-memory index.html with CSP nonce", resource.getDescription());
        assertTrue(resource.contentLength() > 0);
        assertTrue(resource.lastModified() > 0);
        assertNull(resource.createRelative("other"));
    }

    @Test
    void inMemoryResource_getInputStream() throws IOException {
        String content = "<html><body>Hello</body></html>";
        WebConfig.InMemoryResource resource = new WebConfig.InMemoryResource(content);

        try (InputStream is = resource.getInputStream()) {
            String result = new String(is.readAllBytes());
            assertEquals(content, result);
        }
    }

    @Test
    void spaResourceResolver_construction() {
        WebConfig.SpaResourceResolver resolver = new WebConfig.SpaResourceResolver("/some/path", "<html></html>");
        assertNotNull(resolver);
    }

    @Test
    void spaResourceResolver_withNullTemplate() {
        WebConfig.SpaResourceResolver resolver = new WebConfig.SpaResourceResolver("/some/path", null);
        assertNotNull(resolver);
    }
}
