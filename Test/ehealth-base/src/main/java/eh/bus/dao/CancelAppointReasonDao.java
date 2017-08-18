package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.bus.CancelAppointReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by cqian on 2017/6/21.
 */
@RpcSupportDAO
public abstract class CancelAppointReasonDao extends
        HibernateSupportDelegateDAO<CancelAppointReason> {

    private static final Logger log = LoggerFactory.getLogger(CallRecordDAO.class);
    private Logger logger = LoggerFactory.getLogger(CancelAppointReasonDao.class);

    public CancelAppointReasonDao() {
        super();
        this.setEntityName(CancelAppointReason.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "from CancelAppointReason order by COALESCE (updateTime,updateTime) desc")
    public abstract <T> List<T> findAll();
}
