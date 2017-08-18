package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.ngari.his.appoint.mode.CancelAllTransferRequestTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.account.session.SessionItemManager;
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
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.converter.support.StringToDate;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.OrganConstant;
import eh.bus.service.HisRemindService;
import eh.bus.service.ObtainImageInfoService;
import eh.bus.service.transfer.RequestTransferService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.base.*;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.Otherdoc;
import eh.entity.his.AppointInHosRequest;
import eh.entity.his.AppointInHosResponse;
import eh.entity.his.MedRequest;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.SmsInfo;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.EvaluationDAO;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.service.follow.FollowUpdateService;
import eh.msg.dao.SessionMemberDAO;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.task.executor.InHosAppointExecutor;
import eh.task.executor.TransferCancelExecutor;
import eh.task.executor.TransferSendExecutor;
import eh.task.executor.WxRefundExecutor;
import eh.util.DBParamLoaderUtil;
import eh.util.DoctorUtil;
import eh.util.RpcServiceInfoUtil;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.wxpay.service.NgariPayService;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 【门诊转诊确认】，【住院转诊确认】发送短信，由原来的更新成功发送改为his返回之后，更新我们数据库成功之后，才发送
 *
 * @author ZX
 *         <p>
 *         2015-06-03 业务需求，去掉限制【申请医生和目标医生不能为同一个人】【患者和申请医生不能为同一个人】-zx
 */

public abstract class TransferDAO extends HibernateSupportDelegateDAO<Transfer> {

    public static final Logger log = LoggerFactory.getLogger(TransferDAO.class);

    //App首页业务个性化
    private AsynDoBussService asynDoBussService=AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

//    // 住院转诊确认短信代码
//    private static final String IN_HOSPITAL_MSG_NO = "18188";
//    // 转诊确认短信代码(门诊)
//    private static final String OUT_PATIENT_MSG_NO = "18184";

    public TransferDAO() {
        super();
        this.setEntityName(Transfer.class.getName());
        this.setKeyField("transferId");
    }

    @RpcService
    @DAOMethod
    public abstract Transfer getById(int id);

