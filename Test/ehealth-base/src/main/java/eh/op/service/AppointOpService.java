package eh.op.service;


import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointScheduleDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.CloudClinicSetDAO;
import eh.bus.service.AppointService;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointRecordAndDoctors;
import eh.entity.bus.AppointRecordAndPatientAndDoctor;
import eh.entity.bus.CloudClinicSet;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.op.auth.service.SecurityService;
import eh.utils.DateConversion;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

/**
 * 运营平台预约相关接口服务
 * User:houxr
 * Time:2016/5/24 15:20:32
 */
public class AppointOpService {
    private static final Log logger = LogFactory.getLog(AppointOpService.class);


    /**
     * 运营平台预约信息统计查询
     * 新增 申请类型 云门诊查询功能
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param appointRecord 预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return List<AppointRecordAndDoctors>
     * @author houxr
     * @date 2016-5-26
     */
    @RpcService
    public QueryResult<AppointRecordAndDoctors> findAppointRecordAndDoctorsForOP(
            final Integer timeType, final Date startTime, final Date endTime,
            final Integer telClinicFlag, final String mpiId, final AppointRecord appointRecord,
            final List<Integer> appointOragns, final List<Integer> organIds,
            final int start, final int limit) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        QueryResult<AppointRecordAndDoctors> checkRequests = appointRecordDAO.findAppointRecordAndDoctorsForOP(
                timeType, startTime, endTime, telClinicFlag, mpiId, appointRecord, appointOragns, organIds, start, limit);
        return checkRequests;
    }

    /**
     * 运营平台 预约记录相关对象查询
     * <p>
     * 运营平台（权限改造）
     *
     * @param appointRecordId
     * @return
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor queryAppointRecordByAppointRecordId(final Integer appointRecordId) {
        if (appointRecordId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointRecordId is required!");
        }
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        CloudClinicSetDAO clinicSetDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);

        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);

        if (appointRecord == null) {
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        String appointOrgan = appointRecord.getAppointOragn();
        if (!StringUtils.isEmpty(appointOrgan)) {
            try {
                o.add(Integer.parseInt(appointOrgan));
            } catch (Exception e) {

            }
        }
        o.add(appointRecord.getOrganId());
        Integer oppOrgan = appointRecord.getOppOrgan();
        if (oppOrgan != null) {
            o.add(oppOrgan);
        }
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        if (appointRecord.getMpiid() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointRecordId.getMpiid() is null!");
        }
        if (appointRecord.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointRecord.getDoctorId() is null!");
        }
        Patient patient = patientDAO.getPatientByMpiId(appointRecord.getMpiid());
        Doctor doctor = doctorDAO.getByDoctorId(appointRecord.getDoctorId());
        Doctor requestDoctor = new Doctor();
        Doctor oppDoctor = new Doctor();
        Patient requestPatient = new Patient();
        //CloudClinicSet oppDocSet = new CloudClinicSet();
        String appointUser = appointRecord.getAppointUser();
        //判断是医生帮患者预约还是患者家属或患者自己预约
        if (!appointUser.isEmpty() && appointUser.length() < 32) {
            requestDoctor = doctorDAO.getByDoctorId(Integer.parseInt(appointUser));
        } else {
            requestPatient = patientDAO.getPatientByMpiId(appointUser);
        }
        //诊疗对方类型 1医院
        if (appointRecord.getOppType() != null && appointRecord.getOppType() == 1) {
            if (appointRecord.getOppdoctor() != null) {
                oppDoctor = doctorDAO.getByDoctorId(appointRecord.getOppdoctor());
            }
        }
        CloudClinicSet oppDocSet = null;//clinicSetDAO.getDoctorSet(appointRecord.getDoctorId());
        AppointRecordAndPatientAndDoctor arpd = new AppointRecordAndPatientAndDoctor(
                appointRecord, patient, requestPatient,
                doctor, requestDoctor, oppDoctor, oppDocSet);
        return arpd;
    }

    /**
     * 运营平台取消预约服务
     *
     * @param appointRecordId 预约记录主键
     * @param cancelUser      取消人Id
     * @param cancelName      取消人姓名
     * @param cancelResean    取消原因
     * @return
     */
    @RpcService
    public boolean cancelAppoint(final int appointRecordId, final String cancelUser, final String cancelName,
                                 final String cancelResean) {
        BusActionLogService.recordBusinessLog("业务单取消", String.valueOf(appointRecordId),
                "AppointRecord", "预约单[" + appointRecordId + "]被取消");
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        return appointRecordDAO.cancelAppoint(appointRecordId, cancelUser, cancelName, cancelResean);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(final Integer timeType, final Date startTime, final Date endTime,
                                                          final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                          final List<Integer> appointOragns, final List<Integer> organIds,
                                                          final int start, final int limit) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        return appointRecordDAO.getStatisticsByStatus(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByAppointRoad(final Integer timeType, final Date startTime, final Date endTime,
                                                               final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                               final List<Integer> appointOragns, final List<Integer> organIds,
                                                               final int start, final int limit) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        return appointRecordDAO.getStatisticsByAppointRoad(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                                final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                                final List<Integer> appointOragns, final List<Integer> organIds,
                                                                final int start, final int limit) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        return appointRecordDAO.getStatisticsByRequestOrgan(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                               final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                               final List<Integer> appointOragns, final List<Integer> organIds,
                                                               final int start, final int limit) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        return appointRecordDAO.getStatisticsByTargetOrgan(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);

    }

    /**
     * 将该机构的所有平台排班，停班并停诊；
     * 所有HIS传入的号源停诊
     * （当天以后）
     *
     * @param organId 机构ID
     */
    @RpcService
    public void stopAllSourceByOrganId(Integer organId) {
        logger.info("将机构【" + organId + "】全部排班和号源停诊");
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " organId is require");
        }
        AppointScheduleDAO appointScheduleDAO = DAOFactory.getDAO(AppointScheduleDAO.class);
        List<Integer> schedules = appointScheduleDAO.findScheduleIdByOrganId(organId);
        AppointService appointService = AppContextHolder.getBean("appointService", AppointService.class);
        if (schedules != null && schedules.size() > 0) {
            appointService.updateScheduleAndSourceStopOrNot(schedules, 1, true);//将该机构的所有有效普通排班停班
        }
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);

        List<Integer> sourceIds = appointSourceDAO.findSourceIdByOrganId(organId);
        if (sourceIds != null && sourceIds.size() > 0) {
            appointService.updateSourcesStopOrNotNew(sourceIds, 1);
        }
        appointSourceDAO.updateHisSourceByOrganId(organId);//将所有his号源停诊
    }

    @RpcService
    public QueryResult<AppointRecord> queryCloudClinic(Integer appointOrgan, Integer appointDoctor, Integer organ,
                                                       Integer doctor, Integer oppOrgan, Integer oppDoctor, Integer dateType,final Date startDate, final Date endDate
            , String mpiId, Integer status, Integer telClinicFlag, final int start, final int limit,Integer offLineAccount) {

        if (dateType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "dateType is require");
        }
        if (startDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startDate is require");
        }
        if (endDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "endDate is require");
        }
        StringBuffer sb = new StringBuffer(" from AppointRecord a " +
                "where a.clinicObject=1 ");
        if (dateType.equals(1)) {
            sb.append(" and a.appointDate>=:startDate and a.appointDate<=:endDate ");
        } else {
            sb.append(" and a.workDate>=:startDate and a.workDate<=:endDate ");
        }
        final Map<String, Object> parameters = new HashMap<String, Object>();
        if (appointOrgan != null) {
            sb.append(" and a.appointOragn=:appointOragn");
            parameters.put("appointOragn", appointOrgan.toString());
        }
        if (appointDoctor != null) {
            sb.append(" and a.appointUser=:appointUser");
            parameters.put("appointUser", appointDoctor.toString());
        }
        if (organ != null) {
            sb.append(" and a.organId=:organId");
            parameters.put("organId", organ);
        }
        if (doctor != null) {
            sb.append(" and a.doctorId=:doctorId");
            parameters.put("doctorId", doctor);
        }
        if (oppOrgan != null) {
            sb.append(" and a.oppOrgan=:oppOrgan");
            parameters.put("oppOrgan", oppOrgan);
        }
        if (oppDoctor != null) {
            sb.append(" and a.oppdoctor=:oppdoctor");
            parameters.put("oppdoctor", oppDoctor);
        }
        if (!StringUtils.isEmpty(mpiId)) {
            sb.append(" and a.mpiid=:mpiid");
            parameters.put("mpiid", mpiId);
        }
        if (status != null) {
            sb.append(" and a.appointStatus=:appointStatus");
            parameters.put("appointStatus", status);
        }
        if(offLineAccount!=null){
            if (offLineAccount == 0) {
                sb.append(" and a.offLineAccount is null ");
            } else {
                sb.append(" and a.offLineAccount=:offLineAccount");
                parameters.put("offLineAccount",offLineAccount);
            }

        }
        if (telClinicFlag == null) {
            sb.append(" and a.telClinicFlag in (1,2)");
        } else {
            sb.append(" and a.telClinicFlag =:telClinicFlag");
            parameters.put("telClinicFlag", telClinicFlag);
        }
        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<AppointRecord>> action
                = new AbstractHibernateStatelessResultAction<QueryResult<AppointRecord>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query cQuery = ss.createQuery(" select count(*)" + hql);
                Query query = ss.createQuery(hql + " order By a.appointRecordId desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                cQuery.setParameter("startDate", startDate);
                query.setParameter("startDate", startDate);
                Date lDate = DateConversion.getDateAftXDays(endDate, 1);
                cQuery.setParameter("endDate", lDate);
                query.setParameter("endDate", lDate);
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    cQuery.setParameter(entry.getKey(), entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
                Long total = (Long) cQuery.uniqueResult();
                setResult(new QueryResult<AppointRecord>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


}
