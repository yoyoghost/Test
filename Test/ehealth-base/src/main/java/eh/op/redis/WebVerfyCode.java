package eh.op.redis;

import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.redis.RedisClient;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jianghc
 * @create 2017-06-15 17:14
 **/
public class WebVerfyCode {
    private final long OVERTIME = 60;//秒
    private final String REDISKEY = "WEB.VERFYCODE";//REDIS业务名
    private final String DIFF = ".";
    private RedisClient redisClient = RedisClient.instance();


    private String getKey(String userId) {
        return StringUtils.join(REDISKEY, DIFF, userId);
    }

    @RpcService
    public void putEx(String userId, String verfyCode) {
        String key = getKey(userId);
        boolean isExist = redisClient.exists(key);
        if (isExist) {
            redisClient.del(key);
        }
        redisClient.setEX(key, OVERTIME, verfyCode);
    }

    @RpcService
    public String getEx(String userId) {
        String key = getKey(userId);
        String vc = redisClient.get(key);
        this.remove(userId);
        return vc;
    }
    public void remove(String userId) {
        String key = getKey(userId);
        redisClient.del(key);

    }

    @RpcService
    public boolean checkVerfyCode(String uid,String verfyCode){
        String vc = this.getEx(uid);
        if (StringUtils.isEmpty(vc)) {
            throw new DAOException("该用户无验证码");
        }
        if (!vc.equals(verfyCode)) {
            throw new DAOException("验证码错误");
        }
        return true;
    }



}
