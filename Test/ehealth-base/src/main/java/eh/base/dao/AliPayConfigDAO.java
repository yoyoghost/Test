package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.AliPayConfig;
import org.apache.log4j.Logger;

import java.util.List;

/**
 *
 * @author jianghc
 * @create 2016-11-07 11:03
 **/
public abstract class AliPayConfigDAO extends HibernateSupportDelegateDAO<AliPayConfig> {
    public static final Logger log = Logger.getLogger(AliPayConfigDAO.class);

    public AliPayConfigDAO(){
        super();
        this.setEntityName(AliPayConfig.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod
    public abstract AliPayConfig getById(Integer id);

    @DAOMethod
    public abstract AliPayConfig getByAppID(String appID);

    @DAOMethod(sql = "delete from AliPayConfig where id =:id")
    public abstract void deleteOneById( @DAOParam("id") Integer id );

    @RpcService
    @DAOMethod(sql = " from AliPayConfig")
    public abstract List<AliPayConfig> findAllConfig(@DAOParam(pageStart = true) int start,
                                                     @DAOParam(pageLimit = true) int limit);
}