    /**
     * 服务名称:转诊申请单创建服务(updated according to the method called createTransfer)
     * <p>
     * <p>
     * 将原来的字段组装成一个bean,并加入了一个文档List
     * </p>
     *
     * @param trans
     * @param otherDocs
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author Eric
     */
    @RpcService
    public void createTransferAndOtherDoc(final Transfer trans,
                                          final List<Otherdoc> otherDocs) throws DAOException,
            ControllerException {
        log.info("转诊申请前端数据:" + JSONUtils.toString(trans));

        if (trans.getRequestOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestOrgan is required!");
        }
        if (trans.getRequestDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestDepart is required!");
        }
        if (trans.getRequestDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestDoctor is required!");
        }

        String mpiId = trans.getMpiId();
        if (mpiId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "待转诊病人为空");
        }

        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        Patient patient=patDao.get(trans.getMpiId());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient==null || org.apache.commons.lang3.StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能转诊");
        }

        // 判断申请医生是否存在
        Integer requestDocId = trans.getRequestDoctor();
        if (requestDocId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请医生为空");
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor requestDoc = doctorDAO.getByDoctorId(requestDocId);
        if (requestDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请医生为空");
        }

        // 判断目标医生是否存在
        Integer targetDocId = trans.getTargetDoctor();
        if (targetDocId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医生为空");
        }

        final Doctor targetDoc = doctorDAO.getByDoctorId(targetDocId);
        boolean virtualdoctor = false;// 是否虚拟医生标志true是false否
        if (targetDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医生为空");
        } else {
            // 判断目标医生是否虚拟医生
            virtualdoctor = targetDoc.getVirtualDoctor() == null ? false
                    : targetDoc.getVirtualDoctor();
        }

        // 判断目标医院是否为可转诊医院
        Integer targetOrganId = trans.getTargetOrgan();
        if (targetOrganId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医院不能为空");
        }

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory
                .getDAO(HisServiceConfigDAO.class);
        // if (!hisServiceConfigDao.isServiceEnable(targetOrganId,
        // ServiceType.TRANSFER)) {
        // if (!virtualdoctor) {
        // throw new DAOException(609, "该医院暂时未开通转诊功能");
        // }
        // }

        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(trans.getAnswerTel())) {
            trans.setAnswerTel(DAOFactory.getDAO(PatientDAO.class)
                    .getMobileByMpiId(trans.getMpiId()));
        }

        // 蒋旭辉和王宁武转诊申请不限制
        if (mpiId.equals("2c9081814cc5cb8a014ccf86ae3d0000")
                || mpiId.equals("2c9081814cc3ad35014cc3e0361f0000")) {

        } else {
            TransferAndPatient tp = this.getApplyingTransferByMpiId(mpiId);
            if (tp != null) {
                throw new DAOException(602, "患者" + tp.getPatientName()
                        + "有一条未处理的转诊申请单，不能再进行转诊");
            }
        }

        try {
            trans.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        } catch (Exception e) {
            log.info(LocalStringUtil.format("createTransferAndOtherDoc get deviceId from session exception! errorMessage[{}]", e.getMessage()));
        }

        // 患者和目标医生不能为同一个人(申请医生不能把目标医生(患者身份)转诊给目标医生)
        if (SameUserMatching.patientAndDoctor(mpiId, trans.getTargetDoctor())) {
            throw new DAOException(609, "患者和目标医生不能为同一个人");
        }
        HibernateStatelessResultAction<Transfer> action = new AbstractHibernateStatelessResultAction<Transfer>() {

            @Override
            public void execute(StatelessSession arg0) throws Exception {
                // 保存转诊申请信息
                Date requestTime = new Date();
                trans.setRequestTime(requestTime);
                trans.setTransferStatus(0);
                trans.setInsuRecord(0);
                trans.setPayflag(1);// 默认为已支付
                trans.setIsAdd(true);// 加号转诊
                trans.setInsuFlag(trans.getInsuFlag() == null ? 0 : trans
                        .getInsuFlag());
                trans.setEvaStatus(0);
                Transfer tr = save(trans);
                if (tr == null) {
                    setResult(null);
                } else {
                    // 获取转诊单号
                    Integer transferId = tr.getTransferId();
                    // 保存图片
                    if (otherDocs != null && otherDocs.size() > 0) {
                        DAOFactory.getDAO(CdrOtherdocDAO.class)
                                .saveOtherDocList(1, transferId, otherDocs);
                    }
                    setResult(tr);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        // 如果转诊申请成功,则推送消息给目标医生
        if (action.getResult() != null) {
            Transfer t = action.getResult();

            //纳里医生App端首页个性化 转诊申请成功
            asynDoBussService.fireEvent(new BussCreateEvent(t, BussTypeConstant.TRANSFER));

            // 给转诊申请医生的推荐医生推荐奖励，不考虑该转诊单是否成功完成
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(t.getRequestDoctor());

            if (!virtualdoctor) {// 如果目标医生是非虚拟医生
                //2017-6-2 20:23:39 zhangx 消息优化：将消息拆分成更加细致的场景
                sendDocTransferApplyMsg(t);
            }

            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForTransfer(t);

            // 判断是否要发送给前置机
            int requestOrganId = trans.getRequestOrgan();

            if (hisServiceConfigDao.isServiceEnable(requestOrganId,
                    ServiceType.MEDFILING)) {
                log.info("发起医保备案服务");
                registTransfer(trans);
            }
            if (!virtualdoctor) {// 如果目标医生是非虚拟医生，则创建群聊
                // 2017-2-7 luf:关闭转诊创建群聊入口
//                // 创建群聊
//                GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
//                Integer transferId = t.getTransferId();
//                Group group = groupDAO.createTransferGroup(transferId);
//                if (!StringUtils.isEmpty(group.getGroupId())) {
//                    updateSessionStartTimeByTransferId(t.getRequestTime(),
//                            transferId);
//                    updateSessionIDByTransferId(group.getGroupId(), transferId);
//                }
            } else {// 如果目标医生是虚拟医生,则将该转诊申请直接确认
                autoConfirmTransferForVitural(t);
            }
        }
    }

    /**
     * 患者申请的转诊
     *
     * @param trans     转诊申请单
     * @param otherDocs 图片信息
     * @throws DAOException        505:必传值未传;609:业务判断限制
     * @throws ControllerException
     * @author zhangx
     * @date 2015-12-21 下午6:15:59
     * @desc 2016-02-26 患者申请的特需预约不进行限制
     */
    @RpcService
    public Transfer createPatientTransferAndOtherDoc(final Transfer trans,
                                                     final List<Otherdoc> otherDocs)
            throws DAOException, ControllerException {
        log.info("转诊患者申请前端数据:" + JSONUtils.toString(trans));

        Integer organId = trans.getTargetOrgan();
        if (StringUtils.isEmpty(organId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "该机构编码为空");
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        String mpiId = trans.getMpiId();
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "特需预约患者为空");
        }
        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        Patient patient=patDao.get(trans.getMpiId());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient==null || org.apache.commons.lang3.StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能转诊");
        }

        String requestMpi = trans.getRequestMpi();
        if (StringUtils.isEmpty(requestMpi)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "特需预约申请人为空");
        }

        // 判断目标医生是否存在
        Integer targetDocId = trans.getTargetDoctor();
        if (targetDocId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "特需预约目标医生为空");
        }

        final Doctor targetDoc = doctorDAO.getByDoctorId(targetDocId);
        boolean virtualdoctor = false;// 是否虚拟医生标志true是false否
        if (targetDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "特需预约目标医生为空");
        } else {
            // 判断目标医生是否虚拟医生
            virtualdoctor = targetDoc.getVirtualDoctor() == null ? false
                    : targetDoc.getVirtualDoctor();
        }

        if (virtualdoctor) {
            throw new DAOException(609, "该目标医生不接收特需预约");
        }

        // 判断目标医院是否为可转诊医院
        Integer targetOrganId = trans.getTargetOrgan();
        if (targetOrganId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "特需预约目标医院不能为空");
        }

        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(trans.getAnswerTel())) {
            trans.setAnswerTel(DAOFactory.getDAO(PatientDAO.class)
                    .getMobileByMpiId(trans.getMpiId()));
        }

        try {
            trans.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        } catch (Exception e) {
            log.info(LocalStringUtil.format("createPatientTransferAndOtherDoc get deviceId from session exception! errorMessage[{}]", e.getMessage()));
        }

        //2016-6-14 luf:微信2.1.1需求，目标医生为团队医生，则不做限制
        Map<String, Boolean> matchMap = SameUserMatching.patientsAndDoctor(mpiId, mpiId, trans.getTargetDoctor());
        if (null != matchMap && null != matchMap.get("teams") && null != matchMap.get("patSameWithDoc")) {
            Boolean teams = matchMap.get("teams");
            Boolean patSameWithDoc = matchMap.get("patSameWithDoc");
            if (!teams && patSameWithDoc) {
//                log.error("患者与目标医生不能为同一个人");
                throw new DAOException(609, "患者与目标医生不能为同一个人");
            }
        }
        //2016-6-20 luf:微信2.1.1需求，添加每天限制申请一条
        Date requestDate = new Date();
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        List<Transfer> transfers = transferDAO.findHasPatientTransfer(mpiId, requestMpi, targetDoc.getDoctorId(), requestDate);
        if (transfers != null && !transfers.isEmpty()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
        }

        //病人影像服务
        new ObtainImageInfoService(patient,organId).getImageInfo();

        HibernateStatelessResultAction<Transfer> action = new AbstractHibernateStatelessResultAction<Transfer>() {

            @Override
            public void execute(StatelessSession arg0) throws Exception {
                // 保存转诊申请信息
                Date requestTime = new Date();
                trans.setRequestTime(requestTime);
                trans.setTransferStatus(0);
                trans.setAccompanyFlag(false);
                trans.setRequestDoctor(null);
                trans.setInsuRecord(0);
                trans.setIsAdd(true);// 加号转诊
                trans.setInsuFlag(trans.getInsuFlag() == null ? 0 : trans
                        .getInsuFlag());
                if (trans.getTransferCost() != null
                        && trans.getTransferCost() == 0) {
                    trans.setPayflag(1);
                } else {
                    trans.setPayflag(0);
                }
                trans.setEvaStatus(0);

                Transfer tr = save(trans);
                if (tr == null) {
                    setResult(null);
                } else {
                    // 获取转诊单号
                    Integer transferId = tr.getTransferId();
                    // 保存图片
                    if (otherDocs != null && otherDocs.size() > 0) {
                        DAOFactory.getDAO(CdrOtherdocDAO.class)
                                .saveOtherDocList(1, transferId, otherDocs);
                    }
                    setResult(tr);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);


        // 如果转诊申请成功,则推送消息给目标医生
        if (action.getResult() != null) {
            Transfer t = action.getResult();

            //纳里医生App端首页个性化 转诊申请成功
            asynDoBussService.fireEvent(new BussCreateEvent(t, BussTypeConstant.TRANSFER));

            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForTransfer(t);

            if (t.getPayflag() != null && t.getPayflag() == 1) {
                // 消息推送、系统消息
                AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                        .pushMsgData2Ons(t.getTransferId(), trans.getTargetOrgan(), "PatTransferApply", "PatTransferApply", t.getDeviceId());
            }

            // 2017-2-7 luf:关闭转诊创建群聊入口
//            if (!virtualdoctor) {// 如果目标医生是非虚拟医生，则创建群聊
//                // 创建群聊
//                GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
//                Integer transferId = t.getTransferId();
//                Group group = groupDAO.createPatientTransferGroup(transferId);
//                if (!StringUtils.isEmpty(group.getGroupId())) {
//                    updateSessionStartTimeByTransferId(t.getRequestTime(),
//                            transferId);
//                    updateSessionIDByTransferId(group.getGroupId(), transferId);
//                }
//            }
        }
        if (action.getResult() != null) {
            return action.getResult();
        } else {
            return null;
        }
    }

    /**
     * Title:转诊医生为虚拟医生时，在转诊申请成功后直接对其确认 在方法createTransferAndOtherDoc()中调用
     *
     * @return void
     * @author zhangjr
     * @date 2015-9-22
     */
    public void autoConfirmTransferForVitural(final Transfer transfer)
            throws DAOException {
        log.info("转诊确认前端数据:" + JSONUtils.toString(transfer));
        final int transferId = transfer.getTransferId();
        final int agreeDoctor = transfer.getTargetDoctor();
        final int transferResultType = transfer.getTransferType();// 取transfertype值
        final int confirmOrgan = transfer.getTargetOrgan();
        final int confirmDepart = transfer.getTargetDepart();
        final int confirmDoctor = agreeDoctor;
        final int sourceLevel = 1;// 默认为普通号源类型

        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 更新transfer
                String hql = "update Transfer set transferStatus = 2, transferResult = 1, agreeDoctor=:agreeDoctor, agreeTime=:agreeTime, "
                        + "transferResultType=:transferResultType, confirmOrgan=:confirmOrgan, confirmDepart=:confirmDepart, confirmDoctor=:confirmDoctor,"
                        + "sourceLevel = :sourceLevel,confirmClinicAddr=:confirmClinicAddr,clinicPrice=:clinicPrice where transferId=:transferId";
                Query query = ss.createQuery(hql);
                query.setInteger("agreeDoctor", agreeDoctor);
                query.setTimestamp("agreeTime", new Date());
                query.setInteger("transferResultType", transferResultType);
                query.setInteger("confirmOrgan", confirmOrgan);
                query.setInteger("confirmDepart", confirmDepart);
                query.setInteger("confirmDoctor", confirmDoctor);
                query.setInteger("sourceLevel", sourceLevel);

                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                Organ organ = organDAO.getByOrganId(confirmOrgan);
                if (organ != null && !StringUtils.isEmpty(organ.getName())) {
                    query.setParameter("confirmClinicAddr", organ.getName());
                } else {
                    query.setParameter("confirmClinicAddr", " ");
                }
                query.setParameter("clinicPrice", 0d);

                query.setInteger("transferId", transferId);
                int result = query.executeUpdate();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult() > 0) {// 向申请医生推送消息、发送短信
            Integer requestDocId = transfer.getRequestDoctor();
            DoctorAccountDAO doctorAccountDAO = DAOFactory
                    .getDAO(DoctorAccountDAO.class);
            // 增加帐户收入（申请医生id，1，转诊单Id，0
            log.info("转诊预约成功,给申请医生增加账户金额,requestDocId:" + requestDocId);
            doctorAccountDAO.addDoctorIncome(requestDocId, 1, transferId, 0);

			/*
             * // 转诊自动接收发送给申请医生 sendSmsForTransferAutoConfirmForRequestDoc(tp);
			 * // 转诊自动接收发送短信给患者 sendSmsForTransferAutoConfirmForPatient(tp);
			 */
            // 转诊自动接收发送申请医生和患者
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                    .pushMsgData2Ons(transferId, transfer.getRequestOrgan(), "DocTransferVirtualConfirm", "DocTransferVirtualConfirm", transfer.getDeviceId());
        }
    }

    /**
     * 更新会话开始时间到转诊表（供 转诊申请单创建服务 调用）
     *
     * @param sessionStartTime
     * @param transferId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionStartTimeByTransferId(
            Date sessionStartTime, Integer transferId);

    /**
     * 更新群组ID到转诊表（供 转诊申请单创建服务 调用）
     *
     * @param sessionID
     * @param transferId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionIDByTransferId(String sessionID,
                                                     Integer transferId);

//    /**
//     * @param trans
//     * @param targetDoc
//     * @return void
//     * @throws ControllerException
//     * @function 有号转诊审核成功后给目标医生发送系统消息
//     * @author zhangjr
//     * @date 2016-3-10
//     */
//    public void pushMessageToTargetDocForSource(Integer transferId)
//            throws ControllerException {
//        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
//        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
//
//        Transfer trans = transferDAO.getById(transferId);
//        // 申请医生信息
//        String requestDoctorName = DictionaryController.instance()
//                .get("eh.base.dictionary.Doctor")
//                .getText(trans.getRequestDoctor());
//
//        // 申请科室名称
//        String requestDepartnName = DictionaryController.instance()
//                .get("eh.base.dictionary.Depart")
//                .getText(trans.getRequestDepart());
//
//        // 医院名称
//        String requestOrganName = DictionaryController.instance()
//                .get("eh.base.dictionary.Organ")
//                .getText(trans.getRequestOrgan());
//
//        // 病人
//        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
//        String patientName = patientDAO.get(trans.getMpiId()).getPatientName();
//
//        // 构建推送消息附加信息
//        Map<String, Object> custom = new HashMap<String, Object>();
//        custom.put("page", "transfer");
//        custom.put("id", trans.getTransferId());
//
//        String title = "转诊申请消息";
//
//        // 目标医生电话
//        String docTel = doctorDAO.getByDoctorId(trans.getTargetDoctor())
//                .getMobile();
//
//        Boolean teams = doctorDAO.getTeamsByDoctorId(trans.getTargetDoctor());
//
//        // 系统消息内容
//        String detailMsg = "您有一条新的转诊申请,患者:" + patientName + "; 申请医生："
//                + requestDoctorName + "(" + requestDepartnName + "|"
//                + requestOrganName + ")";
//
//        // 新增系统消息
//        SessionDetailService detailService = new SessionDetailService();
//        detailService.addSysTextMsgTransferToTarDoc(trans.getTransferId(), docTel, title, detailMsg, teams, true);
////        addMsgDetail(trans.getTransferId(), 1, docTel, "text", title,
////                detailMsg, "", true);
//    }

    /**
     * 服务名:获取待处理转诊单数服务
     *
     * @param doctorId
     * @param groupFlag
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public long getUnTransferNum(final int doctorId, final boolean groupFlag)
            throws DAOException {
        long result1 = 0;
        long result2 = 0;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select count(*) from Transfer where payflag=1 and transferStatus<2 and (targetDoctor=:doctorId or agreeDoctor=:doctorId)");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                long totalCount = (long) query.uniqueResult();
                setResult(totalCount);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        result1 = ((Long) action.getResult()).longValue();
        if (groupFlag) {
            HibernateStatelessResultAction action2 = new AbstractHibernateStatelessResultAction() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hpl = new StringBuilder(
                            "select count(*) from Transfer a, DoctorGroup b where payflag=1 and a.transferStatus<1 and a.targetDoctor = b.doctorId and b.memberId=:doctorId");
                    Query query = ss.createQuery(hpl.toString());
                    query.setParameter("doctorId", doctorId);
                    long totalCount = (long) query.uniqueResult();
                    setResult(totalCount);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action2);
            result2 = ((Long) action2.getResult()).longValue();
        }
        return result1 + result2;
    }

    /**
     * 服务名:查询转诊申请单列表服务 备注：显示该医生正在申请中的转诊申请单列表
     *
     * @param doctorId
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<TransferAndPatient> queryHisTransfer(final int doctorId)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus<2 and requestDoctor =:doctorId and b.mpiId=a.mpiId order by requestTime desc");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    // 获取签约标记
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    // 获取团队标记
                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 服务名:查询转诊申请单列表服务 (分页-两个参数) 备注：显示该医生正在申请中的转诊申请单列表
     *
     * @param doctorId
     * @param start
     * @param limit
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午2:06:27
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<TransferAndPatient> queryHisTransferWithStartAndLimit(
            final int doctorId, final int start, final int limit)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus<2 and requestDoctor =:doctorId and b.mpiId=a.mpiId order by requestTime desc");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    // 获取签约标记
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    Doctor targetDoc = doctorDAO.getByDoctorId(temp
                            .getTargetDoctor());
                    Boolean groupFlag = false;
                    if (targetDoc != null) {
                        // 获取团队标记
                        groupFlag = doctorDAO.getByDoctorId(
                                temp.getTargetDoctor()).getTeams();
                    }
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 服务名:查询转诊申请单列表服务 (分页-一个参数) 备注：显示该医生正在申请中的转诊申请单列表
     *
     * @param doctorId
     * @param start
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午2:06:27
     */
    @RpcService
    public List<TransferAndPatient> queryHisTransferWithStart(int doctorId,
                                                              int start, int limit) throws DAOException {
        return queryHisTransferWithStartAndLimit(doctorId, start, 10);
    }

    /**
     * 服务名:查询待处理转诊单列表服务
     *
     * @param doctorId
     * @param groupFlag
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<TransferAndPatient> queryTransfer(final int doctorId,
                                                  final boolean groupFlag) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (groupFlag) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where ((transferStatus<2 and (targetDoctor=:doctorId or agreeDoctor=:doctorId)) or (transferStatus<1 and targetDoctor in (select doctorId from DoctorGroup where memberId=:doctorId))) and b.mpiId=a.mpiId and a.payflag=1 order by requestTime asc");
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where transferStatus<2 and targetDoctor=:doctorId and b.mpiId=a.mpiId and a.payflag=1 order by requestTime asc");
                }
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);

                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 服务名:查询待处理转诊单列表服务-分页，两个参数
     *
     * @param doctorId
     * @param groupFlag
     * @param start
     * @param limit
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:03:09
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<TransferAndPatient> queryTransferWithStartAndLimit(
            final int doctorId, final boolean groupFlag, final int start,
            final int limit) throws DAOException {
        HibernateStatelessResultAction<List<TransferAndPatient>> action = new AbstractHibernateStatelessResultAction<List<TransferAndPatient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (groupFlag) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where ((transferStatus<2 and (targetDoctor=:doctorId or agreeDoctor=:doctorId)) or (transferStatus<1 and targetDoctor in (select doctorId from DoctorGroup where memberId=:doctorId))) and b.mpiId=a.mpiId and a.payflag=1 order by requestTime asc");
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where transferStatus<2 and targetDoctor=:doctorId and b.mpiId=a.mpiId and a.payflag=1 order by requestTime asc");
                }
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List<TransferAndPatient>) action.getResult();
        return tfList;
    }

    /**
     * 查询待处理转诊单列表服务-分页，一个参数
     *
     * @param doctorId
     * @param groupFlag
     * @param start
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:04:20
     */
    @RpcService
    public List<TransferAndPatient> queryTransferWithStart(int doctorId,
                                                           boolean groupFlag, int start) throws DAOException {
        return queryTransferWithStartAndLimit(doctorId, groupFlag, start, 10);
    }

    /**
     * 服务名:转诊审核开始服务
     *
     * @param transferId
     * @param agreeDoctor
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    public boolean startTransfer(final int transferId, final int agreeDoctor) throws DAOException {
        Transfer transfer = this.getById(transferId);
        Boolean b = false;
        if (transfer.getTransferStatus() == 0) {
            HibernateSessionTemplate.instance().execute(
                    new HibernateStatelessAction() {
                        public void execute(StatelessSession ss)
                                throws Exception {
                            String hql = "update Transfer set transferStatus = 1, agreeDoctor=:agreeDoctor where transferId=:transferId";
                            Query query = ss.createQuery(hql);
                            query.setInteger("agreeDoctor", agreeDoctor);
                            query.setParameter("transferId", transferId);
                            query.executeUpdate();
                        }
                    });

            b = true;
        } else if (transfer.getTransferStatus() == 1) {
            if (transfer.getAgreeDoctor().equals(agreeDoctor)) {
                b = true;
            } else {
                b = false;
            }
        } else {
            b = false;
        }
        if (!b) {
            return b;
        }
        // 2017-2-7 luf:关闭转诊创建群聊入口
//        GroupDAO gDao = DAOFactory.getDAO(GroupDAO.class);
//        Integer bussType = 1;
//        gDao.addUserToGroup(bussType, transferId, agreeDoctor);

        //纳里医生App端首页业务 转诊开始接收服务
//        asynDoBussService.fireEvent(new BussAcceptEvent(transferId, BussTypeConstant.TRANSFER, agreeDoctor));

        return b;
    }

    /**
     * 服务名:获取转诊单信息服务
     *
     * @param transferId
     * @return TransferAndPatient
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TransferAndPatient getTransferByID(final int transferId) {

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patDAO = DAOFactory.getDAO(PatientDAO.class);

        TransferAndPatient temp=new TransferAndPatient();

        Transfer transfer=get(transferId);
        temp.setTransfer(transfer);

        Integer tarDocId = transfer.getTargetDoctor();
        Integer reqDocId = transfer.getRequestDoctor();
        String mpiId= transfer.getMpiId();

        Doctor tarDoc = doctorDAO.getByDoctorId(tarDocId);

        if (reqDocId != null) {
            String rMobile = doctorDAO.getByDoctorId(reqDocId)
                    .getMobile();
            temp.setRequestDoctorMobile(rMobile);
        }

        Boolean groupFlag = tarDoc.getTeams();
        temp.setTeams(groupFlag);

        String tMobile = tarDoc.getMobile();
        temp.setTargetDoctorMobile(tMobile);

        Patient pat=patDAO.get(mpiId);
        if(pat!=null){
            temp.setPatientSex(pat.getPatientSex());
            temp.setBirthday(pat.getBirthday());
            temp.setPhoto(pat.getPhoto());
            temp.setPatientName(pat.getPatientName());
            temp.setPatientType(pat.getPatientType());
            temp.setPatientMobile(pat.getMobile());
            temp.setIdcard(pat.getIdcard());
        }

        return temp;
    }

    /**
     * @param transferId 转诊单号
     * @return TransferAndPatient
     * @author Eric
     * <p>
     * 服务名:获取转诊单信息服务(updated according to the method called
     * getTransferByID)
     * @desc 修改以适应患者转诊申请详情查询 zhangjr 2016-04-21
     */
    @RpcService
    public TransferAndPatient getTransferAndCdrById(final Integer transferId)
            throws DAOException {
        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard,b.mpiId) from Transfer a,Patient b where transferId=:transferId and b.mpiId=a.mpiId";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("transferId", transferId);
                TransferAndPatient temp = (TransferAndPatient) query.list().get(0);

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Integer requestDoctorId = temp.getTransfer().getRequestDoctor();
                Integer targetDoctorId = temp.getTransfer().getTargetDoctor();

                Doctor requestDoctor = null;
                String rMobile = null;
                Integer requestBusyFlag = null;
                if (requestDoctorId != null) {
                    requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);
                    rMobile = requestDoctor.getMobile();
                    requestBusyFlag = doctorDAO.getBusyFlagByDoctorId(requestDoctorId);
                } else {//患者申请
                    String requestMpi = temp.getTransfer().getRequestMpi();
                    if (temp.getTransfer().getMpiId().equals(requestMpi)) {
                        temp.setRequestPatientName(temp.getPatientName());
                        temp.setRequestPatientTel(temp.getPatientMobile());
                        temp.setPhoto(temp.getPhoto());
                    } else {
                        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                        Patient requestPatient = patientDAO.getPatientByMpiId(requestMpi);
                        temp.setRequestPatientName(requestPatient.getPatientName());
                        temp.setRequestPatientTel(requestPatient.getMobile());
                        temp.setPhoto(requestPatient.getPhoto());
                    }
                }

                Doctor targetDoctor = doctorDAO.getByDoctorId(targetDoctorId);

                Boolean groupFlag = targetDoctor.getTeams();


                String tMobile = targetDoctor.getMobile();

                // 获取医生忙闲状态
                Integer exeDoctor = temp.getTransfer().getConfirmDoctor() == null ? null
                        : temp.getTransfer().getConfirmDoctor();
                Integer targetBusyFlag = null;
                if (exeDoctor != null) {
                    targetBusyFlag = doctorDAO.getBusyFlagByDoctorId(exeDoctor);
                }

                temp.setTeams(groupFlag);
                temp.setRequestDoctorMobile(rMobile);
                temp.setTargetDoctorMobile(tMobile);
                temp.setRequestBusyFlag(requestBusyFlag);
                temp.setTargetBusyFlag(targetBusyFlag);
                temp.setRequestDoctor(requestDoctor);
                temp.setTargetDoctor(targetDoctor);
                HealthCardDAO healthCardDao = DAOFactory
                        .getDAO(HealthCardDAO.class);
                // 获取患者医保卡号
                Integer cardOrgan = Integer.parseInt(temp.getPatientType());
                String cardId = healthCardDao.getMedicareCardId(
                        temp.getMpiId(), cardOrgan);
                temp.setCardId(cardId);

                // 获取挂号科室编码(appointDepartCode)
                String departId = temp.getTransfer().getAppointDepartId();
                if (departId != null) {

                    // 通过 挂号科室编码(appointDepartCode) 和 预约机构编号(ConfirmOrgan)
                    // 获取挂号科室
                    AppointDepartDAO AppointDepartDAO = DAOFactory
                            .getDAO(AppointDepartDAO.class);
                    AppointDepart appointdepart = AppointDepartDAO
                            .getByOrganIDAndAppointDepartCode(temp
                                    .getTransfer().getConfirmOrgan(), departId);

                    if (appointdepart != null) {
                        temp.getTransfer().setAppointDepartName(
                                appointdepart.getAppointDepartName());
                        temp.getTransfer().setProfessionCode(
                                appointdepart.getProfessionCode());

                    }
                }
                Transfer tran = temp.getTransfer();
                if (tran != null) {
                    List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                            CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                            1, tran.getTransferId());

                    temp.setCdrOtherdocs(cdrOtherdocs);
                }
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        TransferAndPatient tp = (TransferAndPatient) action.getResult();
        return tp;
    }

    /**
     * 获取住院转诊单详情
     *
     * @param transferId 转诊单号
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-27 下午8:26:42
     */
    @RpcService
    public TransferAndInhosp getInhospTransfeById(final Integer transferId)
            throws DAOException {
        HibernateStatelessResultAction<TransferAndInhosp> action = new AbstractHibernateStatelessResultAction<TransferAndInhosp>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 获取转诊单信息和病人信息
                String hql = "select new eh.entity.bus.TransferAndInhosp(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard,b.mpiId) from Transfer a,Patient b where transferId=:transferId and b.mpiId=a.mpiId";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("transferId", transferId);
                TransferAndInhosp temp = (TransferAndInhosp) query.list()
                        .get(0);

                // 获取团队标记
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                Integer requestDoctorId = temp.getTransfer().getRequestDoctor();
                Integer targetDoctorId = temp.getTransfer().getTargetDoctor();

                Doctor requestDoctor = null;
                if (StringUtils.isEmpty(requestDoctorId) == false) {
                    requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);
                }
                Doctor targetDoctor = doctorDAO.getByDoctorId(targetDoctorId);

                Boolean groupFlag = targetDoctor.getTeams();
                String rMobile = requestDoctor == null ? null : requestDoctor
                        .getMobile();
                String tMobile = targetDoctor.getMobile();

                temp.setRequestDoctor(requestDoctor);
                temp.setTargetDoctor(targetDoctor);
                temp.setTeams(groupFlag);
                temp.setRequestDoctorMobile(rMobile);
                temp.setTargetDoctorMobile(tMobile);

                // 获取文档资料
                Transfer tran = temp.getTransfer();
                if (tran != null) {
                    List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                            CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                            1, tran.getTransferId());
                    temp.setCdrOtherdocs(cdrOtherdocs);
                }

                // 获取住院预约信息
                AppointInhospDAO appointInhospDAO = DAOFactory
                        .getDAO(AppointInhospDAO.class);
                AppointInhosp inhospInfo = appointInhospDAO
                        .getByTransferId(transferId);
                temp.setAppointInhosp(inhospInfo);
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        TransferAndInhosp tp = (TransferAndInhosp) action.getResult();
        return tp;
    }

    /**
     * 获取医生近一个月的转诊记录
     *
     * @param doctorId 医生id
     * @return
     */
    @RpcService
    public List<TransferAndPatient> getHisByDoctorLastMonth(int doctorId) {

        Date endDate = Context.instance().get("date.getDatetime", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);

        return getHisByDoctor(doctorId, startDate, endDate);
    }

    /**
     * 获取医生近一个月的转诊记录
     *
     * @param doctorId 医生id
     * @return
     */
    @RpcService
    public List<TransferAndPatient> getHisByDoctorLastMonthStart(int doctorId,
                                                                 int start) {

        Date endDate = Context.instance().get("date.getDatetime", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);
        return getHisByDoctorWithStartAndLimit(doctorId, startDate, endDate,
                start, 10);
    }

    /**
     * 获取医生近一个月的转诊记录
     *
     * @param doctorId 医生id
     * @return
     */
    @RpcService
    public List<TransferAndPatient> getHisByDoctorLastMonthStartAndLimit(
            int doctorId, int start, int limit) {

        Date endDate = Context.instance().get("date.getDatetime", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);

        return getHisByDoctorWithStartAndLimit(doctorId, startDate, endDate,
                start, limit);
    }

    /**
     * 服务名:按照医生查询历史转诊单列表服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<TransferAndPatient> getHisByDoctor(final int doctorId,
                                                   final Date startDate, final Date endDate) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus>1 and (agreeDoctor=:doctorId or requestDoctor=:doctorId) and (requestTime>=:startDate and requestTime<=:endDate) and b.mpiId=a.mpiId order by requestTime desc";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                if (startDate.after(endDate)) {
                    query.setTimestamp("startDate", endDate);
                    query.setTimestamp("endDate", startDate);
                } else {
                    query.setTimestamp("startDate", startDate);
                    query.setTimestamp("endDate", endDate);
                }

                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                AppointDepartDAO AppointDepartDAO = DAOFactory
                        .getDAO(AppointDepartDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    // 获取签约标记
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    // 获取团队标记
                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);

                    // 获取挂号科室编码(appointDepartCode)
                    String departId = list.get(i).getTransfer()
                            .getAppointDepartId();
                    if (departId != null) {

                        // 通过 挂号科室编码(appointDepartCode) 和 预约机构编号(ConfirmOrgan)
                        // 获取挂号科室
                        AppointDepart appointdepart = AppointDepartDAO
                                .getByOrganIDAndAppointDepartCode(list.get(i)
                                                .getTransfer().getConfirmOrgan(),
                                        departId);

                        if (appointdepart != null) {
                            list.get(i)
                                    .getTransfer()
                                    .setAppointDepartName(
                                            appointdepart
                                                    .getAppointDepartName());
                            list.get(i)
                                    .getTransfer()
                                    .setProfessionCode(
                                            appointdepart.getProfessionCode());
                        }
                    }
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 按照医生查询历史转诊单列表服务-分页，两个参数
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @param start
     * @param limit
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:23:21
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<TransferAndPatient> getHisByDoctorWithStartAndLimit(
            final int doctorId, final Date startDate, final Date endDate,
            final int start, final int limit) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus>1 and (agreeDoctor=:doctorId or requestDoctor=:doctorId) and (requestTime>=:startDate and requestTime<=:endDate) and b.mpiId=a.mpiId order by requestTime desc";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                if (startDate.after(endDate)) {
                    query.setTimestamp("startDate", endDate);
                    query.setTimestamp("endDate", startDate);
                } else {
                    query.setTimestamp("startDate", startDate);
                    query.setTimestamp("endDate", endDate);
                }

                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<TransferAndPatient> list = query.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                AppointDepartDAO AppointDepartDAO = DAOFactory
                        .getDAO(AppointDepartDAO.class);

                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    // 获取签约标记
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(
                            temp.getMpiId(), doctorId);
                    list.get(i).setSignFlag(signFlag);

                    // 获取团队标记
                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);

                    // 获取挂号科室编码(appointDepartCode)
                    String departId = list.get(i).getTransfer()
                            .getAppointDepartId();
                    if (departId != null) {

                        // 通过 挂号科室编码(appointDepartCode) 和 预约机构编号(ConfirmOrgan)
                        // 获取挂号科室
                        AppointDepart appointdepart = AppointDepartDAO
                                .getByOrganIDAndAppointDepartCode(list.get(i)
                                                .getTransfer().getConfirmOrgan(),
                                        departId);

                        if (appointdepart != null) {
                            list.get(i)
                                    .getTransfer()
                                    .setAppointDepartName(
                                            appointdepart
                                                    .getAppointDepartName());
                            list.get(i)
                                    .getTransfer()
                                    .setProfessionCode(
                                            appointdepart.getProfessionCode());
                        }
                    }
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<TransferAndPatient> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 按照医生查询历史转诊单列表服务-分页，一个参数
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @param start
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:24:30
     */
    @RpcService
    public List<TransferAndPatient> getHisByDoctorWithStart(int doctorId,
                                                            Date startDate, Date endDate, int start) throws DAOException {
        return getHisByDoctorWithStartAndLimit(doctorId, startDate, endDate,
                start, 10);
    }

    /**
     * 服务名:按照主索引查询历史转诊单列表服务 备注：give up
     *
     * @param mpiId
     * @param startDate
     * @param endDate
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Transfer> getHisByMpi(final String mpiId, final Date startDate,
                                      final Date endDate) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus>1 and a.mpiId=:mpiId and (requestTime>=:startDate and requestTime<=:endDate) and b.mpiId=a.mpiId order by requestTime desc";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("mpiId", mpiId);
                if (startDate.after(endDate)) {
                    query.setTimestamp("startDate", endDate);
                    query.setTimestamp("endDate", startDate);
                } else {
                    query.setTimestamp("startDate", startDate);
                    query.setTimestamp("endDate", endDate);
                }
                List<TransferAndPatient> list = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Transfer> tfList = (List) action.getResult();
        return tfList;
    }

    /**
     * 根据主索引信息获取历史转诊单-分页(两个参数)
     *
     * @param mpiId
     * @param startDate
     * @param endDate
     * @param start
     * @param limit
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:27:02
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Transfer> getHisByMpiWithStartAndLimit(final String mpiId,
                                                       final Date startDate, final Date endDate, final int start,
                                                       final int limit) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus>1 and a.mpiId=:mpiId and (requestTime>=:startDate and requestTime<=:endDate) and b.mpiId=a.mpiId order by requestTime desc";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("mpiId", mpiId);
                if (startDate.after(endDate)) {
                    query.setTimestamp("startDate", endDate);
                    query.setTimestamp("endDate", startDate);
                } else {
                    query.setTimestamp("startDate", startDate);
                    query.setTimestamp("endDate", endDate);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<TransferAndPatient> list = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Transfer temp = list.get(i).getTransfer();

                    Boolean groupFlag = doctorDAO.getByDoctorId(
                            temp.getTargetDoctor()).getTeams();
                    list.get(i).setTeams(groupFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Transfer> tfList= (List) action.getResult();
        return tfList;
    }

    /**
     * 根据主索引信息获取历史转诊单-分页(一个参数)
     *
     * @param mpiId
     * @param startDate
     * @param endDate
     * @param start
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-4 下午5:28:34
     */
    @RpcService
    public List<Transfer> getHisByMpiWithStart(String mpiId, Date startDate,
                                               Date endDate, int start) throws DAOException {
        return getHisByMpiWithStartAndLimit(mpiId, startDate, endDate, start,
                10);
    }

    /**
     * 转诊确认服务
     *
     * @param transfer
     * @throws DAOException 2015-04-29 发送短信，由原来的更新成功发送改为his返回之后，更新我们数据库成功之后，才发送
     * @author ZX
     * @date 2015-4-20 下午5:25:29
     */
    @RpcService
    public void confirmTransfer(final Transfer transfer) throws DAOException {
        log.info("转诊确认前端数据:" + JSONUtils.toString(transfer));

        // 接收数据校验
        isValidConfirmTransferData(transfer);

        final int confirmOrgan = transfer.getConfirmOrgan();
        final int confirmDoctor = transfer.getConfirmDoctor();
        // 省中湖滨下沙 特殊处理(无排班默认将挂号门诊改成特需门诊（预约记录和转诊记录 都改）)
        dealSZXS(transfer);

        final int transferId = transfer.getTransferId();
        final int agreeDoctor = transfer.getAgreeDoctor();
        final int transferResultType = transfer.getTransferResultType();
        final int confirmDepart = transfer.getConfirmDepart();
        final String confirmClinicAddr = transfer.getConfirmClinicAddr();
        final String returnMess = transfer.getReturnMess();
        final Double clinicPrice = transfer.getClinicPrice();
        final int sourceLevel = transfer.getSourceLevel();
        final String appointDepartCode = transfer.getAppointDepartId();
        final Date confirmClinicTime = transfer.getConfirmClinicTime();
        final boolean ifCreatePlan = (transfer.getIfCreateFollowPlan()==null)?false:transfer.getIfCreateFollowPlan();
        final int triggerId=(transfer.getTriggerId()==null)?0:transfer.getTriggerId();
        Date now = new Date();
        if (confirmClinicTime.before(now)) {
            throw new DAOException(601, "就诊时间必须在当前时间之后");
        }

        Transfer ts = this.getById(transferId);
        Integer transferType = transfer.getTransferType() == null || transfer.getTransferType() <= 0 ? ts.getTransferType() : transfer.getTransferType();
        if (null != transferType && transferType > 0) {
            DAOFactory.getDAO(HisServiceConfigDAO.class).isOverConfirmTimeLimit(confirmOrgan, transferType, confirmClinicTime);
        }

        //判断经过his的转诊接收是否在维护
        transfer.setTransferType(transferType);
        new HisRemindService().saveRemindRecordForNormalTransfer(transfer);

        Integer transferStatus = ts.getTransferStatus();
        if (transferStatus != 1) {
            throw new DAOException(601, "该转诊单未开始或者已处理");
        }
        if (transferStatus == 1 && agreeDoctor != ts.getAgreeDoctor()) {
            throw new DAOException(601, "该转诊单已被其他医生处理中，您无法进行接收操作");
        }

        AbstractHibernateStatelessResultAction<TransferAndPatient> action = new AbstractHibernateStatelessResultAction<TransferAndPatient>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 更新transfer
                String hql = "update Transfer set transferStatus =:transferStatus, transferResult = 1, agreeDoctor=:agreeDoctor, agreeTime=:agreeTime, "
                        + "transferResultType=:transferResultType, confirmOrgan=:confirmOrgan, confirmDepart=:confirmDepart, confirmDoctor=:confirmDoctor, "
                        + "confirmClinicTime=:confirmClinicTime, confirmClinicAddr=:confirmClinicAddr, returnMess=:returnMess, clinicPrice=:clinicPrice, "
                        + "sourceLevel=:sourceLevel,appointDepartId=:appointDepartId,ifCreateFollowPlan=:ifCreatePlan,triggerId=:triggerId where transferId=:transferId";
                Query query = ss.createQuery(hql);
                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);

                if (!hisServiceConfigDao.isServiceEnable(confirmOrgan, ServiceType.TOHIS)) {
                    query.setInteger("transferStatus", 2);
                } else {
                    query.setInteger("transferStatus", 8);
                }
                query.setInteger("agreeDoctor", agreeDoctor);
                query.setTimestamp("agreeTime", new Date());
                query.setInteger("transferResultType", transferResultType);
                query.setInteger("confirmOrgan", confirmOrgan);
                query.setInteger("confirmDepart", confirmDepart);
                query.setInteger("confirmDoctor", confirmDoctor);
                query.setTimestamp("confirmClinicTime", confirmClinicTime);
                query.setString("confirmClinicAddr", confirmClinicAddr);
                query.setString("returnMess", returnMess);
                query.setDouble("clinicPrice", clinicPrice);
                query.setInteger("sourceLevel", sourceLevel);
                query.setString("appointDepartId", appointDepartCode);
                query.setInteger("transferId", transferId);
                query.setBoolean("ifCreatePlan", ifCreatePlan);
                query.setInteger("triggerId",triggerId);
                query.executeUpdate();

                TransferAndPatient tp = getTransferByID(transferId);
                // 增加预约记录
                saveAppointRecord(tp);

                setResult(tp);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        TransferAndPatient tap = action.getResult();

        //纳里医生App端首页优化 转诊确认成功首页数据显示
        asynDoBussService.fireEvent(new BussFinishEvent(transferId, BussTypeConstant.TRANSFER));

        if (tap != null) {
            // 给予转诊确认医生的推荐医生推荐奖励
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(ts.getAgreeDoctor());
            // 转诊申请类型
            int transferType_request = action.getResult().getTransfer().getTransferType();
            // 转诊接收类型
            int transferType_result = action.getResult().getTransfer().getTransferResultType();
            // 门诊转诊
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            if (transferType_result == 1 && transferType_request != 3) {// 普通门诊转诊
                // 无接口
                // 自己预约成功
                AppointRecord appointRecord = appointRecordDAO
                        .getByTransferId(action.getResult().getTransfer()
                                .getTransferId());
                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                // 预约
                if (hisServiceConfigDao.isServiceEnable(
                        appointRecord.getOrganId(), ServiceType.TOHIS)
                        || appointRecord.getAppointRoad() == 5) {

                } else {
                    // 直接更新成转诊成功
                    String appointRecordId = appointRecord.getAppointRecordId().toString();
                    log.info("直接更新成转诊预约成功:appointRecordId=" + appointRecordId);
                    AppointmentResponse res = new AppointmentResponse();
                    res.setId(appointRecordId);
                    res.setAppointID("");
                    res.setClinicArea(appointRecord.getConfirmClinicAddr());
                    //2016-11-23 12:05:54 zhangx：修改BUG，转诊自动接收空指针异常,导致奖励未给
                    res.setOrderNum(appointRecord.getOrderNum() == null ? 0 : appointRecord.getOrderNum());
                    appointRecordDAO.updateAppointId4TransferNottohis(res);
                }
            }
            // 远程门诊转诊
            if (transferType_request == 3) {
                // 发送短信等
                List<AppointRecord> appointRecords = appointRecordDAO.findByTransferId(transferId);
                appointRecordDAO.doAfterAddAppointRecordForCloudClinic(appointRecords);
            }
        }
    }

    /***
     * 省中湖滨下沙 特殊处理(无排班默认将挂号门诊改成特需门诊 （预约记录和转诊记录 都改）)
     */
    @SuppressWarnings("deprecation")
    private void dealSZXS(Transfer transfer) {
        Integer confirmOrgan = transfer.getConfirmOrgan();
        Integer confirmDoctor = transfer.getConfirmDoctor();
        if (confirmOrgan == OrganConstant.Organ_SZ
                || confirmOrgan == OrganConstant.Organ_XS) {
            AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
            Date appointDate = null;
            try {
                DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");
                appointDate = df1.parse(df1.format(transfer
                        .getConfirmClinicTime()));
            } catch (ParseException e2) {
                throw new DAOException(e2);
            }

            // 获取时间，判断工作类型 workType（0："全天" 1："上午" 2："下午" 3："晚上"）
            int hour = appointDate.getHours();
            int workType;
            if ((hour < 12) && (hour >= 0)) {
                workType = 1;
            } else if ((hour >= 12) && (hour < 24)) {
                workType = 2;
            } else {
                workType = 3;
            }

            boolean isExist = appointSourceDAO.checkIsExistScheduling(confirmOrgan,
                    confirmDoctor, workType, appointDate);
            if (isExist) {

            } else {
                // 省中无排班： 副高以上按特需门诊100 来； 副高一下按特需门诊10来；

                if (confirmOrgan == OrganConstant.Organ_SZ
                        || confirmOrgan == OrganConstant.Organ_XS) {
                    // 省中
                    if (confirmOrgan == OrganConstant.Organ_SZ) {

                        transfer.setAppointDepartId(OrganConstant.TXDepartCode_SZ);
                        transfer.setAppointDepartName("特需门诊");

                    } else if (confirmOrgan == OrganConstant.Organ_XS) {

                        transfer.setAppointDepartId(OrganConstant.TXDepartCode_XS);
                        transfer.setAppointDepartName("特需门诊");
                    }
                    DoctorDAO doctordao = DAOFactory.getDAO(DoctorDAO.class);
                    Doctor doctor = doctordao.getByDoctorId(confirmDoctor);
                    String proTitle = doctor.getProTitle();
                    // 副高以上 无排班 特需转诊100
                    if ("1,2,5,6".contains(proTitle)
                            && !StringUtils.isEmpty(proTitle)) {
                        transfer.setClinicPrice(100d);
                    } else {
                        // 副高以下 都 无排班 特需转诊10 -1
                        transfer.setClinicPrice(10d);
                    }
                    transfer.setSourceLevel(3);// 特需
                }
            }
        }
    }

    /**
     * 保存门诊预约记录/保存云门诊预约记录
     *
     * @param tp
     * @throws ControllerException
     * @author ZX
     * @date 2015- 4- 23 下午2:45:22
     */
    public void saveAppointRecord(TransferAndPatient tp)
            throws ControllerException {

        AppointRecordDAO appointRecordDAO = DAOFactory
                .getDAO(AppointRecordDAO.class);
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);

        Transfer transfer = tp.getTransfer();
        int transferType_result = transfer.getTransferResultType();
        int transferType_request = transfer.getTransferType();

        // 门诊转诊
        if (transferType_result == 1 && transferType_request != 3) {
            AppointRecord appointRecord = getAppointRecordByTransfer(tp);
            appointRecordDAO.addAppointRecordNew(appointRecord);
        }
        // (远程门诊接收是按照门诊转诊来的transferType_result == 1 && transferType_request ==
        // 3)
        // 远程门诊转诊()
        if (transferType_request == 3) {
            // 出诊方(接收医生为出诊方，存放申请医生数据)
            AppointRecord visitsRecord = getAppointRecordByTransfer(tp);
            visitsRecord.setAppointStatus(9);//2017-04-17 13:51:29 zhangsl 云门诊付费
            visitsRecord.setClinicObject(2);
            visitsRecord.setOppType(1);
            visitsRecord.setOppOrgan(transfer.getRequestOrgan());
            Department dept = deptDao.getById(transfer.getRequestDepart());
            if (dept != null) {
                visitsRecord.setOppdepart(dept.getCode());
                visitsRecord.setOppdepartName(dept.getName());
            }
            visitsRecord.setOppdoctor(transfer.getRequestDoctor());
            visitsRecord.setEvaStatus(0);

            // 接诊方(申请医生为接诊方，存放接收医生数据)
            AppointRecord acceptsRecord = getAppointRecordByTransfer(tp);
            acceptsRecord.setAppointStatus(9);//2017-04-17 13:51:29 zhangsl 云门诊付费
            acceptsRecord.setClinicObject(1);
            acceptsRecord.setOppType(1);
            acceptsRecord.setOppOrgan(transfer.getConfirmOrgan());
            acceptsRecord.setOppdepart(transfer.getAppointDepartId());
            acceptsRecord
                    .setOppdepartName(acceptsRecord.getAppointDepartName());
            acceptsRecord.setOppdoctor(transfer.getConfirmDoctor());
            // 转诊接收请求中获取不到这些数值
            acceptsRecord.setAppointDepartId("");
            acceptsRecord.setAppointDepartName("");
            acceptsRecord.setDoctorId(transfer.getRequestDoctor());
            acceptsRecord.setOrganId(transfer.getRequestOrgan());
            acceptsRecord.setEvaStatus(0);
            acceptsRecord.setTriggerId(transfer.getTriggerId());

            List<AppointRecord> records = new ArrayList<AppointRecord>();
            records.add(visitsRecord);
            records.add(acceptsRecord);

            appointRecordDAO.addAppointRecordForCloudClinic(records);
        }

    }

    /**
     * 根据转诊数据组装预约记录
     *
     * @param tp
     * @return
     * @throws ControllerException
     * @author ZX 2015-8-28 下午5:23:35
     */
    @SuppressWarnings("deprecation")
    public AppointRecord getAppointRecordByTransfer(TransferAndPatient tp)
            throws ControllerException {
        Transfer transfer = tp.getTransfer();
        int confirmOrgan = transfer.getConfirmOrgan();
        int confirmDoctor = transfer.getConfirmDoctor();
        Double clinicPrice = transfer.getClinicPrice();
        int sourceLevel = transfer.getSourceLevel();
        String appointDepartCode = transfer.getAppointDepartId();
        Date confirmClinicTime = transfer.getConfirmClinicTime();

        // 增加预约记录
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);
        AppointDepart appointDepart = appointDepartDAO
                .getByOrganIDAndAppointDepartCode(confirmOrgan,
                        appointDepartCode);
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);

        if (appointDepart == null) {
            OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);

            if (!organDao.exist(confirmOrgan)) {
                throw new DAOException(609, "不存在该医院");
            }

            Organ o = organDao.getByOrganId(confirmOrgan);
            Department dept = deptDao.getByCodeAndOrgan(appointDepartCode,
                    confirmOrgan);

            String organName = o.getName();
            String deptName = dept.getName();

            throw new DAOException(609, "(" + organName + "|" + deptName
                    + ")尚未开通转诊业务");
        }

        String appointDepartName = appointDepart.getAppointDepartName();

        String appointName = DictionaryController.instance()
                .get("eh.base.dictionary.Doctor")
                .getText(transfer.getRequestDoctor());

        DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");

        Date appointDate = null;
        try {
            appointDate = df1.parse(df1.format(confirmClinicTime));
        } catch (ParseException e2) {
            throw new DAOException(e2);
        }

        // 获取时间，判断工作类型 workType（0："全天" 1："上午" 2："下午" 3："晚上"）
        int hour = appointDate.getHours();
        int workType;
        if ((hour < 12) && (hour >= 0)) {
            workType = 1;
        } else if ((hour >= 12) && (hour < 24)) {
            workType = 2;
        } else {
            workType = 3;
        }

        // 保存预约记录
        AppointRecord appointRecord = new AppointRecord();
        appointRecord.setMpiid(transfer.getMpiId());
        appointRecord.setPatientName(tp.getPatientName());
        appointRecord.setCertId(tp.getIdcard());
        appointRecord.setLinkTel(tp.getPatientMobile());
        appointRecord.setDeviceId(transfer.getDeviceId());
        appointRecord.setOrganAppointId("");
        appointRecord.setAppointSourceId(0);
        appointRecord.setOrganId(confirmOrgan);
        appointRecord.setAppointDepartId(appointDepartCode);
        appointRecord.setAppointDepartName(appointDepartName);
        appointRecord.setDoctorId(confirmDoctor);
        appointRecord.setWorkDate(appointDate);
        appointRecord.setWorkType(workType);
        appointRecord.setSourceType(3);
        appointRecord.setStartTime(confirmClinicTime);
        appointRecord.setEndTime(confirmClinicTime);
        appointRecord.setOrderNum(0);
        appointRecord.setAppointRoad(6);
        appointRecord.setAppointStatus(0);
        appointRecord.setAppointDate(new Date());
        // 医生申请的加号转诊
        if (StringUtils.isEmpty(transfer.getRequestMpi())) {
            appointRecord.setAppointUser(transfer.getRequestDoctor() + "");
            appointRecord.setAppointName(appointName);
            appointRecord.setAppointOragn(transfer.getRequestOrgan() + "");
        } else {
            // 患者申请的转诊(特需预约)
            Patient p = patDao.get(transfer.getRequestMpi());
            if (p == null) {
                throw new DAOException(609, "不存在该申请人");
            }
            appointRecord.setAppointUser(transfer.getRequestMpi());
            appointRecord.setAppointName(p.getPatientName());
            appointRecord.setAppointOragn("");
        }
        appointRecord.setTriggerId(transfer.getTriggerId());
        appointRecord.setClinicPrice(clinicPrice);
        appointRecord.setTransferId(transfer.getTransferId());
        appointRecord.setSourceLevel(sourceLevel);
        appointRecord.setConfirmClinicAddr(transfer.getConfirmClinicAddr());// 转诊地址

        return appointRecord;
    }

    /**
     * 保存云门诊预约记录
     *
     * @param tp
     * @author ZX
     * @date 2015-8-21 下午5:04:44
     */
    public void saveRemoteAppointRecord(TransferAndPatient tp) {

    }

    /**
     * 医院确认中，住院接收可发起重试
     *
     * @param appointInHospId 预约记录id
     */
    @RpcService
    public void reTryAppointInHosp(Integer appointInHospId) {
        // 此处调用sendToHis
        AppointInhosp appointInhosp = DAOFactory.getDAO(AppointInhospDAO.class)
                .get(appointInHospId);
        if (appointInhosp != null && appointInhosp.getStatus() == 9) {
            sendToHis(appointInhosp);
        }
    }

    /**
     * 往his发送住院转诊信息
     *
     * @param app
     */
    public void sendToHis(AppointInhosp app) {
        AppointInHosRequest req = new AppointInHosRequest();
        req.setId(app.getAppointInHospId().toString());
        req.setPatientName(app.getPatientName());// 姓名
        req.setMobile(app.getMobile());
        req.setOrganID(app.getOrganId() + "");
        req.setPatientSex(app.getPatientSex());
        req.setPatientType(app.getPatientType());
        req.setBirthday(app.getBirthday());
        req.setCredentialsType("身份证");
        req.setCertID(app.getIdcard());
        DepartmentDAO departDao = DAOFactory.getDAO(DepartmentDAO.class);
        String clinicDepart = departDao.get(app.getClinicDepart()).getCode();
        req.setClinicDepart(clinicDepart);// //要转成机构码
        String inDepart = departDao.get(app.getInHospDepart()).getCode();
        req.setInDepart(inDepart);// 要转成机构码
        // 获取医生执业机构信息（平台-->his）
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        // 机构医生代码
        List<String> jobNumbers = employmentDAO
                .findJobNumberByDoctorIdAndOrganId(app.getRequestDoctor(),
                        app.getOrganId());
        String jobNumber = jobNumbers.get(0);
        req.setDoctorID(jobNumber);// 要转
        req.setApplyDate(app.getRequestDate());
        // 机构医生代码
        List<String> attendinds = employmentDAO
                .findJobNumberByDoctorIdAndOrganId(app.getAttending(),
                        app.getOrganId());
        String attendind = attendinds.get(0);
        req.setAttending(attendind);// 要转
        req.setWorkDate(app.getRequestInDate());// 申请入院时间
        req.setClinicDiagnoseis(app.getClinicDiagnoseis());
        req.setNearbyReceive(app.getNearbyReceive());
        req.setAdmissionExam(app.getAdmissionExam());
        req.setAdmissionExamCode(app.getAdmissionExamItem());// ,号转成/
        req.setAdmissionExamItem("");// 从项目里查询项目名称
        req.setSpecialExam(app.getSpecialExam());
        req.setSpecialExamCode(app.getSpecialExamItem());// ,号转成/
        req.setSpecialExamItem("");// 从项目里查询项目名称
        req.setIsOperation(app.getIsOperation());
        req.setOperationDate(app.getOperateDate());
        req.setPrepayment(app.getPrepayment());
        req.setAppointRoad(app.getAppointRoad());

        // 调用前置机转诊服务
        InHosAppointExecutor executor = new InHosAppointExecutor(req);
        executor.execute();

    }

    /**
     * 转诊确认服务(住院转诊确认)
     *
     * @param transfer
     * @throws DAOException 2015-04-29 发送短信，由原来的更新成功发送改为his返回之后，更新我们数据库成功之后，才发送
     * @author ZX
     * @date 2015-4-20 下午5:25:29
     * @date 2016-2-29 luf 增加 若不是"处理中"的状态，不予转诊确认。
     */
    @RpcService
    public void confirmTransferWithInHospital(final Transfer transfer,
                                              final AppointInhosp appointInhosp) throws DAOException {
        log.info("转诊确认前端住院数据:" + JSONUtils.toString(transfer));
        if (transfer == null || transfer.getTransferId() == null) {
//            log.error("transfer/transferId is required!");
            throw new DAOException(DAOException.VALUE_NEEDED, "transfer/transferId is required!");
        }
        final int transferId = transfer.getTransferId();
        Transfer tr = this.get(transferId);
        if (tr.getTransferStatus() != 1) {
//            log.error("转诊确认服务(住院转诊确认)confirmTransferWithInHospital==>该转诊单未开始或者已处理");
            throw new DAOException(601, "该转诊单未开始或者已处理");
        }
        final int agreeDoctor = transfer.getAgreeDoctor();
        final int confirmOrgan = transfer.getConfirmOrgan();
        final int confirmDepart = transfer.getConfirmDepart();
        final int confirmDoctor = transfer.getConfirmDoctor();
        final Date confirmClinicTime = transfer.getConfirmClinicTime();
        final String returnMess = transfer.getReturnMess();
        final String diagianName = transfer.getDiagianName();

        // 判断时间是否符合要求
        Date now = new Date();
        if (confirmClinicTime.before(now)) {
            throw new DAOException(601, "申请入院时间必须在当前时间之后");
        }

        if (appointInhosp.getIsOperation() == 1
                && appointInhosp.getOperationDate().before(confirmClinicTime)) {
            throw new DAOException(601, "手术时间必须在申请入院时间之后");
        }

        if (appointInhosp.getIsOperation() == 0) {
            appointInhosp.setOperationDate(null);
        }

        // 是否做入院检查
        if (appointInhosp.getAdmissionExam() == null) {
            if (StringUtils.isEmpty(appointInhosp.getAdmissionExamItem())) {
                appointInhosp.setAdmissionExam(0);
            } else {
                appointInhosp.setAdmissionExam(1);
            }
        }
        // 是否做特殊检查
        if (appointInhosp.getSpecialExam() == null) {
            if (StringUtils.isEmpty(appointInhosp.getSpecialExamItem())) {
                appointInhosp.setSpecialExam(0);
            } else {
                appointInhosp.setSpecialExam(1);
            }
        }
        // 检验住院预约数据
        isValidAppointInhospData(appointInhosp);

        AbstractHibernateStatelessResultAction<TransferAndPatient> action = new AbstractHibernateStatelessResultAction<TransferAndPatient>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 更新transfer
                String hql = "update Transfer set transferStatus = 8, transferResult = 1, agreeDoctor=:agreeDoctor, agreeTime=:agreeTime, "
                        + "transferResultType=:transferResultType, confirmOrgan=:confirmOrgan, confirmDepart=:confirmDepart, confirmDoctor=:confirmDoctor, "
                        + "confirmClinicTime=:confirmClinicTime, returnMess=:returnMess,diagianName=:diagianName "
                        + "where transferId=:transferId";
                Query query = ss.createQuery(hql);
                query.setInteger("agreeDoctor", agreeDoctor);
                query.setTimestamp("agreeTime", new Date());
                query.setInteger("transferResultType", 2);
                query.setInteger("confirmOrgan", confirmOrgan);
                query.setInteger("confirmDepart", confirmDepart);
                query.setInteger("confirmDoctor", confirmDoctor);
                query.setTimestamp("confirmClinicTime", confirmClinicTime);
                query.setString("returnMess", returnMess);
                query.setString("diagianName", diagianName);
                query.setInteger("transferId", transferId);
                query.executeUpdate();

                TransferAndPatient tp = getTransferByID(transferId);

                // 保存预约记录
                saveAppointInhosp(tp, appointInhosp);

                setResult(tp);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        TransferAndPatient tp = action.getResult();

        //纳里医生App端首页个性化 住院转诊完成
        asynDoBussService.fireEvent(new BussFinishEvent(transferId, BussTypeConstant.TRANSFER));

        if (tp != null) {
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(tp.getTransfer().getAgreeDoctor());
            int organid = appointInhosp.getOrganId().intValue();
            HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
            boolean isEnable = hisServiceConfigDAO.isServiceEnable(organid, ServiceType.INHOSPTOHIS);
            if (isEnable) {
                sendToHis(appointInhosp);
            } else {
                //不发送his直接住院转诊成功
                AppointInhospDAO appointInhospDAO = DAOFactory.getDAO(AppointInhospDAO.class);
                AppointInHosResponse appres = new AppointInHosResponse();
                appres.setId(appointInhosp.getAppointInHospId() + "");
                appres.setOrganAppointInhospID("NL_SUCCESS");
                appointInhospDAO.updateOrganAppointInHosId(appres);
            }
        }
    }

    /**
     * 保存住院预约记录
     *
     * @author ZX
     * @date 2015-4-23 下午3:43:01
     */
    public void saveAppointInhosp(TransferAndPatient tp,
                                  AppointInhosp appointInhosp) {
        Transfer transfer = tp.getTransfer();

        appointInhosp.setRequestDoctor(transfer.getAgreeDoctor());
        appointInhosp.setRequestDate(new Date());
        appointInhosp.setOrganId(transfer.getConfirmOrgan());
        appointInhosp.setInHospDepart(transfer.getConfirmDepart());
        appointInhosp.setAttending(transfer.getConfirmDoctor());
        appointInhosp.setRequestInDate(transfer.getConfirmClinicTime());
        appointInhosp.setTransferId(transfer.getTransferId());
        appointInhosp.setClinicDiagnoseis(transfer.getDiagianName());
        appointInhosp.setMpiid(transfer.getMpiId());
        appointInhosp.setPatientName(tp.getPatientName());
        appointInhosp.setPatientSex(tp.getPatientSex());
        appointInhosp.setBirthday(tp.getBirthday());
        appointInhosp.setPatientType(tp.getPatientType());
        appointInhosp.setIdcard(tp.getIdcard());
        appointInhosp.setMobile(tp.getPatientMobile());
        appointInhosp.setStatus(4);// 医院处理中
        appointInhosp.setAppointRoad(2);

        AppointInhospDAO appointInhospDAO = DAOFactory
                .getDAO(AppointInhospDAO.class);
        appointInhospDAO.saveAppointInhosp(appointInhosp);
    }

    /**
     * 转诊确认时，给申请医生，接收医生增加收入
     *
     * @param transferId 转诊单Id
     * @author ZX
     * @date 2015-4-27 上午9:58:52
     */
    public void addTransferIncome(int transferId) {
        // 获取转诊单信息
        Transfer trans = getById(transferId);

        // 申请医生id
        Integer requestDocId = trans.getRequestDoctor();

        // 审核医生id
        int agreeDocId = trans.getAgreeDoctor();

        DoctorAccountDAO doctorAccountDAO = DAOFactory
                .getDAO(DoctorAccountDAO.class);

        if (requestDocId != null) {
            // 增加帐户收入（申请医生id，1，转诊单Id，0）
            log.info("转诊预约成功,给申请医生增加账户金额,requestDocId:" + requestDocId);
            doctorAccountDAO.addDoctorIncome(requestDocId, 1, transferId, 0);

            // 增加帐户收入（审核医生id，2，转诊单Id，0）
            log.info("转诊预约成功,给审核医生增加账户金额,agreeDocId:" + agreeDocId);
            doctorAccountDAO.addDoctorIncome(agreeDocId, 2, transferId, 0);
        }

        if (requestDocId == null && trans.getPayflag() != null
                && trans.getPayflag() == 1 && trans.getTransferCost() != null
                && trans.getTransferCost() > 0) {
            // 增加账户收入(患者申请的特需预约)
            log.info("转诊预约成功,给审核医生增加账户金额,agreeDocId:" + agreeDocId);
            doctorAccountDAO.addDoctorRevenue(agreeDocId, 22, transferId,
                    trans.getTransferPrice());
        }

    }

    /**
     * 检验住院预约数据
     *
     * @param appointInhosp
     * @author ZX
     * @date 2015-4-23 下午5:10:19
     */
    public void isValidAppointInhospData(AppointInhosp appointInhosp) {
        // 门诊就诊科室
        if (appointInhosp.getClinicDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "clinicDepart is required");
        }

        // 是否就近收治
        if (appointInhosp.getNearbyReceive() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "nearbyReceive is required");
        }

        // 是否手术
        if (appointInhosp.getIsOperation() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "isOperation is required");
        }
    }

