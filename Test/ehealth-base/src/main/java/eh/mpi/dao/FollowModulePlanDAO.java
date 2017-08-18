package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.FollowModulePlan;

import java.util.List;

/**
 * @author renzh
 * @date 2016/10/9 0009 下午 15:06
 */
public abstract class FollowModulePlanDAO extends HibernateSupportDelegateDAO<FollowModulePlan> {

    public FollowModulePlanDAO(){
        super();
        this.setEntityName(FollowModulePlan.class.getName());
        this.setKeyField("mpid");
    }

    @DAOMethod(sql = "from FollowModulePlan where mid = :mid")
    public abstract List<FollowModulePlan> findByMid(@DAOParam("mid") Integer mid);

    @RpcService
    @DAOMethod()
    public abstract void deleteByMpid(Integer mpid);

    @DAOMethod
    public  abstract FollowModulePlan getByMpid(Integer mpid);

    @RpcService
    @DAOMethod()
    public abstract void deleteByMid(Integer mid);

}
