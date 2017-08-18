package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganCheckItemDAO;
import eh.bus.dao.CheckAppointItemDAO;
import eh.bus.dao.CheckRequestDAO;
import eh.entity.base.Doctor;
import eh.entity.base.OrganCheckItem;
import eh.entity.bus.CheckAppointItem;
import eh.entity.bus.CheckRequest;
import eh.entity.bus.CheckRequestAndPatientAndDoctor;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.op.auth.service.SecurityService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * User:houxr
 * Date:2016/5/25 16:20:31
 * 运营平台 医技检查相关服务
 */
public class CheckRequestOpService {
    private static final Log logger = LogFactory.getLog(CheckRequestOpService.class);

    /**
     * 根据organId查询医技检查项目名称
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<OrganCheckItem> findOrganCheckItemByOrganId(Integer organId) {
        OrganCheckItemDAO organCheckItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        List<OrganCheckItem> organCheckItems = organCheckItemDAO.findByOrganId(organId);
        return organCheckItems;
    }

    /**
     * 运营平台医技检查条件查询记录
     *
     * @param timeType     查询时间类型
     * @param startTime    起始时间
     * @param endTime      结束时间
     * @param checkRequest 检查单
     * @param start        分页起始位置
     * @param limit        条数
     * @return
     */
    @RpcService
    public QueryResult<CheckRequest> queryCheckListForOP(
            final Integer timeType, final Date startTime, final Date endTime,
            final CheckRequest checkRequest, final Integer start, final Integer limit) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        QueryResult<CheckRequest> checkRequests = null;
        if (checkRequest != null) {
            checkRequests = checkRequestDAO.queryCheckListForOP(timeType, startTime, endTime, checkRequest, start, limit);
        }
        return checkRequests;
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(final Integer timeType, final Date startTime, final Date endTime,
                                                          final CheckRequest checkRequest, final Integer start, final Integer limit) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        return checkRequestDAO.getStatisticsByStatus(timeType, startTime, endTime, checkRequest, start, limit);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByCheckItemName(final Integer timeType, final Date startTime, final Date endTime,
                                                                 final CheckRequest checkRequest, final Integer start, final Integer limit) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        return checkRequestDAO.getStatisticsByCheckItemName(timeType, startTime, endTime, checkRequest, start, limit);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                                final CheckRequest checkRequest, final Integer start, final Integer limit) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        return checkRequestDAO.getStatisticsByRequestOrgan(timeType, startTime, endTime, checkRequest, start, limit);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                               final CheckRequest checkRequest, final Integer start, final Integer limit) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        return checkRequestDAO.getStatisticsByTargetOrgan(timeType, startTime, endTime, checkRequest, start, limit);
    }

    /**
     * 医技检查查询详情页的接口调用
     * <p>
     * 运营平台（权限改造）
     *
     * @param checkRequestId
     * @return
     */
    @RpcService
    public CheckRequestAndPatientAndDoctor getCheckRequestDetailByCheckRequestId(final Integer checkRequestId) {
        if (checkRequestId == null) {
//            logger.error("查询条件不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "查询条件不能为空");
        }
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckRequest checkRequest = checkRequestDAO.getByCheckRequestId(checkRequestId);

        if (checkRequest == null) {
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(checkRequest.getRequestOrgan());
        o.add(checkRequest.getOrganId());
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }

        OrganCheckItemDAO organCheckItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        OrganCheckItem organCheckItem = organCheckItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(checkRequest.getOrganId(), checkRequest.getCheckItemId(), checkRequest.getCheckAppointId());
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getPatientByMpiId(checkRequest.getMpiid());
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        CheckAppointItemDAO checkAppointItemDao = DAOFactory.getDAO(CheckAppointItemDAO.class);
        CheckAppointItem checkAppointItem = checkAppointItemDao.get(organCheckItem.getCheckAppointId());
        Doctor doctor = doctorDAO.getByDoctorId(checkRequest.getRequestDoctorId());
        CheckRequestAndPatientAndDoctor crpd = new CheckRequestAndPatientAndDoctor(checkRequest, organCheckItem, checkAppointItem, patient, doctor);
        return crpd;
    }


}
