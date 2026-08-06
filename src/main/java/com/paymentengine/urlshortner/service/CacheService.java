package com.paymentengine.urlshortner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private static final String KEY_PREFIX = "url:";

    private final StringRedisTemplate redis;

    @Value("${url.cache-ttl-hours:24}")
    private long cacheTtlHours;


    /// get data from cache
    public Optional<String> getOriginalUrl(String shortCode){
       String value = redis.opsForValue().get(KEY_PREFIX +  shortCode);
       if (value != null){
           log.debug("Cache hit for shortCode={}", shortCode);
       }
       return  Optional.ofNullable(value);

    }

    /// data bind to cache
    public void cacheUrl(String shortCode ,String originalUrl){
        redis.opsForValue().set(
                KEY_PREFIX + shortCode,
                originalUrl,
                Duration.ofHours(cacheTtlHours)
        );
        log.debug("Cached shortCode={} TTL={}h", shortCode, cacheTtlHours);
    }

    /// delete data from cache
    public void evict(String shortCode){
        redis.delete(KEY_PREFIX + shortCode);
        log.debug("Evicted shortCode={} from cache", shortCode);
    }
}
