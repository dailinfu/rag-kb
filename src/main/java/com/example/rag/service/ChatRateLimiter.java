package com.example.rag.service;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 按客户端标识限制问答频率的 Redis 滑动窗口。
 */
@Service
public class ChatRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);
    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final int MAX_REQUESTS = 100;
    private static final String KEY_PREFIX = "rag:rate-limit:chat:";

    private static final String SLIDING_WINDOW_SCRIPT =
            "local time = redis.call('TIME')\n" +
            "local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)\n" +
            "local window = tonumber(ARGV[1])\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "if count >= tonumber(ARGV[2]) then return 0 end\n" +
            "redis.call('ZADD', KEYS[1], now, ARGV[3])\n" +
            "redis.call('PEXPIRE', KEYS[1], window)\n" +
            "return 1";

    private final RedissonClient redissonClient;
    public ChatRateLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 原子地清理过期请求、检查窗口容量并记录本次请求。
     */
    public boolean tryAcquire(String userKey) {
        try {
            Long result = redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    SLIDING_WINDOW_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(KEY_PREFIX + userKey),
                    WINDOW_MILLIS,
                    MAX_REQUESTS,
                    UUID.randomUUID().toString());
            if (result == null) {
                log.warn("聊天限流脚本未返回结果，暂时放行请求");
                return true;
            }
            return result == 1L;
        } catch (RuntimeException e) {
            log.warn("聊天限流服务异常，暂时放行请求", e);
            return true;
        }
    }
}
