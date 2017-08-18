package eh.base.service.thirdparty;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.thirdparty.ThirdParty;
import ctd.account.thirdparty.ThirdPartyMapping;
import ctd.account.thirdparty.ThirdPartyProps;
import ctd.account.user.User;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.util.ThirdPartyProvider;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.support.BroadcastInstance;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.impl.thirdparty.ThirdPartyDao;
import ctd.persistence.support.impl.thirdparty.ThirdPartyMappingDao;
import ctd.persistence.support.impl.thirdparty.ThirdPartyPropsDao;
import ctd.util.annotation.RpcService;
import ctd.util.exception.CodedBaseException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ThirdPartyProviderImpl implements ThirdPartyProvider {

    private static final String Topic = "ThirdPartyTopic";

    private LoadingCache<String, ThirdParty> thirdPartys = CacheBuilder.newBuilder().build(new CacheLoader<String, ThirdParty>() {
        @Override
        public ThirdParty load(String appkey) throws Exception {
            ThirdPartyDao thirdPartyDao = DAOFactory.getDAO(ThirdPartyDao.class);
            ThirdParty thirdParty = thirdPartyDao.get(appkey);
            if(thirdParty == null){
                throw new CodedBaseException("thirdParty " + appkey + " not exists");
            }
            ThirdPartyPropsDao thirdPartyPropsDao = DAOFactory.getDAO(ThirdPartyPropsDao.class);
            List<ThirdPartyProps> props = thirdPartyPropsDao.findByAppkey(appkey);
            if(props != null){
                Map<String, Object> ps = Maps.newHashMap();
                for(ThirdPartyProps p:props){
                    ps.put(p.getPropName(), p.getPropValue());
                }
                thirdParty.setProps(ps);
            }
            return thirdParty;
        }
    });

    public ThirdPartyProviderImpl(){
        BroadcastInstance.getSubscriber().attach(Topic, new Observer<String>() {
            @Override
            public void onMessage(String message) {
                thirdPartys.invalidate(message);
            }
        });
    }

    @RpcService
    @Override
    public ThirdParty get(String appkey) {
        try {
            return thirdPartys.get(appkey);
        } catch (ExecutionException e) {
            return null;
        }
    }

    @RpcService
    @Override
    public void reload(String appkey) {
        BroadcastInstance.getPublisher().publish(Topic, appkey);
    }

    /**
     * 绑定新用户
     * @param appkey
     * @param appsecret
     * @param tid
     * @return
     */
    @RpcService
    @Override
    public UserRoleToken loginWithThirdParty(String appkey, String appsecret, Object tid){
        ThirdParty thirdParty = check(appkey, appsecret, tid);
        ThirdPartyMappingDao thirdPartyMappingDao = DAOFactory.getDAO(ThirdPartyMappingDao.class);
        String thirdPartyUserId = tid.toString();
        ThirdPartyMapping thirdPartyMapping = thirdPartyMappingDao.getByThirdpartyAndTid(appkey, thirdPartyUserId);
        if(thirdPartyMapping == null){
            return null;
        }
        if(!"1".equals(thirdPartyMapping.getStatus())){
            throw new IllegalArgumentException("thirdParty[" + appkey + "]" + tid + " is not enabled");
        }
        String userId = thirdPartyMapping.getUserId();
        try {
            User user = AccountCenter.getUser(userId);
            int urt = thirdPartyMapping.getUrt();
            UserRoleToken token = user.getUserRoleToken(thirdPartyMapping.getUrt());
            if(token == null){
                throw new IllegalArgumentException("thirdParty[" + appkey + "]" + tid + " with user[" + userId + "] urt [" + urt + "] not exists");
            }
            return token;
        } catch (ControllerException e) {
            throw new IllegalArgumentException("thirdParty[" + appkey + "]" + tid + " with user[" + userId + "] not exists");
        }
    }

    /**
     * 授权第三方登录
     * @param appkey
     * @param appsecret
     * @param tid
     * @param user
     * @param role
     */
    @RpcService
    @Override
    public void bind(String appkey, String appsecret, Object tid, User user, String role){
        ThirdParty thirdParty = check(appkey, appsecret, tid);
        String thirdPartyUserId = tid.toString();
        ThirdPartyMappingDao thirdPartyMappingDao = DAOFactory.getDAO(ThirdPartyMappingDao.class);
        ThirdPartyMapping thirdPartyMapping = thirdPartyMappingDao.getByThirdpartyAndTid(appkey, thirdPartyUserId);
        String uid = user.getId();
        int urt = 0;
        List<UserRoleToken> tokens = user.findUserRoleTokenByRoleId(role);
        if(tokens.size()>0){
            urt = tokens.get(0).getId();
        }
        boolean create = false;
        if(thirdPartyMapping == null){
            create = true;
            thirdPartyMapping = new ThirdPartyMapping();
            thirdPartyMapping.setThirdparty(appkey);
            thirdPartyMapping.setTid(thirdPartyUserId);
        }
        thirdPartyMapping.setUserId(uid);
        thirdPartyMapping.setUrt(urt);
        thirdPartyMapping.setStatus("1");
        if(create){
            thirdPartyMappingDao.save(thirdPartyMapping);
        }else {
            thirdPartyMappingDao.update(thirdPartyMapping);
        }
    }


    /**
     * 授权第三方登录（医生角色）
     * @param appkey
     * @param appsecret
     * @param tid
     * @param user
     */
    @RpcService
    @Override
    public void bind(String appkey, String appsecret, Object tid, User user) {
        bind(appkey, appsecret, tid, user, "doctor");
    }

    @RpcService
    @Override
    public void unbind(String appkey, String appsecret, Object tid) {
        ThirdParty thirdParty = check(appkey, appsecret, tid);
        ThirdPartyMappingDao thirdPartyMappingDao = DAOFactory.getDAO(ThirdPartyMappingDao.class);
        thirdPartyMappingDao.updateStatusByThirdpartyAndTid(appkey, tid.toString(), "0");
    }

    @RpcService
    @Override
    public ThirdParty registerThirdParty(ThirdParty thirdParty) {
        ThirdPartyDao thirdPartyDao = DAOFactory.getDAO(ThirdPartyDao.class);
        return thirdPartyDao.save(thirdParty);
    }

    @RpcService
    @Override
    public ThirdParty updateThirdParty(ThirdParty thirdParty) {
        ThirdPartyDao thirdPartyDao = DAOFactory.getDAO(ThirdPartyDao.class);
        return thirdPartyDao.update(thirdParty);
    }

    @RpcService
    @Override
    public void disableThirdParty(String appkey) {
        ThirdPartyDao thirdPartyDao = DAOFactory.getDAO(ThirdPartyDao.class);
        thirdPartyDao.updateStatusByAppkey(appkey, "0");
        thirdPartys.invalidate(appkey);
    }

    private ThirdParty check(String appkey, String appsecret, Object tid){
        if(StringUtils.isEmpty(appkey)){
            throw new IllegalArgumentException("appkey missing");
        }
        if(StringUtils.isEmpty(appsecret)){
            throw new IllegalArgumentException("appsecret missing");
        }
        if(tid == null || "".equals(tid)){
            throw new IllegalArgumentException("tid missing");
        }
        try {
            ThirdParty thirdParty = thirdPartys.get(appkey);
            if(!"1".equals(thirdParty.getStatus())){
                throw new IllegalArgumentException("thirdParty[" + appkey + "] is not enabled");
            }
            if(!appsecret.equals(thirdParty.getAppsecret())){
                throw new IllegalArgumentException("thirdParty[" + appkey + "] secret incorrect");
            }
            return thirdParty;
        } catch (ExecutionException e) {
            throw new IllegalArgumentException("thirdParty[" + appkey + "] not exists");
        }
    }

    @RpcService
    public ThirdPartyMapping getByThirdpartyAndTid(String appkey, String tid){
        return DAOFactory.getDAO(ThirdPartyMappingDao.class).getByThirdpartyAndTid(appkey, tid);
    }
}
