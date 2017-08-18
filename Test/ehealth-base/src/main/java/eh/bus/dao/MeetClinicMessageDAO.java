package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.MeetClinicMsg;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public abstract class MeetClinicMessageDAO extends HibernateSupportDelegateDAO<MeetClinicMsg> {
    private static final Log log = LogFactory.getLog(MeetClinicMessageDAO.class);

    public MeetClinicMessageDAO() {
        super();
        setEntityName(MeetClinicMsg.class.getName());
        setKeyField("id");
    }

    @DAOMethod(sql = "FROM MeetClinicMsg WHERE meetClinicId=:meetClinicId")
    public abstract List<MeetClinicMsg> findMessageListByMeetClinicId(
            @DAOParam("meetClinicId") Integer meetClinicId);


}