//    /**
//     * 向申请医生和患者发送住院转诊确认短信
//     *
//     * @author ZX
//     */
//    public void sendInHospitalMsg(TransferAndPatient tp) {
//        try {
//            Transfer t = tp.getTransfer();
//
//            // 短信模板代码
//            String msgDemo = IN_HOSPITAL_MSG_NO;
//
//            // 申请医生电话
//            String docTel = tp.getRequestDoctorMobile();
//
//            // 患者电话
//            String patientTel = tp.getPatientMobile();
//
//            // 患者名称
//            String patientName = tp.getPatientName();
//
//            // 确认医生姓名
//            String confirmDoctorName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Doctor")
//                    .getText(t.getConfirmDoctor());
//
//            // 确认科室名称
//            String confirmDepartName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Depart")
//                    .getText(t.getConfirmDepart());
//
//            // 医院名称
//            String confirmOrganName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Organ")
//                    .getText(t.getConfirmOrgan());
//
//            SendTemplateSMS menger = new SendTemplateSMS();
//
//            String doctorInfo = confirmDoctorName + "(" + confirmDepartName
//                    + "|" + confirmOrganName + ")";
//            String[] parameter = new String[]{patientName, doctorInfo, "24"};// [patientName,confirmDoctor(confirmDepart|confirmOrgan),24,leaveMess];
//
//            // 给申请医生发送信息
//            menger.SendSMS(docTel, msgDemo, parameter);
//
//            // 给患者发送信息
//            menger.SendSMS(patientTel, msgDemo, parameter);
//
//            String msg = "您有一条转诊申请已被处理";
//            String title = "转诊接收提醒";
//            String detailMsg = "您有一条住院转诊申请已被接收,接收医生:" + doctorInfo;
//
//            // 给申请医生，患者发送推送消息
//            MsgPush(tp, msg, title, detailMsg);
//        } catch (Exception e) {
//            log.error(e.getMessage());
//        }
//    }

