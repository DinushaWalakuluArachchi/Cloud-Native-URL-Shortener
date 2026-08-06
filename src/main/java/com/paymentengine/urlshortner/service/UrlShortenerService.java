package com.paymentengine.urlshortner.service;

import com.paymentengine.urlshortner.domain.ShortUrl;
import com.paymentengine.urlshortner.repository.ShortUrlRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class UrlShortenerService {

    private final ShortUrlRepository repository;
    private final CacheService cacheService;
    private final Counter urlsCreatedCounter;
    private final Counter redirectsCounter;
    private final Counter cacheHitsCounter;
    private final Counter cacheMissesCounter;

    @Value("${url.base-domain}")
    private String baseDomain;

    @Value("${url.short-code-length:6}")
    private int shortCodeLength;

    public UrlShortenerService(ShortUrlRepository repository, CacheService cacheService, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.cacheService = cacheService;

        this.urlsCreatedCounter = meterRegistry.counter("created.urls");
        this.redirectsCounter = meterRegistry.counter("redirects");
        this.cacheHitsCounter = meterRegistry.counter("cache.hits");
        this.cacheMissesCounter = meterRegistry.counter("cache.misses");

    }

   @Transactional
    public ShortnerResult shorten(String originalUrl, String createdBy) {
        String shortCode = generateUniCode();
        ShortUrl shortUrl = ShortUrl.create(shortCode, originalUrl, createdBy);
        repository.save(shortUrl);

        //pre-warm the cache on creation
        cacheService.cacheUrl(shortCode, originalUrl);
        urlsCreatedCounter.increment();

        String shortLink = baseDomain + "/" + shortCode;
        log.info("Created short URL: {} -> {}", shortLink, originalUrl);
        return new ShortnerResult(shortCode, shortLink, originalUrl);

    }

    @Transactional
    public Optional<String> resolveAndRedirect(String shortCode){
        //check Redis first
        Optional<String> cached = cacheService.getOriginalUrl(shortCode);
        if (cached.isPresent()){
            cacheHitsCounter.increment();
            redirectsCounter.increment();

            //async click count increment (non-blocking)
            repository.incrementClickCount(shortCode);

            return cached;
        }

        // cache miss: hit the database
        cacheMissesCounter.increment();
        Optional<ShortUrl> found = repository.findByShortCode(shortCode);

        if (found.isEmpty() || found.get().isExpired()){
            return Optional.empty();
        }

        ShortUrl shortUrl = found.get();

        // re-populate cache for future requests
        cacheService.cacheUrl(shortCode, shortUrl.getOriginalUrl());
        redirectsCounter.increment();
        repository.incrementClickCount(shortCode);

        return Optional.of(shortUrl.getOriginalUrl());

    }

    @Transactional(readOnly = true)
    public Optional<ShortUrl> getStats(String shortCode){
        return repository.findByShortCode(shortCode);
    }



    private String generateUniCode() {
        final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String code;
        int attempts = 0;

        do {
            StringBuilder stringBuilder = new StringBuilder(shortCodeLength);
            for (int i = 0; i < shortCodeLength; i++) {
                stringBuilder.append(chars.charAt((int) (Math.random() * chars.length())));
            }
            code = stringBuilder.toString();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Could not generate unique short code after 10 attempts");
            }
        } while (repository.existsByShortCode(code));

        return code;
    }


    public record ShortnerResult(String shortCode, String shortUrl, String originalUrl) {
    }


}
