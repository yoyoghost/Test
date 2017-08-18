package eh.op.auth.service;

import ctd.account.UserRoleToken;
import ctd.mvc.controller.util.MvcAuthenticationProvider;
import eh.redis.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthProviderService implements MvcAuthenticationProvider {

    private Logger logger = LoggerFactory.getLogger(AuthProviderService.class);
    private RedisClient redisClient = RedisClient.instance();

    //防刷规则   一秒内 最多调用 10次
    private int interval = 1;
    private int maxCount = 10;

    @Override
    public boolean isAccess(String serviceId, String method, UserRoleToken urt) {
        /*
        //dao
        //默认某些服务直接访问
        if () {
            //eh.securityService  getUserPermissions
            //eh.organ findOrgansByManageUnit getByManageUnit
            //eh.*  get*
        } else{
            return (new SecurityService()).isAuthoritied(urt, serviceId, method);
        }*/

//        return (new SecurityService()).isAuthoritied(urt, serviceId, method);


        /**
         * 对防刷做限制
         */
        return tpsIsAllowable(serviceId,method,urt.getId());

    }
    /**
     * 是否允许接口调用
     * @param serviceId
     * @param method
     * @param urt
     * @return
     */
    private boolean tpsIsAllowable(String serviceId, String method, int urt){

        boolean flag = true;
        final String key = serviceId+"."+method+"."+urt;
        try {
            //先判断开关是否开启
            Object isDefence = redisClient.getRedisTemplate().opsForValue().get("base.auth.isDefence");
            if(isDefence == null || !isDefence.toString().equals("1")){
                //没有配置 或者 值不为1
                return flag;
            }

            long value = redisClient.getRedisTemplate().opsForValue().increment(key,1);

            if(value == 1){
                redisClient.setex(key,interval);
                return flag;
            }
            if(value >maxCount){
                flag = false;
            }
        }catch (Exception e){
            logger.error("method tpsIsAllowable error",e);
            return true;
        }
        return flag;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}
