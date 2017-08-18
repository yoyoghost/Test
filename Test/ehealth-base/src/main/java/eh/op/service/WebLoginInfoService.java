package eh.op.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.redis.RedisClient;
import org.apache.log4j.Logger;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author jianghc
 * @create 2017-06-15 17:14
 **/
public class WebLoginInfoService {
    private static final Logger logger = Logger.getLogger(WebLoginInfoService.class);
    private final String REDISKEY= "WEB.LOGININFO";//REDIS业务名


    private RedisTemplate redisTemplate;

    public WebLoginInfoService(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisTemplate.expire("max",60, TimeUnit.SECONDS);
    }

    @RpcService
    public void putLoginInfo(String ticket,String openId){
        if (StringUtils.isEmpty(ticket)){
            throw new DAOException(DAOException.VALUE_NEEDED,"ticket is require");
        }
        if (StringUtils.isEmpty(openId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"openId is require");
        }
        redisTemplate.opsForHash().put(REDISKEY,ticket,openId);
    }
    @RpcService
    public String getLoginInfo(String ticket){
        if (StringUtils.isEmpty(ticket)){
            throw new DAOException(DAOException.VALUE_NEEDED,"ticket is require");
        }
        return (String) redisTemplate.opsForHash().entries(REDISKEY).get(ticket);
    }

    @RpcService
    public void remove(String ticket){
        if (StringUtils.isEmpty(ticket)){
            throw new DAOException(DAOException.VALUE_NEEDED,"ticket is require");
        }
      redisTemplate.opsForHash().delete(REDISKEY,ticket);
    }

}
