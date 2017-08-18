package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.CheckSmsInfo;
/**
 * Created by zhangxq on 2016-5-28.
 */
public abstract class CheckSmsInfoDAO extends HibernateSupportDelegateDAO<CheckSmsInfo> {
    @RpcService
    @DAOMethod
    public abstract CheckSmsInfo getByOrgan(Integer organ);



    @RpcService
    @DAOMethod(sql = " from CheckSmsInfo where organ=:organ and bussType=:bussType ")
    public abstract CheckSmsInfo getByOrganAndBussType(@DAOParam("organ")Integer organ, @DAOParam("bussType")String bussType);

}
