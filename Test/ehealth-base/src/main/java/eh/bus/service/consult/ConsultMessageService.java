package eh.bus.service.consult;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.Publisher;
import ctd.net.broadcast.Subscriber;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.converter.ConversionUtils;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.user.UserSevice;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.ConsultMessageDAO;
import eh.bus.service.common.AsyncMsgSenderExecutor;
import eh.bus.service.meetclinic.MeetClinicMessageService;
import eh.bus.service.report.QueryLabReportsDetailService;
import eh.cdr.bean.RecipeTagMsgBean;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.cdr.service.RecipeService;
import eh.entity.base.*;
import eh.entity.bus.*;
import eh.entity.bus.vo.LocalDate;
import eh.entity.bus.vo.WxMsgVo;
import eh.entity.cdr.Otherdoc;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.*;
import eh.entity.msg.SmsInfo;
import eh.evaluation.dao.EvaluationDAO;
import eh.evaluation.service.EvaluationService;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.RequestMindGiftService;
import eh.mpi.dao.FollowChatMsgDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.follow.FollowChatService;
import eh.msg.dao.GroupDAO;
import eh.push.SmsPushService;
import eh.util.Easemob;
import eh.util.RetryTask;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.util.Util;
import it.sauronsoftware.jave.AudioAttributes;
import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncoderException;
import it.sauronsoftware.jave.EncodingAttributes;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/6/3 0003.
 * 医患聊天消息服务
 */
public class ConsultMessageService {
    private static final Logger log = LoggerFactory.getLogger(ConsultMessageService.class);


    final static String ENVIRONMENTROOTPATH = ConsultMessageService.class.getClassLoader().getResource("/").getPath().replaceAll("WEB-INF/classes/", "").concat("repository/");


