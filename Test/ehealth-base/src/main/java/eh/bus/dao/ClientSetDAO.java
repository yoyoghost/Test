package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.bus.RpcServiceInfo;

import java.util.List;

/**
 * Created by Administrator on 2016/7/19.
 */
public abstract class ClientSetDAO extends HibernateSupportDelegateDAO<RpcServiceInfo> implements DBDictionaryItemLoader<RpcServiceInfo> {

    @RpcService
    @DAOMethod(sql = "from RpcServiceInfo where serviceName =:serviceName")
    public abstract RpcServiceInfo getByOrganIdAndServiceName(@DAOParam("serviceName") String serviceName);

    @DAOMethod
    public abstract RpcServiceInfo getByServiceName(String serviceName);

    /**
     * 查询所有服务信息
     * @return
     */
    @DAOMethod(sql = "from RpcServiceInfo where 1=1")
    public abstract List<RpcServiceInfo> findAll();
}
