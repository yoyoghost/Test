package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.LabReportId;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Created by zhangz on 2016/8/9.
 */
public abstract class LabReportIdDAO extends HibernateSupportDelegateDAO<LabReportId> {
    private static final Log logger = LogFactory.getLog(LabReportIdDAO.class);

    public LabReportIdDAO(){
        super();
        this.setEntityName(LabReportId.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    @DAOMethod()
    public abstract LabReportId getByIdNumber(String idnumber);

    @RpcService
    @DAOMethod()
    public abstract LabReportId getByOrganIdAndReportId(Integer organId, String reportId);

    @RpcService
    public void addLabReportId(LabReportId rep) {
        if (rep !=null){
            this.save(rep);
        }
    }

}