    @RpcService
    public PageResult<ChatMessage> findLatestChatMessage(Integer requestMode, String sessionId, Integer startIndex, Integer pageSize) {
        if (ValidateUtil.nullOrZeroInteger(requestMode) || ValidateUtil.blankString(sessionId)) {
            log.error("findLatestChatMessage necessary parameter null, please check! requestMoode[{}], sessionId[{}]", requestMode, sessionId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        if (ValidateUtil.nullOrZeroInteger(startIndex)) {
            startIndex = 0;
        }
        if (ValidateUtil.nullOrZeroInteger(pageSize)) {
            pageSize = 10;
        }
        try {
            PageResult<ChatMessage> pageResult = new PageResult<>();
            pageResult.setVersion(System.currentTimeMillis());
            List<ChatMessage> data = Lists.newArrayList();
            if (requestMode.equals(ConsultConstant.CONSULT_TYPE_GRAPHIC) || requestMode.equals(ConsultConstant.CONSULT_TYPE_RECIPE) || requestMode.equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)) {
                List<BusConsultMsg> consultMsgList = DAOFactory.getDAO(ConsultMessageDAO.class).findLatestChatMessage(requestMode, sessionId, startIndex, pageSize);
                data = fullFillAFromForChatMessageList(convertConsultToChatList(consultMsgList, sessionId));
            } else { // 随访
                List<FollowChatMsg> followChatMsgList = DAOFactory.getDAO(FollowChatMsgDAO.class).findLatestChatMessage(sessionId, startIndex, pageSize);
                data = fullFillAFromForChatMessageList(convertFollowChatToChatList(followChatMsgList, sessionId));
            }
            pageResult.setPageSize(data.size());
            pageResult.setData(data);
            return pageResult;
        } catch (Exception e) {
            log.error("findLatestChatMessage error, requestMoode[{}], sessionId[{}], errorMessage[{}], stackTrace[{}]", requestMode, sessionId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private List<ChatMessage> fullFillAFromForChatMessageList(List<ChatMessage> chatMessageList) {
        if (ValidateUtil.blankList(chatMessageList)) {
            return chatMessageList;
        }
        for (ChatMessage cm : chatMessageList) {
            packSender(cm);
        }
        return chatMessageList;
    }

    private ChatMessage packSender(ChatMessage cm) {
        String aFrom = "";
        String senderRole = cm.getSenderRole();
        String senderId = cm.getSenderId();
        if (ConsultConstant.MSG_ROLE_TYPE_DOCTOR.equals(senderRole)) {
            Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(Integer.valueOf(senderId));
            cm.setSenderName(doctor.getName());
            cm.setSenderPortrait(doctor.getPhoto());
            Integer urt = Util.getUrtForDoctor(doctor.getMobile());
            aFrom = Easemob.getDoctor(urt);
            cm.setaFrom(aFrom);
        } else if (ConsultConstant.MSG_ROLE_TYPE_PATIENT.equals(senderRole)) {
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(senderId);
            cm.setSenderName(patient.getPatientName());
            cm.setSenderPortrait(patient.getPhoto());
            Integer urt = Util.getUrtByMobileForPatient(patient.getLoginId());
            aFrom = Easemob.getPatient(urt);
            cm.setaFrom(aFrom);
        }
        return cm;
    }

    private List<ChatMessage> convertConsultToChatList(List<BusConsultMsg> consultMsgList, String sessionId) {
        List<ChatMessage> chatMessageList = Lists.newArrayList();
        if (ValidateUtil.blankList(consultMsgList)) {
            return chatMessageList;
        }
        for (BusConsultMsg bc : consultMsgList) {
            ChatMessage cm = new ChatMessage();
            cm.setUuid(bc.getMsgExtra());
            cm.setSessionID(sessionId);
            cm.setContent(bc.getMsgContent());
            try {
                MsgTypeEnum msgTypeEnum = MsgTypeEnum.fromId(bc.getMsgType());
                switch (msgTypeEnum) {
                    case PATIENT_DISEASE_DESCRIPTION:
                        PatientDiseaseDescription pdd = JSONObject.parseObject(bc.getMsgContent(), PatientDiseaseDescription.class);
                        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                                .findByClinicTypeAndClinicId(3, pdd.getConsultId());
                        String content = packageConsultPDD(pdd, cdrOtherdocs);
                        cm.setContent(content);
                        break;
                    case REPORT:
                        cm.setContent(packReportForApp(bc.getMsgContent()));
                        break;
                    case RECIPE:
                        cm.setContent(packRecipeForApp(bc.getMsgContent()));
                        break;
                    case DRUG:
                        cm.setContent(packDrugForApp(bc.getMsgContent()));
                        break;
                    case MIND_GIFT:
                        cm.setContent(packMindGiftForApp(bc.getMsgContent()));
                        break;
                    default:
                        break;
                }
                Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(bc.getMpiId());
                String modeText = DictionaryController.instance().get("eh.bus.dictionary.RequestMode").getText(bc.getRequestMode());
                cm.setGroupName(patient.getPatientName() + "的" + modeText);
            } catch (Exception e) {
                log.error("convertConsultToChatList error, params: bc[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(bc), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                continue;
            }
            cm.setType(bc.getMsgType());
            cm.setServerTime(bc.getCreateTime());
            cm.setBusId(bc.getConsultId());
            cm.setSenderRole(bc.getSenderRole());
            if (ConsultConstant.MSG_ROLE_TYPE_DOCTOR.equals(bc.getSenderRole())) {
                cm.setSenderId(ValidateUtil.nullOrZeroInteger(bc.getExeDoctorId()) ? bc.getSenderId() : String.valueOf(bc.getExeDoctorId()));
            } else {
                cm.setSenderId(bc.getSenderId());
            }
            chatMessageList.add(cm);
        }
        return chatMessageList;

    }

    private List<ChatMessage> convertFollowChatToChatList(List<FollowChatMsg> followChatMsgList, String sessionId) throws UnsupportedEncodingException {
        List<ChatMessage> chatMessageList = Lists.newArrayList();
        if (ValidateUtil.blankList(followChatMsgList)) {
            return chatMessageList;
        }
        for (FollowChatMsg bc : followChatMsgList) {
            ChatMessage cm = new ChatMessage();
            cm.setUuid(bc.getMsgExtra());
            cm.setSessionID(sessionId);
            //随访表单、健康评估和患教文章urlEncode处理
            if (bc.getMsgType() == 91) {
                cm.setContent(packFollowAssessForApp(bc.getMsgContent()));
            } else if (bc.getMsgType() == 92) {
                cm.setContent(packHealthAssessForApp(bc.getMsgContent()));
            } else if (bc.getMsgType() == 93) {
                cm.setContent(packArticleForApp(bc.getMsgContent()));
            } else {
                cm.setContent(bc.getMsgContent());
            }
            cm.setType(bc.getMsgType());
            cm.setServerTime(bc.getCreateTime());
            cm.setBusId(bc.getFollowChatId());
            cm.setSenderRole(bc.getSenderRole());
            cm.setSenderId(bc.getSenderId());
            cm.setGroupName((DAOFactory.getDAO(GroupDAO.class).get(sessionId)).getTitle());
            cm.setMpiId(bc.getMpiId());
            chatMessageList.add(cm);
        }
        return chatMessageList;

    }

    /**
     * 更改某条消息的状态为已读
     *
     * @param cidOrUUid 当页面停留在聊天界面时，通过环信收到语音消息，此时只能拿到UUid，因此，此字段值可能有两种情况，uuid或者consultMessageId
     */
    @RpcService
    public void updateConsultMessageToHasRead(String cidOrUUid) {
        log.info("updateConsultMessageToHasChat cidOrUUid[{}]", cidOrUUid);
        DAOFactory.getDAO(ConsultMessageDAO.class).updateConsultMessageToHasRead(cidOrUUid);
    }

    /**
     * 健康app发送聊天消息接口， 服务端记录此消息并异步发送环信消息到医生端
     *
     * @param hxMsgId    环信消息id(全局唯一即可）
     * @param consultId  咨询id
     * @param msgType    消息类型
     * @param msgContent 消息内容
     * @return true 成功，false失败
     * @throws DAOException
     */
    @Deprecated
    @RpcService
    public boolean sendConsultMsgToDoctor(String hxMsgId, Integer consultId, String msgType, String msgContent, String customerTimeGroup) throws DAOException {
        Date customerTime = DateConversion.getCurrentDate(customerTimeGroup, "yyyy-MM-dd HH:mm");
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setHxMsgId(hxMsgId);
        tMsg.setConsultId(consultId);
        tMsg.setMsgType(msgType);
        tMsg.setMsgContent(msgContent);
        tMsg.setSendTime(customerTime);
        tMsg.setRequestMode(ConsultConstant.CONSULT_TYPE_GRAPHIC);
        tMsg.setCreateTime(new Date());
        try {
            // 保存到数据库
            addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
            Consult consult = DAOFactory.getDAO(ConsultDAO.class).getById(consultId);
            Patient requestPatient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(consult.getRequestMpi());
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(consult.getMpiid());
            // 发送环信消息给医生
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", consult.getConsultId().toString());
            ext.put("busType", "2");    //会诊是 1 咨询可以是2
            ext.put("name", requestPatient.getPatientName());
            ext.put("groupName", patient.getPatientName() + "的咨询");
            ext.put("avatar", requestPatient.getPhoto() == null ? null : requestPatient.getPhoto().toString());
            ext.put("gender", requestPatient.getPatientSex());
            ext.put("appId", consult.getAppId());
            ext.put("openId", consult.getOpenId());
            AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), tMsg.getMsgContent(), ext);
            return true;
        } catch (Exception e) {
            log.error(LocalStringUtil.format("sendConsultMsgToDoctor failed! time:[{}],errorMessage:[{}], tMsg:[{}]", new LocalDate(), e.getMessage(), tMsg));
            throw e;
        }
    }

    /**
     * 获取当前患者的最新一条咨询信息
     *
     * @param doctorId
     * @return
     * @Date 2016-12-08 12:03:24
     * @author zhangsl
     * 获取最新咨询新增评价信息
     * 2017-7-6 16:14:07  该接口目前微信端在使用
     */
    @Deprecated
    @RpcService
    public Consult getLatestConsultByDoctorId(Integer doctorId) {
        return getLatestConsultByDoctorIdWithRequestMode(doctorId, ConsultConstant.CONSULT_TYPE_GRAPHIC);
    }

    @RpcService
    public Consult getLatestConsultByDoctorIdWithRequestMode(Integer doctorId, Integer requestMode) {
        log.info("getLatestConsultByDoctorIdWithRequestMode with param doctorId[{}], requestMode[{}]", doctorId, requestMode);
        if (ValidateUtil.nullOrZeroInteger(doctorId)) {
            log.error("getLatestConsultByDoctorIdWithRequestMode failed, doctorId is null or zero!");
        }
        try {
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            String requestMpi = patient.getMpiId();
            List<Consult> consultList = DAOFactory.getDAO(ConsultDAO.class).findLatestConsultByMpiAndDoctorIdAndRequestMode(requestMpi, doctorId, requestMode);
            if (ValidateUtil.blankList(consultList)) {
                log.info("getLatestConsultByDoctorIdWithRequestMode result null, parameters: requestMpi[{}], doctorId[{}]", requestMpi, doctorId);
                return null;
            }
            Consult c = consultList.get(0);
            if (c != null) {
                // 是否评价(执行医生)
                if (c.getConsultStatus() == 2 && c.getRefuseFlag() == null && c.getExeDoctor() != null) {
                    EvaluationService evaService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
                    EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                    String requestMpiId = c.getRequestMpi();
                    UserSevice userService = new UserSevice();
                    // 获取申请者urtid
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    Patient requestPatinet = patientDAO.get(requestMpiId);
                    String requestMobile = requestPatinet.getLoginId();
                    int requestUrtId = userService.getUrtIdByUserId(requestMobile,
                            "patient");
                    c.setFeedBack(evaService.isEvaluation(c.getExeDoctor(), "3", String.valueOf(c.getConsultId()), requestUrtId, "patient"));
                    if (c.getFeedBack()) {
                        c.setFeedbackId(evaDao.
                                findEvaByServiceAndUser(c.getExeDoctor(), "3", String.valueOf(c.getConsultId()), requestUrtId, "patient").get(0).getFeedbackId());
                    }
                } else {
                    c.setFeedBack(false);
                }
            }
            return c;
        } catch (Exception e) {
            log.info("getLatestConsultByDoctorIdWithRequestMode exception, errorMessage[{}], stackTrace[{}], parameters: doctorId[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()), doctorId);
            return null;
        }
    }

    /**
     * 查询用户所有未读消息条数总数
     *
     * @return
     */
    @RpcService
    public Long findUserNotReadMsgCount() {
        Long count = null;
        Long countConsultMsg = null;
        Long countFollowChatMsg = null;
        try {
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            String requestMpi = patient.getMpiId();
            ConsultMessageDAO consultMessageDAO = DAOFactory.getDAO(ConsultMessageDAO.class);
            FollowChatMsgDAO followChatMsgDAO = DAOFactory.getDAO(FollowChatMsgDAO.class);
            countConsultMsg = consultMessageDAO.findUserNotReadMsgCountWithMpiId(requestMpi);
            countFollowChatMsg = followChatMsgDAO.findUserNotReadFollowChatMsgCountWithMpiId(requestMpi);
            count = (countConsultMsg == null ? 0 : countConsultMsg) + (countFollowChatMsg == null ? 0 : countFollowChatMsg);
            log.info(LocalStringUtil.format("findUserNotReadMsgCount with requestParameter[{}], resultCount[{}]", requestMpi, count));
        } catch (Exception e) {
            log.error(LocalStringUtil.format("findUserNotReadMsgCount with resultCount[{}], errorMessage[{}], errorStackTrace[{}]", count, e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        if (count == null) {
            return 0l;
        }
        return count;
    }


    /**
     * 用于接收患者消息并将消息放入消息队列，供后续业务接口处理
     * 接口升级记录： 2016-07-14 增加入参：hxMsgId
     *
     * @param customerKey
     * @param consultId
     * @param msgType
     * @param msgContent
     * @return
     * @throws DAOException
     */
    @Deprecated
    @RpcService
    public String receiveMessageFromPatient(String hxMsgId, String customerKey, Integer consultId, String msgType, String msgContent, String customerTimeGroup) throws DAOException {
        return receiveMessageFromPatientWithRequestMode(hxMsgId, customerKey, consultId, msgType, msgContent, customerTimeGroup, ConsultConstant.CONSULT_TYPE_GRAPHIC);
    }

    @RpcService
    public String receiveMessageFromPatientWithRequestMode(String hxMsgId, String customerKey, Integer consultId, String msgType, String msgContent, String customerTimeGroup, Integer requestMode) throws DAOException {
        if (!OnsConfig.onsSwitch) {
            log.info("the onsSwitch is set off, ons is out of service.");
            return null;
        }
        Date customerTime = DateConversion.getCurrentDate(customerTimeGroup, "yyyy-MM-dd HH:mm");
        if (customerTime == null) {
            log.error(LocalStringUtil.format("receiveMessageFromPatient error, invalid parameter-customerTimeGroup[{}],cid[{}],mT[{}]", customerTimeGroup, consultId, msgType));
            return null;
        }
        final Publisher publisher = MQHelper.getMqPublisher();
        final TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setHxMsgId(hxMsgId);
        tMsg.setConsultId(consultId);
        tMsg.setMsgType(msgType);
        tMsg.setMsgContent(msgContent);
        tMsg.setSendTime(customerTime);
        tMsg.setCreateTime(new Date());
        tMsg.setRequestMode(requestMode);
        log.info("receiveMessageFromPatientWithRequestMode tMsg[{}]", JSONObject.toJSONString(tMsg));
        RetryTask retry = new RetryTask("receiveMessageFromPatient") {
            @Override
            public Object task() throws Exception {
                publisher.publish(OnsConfig.patientTopic, tMsg);
                return null;
            }
        };
        retry.retryTask();
        return customerKey;
    }

    /**
     * 订阅消息
     */
    @PostConstruct
    public void busConsultMsgConsumer() {
        OnsConfig onsConfig = (OnsConfig) AppContextHolder.getBean("onsConfig");
        if (!OnsConfig.onsSwitch) {
            log.info("the onsSwitch is set off, consumer not subscribe.");
            return;
        }
        Subscriber subscriber = MQHelper.getMqSubscriber();
        subscriber.attach(OnsConfig.patientTopic, new Observer<TempMsgType>() {
            @Override
            public void onMessage(TempMsgType tMsg) {
                if (tMsg instanceof TempMsgBody4MQ) {
                    TempMsgBody4MQ consultMsgBody = (TempMsgBody4MQ) tMsg;
                    handlePatientMessage(consultMsgBody);
                } else if (tMsg instanceof FollowTempMsgType) {
                    FollowTempMsgType followTempMsgType = (FollowTempMsgType) tMsg;
                    FollowChatService followChatService = AppContextHolder.getBean("followChatService", FollowChatService.class);
                    followChatService.handlePatientMessage(followTempMsgType);
                }
            }
        });
        subscriber.attach(OnsConfig.doctorTopic, new Observer<TempMsgType>() {
            @Override
            public void onMessage(TempMsgType tMsg) {
                if (tMsg instanceof TempMsgBody4MQ) {
                    TempMsgBody4MQ consultMsgBody = (TempMsgBody4MQ) tMsg;
                    handleDoctorMessage(consultMsgBody);
                } else if (tMsg instanceof MeetMsgBody4MQ) {
                    MeetMsgBody4MQ meetMsgBody = (MeetMsgBody4MQ) tMsg;
                    MeetClinicMessageService meetClinicMessageService = AppContextHolder.getBean("meetClinicMessageService", MeetClinicMessageService.class);
                    meetClinicMessageService.handleDoctorMsg(meetMsgBody);
                } else if (tMsg instanceof FollowTempMsgType) {
                    FollowTempMsgType followTempMsgType = (FollowTempMsgType) tMsg;
                    FollowChatService followChatService = AppContextHolder.getBean("followChatService", FollowChatService.class);
                    followChatService.handleDoctorMessage(followTempMsgType);
                }

            }
        });
    }

    /**
     * 查询咨询单的状态
     *
     * @param consultId
     * @return
     */
    @RpcService
    public Map<String, Boolean> checkConsultStatus(Integer consultId) {
        HashMap<String, Boolean> consultStatus = Maps.newHashMap();

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDAO.get(consultId);
        if (consult == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "consult is required!");
        }

        Integer consultStatusVar = consult.getConsultStatus();
        if (consultStatusVar != null && consultStatusVar.intValue() == 2 && !consult.getHasAdditionMessage()) {
            consultStatus.put("canSendAdditionMessage", true);
            consultStatus.put("hasAdditionMessage", false);
        } else {
            consultStatus.put("canSendAdditionMessage", false);
            consultStatus.put("hasAdditionMessage", true);//true就不展示补充留言
        }

        consultStatus.put("groupEnable", Boolean.FALSE);

        if (consultStatusVar != null && (consultStatusVar.intValue() == 2 || consultStatusVar.intValue() == 3 || consultStatusVar.intValue() == 9)) {
            return consultStatus;
        }

        Integer newConsultId = consultDAO.getNewestConsultId(consult);
        if (null != newConsultId) {
            Consult newConsult = consultDAO.get(newConsultId);
            if (null != newConsult && newConsult.getConsultStatus() == 1 && consult.getSessionID().equals(newConsult.getSessionID())) {
                consultStatus.put("groupEnable", Boolean.TRUE);
            }
        }
        return consultStatus;
    }

    /**
     * 医生发送补充留言后，更新状态
     * 咨询结束后医生发送补充留言，只能一次
     *
     * @return 返回补充留言完成后的consult对象供客户端查看状态
     */
    @RpcService
    public Consult updateAdditionalMessStatus(String uuid, final int consultId) {

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        //更新状态
        Consult consult = consultDAO.getById(consultId);

/*        GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
            @Override
            public void run() {

                //获取环信的消息插入后台数据库
                TempMsgBody4MQ tempMsgBody4MQ = new TempMsgBody4MQ();

                tempMsgBody4MQ.setConsultId(consult.getConsultId());
                tempMsgBody4MQ.setRequestMode(consult.getRequestMode());
                tempMsgBody4MQ.setSendDocID();
                tempMsgBody4MQ.set

                handleDoctorMessage(tempMsgBody4MQ);
            }
        });*/


        consult.setHasAdditionMessage(true);
        return consultDAO.update(consult);
    }

    /**
     * 根据当前consultId获取最新的consultId发送信息
     *
     * @param hxMsgId
     * @param busType
     * @param busId
     * @param msgType
     * @param msgContent
     * @return
     * @throws DAOException
     */
    @RpcService
    public boolean doctorSendMsgWithConsultId(String hxMsgId, Integer busType,
                                              Integer busId, String msgType, String msgContent) throws DAOException {
        log.info("doctorSendMsgWithConsultId hxMsgId[{}], busId[{}], busType[{}], msgType[{}], msgContent[{}]", hxMsgId, busId, busType, msgType, msgContent);
        switch (busType) {
            case ConsultConstant.BUS_TYPE_CONSULT:
                return packAndPutConsultMsg(hxMsgId, busType, busId, msgType, msgContent);
            case ConsultConstant.BUS_TYPE_MEET:
                return packAndPutMeetClinicMsg(hxMsgId, busType, busId, msgType, msgContent);
            case ConsultConstant.BUS_TYPE_APPOINTMENT:
            case ConsultConstant.BUS_TYPE_TRANSFER:
            default:
                log.info("doctorSendMsgWithConsultId busType not support, busId[{}], busType[{}]", busId, busType);
                return true;
        }
    }

    private boolean packAndPutMeetClinicMsg(String hxMsgId, Integer busType, Integer busId, String msgType, String msgContent) {
        if (!OnsConfig.onsSwitch) {
            log.info("the onsSwitch is set off, ons is out of service.");
            return false;
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        Doctor doctor = (Doctor) urt.getProperty("doctor");
        final MeetMsgBody4MQ msgBody = new MeetMsgBody4MQ();
        msgBody.setDoctorId(doctor.getDoctorId());
        msgBody.setMeetClinicId(busId);
        msgBody.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
        msgBody.setMsgContent(msgContent);
        msgBody.setCreateTime(new Date());
        msgBody.setSendTime(new Date());
        msgBody.setMsgType(msgType);
        msgBody.setHxMsgId(hxMsgId);
        final Publisher publisher = MQHelper.getMqPublisher();
        RetryTask retry = new RetryTask("doctorSendMeetClinic") {
            @Override
            public Object task() throws Exception {
                publisher.publish(OnsConfig.doctorTopic, msgBody);
                return null;
            }
        };
        retry.retryTask();
        return true;
    }

    private boolean packAndPutConsultMsg(String hxMsgId, Integer busType,
                                         Integer consultId, String msgType, String msgContent) {
        log.info("doctorSendMsgWithConsultId consultId:" + consultId);
        Assert.notNull(consultId, "doctorSendMsgWithConsultId consultId is null");

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDao.get(consultId);
        Assert.notNull(consult, "doctorSendMsgWithConsultId consult is null! consultId:" + consultId);

//        Integer newConsultId = consultDao.getNewestConsultId(consult);
        Integer newConsultId = consultDao.getConsultTypeNewestConsultId(consult);
        if (null != newConsultId) {
            log.info("doctorSendMsgWithConsultId newConsultId:" + newConsultId);


            //2017-3-7 10:54:06 zhangx  8689 【日间手术团队】添加或剔除提的成员问题
            try {
                Boolean canSend = doctorCanSendMsg(newConsultId);
                if (!canSend) {
                    log.info("doctorSendMsgWithConsultId doctorCanSendMsg:" + canSend);
                    return false;
                }
            } catch (Exception e) {
                log.info("doctorSendMsgWithConsultId doctorCanSendMsg:" + e.getMessage());
            }

            UserRoleToken urt = UserRoleToken.getCurrent();
            Doctor doc = (Doctor) urt.getProperty("doctor");
            Integer sendId = null;
            if (doc != null) {
                sendId = doc.getDoctorId();
            }

            return this.doctorSendMsg(hxMsgId, busType, newConsultId, msgType, msgContent, consult.getRequestMode(), sendId);
        }
        return false;
    }

    /**
     * 判断当前发送消息的医生能否发送消息给患者
     *
     * @param newConsultId
     * @return 2017-3-7 10:54:06 zhangx  8689 【日间手术团队】添加或剔除提的成员问题
     */
    private Boolean doctorCanSendMsg(Integer newConsultId) {
        Boolean flag = true;
        UserRoleToken urt = UserRoleToken.getCurrent();
        Doctor doc = (Doctor) urt.getProperty("doctor");
        Integer sendId = null;
        if (doc != null) {
            sendId = doc.getDoctorId();
        }

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDao.get(newConsultId);
        Assert.notNull(consult, "doctorCanSendMsg consult is null! newConsultId:" + newConsultId);

        Integer teamId = consult.getConsultDoctor();

        //个人咨询单消息，可以发送
        Boolean teams = consult.getTeams() == null ? false : consult.getTeams();
        if (!teams) {
            return true;
        }

        //groupMode=0 抢单模式,可以发送消息
        int groupMode = consult.getGroupMode() == null ? 0 : consult.getGroupMode();
        if (groupMode == 0) {
            return true;
        }

        //非抢单模式，判断发送消息的人是否在目标医生团队里面
        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorGroup group = groupDAO.getByDoctorIdAndMemberId(teamId, sendId);
        if (group == null) {
            log.info("consult[" + newConsultId + "],teams[" + teams + "],groupMode[" + groupMode + "]" +
                    "sendId[" + sendId + "]not in teams[" + teamId + "]");
            flag = false;
        }

        return flag;
    }

    @RpcService
    @Deprecated
    public boolean doctorSendMsgWithSessionId(String hxMsgId, Integer busType,
                                              String sessionId, String msgType, String msgContent) throws DAOException {
        if (StringUtils.isEmpty(sessionId)) {
            throw new DAOException(609, "sessionId is null");
        }

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        List<Consult> consults = consultDao.findBySessionID(sessionId);
        if (null != consults && !consults.isEmpty() && consults.size() > 0) {

            Integer newestConsultId = consultDao.getNewestConsultId(consults.get(0));

            log.info("doctorSendMsg consultIds:" + newestConsultId);
            if (null != newestConsultId) {
                UserRoleToken urt = UserRoleToken.getCurrent();
                Doctor doc = (Doctor) urt.getProperty("doctor");
                Integer sendId = null;
                if (doc != null) {
                    sendId = doc.getDoctorId();
                }
                return this.doctorSendMsg(hxMsgId, busType, newestConsultId, msgType, msgContent, ConsultConstant.CONSULT_TYPE_GRAPHIC, sendId);
            }
        }

        return false;
    }


    /**
     * 用于接收医生消息，并将消息放入消息队列，供后续接口处理
     *
     * @param hxMsgId
     * @param busType
     * @param busId
     * @param msgType
     * @param msgContent
     * @return
     * @throws DAOException
     */
    public boolean doctorSendMsg(String hxMsgId, Integer busType, Integer busId, String msgType, String msgContent, Integer requestMode, Integer sendDocId) throws DAOException {
        // 2.1.1版本需求，只为咨询提供服务，故此处先将其他业务类型消息丢弃
        if (ConsultConstant.BUS_TYPE_CONSULT != busType) {
            log.info("other busType, do not handle, busType:" + busType + " busId:" + busId + " msgType:" + msgType);
            return true;
        }
        if (!OnsConfig.onsSwitch) {
            log.info("the onsSwitch is set off, ons is out of service.");
            return false;
        }
        final Publisher publisher = MQHelper.getMqPublisher();
        final TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setHxMsgId(hxMsgId);
        tMsg.setConsultId(busId);
        tMsg.setMsgType(msgType);
        tMsg.setMsgContent(msgContent);
        tMsg.setCreateTime(new Date());
        tMsg.setRequestMode(requestMode);
        tMsg.setSendDocID(sendDocId);
        RetryTask retry = new RetryTask("doctorSendMsg") {
            @Override
            public Object task() throws Exception {
                publisher.publish(OnsConfig.doctorTopic, tMsg);
                return null;
            }
        };
        retry.retryTask();
        return true;
    }

    /**
     * 用于接收医生消息，并将消息放入消息队列，供后续接口处理
     * log: 为已发出版本预留
     * deprecated Time: 2016-07-14
     *
     * @param busType    业务类型
     * @param busId      业务id
     * @param msgType    消息类型
     * @param msgContent 消息内容
     * @return
     * @throws DAOException
     */
    @Deprecated
    @RpcService
    public boolean receiveMessageFromDoctor(Integer busType, Integer busId, String msgType, String msgContent) throws DAOException {
        UserRoleToken urt = UserRoleToken.getCurrent();
        Doctor doc = (Doctor) urt.getProperty("doctor");
        Integer sendId = null;
        if (doc != null) {
            sendId = doc.getDoctorId();
        }
        return doctorSendMsg(null, busType, busId, msgType, msgContent, ConsultConstant.CONSULT_TYPE_GRAPHIC, sendId);
    }

    /**
     * 处理患者发送过来的消息，添加到消息记录表
     *
     * @param tMsg
     */
    public void handlePatientMessage(TempMsgBody4MQ tMsg) {
        if (ValidateUtil.nullOrZeroInteger(tMsg.getConsultId())) {
//            log.error("handlePatientMessage error! consultId is null or Zero, consultId:" + tMsg.getConsultId());
            throw new DAOException(609, "handlePatientMessage error! consultId is null or Zero consultId:" + tMsg.getConsultId());
        }
        try {
            addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        } catch (Exception e) {
            log.error(LocalStringUtil.format("handlePatientMessage failed! time:[{}],errorMessage:[{}], tMsg:[{}]", new LocalDate(), e.getMessage(), tMsg));
            throw e;
        }
    }


    /**
     * 处理医生发送过来的消息，添加到消息记录表，并发送微信客服消息给患者
     *
     * @param tMsg
     */
    public void handleDoctorMessage(TempMsgBody4MQ tMsg) {
        log.info("收到消息内容" + tMsg.getMsgContent() + ",咨询单Id：" + tMsg.getConsultId());
        if (ValidateUtil.nullOrZeroInteger(tMsg.getConsultId())) {
//            log.error("handlePatientMessage error! consultId is null or Zero, consultId:" + tMsg.getConsultId());
            throw new DAOException(609, "handlePatientMessage error! consultId is null or Zero consultId:" + tMsg.getConsultId());
        }
        try {
            // 当医生回复第一条消息的时候，更新咨询表的hasChat字段为1，并且给患者发送短信通知
            ConsultMessageDAO msgDao = DAOFactory.getDAO(ConsultMessageDAO.class);
            List<BusConsultMsg> msgList = msgDao.findChatMessageListByConsultId(tMsg.getConsultId());
            ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
            Consult consult = consultDao.getById(tMsg.getConsultId());
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            Integer clientId = consult.getDeviceId();
            if (ValidateUtil.blankList(msgList)) {
                //consultDao.sendSMSForEndConsult(tMsg.getConsultId());
                smsPushService.pushMsgData2Ons(consult.getConsultId(), consult.getConsultOrgan(), "SendSMSForEndConsult", "SendSMSForEndConsult", clientId);

                consultDao.updateHasChat(tMsg.getConsultId());
                if (consult.getExeDoctor() == null && ValidateUtil.isNotTrue(consult.getTeams())) {
                    StartConsultService startConsultService = new StartConsultService();
                    startConsultService.startConsult(consult.getConsultId(), consult.getConsultDoctor(), consult.getConsultDepart(), consult.getConsultOrgan());
                }
            }
            // 当消息为图片类型、且包含 img: 前缀等非数字时，去掉非数字
            if (MsgTypeEnum.GRAPHIC.getId() == Short.valueOf(tMsg.getMsgType()) && tMsg.getMsgContent() != null) {
                String tempContent = tMsg.getMsgContent();
                tempContent = tempContent.replaceAll("\\D", "");
                tMsg.setMsgContent(tempContent);
            }

            //消息类型是语音，
            if (MsgTypeEnum.AUDIO.getId() == Short.valueOf(tMsg.getMsgType()) && tMsg.getMsgContent() != null) {
                String mobileByMpiId = DAOFactory.getDAO(PatientDAO.class).getMobileByMpiId(consult.getRequestMpi());
                tMsg.setUserId(mobileByMpiId);

                handleDoctorVoiceMessage(tMsg);
            }

            log.info("save msgRecord:", tMsg.getMsgContent());
            addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            log.info("save msgRecord successfully");

            Integer doctorId = consult.getExeDoctor();
            if (ValidateUtil.notNullAndZeroInteger(consult.getGroupMode())) {
                doctorId = consult.getConsultDoctor();
            }
            Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
            StringBuffer sb = new StringBuffer();
            if (ValidateUtil.nullOrZeroInteger(consult.getGroupMode())) {
                sb.append(doctor.getName() + "医生");
            } else {
                sb.append(doctor.getName());
            }

            if (MsgTypeEnum.TEXT.getId() == Short.valueOf(tMsg.getMsgType())) {
                sb.append(":");
                sb.append(tMsg.getMsgContent());
                sb.append("\n");
                sb.append("<a href=\"{}\">点击回复</a>");
            } else if (MsgTypeEnum.GRAPHIC.getId() == Short.valueOf(tMsg.getMsgType())) {
                sb.append("给您发了一张");
                sb.append(MsgTypeEnum.GRAPHIC.getName());
                sb.append("。\n");
                sb.append("<a href=\"{}\">点击查看</a>");
            } else if (MsgTypeEnum.AUDIO.getId() == Short.valueOf(tMsg.getMsgType())) {
                sb.append("给您发了一段");
                sb.append(MsgTypeEnum.AUDIO.getName());
                sb.append("。\n");
                sb.append("<a href=\"{}\">点击查看</a>");
            } else if (MsgTypeEnum.VCARD.getId() == Short.valueOf(tMsg.getMsgType())) {
                Map jsonMap = customerMessageParamsToMap(tMsg.getMsgContent());
                sb.append("向您推荐了");
                sb.append(jsonMap.get("name") == null ? "" : jsonMap.get("name"));
                sb.append("。\n");
                sb.append("<a href=\"{}\">点击查看</a>");
            }
            // 发送微信 客服消息 给患者
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(consult.getConsultId());
            smsInfo.setOrganId(consult.getConsultOrgan());
            smsInfo.setBusType("SendWXForEndConsult");
            smsInfo.setSmsType("SendWXForEndConsult");
            smsInfo.setClientId(clientId);
            smsInfo.setCreateTime(new Date());
            smsInfo.setStatus(0);
            smsInfo.setExtendValue(sb.toString());
            smsInfo.setClientId(consult.getDeviceId());
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);

        } catch (Exception e) {
            log.error(LocalStringUtil.format("handleDoctorMessage error! tMsg[{}], errorMessage[{}], errorStack[{}]", tMsg, e.getMessage(), e.getStackTrace()[0]));
        }
    }

    private Map customerMessageParamsToMap(String msgContent) throws UnsupportedEncodingException {
        if (msgContent.startsWith("text://") && msgContent.indexOf("json=") != -1) {
            String[] strings = msgContent.split("json=");
            if (strings.length == 2) {
                String decodeJson = URLDecoder.decode(strings[1], "UTF-8");
                return JSON.parseObject(decodeJson, HashMap.class);
            } else {
                throw new IllegalArgumentException("missing json content");
            }
        }
        return null;
    }

    /**
     * 从环信获取医生的语音,保存至oss，返回的ossId保存至消息
     *
     * @param tMsg tMsg的messageContent里面需要有从环信获取文件的shareSecret和uuid
     */
    public void handleDoctorVoiceMessage(TempMsgType tMsg) {
        String tempContent = tMsg.getMsgContent();
        Map<String, Object> messageContentMap = (Map<String, Object>) JSON.parse(tempContent);

        String fileUrl = String.valueOf(messageContentMap.get("fileUrl") == null ? "" : messageContentMap.get("fileUrl"));
        String shareSecret = String.valueOf(messageContentMap.get("shareSecret") == null ? "" : messageContentMap.get("shareSecret"));

        if (!fileUrl.equalsIgnoreCase("")) {
            messageContentMap.put("ossid", saveFileToOss2(fileUrl, tMsg.getUserId(), true));
            tMsg.setMsgContent(JSON.toJSONString(messageContentMap));
        } else {
            throw new RuntimeException("下载语音文件参数缺失");
        }


    }

    //截取语音文件url的文件uuid部分
    private String getFileUuidFromUrl(String fileUrl) {
        String fileUuid = "";
        if (fileUrl != null && !fileUrl.trim().equalsIgnoreCase("") && fileUrl.indexOf("chatfiles/") > -1) {
            String[] splitedUrls = fileUrl.split("chatfiles/");
            if (splitedUrls.length == 2) {
                fileUuid = splitedUrls[1];
            }

        }
        return fileUuid;
    }


    @RpcService
    public Integer convertAmr2Mp3(String url, String userId) {

        return saveFileToOss2(url, userId, true);
    }

    private Integer saveFileToOss2(String fileUrl, String userId, Boolean convertVoice) {
        log.info("userId==" + userId);
        InputStream is = null;
        File mp3File = null;
        InputStream mp3Is = null;
        try {
            URL urlObject = new URL(fileUrl);
            URLConnection connection = urlObject.openConnection();
            is = connection.getInputStream();
//            String contentType = connection.getContentType();
            int size = connection.getContentLength();


            String fileUuid = getFileUuidFromUrl(fileUrl);
            String fileName;

            Date now = new Date();
            FileMetaRecord meta = new FileMetaRecord();
            meta.setCatalog("ngarimedia");
            meta.setOwner(userId);
            meta.setTenantId("eh");
            meta.setMode(FileMetaRecord.MODE_PUBLIC_READ);//31是未登录可用,mode=0需要验证
            meta.setManageUnit("eh");
            meta.setFileSize(size);
            meta.setLastModify(now);
            meta.setUploadTime(now);

            if (!convertVoice) {
                fileName = fileUuid + ".amr";
                meta.setFileName(fileName);
                meta.setContentType(connection.getContentType());
                FileService.instance().upload(meta, is);
            } else {
                mp3File = convertAmr2Mp3(is, fileUuid);
                meta.setFileSize(mp3File.length());
                mp3Is = new FileInputStream(mp3File);
                fileName = fileUuid + ".mp3";
                meta.setFileName(fileName);
                meta.setContentType("audio/mp3");
                FileService.instance().upload(meta, mp3Is);
            }
            log.info("meta.getFileId()=" + String.valueOf(meta.getFileId()));
            return meta.getFileId();
        } catch (Exception e) {
            log.error("convert error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error("saveFileToOss2-->" + e);
                }
            }
            if (mp3Is != null) {
                try {
                    mp3Is.close();
                    mp3File.delete();
                } catch (IOException e) {
                    log.error("saveFileToOss2-->" + e);
                }

            }
        }
        return null;
    }


    /**
     * 转换语音文件为Mp3 ，可H5的audio播放
     * https://www.douban.com/note/262040939/
     */
    public File convertAmr2Mp3(InputStream is, String fileName) throws IOException {
        File amrFile = null;
        File target = null;
        try {
            long startCound = System.nanoTime();
            amrFile = getFileFromConnection(is, fileName);

            target = new File(ENVIRONMENTROOTPATH + fileName + ".mp3");
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libmp3lame");
           /*
            audio.setBitRate(new Integer(128000));//码率高低直接影响音质，码率高音质好，码率低音质差。
            audio.setChannels(new Integer(1)); //单声道
            audio.setSamplingRate(new Integer(16000)); //采样率
            */
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setFormat("mp3");
            attrs.setAudioAttributes(audio);
            Encoder encoder = new Encoder();
            try {
                encoder.encode(amrFile, target, attrs);
                log.info("转换[{}]的mp3格式耗时[{}]", fileName, String.valueOf(System.nanoTime() - startCound));
            } catch (EncoderException e) {
                log.error("convertAmr2Mp3 error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
            return target;
        } finally {
            if (amrFile != null && amrFile.exists() && amrFile.length() > 0) {
                amrFile.delete();
            }
        }
    }

    private File getFileFromConnection(InputStream is, String fileName) throws IOException {
        File tmpSaveFolder = new File(ENVIRONMENTROOTPATH);
        if (!tmpSaveFolder.exists()) {
            tmpSaveFolder.mkdir();
            tmpSaveFolder.setWritable(true);
        }

        //流写出文件
        File amrFile = new File(ENVIRONMENTROOTPATH + fileName + ".amr");

        FileUtils.copyInputStreamToFile(is, amrFile);

        return amrFile;
    }


    /**
     * 获取环信扩展体的busType
     *
     * @param requestMode
     * @return
     */
    private String getExtBusTypeByRequestMode(Integer requestMode) {
        //0转诊  1会诊 2咨询 3在线续方  5专家会诊  6 随访  7 专家解读  8群聊
        String busType = "2";
        if (ConsultConstant.CONSULT_TYPE_RECIPE.equals(requestMode)) {
            busType = "3";
        } else if (ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(requestMode)) {
            busType = "7";
        }

        return busType;
    }


    /**
     * 患者发起咨询时的一些默认操作>
     * step 1: 记录病情描述消息
     * step 2: 记录系统通知消息
     * step 3: 向医生发送默认消息
     * step 4: 记录默认消息
     *
     * @param consult
     */
    public void defaultHandleWhenPatientStartConsult(Consult consult) {
        try {
            // hx消息附加属性
            Patient requestPatient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(consult.getRequestMpi());
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(consult.getMpiid());
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", consult.getConsultId().toString());
            ext.put("busType", getExtBusTypeByRequestMode(consult.getRequestMode()));
            ext.put("name", requestPatient.getPatientName());
            ext.put("groupName", patient.getPatientName() + "的" + DictionaryController.instance().get("eh.bus.dictionary.RequestMode").getText(consult.getRequestMode()));
            ext.put("avatar", requestPatient.getPhoto() == null ? null : requestPatient.getPhoto().toString());
            ext.put("gender", requestPatient.getPatientSex());
            ext.put("appId", consult.getAppId());
            ext.put("openId", consult.getOpenId());
            ext.put(ConsultConstant.UUIDKEY, String.valueOf(UUID.randomUUID()));
            Date sendTime = new Date();

            // 插入患者病情描述消息到消息表
            handlePatientDiseaseDescriptionMsg(consult, sendTime, newMap(ext));
            if (ValidateUtil.notBlankString(consult.getReportInfo())) {
                //判断是否是由报告单发起的咨询，若是，则向医生发送该报告单信息
                handleReportInfoMsg(consult, sendTime, newMap(ext));
            }
            String notificationText = "医生是利用休息时间为您解答的，请耐心等待。";
            SystemNotificationMsgBody msgObj = new SystemNotificationMsgBody();
            msgObj.setType(ConsultConstant.SYSTEM_MSG_TYPE_WITHOUT_LINK);
            msgObj.setText(notificationText);
            msgObj.setUrl(null);
            // 插入系统通知消息到消息表
            handleSystemNotificationMessage(consult.getConsultId(), msgObj, sendTime, consult.getRequestMode());

            if (ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())) {
                //此处扩展信息为药品ID数据
                String extData = consult.getExtData();
                if (ValidateUtil.notBlankString(extData)) {
                    DrugList drug = DAOFactory.getDAO(DrugListDAO.class).get(Integer.parseInt(extData));
                    if (null != drug) {
                        handleDrugMsg(consult, drug, sendTime, newMap(ext));
                    }
                }
            }

            // 向医生发送默认消息并将此消息插入消息表，默认消息：医生，您好！
            handleHelloDoctorMsg(consult, sendTime, newMap(ext));

            if (ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())) {
                //如果用户没有完善信息(没有身份证或者医保)
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient _p = patientDAO.getByMpiId(consult.getMpiid());
                if (null != _p && (StringUtils.isEmpty(_p.getIdcard()) || StringUtils.isEmpty(_p.getPatientType()))) {
                    //插入完善身份信息
                    handleConsummateIdentityMsg(consult);
                }
            }

        } catch (Exception e) {
            log.error(LocalStringUtil.format("defaultHandleWhenPatientStartConsult error! params|consult[{}],errorMessage[{}],stackTrace0[{}]", consult, e.getMessage(), e.getStackTrace()[0]));
        }
    }

    /**
     * 心意支付成功后，发送聊天消息至消息框
     *
     * @param mindGiftId
     */
    public void sendMindGiftNotificationMessage(Integer mindGiftId) {
        try {
            MindGiftDAO mindDAO = DAOFactory.getDAO(MindGiftDAO.class);
            MindGift mind = mindDAO.get(mindGiftId);

            //展示在聊天框中的内容()
            MindGiftNotificationMsgBody msg = new MindGiftNotificationMsgBody();
            msg.setFiltText(mind.getFiltText());
            msg.setGiftIcon(mind.getGiftIcon() == null ? "0" : mind.getGiftIcon().toString());
            msg.setMindGiftId(mindGiftId.toString());
            msg.setMindGiftTitle("【赠送】心意");

            Date now = new Date();

            ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
            Consult consult = consultDao.get(mind.getBusId());

            PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
            Patient requestPatient = patDao.get(consult.getRequestMpi());
            Patient patient = patDao.get(consult.getMpiid());

            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", consult.getConsultId().toString());
            ext.put("busType", getExtBusTypeByRequestMode(consult.getRequestMode()));
            ext.put("name", requestPatient.getPatientName());
            ext.put("groupName", patient.getPatientName() + "的" + DictionaryController.instance().get("eh.bus.dictionary.RequestMode").getText(consult.getRequestMode()));
            ext.put("avatar", requestPatient.getPhoto() == null ? null : requestPatient.getPhoto().toString());
            ext.put("gender", requestPatient.getPatientSex());
            ext.put(ConsultConstant.UUIDKEY, String.valueOf(UUID.randomUUID()));

            // 插入心意消息到消息表
            TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
            tMsg.setConsultId(consult.getConsultId());
            tMsg.setMsgType(MsgTypeEnum.MIND_GIFT.getId() + "");
            tMsg.setMsgContent(JSONObject.toJSONString(msg));
            tMsg.setRequestMode(consult.getRequestMode());
            tMsg.setSendTime(now);
            tMsg.setCreateTime(now);
            tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY));

            this.addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);


            // 向医生发送心意消息
            String content = packMindGiftForApp(JSONObject.toJSONString(msg));

            if (ValidateUtil.notBlankString(content)) {
                AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), content, ext);
            }
            TimeUnit.MILLISECONDS.sleep(300);
            log.info(LocalStringUtil.format("sendMindGiftNotificationMessage send huanxin IM msg to app, parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), tMsg.getMsgContent(), ext));


        } catch (Exception e) {
            log.error(LocalStringUtil.format("sendMindGiftNotificationMessage error! params|mindGiftId[{}],errorMessage[{}]", mindGiftId, e.getMessage()));
        }
    }

    private String packMindGiftForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://mindgift?json=";
        return prefix + URLEncoder.encode(msgContent, "UTF-8");
    }

    private Map<String, String> newMap(Map<String, String> ext) {
        Map<String, String> newMap = Maps.newHashMap(ext);
        newMap.put(ConsultConstant.UUIDKEY, String.valueOf(UUID.randomUUID()));
        return newMap;
    }

    private void handleHelloDoctorMsg(Consult consult, Date sendTime, Map<String, String> ext) {
        String firstDefaultMsgContent = "医生，您好！";
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consult.getConsultId());
        tMsg.setMsgType(MsgTypeEnum.TEXT.getId() + "");
        tMsg.setMsgContent(firstDefaultMsgContent);
        tMsg.setRequestMode(consult.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY)); // hxMsgId获取不到
        addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        // 向医生发送消息
        AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), firstDefaultMsgContent, ext);
        log.info(LocalStringUtil.format("handleHelloDoctorMsg sendIM parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), firstDefaultMsgContent, ext));

    }

    private void handleReportInfoMsg(Consult consult, Date sendTime, Map<String, String> ext) throws UnsupportedEncodingException, InterruptedException {
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consult.getConsultId());
        tMsg.setMsgType(MsgTypeEnum.REPORT.getId() + "");
        tMsg.setMsgContent(consult.getReportInfo());
        tMsg.setRequestMode(consult.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY)); // hxMsgId获取不到
        addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        String content = packReportForApp(consult.getReportInfo());
        AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), content, ext);
        TimeUnit.MILLISECONDS.sleep(300);
        log.info(LocalStringUtil.format("handleReportInfoMsg sendIM parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), tMsg.getMsgContent(), ext));
    }

    private String packReportForApp(String reportInfo) throws UnsupportedEncodingException {
        String msgBody = fullfillLabelReportIdForReport(reportInfo);
        String content = "text://examination?json=" + URLEncoder.encode(msgBody, "UTF-8");
        return content;
    }

    /**
     * 药品标签消息
     *
     * @param consult
     * @param drug
     * @param sendTime
     * @param ext
     */
    private void handleDrugMsg(Consult consult, DrugList drug, Date sendTime, Map<String, String> ext) {
        String msgContent = JSONUtils.toString(drug);
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consult.getConsultId());
        tMsg.setMsgType(MsgTypeEnum.DRUG.getId() + "");
        tMsg.setMsgContent(msgContent);
        tMsg.setRequestMode(consult.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY)); // hxMsgId获取不到
        addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        try {
            String jsonString = packDrugForApp(msgContent);
            // 向医生发送消息
            AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), jsonString, ext);
            log.info(LocalStringUtil.format("handleDrugMsg sendIM parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), jsonString, ext));
        } catch (Exception e) {
            log.info(LocalStringUtil.format("class[{}] method[{}] encode exception! message[{}]", this.getClass().getSimpleName(), "packageConsultPDD", e.getMessage()));
        }
    }

    private String packDrugForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://drug?json=";
        return prefix + URLEncoder.encode(msgContent, "UTF-8");
    }

    /**
     * 处方标签消息
     *
     * @param consult
     * @param recipeTagMsgBean
     */
    public void handleRecipeMsg(Consult consult, RecipeTagMsgBean recipeTagMsgBean) {
        try {
            String msgContent = ctd.util.JSONUtils.toString(recipeTagMsgBean);
            Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(consult.getConsultDoctor());
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(consult.getMpiid());
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", consult.getConsultId().toString());
            ext.put("busType", getExtBusTypeByRequestMode(consult.getRequestMode()));
            ext.put("name", doctor.getName());
            ext.put("groupName", patient.getPatientName() + "的" + DictionaryController.instance().get("eh.bus.dictionary.RequestMode").getText(consult.getRequestMode()));
            ext.put("avatar", doctor.getPhoto() == null ? null : doctor.getPhoto().toString());
            ext.put("gender", doctor.getGender());
            ext.put("appId", consult.getAppId());
            ext.put("openId", consult.getOpenId());
            ext.put(ConsultConstant.UUIDKEY, String.valueOf(UUID.randomUUID()));
            TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
            tMsg.setConsultId(consult.getConsultId());
            tMsg.setMsgType(MsgTypeEnum.RECIPE.getId() + "");
            tMsg.setMsgContent(msgContent);
            tMsg.setRequestMode(consult.getRequestMode());
            Date sendTime = new Date();
            tMsg.setSendTime(sendTime);
            tMsg.setCreateTime(sendTime);
            tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY));

            //将处方卡片消息添加至数据库
            addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_DOCTOR);

            String jsonString = packRecipeForApp(msgContent);
            // 向患者发送消息
            AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByDoctorUrt(consult.getConsultDoctorUrt(), consult.getSessionID(), jsonString, ext);

            //聊过天后将hasChat设为true
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
            consultDAO.updateHasChat(consult.getConsultId());

            //参数1 发送者身份
            log.info(LocalStringUtil.format("handleRecipeMsg sendIM parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getConsultDoctorUrt(), consult.getSessionID(), jsonString, null));
        } catch (Exception e) {
            log.info(LocalStringUtil.format("class[{}] method[{}] encode exception! message[{}]", this.getClass().getSimpleName(), "packageConsultPDD", e.getMessage()));
        }

    }

    private String packRecipeForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://recipe?json=";
        String encoderString = URLEncoder.encode(msgContent, "UTF-8");
        return prefix + encoderString.replaceAll("\\+", "%20");
    }

    private String packFollowAssessForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://followupmeun?json=";
        return prefix + URLEncoder.encode(msgContent, "UTF-8");
    }

    private String packArticleForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://article?json=";
        return prefix + URLEncoder.encode(msgContent, "UTF-8");
    }

    private String packHealthAssessForApp(String msgContent) throws UnsupportedEncodingException {
        String prefix = "text://assess?json=";
        return prefix + URLEncoder.encode(msgContent, "UTF-8");
    }

    /**
     * 完善信息消息
     *
     * @param consult
     */
    private void handleConsummateIdentityMsg(Consult consult) {
        String consultName = ParamUtils.getParam(ParameterConstant.KEY_RECIPE_CONSULT_NAME);
        String msgContent = "您好，欢迎来到" + consultName + "，您可以在线咨询、配药、获得就医帮助。请先<a class='btn-finishInfo'>补充完善信息</a>，方便您后续就诊。";
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consult.getConsultId());
        tMsg.setMsgType(MsgTypeEnum.BEFORE_CHAT_MSG.getId() + "");
        tMsg.setMsgContent(msgContent);
        tMsg.setRequestMode(consult.getRequestMode());
        Date sendTime = new Date();
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(sendTime);
        tMsg.setHxMsgId(null); // hxMsgId获取不到
        addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
        log.info(LocalStringUtil.format("handleConsummateIdentityMsg sendIM parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), msgContent, null));
    }

    private String fullfillLabelReportIdForReport(String reportInfoJsonString) {
        ReportInfo reportInfo = JSONObject.parseObject(reportInfoJsonString, ReportInfo.class);
        String reportType = reportInfo.getRePortType();
        Integer organId = reportInfo.getOrganID();
        String reportId = reportInfo.getReportID();
        String mpiId = reportInfo.getMpiID();
        QueryLabReportsDetailService queryLabReportsDetailService = AppContextHolder.getBean("queryLabReportsDetailService", QueryLabReportsDetailService.class);
        Integer labReportId = queryLabReportsDetailService.getDbReportId(reportType, reportId, organId, mpiId);
        reportInfo.setLabReportId(labReportId);
        return JSONObject.toJSONString(reportInfo);
    }

    /**
     * 内部方法：供其他service调用
     * 处理患者病情描述信息，放入消息表
     *
     * @param consult
     */
    public void handlePatientDiseaseDescriptionMsg(Consult consult, Date sendTime, Map<String, String> ext) throws InterruptedException {
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(consult.getMpiid());

        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(3, consult.getConsultId());
        PatientDiseaseDescription pdd = new PatientDiseaseDescription();
        pdd.setName(patient.getPatientName());
        pdd.setAge(DateConversion.getAge(patient.getBirthday()) + "岁");
        pdd.setSex(patient.getPatientSex());
        //判断是否包含问卷
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        String answer = "";
        if (consultDAO.setConsultQuestionnaire(consult)) {
            //若包含问卷则拼装问卷信息，返回给前段调用
            answer = consult.getQuestionnaire().getQuestionDesc() + "\n";
        }
        pdd.setDesc(answer + consult.getLeaveMess());
        pdd.setConsultId(consult.getConsultId());
        if (ValidateUtil.blankList(cdrOtherdocs)) {
            pdd.setImgUrl("");
        } else {
            pdd.setImgUrl(String.valueOf(cdrOtherdocs.get(0).getDocContent()));
        }
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consult.getConsultId());
        tMsg.setMsgType(MsgTypeEnum.PATIENT_DISEASE_DESCRIPTION.getId() + "");
        tMsg.setMsgContent(JSONObject.toJSONString(pdd));
        tMsg.setRequestMode(consult.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(ext.get(ConsultConstant.UUIDKEY));

        this.addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        // 向医生发送该咨询单病情描述消息
        String content = packageConsultPDD(pdd, cdrOtherdocs);
        if (ValidateUtil.notBlankString(content)) {
            AsyncMsgSenderExecutor.sendEaseMobMsgToGroupByPatientUrt(consult.getRequestMpiUrt(), consult.getSessionID(), content, ext);
        }
        TimeUnit.MILLISECONDS.sleep(300);
        log.info(LocalStringUtil.format("handlePatientDiseaseDescriptionMsg send huanxin IM msg to app, parameters: urt[{}],sId[{}],msg[{}],ext[{}]", consult.getRequestMpiUrt(), consult.getSessionID(), tMsg.getMsgContent(), ext));


    }

    private String packageConsultPDD(PatientDiseaseDescription pdd, List<Otherdoc> otherDocs) {
        String prefix = "text://inquire?json=";
        List<Integer> list = new ArrayList<>();
        if (ValidateUtil.notBlankList(otherDocs)) {
            for (Otherdoc o : otherDocs) {
                if (ValidateUtil.notNullAndZeroInteger(o.getDocContent())) {
                    list.add(o.getDocContent());
                }
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", pdd.getConsultId());
        map.put("name", pdd.getName());
        map.put("age", pdd.getAge().replaceAll("\\D", ""));
        map.put("sex", pdd.getSex());
        map.put("desc", pdd.getDesc());
        map.put("diagianName", null);
        map.put("imgUrl", list);
        Consult consult = DAOFactory.getDAO(ConsultDAO.class).getById(pdd.getConsultId());
        Integer doctorId = ValidateUtil.nullOrZeroInteger(consult.getExeDoctor()) ? consult.getConsultDoctor() : consult.getExeDoctor();
        String docName = DAOFactory.getDAO(DoctorDAO.class).getNameById(doctorId);
        map.put("docName", docName);
        String jsonString = JSONUtils.toString(map);
        try {
            return prefix + URLEncoder.encode(jsonString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info(LocalStringUtil.format("class[{}] method[{}] encode exception! message[{}]", this.getClass().getSimpleName(), "packageConsultPDD", e.getMessage()));
        }
        return null;
    }

    /**
     * 内部方法：供其他service调用
     * 处理系统通知消息
     *
     * @param consultId
     * @param msgObj
     */
    public void handleSystemNotificationMessage(Integer consultId, SystemNotificationMsgBody msgObj) {
        BusConsultMsg msg = DAOFactory.getDAO(ConsultMessageDAO.class).getLatestMsgByConsultId(consultId);
        Date sendTime = new Date();
        if (msg == null || msg.getSendTime() == null) {
            log.error(LocalStringUtil.format(" error, the latest msg not exists or sendTime is null! cId[{}]", consultId));
        } else {
            int fiveMinuteMillisSeconds = 5 * 60 * 1000;
            long minusMillisSeconds = sendTime.getTime() - msg.getSendTime().getTime();
            if (minusMillisSeconds < fiveMinuteMillisSeconds && minusMillisSeconds >= 0) {
                sendTime = msg.getSendTime();
            }
        }
        String msgContent = JSONObject.toJSONString(msgObj);
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consultId);
        tMsg.setMsgType(String.valueOf(MsgTypeEnum.SYSTEM.getId()));
        tMsg.setMsgContent(msgContent);
        tMsg.setRequestMode(msg == null ? ConsultConstant.CONSULT_TYPE_GRAPHIC : msg.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(null);
        this.addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_SYSTEM);
    }

    /**
     * 内部方法：供其他service调用
     * 处理系统通知消息
     *
     * @param consultId
     * @param msgObj
     * @param sendTime
     */
    public void handleSystemNotificationMessage(Integer consultId, SystemNotificationMsgBody msgObj, Date sendTime, Integer requestMode) {
        String msgContent = JSONObject.toJSONString(msgObj);
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consultId);
        tMsg.setMsgType(String.valueOf(MsgTypeEnum.SYSTEM.getId()));
        tMsg.setMsgContent(msgContent);
        tMsg.setSendTime(sendTime);
        tMsg.setRequestMode(requestMode);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(null);
        this.addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_SYSTEM);
    }

    /**
     * 内部方法：供其他service调用
     * 处理评价通知消息
     *
     * @param consultId
     * @param msgObj
     * @author zhangsl
     * @Date 2016-11-16 18:20:05
     */
    public void handleEvaluationNotificationMessage(Integer consultId, EvaluationNotificationMsgBody msgObj) {
        BusConsultMsg msg = DAOFactory.getDAO(ConsultMessageDAO.class).getLatestMsgByConsultId(consultId);
        Date sendTime = new Date();
        if (msg == null || msg.getSendTime() == null) {
            log.error(LocalStringUtil.format(" error, the latest msg not exists or sendTime is null! cId[{}]", consultId));
        } else {
            int fiveMinuteMillisSeconds = 5 * 60 * 1000;
            long minusMillisSeconds = sendTime.getTime() - msg.getSendTime().getTime();
            if (minusMillisSeconds < fiveMinuteMillisSeconds && minusMillisSeconds >= 0) {
                sendTime = msg.getSendTime();
            }
        }
        String msgContent = JSONObject.toJSONString(msgObj);
        TempMsgBody4MQ tMsg = new TempMsgBody4MQ();
        tMsg.setConsultId(consultId);
        tMsg.setMsgType(String.valueOf(MsgTypeEnum.EVALUATION.getId()));
        tMsg.setMsgContent(msgContent);
        tMsg.setRequestMode(msg == null ? ConsultConstant.CONSULT_TYPE_GRAPHIC : msg.getRequestMode());
        tMsg.setSendTime(sendTime);
        tMsg.setCreateTime(new Date());
        tMsg.setHxMsgId(null);
        this.addConsultMessage(tMsg, ConsultConstant.MSG_ROLE_TYPE_SYSTEM);
    }

    /**
     * 内部方法：供其他service调用
     * 将 评价通知替换为评价内容
     *
     * @param consultId
     * @Date 2016-12-13 09:52:57
     * @author zhangsl
     * 新增处理图文咨询历史数据的情况
     */
    public void updateEvaluationNotificationMessage(Integer consultId, Double evaValue, String evaText) {
        ConsultMessageDAO msgDao = DAOFactory.getDAO(ConsultMessageDAO.class);
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult c = consultDao.getById(consultId);
        List<BusConsultMsg> sysMsgList = msgDao.findMessageListByConsultIdAndMsgType(consultId, MsgTypeEnum.EVALUATION.getId());
        try {
            if (sysMsgList.size() == 0 && ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(c.getRequestMode())) {//处理图文咨询历史数据
                EvaluationNotificationMsgBody msgObj = new EvaluationNotificationMsgBody();
                msgObj.setEvaSwitch(ConsultConstant.EVALUATION_MSG_SWITCH_ON);
                //wx2.9咨询会话页，评价成功后，去除原评价详情，系统自动发送消息，文案您已评价，可点此查看评价>>，点击跳转到评价详情页
//                msgObj.setEvaValue(String.valueOf(evaValue));
//                msgObj.setEvaText(evaText);
                msgObj.setText(evaText);
                msgObj.setType(String.valueOf(MsgTypeEnum.EVALUATION.getId()));
                this.handleEvaluationNotificationMessage(consultId, msgObj);
            } else {
                for (BusConsultMsg msg : sysMsgList) {
                    EvaluationNotificationMsgBody enm = JSONObject.parseObject(msg.getMsgContent(), EvaluationNotificationMsgBody.class);
                    if (ConsultConstant.EVALUATION_MSG_SWITCH_OFF == enm.getEvaSwitch()) {

                        //wx2.9咨询会话页，评价成功后，去除原评价详情，自动发送系统消息，文案您已评价，可点此查看评价>>，点击跳转到评价详情页
//                        enm.setEvaSwitch(ConsultConstant.EVALUATION_MSG_SWITCH_ON);
                        //msgObj.setEvaValue(String.valueOf(evaValue));
                        //msgObj.setEvaText(evaText);

                        SystemNotificationMsgBody sysBody = new SystemNotificationMsgBody();
                        sysBody.setType(MsgTypeEnum.EVALUATION.getId());
                        sysBody.setText(evaText);

                        msgDao.updateSystemNotificationMessageAndMsgType(msg.getId(), MsgTypeEnum.SYSTEM.getId(),
                                JSONObject.toJSONString(sysBody));
                    }
                }
            }
        } catch (Exception e) {
            log.error("updateSystemNotificationMessage error! consultId:" + consultId + "errorMessage:" + e.getMessage());
        }
    }

    /**
     * 内部方法：供其他service调用
     * 将 原有的系统通知消息中的"为医生的耐心解答点赞"替换为"已为医生的耐心解答点赞"
     *
     * @param consultId
     */
    public void updateSystemNotificationMessage(Integer consultId) {
        ConsultMessageDAO msgDao = DAOFactory.getDAO(ConsultMessageDAO.class);
        List<BusConsultMsg> sysMsgList = msgDao.findMessageListByConsultIdAndMsgType(consultId, MsgTypeEnum.SYSTEM.getId());
        try {
            for (BusConsultMsg msg : sysMsgList) {
                SystemNotificationMsgBody snm = JSONObject.parseObject(msg.getMsgContent(), SystemNotificationMsgBody.class);
                if (ConsultConstant.SYSTEM_MSG_TYPE_WITH_LINK == snm.getType()) {
                    snm.setText("咨询已结束，已为医生的耐心解答点赞");
                    msgDao.updateSystemNotificationMessage(msg.getId(), JSONObject.toJSONString(snm));
                }
            }
        } catch (Exception e) {
            log.error("updateSystemNotificationMessage error! consultId:" + consultId + "errorMessage:" + e.getMessage());
        }
    }

    @RpcService
    public PageResult<Map<String, Object>> findConsultMsgDetailWithDoctorId(Integer doctorId, int startIndex, int pageSize, String version, Integer requestMode) {
        if (startIndex != 0 && ValidateUtil.isNotNum(version)) {
            log.error(LocalStringUtil.format("findConsultMsgDetailWithDoctorId parameters invalid! doctorId[{}]startIndex[{}]version[{}]", doctorId, startIndex, version));
            throw new DAOException(609, "请求参数[version]有误");
        }
        Date firstRequestTime = new Date();
        if (startIndex != 0) {
            firstRequestTime = new Date(Long.valueOf(version.trim()));
        }
        // 获取当前患者信息
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");
        String requestMpi = patient.getMpiId();              //"2c9081814cd4ca2d014cd4ddd6c90000";
//        String requestMpi = "2c9081895576da7e01557738ec970000";
        // 修改该咨询对应的除语音消息之外的所有未读消息为已读
        DAOFactory.getDAO(ConsultMessageDAO.class).updateDoctorMessageToHasReadExceptAudio(requestMpi, doctorId, requestMode);
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setPageSize(pageSize);
        log.info(LocalStringUtil.format("findConsultMsgDetailWithDoctorId parameter, mpi[{}],docId[{}],firstRT[{}],startInd[{}],pageS[{}]", requestMpi, doctorId, firstRequestTime, startIndex, pageSize));
        List<BusConsultMsg> consultMessageList = DAOFactory.getDAO(ConsultMessageDAO.class).findDoctorConsultMessageList(requestMpi, doctorId, firstRequestTime, startIndex, pageSize, requestMode);
        try {
            pageResult.setNextIndex(startIndex + consultMessageList.size());
            pageResult.setVersion(firstRequestTime.getTime());
            List<Map<String, Object>> data = packageConsultMessageList(consultMessageList);
            pageResult.setData(data);
        } catch (Exception e) {
            log.error(LocalStringUtil.format("beanMapper exception! errorMsg[{}], errorStack[{}]", e.getMessage(), e.getStackTrace()[0]));
        } catch (Throwable e) {
            log.error(LocalStringUtil.format("beanMapper throwable! errorMsg[{}], errorStack[{}]", e.getMessage(), e.getStackTrace()[0]));
        }
        log.info(LocalStringUtil.format("findConsultMsgDetailWithDoctorId parameters: mpi[{}], pageResult[{}]", requestMpi, JSONObject.toJSON(pageResult)));
        return pageResult;
    }

    private List<Map<String, Object>> packageConsultMessageList(List<BusConsultMsg> consultMessageList) {
//        List<WxMsgVo> msgVoList = BeanMapper.mapList(consultMessageList, WxMsgVo.class);
        List<WxMsgVo> msgVoList = new ArrayList<>();
        for (BusConsultMsg bc : consultMessageList) {
            WxMsgVo vo = new WxMsgVo();
            vo.setId(bc.getId());
            vo.setMsgContent(bc.getMsgContent());
            vo.setSendTime(bc.getSendTime());
            vo.setCreateTime(bc.getCreateTime());
            vo.setMsgType(bc.getMsgType());
            vo.setReceiverId(bc.getReceiverId());
            vo.setReceiverRole(bc.getReceiverRole());
            vo.setSenderId(bc.getSenderId());
            vo.setSenderRole(bc.getSenderRole());
            vo.setHxMsgId(bc.getMsgExtra());
            vo.setHasRead(ValidateUtil.isTrue(bc.getHasRead()) ? 1 : 0);
            msgVoList.add(vo);
        }
        log.info(LocalStringUtil.format("after beanMapper, msgVoList[{}]", msgVoList));
        TempDataList<WxMsgVo> dataList = new TempDataList<>();
        Collections.reverse(msgVoList);
        String lastGroupTimeStr = null;
        for (WxMsgVo vo : msgVoList) {
            try {
                if (MsgTypeEnum.PATIENT_DISEASE_DESCRIPTION.getId() == vo.getMsgType() && vo.getMsgContent() != null) {
                    PatientDiseaseDescription pdd = JSONObject.parseObject((String) vo.getMsgContent(), PatientDiseaseDescription.class);
                    vo.setMsgContent(pdd);
                } else if (MsgTypeEnum.SYSTEM.getId() == vo.getMsgType() && vo.getMsgContent() != null) {
                    SystemNotificationMsgBody snm = JSONObject.parseObject((String) vo.getMsgContent(), SystemNotificationMsgBody.class);
                    vo.setMsgContent(snm);
                }
//                String dateStr = floorFiveMinutesAndConvertToLocalForm(vo.getCreateTime());
                lastGroupTimeStr = getTimeGroupByCompareSendTimeAndLast(vo, lastGroupTimeStr);
                dataList.add(lastGroupTimeStr, vo);
            } catch (Exception e) {
                log.error(LocalStringUtil.format("bean mapper failed! msgType:{}, sourceStr:{}, " +
                        "errorMessage:{}", vo.getMsgType(), vo.getMsgContent(), e.getMessage()));
            }
        }
        return dataList.getList();
    }

    private String getTimeGroupByCompareSendTimeAndLast(WxMsgVo vo, String lastGroupTimeStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            if (vo.getSendTime() != null) {
                return sdf.format(vo.getSendTime());
            }
            if (ValidateUtil.notBlankString(lastGroupTimeStr)) {
                Date lastTime = sdf.parse(lastGroupTimeStr);
                int fiveMinuteMillisSeconds = 5 * 60 * 1000;
                long minusMillisSeconds = vo.getCreateTime().getTime() - lastTime.getTime();
                if (minusMillisSeconds < fiveMinuteMillisSeconds && minusMillisSeconds >= 0) {
                    return sdf.format(lastTime);
                }
            }
        } catch (ParseException e) {
            log.error(LocalStringUtil.format("getTimeGroupByCompareSendTimeAndLast error, errorMessage[{}],lastGroupTimeStr[{}],vo[{}]", e.getMessage(), lastGroupTimeStr, vo));
        }
        return sdf.format(vo.getCreateTime());
    }


    /**
     * 局部容器类，用于存放有序的格式化数据
     *
     * @param <T>
     */
    static class TempDataList<T> {
        private static String DATE_KEY = "date";
        private static String LIST_KEY = "msgList";
        private List<Map<String, Object>> list = new ArrayList<>();

        public void add(String key, T t) {
            boolean exists = false;
            for (Map<String, Object> map : list) {
                if (key.equals(map.get(DATE_KEY))) {
                    List<T> subList = (List<T>) map.get(LIST_KEY);
                    subList.add(t);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                Map<String, Object> map = new HashMap<>();
                List<T> subList = new ArrayList<>();
                subList.add(t);
                map.put(DATE_KEY, key);
                map.put(LIST_KEY, subList);
                list.add(map);
            }

        }

        public List<Map<String, Object>> getList() {
            return list;
        }
    }

    /**
     * 根据给定时间 舍入5分钟，并格式化为固定格式字符串
     *
     * @param dbTime
     * @return
     */
    private String floorFiveMinutesAndConvertToLocalForm(Date dbTime) {
        //舍入 5分钟
        int fiveMinuteInMillisSeconds = 5 * 60 * 1000;
        int minusMillisSeconds = (int) (dbTime.getTime() % fiveMinuteInMillisSeconds);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dbTime);
        calendar.add(Calendar.MILLISECOND, -minusMillisSeconds);
        Date floorFiveMinutesDate = calendar.getTime();
        Date currentDate = new Date();
        // 根据给定规则格式化为字符串
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
        return sdf.format(floorFiveMinutesDate);
        /*  暂定为前台处理
        sdf.applyPattern("yyyy-MM-dd");
        String resultDateStr = "";
        if(sdf.format(floorFiveMinutesDate).equals(sdf.format(currentDate))){
            sdf.applyPattern("HH:mm");
            return sdf.format(calendar.getTime());
        }
        sdf.applyPattern("yyyy");
        if(sdf.format(floorFiveMinutesDate).equals(sdf.format(currentDate))){
            sdf.applyPattern("MM月dd日 HH:mm");
            return sdf.format(calendar.getTime());
        }else{
            sdf.applyPattern("yyyy年MM月dd日 HH:mm");
            return sdf.format(calendar.getTime());
        }
        */
    }


    /**
     * 微信端查询医生列表（包含未读消息条数，最新消息内容）
     *
     * @return
     */
    @RpcService
    public List<ChatVo> findDoctorListWithNotReadCount() {
        List<ChatVo> list = Lists.newArrayList();
        // 获取当前患者信息
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");
        String requestMpi = patient.getMpiId();       //"2c9081814cd4ca2d014cd4ddd6c90000";
//        String requestMpi = "2c9081814d48badc014d48cf97c80000"; //"ff80808154ff56e8015500ca6ea80007";
        if (ValidateUtil.blankString(requestMpi)) {
            log.error("findMsgListWithNotReadCount error, patient not login!");
            return list;
        }
        List<Consult> consultList = DAOFactory.getDAO(ConsultDAO.class).findAllConsultDoctors(requestMpi);
        List<BusConsultMsg> msgList = DAOFactory.getDAO(ConsultMessageDAO.class).findMessageListByMpiIdAndMsgType(requestMpi, MsgTypeEnum.FOLLOW_SCHEDULE.getId());
        List<TempMsgVo> voList = DAOFactory.getDAO(ConsultMessageDAO.class).findDoctorNotReadMessageList(requestMpi, packageConsultIdList(consultList, msgList));
        for (TempMsgVo vo : voList) {
            fullfillMsgVo(requestMpi, vo);
        }
        log.info(LocalStringUtil.format("findDoctorListWithNotReadCount parameters: mpi[{}], result[{}]", requestMpi, voList));
        List<FollowSimpleMsg> followSimpleMsgList = DAOFactory.getDAO(FollowChatMsgDAO.class).getFollowChatMsgForPatient(requestMpi, urt.getId());
        list.addAll(voList);
        list.addAll(followSimpleMsgList);
        Collections.sort(list);
        return list;
    }

    @RpcService
    public void addFollowScheduleConsultMessage(FollowSchedule follow) {
        String planTypeText = null;
        try {
            planTypeText = DictionaryController.instance().get("eh.mpi.dictionary.PlanType").getText(follow.getPlanType());
        } catch (ControllerException e) {
            log.error(LocalStringUtil.format("DictionaryController.instance().get(eh.mpi.dictionary.PlanType) error, errorMessage[{}], errorStackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        String msgContent = LocalStringUtil.format("[{}]{}", planTypeText, follow.getRemindContent());
        msgContent = msgContent.replaceAll(ConsultConstant.REGEX_EMOJI, "");
        BusConsultMsg msg = new BusConsultMsg();
        msg.setConsultId(follow.getId());
        msg.setHasRead(false);
        msg.setMpiId(follow.getMpiId());
        msg.setDoctorId(follow.getDoctorId());
        msg.setExeDoctorId(follow.getDoctorId());
        msg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
        msg.setSenderId(String.valueOf(msg.getDoctorId()));
        msg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        msg.setReceiverId(follow.getMpiId());
        msg.setMsgExtra(null);
        msg.setMsgType(MsgTypeEnum.FOLLOW_SCHEDULE.getId());
        msg.setMsgContent(msgContent);
        msg.setRequestMode(ConsultConstant.CONSULT_TYPE_GRAPHIC);
        msg.setAppId(null);
        msg.setOpenId(null);
        msg.setSendTime(new Date());
        msg.setDeleted(0);
        msg.setCreateTime(new Date());
        DAOFactory.getDAO(ConsultMessageDAO.class).save(msg);
    }

    /**
     * 内部方法：完善MsgVo信息
     *
     * @param vo
     */
    private void fullfillMsgVo(String requestMpi, TempMsgVo vo) {
        // 处理时间文本显示，规则：几分钟前、几小时前、昨天、前天、几天前、几个月前、几年前
        if (vo.getCreateTime() == null) {
            log.info("fullfillMsgVo part of params null, vo[{}]", JSONObject.toJSONString(vo));
            return;
        }
        vo.setTimeText(eh.utils.DateConversion.handleTimeText(vo.getCreateTime()));
        MsgTypeEnum msgTypeEnum = MsgTypeEnum.fromId(ConversionUtils.convert(vo.getMsgType(), short.class));
        // 处理最新一条消息显示文本
        if (ValidateUtil.notBlankString(vo.getMsgContent())) {
            if (MsgTypeEnum.SYSTEM.getId() == vo.getMsgType()) {
                SystemNotificationMsgBody snm = JSONObject.parseObject(vo.getMsgContent(), SystemNotificationMsgBody.class);
                if (snm != null && ValidateUtil.notBlankString(snm.getText())) {
                    vo.setMsgContent(snm.getText().replaceAll("<[.[^<>]]*>", ""));
                }
            } else if (MsgTypeEnum.PATIENT_DISEASE_DESCRIPTION.getId() == vo.getMsgType()) {
                PatientDiseaseDescription pdd = JSONObject.parseObject(vo.getMsgContent(), PatientDiseaseDescription.class);
                vo.setMsgContent(pdd.getDesc());
            } else if (MsgTypeEnum.GRAPHIC.getId() == vo.getMsgType()) {
                String text = "[图片]";
                vo.setMsgContent(text);
            } else if (MsgTypeEnum.AUDIO.getId() == vo.getMsgType()) {
                String text = "[语音]";
                vo.setMsgContent(text);
            } else if (MsgTypeEnum.VEDIO.getId() == vo.getMsgType()) {
                String text = "[视频]";
                vo.setMsgContent(text);
            } else if (MsgTypeEnum.REPORT.equals(msgTypeEnum)) {
                vo.setMsgContent("[" + msgTypeEnum.getName() + "]");
            }
        }
        // 处理咨询聊天参与用户属性信息
        Map<String, Object> userMap = new HashMap<>();
        List<Consult> consultList = DAOFactory.getDAO(ConsultDAO.class).findLatestConsultByMpiAndDoctorIdAndRequestMode(requestMpi, vo.getDoctorId(), vo.getRequestMode());
        if (ValidateUtil.notBlankList(consultList)) {
            vo.setLastConsultStatus(consultList.get(0).getConsultStatus());
            vo.setLastConsultId(consultList.get(0).getConsultId());
        } else {
            vo.setLastConsultId(0);
            vo.setLastConsultStatus(ConsultConstant.CONSULT_STATUS_FOR_FOLLOW);
        }
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(requestMpi);
        Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(vo.getDoctorId());
        Map<String, Object> patientMap = new HashMap<>();
        Map<String, Object> doctorMap = new HashMap<>();
        patientMap.put("mpiId", patient.getMpiId());
        patientMap.put("name", patient.getPatientName());
        patientMap.put("gender", patient.getPatientSex());
        patientMap.put("photo", patient.getPhoto());
        doctorMap.put("doctorId", doctor.getDoctorId());
        doctorMap.put("name", doctor.getName());
        doctorMap.put("gender", doctor.getGender());
        doctorMap.put("photo", doctor.getPhoto());
        doctorMap.put("organ", doctor.getOrgan());
        doctorMap.put("teams", doctor.getGroupMode());
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(doctor.getOrgan());
        doctorMap.put("organText", organ.getName());
        userMap.put(requestMpi, patientMap);
        userMap.put(String.valueOf(vo.getDoctorId()), doctorMap);
        vo.setUserMap(userMap);
    }

    /**
     * 内部方法：获取医生id列表
     *
     * @param consultList
     * @return
     */
    private List<Integer> packageConsultIdList(List<Consult> consultList, List<BusConsultMsg> msgList) {
        List<Integer> consultIdList = new ArrayList<>();
        if (ValidateUtil.notBlankList(consultList)) {
            for (Consult consult : consultList) {
                consultIdList.add((consult.getExeDoctor() == null ? consult.getConsultDoctor() : (ValidateUtil.notNullAndZeroInteger(consult.getGroupMode()) ? consult.getConsultDoctor() : consult.getExeDoctor())));
            }
        }
        if (ValidateUtil.notBlankList(msgList)) {
            for (BusConsultMsg msg : msgList) {
                consultIdList.add(msg.getDoctorId());
            }
        }
        return consultIdList;
    }

    /**
     * 内部方法：添加咨询消息到数据库
     *
     * @param tMsg
     * @param senderRole
     */
    private void addConsultMessage(TempMsgBody4MQ tMsg, String senderRole) {
        if (ValidateUtil.nullOrZeroInteger(tMsg.getConsultId())) {
            throw new DAOException(609, "addConsultMessage error! consultId is null or Zero consultId:" + tMsg.getConsultId());
        }
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        ConsultMessageDAO msgDao = DAOFactory.getDAO(ConsultMessageDAO.class);
        //咨询单
        Consult consult = consultDao.getById(tMsg.getConsultId());
        if (consult == null || ValidateUtil.nullOrZeroInteger(consult.getConsultId())) {
            throw new DAOException(609, "addConsultMessage error! db consult is null or Zero consultId:" + tMsg.getConsultId());
        }
        BusConsultMsg msg = new BusConsultMsg();
        msg.setConsultId(consult.getConsultId());
        msg.setHasRead(false);
        msg.setMpiId(consult.getRequestMpi());
        msg.setRequestMode(tMsg.getRequestMode());
        if (ValidateUtil.isTrue(consult.getTeams())) {
            if (ValidateUtil.notNullAndZeroInteger(consult.getGroupMode())) {
                msg.setDoctorId(consult.getConsultDoctor());
                msg.setExeDoctorId(tMsg.getSendDocID());
            } else {
                msg.setDoctorId(consult.getExeDoctor());
                msg.setExeDoctorId(tMsg.getSendDocID());
            }
        } else {
            msg.setDoctorId(consult.getConsultDoctor());
            msg.setExeDoctorId(tMsg.getSendDocID());
        }

        if (ConsultConstant.MSG_ROLE_TYPE_PATIENT.equals(senderRole)) {
            msg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
            msg.setSenderId(consult.getRequestMpi());
            msg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            msg.setReceiverId(String.valueOf(msg.getDoctorId()));
            msg.setHasRead(true);
        } else if (ConsultConstant.MSG_ROLE_TYPE_DOCTOR.equals(senderRole)) {
            msg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            msg.setSenderId(String.valueOf(msg.getDoctorId()));
            msg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
            msg.setReceiverId(consult.getRequestMpi());
        } else if (ConsultConstant.MSG_ROLE_TYPE_SYSTEM.equals(senderRole)) {
            // 目前系统消息主要指 “发给”患者的消息
            msg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_SYSTEM);
            msg.setSenderId(String.valueOf(-1));
            msg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
            msg.setReceiverId(consult.getRequestMpi());
            msg.setHasRead(true);
        } else {
            throw new DAOException("addConsultMessage error! senderRole type error, senderRole:" + senderRole);
        }
        msg.setMsgExtra(tMsg.getHxMsgId());
        msg.setMsgType(Short.valueOf(tMsg.getMsgType()));
        msg.setMsgContent(tMsg.getMsgContent());
        msg.setAppId(consult.getAppId());
        msg.setOpenId(consult.getOpenId());
        msg.setSendTime(tMsg.getSendTime());
        msg.setDeleted(0);
        msg.setCreateTime(tMsg.getCreateTime());
        msgDao.save(msg);
    }

    /**
     * 新增消息类型（MsgTypeEnum)插入到聊天记录界面时调用此方法，供后续新增需求时使用
     *
     * @param senderRole   发送者角色，定义参见ConsultConstant
     * @param receiverRole 接收者角色，定义参见ConsultConstant
     * @param mpiId        患者mpiId
     * @param doctorId     医生doctorId
     * @param msgType      消息类型，目前已有类型参见MsgTypeEnum枚举类，若没有可新增
     * @param busId        业务id（可选），如咨询id、报告单id等等，尽量填写
     * @param msgContent   消息内容，可为文本、json对象字符串，根据不同的msgType定义不同的字段值，可参考数据表（bus_consult_msg)对应字段值
     */
    @RpcService
    public void addChatMsgOutMsgType(String senderRole, String receiverRole, String mpiId, Integer doctorId, MsgTypeEnum msgType, Integer busId, String msgContent) {
        log.info("[{}] addChatMsgOutMsgType with params: sendRole[{}], receiverRole[{}], mpiId[{}], doctorId[{}], msgType[{}], busId[{}], msgContent[{}]", this.getClass().getSimpleName(), senderRole, receiverRole, mpiId, doctorId, msgType, busId, msgContent);
        BusConsultMsg msg = new BusConsultMsg();
        msg.setConsultId(busId);
        msg.setHasRead(true);
        msg.setMpiId(mpiId);
        msg.setDoctorId(doctorId);
        msg.setExeDoctorId(doctorId);
        msg.setSenderRole(senderRole);
        msg.setSenderId(filterUid(senderRole, mpiId, doctorId));
        msg.setReceiverRole(receiverRole);
        msg.setReceiverId(filterUid(receiverRole, mpiId, doctorId));
        msg.setMsgExtra(null);
        msg.setMsgType(msgType.getId());
        msg.setRequestMode(ConsultConstant.CONSULT_TYPE_GRAPHIC);
        msg.setMsgContent(msgContent);
        msg.setAppId(null);
        msg.setOpenId(null);
        msg.setSendTime(new Date());
        msg.setDeleted(0);
        msg.setCreateTime(new Date());
        DAOFactory.getDAO(ConsultMessageDAO.class).save(msg);
    }

    private String filterUid(String roleType, String mpiId, Integer doctorId) {
        String uid = null;
        switch (roleType) {
            case ConsultConstant.MSG_ROLE_TYPE_DOCTOR:
                uid = String.valueOf(doctorId);
                break;
            case ConsultConstant.MSG_ROLE_TYPE_PATIENT:
                uid = mpiId;
                break;
            case ConsultConstant.MSG_ROLE_TYPE_SYSTEM:
                uid = "-1";
                break;
            default:
                break;
        }
        return uid;
    }

    /**
     * 运营平台
     */
    @RpcService
    public QueryResult<BusConsultMsg> queryByConsultId(Integer consultId, int start, int limit) {
        if(consultId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"consultId is require");
        }
        ConsultMessageDAO consultMessageDAO = DAOFactory.getDAO(ConsultMessageDAO.class);
        Long total = consultMessageDAO.getCountByConsultId(consultId);
        if(total==null||total.intValue()<=0){
            return null;
        }
        List<BusConsultMsg> list = consultMessageDAO.findByConsultId(consultId, start, limit);
        return new QueryResult<BusConsultMsg>(total,start,limit,list);
    }

    /**
     * 咨询聊天页一些额外的配置
     *  yuanb 2017年7月3日11:34:49  app3.8.8版本 聊天页面添加开方温馨提醒，故此将提醒和送心意整合到一起
     * @param consultId
     * @return
     */
    @RpcService
    public Map<String,Map<String,Object>> consultExtraConfig (Integer consultId){
        RequestMindGiftService requestMindGiftService = AppContextHolder.getBean("eh.requestMindGiftService", RequestMindGiftService.class);

        Map<String,Map<String,Object>> config = Maps.newHashMap();
        Map<String,Object> gift = Maps.newHashMap();

        //关于送心意的配置
        Boolean canSendGiftByOrgan = requestMindGiftService.canSendGiftByOrgan(3,consultId);

        gift.put("canSendGiftByOrgan",canSendGiftByOrgan);
        //关于开方提醒的配置
        Map<String,Object>reminder = canShowReminderByConsult(consultId);

        config.put("gift",gift);
        config.put("reminder",reminder);
        return config;
    }

    public Map<String,Object> canShowReminderByConsult(Integer consultId){
        RecipeService recipeService = AppContextHolder.getBean("eh.recipeService",RecipeService.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        OrganBusAdvertisementDAO organBusAdvertisementDAO = DAOFactory.getDAO(OrganBusAdvertisementDAO.class);
        Consult consult = consultDAO.getById(consultId);
        Doctor consultDoctor = doctorDAO.getByDoctorId(consult.getConsultDoctor());

        Map<String,Object> reminder = Maps.newHashMap();
        reminder.put("reminderMsg",null);
        Boolean flag = consult.getRecipeReminderFlag();
        if(flag==null){
            flag = false;
        }
        if(flag){
            //已经显示过了  不显示
            log.info("已经显示过温馨提示，不再显示，consultId[{}]",consultId);
            return reminder;
        }
        if(!(consult.getConsultStatus().equals(ConsultConstant.CONSULT_STATUS_SUBMIT)||consult.getConsultStatus().equals(ConsultConstant.CONSULT_STATUS_HANDLING))){
            //该咨询状态不显示
            log.info("咨询单该状态不显示温馨提示，consultId[{}]",consultId);
            return reminder;
        }
        if(consult.getTeams()){
            //团队医生不显示开方提醒
            log.info("团队医生不显示温馨提示，consultId[{}]",consultId);
            return reminder;
        }
        Map<String,Object> canDoctorOpenRecipe = recipeService.openRecipeOrNot(consult.getConsultDoctor());
        if(canDoctorOpenRecipe==null||!(Boolean)canDoctorOpenRecipe.get("result")){
            //该医生所在机构不支持开处方
            log.info("该机构不支持开处方，consultId[{}]",consultId);
            return reminder;
        }else if(!consultDoctor.getUserType().equals(1)){
            //该医生不支持开处方
            log.info("该医生不支持开处方，consultId[{}]",consultId);
            return reminder;
        }

        //判断该公众号是否显示提醒
        Integer organId = consult.getConsultOrgan();
        if(organId==null){
            log.info("机构信息获取失败，不显示温馨提示，consultId[{}]",consultId);
            return reminder;
        }
        OrganBusAdvertisement organBusAdvertisement = organBusAdvertisementDAO.getByOrganIdAndBusType(consult.getConsultOrgan(),ConsultConstant.CONSULT_RECIPE_REMINDER);
        if(organBusAdvertisement == null){
            log.info("该机构不显示温馨提醒，consultId[{}]",consultId);
            return reminder;
        }
        String reminderMsg = organBusAdvertisement.getAdvertisement();
        reminder.put("reminderMsg",reminderMsg);

        //若成功显示温馨提醒则不再显示，只显示一次
        if(!(reminderMsg==null||reminderMsg.trim().equals(""))){
            consult.setRecipeReminderFlag(true);
            consultDAO.update(consult);
            log.info("成功显示温馨提醒，consultId[{}]",consultId);
        }

        return  reminder;
    }


}