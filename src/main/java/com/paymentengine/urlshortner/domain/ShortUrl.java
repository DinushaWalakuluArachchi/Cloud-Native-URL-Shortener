package com.paymentengine.urlshortner.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "short_urls", indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true),
        @Index(name = "idx_created_by", columnList = "createdBy")
})
@Getter
@NoArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false)
    private  String createdBy;

    @Column(nullable = false)
    private long clickCount = 0L;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant expiresAt;

    @PrePersist
    private void onCreate(){
        createdAt = Instant.now();
    }

    public static ShortUrl create(String shortCode, String originalUrl, String createdBy){
        ShortUrl shortUrl = new ShortUrl();
        shortUrl.shortCode = shortCode;
        shortUrl.originalUrl = originalUrl;
        shortUrl.createdBy = createdBy;
        return shortUrl;
    }

    public void incrementsClicks(){
        this.clickCount++;
    }

    public boolean isExpired(){
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }



}
