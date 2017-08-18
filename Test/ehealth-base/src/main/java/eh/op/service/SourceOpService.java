package eh.op.service;


import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointDepartDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointSource;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 运营平台号源服务
 */
public class SourceOpService {
    private static final Log logger = LogFactory.getLog(SourceOpService.class);

    /**
     * 新增号源信息
     *
     * @param a 新增的号源信息组成的对象
     * @return List<Integer> 新增成功返回号源序号列表
     * @author houxr
     * @date 2016-05-23
     */
    @RpcService
    public List<Integer> addOneSource(AppointSource a, Integer startNum) {
        logger.info("新增号源信息AppointSourceDAO==> addOneSource <===" + "a:" + JSONUtils.toString(a));
        if (a == null || a.getDoctorId() == null || a.getOrganId() == null
                || a.getSourceNum() == null || a.getSourceNum() <= 0
                || a.getSourceType() == null || a.getWorkType() == null
                || a.getEndTime() == null || a.getStartTime() == null
                || a.getWorkDate() == null
                || a.getEndTime().before(a.getStartTime())
                || StringUtils.isEmpty(a.getAppointDepartCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointSource is required");
        }
        if (a.getStopFlag() == null) {
            a.setStopFlag(0);
        }
        if (StringUtils.isEmpty(a.getAppointDepartName())) {
            a.setAppointDepartName(DAOFactory
                    .getDAO(AppointDepartDAO.class)
                    .getByOrganIDAndAppointDepartCode(a.getOrganId(),
                            a.getAppointDepartCode()).getAppointDepartName());
        }
        if(StringUtils.isEmpty(a.getOrganSchedulingId())){
            a.setOrganSchedulingId(a.getDoctorId()
                    +DateConversion.getDateFormatter(a.getWorkDate(),"yyyyMMdd")
                    +a.getWorkType());
        }
        a.setFromFlag(1);
        a.setUsedNum(0);
        a.setCreateDate(new Date());
        List<Integer> ids = new ArrayList<Integer>();
        int avg = a.getSourceNum();
        List<Object[]> os = DateConversion.getAverageTime(a.getStartTime(), a.getEndTime(), avg);
        Integer docId = a.getDoctorId();
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        Integer orderNum = appointSourceDAO.getMaxOrderNum(docId, a.getOrganId(), a.getWorkDate());
        if (startNum != null) {
            orderNum = startNum - 1;//设置 起始号源
        }
        if (orderNum == null) {
            orderNum = 0;
        }
        a.setSourceNum(1);
        for (Object[] o : os) {
            AppointSource appointSource = a;
            appointSource.setStartTime((Date) o[0]);
            appointSource.setEndTime((Date) o[1]);
            appointSource.setOrderNum(++orderNum);
            ids.add(appointSourceDAO.save(appointSource).getAppointSourceId());
        }
        //增加号源记录操作日志
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(docId);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.get(doctor.getOrgan());
        BusActionLogService.recordBusinessLog("医生号源", docId.toString(), "AppointSource",
                "给[" + organ.getShortName() + "]机构的医生[" + doctor.getName() + "]添加" + avg + "个号源");

        if (ids.size() <= 0) {
            return null;
        }
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = dDao.get(docId);
        d.setHaveAppoint(1);
        dDao.update(d);
        return ids;
    }


}
