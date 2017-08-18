package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.CheckRequest;
import eh.entity.his.fee.OutpatientListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by renzk on 2017/2/14.
 */
public abstract class OutpatientResponseDAO extends
        HibernateSupportDelegateDAO<OutpatientListResponse> {
    private static final Logger logger = LoggerFactory.getLogger(OutpatientResponseDAO.class);

    public OutpatientResponseDAO() {
        super();
        this.setEntityName(OutpatientListResponse.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    @DAOMethod
    public abstract OutpatientListResponse getById(Integer Id);

    @RpcService
    @DAOMethod(sql = "from OutpatientListResponse where OrderID=:OrderID and OrganId=:OrganId and PatientId=:PatientId")
    public abstract OutpatientListResponse getByOrderIDAndOrganId(@DAOParam("OrderID")String OrderID, @DAOParam("OrganId")String OrganId, @DAOParam("PatientId")String PatientId);

    @RpcService
    @DAOMethod
    public abstract List<OutpatientListResponse> findByStay(String Stay);

    @DAOMethod(sql = "from OutpatientListResponse where Mpi=:Mpi and PayFlag=:PayFlag")
    public abstract List<OutpatientListResponse> findByMpi(@DAOParam("Mpi")String Mpi, @DAOParam("PayFlag")String PayFlag);

    @RpcService
    @DAOMethod(sql = "update OutpatientListResponse set PayFlag=:PayFlag where Mpi =:Mpi and OrganId =:OrganId and OrderID =:OrderID")
    public abstract void updatePayFlagByMpiAndOrganIdAndOrderID(@DAOParam("PayFlag") String PayFlag,@DAOParam("Mpi") String Mpi,@DAOParam("OrganId") String OrganId,@DAOParam("OrderID") String OrderID);
}
