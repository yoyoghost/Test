package eh.base.dao;

import com.alibaba.druid.util.StringUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.support.BroadcastInstance;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.event.support.AbstractDAOEventLisenter;
import ctd.persistence.event.support.CreateDAOEvent;
import ctd.persistence.event.support.UpdateDAOEvent;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.BeanUtils;
import eh.base.service.BusActionLogService;
import eh.entity.base.AppDomainRpcUrl;

import java.util.concurrent.ExecutionException;

/**
 * @author jianghc
 * @create 2017-05-16 14:58
 **/
public abstract class AppDomainRpcUrlDAO extends HibernateSupportDelegateDAO<AppDomainRpcUrl> {
    private LoadingCache<String, AppDomainRpcUrl> store = CacheBuilder.newBuilder().build(new CacheLoader<String, AppDomainRpcUrl>() {
        @Override
        public AppDomainRpcUrl load(String appDomainId) throws Exception {
            return getByAppDomainId(appDomainId);
        }
    });

    public AppDomainRpcUrlDAO() {
        super();
        this.setEntityName(AppDomainRpcUrl.class.getName());
        this.setKeyField("Id");
        BroadcastInstance.getSubscriber().attach(AppDomainRpcUrlDAO.class.getSimpleName(), new Observer<String>() {
            @Override
            public void onMessage(String message) {
                store.invalidate(message);
            }
        });
        this.addEventListener(new AbstractDAOEventLisenter() {
            @Override
            public void onUpdate(UpdateDAOEvent e) {
                AppDomainRpcUrl appDomainRpcUrl = (AppDomainRpcUrl) e.getTarget();
                BroadcastInstance.getPublisher().publish(AppDomainRpcUrlDAO.class.getSimpleName(), appDomainRpcUrl.getAppDomainId());
            }

            @Override
            public void onCreate(CreateDAOEvent e) {
                AppDomainRpcUrl appDomainRpcUrl = (AppDomainRpcUrl) e.getTarget();
                BroadcastInstance.getPublisher().publish(AppDomainRpcUrlDAO.class.getSimpleName(), appDomainRpcUrl.getAppDomainId());
            }
        });


    }

    @DAOMethod
    public abstract AppDomainRpcUrl getByAppDomainId(String appDomainId);

    @DAOMethod(sql = "delete from AppDomainRpcUrl where appDomainId=:appDomainId")
    public abstract void deleteByAppDomainId(@DAOParam("appDomainId") String appDomainId);


    public AppDomainRpcUrl saveOrUpdateUrl(AppDomainRpcUrl rpcUrl) {
        if (rpcUrl == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " AppDomainRpcUrl is require");
        }
        rpcUrl.setId(null);
        if (StringUtils.isEmpty(rpcUrl.getAppDomainId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " AppDomainRpcUrl.AppDomainId is require");
        }
        if (StringUtils.isEmpty(rpcUrl.getRpcUrl())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " AppDomainRpcUrl.RpcUrl is require");
        }

        AppDomainRpcUrl old = getByAppDomainId(rpcUrl.getAppDomainId());

        if (old == null) {
            rpcUrl = this.save(rpcUrl);
            BusActionLogService.recordBusinessLog("AppDomainRpcUrl管理", rpcUrl.getId().toString(), "AppDomainRpcUrl", "新增AppDomainRpcUrl【domain:" + rpcUrl.getAppDomainId() + "url:" + rpcUrl.getRpcUrl() + "】");
            return rpcUrl;
        }
        BusActionLogService.recordBusinessLog("AppDomainRpcUrl管理", old.getId().toString(), "AppDomainRpcUrl", "更新AppDomainRpcUrl(" + rpcUrl.getAppDomainId() + "):url:" + old.getRpcUrl() + "更新为" + rpcUrl.getRpcUrl());
        BeanUtils.map(rpcUrl, old);

        return this.update(old);
    }

    public AppDomainRpcUrl getByAppDomainIdFromCache(String appDomainId) {
        try {
            return store.get(appDomainId);
        } catch (Exception e) {
            return null;
        }
    }


}