//    /**
//     * 向申请医生和患者发送门诊转诊确认短信
//     *
//     * @author ZX
//     */
//    public void sendOutPatientMsg(TransferAndPatient tp) {
//        try {
//            Transfer t = tp.getTransfer();
//
//            // 短信模板代码
//            String msgDemo = OUT_PATIENT_MSG_NO;
//
//            // 申请医生电话
//            String docTel = tp.getRequestDoctorMobile();
//
//            // 患者电话
//            String patientTel = tp.getPatientMobile();
//
//            // 患者名称
//            String patientName = tp.getPatientName();
//
//            // 确认医生姓名
//            String confirmDoctorName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Doctor")
//                    .getText(t.getConfirmDoctor());
//
//            // 确认科室名称
//            String confirmDepartName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Depart")
//                    .getText(t.getConfirmDepart());
//
//            // 医院名称
//            String confirmOrganName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Organ")
//                    .getText(t.getConfirmOrgan());
//
//            // 申请医生留言
//            String returnMess = t.getReturnMess();
//
//            // 就诊时间
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
//            String confirmClinicTime = sdf.format(t.getConfirmClinicTime());
//
//            // 就诊地点
//            String confirmClinicAddr = t.getConfirmClinicAddr();
//
//            SendTemplateSMS menger = new SendTemplateSMS();
//
//            String doctorInfo = confirmDoctorName + "(" + confirmDepartName
//                    + "|" + confirmOrganName + ")";
//            String[] parameter = new String[]{patientName, doctorInfo,
//                    confirmClinicTime, confirmClinicAddr, returnMess};// [patientName,confirmDoctor(confirmDepart|confirmOrgan),24,leaveMess];
//
//            // 给申请医生发送信息
//            log.info("给申请医生发送转诊接收成功短信");
//            menger.SendSMS(docTel, msgDemo, parameter);
//
//            // 给患者发送信息
//            log.info("给患者发送转诊接收成功短信");
//            menger.SendSMS(patientTel, msgDemo, parameter);
//
//            String msg = "您有一条转诊申请已被处理";
//            String title = "转诊接收提醒";
//            String detailMsg = "您有一条门诊转诊申请已被接收,接收医生:" + doctorInfo;
//
//            // 给申请医生，患者发送推送消息
//            MsgPush(tp, msg, title, detailMsg);
//        } catch (Exception e) {
//            log.error("转诊接收成功短信发送失败" + e.getMessage());
//        }
//    }

    /**
     * 服务名:转诊审核拒绝服务
     *
     * @param transferId
     * @param agreeDoctor
     * @param agreeTime
     * @param refuseCause
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    public void refuseTransfer(final Integer transferId,
                               final Integer agreeDoctor, final Date agreeTime,
                               final String refuseCause) throws DAOException {
        log.info("转诊拒绝前端转诊单数据--transferId=" + transferId + "; agreeDoctor="
                + agreeDoctor + "; agreeTime=" + agreeTime + " ;refuseCause="
                + refuseCause);
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update Transfer set transferStatus = 2, transferResult = 2, agreeDoctor=:agreeDoctor, agreeTime=:agreeTime, "
                        + "refuseCause=:refuseCause, refuseFlag=1 where transferId=:transferId";
                Query query = ss.createQuery(hql);
                query.setInteger("agreeDoctor", agreeDoctor);

                query.setTimestamp("agreeTime", new Date());
                query.setString("refuseCause", refuseCause);
                query.setInteger("transferId", transferId);
                query.executeUpdate();

                setResult(true);
            }
        };
        try {

            HibernateSessionTemplate.instance().execute(action);

        } catch (DAOException e) {
            log.error(e.getMessage());
            throw new DAOException(609, "转诊拒绝失败！");
        }

        if (action.getResult()) {
            Transfer transfer = this.getById(transferId);

            //App端首页显示 转诊业务取消/拒绝数据
            asynDoBussService.fireEvent(new BussCancelEvent(transfer.getTransferId(),BussTypeConstant.TRANSFER));


            SmsPushService service=AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);

            Integer organ=Integer.valueOf(0);
            String busType="DocTransferRefuse";
            if(!StringUtils.isEmpty(transfer.getRequestMpi())){
                //患者申请的个性化门诊
                busType="PatTransferRefuse";
                organ=transfer.getTargetOrgan()==null?Integer.valueOf(0):transfer.getTargetOrgan();
            }else{
                busType="DocTransferRefuse";
                organ=transfer.getRequestOrgan()==null?Integer.valueOf(0):transfer.getRequestOrgan();
            }
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(transfer.getTransferId());
            smsInfo.setBusType(busType);
            smsInfo.setSmsType(busType);
            smsInfo.setOrganId(organ);
            smsInfo.setClientId(transfer.getDeviceId());
            smsInfo.setExtendValue(refuseCause);
            service.pushMsgData2OnsExtendValue(smsInfo);

            /*service.pushMsgData2Ons(transfer.getTransferId(), organ,
                    busType, busType, transfer.getDeviceId());*/

            if (!StringUtils.isEmpty(transfer.getRequestMpi())) {// 患者申请拒绝 需要返回金额给患者
                WxRefundExecutor executor = new WxRefundExecutor(
                        transfer.getTransferId(), "transfer");
                executor.execute();
            }
        }

    }

    /**
     * 服务名:转诊拒绝理由字典查询服务
     *
     * @param
     * @return
     * @throws ControllerException
     * @author yxq
     */
    @RpcService
    public Map<Integer, String> refuseTransferDic() throws ControllerException {
        ctd.dictionary.Dictionary dic = DictionaryController.instance().get(
                "eh.bus.dictionary.RefuseCause");
        Map<Integer, String> value = new HashMap<Integer, String>();
        int key = 0;
        while (dic.keyExist(String.valueOf(key))) {
            value.put(key, dic.getText(key + ""));
            key++;
        }
        return value;
    }

    /**
     * 转诊取消
     *
     * @param transfer
     * @throws DAOException
     * @author ZX
     * @date 2015-4-21 下午2:57:02
     */
    @RpcService
    public void cancelTransferNew(final Transfer transfer) throws DAOException {
        log.info("转诊取消前端传入数据" + JSONUtils.toString(transfer));

        Transfer t = this.getById(transfer.getTransferId());
        if (t == null) {
            throw new DAOException(609, "不存在该转诊单");
        }
        if (t.getTransferStatus() >= 1) {
            throw new DAOException(609, "该转诊单不是待处理状态，无法取消");
        }

        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update Transfer set transferStatus = 9, cancelOrgan=:cancelOrgan, cancelDepart=:cancelDepart, "
                        + "cancelDoctor=:cancelDoctor, cancelTime=:cancelTime, cancelCause=:cancelCause where transferId=:transferId and transferStatus < 1";
                Query query = ss.createQuery(hql);
                query.setInteger("cancelOrgan",
                        transfer.getCancelOrgan());
                query.setInteger("cancelDepart",
                        transfer.getCancelDepart());
                query.setInteger("cancelDoctor",
                        transfer.getCancelDoctor());
                query.setTimestamp("cancelTime", new Date());
                query.setString("cancelCause",
                        transfer.getCancelCause());
                query.setInteger("transferId", transfer.getTransferId());
                setResult(query.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        Integer count = action.getResult();
        if (count != null && count > 0) {
            if (t.getInsuRecord() == 1) {// 已备案的需取消
                TransferCancelExecutor exe = new TransferCancelExecutor(t);
                exe.execute();
            }
            sendDocTransferCancelMsg(t);
        }
    }

    /**
     * 根据病人mpiId查询申请中或者审核开始的转诊记录
     *
     * @param mpiId 病人mpiId
     * @return
     * @date 2016-3-2 luf 特需预约不限制，只查医生端申请的转诊
     * @author ZX
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where payflag=1 and transferStatus<2 and b.mpiId=a.mpiId and b.mpiId=:mpiId and a.requestDoctor is not null")
    public abstract TransferAndPatient getApplyingTransferByMpiId(
            @DAOParam("mpiId") String mpiId);

    /**
     * 转诊申请前病人即医生状态判断，是否可以进行转诊
     *
     * @param mpiId
     * @param doctorId
     */
    @RpcService
    public void beforeCreateTransfer(String mpiId, Integer doctorId) {
        TransferAndPatient tp = getApplyingTransferByMpiId(mpiId);
        if (tp != null) {
            throw new DAOException(602, "病人" + tp.getPatientName()
                    + "有一条未处理的转诊申请单，不能再进行转诊");
        }
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet cs = csDao.getById(doctorId);
        if (cs == null) {
            throw new DAOException(602, "该医生没有开通转诊权限，无法进行转诊");
        }
        Integer tranStatus = cs.getTransferStatus();
        if (tranStatus != 1) {
            throw new DAOException(602, "该医生没有开通转诊权限，无法进行转诊");
        }
    }

    /**
     * 服务名:转诊单执行接收服务
     *
     * @param transferId
     * @param exeTime
     * @param transferStatus
     * @return
     * @throws
     * @author yxq
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set exeTime=:exeTime, transferStatus=:transferStatus where transferId=:transferId")
    public abstract void updateExeTransfer(
            @DAOParam("transferId") int transferId,
            @DAOParam("exeTime") Date exeTime,
            @DAOParam("transferStatus") int transferStatus);

    /**
     * his预约成功的时候，更新状态和就诊地点
     *
     * @param transferId
     * @author ZX
     * @date 2015-4-29 下午4:20:05
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set  transferStatus=2 ,confirmClinicAddr=:confirmClinicAddr where transferId=:transferId")
    public abstract void updateTransferFromHosp(
            @DAOParam("transferId") int transferId,
            @DAOParam("confirmClinicAddr") String confirmClinicAddr);
    
    /**
     * his预约首次失败再次自动预约成功的时候，更新状态和就诊地点
     * @param transferId
     * @param confirmClinicAddr
     */
    @DAOMethod(sql = "update Transfer set  transferStatus=2,transferResult=1, confirmClinicAddr=:confirmClinicAddr where transferId=:transferId")
    public abstract void updateTransferAndResultFromHosp(
            @DAOParam("transferId") int transferId,
            @DAOParam("confirmClinicAddr") String confirmClinicAddr);

    /**
     * his处理失败，更新转诊信息为医院处理失败，拒绝转诊
     *
     * @param transferId
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set  transferStatus=7,transferResult=2,refuseCause=:refuseCause,refuseFlag=1 where transferId=:transferId")
    public abstract void updateTransferFailed(
            @DAOParam("transferId") int transferId,
            @DAOParam("refuseCause") String refuseCause);

//    /**
//     * 给患者和申请医生发推送消息消息
//     *
//     * @param tp
//     */
//    public void MsgPush(TransferAndPatient tp, String msg, String title,
//                        String detailMsg) {
//        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
//        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
//        SessionDetailService detailService = new SessionDetailService();
//
//        Transfer t = tp.getTransfer();
//
//        Integer transferId = t.getTransferId();
//
//        Doctor targetDoc = docDao.get(t.getTargetDoctor());
//        Boolean teams = targetDoc.getTeams() == null ? false : targetDoc.getTeams();
//
//        Boolean patientRequest = false;
//
//        // 申请医生电话
//        String docTel = tp.getRequestDoctorMobile();
//
//        // 申请医生新增系统信息
//        detailService.addSysTextMsgTransferToReqDoc(transferId, docTel, title, detailMsg, teams, true);
////        addMsgDetail(transferId, 1, docTel, "text",
////                title, detailMsg, "", true);
//
//        //发送推送消息给申请医生
//        HashMap<String, Object> msgCustomToTarget = CustomContentService.getTransferCustomContentToRequest(transferId, patientRequest, teams);
//        MsgPushService.pushMsgToDoctor(docTel, msg, msgCustomToTarget);
//
//
//        // 患者用户名
//        Patient patient = patDao.get(t.getMpiId());
//        String patLoginId = patient.getLoginId();
//        if (!StringUtils.isEmpty(patLoginId)) {
//            // 患者新增系统信息
//            detailService.addSysTextMsgBussToPat(transferId, 1, patLoginId, title, detailMsg, teams, true);
////            addMsgDetail(transferId, 2, patLoginId, "text",
////                    title, detailMsg, "", true);
//
//            //发送推送消息给申请医生
//            HashMap<String, Object> msgCustom = CustomContentService.getTransferCustomContentToTarget(transferId, patientRequest, teams);
//            MsgPushService.pushMsgToPatient(patLoginId, msg, msgCustom);
//
//        }
//
//    }

    /**
     * 新增系统提醒消息
     *
     * @param tel    接收消息的对象电话
     * @param title  接收消息的标题
     * @param msg    接收消息的内容
     * @param hasBtn 是否有按钮可以跳转到详情页(true:有;false:没有)
     * @author ZX
     * @date 2015-4-10 下午3:36:04
     */
//    public void addMsgDetail(int transferid, int memberType, String tel,
//                             String msgType, String title, String msg, String url, boolean hasBtn) {
//        // 构建系统消息
//        SessionMessage sessionMsg = new SessionMessage();
//        sessionMsg.setToUserId(tel);
//        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
//        sessionMsg.setMsgType(msgType);
//
//        List<Article> list = new ArrayList<Article>();
//
//        Article art = new Article();
//        art.setContent(msg);
//        art.setTitle(title);
//        art.setUrl(url);
//        if (hasBtn) {
//            art.setBussType(1);
//            art.setBussId(transferid);
//        }
//        list.add(art);
//
//        sessionMsg.setArticles(list);
//
//        // 新增系统消息
//        SessionDetailDAO sessionDetailDAO = DAOFactory
//                .getDAO(SessionDetailDAO.class);
//        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
//                memberType, "eh", tel);
//    }

    /**
     * 获取当天总转诊数
     *
     * @param requestTime
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM Transfer WHERE DATE(requestTime)=DATE(:requestTime)")
    public abstract Long getAllNowTranNum(
            @DAOParam("requestTime") Date requestTime);

    /**
     * 获取当天人均转诊数
     *
     * @param requestTime
     * @return
     * @author LF
     */
    @RpcService
    public Double getAverageNum(Date requestTime) {
        Long tranNum = getAllNowTranNum(requestTime);
        Long doctorNum = DAOFactory.getDAO(DoctorDAO.class).getAllDoctorNum();
        if (doctorNum <= 0) {
            return (double) 0;
        }
        return tranNum / (double) doctorNum;
    }

    /**
     * 转诊统计查询
     *
     * @return
     * @author ZX
     * @date 2015-5-8 上午11:49:44
     */
    @RpcService
    public List<TransferAndPatient> findTransferWithStatic(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start) {

        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<List<TransferAndPatient>> action = new AbstractHibernateStatelessResultAction<List<TransferAndPatient>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) from Transfer a,Patient b where a.mpiId=b.mpiId and DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime) ");

                // 添加申请机构条件
                if (tran.getRequestOrgan() != null) {
                    hql.append(" and a.requestOrgan=" + tran.getRequestOrgan());
                }

                // 添加目标机构条件
                if (tran.getTargetOrgan() != null) {
                    hql.append(" and a.targetOrgan=" + tran.getTargetOrgan());
                }

                // 添加申请医生条件
                if (tran.getRequestDoctor() != null) {
                    hql.append(" and a.requestDoctor="
                            + tran.getRequestDoctor());
                }

                // 添加目标医生条件
                if (tran.getTargetDoctor() != null) {
                    hql.append(" and a.targetDoctor=" + tran.getTargetDoctor());
                }

                // 添加转诊单结果
                if (tran.getTransferResult() != null) {
                    hql.append(" and a.transferResult="
                            + tran.getTransferResult());
                }

                // 添加转诊单状态
                if (tran.getTransferStatus() != null) {
                    hql.append(" and a.transferStatus="
                            + tran.getTransferStatus());
                }

                hql.append(" order by a.requestTime desc");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<TransferAndPatient> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                for (TransferAndPatient transferAndPatient : tfList) {
                    // 申请医生电话
                    int requestDocId = transferAndPatient.getTransfer()
                            .getRequestDoctor();
                    Doctor reqDoctor = doctorDAO.getByDoctorId(requestDocId);
                    if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                        transferAndPatient.setRequestDoctorMobile(reqDoctor
                                .getMobile());
                    }

                    // 目标医生电话
                    int targerDocId = transferAndPatient.getTransfer()
                            .getTargetDoctor();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        transferAndPatient.setTargetDoctorMobile(targetDoctor
                                .getMobile());
                    }

                }
                setResult(tfList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<TransferAndPatient>) action.getResult();
    }

    /**
     * 转诊统计查询记录数
     *
     * @param startTime
     * @param endTime
     * @param tran
     * @return
     * @author ZX
     * @date 2015-5-12 下午4:16:54
     */
    @RpcService
    public long getNumWithStatic(final Date startTime, final Date endTime,
                                 final Transfer tran) {

        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from Transfer a where  DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime)");

                // 添加申请机构条件
                if (tran.getRequestOrgan() != null) {
                    hql.append(" and a.requestOrgan=" + tran.getRequestOrgan());
                }

                // 添加目标机构条件
                if (tran.getTargetOrgan() != null) {
                    hql.append(" and a.targetOrgan=" + tran.getTargetOrgan());
                }

                // 添加申请医生条件
                if (tran.getRequestDoctor() != null) {
                    hql.append(" and a.requestDoctor="
                            + tran.getRequestDoctor());
                }

                // 添加目标医生条件
                if (tran.getTargetDoctor() != null) {
                    hql.append(" and a.targetDoctor=" + tran.getTargetDoctor());
                }

                // 添加转诊单结果
                if (tran.getTransferResult() != null) {
                    hql.append(" and a.transferResult="
                            + tran.getTransferResult());
                }

                // 添加转诊单状态
                if (tran.getTransferStatus() != null) {
                    hql.append(" and a.transferStatus="
                            + tran.getTransferStatus());
                }

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 申请方转诊数统计
     *
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-5-25 上午11:28:10
     */
    @RpcService
    public Long getRequestNumFromTo(final String manageUnit,
                                    final Date startDate, final Date endDate,
                                    final String... transferStatus) {
        if (startDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from Transfer a ,Organ o where a.requestOrgan = o.organId and o.manageUnit like :manageUnit and  DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime)");

                // 添加转诊单状态
                if (transferStatus.length > 0) {
                    hql.append(" and (");

                    for (String string : transferStatus) {
                        hql.append(" a.transferStatus=" + string + " or ");
                    }

                    hql.delete(hql.length() - 4, hql.length());
                    hql.append(")");
                }

                Query query = ss.createQuery(hql.toString());
                query.setString("manageUnit", manageUnit);
                query.setDate("startTime", startDate);
                query.setDate("endTime", endDate);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 申请方昨日转诊总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:41:50
     */
    @RpcService
    public Long getRequestNumForYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getRequestNumFromTo(manageUnit + "%", date, date, transferStatus);
    }

    /**
     * 申请方今日转诊总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:41:50
     */
    @RpcService
    public Long getRequestNumForToday(String manageUnit) {
        Date date = Context.instance().get("date.getToday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getRequestNumFromTo(manageUnit + "%", date, date, transferStatus);
    }

    /**
     * 申请方总转诊数
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:44:59
     */
    @RpcService
    public Long getRequestNum(String manageUnit) {
        Date startDate = new StringToDate().convert("2014-05-06");
        Date endDate = Context.instance().get("date.getToday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getRequestNumFromTo(manageUnit + "%", startDate, endDate,
                transferStatus);
    }

    /**
     * 申请方一段时间内总转诊数
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-8-5 下午4:13:32
     */
    @RpcService
    public Long getRequestNumForTime(String manageUnit, Date startDate,
                                     Date endDate) {
        String[] transferStatus = {"2", "3", "4", "5"};
        return getRequestNumFromTo(manageUnit + "%", startDate, endDate,
                transferStatus);
    }

    /**
     * 目标方转诊数统计
     *
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-5-25 上午11:28:10
     */
    @RpcService
    public Long getTargetNumFromTo(final String manageUnit,
                                   final Date startDate, final Date endDate,
                                   final String... transferStatus) {
        if (startDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from Transfer a ,Organ o where a.targetOrgan = o.organId and o.manageUnit like :manageUnit and  DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime)");

                // 添加转诊单状态
                if (transferStatus.length > 0) {
                    hql.append(" and (");

                    for (String string : transferStatus) {
                        hql.append(" a.transferStatus=" + string + " or ");
                    }

                    hql.delete(hql.length() - 4, hql.length());
                    hql.append(")");
                }

                Query query = ss.createQuery(hql.toString());
                query.setString("manageUnit", manageUnit);
                query.setDate("startTime", startDate);
                query.setDate("endTime", endDate);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 目标方昨日转诊总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:41:50
     */
    @RpcService
    public Long getTargetNumForYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getTargetNumFromTo(manageUnit + "%", date, date, transferStatus);
    }

    /**
     * 目标方今日转诊总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:41:50
     */
    @RpcService
    public Long getTargetNumForToday(String manageUnit) {
        Date date = Context.instance().get("date.getToday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getTargetNumFromTo(manageUnit + "%", date, date, transferStatus);
    }

    /**
     * 目标方总转诊数
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-25 下午3:44:59
     */
    @RpcService
    public Long getTargetNum(String manageUnit) {
        Date startDate = new StringToDate().convert("2014-05-06");
        Date endDate = Context.instance().get("date.getToday", Date.class);
        String[] transferStatus = {"2", "3", "4", "5"};
        return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
                transferStatus);
    }

    /**
     * 目标方一段时间内总转诊数
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-8-5 下午4:18:26
     */
    @RpcService
    public Long getTargetNumForTime(String manageUnit, Date startDate,
                                    Date endDate) {
        String[] transferStatus = {"2", "3", "4", "5"};
        return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
                transferStatus);
    }


    /**
     * 转诊住院接收发送给申请医生和病人 患者：</br>
     * 【纳里健康】小纳提醒:{1}，{2}医生申请的住院转诊已确认接收，小纳已将转诊要求通知医院住院部，住院部也将
     * {3}小时内与您联系，确定入院时间及相关事宜。如有疑问，请通过{4}联系小纳。 申请医生：</br>
     * 【纳里健康】小纳提醒：患者{1}的转诊住院申请已被{2}医生接收，住院办理相关事宜已通过短信发送给患者。如有疑问，请通过{3}联系小纳。
     *
     * @param tp
     * @author liqifei
     * @date 2015-6-19下午15:50:28
     */
    public void sendSmsForTransferInHosp(TransferAndPatient tp) {
        Transfer transfer = tp.getTransfer();
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(transfer.getTransferId());
        smsInfo.setBusType("DocInHospTransferConfirm");
        smsInfo.setSmsType("DocInHospTransferConfirm");
        smsInfo.setOrganId(transfer.getTargetOrgan());
        smsInfo.setClientId(null);
        smsInfo.setCreateTime(new Date());
        smsInfo.setStatus(0);

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);

    }


    /**
     * 转诊备案重试服务
     *
     * @param transferId
     * @author wnw
     * @date 2015-11-27
     */
    @RpcService
    public void retryRegistTransfer(Integer transferId) {
        Transfer ts = this.getById(transferId);
        if (ts == null) {
            throw new DAOException("未能找到对应的转诊单！无法取消");
        }
        registTransfer(ts);
    }

    /**
     * 调his转诊申请服务
     *
     * @param trans
     * @author ZX
     * @date 2015-7-8 下午5:50:39
     */
    @RpcService
    public void registTransfer(Transfer trans) {
        // 判断是否为自费病人，如果是自费病人，则不需要调his接口
        String mpiId = trans.getMpiId();
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDao.getByMpiId(mpiId);
        if (p == null) {
            return;
        }
        if (p.getPatientType().equals("1")) {
            return;
        }

        int requestOrganId = trans.getRequestOrgan();

        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);

        Doctor requestDoc = doctorDao.getByDoctorId(trans.getRequestDoctor());
        Doctor targetDoc = doctorDao.getByDoctorId(trans.getTargetDoctor());

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        List<HealthCard> cards = cardDAO.findByMpiId(p.getMpiId());

        try {
            MedRequest medReq = new MedRequest();

            // 赋值(将平台数据转化成his数据)
            medReq.setAppointDoctorName(requestDoc.getName());// 申请医生姓名

            for (HealthCard healthCard : cards) {
                if (healthCard.getCardType().equals("2")
                        && healthCard.getCardOrgan().toString()
                        .equals(p.getPatientType())) {
                    medReq.setCardno(healthCard.getCardId());// 医保卡号
                    break;
                }
            }
            if (org.apache.commons.lang3.StringUtils
                    .isEmpty(medReq.getCardno())) {
                return;
            }
            medReq.setCertno(p.getIdcard());// 身份证号
            medReq.setDiagnose(trans.getDiagianName());// 诊断名称
            medReq.setIcdcode(trans.getDiagianCode());// 诊断码
            medReq.setPname(p.getPatientName());// 患者姓名
            medReq.setRequestOrganId(requestOrganId);// 申请机构
            medReq.setTargetDoctorName(targetDoc.getName());// 主治医生姓名
            medReq.setTargetOrganId(trans.getTargetOrgan());// 目标机构
            medReq.setPatientCondition(trans.getPatientCondition());// 病情描述
            medReq.setTrycount(0);// 重试次数
            medReq.setCreatetime(new Date());

            medReq.setPatientType(p.getPatientType());
            medReq.setRequestId(trans.getTransferId());
            medReq.setRequestType(1);// 1转诊2预约
            medReq.setPatientRequire(trans.getPatientRequire());// 转诊要求
            TransferSendExecutor executor = new TransferSendExecutor(medReq);
            executor.execute();

        } catch (Exception e) {
            log.error("his invoke error" + e.getMessage());
        }
    }

    /**
     * @param status 1成功
     * @param id     业务id
     */
    @RpcService
    @DAOMethod
    public abstract void updateInsuRecordById(int status, int id);

    /**
     * 根据转诊单Id更新状态
     *
     * @param transferStatus
     * @param id
     * @author zhangx
     * @date 2015-12-16 下午2:04:26
     */
    @RpcService
    @DAOMethod
    public abstract void updateTransferStatusById(int transferStatus, int id);

    /**
     * 转诊备案 状态回写
     *
     * @param id   业务id
     * @param type 业务类型 1转诊2预约
     */
    @RpcService
    public void updateInsuRecord(Integer id, Integer type) throws DAOException {
        if (null == id || StringUtils.isEmpty(type)) {
            return;
        }
        if (type.equals(1)) {
            updateInsuRecordById(1, id);
        }
    }

    /**
     * 转诊备案失败回写服务
     *
     * @param transferId 转诊单号
     * @param errMsg     备案失败原因
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set cancelCause:=cancelCause,cancelDoctor=requestDoctor,cancelTime=NOW() where transferId:=transferId")
    public abstract void updateInsuRecordForFail(
            @DAOParam("transferId") int transferId,
            @DAOParam("cancelCause") String errMsg);

    /**
     * 转诊备案取消服务
     *
     * @param transferId 转诊单号
     * @author wnw
     * @date 2015-11-26
     */
    @RpcService
    public void cancelInsuRecord(int transferId) throws DAOException {
        Transfer ts = this.getById(transferId);
        if (ts == null) {
            throw new DAOException("未能找到对应的转诊单！无法取消");
        }
        if (ts.getInsuRecord() == 0) {
            throw new DAOException("该转诊单未备案！无法取消");
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory
                .getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(ts
                .getRequestOrgan());
        if (cfg == null) {
            throw new DAOException("该机构未配置转诊取消服务");
        }
        String hisServiceId = cfg.getAppDomainId() + ".transferService";// 调用服务id
//        IHisServiceInterface transferService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);

        log.info("start to cancel medRecord:" + transferId);
//        boolean isSucc = transferService.cancelMedResult(transferId);
        //boolean isSucc = (boolean)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId,"cancelMedResult",transferId);
        boolean isSucc = false;
        if(DBParamLoaderUtil.getOrganSwich(cfg.getOrganid())){
			IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
			CancelAllTransferRequestTO cancelAllTransferRequestTO = new CancelAllTransferRequestTO();
			cancelAllTransferRequestTO.setOrganId(ts.getRequestOrgan());
			cancelAllTransferRequestTO.setTransferId(ts.getTransferId());
			appointService.cancelAllTransferResult(cancelAllTransferRequestTO);
		}else
			isSucc = (boolean)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId,"cancelMedResult",transferId);
        if (isSucc) {
            log.info(" cancel medRecord success:" + transferId);
            updateInsuRecordById(0, transferId);
        }

    }

    /**
     * Title:查询转诊业务数据 Description: 根据开始时间和结束时间查询转诊业务数据，按转诊申请时间降序排列
     *
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @return int
     * @author AngryKitty
     * @date 2015-8-24
     */

    @RpcService
    public List<Transfer> findByStartTimeAndEndTime(final Date startTime,
                                                    final Date endTime) {
        HibernateStatelessResultAction<List<Transfer>> action = new AbstractHibernateStatelessResultAction<List<Transfer>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                String sql = " from Transfer ppt where ppt.requestTime>=:startTime and ppt.requestTime<=:endTime order by requestTime desc ";
                Query q = ss.createQuery(sql);
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                List<Transfer> andResults = q.list();
                setResult(andResults);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title: 统计转诊信息 Description:
     * 在原来的基础上，新加一个搜索参数（患者主键），跟机构相关的查询条件（申请机构、目标机构）由原来的一个定值改为一个数组
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param tran          ---转诊信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param requestOrgans ---转诊申请机构（集合）
     * @param targetOrgans  ---转诊目标机构（集合）
     * @return QueryResult<TransferAndPatient>
     * @author AngryKitty
     * @date 2015-8-31
     * @date 2016-2-23 luf 添加 if (tran != null)
     * @desc 添加陪诊、转诊类型、紧急程序查询条件，添加无机构条件即患者申请  zhangjr 2016-04-20
     */
    @RpcService
    public QueryResult<TransferAndPatient> findTransferAndPatientByStatic(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

        this.validateOptionForStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<QueryResult<TransferAndPatient>> action = new AbstractHibernateStatelessResultAction<QueryResult<TransferAndPatient>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;
                StringBuilder hql = preparedHql;
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setDate("startTime", startTime);
                countQuery.setDate("endTime", endTime);
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数

                hql.append(" order by a.requestTime desc");
                Query query = ss.createQuery("select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType,b.mobile,b.idcard) " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<TransferAndPatient> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                for (TransferAndPatient transferAndPatient : tfList) {
                    Transfer tr = transferAndPatient.getTransfer();
                    // 申请医生电话
                    Integer requestDocId = tr.getRequestDoctor();
                    if (requestDocId != null) {//医生申请
                        Doctor reqDoctor = doctorDAO.getByDoctorId(requestDocId);
                        if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                            transferAndPatient.setRequestDoctorMobile(reqDoctor
                                    .getMobile());
                        }
                    } else {//患者申请
                        String requestMpi = tr.getRequestMpi();
                        if (tr.getMpiId().equals(requestMpi)) {
                            transferAndPatient.setRequestPatientName(transferAndPatient.getPatientName());
                            transferAndPatient.setRequestPatientTel(transferAndPatient.getPatientMobile());
                        } else {
                            Patient requestPatient = patientDAO.getPatientByMpiId(requestMpi);
                            transferAndPatient.setRequestPatientName(requestPatient.getPatientName());
                            transferAndPatient.setRequestPatientTel(requestPatient.getMobile());
                        }
                    }


                    // 目标医生电话
                    int targerDocId = transferAndPatient.getTransfer()
                            .getTargetDoctor();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        transferAndPatient.setTargetDoctorMobile(targetDoctor
                                .getMobile());
                    }

                }
                QueryResult<TransferAndPatient> qResult = new QueryResult<TransferAndPatient>(
                        total, query.getFirstResult(), query.getMaxResults(),
                        tfList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<TransferAndPatient>) action.getResult();
    }

    /**
     * Title: 根据状态统计
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param tran          ---转诊信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param requestOrgans ---转诊申请机构（集合）
     * @param targetOrgans  ---转诊目标机构（集合）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByStatus(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        this.validateOptionForStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.transferStatus ");
                Query query = ss.createQuery("select a.transferStatus, count(a.transferId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.TransferStatus").getText(status);
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
     * Title: 根据备案情况统计
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param tran          ---转诊信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param requestOrgans ---转诊申请机构（集合）
     * @param targetOrgans  ---转诊目标机构（集合）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByInsuRecord(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        this.validateOptionForStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.insuRecord ");
                Query query = ss.createQuery("select a.insuRecord, count(a.transferId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            String status = hps[0].toString();
                            if (status != null)
                            {
                                if (Integer.parseInt(status) == 1)
                                {
                                    status = "已备案";
                                }
                                else
                                {
                                    status = "未备案";
                                }
                                mapStatistics.put(status, Integer.parseInt(hps[1].toString()));
                            }
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
     * Title: 根据申请机构统计
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param tran          ---转诊信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param requestOrgans ---转诊申请机构（集合）
     * @param targetOrgans  ---转诊目标机构（集合）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Date startTime, final Date endTime, final Transfer tran,
                                                                final int start, final String mpiId, final Integer type,
                                                                final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        this.validateOptionForStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.requestOrgan ");
                Query query = ss.createQuery("select a.requestOrgan, count(a.transferId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            Integer consultOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(consultOrganId, Integer.parseInt(hps[1].toString()));
                        }
                        else
                        {
                            if (hps[1] != null && Integer.parseInt(hps[1].toString()) >0)
                            {
                                Integer emptyCount = 0 ;
                                if (mapStatistics.get(0) != null)
                                {
                                    emptyCount +=  Integer.parseInt(mapStatistics.get(0).toString());
                                }
                                else
                                {
                                    emptyCount = Integer.parseInt(hps[1].toString());
                                }
                                mapStatistics.put(0, emptyCount);
                            }
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
     * Title: 根据目标机构统计
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param tran          ---转诊信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param requestOrgans ---转诊申请机构（集合）
     * @param targetOrgans  ---转诊目标机构（集合）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Date startTime, final Date endTime, final Transfer tran,
                                                               final int start, final String mpiId, final Integer type,
                                                               final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        this.validateOptionForStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,tran,start,mpiId,type,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.targetOrgan ");
                Query query = ss.createQuery("select a.targetOrgan, count(a.transferId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            Integer consultOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(consultOrganId, Integer.parseInt(hps[1].toString()));
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

    private void validateOptionForStatistics(final Date startTime, final Date endTime, final Transfer tran,
                                             final int start, final String mpiId, final Integer type,
                                             final List<Integer> requestOrgans, final List<Integer> targetOrgans)
    {

        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
    }

    private StringBuilder generateHQLforStatistics(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        StringBuilder hql = new StringBuilder(
                " from Transfer a,Patient b where a.mpiId=b.mpiId and DATE(a.requestTime)>=DATE(:startTime)"
                        + " and DATE(a.requestTime)<=DATE(:endTime) ");

        // 添加申请机构条件
        if (requestOrgans != null && requestOrgans.size() > 0) {
            boolean flag = true;
            for (Integer i : requestOrgans) {
                if (i != null) {
                    if (i == 0) {//无机构患者申请
                        hql.append(" and a.requestMpi is not null ");
                        break;
                    } else {
                        if (flag) {
                            hql.append(" and requestDoctor is not null and a.requestOrgan in(");
                            flag = false;
                        }
                        hql.append(i + ",");
                    }

                }
            }
            if (!flag) {
                hql = new StringBuilder(hql.substring(0,
                        hql.length() - 1) + ") ");
            }
        }

        // 添加目标机构条件
        if (targetOrgans != null && targetOrgans.size() > 0) {
            boolean flag = true;
            for (Integer i : targetOrgans) {
                if (i != null) {
                    if (flag) {
                        hql.append(" and a.targetOrgan in(");
                        flag = false;
                    }
                    hql.append(i + ",");
                }
            }
            if (!flag) {
                hql = new StringBuilder(hql.substring(0,
                        hql.length() - 1) + ") ");
            }
        }

        // 患者主键
        if (!StringUtils.isEmpty(mpiId)) {
            hql.append(" and a.mpiId='" + mpiId + "' ");
        }

        if (tran != null) {
            // 添加申请医生条件
            if (tran.getRequestDoctor() != null) {
                hql.append(" and a.requestDoctor="
                        + tran.getRequestDoctor());
            }

            // 添加目标医生条件
            if (tran.getTargetDoctor() != null) {
                hql.append(" and a.targetDoctor="
                        + tran.getTargetDoctor());
            }

            // 添加转诊单结果
            if (tran.getTransferResult() != null) {
                hql.append(" and a.transferResult="
                        + tran.getTransferResult());
            }

            //添加（患者申请）申请人
            if (tran.getRequestMpi() != null) {
                hql.append(" and a.requestMpi = '" + tran.getRequestMpi() + "' ");
            }

            // 添加转诊单状态
            if (tran.getTransferStatus() != null) {
                hql.append(" and a.transferStatus="
                        + tran.getTransferStatus());
            }

            //添加陪诊查询条件
            if (tran.getAccompanyFlag() != null) {
                hql.append(" and a.accompanyFlag="
                        + tran.getAccompanyFlag());
            }
            //添加紧急程度查询条件
            if (tran.getEmergency() != null) {
                hql.append(" and a.emergency="
                        + tran.getEmergency());
            }
        }

        //添加转诊类型条件
        if (type != null) {
            if (type == 0) {//有号
                hql.append(" and a.isAdd =" + 0);
            } else if (type == 1) {//加号
                hql.append(" and a.isAdd =" + 1);
            } else if (type == 2) {//门诊转诊
                hql.append(" and (((a.transferStatus <2 or a.transferStatus=9) and a.transferType = 1) or ( a.transferStatus >=2 and a.transferStatus<>9 and a.transferResultType = 1) or (a.transferResult = 2 and a.transferType = 1))");
            } else if (type == 3) {//住院转诊
                hql.append(" and (((a.transferStatus <2 or a.transferStatus=9) and a.transferType = 2) or ( a.transferStatus >=2 and a.transferStatus<>9 and a.transferResultType = 2) or (a.transferResult = 2 and a.transferType = 2))");
            } else if (type == 4) {//远程门诊转诊
                hql.append(" and (((a.transferStatus <2 or a.transferStatus=9) and a.transferType = 3) or ( a.transferStatus >=2 and a.transferStatus<>9 and a.transferResultType = 3) or (a.transferResult = 2 and a.transferType = 3))");
            }
        }
        return hql;
    }
    /**
     * 根据医生id，转诊状态，转诊结果获取转诊记录
     *
     * @param doctorId       医生id
     * @param transferStatus 转诊单状态(0:待处理；1处理中；2审核完成；3已到院就诊；4住院预约；5入院登记；7医院处理失败；8医院确认中；
     *                       9已取消)
     * @param transferResult 转诊结果( 1接收；2拒绝)
     * @return
     * @author zhangx
     * @date 2015-10-9下午7:58:07
     */
    @RpcService
    @DAOMethod(sql = "from Transfer where (requestDoctor=:doctorId or confirmDoctor=:doctorId) and transferStatus=:transferStatus and transferResult=:transferResult and payflag=1 and IsAdd =1 and requestDoctor is not null")
    public abstract List<Transfer> findTransferByDoctorIdAndStatus(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("transferStatus") int transferStatus,
            @DAOParam("transferResult") int transferResult);

    @DAOMethod(sql = "SELECT count(*) FROM Transfer a,Doctor d WHERE (a.requestDoctor = :doctorId OR a.confirmDoctor = :doctorId) AND a.transferStatus = 2 AND a.transferResult = 1 AND a.payflag = 1 AND a.isAdd = 1 AND a.requestDoctor IS NOT NULL and a.confirmDoctor=d.doctorId and d.virtualDoctor=1")
    public abstract long getVirtualTransferNum( @DAOParam("doctorId") Integer doctorId);

    /**
     * 历史转诊单列表服务(纯分页)
     *
     * @param doctorId 医生内码
     * @param start    页面开始位置
     * @param limit    每页限制条数
     * @return List<TransferAndPatient>
     * @author luf
     */
    @RpcService
    public List<TransferAndPatient> queryTransferHisWithPage(int doctorId,
                                                             int start, int limit) {
        List<TransferAndPatient> taps = new ArrayList<TransferAndPatient>();
        List<Transfer> transfers = this.findHisTransByDoctorId(doctorId, start,
                limit);
        RelationDoctorDAO RelationDoctorDAO = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        AppointDepartDAO AppointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        for (Transfer transfer : transfers) {
            TransferAndPatient tap = new TransferAndPatient();
            String mpiId = transfer.getMpiId();
            // 获取签约标记
            Boolean signFlag = RelationDoctorDAO.getSignFlag(mpiId, doctorId);
            tap.setSignFlag(signFlag);// transfer.setSignFlag(signFlag);
            // 获取团队标记
            Boolean teams = doctorDAO.getByDoctorId(transfer.getTargetDoctor())
                    .getTeams();
            tap.setTeams(teams);// transfer.setTeams(teams);
            // 获取挂号科室编码(appointDepartCode)
            String departId = transfer.getAppointDepartId();
            if (!StringUtils.isEmpty(departId)) {
                // 通过 挂号科室编码(appointDepartCode) 和 预约机构编号(ConfirmOrgan)
                // 获取挂号科室
                AppointDepart appointdepart = AppointDepartDAO
                        .getByOrganIDAndAppointDepartCode(
                                transfer.getConfirmOrgan(), departId);

                if (appointdepart != null) {
                    transfer.setAppointDepartName(appointdepart
                            .getAppointDepartName());
                    transfer.setProfessionCode(appointdepart
                            .getProfessionCode());
                }
            }
            Patient patient = patientDAO.get(mpiId);
            // patient = this.convertPatientForTransfer(patient);
            tap.setTransfer(transfer);// tap.setPatient(patient);
            tap.setPatientSex(patient.getPatientSex());
            tap.setBirthday(patient.getBirthday());
            tap.setPhoto(patient.getPhoto());
            tap.setPatientName(patient.getPatientName());
            tap.setPatientType(patient.getPatientType());
            tap.setPatientMobile(patient.getMobile());
            tap.setIdcard(patient.getIdcard());
            taps.add(tap);
        }
        return taps;
    }

    /**
     * 根据医生查历史转诊单列表
     *
     * @param doctorId 医生内码
     * @param start    页面开始位置
     * @param limit    每页限制条数
     * @return List<Transfer>
     * @author luf
     */
    @DAOMethod(sql = "from Transfer where transferStatus>1 and payflag=1 and (agreeDoctor=:doctorId or requestDoctor=:doctorId) order by requestTime desc")
    public abstract List<Transfer> findHisTransByDoctorId(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 病人信息转换
     *
     * @param patient 病人信息
     * @return Patient
     * @author luf
     */
    public Patient convertPatientForTransfer(Patient patient) {
        Patient p = new Patient();
        p.setMpiId(patient.getMpiId());
        p.setPatientSex(patient.getPatientSex());
        p.setBirthday(patient.getBirthday());
        p.setPhoto(patient.getPhoto());
        p.setPatientName(patient.getPatientName());
        p.setPatientType(patient.getPatientType());
        p.setMobile(patient.getMobile());
        p.setIdcard(patient.getIdcard());
        return p;
    }

    /**
     * 获取转诊详情单（包括预约信息）
     *
     * @param transferId 转诊序号
     * @return TransferAndPatient
     * @author luf
     */
    @RpcService
    public TransferAndPatient getTransferAndAppointById(Integer transferId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        HealthCardDAO healthCardDao = DAOFactory.getDAO(HealthCardDAO.class);
        CdrOtherdocDAO cdrOtherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        AppointRecordDAO appointRecordDAO = DAOFactory
                .getDAO(AppointRecordDAO.class);
        EmploymentDAO emDao = DAOFactory.getDAO(EmploymentDAO.class);
        AppointInhospDAO appointInhospDAO = DAOFactory.getDAO(AppointInhospDAO.class);
        AppointInhosp appointInhosp = appointInhospDAO.getByTransferId(transferId);
        TransferAndPatient tp = new TransferAndPatient();
        Transfer transfer = this.get(transferId);
        if (transfer == null) {
            return tp;
        }
        String mpiId = transfer.getMpiId();
        Integer requestDoctorId = transfer.getRequestDoctor();
        Integer targetDoctorId = transfer.getTargetDoctor();
        Integer exeDoctorId = transfer.getConfirmDoctor();

        Patient patient = patientDAO.get(mpiId);
        patient = this.convertPatientForTransfer(patient);

        Doctor requestDoctor = null;
        if (StringUtils.isEmpty(requestDoctorId) == false) {
            requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);
        }
        Doctor targetDoctor = doctorDAO.getByDoctorId(targetDoctorId);
        Doctor exeDoctor = new Doctor();
        if (exeDoctorId != null) {
            exeDoctor = doctorDAO.getByDoctorId(exeDoctorId);
        }
        // 获取患者医保卡号
        Integer cardOrgan = Integer.parseInt(patient.getPatientType());
        String cardId = healthCardDao.getMedicareCardId(mpiId, cardOrgan);
        patient.setCardId(cardId);
        // 获取挂号科室编码(appointDepartCode)
        String departId = transfer.getAppointDepartId();
        if (departId != null) {
            // 通过 挂号科室编码(appointDepartCode) 和 预约机构编号(ConfirmOrgan)
            // 获取挂号科室
            AppointDepartDAO AppointDepartDAO = DAOFactory
                    .getDAO(AppointDepartDAO.class);
            AppointDepart appointdepart = AppointDepartDAO
                    .getByOrganIDAndAppointDepartCode(
                            transfer.getConfirmOrgan(), departId);
            if (appointdepart != null) {
                transfer.setAppointDepartName(appointdepart
                        .getAppointDepartName());
                transfer.setProfessionCode(appointdepart.getProfessionCode());
            }
        }
        List<Otherdoc> cdrOtherdocs = cdrOtherdocDAO
                .findByClinicTypeAndClinicId(1, transferId);
        List<AppointRecord> appointRecords = appointRecordDAO
                .findByTransferId(transferId);
        Integer receiveDoctorId = null;
        Doctor receiveDoctor = null;
        for (AppointRecord ar : appointRecords) {
            if (ar.getClinicObject() != null && ar.getClinicObject() == 1) {
                receiveDoctorId = ar.getDoctorId();
                if (receiveDoctorId != null) {
                    receiveDoctor = doctorDAO.getByDoctorId(receiveDoctorId);
                    Employment em = emDao
                            .getPrimaryEmpByDoctorId(receiveDoctorId);
                    if (em != null && em.getDepartment() != null) {
                        receiveDoctor.setDepartment(em.getDepartment());
                    }
                }
            }
        }

        tp.setTransfer(transfer);
        tp.setPatient(patient);
        tp.setRequestDoctor(requestDoctor);
        tp.setTargetDoctor(targetDoctor);
        tp.setExeDoctor(exeDoctor);
        tp.setCdrOtherdocs(cdrOtherdocs);
        tp.setAppointRecords(appointRecords);
        tp.setReceiveDoctor(receiveDoctor);
        tp.setAppointInhosp(appointInhosp);
        return tp;
    }

    /**
     * 判断是否有资格备案
     *
     * @param trans 转诊信息--mpiId主索引，requestOrgan申请机构，targetOrgan目标机构不可空
     * @return Boolean
     * @author luf
     */
    @RpcService
    public Boolean insuRecordEnableOrDis(Transfer trans) {
        String mpiId = trans.getMpiId();
        if (StringUtils.isEmpty(mpiId)) {
            return false;
        }
        Integer requestOrgan = trans.getRequestOrgan();
        if (requestOrgan == null) {
            return false;
        }
        Integer targetOrgan = trans.getTargetOrgan();
        if (targetOrgan == null) {
            return false;
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory
                .getDAO(HisServiceConfigDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        String patientType = patient.getPatientType();
        if (StringUtils.isEmpty(patientType)) {
            patientType = "1";
        }
        if (!patientType.equals("1")
                && !requestOrgan.equals(targetOrgan)
                && hisServiceConfigDao.isServiceEnable(requestOrgan,
                ServiceType.MEDFILING)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 有号转诊服务（包含预约）
     *
     * @param trans          转诊信息
     * @param otherDocs      其他文档
     * @param appointRecords 预约记录列表
     * @return Boolean
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     * @Date 2016-1-26 修改-关于数据库锁死
     * @Date 2016-1-26 修改-关于数据库锁死
     */
    @RpcService
    public Boolean requestTransferClinic(Transfer trans,
                                         List<Otherdoc> otherDocs,
                                         List<AppointRecord> appointRecords) throws DAOException, ControllerException {

        RequestTransferService requestTransferService = new RequestTransferService();
        Integer result = requestTransferService.requestTransferClinic(trans, otherDocs, appointRecords);
        if (null == result || 0 >= result) {
            return false;
        }
        return true;
    }

    private boolean checkSource(AppointRecord o) {
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        Integer sourceid = o.getAppointSourceId();
        if (sourceid == null || sourceid.intValue() == 0) {
            return true;
        }
        AppointSource source = appointSourceDAO.get(sourceid);
        if (source == null) {
            return true;
        } else {
            return source.getSourceNum() > source.getUsedNum();
        }
    }

    @DAOMethod(sql = "from Transfer where transferStatus<2 and payflag=1 and mpiId=:mpiId and requestDoctor is not null")
    public abstract Transfer getApplyingTransferRecordByMpiId(
            @DAOParam("mpiId") String mpiId);

    /**
     * 加号转诊服务（修改备案部分）
     *
     * @param trans     申请转诊信息
     * @param otherDocs 其他电子病历列表
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     */
    @RpcService
    public Integer createTransferAdd(final Transfer trans,
                                     final List<Otherdoc> otherDocs) throws DAOException,
            ControllerException {
        log.info("转诊申请前端数据:" + JSONUtils.toString(trans));

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);

        Integer requestOrgan = trans.getRequestOrgan();
        Integer requestDepart = trans.getRequestDepart();
        Integer requestDoctor = trans.getRequestDoctor();
        String mpiId = trans.getMpiId();
        Integer targetDoctor = trans.getTargetDoctor();
        Integer targetOrgan = trans.getTargetOrgan();
        boolean virtualdoctor = false;// 是否虚拟医生标志true是false否
        Integer insuFlag = trans.getInsuFlag() == null ? 0 : trans.getInsuFlag();
        Boolean accompanyFlag = trans.getAccompanyFlag() == null ? false : trans.getAccompanyFlag();

        Doctor requestDoc = doctorDAO.getByDoctorId(requestDoctor);
        final Doctor targetDoc = doctorDAO.getByDoctorId(targetDoctor);
        Patient patient = patientDAO.getByMpiId(mpiId);
        if (requestOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestOrgan is required!");
        }
        if (requestDepart == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestDepart is required!");
        }
        if (mpiId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "待转诊病人为空");
        }
        if (requestDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请医生为空");
        }
        if (targetDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医生为空");
        } else {
            // 判断目标医生是否虚拟医生
            virtualdoctor = targetDoc.getVirtualDoctor() == null ? false : targetDoc.getVirtualDoctor();
        }
        if (targetOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医院不能为空");
        }
        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(trans.getAnswerTel())) {
            trans.setAnswerTel(patient.getMobile());
        }

        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能转诊");
        }
        new ObtainImageInfoService(patient,requestOrgan).getImageInfo();
        // 蒋旭辉和王宁武转诊申请不限制
        if (!mpiId.equals("2c9081814cc5cb8a014ccf86ae3d0000")
                && !mpiId.equals("2c9081814cc3ad35014cc3e0361f0000")
                && getApplyingTransferRecordByMpiId(mpiId) != null) {
            throw new DAOException(602, "患者" + patient.getPatientName()
                    + "有一条未处理的转诊申请单，不能再进行转诊");
        }
        // 患者和目标医生不能为同一个人(申请医生不能把目标医生(患者身份)转诊给目标医生)
        if (SameUserMatching.patientAndDoctor(mpiId, targetDoctor)) {
            throw new DAOException(609, "患者和目标医生不能为同一个人");
        }
        trans.setAccompanyFlag(accompanyFlag);
        if (accompanyFlag) {
            trans.setAccompanyPrice(DAOFactory.getDAO(OrganDAO.class)
                    .get(targetOrgan).getAccompanyPrice());
        }
        try {
            trans.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        } catch (Exception e) {
            log.info(LocalStringUtil.format("createTransferAdd get deviceId from session exception! errorMessage[{}]", e.getMessage()));
        }
        HibernateStatelessResultAction<Transfer> action = new AbstractHibernateStatelessResultAction<Transfer>() {
            @Override
            public void execute(StatelessSession arg0) throws Exception {
                // 保存转诊申请信息
                Date requestTime = new Date();
                trans.setRequestTime(requestTime);
                trans.setTransferStatus(0);
                trans.setInsuRecord(0);
                trans.setPayflag(1);// 默认支付
                trans.setIsAdd(true);// 加号转诊
                trans.setEvaStatus(0);// 加号转诊 默认评价未处理
                Transfer tr = save(trans);
                setResult(tr);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Transfer t = action.getResult();

        //纳里医生App端首页个性化 加号转诊申请成功
        if(!virtualdoctor) {
            asynDoBussService.fireEvent(new BussCreateEvent(t, BussTypeConstant.TRANSFER));
        }
        // 如果申转诊请成功,则推送消息给目标医生
        if (t != null) {
            // 给转诊申请医生的推荐医生推荐奖励，不考虑该转诊单是否成功完成
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(t.getRequestDoctor());

            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForTransfer(t);
            // 获取转诊单号
            Integer transferId = t.getTransferId();
            // 保存图片
            if (otherDocs != null && otherDocs.size() > 0) {
                DAOFactory.getDAO(CdrOtherdocDAO.class).saveOtherDocList(1,
                        transferId, otherDocs);
            }
            if (!virtualdoctor) {// 如果目标医生是非虚拟医生
                // 2017-2-7 luf:关闭转诊创建群聊入口
                //2017-6-2 20:23:39 zhangx 消息优化：将消息拆分成更加细致的场景
                sendDocTransferApplyMsg(t);
                 // 创建群聊
//                GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
//                Group group = groupDAO.createTransferGroup(transferId);
//                if (!StringUtils.isEmpty(group.getGroupId())) {
//                    updateSessionStartTimeByTransferId(t.getRequestTime(),
//                            transferId);
//                    updateSessionIDByTransferId(group.getGroupId(), transferId);
//                }
            } else {// 如果目标医生是虚拟医生,则将该转诊申请直接确认
                autoConfirmTransferForVitural(t);
            }
            if (insuFlag == 1
                    && hisServiceConfigDao.isServiceEnable(requestOrgan,
                    ServiceType.MEDFILING)) {
                log.info("发起医保备案服务");
                registTransfer(trans);
            }
            return t.getTransferId();
        }
        return null;
    }

    /**
     * 我的转诊/我的申请列表
     *
     * @param doctorId 医生内码
     * @param mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     * @param listType 来源--0我的申请1我的转诊
     * @param start    分页开始位置
     * @param limit    每页限制条件
     * @return Hashtable<String, List<TransferAndPatient>>
     * 只有mark为0时，“unfinished”对应的list有值
     * @author luf
     */
    public Hashtable<String, List<TransferAndPatient>> queryRequestOrTransferList(
            int doctorId, int mark, int listType, int start, int limit) {
        Hashtable<String, List<TransferAndPatient>> table = new Hashtable<String, List<TransferAndPatient>>();
        List<TransferAndPatient> taps = new ArrayList<TransferAndPatient>();
        List<Transfer> transfers = new ArrayList<Transfer>();
        if (listType == 0) {
            transfers = findRequestTransferList(doctorId, mark, start, limit);
        } else {
            transfers = findTransferList(doctorId, mark, start, limit);
        }
        if (mark == 0) {
            taps = convertFromTransferToAnd(transfers, listType);
            table.put("unfinished", taps);
            if (transfers.size() < limit) {
                if (listType == 0) {
                    transfers = findRequestTransferList(doctorId, 1, 0, limit
                            - transfers.size());
                } else {
                    transfers = findTransferList(doctorId, 1, 0, limit
                            - transfers.size());
                }
                taps = convertFromTransferToAnd(transfers, listType);
                table.put("completed", taps);
            } else {
                table.put("completed", new ArrayList<TransferAndPatient>());
            }
        } else {
            taps = convertFromTransferToAnd(transfers, listType);
            table.put("completed", taps);
            table.put("unfinished", new ArrayList<TransferAndPatient>());
        }
        return table;
    }

    /**
     * 供 queryRequestOrTransferList 调用
     *
     * @param doctorId 医生内码
     * @param mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     * @param start    分页开始位置
     * @param limit    每页限制条件
     * @return List<Transfer>
     * @author luf
     */
    public List<Transfer> findRequestTransferList(final int doctorId,
                                                  final int mark, final int start, final int limit) {
        HibernateStatelessResultAction<List<Transfer>> action = new AbstractHibernateStatelessResultAction<List<Transfer>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "From Transfer where payflag=1 and requestDoctor=:requestDoctor ");
                switch (mark) {
                    case 0:
                        hql.append("and (transferStatus=0 or transferStatus=1 or transferStatus=8) ");
                        break;
                    case 1:
                        hql.append("and transferStatus>=2 and transferStatus<>8 ");
                        break;
                    case 2:
                        hql.append("and (transferStatus=0 or transferStatus=1) ");
                        break;
                    case 3:
                        // desc_2016.3.7 根据需求显示待就诊转诊记录
                        hql.append("and ((transferStatus=2 or transferStatus=8) and "
                                + "confirmClinicTime>now()) ");
                        // hql.append("and (((transferStatus=2 or transferStatus=3 or transferStatus=5)and "
                        // +
                        // "confirmClinicTime>now())or transferStatus=4 or transferStatus=8) ");
                        break;
                    case 4:
                        hql.append("and((transferStatus=2 or transferStatus=3 or transferStatus=5)and "
                                + "confirmClinicTime<=now()) ");
                    default:
                        break;
                }
                if (mark == 3) {
                    hql.append("order by confirmClinicTime asc");
                } else if (mark == 4) {
                    hql.append("order by confirmClinicTime desc");
                } else {
                    hql.append("order by requestTime desc");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("requestDoctor", doctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供 queryRequestOrTransferList 调用
     *
     * @param transfers 转诊申请单列表
     * @param from      0我的申请1我的转诊
     * @return List<TransferAndPatient>
     * @author luf
     */
    public List<TransferAndPatient> convertFromTransferToAnd(
            List<Transfer> transfers, int from) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<TransferAndPatient> taps = new ArrayList<TransferAndPatient>();
        for (Transfer transfer : transfers) {
            TransferAndPatient tap = new TransferAndPatient();
            String mpiId = transfer.getMpiId();
            Integer doctorId = 0;
            if (from == 0) {
                doctorId = transfer.getRequestDoctor();
            }
            if (from == 1) {
                doctorId = transfer.getAgreeDoctor();
                if (doctorId == null || doctorId == 0) {
                    doctorId = transfer.getTargetDoctor();
                }
                if (!StringUtils.isEmpty(transfer.getRequestMpi())) {
                    tap.setRequestPatient(patientDAO.getNameByMpiId(transfer
                            .getRequestMpi()));
                }
            }
            Patient patient = patientDAO.convertPatientForAppointRecord(mpiId,
                    doctorId);
            Boolean teams = doctorDAO.getByDoctorId(transfer.getTargetDoctor())
                    .getTeams();
            String requestTimeString = DateConversion
                    .convertRequestDateForBuss(transfer.getRequestTime());
            transfer.setRequestTimeString(requestTimeString);
            tap.setTeams(teams);// transfer.setTeams(teams);
            tap.setTransfer(transfer);
            tap.setPatient(patient);
            taps.add(tap);
        }
        return taps;
    }

    /**
     * 供 queryRequestOrTransferList 调用
     *
     * @param doctorId 医生内码
     *                 \
     *
     *
     * @param mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return List<Transfer>
     * @author luf
     */
    public List<Transfer> findTransferList(final int doctorId, final int mark,
                                           final int start, final int limit) {
        HibernateStatelessResultAction<List<Transfer>> action = new AbstractHibernateStatelessResultAction<List<Transfer>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                // 我的转诊列表中过滤掉 医院确认中的 转诊记录transferStatus<>8
                // desc_2016.3.7 暂定方案1 目标医生可以看到医院确认中的转诊记录 去除transferStatus<>8的条件 zjr
                // desc_2016.06.22 本地执行sql无反应，进行优化 zx

                List<Integer> transferIds = DAOFactory.getDAO(AppointRecordDAO.class)
                        .findTransferIdByClinicObjectAndDoctorId(1, doctorId);

                StringBuilder hql = new StringBuilder(
                        "From Transfer where payflag=1 and (targetDoctor=:doctorId or agreeDoctor=:doctorId");
                List<Integer> ids = DAOFactory.getDAO(DoctorGroupDAO.class)
                        .findDoctorIdsByMemberId(doctorId);
                for (Integer id : ids) {
                    hql.append(" or targetDoctor=");
                    hql.append(id);
                }
                // desc_2016.2.26 解决接诊医生看不到转诊记录的问题 zjr
                if (transferIds.size() > 0) {
                    hql.append(" or transferId in (:transferIds)");
                }

                hql.append(") ");
                switch (mark) {
                    case 0:
                        // desc_2016.2.29 解决团队中其他医生看到已处理中的转诊记录的问题 zjr
                        // desc_2016.3.2 解决医院确认中的转诊记录出现在接收医生的转诊列表内问题 删除
                        // transferStatus=8的并条件
                        hql.append("and (transferStatus=0 or (transferStatus=1 and agreeDoctor=:doctorId)) ");
                        break;
                    case 1:
                        // desc_2016.2.29 解决目标医生看到已取消的转诊记录问题 zjr
                        // desc_2016.3.2 解决目标医生看到团队中其他医生已接收的转诊记录问题 zjr
                        // desc_2016.3.7 解决接诊医生看不到接收成功转诊记录的问题 zjr
                        // desc_2016.3.7 针对方案1，去除transferStatus<>8的条件
                        // luf 2016-5-19 显示已取消转诊单，去掉 and transferStatus<>9
                        hql.append("and (agreeDoctor is null or agreeDoctor=:doctorId ");
                        if (transferIds.size() > 0) {
                            hql.append(" or transferId in (:transferIds)");
                        }
                        hql.append(")and transferStatus>=2");
                        // hql.append("and agreeDoctor=:doctorId and transferStatus>=2 and transferStatus<>8 and transferStatus<>9");
                        break;
                    case 2:
                        // desc_2016.2.29 解决团队中其他医生看到已处理中的转诊记录的问题 zjr
                        hql.append("and (transferStatus=0 or (transferStatus=1 and agreeDoctor=:doctorId)) ");
                        break;
                    case 3:
                        // desc_2016.3.7 根据需求显示待就诊转诊记录
                        hql.append("and ((transferStatus=2 or (transferStatus=8 and isAdd = 1 and agreeDoctor=:doctorId)) and "
                                + "confirmClinicTime>now())");
                        // hql.append("and (((transferStatus=2 or transferStatus=3 or transferStatus=5)and "
                        // +
                        // "confirmClinicTime>now()) or transferStatus=4 or transferStatus=8) ");
                        break;
                    case 4:
                        // desc_2016.3.3 解决团队其他医生看到已结束的转诊记录的问题 zjr
                        // desc_2016.3.7 解决接诊医生看不到已结束转诊记录问题
                        hql.append("and ((transferStatus=2 or transferStatus=3 or transferStatus=5) and "
                                + "confirmClinicTime<=now() and (agreeDoctor=:doctorId ");
                        if (transferIds.size() > 0) {
                            hql.append(" or transferId in (:transferIds)");
                        }
                        hql.append("))");
                    default:
                        break;
                }
                if (mark == 3) {
                    hql.append(" order by confirmClinicTime asc");
                } else if (mark == 4) {
                    hql.append(" order by confirmClinicTime desc");
                } else {
                    hql.append(" order by requestTime desc");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                if (transferIds.size() > 0) {
                    q.setParameterList("transferIds", transferIds);
                }

                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 我的转诊申请列表
     *
     * @param doctorId 医生内码
     * @param mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return Hashtable<String, List<TransferAndPatient>>
     * @author luf
     */
    @RpcService
    public Hashtable<String, List<TransferAndPatient>> queryRequestTransferList(
            int doctorId, int mark, int start, int limit) {
        return this.queryRequestOrTransferList(doctorId, mark, 0, start, limit);
    }

    /**
     * 我的转诊列表
     *
     * @param doctorId 医生内码
     * @param mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return Hashtable<String, List<TransferAndPatient>>
     * @author luf
     */
    @RpcService
    public Hashtable<String, List<TransferAndPatient>> queryTransferList(
            int doctorId, int mark, int start, int limit) {
        return this.queryRequestOrTransferList(doctorId, mark, 1, start, limit);
    }

    /**
     * 转诊取消（处理中也可取消）
     *
     * @param transfer 转诊信息
     * @throws DAOException
     * @author luf
     */
    @RpcService
    public void cancelTransferIn(final Transfer transfer) throws DAOException {
        log.info("转诊取消前端传入数据" + JSONUtils.toString(transfer));
        Transfer t = this.getById(transfer.getTransferId());
        if (t == null) {
            throw new DAOException(609, "不存在该转诊单");
        }
        if (t.getTransferStatus() > 1) {
            throw new DAOException(609, "不能取消");
        }
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update Transfer set transferStatus = 9, cancelOrgan=:cancelOrgan, cancelDepart=:cancelDepart, "
                        + "cancelDoctor=:cancelDoctor, cancelTime=:cancelTime, cancelCause=:cancelCause "
                        + "where transferId=:transferId and transferStatus <= 1";
                Query query = ss.createQuery(hql);
                query.setInteger("cancelOrgan", transfer.getCancelOrgan());
                query.setInteger("cancelDepart", transfer.getCancelDepart());
                query.setInteger("cancelDoctor", transfer.getCancelDoctor());
                query.setTimestamp("cancelTime", new Date());
                query.setString("cancelCause", transfer.getCancelCause());
                query.setInteger("transferId", transfer.getTransferId());
                setResult(query.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);

        //App端首页优化 取消转诊数据记录
        asynDoBussService.fireEvent(new BussCancelEvent(t.getTransferId(), BussTypeConstant.TRANSFER));

        Integer count = action.getResult();
        if (count != null && count > 0) {
            if (t.getInsuRecord() == 1) {// 已备案的需取消
                TransferCancelExecutor exe = new TransferCancelExecutor(t);
                exe.execute();
            }
            sendDocTransferCancelMsg(t);
        }
    }

    /**
     * 转诊取消（虚拟医生的不管什么状态都可取消）， 其他完成的不能取消
     *
     * @param transfer 转诊信息
     * @throws DAOException
     * @author andywang
     */
    @RpcService
    public void cancelTransferForOp(final Transfer transfer) throws DAOException {
        log.info("转诊取消前端传入数据" + JSONUtils.toString(transfer));
        Transfer t = this.getById(transfer.getTransferId());
        if (t == null) {
            throw new DAOException(609, "不存在该转诊单");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = null;
        if (t.getTargetDoctor() != null)
        {
            doctor = doctorDAO.getByDoctorId(t.getTargetDoctor());
        }
        if (doctor == null)
        {
            throw new DAOException(609, "找不到目标医生");
        }
        if(t.getTransferStatus()== 9)
        {
            throw new DAOException(609, "该转诊单已经取消，不能重复取消！");
        }
        if (t.getTransferStatus() > 1 && !doctor.getVirtualDoctor() ) {
            throw new DAOException(609, "不能取消");
        }
        t.setTransferStatus(9);
        t.setCancelTime(DateConversion.convertFromDateToTsp(new Date()));
        t.setCancelCause("客服取消");
        TransferDAO tDao = DAOFactory.getDAO(TransferDAO.class);
        Transfer updatedTransfer=tDao.update(t);
        if (t.getInsuRecord() == 1) {// 已备案的需取消
            TransferCancelExecutor exe = new TransferCancelExecutor(t);
            exe.execute();
        }

        BusActionLogService.recordBusinessLog("业务单取消",String.valueOf(transfer.getTransferId()),"Transfer",
                "转诊单["+transfer.getTransferId()+"]被取消");
        sendDocTransferCancelMsg(updatedTransfer);
    }
    /**
     * 转诊申请超过24小时未处理，自动取消
     *
     * @author luf
     */
    @RpcService
    public void cancelOverTimeWithPush() {
        Date cancelTime = new Date();
        Date yesterdayDate = DateConversion.getDaysAgo(1);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date yesterday = DateConversion.getDateByTimePoint(yesterdayDate,
                timePoint);
        List<Transfer> transfers = this
                .findTransfersOverByrequestTime(yesterday);
        for (Transfer transfer : transfers) {
            transfer.setCancelDepart(transfer.getTargetDepart());
            transfer.setCancelDoctor(transfer.getTargetDoctor());
            transfer.setCancelOrgan(transfer.getTargetOrgan());
            transfer.setCancelCause("对方医生超过24小时未答复，系统自动取消");
            transfer.setCancelTime(DateConversion
                    .convertFromDateToTsp(cancelTime));
            transfer.setTransferStatus(9);
            update(transfer);
            asynDoBussService.fireEvent(new BussCancelEvent(transfer.getTransferId(),BussTypeConstant.TRANSFER));
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                    .pushMsgData2Ons(transfer.getTransferId(), transfer.getRequestOrgan(), "DocTransferAutoCancel", "DocTransferAutoCancel", transfer.getDeviceId());

        }
    }

    /**
     * 供cancelOverTimeWithPush调用
     *
     * @return
     * @author luf
     */
    @DAOMethod(sql = "From Transfer where transferStatus=0 and requestTime<=:yesterdayDate and payflag=1 and requestMpi is null")
    public abstract List<Transfer> findTransfersOverByrequestTime(
            @DAOParam("yesterdayDate") Date yesterdayDate);

//    /**
//     * 转诊接收成功后取消给目标医生推送消息
//     *
//     * @param transfer 转诊单信息
//     * @author zhangx
//     * @date 2015-11-25 下午12:59:41
//     */
//    public void pushMsgForCancelToTargetDoc(AppointRecord ar) {
//        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
//        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
//
//        Doctor doc = doctorDAO.getByDoctorId(ar.getDoctorId());
//        Doctor reqDoc = doctorDAO.getByDoctorId(Integer.valueOf(ar
//                .getAppointUser()));
//
//        Integer transferId = ar.getTransferId();
//        Doctor targetDoc = doctorDAO.get(transferDAO.get(transferId).getTargetDoctor());
//        Boolean teams = targetDoc.getTeams() == null ? false : targetDoc.getTeams();
//        Boolean patientRequest = false;
//
//
//        String reqOrganName = "";
//        try {
//            reqOrganName = DictionaryController.instance()
//                    .get("eh.base.dictionary.Organ")
//                    .getText(ar.getAppointOragn());
//        } catch (ControllerException e) {
//            log.error(e.getMessage());
//        }
//
//        if (doc == null) {
//            return;
//        }
//        String docTel = doc.getMobile();
//
//        if (StringUtils.isEmpty(docTel)) {
//            return;
//        }
//
//        String title = "转诊取消提醒";
//        String detailMsg = reqOrganName + reqDoc.getName() + "向您发起的转诊申请由于"
//                + ar.getCancelResean().trim() + "原因，已被对方取消";
//
//        // 申请医生新增系统信息
//        SessionDetailService detailService = new SessionDetailService();
//        detailService.addSysTextMsgTransferToTarDoc(transferId, docTel, title, detailMsg, teams, true);
////        addMsgDetail(transferId, 1, docTel, "text", title, detailMsg, "", false);
//
//        // 根据申请医生设备信息，选择消息推送方式
//        String msg = "您有一条转诊申请已被取消";
//        HashMap<String, Object> msgCustomToTarget = CustomContentService.getTransferCustomContentToTarget(transferId, patientRequest, teams);
//        MsgPushService.pushMsgToDoctor(docTel, msg, msgCustomToTarget);
//    }

    /**
     * @param transferResult 1接收 2拒绝
     * @param transferId
     * @return void
     * @function 根据transferId更新转诊结果
     * @author zhangjr
     * @date 2015-12-9
     */
    @RpcService
    @DAOMethod
    public abstract void updateTransferResultByTransferId(
            Integer transferResult, Integer transferId);

    /**
     * @return void
     * @throws DAOException
     * @function 患者转诊申请，超过48小时自动拒绝
     * @author zhangjr
     * @date 2015-12-9
     */
    @RpcService
    public void autoDenyByOvertime() throws DAOException {
        Date cancelTime = new Date();
        String cancelReason = "医生超过48小时未回复，该条特需预约申请已自动结束";
        //SessionDetailDAO sessionDetailDAO = DAOFactory.getDAO(SessionDetailDAO.class);
        // 获取超时转诊记录
        List<Transfer> transfers = this.findByTranResultWithReqTime();
        for (Transfer transfer : transfers) {
            transfer.setRefuseCause(cancelReason);
            transfer.setAgreeTime(DateConversion.convertFromDateToTsp(cancelTime));
            transfer.setTransferStatus(2);// 审核完成
            transfer.setTransferResult(2);// 结果为拒绝
            transfer.setRefuseFlag(0);// 自动拒绝
            update(transfer);
            Integer transferId = transfer.getTransferId();

            // 发起退款
//			WxRefundService service = new WxRefundService();
//			service.refund(transfer.getTransferId(), "transfer");
            WxRefundExecutor executor = new WxRefundExecutor(transferId, "transfer");
            executor.execute();
            //首页任务取消
            asynDoBussService.fireEvent(new BussCancelEvent(transferId,BussTypeConstant.TRANSFER));
            //消息改造 add by houxr
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(transferId);
            smsInfo.setBusType("PatTransferOvertimeAutoDeny");//患者转诊申请，超过48小时自动拒绝
            smsInfo.setSmsType("PatTransferOvertimeAutoDeny");
            smsInfo.setOrganId(transfer.getTargetOrgan());
            smsInfo.setClientId(transfer.getDeviceId());
            smsInfo.setExtendValue(null);
            SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);

            /**
             * 消息改造后注释 add by houxr
             // desc_2016.3.8 给目标医生发送系统消息 zjr
             // 增加系统提醒消息
             Patient patien = patientDAO.getByMpiId(transfer.getMpiId());
             Doctor targetDoctor = doctorDAO.getByDoctorId(transfer.getTargetDoctor());
             String title = "特需预约自动取消提醒";
             String detailMsg = "由于您超过48小时未处理，患者" + patien.getPatientName()
             + "向您发起的特需预约申请已自动取消。";
             Boolean teams = targetDoctor.getTeams();
             if (teams != null && teams) {
             // 找到该团队里的所有医生成员，推送系统消息
             List<DoctorGroup> groupList = groupDao.findByDoctorId(transfer.getTargetDoctor());
             for (DoctorGroup doctorGroup : groupList) {
             Doctor doctor = doctorDAO.getByDoctorId(doctorGroup.getMemberId());
             detailService.addSysTextMsgPatientTransToTarDoc(transferId, doctor.getMobile(), title, detailMsg, teams, true);
             //                    sessionDetailDAO.addMsgDetail(transfer.getTransferId(), 1,
             //                            1, doctor.getMobile(), "text", title, detailMsg,
             //                            "", true);
             }
             } else {
             detailService.addSysTextMsgPatientTransToTarDoc(transferId, targetDoctor.getMobile(), title, detailMsg, teams, true);
             //                sessionDetailDAO.addMsgDetail(transfer.getTransferId(), 1, 1,
             //                        targetDoctor.getMobile(), "text", title, detailMsg, "",
             //                        true);
             }

             //2016-6-3 luf:添加特需预约超时拒绝后给患者发微信推送
             String organName = DAOFactory.getDAO(OrganDAO.class).getByOrganId(transfer.getTargetOrgan()).getShortName();
             String deptName = DAOFactory.getDAO(DepartmentDAO.class).getNameById(transfer.getTargetDepart());
             String doctorName = targetDoctor.getName();
             //模板的fisrt部分内容，      若无，则置空或者null
             String first = "您的特需预约已被拒绝！";
             //模板的remark部分内容，     若无，则置空或者null
             String remark = "感谢您使用纳里健康~";
             //模板的关键字列表，         必须按顺序依次填写
             String keyword1 = organName;
             String keyword2 = deptName;
             String keyword3 = doctorName + "\n就诊人姓名：" + patien.getPatientName();
             String keyword4 = DateConversion.getDateFormatter(transfer.getRequestTime(),
             "yyyy-MM-dd HH:mm")+"\n失败原因："+"医生48小时内未回复，特需预约单已自动拒绝。";
             //            String keyword5 = "医生48小时内未回复，特需预约单已自动拒绝。";
             Map<String, String> kvMap = new HashMap<>();
             kvMap.put("module", "appoint");
             kvMap.put("aid", transfer.getTransferId() + "");
             kvMap.put("special", "1");
             WxTemplateMsg msg = new WxTemplateMsg();
             msg.setKeywordType(KeywordType.KEYWORDS_WITH_CALLBACK_LINK);
             msg.setTemplateKey(ConsultConstant.WX_TEMPLATE_KEY_PATIENT_TRANSFER_FAIL);
             msg.setFirst(first);
             msg.setRemark(remark);
             msg.setKeywords(new String[]{keyword1, keyword2, keyword3, keyword4});
             msg.setKvMap(kvMap);
             Util.sendWxOrXingeMsgToPatientForTransfer(transfer, msg);
             log.info("patientTransferRecord[" + transfer.getTransferId() + "]特需预约超时拒绝并发送微信推送消息");
             **/
        }
    }

    /**
     * @return List<Transfer>
     * @function 查找超过48小时未处理的特需预约
     * @author zhangjr
     * @date 2015-12-9
     * @desc zhangjr 2016_03_04 添加处理中条件
     */
    @RpcService
    @DAOMethod(sql = "From Transfer where (TransferStatus = 0 or TransferStatus = 1) and TIMESTAMPDIFF(HOUR,requestTime,now())>=48 and payflag=1 and requestMpi is not null")
    public abstract List<Transfer> findByTranResultWithReqTime();

    /**
     * 根据商户订单号 查询转诊单信息
     *
     * @param tradeNo
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract Transfer getByOutTradeNo(String tradeNo);

    /**
     * 支付成功后更新支付标志
     *
     * @param tradeNo
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set payflag=1 , paymentDate=:paymentDate, tradeNo=:tradeNo, outTradeNo=:outTradeNo where transferId=:transferId")
    public abstract void updatePayFlagByOutTradeNo(
            @DAOParam("paymentDate") Date paymentDate,
            @DAOParam("tradeNo") String tradeNo,
            @DAOParam("outTradeNo") String outTradeNo,
            @DAOParam("transferId") Integer transferId);

    /**
     * @param payflag
     * @param outTradeNo
     * @return void
     * @function 根据商户号更新业务表支付状态
     * @author zhangjr
     * @date 2015-12-28
     */
    @RpcService
    @DAOMethod(sql = "update Transfer set payflag=:payflag where outTradeNo=:outTradeNo")
    public abstract void updateSinglePayFlagByOutTradeNo(
            @DAOParam("payflag") int payflag,
            @DAOParam("outTradeNo") String outTradeNo);

    /**
     * @param mpiId
     * @param start
     * @param pageSize
     * @return List<Map<String,Object>>
     * @function 获取我的特需预约列表 纳里健康调用
     * @author zhangjr
     * @date 2015-12-16
     */
    @RpcService
    public List<Map<String, Object>> findPatTransferList(final String mpiId, final int start, final int pageSize) {
        HibernateStatelessResultAction<List<Transfer>> action = new AbstractHibernateStatelessResultAction<List<Transfer>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                StringBuilder hql = new StringBuilder(
                        "from Transfer where (requestMpi = :mpiid and payflag != 0) ");//患者端特需预约
                hql.append(" or (mpiId = :mpiid and refuseflag is null ");//不是医生拒绝和系统自动拒绝的
                hql.append(" and transferStatus = 2 ");// 转诊成功
                hql.append(" and IsAdd=1 and transferType<>3");//加号转诊非远程转诊
                hql.append(" and transferResult=1 ) ");//加号转诊已接收
                hql.append(" order by requestTime desc ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiid", mpiId);
                q.setMaxResults(pageSize);
                q.setFirstResult(start);
                List<Transfer> as = q.list();
                setResult(as);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (Transfer transfer : action.getResult()) {
            OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
            Map<String, Object> map = new HashMap<>();
            Transfer tr = new Transfer();
            tr.setTransferId(transfer.getTransferId());
            tr.setTransferPrice(transfer.getTransferPrice() == null ? 0.00 : transfer.getTransferPrice());//医生设置的转诊费用
            tr.setClinicPrice(transfer.getClinicPrice() == null ? 0.00 : transfer.getClinicPrice());//诊疗费用
            tr.setTransferResult(transfer.getTransferResult());
            tr.setTransferStatus(transfer.getTransferStatus());// 预约状态
            tr.setEvaStatus(transfer.getEvaStatus());//评价状态
            tr.setConfirmClinicTime(transfer.getConfirmClinicTime());
            tr.setConfirmClinicAddr(StringUtils.isEmpty(transfer.getConfirmClinicAddr())?transfer.getConfirmOrgan()==null?transfer.getConfirmClinicAddr():organDAO.getNameById(transfer.getConfirmOrgan()):transfer.getConfirmClinicAddr());
            tr.setConfirmDepart(transfer.getConfirmDepart());
            tr.setConfirmOrgan(transfer.getConfirmOrgan());
            tr.setPatientRequire(transfer.getPatientRequire());
            tr.setTargetDoctor(transfer.getTargetDoctor());
            tr.setTargetOrgan(transfer.getTargetOrgan());
            tr.setTransferResultType(transfer.getTransferResultType());//标记是否是住院转诊记录
            if(transfer.getTransferResult() != null && transfer.getTransferResult() == 1
                    && transfer.getTransferResultType()!=null && transfer.getTransferResultType()==1) {
                AppointRecord appointRecord = DAOFactory.getDAO(AppointRecordDAO.class).getByTransferId(tr.getTransferId());
                tr.setAppointRecordStatus(appointRecord.getAppointStatus());
            }
            tr.setRequestTimeString(DateConversion.convertRequestDateForBuss(transfer.getRequestTime()));
            map.put("transfer", tr);

            // 医生信息
            Doctor doctor = doctorDAO.getByDoctorId(transfer.getRequestMpi() != null ? transfer.getTargetDoctor() : transfer.getConfirmDoctor());
            Doctor doc = new Doctor();
            doc.setDoctorId(doctor.getDoctorId());
            doc.setName(doctor.getName());
            doc.setPhoto(doctor.getPhoto());// 头像
            doc.setProfession(doctor.getProfession());// 专科
            doc.setProTitle(doctor.getProTitle());// 职称
            doc.setGender(doctor.getGender());
            doc.setProTitleImage(doctor.getProTitleImage());
            doc.setOrgan(doctor.getOrgan());
            doc.setTeams(doctor.getTeams());
            map.put("doctor", doc);

            mapList.add(map);
        }
        return mapList;
    }

    /**
     * @param transferId
     * @return Map<String,Object>
     * @function 患者特需预约详情查询[包括住院转诊]
     * @author zhangjr
     * @date 2015-12-16
     */
    @RpcService
    public Map<String, Object> getPatTransferById(final int transferId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Transfer tr = getById(transferId);
        // 如果处于待处理或处理中，则计算接收倒计时间
        Integer transferStatus = null;
        if (tr != null && tr.getTransferStatus() != null) {
            transferStatus = tr.getTransferStatus();
        }
        Integer transferResult = null;
        if (tr != null && tr.getTransferResult() != null) {
            transferResult = tr.getTransferResult();
        }
        if (transferStatus!=null&&(transferStatus == 0 || transferStatus == 1)) {
            int remainHours = DateConversion.getHoursDiffer(tr.getRequestTime(), new Date(), 48);
            tr.setRemainTime(String.valueOf(remainHours));
        }

        //transferStatus:0待处理 1处理中 9已取消 2审核完成  transferResult:1接收 2拒绝
        if (transferStatus!=null&&transferResult!=null&&transferStatus == 2 && transferResult ==1) {
            // 获取就诊科室
            if (ObjectUtils.nullSafeEquals(tr.getTransferResultType(), 2)) {//住院转诊获取就诊科室
                DepartmentDAO departDAO = DAOFactory.getDAO(DepartmentDAO.class);
                Department appointdepart = departDAO.getById(tr.getConfirmDepart());
                tr.setAppointDepartName(appointdepart.getName());
                tr.setProfessionCode(appointdepart.getProfessionCode());
            } else {//门诊加号转诊
                DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
                Doctor doctor=doctorDAO.getByDoctorId(tr.getConfirmDoctor());
                if(doctor.getVirtualDoctor()) {//虚拟医生自动接收
                    DepartmentDAO departDAO = DAOFactory.getDAO(DepartmentDAO.class);
                    Department appointdepart = departDAO.getById(tr.getConfirmDepart());
                    tr.setAppointDepartName(appointdepart.getName());
                    tr.setProfessionCode(appointdepart.getProfessionCode());
                }else{
                    AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                    AppointDepart appointdepart = appointDepartDAO.getByOrganIDAndAppointDepartCode(tr.getConfirmOrgan(), tr.getAppointDepartId());
                    if (appointdepart != null) {
                        tr.setAppointDepartName(appointdepart.getAppointDepartName());
                        tr.setProfessionCode(appointdepart.getProfessionCode());
                    } else {
                        tr.setAppointDepartName("");
                        tr.setProfessionCode("");
                    }
                }
            }

        }
        //获取评价信息
        if (tr != null && tr.getEvaStatus() != null) {
            if (ObjectUtils.nullSafeEquals(2, tr.getEvaStatus())) {
                UserSevice userSevice = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
                String mpiId = tr.getRequestMpi() == null ? tr.getMpiId() : tr.getRequestMpi();
                int requestUrtId = userSevice.getUrtIdByUserId(patientDAO.get(mpiId).getLoginId(), "patient");
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                tr.setFeedbackId(evaDao.findEvaByServiceAndUser(tr.getConfirmDoctor(), EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER,
                        tr.getTransferId().toString(), requestUrtId, EvaluationConstant.EVALUATION_USERTYPE_PATIENT).get(0).getFeedbackId());
            }
        }
        Map<String, Object> map = new HashMap<>();
        /*  暂时不用，后期可能启用
        SeeADoctorOrgan sadOrgan = null;
        if(ValidateUtil.notNullAndZeroInteger(tr.getConfirmOrgan())) {
            try {
                sadOrgan = SeeADoctorController.instance().get(String.valueOf(tr.getConfirmOrgan()));
            } catch (ControllerException e) {
                log.error(LocalStringUtil.format("getPatTransferById get organ"));
            }
            if (sadOrgan != null && sadOrgan.isConnectHisCallNumSystem()) {
                tr.setConnectCallNumberSystem(true);
            } else {
                tr.setConnectCallNumberSystem(false);
            }
        }*/
        if(tr!=null) {
            Integer request = tr.getRequestDoctor();
            //医生给患者特需预约标记
            if (request != null) {
                if (!ObjectUtils.isEmpty(request)) {
                    tr.setStatusName("doc");
                }
            }

        }

        //叫号提醒
        if (tr!=null&&tr.getConfirmOrgan() != null) {
            HisServiceConfig hisServiceConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(tr.getConfirmOrgan());
            if (ObjectUtils.isEmpty(hisServiceConfig)) {
                tr.setConnectCallNumberSystem(false);
            } else {
                Integer connectCallNumberSystem = hisServiceConfig.getCallNum();
                tr.setConnectCallNumberSystem(connectCallNumberSystem == null ? false : connectCallNumberSystem != 0);
            }
        }

        // 转诊记录
        map.put("transfer", tr);

        //判断是否是 住院转诊 有号转诊
        if(tr!=null){
            if(ObjectUtils.nullSafeEquals(tr.getTransferResultType(),2)){//住院转诊接受
                AppointInhosp ar = DAOFactory.getDAO(AppointInhospDAO.class).getByTransferId(tr.getTransferId());
                if (ar != null) {
                    map.put("appointInhospId", ar.getAppointInHospId());
                    map.put("appointInhosp", ar);
                }
            }else {
                AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).getByTransferId(tr.getTransferId());
                if (ar != null) {
                    map.put("appointRecordId", ar.getAppointRecordId());
                }
            }
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (tr!=null&&(!ObjectUtils.isEmpty(tr.getRequestDoctor()))) {
            //申请医生信息
            Doctor requestDoctor = doctorDAO.getByDoctorId(tr.getRequestDoctor());
            Doctor requestDoc = new Doctor();
            requestDoc.setDoctorId(requestDoctor.getDoctorId());
            requestDoc.setName(requestDoctor.getName());
            requestDoc.setPhoto(requestDoctor.getPhoto());// 头像
            requestDoc.setProfession(requestDoctor.getProfession());// 专科
            requestDoc.setProTitle(requestDoctor.getProTitle());// 职称
            requestDoc.setGender(requestDoctor.getGender());
            requestDoc.setProTitleImage(requestDoctor.getProTitleImage());
            requestDoc.setOrgan(requestDoctor.getOrgan());
            requestDoc.setTeams(requestDoctor.getTeams());
            map.put("requestDoctor", requestDoc);
        }

        // 审核前：目标医生信息 审核后：接收确认医生信息
        if(tr!=null){
            Doctor doctor = doctorDAO.getByDoctorId(tr.getConfirmDoctor() == null ? tr.getTargetDoctor() : tr.getConfirmDoctor());
            Doctor doc = new Doctor();
            doc.setDoctorId(doctor.getDoctorId());
            doc.setName(doctor.getName());
            doc.setPhoto(doctor.getPhoto());// 头像
            doc.setProfession(doctor.getProfession());// 专科
            doc.setProTitle(doctor.getProTitle());// 职称
            doc.setGender(doctor.getGender());
            doc.setProTitleImage(doctor.getProTitleImage());
            doc.setOrgan(doctor.getOrgan());
            doc.setTeams(doctor.getTeams());
            map.put("doctor", doc);
        }


        // 病人信息
        if(tr!=null){
            Patient patient = patientDAO.getByMpiId(tr.getMpiId());
            Patient pat = new Patient();
            pat.setMpiId(patient.getMpiId());
            pat.setPatientSex(patient.getPatientSex());
            pat.setPatientName(patient.getPatientName());
            pat.setAge(patient.getBirthday() == null ? 0 : DateConversion.getAge(patient.getBirthday()));
            pat.setPatientType(patient.getPatientType());
            pat.setPhoto(patient.getPhoto());
            pat.setBirthday(patient.getBirthday());
            map.put("patient", pat);
        }

        // 申请人信息
        if(tr!=null){
            String reqMpi = tr.getRequestMpi();
            if (!StringUtils.isEmpty(reqMpi)) {
                Patient reqPatient = patientDAO.getByMpiId(tr.getRequestMpi());
                Patient reqPat = new Patient();
                reqPat.setMpiId(reqPatient.getMpiId());
                reqPat.setPatientName(reqPatient.getPatientName());
                reqPat.setPhoto(reqPatient.getPhoto());
                reqPat.setPatientSex(reqPatient.getPatientSex());
                map.put("reqPatient", reqPat);
            }

        }

        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(1, transferId);

        map.put("cdrOtherdocs", cdrOtherdocs);

        //转诊审核医生 //转诊审核医生 如果是特需预约团队 转诊确认医生 confirmDoctor
        if (null != tr && null != tr.getAgreeDoctor() && tr.getAgreeDoctor() > 0) {
            Doctor exe = doctorDAO.get(tr.getAgreeDoctor());
            Doctor d = new Doctor();
            d.setDoctorId(exe.getDoctorId());
            d.setName(exe.getName());
            d.setPhoto(exe.getPhoto());
            d.setProTitle(exe.getProTitle());
            d.setOrgan(exe.getOrgan());
            d.setGender(exe.getGender());
            d.setProfession(exe.getProfession());
            d.setDepartment(exe.getDepartment());
            map.put("agreeDoctor", d);
        }

        return map;
    }

    /**
     * @param payflag
     * @return List<Transfer>
     * @function 根据支付状态查询转诊列表
     * @author zhangjr
     * @date 2015-12-23
     */
    @RpcService
    @DAOMethod
    public abstract List<Transfer> findByPayflag(int payflag);

    /**
     * @param trans
     * @param otherDocs
     * @param payWay    支付方式
     * @return Map<String,Object>
     * @throws Exception
     * @function 患者转诊申请提交并发起支付
     * @author zhangjr
     * @date 2016-1-11
     */
    @RpcService
    public Map<String, Object> submitTransferAndPay(final Transfer trans,
                                                    final List<Otherdoc> otherDocs, String payWay) throws Exception {
        try{
            // 患者转诊申请提交
            Transfer transfer = createPatientTransferAndOtherDoc(trans, otherDocs);
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            callbackParamsMap.put("price", transfer.getClinicPrice()==null?"0":String.valueOf(transfer.getClinicPrice()));
            Map<String, Object> map = payService.immediatlyPayForBus(payWay, BusTypeEnum.TRANSFER.getCode(), transfer.getTransferId(), transfer.getTargetOrgan(), callbackParamsMap);

            return map;
        }catch (Exception e){
            log.error(LocalStringUtil.format("[{}] submitTransferAndPay error, with params:trans[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(trans), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(e.getMessage());
        }
    }

    /**
     * 获取转诊单详情(原getTransferAndAppointById)
     * <p>
     * eh.bus.dao
     *
     * @param transferId 转诊单号
     * @param doctorId   当前登录医生内码
     * @return TransferAndPatient
     * @author luf 2016-2-17
     */
    @RpcService
    public TransferAndPatient getTransferAndAppointByIdNew(int transferId,
                                                           int doctorId) {
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        TransferAndPatient tap = this.getTransferAndAppointById(transferId);
        //获取转诊申请时间天数
        OrganConfig organConfig=organConfigDAO.getByOrganId(tap.getTargetDoctor().getOrgan());
        if(organConfig.getApplyReferralDateNum()!=null){
            tap.setApplyReferralDateNum(organConfig.getApplyReferralDateNum());
        }
        else{
            tap.setApplyReferralDateNum(0);
        }

        if (tap == null || tap.getPatient() == null) {
            return tap;
        }
        Patient patient = tap.getPatient();
        String mpiId = patient.getMpiId();

        patient.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            patient.setRelationPatientId(rd.getRelationDoctorId());
            patient.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                patient.setSignFlag(true);
            } else {
                patient.setSignFlag(false);
            }
            patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }
        tap.setPatient(patient);

        // Integer isAdd = 1;// 是否加号转诊-0有号，1加号
        // AppointRecordDAO arDao = DAOFactory.getDAO(AppointRecordDAO.class);
        // List<AppointRecord> ars = arDao.findByTransferId(transferId);
        // if (ars != null && ars.size() > 0) {
        // AppointRecord ar = ars.get(0);
        // Integer asId = ar.getAppointSourceId();
        // if (asId > 0) {
        // isAdd = 0;
        // }
        // }
        // tap.getTransfer().setIsAdd(isAdd);
        return tap;
    }

    /**
     * 获取住院转诊单详情(原getInhospTransfeById)
     * <p>
     * eh.bus.dao
     *
     * @param transferId 转诊单号
     * @param doctorId   当前登录医生内码
     * @return TransferAndInhosp
     * @author luf 2016-2-17
     * @desc 2016.3.11 将返回结果类型改为map类型 zjr
     */
    @RpcService
    public Map<String, Object> getInhospTransfeByIdNew(int transferId,
                                                       int doctorId) {
        Map<String, Object> map = new HashMap<String, Object>();
        TransferAndInhosp tai = this.getInhospTransfeById(transferId);
        if (tai == null || StringUtils.isEmpty(tai.getMpiId())) {
            return map;
        }
        map.put("transfer", tai.getTransfer());
        map.put("appointInhosp", tai.getAppointInhosp());
        map.put("requestDoctor", tai.getRequestDoctor());
        map.put("targetDoctor", tai.getTargetDoctor());
        map.put("cdrOtherdocs", tai.getCdrOtherdocs());

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        String mpiId = tai.getMpiId();
        Patient patient = patientDAO.get(mpiId);
        patient.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));

        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            patient.setRelationPatientId(rd.getRelationDoctorId());
            patient.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                patient.setSignFlag(true);
            } else {
                patient.setSignFlag(false);
            }
            patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }
        map.put("patient", patient);
        // 添加执行医生信息 2016.3.9 zjr
        Integer exeDoctorId = tai.getTransfer().getConfirmDoctor();
        if (StringUtils.isEmpty(exeDoctorId) == false) {
            Doctor exeDoctor = doctorDAO.getByDoctorId(exeDoctorId);
            map.put("exeDoctor", exeDoctor);
        }
        //获取转诊申请时间天数
        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        Doctor doctor= doctorDAO.getByDoctorId(doctorId);
        OrganConfig organConfig=organConfigDAO.getByOrganId(doctor.getOrgan());
        if(organConfig.getApplyReferralDateNum()!=null){
            map.put("applyReferralDateNum",organConfig.getApplyReferralDateNum());
        }
        else{
            map.put("applyReferralDateNum",0);
        }
        return map;
    }
    /**
     *获取转诊申请时间天数，安卓使用
     */
    @RpcService
    public int getApplyReferralDateNum(int organId) {
        int applyReferralDateNum=0;
        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        //获取转诊申请时间天数
        OrganConfig organConfig=organConfigDAO.getByOrganId(organId);
        if(organConfig.getApplyReferralDateNum()!=null){
            applyReferralDateNum=organConfig.getApplyReferralDateNum();
        }
       return applyReferralDateNum;
    }

    /**
     * 患者预约详情查询-添加标签名等(原 getPatTransferById)
     * <p>
     * eh.bus.dao
     *
     * @param transferId
     * @return Map<String,Object>
     * @author luf 2016-3-1
     */
    @RpcService
    public Map<String, Object> getPatTransferByIdNew(int transferId,
                                                     int doctorId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Transfer tr = getById(transferId);
        // 如果处于待处理或处理中，则计算接收倒计时间
        Integer transferStatus = tr.getTransferStatus();
        Integer transferResult = tr.getTransferResult();
        if (transferStatus == 0 || transferStatus == 1) {
            int remainHours = DateConversion.getHoursDiffer(
                    tr.getRequestTime(), new Date(), 48);
            tr.setRemainTime(String.valueOf(remainHours));
        }

        if (transferStatus != 0 && transferStatus != 1 && transferStatus != 9
                && !(transferStatus == 2 && transferResult == 2)) {
            // 获取就诊科室
            AppointDepartDAO appointDepartDAO = DAOFactory
                    .getDAO(AppointDepartDAO.class);
            AppointDepart appointdepart = appointDepartDAO
                    .getByOrganIDAndAppointDepartCode(tr.getConfirmOrgan(),
                            tr.getAppointDepartId());
            tr.setAppointDepartName(appointdepart.getAppointDepartName());
            tr.setProfessionCode(appointdepart.getProfessionCode());
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Boolean teams = doctorDAO.getByDoctorId(tr.getTargetDoctor())
                .getTeams();
        tr.setTeams(teams);
        //获取评价信息
        if(ObjectUtils.nullSafeEquals(2,tr.getEvaStatus())){
            UserSevice userSevice=AppContextHolder.getBean("eh.userSevice",UserSevice.class);
            String mpiId=tr.getRequestMpi()==null?tr.getMpiId():tr.getRequestMpi();
            int requestUrtId = userSevice.getUrtIdByUserId(patientDAO.get(mpiId).getLoginId(),
                    "patient");
            EvaluationDAO evaDao= DAOFactory.getDAO(EvaluationDAO.class);
            tr.setFeedbackId(evaDao.findEvaByServiceAndUser(tr.getConfirmDoctor(),EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER,
                    String.valueOf(tr.getTransferId()),requestUrtId, EvaluationConstant.EVALUATION_USERTYPE_PATIENT).get(0).getFeedbackId());
        }
        Map<String, Object> map = new HashMap<>();

        // 转诊记录
        map.put("transfer", tr);

        // 医生信息

        Doctor doctor = doctorDAO.getByDoctorId(tr.getTargetDoctor());
        Doctor doc = new Doctor();
        doc.setDoctorId(doctor.getDoctorId());
        doc.setName(doctor.getName());
        doc.setPhoto(doctor.getPhoto());// 头像
        doc.setProfession(doctor.getProfession());// 专科
        doc.setProTitle(doctor.getProTitle());// 职称
        doc.setGender(doctor.getGender());
        doc.setProTitleImage(doctor.getProTitleImage());
        doc.setOrgan(doctor.getOrgan());
        doc.setTeams(doctor.getTeams());
        map.put("doctor", doc);

        // 病人信息
        String mpiId = tr.getMpiId();
        Patient patient = patientDAO.getByMpiId(mpiId);
        Patient pat = new Patient();
        pat.setMpiId(mpiId);
        pat.setPatientSex(patient.getPatientSex());
        pat.setPatientName(patient.getPatientName());
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        pat.setPatientType(patient.getPatientType());
        pat.setPhoto(patient.getPhoto());
        pat.setBirthday(patient.getBirthday());

        // 申请人信息
        String reqMpi = tr.getRequestMpi();
        if (!StringUtils.isEmpty(reqMpi)) {
            Patient reqPatient = patientDAO.getByMpiId(tr.getRequestMpi());
            Patient reqPat = new Patient();
            reqPat.setMpiId(reqPatient.getMpiId());
            reqPat.setPatientName(reqPatient.getPatientName());
            reqPat.setPhoto(reqPatient.getPhoto());
            reqPat.setPatientSex(reqPatient.getPatientSex());
            map.put("reqPatient", reqPat);
        }

        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            pat.setRelationPatientId(rd.getRelationDoctorId());
            pat.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                pat.setSignFlag(true);
            } else {
                pat.setSignFlag(false);
            }
            pat.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }
        map.put("patient", pat);

        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(1, transferId);

        map.put("cdrOtherdocs", cdrOtherdocs);
        return map;
    }

    /**
     * 转诊开始服务(添加提示)-原生端
     * <p>
     * eh.bus.dao
     *
     * @param transferId  转诊单号
     * @param agreeDoctor 当前登录医生内码
     * @return Boolean
     * @author luf 2016-3-3
     */
    @RpcService
    public Boolean startTransferAddExc(int transferId, int agreeDoctor) {
        Transfer tr = this.get(transferId);
        Integer transferType = tr.getTransferType();
        if (transferType != null && transferType.equals(3)) {
            // 2017-2-6 luf：远程联合门诊转诊调用开始服务抛609（由于此类转诊自动接收不用进行开始操作，前端改动大，后台直接加判断过滤）
            return false;
        }
        Integer status = tr.getTransferStatus();
        String reMpi = tr.getRequestMpi();
        if (status == 9) {
            if (StringUtils.isEmpty(reMpi)) {
//                log.error("转诊开始服务startTransferAddExc==>抱歉，对方医生已取消该转诊申请");
                throw new DAOException(609, "抱歉，对方医生已取消该转诊申请");
            } else {
                throw new DAOException(609, "抱歉，患者已取消该特需预约申请");
            }
        } else if (status >= 1) {
            if (agreeDoctor != tr.getAgreeDoctor()) {
                if (StringUtils.isEmpty(reMpi)) {
//                    log.error("转诊开始服务startTransferAddExc==>抱歉，您团队内已有其他医生提前接收该转诊申请");
                    throw new DAOException(609, "抱歉，您团队内已有其他医生提前接收该转诊申请");
                } else {
                    throw new DAOException(609, "抱歉，您团队内已有其他医生提前接收该特需预约申请");
                }
            } else {
                log.error("重复开始转诊");
                return false;
            }
        }
        String mpi = tr.getMpiId();
        if (StringUtils.isEmpty(mpi)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (SameUserMatching.patientAndDoctor(mpi, agreeDoctor)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者与接收医生不能为同一个人");
        }
        return this.startTransfer(transferId, agreeDoctor);
    }

    /**
     * 转诊拒绝服务(添加提示)-原生端
     * <p>
     * eh.bus.dao
     *
     * @param transferId  转诊单号
     * @param agreeDoctor 当前登录医生
     * @param agreeTime   当前时间
     * @param refuseCause 拒绝原因
     * @author luf 2016-3-3
     */
    @RpcService
    public Boolean refuseTransferAddExc(Integer transferId,
                                        Integer agreeDoctor, Date agreeTime, String refuseCause) {
        Transfer tr = this.get(transferId);
        Integer status = tr.getTransferStatus();
        String reMpi = tr.getRequestMpi();
        if (status == 9) {
            if (StringUtils.isEmpty(reMpi)) {
//                log.error("转诊拒绝服务refuseTransferAddExc==>抱歉，对方医生已取消该转诊申请");
                throw new DAOException(609, "抱歉，对方医生已取消该转诊申请");
            } else {
                throw new DAOException(609, "抱歉，患者已取消该特需预约申请");
            }
        }
        if (status != 1) {
            return false;
        }
        this.refuseTransfer(transferId, agreeDoctor, agreeTime, refuseCause);
        return true;
    }

    /**
     * 转诊接收确认服务(添加提示)-原生
     * <p>
     * eh.bus.dao
     *
     * @param transfer
     * @return Boolean
     * @author luf 2016-3-3
     */
    @RpcService
    public Boolean confirmTransferAddExc(Transfer transfer) {
        log.info("转诊接收确认服务confirmTransferAddExc");
        if (transfer == null || transfer.getTransferId() == null) {
//            log.error("转诊接收确认服务confirmTransferAddExc==>transfer/transferId is required!");
            throw new DAOException(DAOException.VALUE_NEEDED, "transfer/transferId/agreeDoctor is required!");
        }
        Transfer tr = this.get(transfer.getTransferId());
        Integer status = tr.getTransferStatus();
        String reMpi = tr.getRequestMpi();

        // desc_2016.3.7 已被团队其他医生接收过的不能再次被团队医生接收
        if (status == 2) {
            // 2016-8-4 luf:同一个用户多次调用此接口，不给文案，直接返回
            if (tr.getConfirmDoctor().equals(transfer.getConfirmDoctor())) {
                log.error("重复确认转诊");
                return false;
            }
            if (StringUtils.isEmpty(reMpi)) {
//                log.error("转诊接收确认服务confirmTransferAddExc==>抱歉，该转诊单已被团队其他成员接收");
                throw new DAOException(609, "抱歉，该转诊单已被团队其他成员接收");
            } else {
                throw new DAOException(609, "抱歉，您团队内已有其他医生提前接收该特需预约申请");
            }
        }
        if (status == 9) {
            if (StringUtils.isEmpty(reMpi)) {
//                log.error("转诊接收确认服务confirmTransferAddExc==>抱歉，对方医生已取消该转诊申请");
                throw new DAOException(609, "抱歉，对方医生已取消该转诊申请");
            } else {
                throw new DAOException(609, "抱歉，患者已取消该特需预约申请");
            }
        }
        if (status != 1 && StringUtils.isEmpty(reMpi)) { //患者端状态为0待处理也可接收
            return false;
        }
        Integer confirmDoctor = transfer.getConfirmDoctor();
        String mpi = tr.getMpiId();
        if (confirmDoctor == null || confirmDoctor <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "confirmDoctor is required!");
        }
        if (StringUtils.isEmpty(mpi)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (SameUserMatching.patientAndDoctor(mpi, confirmDoctor)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者与接收医生不能为同一个人");
        }

        this.confirmTransfer(transfer);
        return true;
    }

    private void isValidConfirmTransferData(Transfer transfer) {

        if (StringUtils.isEmpty(transfer.getConfirmOrgan())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "confirmOrgan is required");
        }
        if (StringUtils.isEmpty(transfer.getConfirmDoctor())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "confirmDoctor is required");
        }
        if (transfer.getTransferId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "transferId is required");
        }
        if (transfer.getAgreeDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "agreeDoctor is required");
        }
        if (transfer.getTransferResultType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "transferResultType is required");
        }
        if (transfer.getConfirmDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "confirmDepart is required");
        }
        if (StringUtils.isEmpty(transfer.getConfirmClinicAddr())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "confirmClinicAddr is required");
        }
        if (transfer.getClinicPrice() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "clinicPrice is required");
        }
        if (transfer.getSourceLevel() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sourceLevel is required");
        }
        if (StringUtils.isEmpty(transfer.getAppointDepartId())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointDepartId is required");
        }

        if (transfer.getConfirmClinicTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "confirmClinicTime is required");
        }

    }

    /**
     * 住院转诊接收确认服务(添加提示)-原生
     * <p>
     * eh.bus.dao
     *
     * @param transfer
     * @param appointInhosp
     * @return Boolean
     * @author luf 2016-3-3
     */
    @RpcService
    public Boolean confirmTransferWithInHospitalAddExc(Transfer transfer, AppointInhosp appointInhosp) {
        if (transfer == null || transfer.getTransferId() == null) {
//            log.error("住院转诊接收确认服务confirmTransferWithInHospitalAddExc==>transfer/transferId is required!");
            throw new DAOException(DAOException.VALUE_NEEDED, "transfer/transferId/agreeDoctor is required!");
        }

        //判断经过his的转诊接收是否在维护
        new HisRemindService().saveRemindRecordForInHosTransfer(transfer);

        Transfer tr = this.get(transfer.getTransferId());
        Integer status = tr.getTransferStatus();
        if (status == 9) {
//            log.error("住院转诊接收确认服务confirmTransferWithInHospitalAddExc==>抱歉，对方医生已取消该转诊申请");
            throw new DAOException(609, "抱歉，对方医生已取消该转诊申请");
        }
        if (status != 1) {
            return false;
        }
        this.confirmTransferWithInHospital(transfer, appointInhosp);
        return true;
    }

    /**
     * 转诊取消（添加提示）-原生
     *
     * @param transfer 转诊信息
     * @throws DAOException
     * @author luf
     */
    @RpcService
    public Boolean cancelTransferInAddExc(Transfer transfer)
            throws DAOException {
        log.info("转诊取消前端传入数据cancelTransferInAddExc"
                + JSONUtils.toString(transfer));
        Transfer t = this.getById(transfer.getTransferId());
        if (t == null) {
            throw new DAOException(609, "不存在该转诊单");
        }
        if (t.getTransferStatus() > 1) {
            throw new DAOException(609, "医生已答复，您无法取消");
        }
        this.cancelTransferIn(transfer);
        try {
            AppContextHolder.getBean("followUpdateService",FollowUpdateService.class).deleteByAppointRecordId(transfer.getTransferId());
        } catch (Exception e) {
            log.debug("after cancelTransferInAddExc deleteByAppointRecordId faild and transferId is [{}]",transfer.getTransferId());
        }
        return true;
    }

    /**
     * 获取转诊表单信息 修改适用于患者转诊申请详情查询(供运营平台 2.1版本使用)
     *
     * @param transferId 转诊单号
     * @return TransferAndPatient
     * @throws DAOException
     * @author houxr
     * @date 2016-04-27 10:20:12
     */
    @RpcService
    public TransferAndPatient getTransferAndCdrByIdNew(final Integer transferId) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.TransferAndPatient(a,b.patientSex,b.birthday," +
                        "b.photo,b.patientName,b.patientType,b.mobile,b.idcard,b.mpiId) " +
                        "from Transfer a,Patient b where transferId=:transferId and b.mpiId=a.mpiId";
                Query query = ss.createQuery(hql.toString());
                query.setParameter("transferId", transferId);
                TransferAndPatient newTap = (TransferAndPatient) query.list().get(0);

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Integer requestDoctorId = newTap.getTransfer().getRequestDoctor();
                Integer targetDoctorId = newTap.getTransfer().getTargetDoctor();
                Doctor requestDoctor = null;
                String rMobile = null;
                Integer requestBusyFlag = null;

                if (requestDoctorId != null) {
                    requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);
                    rMobile = requestDoctor.getMobile();
                    requestBusyFlag = doctorDAO.getBusyFlagByDoctorId(requestDoctorId);
                } else {
                    String requestMpi = newTap.getTransfer().getRequestMpi();
                    if (!(newTap.getTransfer().getMpiId().equals(requestMpi))) {
                        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                        Patient requestPatient = patientDAO.getPatientByMpiId(requestMpi);
                        newTap.setRequestPatientName(requestPatient.getPatientName());
                        newTap.setRequestPatientTel(requestPatient.getMobile());
                    } else {//患者申请
                        newTap.setRequestPatientName(newTap.getPatientName());
                        newTap.setRequestPatientTel(newTap.getPatientMobile());
                    }
                }
                Doctor targetDoctor = doctorDAO.getByDoctorId(targetDoctorId);
                Boolean groupFlag = targetDoctor.getTeams();
                String tMobile = targetDoctor.getMobile();

                // 获取医生忙闲状态
                Integer exeDoctor = newTap.getTransfer().getConfirmDoctor() == null ? null
                        : newTap.getTransfer().getConfirmDoctor();
                Integer targetBusyFlag = null;
                if (exeDoctor != null) {
                    targetBusyFlag = doctorDAO.getBusyFlagByDoctorId(exeDoctor);
                }
                newTap.setTeams(groupFlag);
                newTap.setRequestDoctorMobile(rMobile);
                newTap.setTargetDoctorMobile(tMobile);
                newTap.setRequestBusyFlag(requestBusyFlag);
                newTap.setTargetBusyFlag(targetBusyFlag);
                newTap.setRequestDoctor(requestDoctor);
                newTap.setTargetDoctor(targetDoctor);
                HealthCardDAO healthCardDao = DAOFactory.getDAO(HealthCardDAO.class);
                // 获取患者医保卡号
                Integer cardOrgan = Integer.parseInt(newTap.getPatientType());
                String cardId = healthCardDao.getMedicareCardId(newTap.getMpiId(), cardOrgan);
                newTap.setCardId(cardId);
                // 获取挂号科室编码(appointDepartCode)
                String departId = newTap.getTransfer().getAppointDepartId();
                if (departId != null) {
                    // 通过 就诊挂号科室编码[appointDepartId] 和 转诊确认机构[ConfirmOrgan]
                    // 获取 就诊挂号科室[就诊科室]
                    AppointDepartDAO AppointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                    AppointDepart appointdepart = AppointDepartDAO
                            .getByOrganIDAndAppointDepartCode(newTap.getTransfer().getConfirmOrgan(), departId);
                    if (appointdepart != null) {
                        String appointDepartName = appointdepart.getAppointDepartName();
                        newTap.getTransfer().setAppointDepartName(appointDepartName);//就诊科室[就诊挂号科室]
                        newTap.getTransfer().setProfessionCode(appointdepart.getProfessionCode());
                    }
                }
                Transfer tran = newTap.getTransfer();
                if (tran != null) {
                    CdrOtherdocDAO cdrOtherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
                    List<Otherdoc> cdrOtherdocs = cdrOtherdocDAO.findByClinicTypeAndClinicId(1, tran.getTransferId());
                    newTap.setCdrOtherdocs(cdrOtherdocs);
                }
                setResult(newTap);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        TransferAndPatient tap = (TransferAndPatient) action.getResult();
        return tap;
    }

