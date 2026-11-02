package com.autohaus.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties({AppProperties.class, SoftnetProperties.class, FeatureProperties.class, ErrorReportingProperties.class, UsageStatsProperties.class,
    UsageInsightsProperties.class, UsageFunnelProperties.class, UsageDropoffProperties.class, CapacityProperties.class})
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private final AppProperties appProperties;
    private final String indexHtmlTemplate;
    private final String distPath;

    public WebConfig(AppProperties appProperties) {
        this.appProperties = appProperties;

        // Try to find the Angular dist folder
        String basePath = System.getProperty("user.dir");
        Path distDir = Path.of(basePath, "..", "dist", "terminbuchungsapp", "browser");
        if (!Files.exists(distDir)) {
            distDir = Path.of(basePath, "dist", "terminbuchungsapp", "browser");
        }
        this.distPath = distDir.toAbsolutePath().normalize().toString();

        Path indexPath = Path.of(this.distPath, "index.html");
        if (Files.exists(indexPath)) {
            String template = null;
            try {
                template = Files.readString(indexPath);
                log.info("Angular app loaded from {}", this.distPath);
            } catch (IOException e) {
                log.warn("Could not read index.html: {}", e.getMessage());
            }
            this.indexHtmlTemplate = template;
        } else {
            log.warn("dist/ folder not found at {} — Angular app not served", this.distPath);
            this.indexHtmlTemplate = null;
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = appProperties.cors() != null ? appProperties.cors().allowedOrigins() : "";
        String[] allowedOrigins = origins != null && !origins.isBlank()
            ? Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
            : new String[]{"http://localhost:4200", "http://localhost:3000"};

        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("Content-Type", "X-Session-Token", "X-Correlation-Id", "X-Session-Id")
            .allowCredentials(false);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (this.indexHtmlTemplate != null) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File distDir = new File(this.distPath);
        if (!distDir.exists()) return;

        registry.addResourceHandler("/**")
            .addResourceLocations("file:" + this.distPath + "/")
            .setCachePeriod(0)  // REQ-025: HTML/SPA-Fallback ohne Browser-Cache (sonst alter Bundle-Hash)
            .resourceChain(true)
            .addResolver(new SpaResourceResolver(this.distPath, this.indexHtmlTemplate));
    }

    /** REQ-025: HTML-Responses dürfen NICHT gecacht werden. JS-Bundles (mit Hash) dürfen forever-cached. */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> noCacheHtmlFilter() {
        jakarta.servlet.Filter filter = (req, res, chain) -> {
            chain.doFilter(req, res);
            jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
            String ct = httpRes.getContentType();
            if (ct != null && ct.startsWith("text/html")) {
                httpRes.setHeader("Cache-Control", "no-store, must-revalidate");
                httpRes.setHeader("Pragma", "no-cache");
            }
        };
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(Integer.MIN_VALUE + 1000);
        return reg;
    }

    /**
     * Custom resolver that serves index.html with CSP nonce for SPA routes.
     * Static files are served as-is; non-API, non-file requests get index.html.
     */
    static class SpaResourceResolver extends PathResourceResolver {
        private final String distPath;
        private final String fallbackTemplate;
        private final SecureRandom secureRandom = new SecureRandom();

        SpaResourceResolver(String distPath, String indexHtmlTemplate) {
            this.distPath = distPath;
            this.fallbackTemplate = indexHtmlTemplate;
        }

        /**
         * REQ-025: Liest index.html bei JEDER Anfrage frisch von Disk — sonst zeigt
         * der Backend nach `npm run build` weiterhin den alten Bundle-Hash und der
         * Browser bekommt 500/404 für nicht mehr existierende JS-Chunks.
         */
        private String readCurrentIndexHtml() {
            try {
                java.nio.file.Path p = java.nio.file.Path.of(distPath, "index.html");
                if (java.nio.file.Files.exists(p)) {
                    return java.nio.file.Files.readString(p);
                }
            } catch (IOException ignored) { /* fallback unten */ }
            return fallbackTemplate;
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null && resource.exists()) {
                return resource;
            }

            // SPA fallback: nur für Pfade OHNE Datei-Extension (= echte Angular-Routen).
            // Datei-Anfragen (.js/.css/.svg/...) die nicht existieren → 404,
            // sonst bekäme der Browser HTML mit falschem MIME-Type oder einen 500.
            if (resourcePath.startsWith("api/")) {
                return new NotFoundResource(resourcePath);
            }
            String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            if (lastSegment.contains(".")) {
                return new NotFoundResource(resourcePath);
            }

            String template = readCurrentIndexHtml();
            if (template == null) {
                return new NotFoundResource(resourcePath);
            }
            byte[] nonceBytes = new byte[16];
            secureRandom.nextBytes(nonceBytes);
            String nonce = Base64.getEncoder().encodeToString(nonceBytes);
            String html = template.replace("<app-root>", "<app-root ngCspNonce=\"" + nonce + "\">");
            return new InMemoryResource(html);
        }
    }

    /** Sentinel-Resource — `exists()` false → Spring liefert 404. */
    static class NotFoundResource implements Resource {
        private final String path;
        NotFoundResource(String path) { this.path = path; }
        @Override public boolean exists() { return false; }
        @Override public java.net.URL getURL() { return null; }
        @Override public java.net.URI getURI() { return null; }
        @Override public File getFile() { return null; }
        @Override public long contentLength() { return 0; }
        @Override public long lastModified() { return 0; }
        @Override public Resource createRelative(String relativePath) { return null; }
        @Override public String getFilename() { return path; }
        @Override public String getDescription() { return "Not found: " + path; }
        @Override public java.io.InputStream getInputStream() throws IOException {
            throw new java.io.FileNotFoundException(path);
        }
    }

    /**
     * In-memory resource for serving modified index.html.
     */
    static class InMemoryResource implements Resource {
        private final byte[] content;

        InMemoryResource(String content) {
            this.content = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override public boolean exists() { return true; }
        @Override public java.net.URL getURL() { return null; }
        @Override public java.net.URI getURI() { return null; }
        @Override public File getFile() { return null; }
        @Override public long contentLength() { return content.length; }
        @Override public long lastModified() { return System.currentTimeMillis(); }
        @Override public Resource createRelative(String relativePath) { return null; }
        @Override public String getFilename() { return "index.html"; }
        @Override public String getDescription() { return "In-memory index.html with CSP nonce"; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
    }
}
