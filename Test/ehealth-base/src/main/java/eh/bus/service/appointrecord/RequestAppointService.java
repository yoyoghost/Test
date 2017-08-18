package eh.bus.service.appointrecord;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.account.session.SessionItemManager;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.constant.OrganConstant;
import eh.bus.dao.AppointControlDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.service.HisRemindService;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointSource;
import eh.entity.bus.DoctorDateSource;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariPayService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by luf on 2016/6/22.
 */

public class RequestAppointService {
    private static final Log logger = LogFactory.getLog(RequestAppointService.class);

    /**
     * 云门诊预约申请-不支持付费
     *
     * @param appointRecords
     * @return Integer
     * @author luf
     */
    @RpcService
    public Integer addAppointRecordForCloudClinic(final List<AppointRecord> appointRecords) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Integer outDoctorId = 0;
        for (AppointRecord ar : appointRecords) {
            if (ar.getClinicObject() == 2) {
                outDoctorId = ar.getDoctorId();
            }
        }
        if (organDAO.getCloudClinicPriceByOrgan(outDoctorId) != 0) {//2017-04-17 15:14:16 付费机构老接口不支持
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "由于版本过低，无法在线扫码支付，请立即更新");
        } else {
            return addCloudClinicOrderPay(appointRecords);
        }
    }

    /**
     * 云门诊预约申请-支持付费
     *
     * @param appointRecords
     * @return Integer
     * @author zhangsl 2017-04-18 09:41:21
     */
    @RpcService
    public Integer addCloudClinicOrderPay(final List<AppointRecord> appointRecords) {
        final AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String telClinicId = String.valueOf(System.currentTimeMillis())
                        + String.valueOf(ThreadLocalRandom.current().nextInt(
                        1000, 9999));

                if (appointRecords == null || appointRecords.size() <= 0) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "appointRecords is required");
                }

                //获取接诊方号源就诊地址
                String confirmClinicAddr = null;
                AppointRecord inAppoint = new AppointRecord();
                for (int i = 0; i < appointRecords.size(); i++) {
                    AppointRecord arr = appointRecords.get(i);
                    if (arr.getClinicObject() != null && arr.getClinicObject() == 1) {//取接诊方号源
                        inAppoint = arr;
                        Integer sourceId = arr.getAppointSourceId();
                        confirmClinicAddr = arr.getConfirmClinicAddr();
                        if (sourceId != null && sourceId != 0 && StringUtils.isEmpty(confirmClinicAddr)) {
                            AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
                            AppointSource appointSource = dao.getByAppointSourceId(sourceId);
                            if (appointSource != null && appointSource.getClinicAddr() != null) {
                                confirmClinicAddr = appointSource.getClinicAddr();
                            } else {
                                confirmClinicAddr = DictionaryController.instance()
                                        .get("eh.base.dictionary.Organ").getText(arr.getOrganId());
                            }
                        } else {
                            if (sourceId == null || sourceId == 0) {//接诊方号源为空的情况下显示执业点
                                confirmClinicAddr = DictionaryController.instance()
                                        .get("eh.base.dictionary.Organ").getText(arr.getOrganId());
                            }

                        }
                    }
                }

                AppointSourceDAO AppointSourceDAO = DAOFactory
                        .getDAO(AppointSourceDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                if (null != inAppoint && !StringUtils.isEmpty(inAppoint.getAppointUser()) && 32 != inAppoint.getAppointUser().length()
                        && null != inAppoint.getDoctorId() && 0 <= inAppoint.getDoctorId()) {
                    Date start = inAppoint.getStartTime();
                    Date end = inAppoint.getEndTime();
                    // 去除当天提前一小时预约条件
                    if (null == start || start.before(DateConversion.getFormatDate(new Date(), "yyyy-MM-dd"))) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "startTime is required!");
                    }
                    if (null == end || end.before(DateConversion.getFormatDate(new Date(), "yyyy-MM-dd"))) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "endTime is required!");
                    }
                    Integer redoc = Integer.valueOf(inAppoint.getAppointUser());
                    Integer indoc = inAppoint.getDoctorId();
                    Long count = dao.countTimeConflict(indoc, start, end);
                    if (null != count && 0 < count) {
                        if (indoc.equals(redoc)) {
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "您同一时间已约远程联合门诊，请更换接诊医生或选择其他号源！");
                        } else {
                            String inName = doctorDAO.getNameById(indoc);
                            throw new DAOException(ErrorCode.SERVICE_ERROR, inName + "医生同一时间已约远程联合门诊，请更换接诊医生或选择其他号源！");
                        }
                    }
                }

                Boolean result = false;
                for (AppointRecord appointRecord : appointRecords) {
                    appointRecord.setConfirmClinicAddr(confirmClinicAddr);//设置就诊地址

                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    Patient p = patientDAO.get(appointRecord.getMpiid());
                    // 入参没有手机号，则后台去取
                    if (StringUtils.isEmpty(appointRecord.getLinkTel())) {
                        appointRecord.setLinkTel(p.getMobile());
                    }
                    if (StringUtils.isEmpty(appointRecord.getCertId())) {
                        appointRecord.setCertId(p.getIdcard());
                    }
                    //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
                    if (StringUtils.isEmpty(appointRecord.getCertId())) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能预约");
                    }

                    if (StringUtils.isEmpty(appointRecord.getPatientName())) {
                        appointRecord.setPatientName(p.getPatientName());
                    }

                    // 检验预约记录信息
                    isValidAppointRecordData(appointRecord);
                    isBussRepeat(appointRecord);
                    AppointSource appointSourceBeforeSave = AppointSourceDAO
                            .getById(appointRecord.getAppointSourceId());

                    // 保存预约记录
                    appointRecord.setAppointDate(new Date());
                    appointRecord.setAppointStatus(9);// 9预约中,
                    appointRecord.setTelClinicFlag(1);// 云门诊标记:0线下,1 远程
                    appointRecord.setClinicStatus(0);// 就诊状态:0等待中,1就诊 中,2就诊结束
                    appointRecord.setTelClinicId(telClinicId);
                    appointRecord.setRecordType(0);//0预约1当天挂号
                    appointRecord.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49
                    appointRecord.setPayFlag(0);//支付状态默认null改为默认0
                    if (appointSourceBeforeSave != null) {
                        appointRecord
                                .setOrganSchedulingId(appointSourceBeforeSave
                                        .getOrganSchedulingId());
                    }
                    try {
                        appointRecord.setDeviceId(SessionItemManager.instance().checkClientAndGet());
                    } catch (Exception e) {
                        logger.info(LocalStringUtil.format("addCloudClinicOrderPay get deviceId from session exception! errorMessage[{}]", e.getMessage()));
                    }

                    logger.info("保存预约记录,入参:"
                            + JSONUtils.toString(appointRecord));
                    AppointRecord ar = dao.save(appointRecord);

                    // 普通预约
                    if (appointSourceBeforeSave != null) {
                        int ordernum = appointSourceBeforeSave.getOrderNum();

                        // 已用号源大于号源数，即已无有效号源，则直接返回false
                        if ((appointSourceBeforeSave.getUsedNum() + 1) > appointSourceBeforeSave
                                .getSourceNum()) {
//                            setResult(null);
//                            break;
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "该号源已被约请重新选号！");
                        }

                        // 已用号源小于号源数，且剩余号源数大于1，则需插入预约记录、更新号源数、顺序数、更新医生表
                        else if ((appointSourceBeforeSave.getUsedNum() + 1) < appointSourceBeforeSave
                                .getSourceNum()) {
                            // 更新号源表【已用号源数】
                            int UesdNum = appointSourceBeforeSave.getUsedNum();
                            UesdNum += 1;
                            ordernum += 1;
                            logger.info("更新号源数顺序数,AppointSourceId:"
                                    + appointRecord.getAppointSourceId());
                            AppointSourceDAO.updateUsedNum(UesdNum, ordernum,
                                    appointRecord.getAppointSourceId());
                            // 更新医生表【是否有预约号源标志（1有号源 0无号源）】
                            List<DoctorDateSource> doctorAllSource = AppointSourceDAO
                                    .totalByDoctorDate(
                                            appointRecord.getDoctorId(),
                                            appointRecord.getSourceType());
                            long doctorSumSource = 0;
                            for (int i = 0; i < doctorAllSource.size(); i++) {
                                doctorSumSource = doctorSumSource
                                        + doctorAllSource.get(i).getSourceNum();
                            }
                            if (doctorSumSource > 0) {
                                doctorDAO.updateHaveAppointByDoctorId(
                                        appointRecord.getDoctorId(), 1);
                            } else {
                                logger.info("更新成无号源:doctorId:"
                                        + appointRecord.getDoctorId());
                                doctorDAO.updateHaveAppointByDoctorId(
                                        appointRecord.getDoctorId(), 0);
                            }
                        }
                        // 剩余号源数为1，即最后一个号源，则无需更新号源顺序号
                        else {
                            int UesdNum = appointSourceBeforeSave.getUsedNum();
                            UesdNum += 1;
                            AppointSourceDAO.updateUsedNum(UesdNum, ordernum,
                                    appointRecord.getAppointSourceId());
                            // 更新医生表【是否有预约号源标志（1有号源 0无号源）】
                            List<DoctorDateSource> doctorAllSource = AppointSourceDAO
                                    .totalByDoctorDate(
                                            appointRecord.getDoctorId(),
                                            appointRecord.getSourceType());
                            long doctorSumSource = 0;
                            for (int i = 0; i < doctorAllSource.size(); i++) {
                                doctorSumSource = doctorSumSource
                                        + doctorAllSource.get(i).getSourceNum();
                            }
                            if (doctorSumSource > 0) {
                                doctorDAO.updateHaveAppointByDoctorId(
                                        appointRecord.getDoctorId(), 1);
                            } else {
                                logger.info("更新成无号源:doctorId:"
                                        + appointRecord.getDoctorId());
                                doctorDAO.updateHaveAppointByDoctorId(
                                        appointRecord.getDoctorId(), 0);
                            }
                        }
                    }
                    if (null != ar.getClinicObject() && 2 == ar.getClinicObject()) {
                        result = true;
                        setResult(ar.getAppointRecordId());
                    }
                }
                if (!result) {
                    setResult(null);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        if (null != action.getResult() && 0 < action.getResult()) {
            if (appointRecords.get(0).getAppointRoad() == 5) {// 当是有号源的云门诊预约
                // 预约成功，云门诊校验是否通知his
                boolean isCloudClinicAppoint = cloudClinicAppointToHis(appointRecords);
                // 预约成功后需发送短息，校验是否需要预结算
                if (!isCloudClinicAppoint) {
                    dao.doAfterAddAppointRecordForCloudClinic(appointRecords);
                }
            }
        }

        return action.getResult();
    }

    /**
     * 校验云门诊预约是否需要发送his
     *
     * @param appointRecords
     * @return
     */
    public AppointRecord isCloudClinicAppointNeedToHis(List<AppointRecord> appointRecords) {
        AppointRecord appointRecord = null;
        for (AppointRecord appointRecordCloud : appointRecords) {
            //取出诊方预约记录信息发送到his
            if (appointRecordCloud.getClinicObject() != null && appointRecordCloud.getClinicObject() == 2) {
                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(appointRecordCloud.getOrganId());
                if (cfg != null && cfg.getCloudClinicNeedToHis() != null && cfg.getCloudClinicNeedToHis() == 1) {
                    appointRecord = appointRecordCloud;
                    break;
                }
            }
        }
        return appointRecord;
    }

    /**
     * 云门诊预约 并发送his
     *
     * @param appointRecords
     * @return
     */
    public boolean cloudClinicAppointToHis(List<AppointRecord> appointRecords) {
        boolean cloudClinicAppointToHis = false;
        AppointRecord appointRecord = isCloudClinicAppointNeedToHis(appointRecords);
        if (appointRecord != null) {
            logger.info("发起his预约转诊服务,AppointName=" + appointRecord.getAppointName());
            cloudClinicAppointToHis = true;
            AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient p = PatientDAO.getByMpiId(appointRecord.getMpiid());
            recordDAO.registAppoint(p, appointRecord);

            // 预约申请，给予推荐奖励,不管预约是否成功
            if (appointRecord.getAppointRoad() == 5 && appointRecord.getAppointUser().length() != 32) {
                DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
                accDao.recommendReward(Integer.parseInt(appointRecord.getAppointUser()));
            }
            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForAppoint(appointRecord);
        }
        return cloudClinicAppointToHis;
    }

    //预约校验是否为空
    private void isValidAppointRecordData(AppointRecord p) {
        if (StringUtils.isEmpty(p.getMpiid())) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(p.getPatientName())) {
//            logger.error("patientName is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }
        if (p.getAppointSourceId() == null) {
//            logger.error("appointSourceId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointSourceId is required");
        }
        if (p.getOrganId() == null) {
//            logger.error("organId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        // if (StringUtils.isEmpty(p.getAppointDepartId())) {
        // logger.error("appointDepartId is required");
        // throw new DAOException(DAOException.VALUE_NEEDED,
        // "appointDepartId is required");
        // }
        if (p.getWorkDate() == null) {
//            logger.error("workDate is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "workDate is required");
        }
        if (p.getWorkType() == null) {
//            logger.error("workType is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "workType is required");
        }
        if (p.getSourceType() == null) {
//            logger.error("sourceType is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sourceType is required");
        }
        if (p.getStartTime() == null) {
//            logger.error("startTime is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "startTime is required");
        }
        if (p.getEndTime() == null) {
//            logger.error("endTime is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "endTime is required");
        }
        if (p.getOrderNum() == null) {
//            logger.error("orderNum is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "orderNum is required");
        }
        if (p.getAppointRoad() == null) {
//            logger.error("appointRoad is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointRoad is required");
        }
        if (p.getAppointStatus() == null) {
//            logger.error("appointStatus is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointStatus is required");
        }
        if (StringUtils.isEmpty(p.getAppointUser())) {
//            logger.error("appointUser is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "appointUser is required");
        }
        if (null == p.getDoctorId() || 0 >= p.getDoctorId()) {
//            logger.error("doctorId is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }

    }

    //预约校验是否重复：医生端和患者端判断条件不同
    private void isBussRepeat(AppointRecord p) {
        if (32 != p.getAppointUser().length()) {
            if (SameUserMatching.patientAndDoctor(p.getMpiid(), p.getDoctorId())) {
//                logger.error("患者和预约医生不能为同一人");
                throw new DAOException(609, "患者和预约医生不能为同一人");
            }
        } else {
            //微信2.1.1需求
            if (SameUserMatching.patientAndDoctor(p.getMpiid(), p.getDoctorId())) {
//                logger.error("患者与目标医生不能为同一个人");
                throw new DAOException(609, "患者与目标医生不能为同一个人");
            }
//            String mpiId = p.getMpiid();
//            String reMpiId = p.getAppointUser();
//            Integer doctorId = p.getDoctorId();
//            Date requestDate = new Date();
//            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
//            if (p.getTransferId() != null && p.getTransferId() == 0) {
//                List<AppointRecord> ars = appointRecordDAO.findHasAppointByFour(mpiId, reMpiId, doctorId, requestDate);
//                if (ars != null && !ars.isEmpty()) {
//                    throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
//                }
//            }
        }
    }

    /**
     * 预约记录增加服务
     *
     * @param appointRecord
     * @return Integer
     * @author luf
     */
    @RpcService
    public Integer addAppointRecordNew(final AppointRecord appointRecord) {
        logger.info("addAppointRecordNew原始入参:" + JSONUtils.toString(appointRecord));
        //上海6元不支持挂号
        final HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        hisServiceConfigDao.disableAppoint(appointRecord.getOrganId());

        final AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = PatientDAO.getByMpiId(appointRecord.getMpiid());
        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(appointRecord.getLinkTel())) {
            appointRecord.setLinkTel(p.getMobile());
        }
        if (StringUtils.isEmpty(appointRecord.getCertId())) {
            appointRecord.setCertId(p.getIdcard());
        }
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (StringUtils.isEmpty(appointRecord.getCertId())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能预约");
        }
        if (StringUtils.isEmpty(appointRecord.getPatientName())) {
            appointRecord.setPatientName(p.getPatientName());
        }

        //判断经过his的预约是否在维护
        new HisRemindService().saveRemindRecordForNormalAppoint(appointRecord);


        AbstractHibernateStatelessResultAction<AppointRecord> action = new AbstractHibernateStatelessResultAction<AppointRecord>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                AppointRecord result = new AppointRecord();
                // 检验预约记录信息
                isValidAppointRecordData(appointRecord);
                dealOrganBuss(appointRecord);       //处理医院实际业务过滤
                isBussRepeat(appointRecord);

                AppointRecordDAO dao = DAOFactory
                        .getDAO(AppointRecordDAO.class);
                AppointSourceDAO AppointSourceDAO = DAOFactory
                        .getDAO(AppointSourceDAO.class);
                DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                AppointSource appointSourceBeforeSave = AppointSourceDAO
                        .getById(appointRecord.getAppointSourceId());
                if (appointSourceBeforeSave != null) {
                    AppointControlDAO appointControlDAO = DAOFactory.getDAO(AppointControlDAO.class);
                    boolean request = appointControlDAO.checkAppoint(appointSourceBeforeSave);
                    if (!request) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "当前时间不可预约挂号");
                    }
                }


                appointRecord.setAppointDate(new Date());
                appointRecord.setAppointStatus(9);// 9医院确认中,0预约成功
                appointRecord.setPayFlag(0);
                if (appointRecord.getAppointRoad() == 6
                        && !hisServiceConfigDao.isServiceEnable(
                        appointRecord.getOrganId(), ServiceType.TOHIS)) {
                    appointRecord.setAppointStatus(0);// 9医院确认中,0预约成功
                }

                if (appointSourceBeforeSave != null) {
                    appointRecord.setOrganSchedulingId(appointSourceBeforeSave
                            .getOrganSchedulingId());
                    appointRecord.setOrderNum(appointSourceBeforeSave.getOrderNum());
                    appointRecord.setConfirmClinicAddr(appointSourceBeforeSave.getClinicAddr());
                }
                logger.info("保存预约记录,入参:" + JSONUtils.toString(appointRecord));

                // 普通预约
                if (appointSourceBeforeSave != null) {
                    int ordernum = appointSourceBeforeSave.getOrderNum();

                    // 已用号源大于号源数，即已无有效号源，则直接返回false
                    // 2016-6-12 luf:将返回结果改成appointRecord。无有效号源，返回new AppointRecord()
                    if ((appointSourceBeforeSave.getUsedNum() + 1) > appointSourceBeforeSave.getSourceNum()) {
                        //ZhangXq 如果该号源已经被预约 则自动查找该排班下 下个号源
//                        setResult(result);
//                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该号源已经被预约，请选择其他号源进行预约!");
                        List<AppointSource> nextSources = AppointSourceDAO.findAppointSourceNext(appointRecord.getOrganId(),appointRecord.getDoctorId(), appointRecord.getWorkDate(), appointRecord.getWorkType(), appointRecord.getOrganSchedulingId(),appointRecord.getAppointDepartId());
                        if(CollectionUtils.isEmpty(nextSources)){
                            setResult(result);
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，该排班号源已预约完，请选择其他排班!");
                        }else{
                            AppointSource nextSource = nextSources.get(0);
                            appointSourceBeforeSave = nextSource;
                            appointRecord.setAppointSourceId(nextSource.getAppointSourceId());
                            appointRecord.setOrderNum(nextSource.getOrderNum());
                            appointRecord.setStartTime(nextSource.getStartTime());
                            appointRecord.setEndTime(nextSource.getEndTime());
                            AppointSourceDAO.updateUsedNumByAppointSourceId(nextSource.getUsedNum()+1, appointRecord.getAppointSourceId());
                        }
                    }


                    // 已用号源小于号源数，且剩余号源数大于1，则需插入预约记录、更新号源数、顺序数、更新医生表
                    else if ((appointSourceBeforeSave.getUsedNum() + 1) < appointSourceBeforeSave.getSourceNum()) {
                        // 更新号源表【已用号源数】
                        int UesdNum = appointSourceBeforeSave.getUsedNum();
                        UesdNum += 1;
                        ordernum += 1;
                        logger.info("更新号源数顺序数,AppointSourceId:"
                                + appointRecord.getAppointSourceId());
                        //TODO
                        AppointSourceDAO.updateUsedNum(UesdNum, ordernum,
                                appointRecord.getAppointSourceId());
                        // 更新医生表【是否有预约号源标志（1有号源 0无号源）】
                        List<DoctorDateSource> doctorAllSource = AppointSourceDAO
                                .totalByDoctorDate(appointRecord.getDoctorId(),
                                        appointRecord.getSourceType());
                        long doctorSumSource = 0;
                        for (int i = 0; i < doctorAllSource.size(); i++) {
                            doctorSumSource = doctorSumSource
                                    + doctorAllSource.get(i).getSourceNum();
                        }
                        if (doctorSumSource > 0) {
                            DoctorDAO.updateHaveAppointByDoctorId(
                                    appointRecord.getDoctorId(), 1);
                        } else {
                            logger.info("更新成无号源:doctorId:"
                                    + appointRecord.getDoctorId());
                            DoctorDAO.updateHaveAppointByDoctorId(
                                    appointRecord.getDoctorId(), 0);
                        }
                    }
                    // 剩余号源数为1，即最后一个号源，则无需更新号源顺序号
                    else {
                        int UesdNum = appointSourceBeforeSave.getUsedNum();
                        UesdNum += 1;
                        if (OrganConstant.WhHanOrganId == appointRecord
                                .getOrganId()) {// 武汉
                            UesdNum = 0;
                        }
                        AppointSourceDAO.updateUsedNum(UesdNum, ordernum,
                                appointRecord.getAppointSourceId());
                        // 更新医生表【是否有预约号源标志（1有号源 0无号源）】
                        List<DoctorDateSource> doctorAllSource = AppointSourceDAO
                                .totalByDoctorDate(appointRecord.getDoctorId(),
                                        appointRecord.getSourceType());
                        long doctorSumSource = 0;
                        for (int i = 0; i < doctorAllSource.size(); i++) {
                            doctorSumSource = doctorSumSource
                                    + doctorAllSource.get(i).getSourceNum();
                        }
                        if (doctorSumSource > 0) {
                            DoctorDAO.updateHaveAppointByDoctorId(
                                    appointRecord.getDoctorId(), 1);
                        } else {
                            logger.info("更新成无号源:doctorId:"
                                    + appointRecord.getDoctorId());
                            DoctorDAO.updateHaveAppointByDoctorId(
                                    appointRecord.getDoctorId(), 0);
                        }
                    }
                } else {// 转诊预约//处理省中，22203是特需门诊code
                    if (appointRecord.getOrganId() == OrganConstant.Organ_SZ
                            || appointRecord.getOrganId() == OrganConstant.Organ_XS) {
                        recordDAO.appointForTransfer(appointRecord);
                    }
                }
                try {
                    if (ValidateUtil.nullOrZeroInteger(appointRecord.getDeviceId())) {
                        appointRecord.setDeviceId(SessionItemManager.instance().checkClientAndGet());
                    }
                } catch (Exception e) {
                    logger.info(LocalStringUtil.format("addAppointRecordNew get deviceId from session exception! errorMessage[{}]", e.getMessage()));
                }
                Integer telClinicFlag = appointRecord.getTelClinicFlag();
                Integer transferId = appointRecord.getTransferId();
                if ((telClinicFlag == null || telClinicFlag == 0) && DateConversion.getDaysBetween(appointRecord.getWorkDate(), new Date()) == 0
                        && (transferId == null || transferId.equals(0))) {
                    appointRecord.setRecordType(1);

                    // 废除 2017-07-26 cq
                    // 免费的实时挂号直接更新为已支付
                  /*  if (appointRecord.getClinicPrice() == 0) {
                        appointRecord.setPayFlag(1);
                    } else {
                        appointRecord.setPayFlag(0);
                    }*/
                } else {
                    appointRecord.setRecordType(0);
                }
                appointRecord.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49
                result = dao.save(appointRecord);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        AppointRecord result = action.getResult();

        if (result != null && result.getAppointRecordId() != null && result.getAppointRecordId() > 0) {
            // 调his预约注册服务
            logger.info("调his预约注册服务入参打印:Patient:" + JSONUtils.toString(p)
                    + ",appointRecord:" + JSONUtils.toString(appointRecord));

            if (hisServiceConfigDao.isServiceEnable(appointRecord.getOrganId(),
                    ServiceType.TOHIS) || appointRecord.getAppointRoad() == 5) {
                logger.info("发起his预约转诊服务");
                recordDAO.registAppoint(p, appointRecord);
            }

            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForAppoint(appointRecord);

            // 预约申请，给予推荐奖励,不管预约是否成功
            if (appointRecord.getAppointRoad() == 5
                    && appointRecord.getAppointUser().length() != 32) {
                DoctorAccountDAO accDao = DAOFactory
                        .getDAO(DoctorAccountDAO.class);
                accDao.recommendReward(Integer.parseInt(appointRecord
                        .getAppointUser()));
            }
            return result.getAppointRecordId();
        } else {
            throw new DAOException(609, "预约挂号异常，请重试");
            // return null;
        }
    }

    /**
     * 实时挂号并支付
     *
     * @param appointRecord 预约记录
     * @param payWay        支付方式
     * @return
     */
    @Deprecated //使用addAppointRecordNew代替新增预约单环节 submitPayAppoint 代替支付环节
    @RpcService
    public Map<String, Object> submitAppointAndPay(AppointRecord appointRecord, String payWay) throws Exception {
        try {
            Integer appointRecordId = this.addAppointRecordNew(appointRecord);
            //
            AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).get(appointRecordId);
            double price = ar.getClinicPrice();
            logger.info("当天价格" + price + "状态：" + ar.getAppointStatus());
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            if (appointRecord.getClinicPrice() > 0) {
                callbackParamsMap.put("price", String.valueOf(appointRecord.getClinicPrice()));
                Map<String, Object> map = payService.immediatlyPayForBus(payWay, BusTypeEnum.APPOINT.getCode(), appointRecordId, appointRecord.getOrganId(), callbackParamsMap);
                return map;
            } else {
                return new HashMap<String, Object>();
            }

        } catch (Exception e) {
            logger.error(LocalStringUtil.format("[{}] submitAppointAndPay error with appointRecord[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(appointRecord), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }


    /**
     * 当天挂号完成支付 生成支付参数
     * @param appointRecordId
     * @return
     * @throws Exception
     */
    @RpcService
    public Map<String, Object> submitPayAppoint(Integer appointRecordId,String payWay) throws Exception {
        AppointRecord appointRecord=null;
        try {
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);


            double price = appointRecord.getClinicPrice();
            logger.info("当天价格" + price + "状态：" + appointRecord.getAppointStatus());
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            //if (appointRecord.getClinicPrice() > 0) {
                callbackParamsMap.put("price", String.valueOf(appointRecord.getClinicPrice()));
                Map<String, Object> map = payService.immediatlyPayForBus(payWay, BusTypeEnum.APPOINT.getCode(), appointRecordId, appointRecord.getOrganId(), callbackParamsMap);
                return map;
//            } else {
//                return new HashMap<String, Object>();
//            }

        }catch (DAOException e){
            logger.error(LocalStringUtil.format("[{}] submitAppointAndPay error with appointRecord[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(appointRecord), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw e;
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("[{}] submitAppointAndPay error with appointRecord[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(appointRecord), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }



    @RpcService
    public Map<String, Object> appointPay(Integer appointRecordId, String payWay) {
        try {
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            AppointRecord appointRecord = appointRecordDAO.get(appointRecordId);
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            Map<String, Object> map = new HashMap<>();
            callbackParamsMap.put("price", String.valueOf(appointRecord.getClinicPrice()));
            if (appointRecord.getRecordType() != null && appointRecord.getRecordType().intValue() == 1) {
                map = payService.immediatlyPayForBus(payWay, BusTypeEnum.APPOINT.getCode(), appointRecordId, appointRecord.getOrganId(), callbackParamsMap);
            } else {
                map = payService.immediatlyPayForBus(payWay, BusTypeEnum.APPOINTPAY.getCode(), appointRecordId, appointRecord.getOrganId(), callbackParamsMap);
            }
            return map;
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("appointPay error with appointRecord[{}], errorMessage[{}], stackTrace[{}]",
                    JSONObject.toJSONString(appointRecordId), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(e.getMessage());
        }
    }

    /**
     * 处理医院实际业务过滤
     */
    public void dealOrganBuss(AppointRecord p) {
        // 邵逸夫(当天挂号，当天转诊)不能转诊当天名医  （云门诊除外）20161026 zxq
        if (1 == p.getOrganId().intValue()
                && DateConversion.isSameDay(new Date(), p.getWorkDate())
                && (p.getTelClinicId() == null || p.getTelClinicId().equals(""))) {
            String departCode = p.getAppointDepartId();
            if (departCode != null && departCode.contains("V")) {//挂号科室为名医
                throw new DAOException(ErrorCode.SERVICE_ERROR, "邵逸夫不能预约当天名医号源，如果您在医院，可以前去护士站询问加号.");
            }
        }
    }

    /**
     * 预约确认时，如果因为网络连接原因、前置机服务器原因、HIS服务器原因导致连接中断，
     * 提示【医院确认中】，则5分钟内，最多发起5次请求，确认订单是否成功，
     * 然后根据订单真实状态进行更新；如5次请求均未果，则提示已取消。
     */
    @RpcService
    public void checkAppoint() {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        Date requestBeforeFive = DateConversion.getDateAftMinute(new Date(), -5);
        Date requestBeforeTen = DateConversion.getDateAftMinute(requestBeforeFive, -10);
        List<AppointRecord> appointList = appointRecordDAO.findByAppointStatus(9, requestBeforeTen, requestBeforeFive);

        if (appointList != null) {
            for (AppointRecord appointRecord : appointList) {
                if (appointRecord.getRequestNum() < 5) {
                    appointRecordDAO.reTryAppoint(appointRecord.getAppointRecordId());
                    appointRecordDAO.updateByAppointRecordId(appointRecord.getAppointRecordId());
                } else {
                    appointRecordDAO.updateStatusById(2, appointRecord.getAppointRecordId());
                }
            }
        }
    }


    /**
     * 生成待支付的挂号单
     *
     * @param appointRecord
     * @return
     */
    @RpcService
    public Map<String, Object> requestUnPayAppoint(AppointRecord appointRecord) {
        Map<String, Object> map = new HashMap<>();
        appointRecord.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);

        try {
            Integer appointRecordId = this.addAppointRecordNew(appointRecord);
            map.put("busId", appointRecordId);
            //方法里抛出DAOException,不能捕获，需要给前端错误提示信息
        } catch (DAOException daoExe) {
            throw daoExe;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return map;
    }
}
