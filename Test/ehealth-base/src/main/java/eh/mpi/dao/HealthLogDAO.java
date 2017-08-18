package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.HealthLog;
import org.apache.log4j.Logger;

/**
 * Created by renzk on 2017/6/15.
 */
public abstract class HealthLogDAO extends HibernateSupportDelegateDAO<HealthLog> {
    public static final Logger log = Logger.getLogger(HealthLogDAO.class);

    public HealthLogDAO() {
        super();
        this.setEntityName(HealthLog.class.getName());
        this.setKeyField("healthLogId");
    }

    @RpcService
    @DAOMethod(sql = "from HealthLog where mpiId=:mpiId")
    public abstract HealthLog getHealthLogByMpiId(@DAOParam("mpiId")String mpiId);

    public HealthLog addHealthLog(HealthLog healthLog){
        return save(healthLog);
    }
}
