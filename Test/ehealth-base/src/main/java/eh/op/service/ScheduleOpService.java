package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointScheduleDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointSchedule;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * User:houxr
 * Time:2016/05/23 15:20:31
 * Desc:运营平台排班服务
 */
public class ScheduleOpService {

    private static final Log logger = LogFactory.getLog(ScheduleOpService.class);

    /**
     * 新增排班信息服务
     *
     * @param a 新增的排班信息组成的对象
     * @return Integer 新增成功返回排班序号
     * @author houxr
     * @date 2016-05-23
     */
    @RpcService
    public Integer addOneSchedule(AppointSchedule a) {
        logger.info("新增排班信息 <============ addOneSchedule ===========> AppointSchedule a:"
                + JSONUtils.toString(a));
        AppointScheduleDAO appointScheduleDAO = DAOFactory.getDAO(AppointScheduleDAO.class);
        if (a == null || a.getDoctorId() == null || a.getDepartId() == null
                || a.getOrganId() == null || a.getSourceNum() == null
                || a.getSourceNum() <= 0 || a.getSourceType() == null
                || a.getClinicType() == null || a.getTelMedFlag() == null
                || a.getTelMedType() == null || a.getMaxRegDays() == null
                || a.getMaxRegDays() <= 0 || a.getWeek() == null
                || a.getWorkType() == null || a.getEndTime() == null
                || a.getStartTime() == null || a.getUseFlag() == null
                || a.getEndTime().before(a.getStartTime())
                || StringUtils.isEmpty(a.getWorkAddr())
                || StringUtils.isEmpty(a.getAppointDepart())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "one or more parameter is required!");
        }
        Date ymd = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
        String startTime = DateConversion.getDateFormatter(a.getStartTime(), "HH:mm:ss");
        String endTime = DateConversion.getDateFormatter(a.getEndTime(), "HH:mm:ss");
        Date s = DateConversion.getDateByTimePoint(ymd, startTime);
        Date e = DateConversion.getDateByTimePoint(ymd, endTime);
        a.setStartTime(s);
        a.setEndTime(e);
        AppointSchedule ad = appointScheduleDAO.save(a);
        //增加排班记录操作日志
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.get(a.getOrganId());
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(a.getDoctorId());
        if (ad == null) {
            return null;
        }
        String str = "给[" + (organ==null?"":organ.getShortName()) + "]机构的医生[" + (doctor==null?"":doctor.getName()) + "]添加一条排班记录";
        BusActionLogService.recordBusinessLog("医生排班", ad.getScheduleId()+"", "AppointSchedule",
                str);
        return ad.getScheduleId();
    }

}
