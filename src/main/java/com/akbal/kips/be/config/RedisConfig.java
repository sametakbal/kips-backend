package com.akbal.kips.be.config;

import com.akbal.kips.be.constants.CacheNames;
import com.akbal.kips.be.dto.product.response.CategoryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class RedisConfig {
    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.cache.redis.time-to-live:10m}") Duration ttl) {
        var mapper = JsonMapper.builder().build();
        var categoryList = mapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class);
        var serializer = new JacksonJsonRedisSerializer<>(mapper, categoryList);
        var categories = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CacheNames.ALL_CATEGORIES, categories)
                .transactionAware()
                .build();
    }
}
