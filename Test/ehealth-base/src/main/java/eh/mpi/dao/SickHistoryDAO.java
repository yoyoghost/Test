package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.SickHistory;
import org.apache.log4j.Logger;

/**
 * Created by Administrator on 2017-04-14.
 */
public abstract class SickHistoryDAO extends HibernateSupportDelegateDAO<SickHistory> {

    public static final Logger log = Logger.getLogger(SickHistoryDAO.class);
    public SickHistoryDAO() {
        super();
        this.setEntityName(SickHistory.class.getName());
        this.setKeyField("sickHistoryId");
    }
    @RpcService
    @DAOMethod(sql = "from SickHistory where mpiId=:mpiId")
    public abstract SickHistory getSickHistoryByMpiId(@DAOParam("mpiId")String mpiId);


}

