package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.mpi.PatientLastBehavior;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/7/14.
 */
public abstract class PatientLastBehaviorDAO extends HibernateSupportDelegateDAO<PatientLastBehavior> {

    private static final Log logger = LogFactory.getLog(PatientLastBehaviorDAO.class);

    public PatientLastBehaviorDAO() {
        super();
    }

    @DAOMethod
    public abstract PatientLastBehavior getByMpiId(String mpiId);

    public boolean saveOrUpdate(PatientLastBehavior behavior){
        if(StringUtils.isNotEmpty(behavior.getMpiId())) {
            PatientLastBehavior dbBehavior = this.getByMpiId(behavior.getMpiId());
            if(null == dbBehavior){
                save(behavior);
            }else{
                if(null != behavior.getAddressID()){
                    dbBehavior.setAddressID(behavior.getAddressID());
                }
                if(null != behavior.getReachHomeMode()){
                    dbBehavior.setReachHomeMode(behavior.getReachHomeMode());
                }
                if(null != behavior.getReachHosMode()){
                    dbBehavior.setReachHosMode(behavior.getReachHosMode());
                }

                update(dbBehavior);
            }
        }

        return true;
    }
}
