package com.paymentengine.urlshortner.api;

import com.paymentengine.urlshortner.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {

        String createdBy = httpRequest.getHeader("X-User-Id");
        if (createdBy == null) createdBy = "anonymous";

        if (request.url() == null || request.url().isBlank()) {
            return ResponseEntity.badRequest().build();
        }


        UrlShortenerService.ShortnerResult result = service.shorten(request.url(), createdBy);

        return ResponseEntity.ok(new ShortenResponse(
                result.shortCode(),
                result.shortUrl(),
                result.originalUrl()
        ));

    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        Optional<String> originalUrl = service.resolveAndRedirect(shortCode);

        if (originalUrl.isEmpty()) {
            return ResponseEntity.notFound().build();
        }


        // 301 = permanent redirect (browser caches it)
        // 302 = temporary redirect (browser asks every time — better for analytics)
        // Using 302 so every redirect is tracked
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl.get())
                .build();
    }

    @GetMapping("/stats/{shortCode}")
    public ResponseEntity<StatsResponse> stats(@PathVariable String shortCode){
        return service.getStats(shortCode)
                .map(s -> ResponseEntity.ok(new StatsResponse(
                        s.getShortCode(),
                        s.getOriginalUrl(),
                        s.getClickCount(),
                        s.getCreatedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }


    record ShortenRequest(String url) {
    }

    record ShortenResponse(String shortCode, String shortUrl, String originalUrl) {
    }

    record StatsResponse(String shortCode, String originalUrl, long clicks, String createdAt) {
    }
}
