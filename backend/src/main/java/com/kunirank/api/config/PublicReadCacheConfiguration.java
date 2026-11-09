package com.kunirank.api.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("railway-unified")
@EnableCaching
class PublicReadCacheConfiguration {
}
