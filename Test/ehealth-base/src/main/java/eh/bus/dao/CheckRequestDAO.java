package eh.bus.dao;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.appoint.mode.AppointCancelRequestTO;
import com.ngari.his.appoint.mode.TaskQueueHisTO;
import com.ngari.his.check.service.ICheckHisService;
import com.ngari.his.image.service.IImageHisService;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.dao.*;
import eh.base.service.organ.OrganConfigService;
import eh.bus.CommonMethod.CheckRequestMethod;
import eh.bus.param.ConfigParam;
import eh.bus.service.HisRemindService;
import eh.bus.service.MailService;
import eh.entity.base.*;
import eh.entity.bus.*;
import eh.entity.his.AppointCancelRequest;
import eh.entity.his.TaskQueue;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.Patient;
import eh.entity.mpi.PatientType;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.PatientTypeDAO;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.task.executor.CheckSendExecutor;
import eh.util.DBParamLoaderUtil;
import eh.util.DoctorUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.params.ParamUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class CheckRequestDAO extends HibernateSupportDelegateDAO<CheckRequest> {

    private static final Log logger = LogFactory.getLog(CheckRequestDAO.class);

    private static int requestNum;

    public CheckRequestDAO() {
        super();
        this.setEntityName(CheckRequest.class.getName());
        this.setKeyField("checkRequestId");
    }

    @RpcService
    @DAOMethod
    public abstract CheckRequest getByCheckRequestId(Integer checkRequestId);

    @RpcService
    @DAOMethod(sql = "from CheckRequest where organId=:organId and organRequestNo=:organRequestNo")
    public abstract CheckRequest getCheckRequestByOrganIdAndRequestNo(@DAOParam("organId") Integer organId, @DAOParam("organRequestNo") String organRequestNo);

    @RpcService
    @DAOMethod(sql = "from CheckRequest where organId=:organId and reportId=:reportId")
    public abstract CheckRequest getCheckRequestByOrganIdAndReportId(@DAOParam("organId") Integer organId, @DAOParam("reportId") String reportId);

    @RpcService
    @DAOMethod(sql = "from CheckRequest where status = 2 and checkStatus = 0 and TIMSTAMPDIFF(HOUR, requestDate, NOW())>=:customerHour")
    public abstract List<CheckRequest> getOvertimeList(@DAOParam("customerHour")  Integer customerHour);

    protected boolean checkLock(CheckRequest checkRequest) {

        CheckSourceDAO checkSourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        CheckSource source = checkSourceDAO.get(checkRequest.getChkSourceId());
        if (source == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "号源不存在！");
        }
        Integer requestDoctor = checkRequest.getRequestDoctorId();
        Integer lockDoctor = source.getLockDoctor();
        if (lockDoctor != null && lockDoctor != 0 && requestDoctor.equals(lockDoctor)) {

        } else {
//            logger.error("啊哦！这个时段刚刚被约走了...");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "啊哦！这个时段刚刚被约走了...");
        }
        return true;

    }

    /**
     * 组装请求参数
     *
     * @param cs
     */
    private AppointmentRequest createRequest(Patient p, CheckRequest checkRequest, CheckSource cs) {
        AppointmentRequest appointment = new AppointmentRequest();
        appointment.setId(checkRequest.getCheckRequestId() + "");
        appointment.setPatientName(p.getPatientName());
        appointment.setPatientSex(p.getPatientSex());
        appointment.setCertID(checkRequest.getCertId());
        appointment.setBirthday(p.getBirthday());
        appointment.setPatientType(p.getPatientType());
        appointment.setMobile(p.getMobile());
        appointment.setOrganID(checkRequest.getOrganId() + "");
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        CheckAppointItem checkAppointItem = checkAppointItemDAO.get(checkRequest.getCheckAppointId());
        appointment.setDepartCode(checkAppointItem.getDepartId() + "");
        appointment.setDepartName(checkAppointItem.getCheckAppointName());
        OrganCheckItemDAO organCheckItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        OrganCheckItem organCheckItem = organCheckItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(checkRequest.getOrganId(), checkRequest.getCheckItemId(), checkRequest.getCheckAppointId());
        String organItemCode = organCheckItem.getOrganItemCode();
//		appointment.setOriginalSourceid(cs.getOrganSourceId());
        appointment.setOriginalSourceid(organItemCode);//机构检查项目code从organCheckItem中取

        appointment.setDiseaseCode(checkRequest.getDiseaseCode());
        appointment.setDisease(checkRequest.getDisease());
        appointment.setDiseasesHistory(checkRequest.getDiseasesHistory());
        appointment.setPurpose(checkRequest.getPurpose());
        appointment.setWorkDate(checkRequest.getCheckDate());
        appointment.setHomeAddr(checkRequest.getCheckItemName());// 检查项目名称
        CheckItemDAO checkItemDAO = DAOFactory.getDAO(CheckItemDAO.class);
        CheckItem checkItem = checkItemDAO.get(checkRequest.getCheckItemId());
        String checkType = checkItem.getCheckClass();
        appointment.setOperjp(checkType);// 检查类型
        appointment.setClinicArea(checkAppointItem.getCheckRoom());// 检查机房
        appointment.setSchedulingID(checkItem.getCheckBody());// BODY
        return appointment;
    }

    @DAOMethod(sql = "update CheckRequest set cancelDate=:cancelDate, cancelName=:cancelName, cancelResean=:cancelResean, status=:status where checkRequestId=:checkRequestId")
    public abstract void updateCancel(@DAOParam("checkRequestId") int appointRecordId, @DAOParam("cancelDate") Date cancelDate, @DAOParam("cancelName") String cancelName,
                                      @DAOParam("cancelResean") String cancelResean, @DAOParam("status") int status);

    /**
     * 医技检查取消预约不发短信通知
     *
     * @param checkRequestId
     * @param cancelReason
     * @return
     */
    public boolean cancelCheckNoMsg(final int checkRequestId, final String cancelReason) {
        final CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckItemDAO checkItemDAO = DAOFactory.getDAO(CheckItemDAO.class);
        final CheckRequest checkReq = checkRequestDAO.get(checkRequestId);
        if (checkReq == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该预约单不存在!");// 9
        }
        Date checkDate = checkReq.getCheckDate();
        Date today = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        if (checkDate.compareTo(today) == 0) {// 当天
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不能取消当天的检查单！");
        }
        if (3 == checkReq.getStatus()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单已经支付，不能取消！如需取消，请至医院退费！");
        }
        int organId = checkReq.getOrganId().intValue();
        HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = configDAO.isServiceEnable(organId, ServiceType.CHECKTHIS);
        if (f) {
            checkRequestDAO.updateCancel(checkReq.getCheckRequestId(), new Date(), "", cancelReason, 9);
            Integer chkSourceId = checkReq.getChkSourceId();
            CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
            CheckSource cs = sourceDAO.get(chkSourceId);
            int useNum = (cs.getUsedNum() - 1) < 0 ? 0 : (cs.getUsedNum() - 1);
            int orderNum = cs.getOrderNum();
            // 更新号源数量
            sourceDAO.updateUsedNum(useNum, orderNum, cs.getChkSourceId());
            sourceDAO.unlockCheckSource(cs.getChkSourceId(),cs.getLockDoctor());
        } else {
            // 调用his
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(checkReq.getOrganId());
            String hisServiceId = cfg.getAppDomainId() + ".checkService";//调用服务id

            AppointCancelRequest req = new AppointCancelRequest();
            req.setAppointId(checkReq.getOrganRequestNo());
            req.setCancelReason(cancelReason);
            CheckItem checkitem = checkItemDAO.get(checkReq.getCheckItemId());
            req.setTerminalInfo(checkitem.getCheckClass());
            if (checkReq.getOrganId().intValue() == 1) {
                OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
                OrganCheckItem oc = organItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(checkReq.getOrganId(), checkReq.getCheckItemId(), checkReq.getCheckAppointId());
                String code = oc.getOrganItemCode();//邵逸夫医院的需要加上院区
                String hosCode = code.split("\\|")[0];
                req.setOrganId(hosCode);
            }
            //boolean cancelResult = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelCheck", req);
            boolean cancelResult;
        	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(checkReq.getOrganId()))){
        		ICheckHisService iCheckHisService = AppDomainContext.getBean("his.iCheckHisService", ICheckHisService.class);
        		AppointCancelRequestTO reqTO= new AppointCancelRequestTO();
        		BeanUtils.copy(req,reqTO);
        		cancelResult = iCheckHisService.cancelCheck(reqTO);
        	}else{
        		cancelResult = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelCheck", req);
        	}
            if (!cancelResult) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "取消失败!");
            }
            AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    // if (checkReq.getStatus() != 2) {// 2 ：确认成功待支付
                    // throw new DAOException(609, "该预约单不能取消!");
                    // }
                    // 更新预约记录
                    checkRequestDAO.updateCancel(checkReq.getCheckRequestId(), new Date(), "", cancelReason, 9);

                    Integer chkSourceId = checkReq.getChkSourceId();
                    CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
                    CheckSource cs = sourceDAO.get(chkSourceId);
                    int useNum = (cs.getUsedNum() - 1) < 0 ? 0 : (cs.getUsedNum() - 1);
                    int orderNum = cs.getOrderNum();
                    // 更新号源数量
                    sourceDAO.updateUsedNum(useNum, orderNum, cs.getChkSourceId());
                    sourceDAO.unlockCheckSource(cs.getChkSourceId(),cs.getLockDoctor());
                    setResult(true);
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);
        }
        return true;
    }

    /**
     * 取消检查预约
     */
    @RpcService
    public void cancelCheck(final int checkRequestId, final String cancelReason) {
        boolean res = cancelCheckNoMsg(checkRequestId, cancelReason);
        if (res) {
            String SmsType = "CancelCheckToPat";
            String BusType = "CancelCheckToPat";
            CheckReqMsg.sendMsg(checkRequestId, SmsType, BusType);
        }
    }

    /**
     * HIS成功后调用 更新状态、发送短信
     */
    @RpcService
    public void updateCheckSuccess(AppointmentResponse res) {
        String checkRequestId = res.getId();// PK
        final String organRequestNo = res.getAppointID();// 机构申请单号
        final String patientid = res.getOrganMPI();//病历号
        final Double price;
        if (res.getCheckPrice() == null) {
            price = 10d;
        } else {
            price = Double.parseDouble(res.getCheckPrice());
        }

        logger.info("预约检查注册成功后,his返回数据:checkRequestId=" + checkRequestId + ",OrganRequestNo=" + organRequestNo + ",price=" + price);
        final CheckRequest ar = this.get(Integer.parseInt(checkRequestId));
        if (ar == null) {
//            logger.error("can not find the record by id:" + res.getId());
            throw new DAOException("can not find the record by id:" + res.getId());
        }
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                ar.setCheckPrice(price);
                ar.setOrganRequestNo(organRequestNo);
                ar.setPatientID(patientid);
                ar.setStatus(2);
                update(ar);
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        if (action.getResult()) {
            // 短信
            String SmsType = "CheckRequestSucc";
            String BusType = "CheckRequestSucc";
            CheckReqMsg.sendMsg(ar.getCheckRequestId(), SmsType , BusType);

            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(ar.getOrganId());
            String crtp = ParamUtils.getParam("crtp");
            if ("1".equals(cfg.getExistStatus())) {
                OrganConfigService organConfigService = new OrganConfigService();
                Map organDoctorList = organConfigService.getOrganConfig(ar.getOrganId());
                Object doctors = organDoctorList.get("notifyPartyForCheck");
                if (doctors != null) {
                    String[] doctorList = doctors.toString().split(",");
                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (String organDoctor : doctorList) {
                        Doctor doctor = doctorDAO.get(Integer.parseInt(organDoctor));
                        if (doctor == null) {
                            logger.info("doctor not found " + organDoctor);
                            continue;
                        }
                        SmsInfo info = new SmsInfo();
                        info.setBusId(ar.getCheckRequestId());// 业务表主键
                        info.setBusType(crtp);// 业务类型
                        info.setSmsType(crtp);
                        info.setOrganId(ar.getOrganId());// 短信服务对应的机构， 0代表通用机构
                        info.setExtendWithoutPersist(doctor.getDoctorId().toString());
                        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                        smsPushService.pushMsgData2OnsExtendValue(info);
                    }
                }
            }

//            String title = "【医技检查】新增预约记录，请处理";
//            String content = getContent(ar);
            //发送邮件
//            logger.info("邮件内容" + content);
//            MailService.sendMail(title, content, "fucz@Ngarihealth.com");
//            MailService.sendMail(title, content, "tongl@Ngarihealth.com");
//            MailService.sendMail(title, content, "zhangxq@Ngarihealth.com");
//            MailService.sendMail(title, content, "zhangx@Ngarihealth.com");
        }

    }

  /**
     * HIS失败后调用 更新状态、发送短信
     */
    @RpcService
    public void cancelForHisFail(AppointmentResponse res) {
        int busID = Integer.parseInt(res.getId());
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        // 更新状态
        checkRequestDAO.updateCancel(busID, new Date(), "", "his预约失败，系统自动取消", 7);
        CheckRequest cr = checkRequestDAO.get(busID);
        Integer chkSourceId = cr.getChkSourceId();
        CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        CheckSource cs = sourceDAO.get(chkSourceId);
        int usedNum = cs.getUsedNum() - 1;
        cs.setUsedNum(usedNum);
        sourceDAO.update(cs);
        // 短信
        String SmsType = "CheckRequestFail";
        String BusType = "CheckRequestFail";
        CheckReqMsg.sendMsg(busID, SmsType, BusType);
        // 推送
        CheckReqMsg.checkSysMsgAndPush(busID, 0);

    }

    /**
     * 检查单申请服务
     *
     * @param cr 检查申请单
     * @return Integer 检查申请单号
     */
    @RpcService
    public Integer requestCheck(final CheckRequest cr) {
        logger.info("检查预约请求============：" + JSONUtils.toString(cr));
        if (StringUtils.isEmpty(cr.getMpiid())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiid is required!");
        }
        PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = PatientDAO.getByMpiId(cr.getMpiid());

        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能检查");
        }

        if (StringUtils.isEmpty(cr.getPatientName())) {
            cr.setPatientName(patient.getPatientName());
        }
        if (StringUtils.isEmpty(cr.getPatientSex())) {
            cr.setPatientSex(patient.getPatientSex());
        }
        if (StringUtils.isEmpty(cr.getPatientType())) {
            cr.setPatientType(patient.getPatientType());
        }
        cr.setCertId(patient.getRawIdcard());
        if (StringUtils.isEmpty(cr.getMobile())) {
            cr.setMobile(patient.getMobile());
        }
        if (StringUtils.isEmpty(cr.getDisease())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "disease is required!");
        }
        if (StringUtils.isEmpty(cr.getPurpose())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "purpose is required!");
        }
        if (StringUtils.isEmpty(cr.getCheckType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkType is required!");
        }
        if (StringUtils.isEmpty(cr.getExaminationTypeName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "examinationTypeName is required!");
        }
        if (StringUtils.isEmpty(cr.getCheckItemName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItemName is required!");
        }
        // if (cr.getCheckDate().before(new Date())) {
        // throw new DAOException(DAOException.VALUE_NEEDED,
        // "checkDate is required!");
        // }
        if (cr.getCheckItemId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItemId is required!");
        }
        if (cr.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        if (cr.getCheckAppointId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkAppointId is required!");
        }
        if (cr.getChkSourceId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "chkSourceId is required!");
        }
        if (cr.getCheckDate() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkDate is required!");
        }
        if (cr.getWorkType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "workType is required!");
        }
        if (cr.getStartDate() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startDate is required!");
        }
        if (cr.getEndDate() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "endDate is required!");
        }
        if (cr.getOrderNum() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "orderNum is required!");
        }
        if (cr.getRequestDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestDoctorId is required!");
        }
        if (cr.getRequestOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestOrgan is required!");
        }
        if (cr.getRequestDepartId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestDepartId is required!");
        }
        if (cr.getPatientID() != null && "0".equals(cr.getPatientID())) {
            cr.setPatientID(null);
        }
        // if(cr.getCheckDate().before(new Date())){
        // throw new DAOException(DAOException.VALUE_NEEDED,
        // "CheckDate is not right!");
        // }
        boolean res = checkRepeat(cr);
        if (res) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "您存在未完成的订单！");
        }
        boolean numAble = checkNum(cr);
        if (!numAble) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者今天已超过最大预约数！");
        }

        //判断经过his的转诊接收是否在维护
        new HisRemindService().saveRemindRecordForCheck(cr);

        final CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Integer request = cr.getRequestDoctorId();
                Integer chkSourceId = cr.getChkSourceId();

                CheckSource cs = sourceDAO.get(chkSourceId);
                if (cs == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "checkSource is required!");
                }
                logger.info("预约参数：" + JSONUtils.toString(cr));
                Integer usedNum = cs.getUsedNum();
                int lastSource = cs.getSourceNum() - usedNum;
                if (lastSource <= 0) {
                    throw new DAOException(609, "啊哦！这个时段刚刚被约走了...请更换时段再约~");
                }
                Integer lock = cs.getLockDoctor();
                if (lock != null && !lock.equals(request)) {
                    throw new DAOException(609, "啊哦！这个时段刚刚被约走了...请更换时段再约~");
                }
                cs.setUsedNum(++usedNum);
                sourceDAO.update(cs);
                cr.setRequestDate(new Date());
                cr.setPayFlag(0);// 未付费
                cr.setStatus(1);// 医院确认中
                cr.setCheckStatus(0); //未审核
                CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
                checkRequestDAO.save(cr);
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);

        if (action.getResult()) {
            int organId = cr.getOrganId().intValue();
            HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
            boolean f = configDAO.isServiceEnable(organId, ServiceType.CHECKTHIS);
            if (f) {
                //汉中默认成功  后期添加配置表
                AppointmentResponse response = new AppointmentResponse();
                response.setAppointID(cr.getCheckRequestId() + "");
                response.setId(cr.getCheckRequestId() + "");
                updateCheckSuccess(response);
            } else {
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient p = patientDAO.getByMpiId(cr.getMpiid());
                Integer chkSourceId = cr.getChkSourceId();
                CheckSource cs = sourceDAO.get(chkSourceId);
                // TODO 组装请求参数
                AppointmentRequest appointment = createRequest(p, cr, cs);
                CheckSendExecutor CheckSendExecutor = new CheckSendExecutor(appointment);
                CheckSendExecutor.execute();
            }
            // 添加记录到OperationRecords
            OperationRecordsDAO operationRecordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForCheck(cr);
        }
        return cr.getCheckRequestId();
    }

    private boolean checkNum(CheckRequest cr) {
        String mpiid = cr.getMpiid();
        List<CheckRequest> res = findTodayCheckListByMpiid(mpiid);
        if (res == null || res.size() >= 2) {
            return false;
        }
        return true;
    }

    private boolean checkRepeat(CheckRequest cr) {
        String mpiid = cr.getMpiid();
        List<CheckRequest> list = findCheckListByMpiid(mpiid, cr.getCheckItemId());
        if (list == null || list.size() == 0) {
            return false;
        }
        return true;
    }

    @RpcService
    public List<CheckRequest> findCheckListByMpiid(final String mpiid, final Integer checkItemId) {
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuffer hql = new StringBuffer("from CheckRequest where status in (1,2,3) and mpiid=:mpiid and checkItemId=:checkItemId");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiid", mpiid);
                q.setParameter("checkItemId", checkItemId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }


    public List<CheckRequest> findTodayCheckListByMpiid(final String mpiid) {
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuffer hql = new StringBuffer("from CheckRequest where   mpiid=:mpiid" +
                        " and to_days(requestDate) = to_days(now())" +
                        " and status in (1,2,3,4,5,0)");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiid", mpiid);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 检查预约记录列表服务
     *
     * @param doctorId 当前登录医生内码
     * @param flag     分类--0全部1待检查2待出报告3已出报告4已取消
     * @param mark     标志（全部）--0未完成1已结束
     * @param start    分页起始位置
     * @param limit    每页限制条数
     * @return List<Object>
     */
    @RpcService
    public List<Object> findCheckList(int doctorId, int flag, int mark, int start, int limit) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        List<Object> results = new ArrayList<Object>();
        List<CheckRequest> crs = this.findCheckRequestsByFive(doctorId, flag, mark, start, limit);
        for (CheckRequest cr : crs) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            String statusName = getStatusName(cr);
            cr.setStatusName(statusName);
            cr.setRequestString(DateConversion.convertRequestDateForBuss(cr.getRequestDate()));
            OrganCheckItem oc = organItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(cr.getOrganId(), cr.getCheckItemId(), cr.getCheckAppointId());
            if (oc == null) {
                continue;
            }
            CheckItemDAO itemDAO = DAOFactory.getDAO(CheckItemDAO.class);
            CheckItem baseItem = itemDAO.get(cr.getCheckItemId());
            String bodyDic = baseItem.getCheckBody();
            String checkType = baseItem.getCheckClass();
            try {
                String bodyTxt = DictionaryController.instance().get("eh.base.dictionary.Body").getText(bodyDic);
                String examinationTypeName = DictionaryController.instance().get("eh.base.dictionary.CheckClass").getText(checkType);
                oc.setBody(bodyDic);
                oc.setBodyText(bodyTxt);
                oc.setCheckType(checkType);
                oc.setExaminationTypeName(examinationTypeName);
            } catch (ControllerException e) {
                logger.error(e);
            }

            String mpiId = cr.getMpiid();
            Patient patient = patientDAO.get(mpiId);
            map.put("organCheckItem", oc);
            map.put("checkRequest", cr);
            if (patient == null) {
                map.put("patient", patient);
                results.add(map);
                continue;
            }
            RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
            if (rd != null) {
                patient.setRelationPatientId(rd.getRelationDoctorId());
                patient.setRelationFlag(true);
                if (rd.getRelationType() == 0) {
                    patient.setSignFlag(true);
                } else {
                    patient.setSignFlag(false);
                }
                patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd.getRelationDoctorId()));
            }
            map.put("patient", patient);
            results.add(map);
        }
        return results;
    }

    /**
     * 获取检查单状态
     *
     * @param cr
     * @return
     */
    private String getStatusName(CheckRequest cr) {
        switch (cr.getStatus()) {
            case 2:
            case 3:
                cr.setStatusName("待检查");
                break;
            case 1:
                cr.setStatusName("医院确认中");
                break;
            case 6:
            case 7:
            case 8:
            case 9:
                cr.setStatusName("已取消");
                break;
            case 4:
                cr.setStatusName("待出报告");
                break;
            case 5:
                cr.setStatusName("待出报告");
                break;
            case 0:
                cr.setStatusName("已出报告");// 可以点击查看影像
                break;
            default:
                break;
        }
        return cr.getStatusName();
    }

    /**
     * 供 检查预约记录列表服务 调用
     *
     * @param doctorId 当前登录医生内码
     * @param flag     分类--0全部1待检查2待出报告3已出报告4已取消
     * @param mark     标志（全部）--0未完成1已结束
     * @return List<CheckRequest>
     */
    public List<CheckRequest> findCheckRequestsByFive(final int doctorId, final int flag, final int mark, final int start, final int limit) {
        if (flag < 0 || flag > 4) {
            throw new DAOException(DAOException.VALUE_NEEDED, "flag is required!");
        }
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuffer hql = new StringBuffer("from CheckRequest where requestDoctorId=:doctorId and ");
                switch (flag) {
                    case 0:
                        if (mark == 0) {
                            hql.append("(status>0 and status<=5) ");
                        } else {
                            hql.append("(status=0 or status>=6) ");
                        }
                        hql.append("order by requestDate desc");
                        break;
                    case 1:
                        hql.append("(status=2 or status=3 or status=1) ");
                        break;
                    case 2:
                        hql.append("(status=4 or status=5) ");
                        break;
                    case 3:
                        hql.append("status=0 ");
                        break;
                    case 4:
                        hql.append("(status=6 or status=7 or status=8 or status=9) ");
                    default:
                        break;
                }
                if (flag >= 1) {
                    hql.append(" order by startDate desc");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 预约记录详情查询服务
     *
     * @param checkRequestId 检查申请单号
     * @param doctorId       当前登录医生内码
     * @return HashMap<String, Object>
     */
    @RpcService
    public HashMap<String, Object> getDetailByCheckRequestId(int checkRequestId, int doctorId) {
        OrganCheckItemDAO ociDao = DAOFactory.getDAO(OrganCheckItemDAO.class);
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);
        CheckRequest cr = this.get(checkRequestId);
        if (cr == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkRequest is required!");
        }
        String mpiId = cr.getMpiid();
        OrganCheckItem oci = ociDao.getByOrganIdAndCheckItemIdAndCheckAppointId(cr.getOrganId(), cr.getCheckItemId(), cr.getCheckAppointId());
        Patient patient = pDao.get(mpiId);
        HashMap<String, Object> map = new HashMap<String, Object>();
        String statusName = getStatusName(cr);
        cr.setStatusName(statusName);
        map.put("checkRequest", cr);

        // 添加信息
        CheckItemDAO itemDAO = DAOFactory.getDAO(CheckItemDAO.class);
        CheckItem baseItem = itemDAO.get(cr.getCheckItemId());
        String bodyDic = baseItem.getCheckBody();
        String checkType = baseItem.getCheckClass();
        try {
            String bodyTxt = DictionaryController.instance().get("eh.base.dictionary.Body").getText(bodyDic);
            String examinationTypeName = DictionaryController.instance().get("eh.base.dictionary.CheckClass").getText(checkType);
            oci.setBody(bodyDic);
            oci.setBodyText(bodyTxt);
            oci.setCheckType(checkType);
            oci.setExaminationTypeName(examinationTypeName);
        } catch (ControllerException e) {
          logger.error(e);
        }
        map.put("organCheckItem", oci);
        if (patient == null) {
            map.put("patient", patient);
            return map;
        }

        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            patient.setRelationPatientId(rd.getRelationDoctorId());
            patient.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                patient.setSignFlag(true);
            } else {
                patient.setSignFlag(false);
            }
            patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd.getRelationDoctorId()));
        }
        map.put("patient", patient);
        return map;
    }

    @RpcService
    @DAOMethod(sql = "update CheckRequest set status=:status where checkRequestId=:checkRequestId and organId=:organId")
    public abstract void updateStatusBycheckRequestId(@DAOParam("status") Integer status, @DAOParam("checkRequestId") Integer checkRequestId, @DAOParam("organId") Integer organId);

    @RpcService
    public void updateCheckrequest(CheckRequest request) {
        update(request);
        // 已出报告待发布 : 获取报告之后需要发布任务给pacs，然后pacs调用注册接口回写给平台，从病人电子病历查看报告数据影像
        if (5 == request.getStatus()) {
            if (request.getOrganId() == 1) {// 邵逸夫医院区分院区代码
//                IHisServiceInterface reprotService = AppContextHolder.getBean("emr.taskQueue", IHisServiceInterface.class);
                TaskQueue q = new TaskQueue();
                // q.setCardno("1212045");
                q.setCardorgan("1");
                q.setCardtype("1");
                q.setCertno(request.getCertId());
                q.setCreatetime(new Date());
                q.setMpi(request.getMpiid());// 平台主索引
                q.setPatientid("");// 病历号
                q.setPatientName(request.getPatientName());
                q.setPatientType("1");
                q.setPriority(1);// 默认1
                q.setStatus(0);// 0初始
                q.setTrycount(0);//
                q.setOrganid(1);
                q.setTopic("QueryReport");// 主题，获取报告
                // 获取院区代码
//                Integer chkSourceId = request.getChkSourceId();
//                CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
                OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
                OrganCheckItem oc = organItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(request.getOrganId(), request.getCheckItemId(), request.getCheckAppointId());
                String code = oc.getOrganItemCode();//邵逸夫医院的需要加上院区
                String hosCode = code.split("\\|")[0];
                q.setExtendid(hosCode + "-" + request.getOrganRequestNo());// 扩展参数，传申请单号
                // 需要加上院区代码
                // q.setExtendid("A001-4614395");
                // RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "emr.taskQueue","saveTaskQueue",q);                
            	if(DBParamLoaderUtil.getOrganSwich(q.getOrganid())){
            		ICheckHisService iCheckHisService = AppDomainContext.getBean("his.iCheckHisService", ICheckHisService.class);
            		TaskQueueHisTO reqTO= new TaskQueueHisTO();
            		BeanUtils.copy(q,reqTO);
            		iCheckHisService.saveTaskQueue(reqTO);
            	}else{
            		RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "emr.taskQueue","saveTaskQueue",q);
            	}
            }

        }
    }

    /**
     * 纳里平台向远程影像诊断中心进行远程影像诊断申请
     * @param checkRequest
     * @param list 影像序列
     */
    @RpcService
    public HisResponse remoteImageDiagApply(CheckRequest checkRequest, List<String> list){
//        String sss = "{\"checkRequestId\":446,\"mpiid\":\"2c9081854fd12c73014fd4c702820034\",\"patientName\":\"王翠丽\",\"patientSex\":\"2\",\"patientType\":\"1\",\"certId\":\"41272519940829612X\",\"mobile\":\"13002106863\",\"cardNo\":\"12567456\",\"disease\":\"qerw\",\"diseasesHistory\":\"qwer\",\"purpose\":\"qewr\",\"organId\":12,\"organRequestNo\":\"132162436\",\"checkType\":\"PFT\",\"examinationTypeName\":\"肺功能\",\"checkItemId\":9,\"checkItemName\":\"邵逸夫通用检查项目\",\"checkBody\":\"PFT\",\"checkDate\":\"2016-09-09 00:00:00\",\"requestOrgan\":1,\"requestDate\":\"2016-11-11 16:28:39\",\"rePortDoctorName\":\"er \",\"rePortDate\":\"2016-09-08 02:27:00\",\"status\":5,\"fromFlag\":1,\"requestType\":0,\"requestDoctorName\":\"候\",\"statusText\":\"已出报告待发布\",\"checkTypeText\":\"肺功能\",\"requestOrganText\":\"浙大附属邵逸夫医院\",\"requestDoctorIdText\":\"\",\"requestTypeText\":\"远程诊断\",\"patientSexText\":\"女\",\"patientTypeText\":\"自费\",\"workTypeText\":\"\",\"organIdText\":\"圣爱康复医院\",\"checkBodyText\":\"肺部\"}";

//        checkRequest = JSONUtils.parse(sss,checkRequest.getClass());
        logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请时传入数据checkRequest: "
                + JSONUtils.toString(checkRequest) + "影像序列：" + String.valueOf(list));

        HisResponse hisResponse = new HisResponse();

        try {

            //校验数据
            hisResponse = CheckRequestMethod.validateCheckRequest(checkRequest, list);

            if ("0".equals(hisResponse.getMsgCode())){
                return hisResponse;
            }

            if (checkRequest.getStatus() == 5){
                TaskQueue taskQueue = CheckRequestMethod.packCRtoTaskqueue(checkRequest, list);

                taskQueue.setTopic("RemoteImageApply"); //表示远程影像诊断申请
                logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请封装后的taskQueue：" + JSONUtils.toString(taskQueue));
                //Object dao =  RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,
                //        "emr.remoteImgApply","remoteImageApply", taskQueue);
                Object response = null;
            	if(DBParamLoaderUtil.getOrganSwich(taskQueue.getOrganid())){
            		IImageHisService iImageHisService = AppDomainContext.getBean("his.iImageHisService", IImageHisService.class);
            		HisResponseTO resTO = new HisResponseTO();
            		TaskQueueHisTO reqTO= new TaskQueueHisTO();
            		BeanUtils.copy(taskQueue,reqTO);
            		resTO = iImageHisService.remoteImageApply(reqTO);
                    BeanUtils.copy(resTO, hisResponse);
                    hisResponse.setMsg(hisResponse.getMsg() + hisResponse.getData());
                    logger.info("远程影像诊断申请远程返回信息：" + JSONUtils.toString(hisResponse));
                    return hisResponse;
            	}else{
            		response = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,"emr.remoteImgApply","remoteImageApply", taskQueue);
            	}
            	
                if (response != null){
                    hisResponse = (HisResponse) response;
                    hisResponse.setMsg(hisResponse.getMsg() + hisResponse.getData());
                    logger.info("远程影像诊断申请远程返回信息：" + JSONUtils.toString(hisResponse));
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("远程影像诊断申请失败！");
                    logger.info("远程影像诊断申请失败！");
//                        throw new DAOException("远程影像诊断申请失败！");
                }
            } else {
                hisResponse.setMsgCode("0");
                hisResponse.setMsg("此检查单不能申请远程影像诊断！");
                logger.info("此检查单不能申请远程影像诊断！");
            }

        } catch (Exception e){
            hisResponse.setMsgCode("0");
            hisResponse.setMsg("远程影像诊断申请程序出现异常！");
            logger.info("远程影像诊断申请程序出现异常！"+e);
        }
        return hisResponse;
    }

    @RpcService
    public List<CheckRequest> findCheckListBystatus(final Integer organId) {
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                Date startTime = new Date();
                Date endTime = DateConversion.getDateAftXDays(new Date(),-30);
                StringBuffer hql = new StringBuffer("from CheckRequest where organId=" + organId +
                        " and status in (1,2,3,4,5)  " +
                        " and DATE(checkDate)>=DATE(:endTime)" +
                        " and DATE(checkDate)<=DATE(:startTime)");
                Query q = ss.createQuery(hql.toString());
                q.setDate("startTime", startTime);
                q.setDate("endTime", endTime);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }

    /**
     * 过了检查当天未检查则为爽约
     *
     * @author luf
     */
    @RpcService
    public Integer cancelForOverTime() {
        final List<CheckRequest> crs = this.findOverTimeList();
        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            public void execute(StatelessSession ss) throws DAOException {
                for (CheckRequest cr : crs) {
                    cr.setStatus(8);
                    update(cr);
                    Integer chkSourceId = cr.getChkSourceId();
                    if (chkSourceId != null) {
                        CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
                        CheckSource cs = sourceDAO.get(chkSourceId);
                        cs.setUsedNum(cs.getUsedNum() - 1);
                        sourceDAO.update(cs);
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        action.getResult();
        return crs.size();
    }

    /**
     * 供 cancelForOverTime 调用
     *
     * @return List<CheckRequest>
     * @author luf
     */
    @DAOMethod(sql = "From CheckRequest where TIMESTAMPDIFF(DAY,checkDate,now())>=1 and status=2 ")
    public abstract List<CheckRequest> findOverTimeList();

    /**
     * 用于生成机构检查项目
     */
    public void autoCreateOrancheckitem() {
        final String checkClass = "X";
        final String checkBody = "X01";
        final String organCheckItemName = "X光(胸片正侧位)";
        final String organItemCode = "QC|X11.010";// QC 为 庆春院区
        final int organid = 1;
        final String checkAddr = "检查地点";
        final String memo = "注意事项";

        final CheckItemDAO checkitemDao = DAOFactory.getDAO(CheckItemDAO.class);
        final CheckAppointItemDAO CheckAppointItemDao = DAOFactory.getDAO(CheckAppointItemDAO.class);
        final OrganCheckItemDAO organCheckItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        HibernateStatelessResultAction<CheckItem> action = new AbstractHibernateStatelessResultAction<CheckItem>() {
            @Override
            public void execute(StatelessSession arg0) throws Exception {
                // 平台检查项目
                CheckItem checkitem = new CheckItem();
                checkitem.setCheckClass(checkClass);
                checkitem.setCheckBody(checkBody);
                checkitem.setCheckItemName(organCheckItemName);
                checkitem.setChooseBody(0);
                checkitemDao.save(checkitem);
                setResult(checkitem);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        final CheckItem result_Checkitem = action.getResult();

        // HibernateStatelessResultAction<CheckAppointItem> action2 = new
        // AbstractHibernateStatelessResultAction<CheckAppointItem>() {
        // @Override
        // public void execute(StatelessSession arg0) throws Exception {
        // //预约项目
        // CheckAppointItem checkAppointItem = new CheckAppointItem();
        // checkAppointItem.setOrganId(organid);
        // checkAppointItem.setCheckAppointName(organCheckItemName);
        // checkAppointItem.setOrganAppointId(organItemCode);
        // checkAppointItem.setCheckRoom(checkAddr);
        //
        // CheckAppointItemDao.save(checkAppointItem);
        // setResult(checkAppointItem);
        // }
        // };
        // HibernateSessionTemplate.instance().execute(action2);

        // final CheckAppointItem result_checkappointitem = action2.getResult();

        HibernateStatelessResultAction<OrganCheckItem> action3 = new AbstractHibernateStatelessResultAction<OrganCheckItem>() {
            @Override
            public void execute(StatelessSession arg0) throws Exception {
                // 机构检查项目
                OrganCheckItem organCheckItem = new OrganCheckItem();
                organCheckItem.setOrganId(organid);
                organCheckItem.setCheckItemId(result_Checkitem.getCheckItemId());
                // organCheckItem.setCheckAppointId(result_checkappointitem.getCheckAppointId());
                organCheckItem.setOrganItemCode(organItemCode);
                organCheckItem.setCheckAddr(checkAddr);
                organCheckItem.setCheckItemName(organCheckItemName);
                organCheckItem.setMemo(memo);
                organCheckItemDAO.save(organCheckItem);
            }
        };
        HibernateSessionTemplate.instance().execute(action3);

    }

    /**
     * 卫宁调用影像注册接口时更新申请记录表的报告图像id，供前台调用生成报告图像
     */
    @RpcService
    @DAOMethod(sql = "update CheckRequest set OrganDocID=:OrganDocID , status=0 where reportId=:reportId and organId=:organId")
    public abstract void updateOrganDocIDByReportIdAndOrganId(@DAOParam("OrganDocID") String OrganDocID, @DAOParam("reportId") String reportId, @DAOParam("organId") Integer organId);

    @RpcService
    public void updateReport(final String organDocID, final Integer status, final String organRequestNo, final Integer organId) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update CheckRequest set OrganDocID=:OrganDocID ,  status=:status " + "where organRequestNo=:organRequestNo and organId=:organId";
                Query query = ss.createQuery(hql);
                query.setString("OrganDocID", organDocID);
                query.setInteger("status", status);
                query.setString("organRequestNo", organRequestNo);
                query.setInteger("organId", organId);
                query.executeUpdate();
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }


    @RpcService
    public List<CheckRequest> findCheckListAdd(final Integer organId) {
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuffer hql = new StringBuffer("from CheckRequest where organId=" + organId + " order by checkRequestId desc");
                Query q = ss.createQuery(hql.toString());
                List<CheckRequest> resList = q.list();
                setResult(resList);
                int requestNum_now = resList.size();
                if (requestNum != 0 && requestNum_now != requestNum) {
                    CheckRequest check = resList.get(0);
                    String title = "【医技检查】新增预约记录，请处理";
                    String content = getContent(check);
                    //发送邮件
                    MailService.sendMail(title, content, "fucz@Ngarihealth.com");
                    MailService.sendMail(title, content, "tongl@Ngarihealth.com");
                    logger.info("邮件内容" + content);
                }
                requestNum = requestNum_now;
                logger.info("医技记录数：" + requestNum);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    protected String getContent(CheckRequest check) {
        StringBuffer sb = new StringBuffer();
        String requestTime = DateConversion.getDateFormatter(check.getRequestDate(), "yyyy-MM-dd HH:mm:ss");
        DoctorDAO doctor = DAOFactory.getDAO(DoctorDAO.class);
        String docName = doctor.getNameById(check.getRequestDoctorId());
        String checkDate = DateConversion.getDateFormatter(check.getStartDate(), "yyyy-MM-dd HH:mm:ss");
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        OrganCheckItem oc = organItemDAO.getByOrganIdAndCheckItemIdAndCheckAppointId(check.getOrganId(), check.getCheckItemId(), check.getCheckAppointId());
        String addr = oc.getCheckAddr();
        sb.append("各位好：\n");
        sb.append(requestTime + "新增一条预约检查记录，请处理。具体信息如下：\n");
        sb.append("  患者：" + check.getPatientName() + ",手机号：" + check.getMobile() + "\n");
        sb.append("  患者病历号：" + check.getPatientID() + "\n");
        sb.append("  预约医生：" + docName + "\n");
        sb.append("  检查项目：" + check.getCheckItemName() + "\n");
        sb.append("  检查时间：" + checkDate + "\n");
        sb.append("  检查地点：" + addr + "\n");
        String content = sb.toString();
        logger.info(content);
        return content;
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
     * @return QueryResult<CheckRequest>
     */
    @RpcService
    public QueryResult<CheckRequest> queryCheckListForOP(
            final Integer timeType, final Date startTime, final Date endTime,
            final CheckRequest checkRequest, final Integer start, final Integer limit) {
        this.validateOptionForStatistics(timeType,startTime,endTime,checkRequest,start,limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType,startTime,endTime,checkRequest,start,limit);;
        HibernateStatelessResultAction<QueryResult<CheckRequest>> action = new AbstractHibernateStatelessResultAction<QueryResult<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                int total = 0;
                StringBuilder hql = preparedHql;
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setDate("startTime", startTime);
                countQuery.setDate("endTime", endTime);
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数
                hql.append(" order by checkRequestId desc");
                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CheckRequest> resList = query.list();
                QueryResult<CheckRequest> qResult = new QueryResult<CheckRequest>(
                        total, query.getFirstResult(), query.getMaxResults(), resList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<CheckRequest>) action.getResult();
    }

    /**
     * 根据状态统计
     *
     * @param timeType     查询时间类型
     * @param startTime    起始时间
     * @param endTime      结束时间
     * @param checkRequest 检查单
     * @param start        分页起始位置
     * @param limit        条数
     * @return HashMap<String, Integer>
     */
    public HashMap<String, Integer> getStatisticsByStatus(final Integer timeType, final Date startTime, final Date endTime,
                                                          final CheckRequest checkRequest, final Integer start, final Integer limit) {
        this.validateOptionForStatistics(timeType,startTime,endTime,checkRequest,start,limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType,startTime,endTime,checkRequest,start,limit);;
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by status ");
                Query query = ss.createQuery("select status, count(checkRequestId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if(hps[0] != null  && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.base.dictionary.CheckRequestStatus").getText(status);
                            mapStatistics.put(statusName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据检查项目统计
     *
     * @param timeType     查询时间类型
     * @param startTime    起始时间
     * @param endTime      结束时间
     * @param checkRequest 检查单
     * @param start        分页起始位置
     * @param limit        条数
     * @return HashMap<String, Integer>
     */
    public HashMap<String, Integer> getStatisticsByCheckItemName(final Integer timeType, final Date startTime, final Date endTime,
                                                          final CheckRequest checkRequest, final Integer start, final Integer limit) {
        this.validateOptionForStatistics(timeType,startTime,endTime,checkRequest,start,limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType,startTime,endTime,checkRequest,start,limit);;
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by checkItemName ");
                Query query = ss.createQuery("select checkItemName, count(checkRequestId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null)
                        {
                            String checkItemName = hps[0].toString();
                            mapStatistics.put(checkItemName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据申请机构统计
     *
     * @param timeType     查询时间类型
     * @param startTime    起始时间
     * @param endTime      结束时间
     * @param checkRequest 检查单
     * @param start        分页起始位置
     * @param limit        条数
     * @return HashMap<String, Integer>
     */
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                                final CheckRequest checkRequest, final Integer start, final Integer limit) {
        this.validateOptionForStatistics(timeType,startTime,endTime,checkRequest,start,limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType,startTime,endTime,checkRequest,start,limit);;
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by requestOrgan ");
                Query query = ss.createQuery("select requestOrgan, count(checkRequestId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if(hps[0] != null  && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            Integer requestOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(requestOrganId, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        HashMap<Integer, Integer> map = action.getResult();
        return DoctorUtil.translateOrganHash(map);
    }

    /**
     * 根据目标机构统计
     *
     * @param timeType     查询时间类型
     * @param startTime    起始时间
     * @param endTime      结束时间
     * @param checkRequest 检查单
     * @param start        分页起始位置
     * @param limit        条数
     * @return HashMap<String, Integer>
     */
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                               final CheckRequest checkRequest, final Integer start, final Integer limit) {
        this.validateOptionForStatistics(timeType,startTime,endTime,checkRequest,start,limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType,startTime,endTime,checkRequest,start,limit);;
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by organId ");
                Query query = ss.createQuery("select organId, count(checkRequestId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if(hps[0] != null  && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            Integer targetOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(targetOrganId, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        HashMap<Integer, Integer> map = action.getResult();
        return DoctorUtil.translateOrganHash(map);
    }

    private void validateOptionForStatistics(
            final Integer timeType, final Date startTime, final Date endTime,
            final CheckRequest checkRequest, final Integer start, final Integer limit) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "结束时间不能为空");
        }
    }

    private StringBuilder generateHQLforStatistics(
            final Integer timeType, final Date startTime, final Date endTime,
            final CheckRequest checkRequest, final Integer start, final Integer limit) {
        StringBuilder hql = new StringBuilder("from CheckRequest where 1=1 ");
        if (1 == timeType) {
            if (startTime != null) {
                hql.append(" and DATE(requestDate)>=DATE(:startTime)");
            }
            if (endTime != null) {
                hql.append(" and DATE(requestDate)<=DATE(:endTime)");
            }
        } else if (2 == timeType) {
            if (startTime != null) {
                hql.append(" and DATE(checkDate)>=DATE(:startTime)");
            }
            if (endTime != null) {
                hql.append(" and DATE(checkDate)<=DATE(:endTime)");
            }
        }
        if (checkRequest != null) {
            if (checkRequest.getRequestOrgan() != null) {
                hql.append(" and requestOrgan=" + checkRequest.getRequestOrgan());
            }
            if (checkRequest.getRequestDoctorId() != null) {
                hql.append(" and requestDoctorId=" + checkRequest.getRequestDoctorId());
            }
            if (checkRequest.getOrganId() != null) {
                hql.append(" and organId=" + checkRequest.getOrganId());
            }
            if (checkRequest.getStatus() != null) {
                hql.append(" and status=" + checkRequest.getStatus());
            }
            if (!StringUtils.isEmpty(checkRequest.getMpiid())) {
                hql.append(" and mpiid='" + checkRequest.getMpiid() + "'");
            }
            if (!StringUtils.isEmpty(checkRequest.getPatientID())) {
                hql.append(" and patientID='" + checkRequest.getPatientID() + "'");
            }
            if (checkRequest.getCheckAppointId() != null) {
                hql.append(" and checkAppointId=" + checkRequest.getCheckAppointId());
            }
            if (checkRequest.getCheckItemId() != null) {
                hql.append(" and checkItemId=" + checkRequest.getCheckItemId());
            }
        } else {
            logger.error(checkRequest + ":查询条件不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "查询条件不能为空");
        }
        return hql;
    }

    @RpcService
    @DAOMethod(sql = "from CheckRequest where organId=:organId and chkSourceId=:chkSourceId and checkAppointId=:checkAppointId")
    public abstract List<CheckRequest> findCheckRequestByCheckAppointIdAndChkSourceId(
            @DAOParam("organId") Integer organId,
            @DAOParam("checkAppointId") Integer checkAppointId,
            @DAOParam("chkSourceId") Integer chkSourceId
    );

    //查询该号源所有待检查的检查申请
    @DAOMethod(sql = "from CheckRequest where organId=:organId and chkSourceId=:chkSourceId and checkAppointId=:checkAppointId and status in(2,3)")
    public abstract List<CheckRequest> findDjcCheckRequestByCheckAppointIdAndChkSourceId(
            @DAOParam("organId") Integer organId,
            @DAOParam("checkAppointId") Integer checkAppointId,
            @DAOParam("chkSourceId") Integer chkSourceId
    );



    /**
     * 医技检查预约停诊短信通知申请医生和患者
     *
     * @author houxr
     * @date 2016-07-27 上午9:29:35
     */
    @RpcService
    public void sendSmsForCheckSourceStopToDocAndPat(Integer checkRequestId) {
        //// 切换为ONS的方式(CheckRequestStop)
       /* CheckRequestDAO recordDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckRequest checkRequest = recordDAO.getByCheckRequestId(checkRequestId);
        SmsInfo info = new SmsInfo();
        info.setBusId(checkRequest.getCheckRequestId());
        info.setBusType("checkSourceStop");// 业务类型
        info.setSmsType("smsForCheckSourceStopToDocAndPat");// 医技检查号源停诊短信通知
        info.setStatus(0);
        info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        exe.execute();*/
        SmsInfo info = new SmsInfo();
        info.setBusId(checkRequestId);
        info.setBusType("CheckRequestStop");
        info.setSmsType("CheckRequestStop");
        info.setStatus(0);
        info.setOrganId(0);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);

    }

    /**
     * 根据审核状态和机构ID查询检查单总条数
     * @param checkStatus
     * @param requestOrgan
     * @return
     */
    public Long getTotalNumByCheckStatusAndOrganId(final int checkStatus, final int requestOrgan){
        Long totalNum = 0L;
        final int customerHour = ConfigParam.CHECKLISTTIMEINTERVAL; //几小时内的数据
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select count(*) from CheckRequest where" +
                        " organId=:requestOrgan");
                //未审核
                if (checkStatus == 0){
                    hql.append(" and status = 2 and checkStatus = 0  and TIMESTAMPDIFF(HOUR,RequestDate,NOW())<=:customerHour");
                }
                //已审核
                if (checkStatus == 1){
                    hql.append(" and checkStatus != 0");
                }
                //全部
                if (checkStatus == 2){
                    //不追加
                }
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("requestOrgan", requestOrgan);
                if (checkStatus == 0){
                    query.setParameter("customerHour", customerHour);
                }
                setResult((Long) query.uniqueResult()); //得到总条数
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);   //执行
        logger.info("总条数" + action.getResult());
        return action.getResult();
    }
    /**
     * 检查单审核平台根据审核状态和机构ID显示检查单列表 带分页()
     * @param checkStatus
     * @param requestOrgan
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> getCheckListByCheckStatusAndRequestOrgan(final int checkStatus, final int requestOrgan, final int start, final int limit){

        final int customerHour = ConfigParam.CHECKLISTTIMEINTERVAL; //几小时内的数据
//        logger.info("入参----checkStatus" + checkStatus + "requestOrgan" + requestOrgan + "start" + start + "limit" + limit);
        try {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);    //患者
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);   //医生
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);  //机构
        DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);   //门诊

        List<HashMap<String, Object>> mapList = new ArrayList<>(); //用来装填结果列表
        HashMap<String, Object> countMap = new HashMap<>();  //用来装填分页信息

        long totalNum = 0;  //总条数
        Long total = getTotalNumByCheckStatusAndOrganId(checkStatus, requestOrgan);
        if (total != null){
            totalNum = total;
        }
        countMap.put("total", totalNum);
        countMap.put("start", start);
        countMap.put("limit", limit);
        mapList.add(countMap);  //装填分页信息
//            logger.info("countMap----" + countMap.toString());
        //根据审核状态和申请机构ID从检查申请单中查询确认成功待支付Status=2的检查单列表
        List<CheckRequest> checkRequestList = new ArrayList<>();
        HibernateStatelessResultAction<List<CheckRequest>> actionList = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("from CheckRequest where organId=:requestOrgan");
                //未审核
                if (checkStatus == 0){
                    hql.append(" and status = 2 and checkStatus = 0 and TIMESTAMPDIFF(HOUR,RequestDate,NOW())<=:customerHour");
                }
                //已审核
                if (checkStatus == 1){
                    hql.append(" and checkStatus != 0");
                }
                //全部
                if (checkStatus == 2){
                    //不追加
                }
                hql.append(" order by requestDate desc");   //按照开单时间由近及远排列
//                logger.info("hql语句----" + hql.toString());
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("requestOrgan", requestOrgan);
                if (checkStatus == 0){
                    query.setParameter("customerHour", customerHour);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().execute(actionList);
        checkRequestList = actionList.getResult();  //得到检查单列表
//        logger.info("checkRequestList-----" + checkRequestList.toString());
        //装填检查单列表信息
        for (CheckRequest cr : checkRequestList){
            HashMap<String, Object> listMap = new HashMap<>();
            CheckRequest checkRequest = packagingCheckRequest(cr); //封装CheckRequest

            listMap.put("checkRequest", checkRequest);  //装填checkRequest

            Patient patient = patientDAO.getPatientByMpiId(cr.getMpiid());  //得到患者对象
            if (patient != null){
                listMap.put("patientName", patient.getPatientName());   //装填患者姓名
            } else {
                logger.info("patient为空！");
            }
            Doctor doctor = null;
            if (cr.getRequestDoctorId() == null) {
                listMap.put("doctorName", "");
            }else {
                doctor = doctorDAO.getByDoctorId(cr.getRequestDoctorId());   //得到医生对象
            }
            if (doctor != null ){
                listMap.put("doctorName", doctor.getName());    //装填开单医生姓名
            } else {
                logger.info("doctor为空！");
            }

            Organ organ = organDAO.getByOrganId(cr.getRequestOrgan());  //得到机构对象
            if (organ != null){
                listMap.put("organShortName", organ.getShortName()); //装填开单机构简称
            } else {
                logger.info("organ为空！");
            }

            Department department = null;
            if(cr.getRequestDepartId()==null){
                listMap.put("departmentName", "");
            } else {
                department = departmentDAO.getById(cr.getRequestDepartId()); //得到门诊对象
            }
            if (department != null){
                listMap.put("departmentName", department.getName());    //装填开单门诊名字
            } else {
                logger.info("department为空！");
            }

            mapList.add(listMap);   //装填检查单信息列表
        }
//        logger.info("返回数据-----" + mapList.toString());
            return mapList;
        } catch (Exception e){
            logger.error(e);
        }
        return null;
    }

    @RpcService
    public boolean checkIsExistUncheckedRequest(final int organ){
        final int customerHour = ConfigParam.CHECKLISTTIMEINTERVAL;
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                String hql = "select count(*) from CheckRequest where organId=:requestOrgan and status = 2 and checkStatus = 0 and TIMESTAMPDIFF(HOUR,RequestDate,NOW()) <=:customerHour";
                Query q = ss.createQuery(hql);
                q.setParameter("requestOrgan", organ);
                q.setParameter("customerHour", customerHour);
                Long count = (Long) q.uniqueResult();
                if (count > 0)
                    setResult(true);
                else
                    setResult(false);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 通过检查单号查询检查单详情
     * @param checkRequestId
     * @return
     */
    @RpcService
    public HashMap<String, Object> getCheckDetailByCheckRequestId(final int checkRequestId){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);    //患者
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);   //医生
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);  //机构
        DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);   //门诊
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);  //医生执业机构
        PatientTypeDAO patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);    //医保类型

        //查询检查单详情
        List<CheckRequest> checkRequestList = new ArrayList<>();
        HibernateStatelessResultAction<List<CheckRequest>> actionList = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("from CheckRequest where checkRequestId=:checkRequestId");
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("checkRequestId", checkRequestId);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().execute(actionList);
        checkRequestList = actionList.getResult();

        HashMap<String, Object> hashMap = new HashMap<>();  //用来装填返回信息
        //装填检查单详情
        if (checkRequestList != null && checkRequestList.size() > 0){
            CheckRequest cr = checkRequestList.get(0); //得到检查单
            CheckRequest checkRequest = packagingCheckRequest(cr); //封装CheckRequest

            hashMap.put("checkRequest", checkRequest);  //装填checkRequest

            Patient patient = patientDAO.getPatientByMpiId(cr.getMpiid());  //得到患者对象
            //计算患者年龄
            Integer patientAge = 0;
            Date patientBirthday = patient.getBirthday(); //患者出生日期
            if (patientBirthday != null){
                patientAge = DateConversion.getAge(patientBirthday);
                patient.setAge(patientAge);
            }
            hashMap.put("patient", patient);    //装填患者信息

            PatientType patientType = patientTypeDAO.get(patient.getPatientType()); //得到医保类型对象
            String patientTypeName = patientType.getText(); //医保类型名字
            hashMap.put("patientTypeName", patientTypeName);    //装填患者医保类型名字

            Doctor doctor = doctorDAO.getByDoctorId(cr.getRequestDoctorId());   //得到医生对象
            hashMap.put("doctorName", doctor.getName());    //装填医生名字

            String doctorOrgan = organDAO.getByDoctorId(cr.getRequestDoctorId()).getShortName();
            hashMap.put("doctorOrgan", doctorOrgan); //装填医生第一执业机构名称

            int doctorId = doctor.getDoctorId();    //医生ID
            int doctorOrganId = doctor.getOrgan();  //医生职业机构ID
            Employment employment = employmentDAO.getDeptNameByDoctorIdAndOrganId(doctorId, doctorOrganId);  //得到医生执业机构对象
            String doctorDepName = employment.getDeptName();    //医生所在科室
            hashMap.put("doctorDepName", doctorDepName);    //装填医生所在科室

            Organ organ = organDAO.getByOrganId(cr.getRequestOrgan());  //得到机构对象
            hashMap.put("organShortName", organ.getShortName()); //装填开单机构简称

            Department department = departmentDAO.getById(cr.getRequestDepartId()); //得到门诊对象
            hashMap.put("departmentName", department.getName());    //装填开单门诊名字
              //装填开单门诊名字
            hashMap.put("photo", doctor.getPhoto());    //医生照片
            try {
                String protitle = DictionaryController.instance()
                        .get("eh.base.dictionary.ProTitle")
                        .getText(doctor.getProTitle());
                hashMap.put("proTitle", protitle);//医生职称
            } catch (ControllerException e) {
                logger.error(e);
            }
        } else {
            logger.error("无此检查单！");
        }
        return hashMap;
    }

    /**
     *审核检查单
     * @param checkRequestId 检查申请单号
     * @param checkStatus 审核状态(1是通过，2是不通过)
     * @param auditor 审核人姓名
     * @param notPassReason 审核不通过原因
     * @return
     */
    @RpcService
    public Boolean saveVerifyResult(final int checkRequestId, final int checkStatus, final String auditor, final String notPassReason){
        logger.info("saveCheckResult [checkRequestId: " + checkRequestId + ", checkStatus: " + checkStatus + "]");
        Boolean flag = false;
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        if (checkRequestId > 0){
            final CheckRequest checkRequest = checkRequestDAO.getByCheckRequestId(checkRequestId);
            if (checkRequest != null){
                if (checkRequest.getStatus() == 2){
                    //更新审核状态
                    final Boolean finalFlag = flag;
                    HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
                        @Override
                        public void execute(StatelessSession statelessSession) throws Exception {
                            StringBuffer hql = new StringBuffer("update CheckRequest set checkStatus=:checkStatus," +
                                    " auditor=:auditor, verifyDate = NOW()");
                            if (checkStatus == 1 || checkStatus == 2){  //校验传入的参数是否合法
                                if (checkStatus == 2){  //如果审核不通过将状态更改为预约取消
                                    hql.append(", status = 9, cancelDate = now(), cancelName=:cancelName," +
                                            " cancelResean = :notPassReason, notPassReason=:notPassReason");
                                }
                                hql.append(" where checkRequestId=:checkRequestId");
                                Query query = statelessSession.createQuery(hql.toString());
                                query.setParameter("checkStatus", checkStatus);
                                query.setParameter("checkRequestId", checkRequestId);
                                query.setParameter("auditor", auditor);
                                if (checkStatus == 2){
                                    query.setParameter("cancelName", auditor);
                                    if (notPassReason != null && notPassReason.trim().length() > 0){
                                        query.setParameter("notPassReason", notPassReason);
                                    } else {
                                        logger.error("请填写不通过原因！");
                                    }
                                }
                                int falg1 = query.executeUpdate();   //执行更新操作
                                setResult(falg1 == 1);   //如果更新结果等于1表示更新成功
                            } else {
                                logger.error("审核状态传入不合法！");
                                return;
                            }
                        }
                    };
                    HibernateSessionTemplate.instance().execute(action);
                    flag = (Boolean) action.getResult();    //将更新结果赋给flag
                } else {
                    logger.error("此检查单status不为2,你不能审核此检查单！");
                    throw (new DAOException("此检查单状态异常，无法审核！"));
                }
            } else {
                logger.error("没有此检查单！");
            }
        } else {
            logger.error("检查单号不正确");
        }
        return flag;
    }

    /**
     * 检查单超过一定时间自动审核通过
     */
    @RpcService
    public void autoVerifyChecklistByOvertime(){
        final int customerHour = ConfigParam.CHECKLISTTIMEINTERVAL; //自定义时间间隔
        logger.info("系统开始审核----");
        HibernateStatelessResultAction<List<CheckRequest>> action = new AbstractHibernateStatelessResultAction<List<CheckRequest>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("from CheckRequest where status = 2 and checkStatus = 0" +
                        " and TIMESTAMPDIFF(HOUR,RequestDate,NOW())>=:customerHour");
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("customerHour", customerHour);
                setResult(query.list()); //封装查询结果
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action); //执行hibernateAction
        List<CheckRequest> checkRequestList = action.getResult(); //得到超时的检查单列表
        if (checkRequestList != null && checkRequestList.size() > 0){
            logger.info("需要自动审核的检查单个数：" + checkRequestList.size());
            for(CheckRequest cr: checkRequestList){
                cr.setCheckStatus(1); //审核通过
                cr.setAuditor("系统");
                cr.setVerifyDate(new Date());
                update(cr); //更新
            }
            logger.info("已成功自动审核" + checkRequestList.size() + "个检查单！");
        } else {
            logger.info("没有需要自动审核的检查单！");
        }
    }
    /**
     * 封装CheckRequest信息
     * @param cr 被封装的CheckRequest对象
     */
    public CheckRequest packagingCheckRequest(CheckRequest cr){
        CheckRequest checkRequest = new CheckRequest();
        if (cr != null){
            checkRequest.setCheckRequestId(cr.getCheckRequestId());
            checkRequest.setMpiid(cr.getMpiid());
            checkRequest.setPatientID(cr.getPatientID());
            checkRequest.setPatientName(cr.getPatientName());
            checkRequest.setPatientSex(cr.getPatientSex());
            checkRequest.setPatientType(cr.getPatientType());
            checkRequest.setCertId(cr.getCertId());
            checkRequest.setMobile(cr.getMobile());
            checkRequest.setCardNo(cr.getCardNo());
            checkRequest.setDisease(cr.getDisease());
            checkRequest.setDiseaseCode(cr.getDiseaseCode());
            checkRequest.setDiseasesHistory(cr.getDiseasesHistory());
            checkRequest.setPurpose(cr.getPurpose());
            checkRequest.setOrganId(cr.getOrganId());
            checkRequest.setOrganRequestNo(cr.getOrganRequestNo());
            checkRequest.setCheckType(cr.getCheckType());
            checkRequest.setExaminationTypeName(cr.getExaminationTypeName());
            checkRequest.setCheckItemId(cr.getCheckItemId());
            checkRequest.setCheckItemName(cr.getCheckItemName());
            checkRequest.setCheckBody(cr.getCheckBody());
            checkRequest.setBodyPartName(cr.getBodyPartName());
            checkRequest.setCheckAppointId(cr.getCheckAppointId());
            checkRequest.setChkSourceId(cr.getChkSourceId());
            checkRequest.setCheckDate(cr.getCheckDate());
            checkRequest.setWorkType(cr.getWorkType());
            checkRequest.setStartDate(cr.getStartDate());
            checkRequest.setEndDate(cr.getEndDate());
            checkRequest.setOrderNum(cr.getOrderNum());
            checkRequest.setRequestOrgan(cr.getRequestOrgan());
            checkRequest.setRequestDepartId(cr.getRequestDepartId());
            checkRequest.setRequestDoctorId(cr.getRequestDoctorId());
            checkRequest.setRequestDate(cr.getRequestDate());
            checkRequest.setCheckPrice(cr.getCheckPrice());
            checkRequest.setPayFlag(cr.getPayFlag());
            checkRequest.setRegisterDate(cr.getRegisterDate());
            checkRequest.setRegisterName(cr.getRegisterName());
            checkRequest.setCancelResean(cr.getCancelResean());
            checkRequest.setCancelDate(cr.getCancelDate());
            checkRequest.setCancelName(cr.getCancelName());
            checkRequest.setReportId(cr.getReportId());
            checkRequest.setImageId(cr.getImageId());
            checkRequest.setExaminationDisplay(cr.getExaminationDisplay());
            checkRequest.setExaminationResult(cr.getExaminationResult());
            checkRequest.setSuggestion(cr.getSuggestion());
            checkRequest.setRePortDoctorName(cr.getRePortDoctorName());
            checkRequest.setRePortDate(cr.getRePortDate());
            checkRequest.setStatus(cr.getStatus());
            checkRequest.setStatusName(cr.getStatusName());
            checkRequest.setRequestString(cr.getRequestString());
            checkRequest.setOrganDocID(cr.getOrganDocID());
            checkRequest.setCheckStatus(cr.getCheckStatus());
            checkRequest.setAuditor(cr.getAuditor());
            checkRequest.setVerifyDate(cr.getVerifyDate());
            checkRequest.setNotPassReason(cr.getNotPassReason());
        } else {
            logger.error("传入的CheckRequest不能为空！");
        }
        return checkRequest;
    }

    /**
     * 获取远程诊断系统列表
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<CheckRequest> queryCheckListForRemote(final Integer organ, final String status, final Integer start, final Integer limit) {
        HibernateStatelessResultAction<QueryResult<CheckRequest>> action = new AbstractHibernateStatelessResultAction<QueryResult<CheckRequest>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                int total = 0;
                StringBuilder hql = new StringBuilder("from CheckRequest where fromFlag=1 and requestOrgan = " + organ );
                if(status!=null){
                    hql.append( " AND status in " + status);
                }
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数
                hql.append(" order by checkRequestId desc");
                Query query = ss.createQuery(hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CheckRequest> resList = query.list();
                QueryResult<CheckRequest> qResult = new QueryResult<CheckRequest>(
                        total, query.getFirstResult(), query.getMaxResults(), resList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<CheckRequest>) action.getResult();
    }

    @Override
    public CheckRequest save(CheckRequest checkRequest){
        if(checkRequest.getFromFlag()==null){
            checkRequest.setFromFlag(0);
        }
        return super.save(checkRequest);
    }
}
