package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.Emergency;

import java.util.List;

/**
 * Created by Administrator on 2017/6/2 0002.
 */
public abstract class EmergencyDao extends HibernateSupportDelegateDAO<Emergency> {

    public EmergencyDao() {
        super();
        setEntityName(Emergency.class.getName());
        setKeyField("emergencyId");
    }

    @DAOMethod(sql = "FROM Emergency WHERE requestMpi=:mpiId AND status=1 ORDER BY id DESC")
    public abstract List<Emergency> findUnPayEmergencyList(@DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "FROM Emergency WHERE requestMpi=:mpiId AND status in (1,2) ORDER BY id DESC")
    public abstract List<Emergency> findAllEmergencyList(@DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "FROM Emergency WHERE requestMpi=:mpiId AND status=:status ORDER BY id DESC", limit = 1)
    public abstract List<Emergency> findTemporaryEmergencyByRequestMpi(@DAOParam("mpiId") String mpiId,
                                                                      @DAOParam("status") Integer status);
}