//    /**
//     * 给目标医生发推送消息消息
//     *
//     * @param transfer  转诊信息
//     * @param msg       显示信息
//     * @param title     接收消息的标题
//     * @param detailMsg 接收消息的内容
//     * @author luf
//     */
//    public void msgPushWithTransferCancel(Transfer transfer, String msg,
//                                          String title, String detailMsg) {
//        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
//        SessionDetailService detailService = new SessionDetailService();
//
//        Integer transferId = transfer.getTransferId();
//        transfer = get(transferId);
//        Integer doctorId = transfer.getTargetDoctor();
//        Doctor targetDoc = doctorDAO.get(doctorId);
//        Boolean teams = targetDoc.getTeams() == null ? false : targetDoc.getTeams();
//        Boolean patientRequest = false;
//
//        Integer agree = transfer.getAgreeDoctor();
//        List<Integer> ids = new ArrayList<Integer>();
//        if (agree != null && agree > 0) {
//            ids.add(agree);
//        } else if (doctorId != null && doctorId > 0) {
//            DoctorGroupDAO doctorGroupDAO = DAOFactory
//                    .getDAO(DoctorGroupDAO.class);
//            List<DoctorGroup> dgs = doctorGroupDAO
//                    .findByDoctorId(doctorId);
//
//            // 目标医生不是团队医生,直接存放目标医生
//            if (dgs == null || dgs.isEmpty()) {
//                ids.add(doctorId);
//            } else {
//                // 目标医生是团队医生，且没有执行医生，存放团队中所有成员
//                for (DoctorGroup dg : dgs) {
//                    ids.add(dg.getMemberId());
//                }
//            }
//        }
//
//
//        for (Integer id : ids) {
//            String docTel = doctorDAO.getMobileByDoctorId(id);// 目标医生电话
//
//            // 目标医生新增系统信息
//            detailService.addMsgDetailRequestOrNot(transferId, 1, docTel, "text", title, detailMsg, "", true, 1);
//
//            //目标医生发送推送消息
//            HashMap<String, Object> msgCustomToTarget = CustomContentService.getTransferCustomContentToTarget(transferId, patientRequest, teams);
//            MsgPushService.pushMsgToDoctor(docTel, msg, msgCustomToTarget);
//        }
//    }

    /**
     * 通过确认就诊时间查询待转诊医生列表[不包括云门诊转诊]
     *
     * @param sdate
     * @param doctorType 1目标医生 2申请医生
     * @return
     * @date 2016-05-19
     * @author houxr
     */
    public List<Doctor> findTransferDoctorByConfirmClinicTime(final Date sdate, final Integer doctorType) {
        log.info(sdate + " find transfer doctor by " + doctorType);
        if (sdate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "时间不能为空");
        }
        if (doctorType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "医生类型不能为空");
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        final String startTime = df.format(sdate) + " 00:00:00";
        final String endTime = df.format(sdate) + " 23:59:59";
        final Date sTime = new StringToDate().convert(startTime);
        final Date eTime = new StringToDate().convert(endTime);
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select d FROM Doctor d,Transfer t " +
                        "WHERE t.payflag = 1 " +
                        "AND t.transferStatus = 2 and t.transferResult=1 AND t.transferType <> 3 " +
                        "AND t.confirmClinicTime >=:sTime " +
                        "AND t.confirmClinicTime <=:eTime ");
                if (doctorType == 1) {//1目标医生
                    hql.append(" AND d.doctorId = t.confirmDoctor ");
                }
                if (doctorType == 2) {//2申请医生
                    hql.append(" AND d.doctorId = t.requestDoctor ");
                }
                hql.append("GROUP BY t.targetDoctor ORDER BY confirmClinicTime DESC");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("sTime", sTime);
                query.setParameter("eTime", eTime);
                List<Doctor> doctorList = query.list();
                setResult(doctorList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取今日有转诊患者的接收医生列表 1目标医生
     *
     * @return
     */
    public List<Doctor> findTodayTransferByConfirmClinicTime() {
        Date today = Context.instance().get("date.getToday", Date.class);
        List<Doctor> targetDoctorLists = findTransferDoctorByConfirmClinicTime(today, 1);
        return targetDoctorLists;
    }

    /**
     * 获取明日有转诊患者的申请医生列表 2申请医生
     *
     * @return
     */
    public List<Doctor> findTomorrowTransferByConfirmClinicTime() {
        Date tomorrow = Context.instance().get("date.getTomorrow", Date.class);
        List<Doctor> requestDoctorLists = findTransferDoctorByConfirmClinicTime(tomorrow, 2);
        return requestDoctorLists;
    }


    /**
     * 查找目标医生今日就诊的转诊病人列表信息 不包括 远程门诊转诊
     * (我作为医生接收的转诊患者，今日就诊的)
     *
     * @param today
     * @param targetDoctor 目标医生
     * @param start        分页位置
     * @param limit        10条
     * @return
     * @author houxr
     * @date 2016-05-19
     */
    @RpcService
    public List<HashMap<String, Object>> findTodayTransferByTargetDoctor(final Date today,
                                                                         final int targetDoctor,
                                                                         final int start, final int limit) {
        List<HashMap<String, Object>> returnList = findTransferPatientByDay(today, targetDoctor, 1, start, limit);
        return returnList;
    }


    /**
     * 查找申请医生明日就诊的转诊病人列表信息 不包括 远程门诊转诊
     * (我作为医生申请的转诊患者，明日就诊的)
     *
     * @param tomorrow
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @param limit         条数
     * @return
     * @author houxr
     * @date 2016-05-19
     */
    @RpcService
    public List<HashMap<String, Object>> findTomorrowTransferByRequestDoctor(final Date tomorrow,
                                                                             final int requestDoctor,
                                                                             final int start, final int limit) {
        List<HashMap<String, Object>> returnList = findTransferPatientByDay(tomorrow, requestDoctor, 2, start, limit);
        return returnList;
    }

    /**
     * @param day      要查询的日期
     * @param doctorid 医生ID
     * @param type     1目标医生 2申请医生
     * @param start    开始位置
     * @param limit    条数
     * @return
     */
    public List<HashMap<String, Object>> findTransferPatientByDay(Date day,
                                                                  int doctorid, int type,
                                                                  int start, int limit) {
        SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);

        Integer memberType = 1;//1 为医生,2为患者
        Integer publisherId = 1;
        if (1 == type) {//今日目标医生
            publisherId = 2;
        } else {//明日申请医生
            publisherId = 3;
        }

        //更新成未读
        memberDAO.updateUnReadByPublisherIdAndMemberType(publisherId, memberType);

        List<HashMap<String, Object>> returnList = new ArrayList<HashMap<String, Object>>();

        //获取就诊患者的转诊单信息
        List<Transfer> tfList = findTransferDoctorByDoctorId(day, doctorid, type);

        for (Transfer tr : tfList) {
            HashMap<String, Object> map = new HashMap<String, Object>();

            Transfer transfer = new Transfer();
            transfer.setTransferId(tr.getTransferId());
            transfer.setRequestMpi(tr.getRequestMpi());
            map.put("transfer", transfer);

            // 病人信息
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            String mpiId = tr.getMpiId();
            Patient pat = patientDAO.getPartInfo(mpiId, doctorid);
            map.put("patient", pat);

            returnList.add(map);
        }

        return returnList;
    }


    /**
     * 查找目标医生今日就诊的转诊病人列表信息 不包括 远程门诊转诊
     *
     * @param targetDoctor 目标医生id
     * @param start        分页起始位置
     * @param limit        条数
     * @return
     * @author houxr
     * @date 2016-05-19
     */
    @RpcService
    public HashMap<String, Object> findTodayTransferByTargetDoctorAndLimit(final int targetDoctor, final int start, final int limit) {
        Date today = Context.instance().get("date.getToday", Date.class);
        List<Transfer> targetLists = findTransferDoctorByDoctorId(today, targetDoctor, 1);
        List<HashMap<String, Object>> transferPatLists = this.findTodayTransferByTargetDoctor(today, targetDoctor, start, limit);

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("num", targetLists.size());
        map.put("patientList", transferPatLists);

        return map;
    }

    /**
     * 查找申请医生 明日待就诊的转诊患者列表 不包括 远程门诊转诊
     *
     * @param requestDoctor 申请医生
     * @param start         分页起始位置
     * @param limit         10条
     * @return
     * @author houxr
     * @date 2016-05-19
     */
    @RpcService
    public HashMap<String, Object> findTomorrowTransferByRequestDoctorAndLimit(final int requestDoctor, final int start, final int limit) {
        Date tomorrow = Context.instance().get("date.getTomorrow", Date.class);
        List<Transfer> requestLists = findTransferDoctorByDoctorId(tomorrow, requestDoctor, 2);
        List<HashMap<String, Object>> transferPatLists = this.findTomorrowTransferByRequestDoctor(tomorrow, requestDoctor, start, limit);

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("num", requestLists.size());
        map.put("patientList", transferPatLists);
        return map;
    }

    /**
     * 向目标医生推送今日就诊的转诊病人信息
     *
     * @date 2016-05-19
     * @author houxr
     */
    @RpcService
    public void todayPushMessageToTargetDoctorTransferInfo() {
        //目标医生 转诊列表
        List<Doctor> doctorList = this.findTodayTransferByConfirmClinicTime();
        Date tday = Context.instance().get("date.getToday", Date.class);
        for (Doctor doctor : doctorList) {
            List<Transfer> targetLists = this.findTransferDoctorByDoctorId(tday, doctor.getDoctorId(), 1);
            Transfer transfer = targetLists.get(0);
            log.info("toTargetDoctor:" + JSONUtils.toString(targetLists));

            HashMap<String,Integer> map=new HashMap<String,Integer>();
            map.put("num",targetLists.size());

            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(transfer.getTransferId());
            smsInfo.setBusType("TodayClinic");
            smsInfo.setSmsType("TodayClinic");
            smsInfo.setOrganId(doctor.getOrgan());
            smsInfo.setClientId(null);
            smsInfo.setCreateTime(new Date());
            smsInfo.setStatus(0);
            smsInfo.setExtendValue(JSONUtils.toString(map));

            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        }
    }

    /**
     * 向申请医生推送明日就诊的转诊病人信息
     *
     * @date 2016-05-19
     * @author houxr
     */
    @RpcService
    public void tomorrowPushMessageToRequestDoctorTransferInfo() {
        List<Doctor> doctorList = this.findTomorrowTransferByConfirmClinicTime();
        Date tomorrow = Context.instance().get("date.getTomorrow", Date.class);
        for (Doctor doctor : doctorList) {
            List<Transfer> requestLists = this.findTransferDoctorByDoctorId(tomorrow, doctor.getDoctorId(), 2);
            log.info("toRequestDoctor:" + JSONUtils.toString(requestLists));
            Transfer transfer = requestLists.get(0);

            HashMap<String,Integer> map=new HashMap<String,Integer>();
            map.put("num",requestLists.size());

            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(transfer.getTransferId());
            smsInfo.setBusType("TomorrowClinic");
            smsInfo.setSmsType("TomorrowClinic");
            smsInfo.setOrganId(doctor.getOrgan());
            smsInfo.setClientId(null);
            smsInfo.setCreateTime(new Date());
            smsInfo.setStatus(0);
            smsInfo.setExtendValue(JSONUtils.toString(map));

            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);

        }
    }

    /**
     * 获取医生待转诊列表
     *
     * @param sday       日期：今天 明天
     * @param doctorId   医生id
     * @param doctorType 1目标医生 2申请医生
     * @return
     * @date 2016-05-23
     * @author houxr
     */
    public List<Transfer> findTransferDoctorByDoctorId(final Date sday, final int doctorId, final Integer doctorType) {
        log.info(sday + " find transfer doctor by " + doctorId + ",doctorType:" + doctorType);
        if (sday == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "时间不能为空");
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        final String startTime = df.format(sday) + " 00:00:00";
        final String endTime = df.format(sday) + " 23:59:59";
        HibernateStatelessResultAction<List<Transfer>> action = new AbstractHibernateStatelessResultAction<List<Transfer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "from Transfer  where payflag=1 and transferStatus=2 and transferResult=1 " +
                                "and confirmClinicTime >=:startTime and confirmClinicTime <=:endTime " +
                                " and transferType<> 3 ");
                if (doctorType == 1) {//1目标医生
                    hql.append("and confirmDoctor =:doctorId and confirmDoctor is not null ");
                }
                if (doctorType == 2) {//2申请医生
                    hql.append(" and requestDoctor =:doctorId and requestDoctor is not null");
                }
                hql.append(" order by confirmClinicTime desc");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("startTime", DateConversion.getCurrentDate(startTime, "yyyy-MM-dd HH:mm:ss"));
                query.setParameter("endTime", DateConversion.getCurrentDate(endTime, "yyyy-MM-dd HH:mm:ss"));
                query.setParameter("doctorId", doctorId);
                List<Transfer> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询同申请人，同一患者，同一目标医生，同一天申请的有效特需预约记录
     *
     * @param mpiId
     * @param requestMpi
     * @param targetDoctor
     * @param requestTime
     * @return
     * @author luf 2016-7-5 添加状态限制：and ((transferStatus in(0,1,3,4,5,8)) or (transferStatus=2 and transferResult=1))
     */
    @DAOMethod(sql = "From Transfer where mpiId=:mpiId and requestMpi=:requestMpi and targetDoctor=:targetDoctor and DATE_FORMAT(requestTime,'%y-%m-%d')=DATE_FORMAT(:requestTime,'%y-%m-%d') " +
            "and ((transferStatus in(0,1,3,4,5,8)) or (transferStatus=2 and transferResult=1))")
    public abstract List<Transfer> findHasPatientTransfer(
            @DAOParam("mpiId") String mpiId, @DAOParam("requestMpi") String requestMpi,
            @DAOParam("targetDoctor") int targetDoctor, @DAOParam("requestTime") Date requestTime);

    /**
     * 查询所有待处理和处理中的转诊单 transferStatus :0待处理,1处理中
     *
     * @return
     */
    @DAOMethod(limit = 0, sql = "From Transfer where transferStatus in (0,1) and payflag=1 and requestDoctor is not null order by transferId asc")
    public abstract List<Transfer> findAllPendingTransfer();

    /**
     * 按日期将接收成功的转诊单更新为待评价状态（除住院转诊）(患者在平台有帐号)
     * 筛选条件：
     [转诊接收-申请类型为普通门诊转诊，接收类型不为住院转诊]
     [加号转诊]
     [医生申请的转诊且就诊人有账户,患者用户申请的转诊且有账户]
     [转诊接收的就诊时间为指定时间]
     [未评价]
     * @author zhangsl 2017-02-13 16:38:43
     */
    @DAOMethod(sql="update Transfer set evaStatus=1 where evaStatus=0 and date(confirmClinicTime)=date(:workDate) and ((ifNULL(requestMpi,'')='' and mpiId in(select mpiId from Patient where loginId<>'')) or (requestMpi<>'' and requestMpi in(select mpiId from Patient where loginId<>''))) and transferResult=1 and transferStatus=2 and transferType=1 and transferResultType<>2 and IsAdd=1 and ifNull(refuseCause,'')=''")
    abstract public void updateTransferEvaStatusByWorkDate(@DAOParam("workDate") Date workDate);

    /**
     * 按就诊日查询待评价状态转诊单
     * @author zhangsl 2017-02-14 16:38:43
     */
    @DAOMethod(sql="From Transfer where date(confirmClinicTime)=date(:workDate) and evaStatus=1",limit = 0)
    abstract public List<Transfer> findNeedEvaTransferByWorkDate(@DAOParam("workDate") Date workDate);

    /**
     * 根据转诊id更新评价状态为已评价
     * @author zhangsl 2017-02-14 16:38:43
     */
    public Integer updateTransferEvaStatusById(final Integer transferId){
        final HibernateStatelessResultAction<Integer> action=new AbstractHibernateStatelessResultAction<Integer>(){
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql=new StringBuffer("update Transfer set evaStatus=2 where transferId=:transferId and evaStatus=1");
                Query q=ss.createQuery(hql.toString());
                q.setParameter("transferId",transferId);
                Integer count=q.executeUpdate();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 按就诊日查询未评价的待评价转诊单
     * @author zhangsl 2017-02-15 14:11:01
     */
    @DAOMethod(sql="select transferId From Transfer where confirmClinicTime<:senvenDate and confirmClinicTime>:startDate and evaStatus=1",limit = 0)
    abstract public List<Integer> findOverTimeNeedEvaTransferByWorkDate(@DAOParam("senvenDate") Date senvenDate,
                                                                        @DAOParam("startDate") Date startDate);

    /**
     * 2017-6-2 20:23:39 zhangx 消息优化：将消息拆分成更加细致的场景
     * @param transfer
     */
    private void sendDocTransferApplyMsg(Transfer transfer){
        int transferType=transfer.getTransferType()==null?1:transfer.getTransferType().intValue();
        String busType="";
        //如果是住院转诊
        if(transferType==2){
            busType="DocInHospTransferApply";
        }else{
            busType="DocTransferApply";
        }
        if(StringUtils.isEmpty(busType)){
            log.info("未判断出业务类型，无法发送消息");
            return;
        }
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                .pushMsgData2Ons(transfer.getTransferId(), transfer.getTargetOrgan(), busType, busType, transfer.getDeviceId());
    }

    /**
     * 2017-6-6 20:23:39 zhangx 消息优化：将消息拆分成更加细致的场景
     * @param transfer
     */
    private void sendDocTransferCancelMsg(Transfer transfer){
        int transferType=transfer.getTransferType()==null?1:transfer.getTransferType().intValue();
        String busType="";
        //如果是住院转诊
        if(transferType==2){
            busType="DocInHospTransferCancel";
        }else{
            busType="DocTransferCancel";
        }
        if(StringUtils.isEmpty(busType)){
            log.info("未判断出业务类型，无法发送消息");
            return;
        }
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                .pushMsgData2Ons(transfer.getTransferId(), transfer.getTargetOrgan(), busType, busType, transfer.getDeviceId());
    }

    public void sendConfirmHisFailedMsg(Transfer transfer){

        int organ=transfer.getTargetOrgan()==null?0:transfer.getTargetOrgan().intValue();

        String busType="";
        //如果特需预约
        if(!StringUtils.isEmpty(transfer.getRequestMpi())){
            busType="PatTransferConfirmHisFailed";
        }else{
            busType="DocTransferConfirmHisFailed";
        }
        if(StringUtils.isEmpty(busType)){
            log.info("未判断出业务类型，无法发送消息");
            return;
        }
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class)
                .pushMsgData2Ons(transfer.getTransferId(),organ, busType, busType, transfer.getDeviceId());
    }

}
