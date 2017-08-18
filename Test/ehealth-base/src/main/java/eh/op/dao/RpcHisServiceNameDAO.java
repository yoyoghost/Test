package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.RpcHisServiceName;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Created by andywang on 2017/1/16.
 */
public abstract class  RpcHisServiceNameDAO  extends HibernateSupportDelegateDAO<RpcHisServiceName> {
    public static final Logger log = Logger.getLogger(WechatModuleMessageDAO.class);

    public RpcHisServiceNameDAO() {
        super();
        this.setEntityName(RpcHisServiceName.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql="from RpcHisServiceName where 1=1 order by id")
    public abstract List<RpcHisServiceName> findAllRpcServiceName();
}
