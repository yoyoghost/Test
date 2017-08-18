package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.WxConfig;

public abstract class WxConfigDAO extends HibernateSupportDelegateDAO<WxConfig> {

    public WxConfigDAO() {
        super();
        this.setEntityName(WxConfig.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    @DAOMethod
    public abstract WxConfig getByAppid(String appid);


}
