package eh.util;

import ctd.util.annotation.RpcService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InvokeLimiter {
    private static final Logger log = LoggerFactory.getLogger(InvokeLimiter.class);
    private static final String REDIS_ADAPTER_COUNT_KEY = "adapter.service.count";
    private static final String REDIS_ADAPTER_LIMIT_CONFIG_KEY = "adapter.service.config";
    private long defaultLimit = 100l;
//    private Map<String, Long> limits = null;
    private RedisTemplate redisTemplate;
    private long expire = 20l;

    public InvokeLimiter(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setDefaultLimit(long defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public String processKey(String origin){
        return StringUtils.join(REDIS_ADAPTER_COUNT_KEY, ".", StringUtils.substringBefore(origin, "."));
    }

    public boolean plus(final String key){
        try{
            Map<String,Long> limits = redisTemplate.opsForHash().entries(REDIS_ADAPTER_LIMIT_CONFIG_KEY);
            if (limits.containsKey(StringUtils.substringBefore(key, "."))){
                long limit = limits.get(StringUtils.substringBefore(key, "."));
                if (redisTemplate.opsForValue().increment(processKey(key), 1l) > limit){
                    expire(key);
                    return false;
                }
            }else {
                redisTemplate.opsForHash().put(REDIS_ADAPTER_LIMIT_CONFIG_KEY, StringUtils.substringBefore(key, "."), defaultLimit);
            }
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return true;
    }

    public void minus(String key){
        try {
            redisTemplate.opsForValue().increment(processKey(key), -1l);
            expire(key);
        }catch (Exception e){
            log.error(e.getMessage());
        }
    }

    private void expire(String key){
        redisTemplate.expire(processKey(key), expire, TimeUnit.SECONDS);
    }

    @RpcService
    public Map getHisLimitConfig(){
        return redisTemplate.opsForHash().entries(REDIS_ADAPTER_LIMIT_CONFIG_KEY);
    }

    @RpcService
    public void setHisLimitConfig(String hisDomain, Long limit){
        redisTemplate.opsForHash().put(REDIS_ADAPTER_LIMIT_CONFIG_KEY, hisDomain, limit==null?defaultLimit:limit);
    }
}
