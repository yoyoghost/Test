package eh.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.support.BroadcastInstance;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.dao.ClientSetDAO;
import eh.entity.bus.RpcServiceInfo;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Created by zhongzx on 2016/8/24 0024.
 * base读取his服务在数据库url地址缓存类
 */
public class RpcServiceInfoStore {
    private static final String TOPIC = RpcServiceInfoStore.class.getSimpleName();
    private LoadingCache<String, RpcServiceInfo> store = CacheBuilder.newBuilder().build(new CacheLoader<String, RpcServiceInfo>() {
        @Override
        public RpcServiceInfo load(String serviceName) throws Exception {
            ClientSetDAO dao = DAOFactory.getDAO(ClientSetDAO.class);
            RpcServiceInfo info = dao.getByServiceName(serviceName);
            return info;
        }
    });

    public RpcServiceInfoStore(){
        //订阅消息
        BroadcastInstance.getSubscriber().attach(TOPIC, new Observer<String>() {
            public void onMessage(String message) {
                remove(message);
            }
        });
    }

    @RpcService
    public RpcServiceInfo getInfo(String serviceName){
        try {
            return store.get(serviceName);
//        } catch (ExecutionException | UncheckedExecutionException e) {
        } catch (Exception e) {
            return null;
        }
    }

    @RpcService
    public Map<String, RpcServiceInfo> getInfos(){
        return store.asMap();
    }

    public void remove(String serviceName){
        store.invalidate(serviceName);
    }

    @RpcService
    public boolean updateRpcServiceCache(String serviceName){
        BroadcastInstance.getPublisher().publish(TOPIC, serviceName);
        return true;
    }

    public static void updateRpcServiceCacheByEntity(RpcServiceInfo info){
        if(null == info){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "没有提供his服务信息");
        }
        BroadcastInstance.getPublisher().publish(TOPIC, info.getServiceName());
    }
}
