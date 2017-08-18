package eh.bus.service.consult;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.account.session.SessionItemManager;
import ctd.account.user.UserRoleTokenEntity;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.dao.QuestionnaireDAO;
import eh.bus.service.ObtainImageInfoService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.bus.Consult;
import eh.entity.bus.Questionnaire;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.push.SmsPushService;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2017/2/14 0014.
 */
public class CommonConsultService {
    private static final Logger log = LoggerFactory.getLogger(CommonConsultService.class);

    private AsynDoBussService asynDoBussService=AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);


    /**
     * 能否申请图文咨询
     * @param requestMpi
     * @param doctorId
     * @param requestMode
     * @return
     */
    @RpcService
    public Boolean canRequestOnlineConsult(String requestMpi, Integer doctorId, Integer requestMode) {
        log.info("canRequestOnlineConsult start in, with param requestMpi[{}], doctorId[{}], reqeustMode[{}]", requestMpi, doctorId, requestMode);
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        //判断该患者和该医生是否有未结束的同种业务
        List<Consult> list = consultDao.findApplyingConsultByPatientsAndDoctorAndRequestMode(requestMpi, doctorId, requestMode);
        if(!requestMode.equals(ConsultConstant.CONSULT_TYPE_POHONE) && ValidateUtil.notBlankList(list)){
            for(Consult c : list){
                if(c.getConsultStatus().equals(ConsultConstant.CONSULT_STATUS_PENDING)){
                    log.info("requestMpiId[{}]图文咨询还未支付，不能再发起咨询",requestMpi);
                    String title = "您有一条待支付的咨询单，请先处理哦！";
                    Map<String, Object> errorObj = Maps.newHashMap();
                    errorObj.put("title", title);
                    errorObj.put("consultId", c.getConsultId());
                    throw new DAOException(ErrorCode.CONSULT_PENDING, JSONObject.toJSONString(errorObj));
                } else {
                    log.info("canRequestConsult exists not ended consult, requestMode[{}]", requestMode);
                    Map<String, Object> errorObj = Maps.newHashMap();
                    errorObj.put("status", ValidateUtil.notBlankString(c.getSessionID())?1:0);
                    errorObj.put("cid", c.getConsultId());
                    throw new DAOException(ErrorCode.REQUEST_MODE_EXISTS, JSONObject.toJSONString(errorObj));
                }
            }
        }
        PatientDAO dao= DAOFactory.getDAO(PatientDAO.class);
        Patient pat=dao.get(requestMpi);
        if(pat!=null) {
            String idCard = pat.getCardId();
            if (StringUtils.isEmpty(idCard)) {
                return true;
            } else {
                String mpiId = requestMpi;
                HashMap<String, Boolean> map = SameUserMatching.patientsAndDoctor(mpiId, requestMpi, doctorId);
                Boolean patSameWithDoc = map.get("patSameWithDoc");//患者是否和医生为同一个人,true为同一个人
                Boolean reqPatSameWithDoc = map.get("reqPatSameWithDoc");//判断申请人是否和医生为同一个人,true为同一个人

                Doctor targetDoctor = doctorDAO.get(doctorId);
                Boolean teams = targetDoctor.getTeams();

                if (null == teams) {
                    teams = false;
                }
               // if (!teams) {//个人图文咨询单
                    //申请人/患者与目标医生不能为同一个人
                    // 默认患者和就诊人相同，所以返回值patSameWithDoc=reqPatSameWithDoc
                    if (!teams && patSameWithDoc && reqPatSameWithDoc) {
                        // 2016-6-18 luf:区分1秒弹框和确定，将 ErrorCode.SERVICE_ERROR 改成 608
                        log.info("患者mpiId[{}],申请人requestMpi[{}]与目标医生ConsultDoctor[{}]为同一个人",mpiId,requestMpi,doctorId);
                        throw new DAOException(608, "申请人与目标医生不能为同一个人");
                    }
           //     }
            }
        }
        return true;
    }

    @RpcService
    public Boolean canSubmitConsult(Consult consult) {
        log.info("canSubmitConsult start in with param: consult[{}]", JSONObject.toJSONString(consult));
        //校验数据是否完整
        isValidRequestConsultData(consult);

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor targetDoctor = doctorDAO.get(consult.getConsultDoctor());
        Boolean teams = targetDoctor.getTeams();
        if (null == teams) {
            teams = false;
        }
        consult.setTeams(teams);

        return canRequestConsult(consult);
    }

    @RpcService
    public Map<String, Object> requestConsultAndCdrOtherdoc(Consult consult,
                                                            final List<Otherdoc> cdrOtherdocs) {
        log.info("咨询申请服务(新增其他病历文档保存)(requestConsultAndCdrOtherdoc):"
                + "consult" + JSONUtils.toString(consult) + ";cdrOtherdocs"
                + JSONUtils.toString(cdrOtherdocs));
        try {
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);

            //咨询单增加问卷信息
            addNewQuestionnaire(consult);
            Consult con = requestConsult(consult, cdrOtherdocs);

            UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
            Integer uid = 0;
            if (ure != null) {
                uid = ure.getId();
            }

            Map<String, Object> consultInfo = consultDAO.getConsultAndPatientAndDoctorById(con.getConsultId(), uid);

            Map<String, Object> returnMap = new HashMap<String, Object>();
            returnMap.put("consultInfo", consultInfo);

            return returnMap;
        }catch (Exception e){
            log.error("requestConsultAndCdrOtherdoc error, errorMessage[{}], stacktrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        }
    }

    /**
     * 咨询单增加问卷
     * @param consult
     */
    public void addNewQuestionnaire(Consult consult){
        if(null != consult.getQuestionnaire()){
            Questionnaire questionnaire = consult.getQuestionnaire();
            if(StringUtils.isEmpty(questionnaire.getMpiid())){
                questionnaire.setMpiid(consult.getMpiid());
            }
            if(questionnaire.getCreateTime() == null){
                questionnaire.setCreateTime(new Date());
            }
            QuestionnaireDAO questionnaireDAO = DAOFactory.getDAO(QuestionnaireDAO.class);
            //设置默认值
            Integer pregnent = questionnaire.getPregnent() == null ? -1 : questionnaire.getPregnent();
            questionnaire.setPregnent(pregnent);
            Integer alleric = questionnaire.getAlleric() == null ? 0 : questionnaire.getAlleric();
            questionnaire.setAlleric(alleric);
            Integer diseaseStatus = questionnaire.getDiseaseStatus() == null ? 0 : questionnaire.getDiseaseStatus();
            questionnaire.setDiseaseStatus(diseaseStatus);
            //将问卷结果存入consult对象
            questionnaire = questionnaireDAO.save(questionnaire);
            consult.setQuestionnaireId(questionnaire.getQuestionnaireId());
        }
    }

    public Consult requestConsult(Consult consult,
                                  final List<Otherdoc> cdrOtherdocs) {
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        //校验数据是否完整
        isValidRequestConsultData(consult);

        //填充相关数据(必填字段设置默认值)
        Consult tarConsult = resertRequestConsultData(consult);

        //判断能否咨询
        if (!canRequestConsult(tarConsult)) {
            return null;
        }
        //保存咨询单信息，及相对应的图片资料信息
        Consult consult2 = consultDao.saveConsult(tarConsult, cdrOtherdocs);

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.get(consult.getMpiid());
        Integer organId = consult.getConsultOrgan();

        new ObtainImageInfoService(p,organId).getImageInfo();

        if (!StringUtils.isEmpty(consult2)) {

            if (consult2.getPayflag().equals(1)) {
                doAfterPaySuccess(consult2.getConsultId());
            }

            // 保存日志
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.saveOperationRecordsForConsult(consult);

            return consult2;
        }
        return null;
    }

    /**
     * RequestConsult()数据校验
     *
     * @param consult
     */
    private void isValidRequestConsultData(Consult consult) {
        if (StringUtils.isEmpty(consult.getMpiid())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(consult.getRequestMpi())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestMpi is required");
        }
        if (consult.getConsultDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDoctor is required");
        }
        if (consult.getConsultOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultOrgan is required");
        }
        if (consult.getConsultDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDepart is required");
        }
        if (null == consult.getRequestMode()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestMode is needed");
        }

        // 电话咨询预约时间不能为空
        if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE) && consult.getAppointTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "AppointTime is needed");
        }
    }

    /**
     * 根据前端传入的相关数据，重新设置部分数据
     *
     * @param consult
     */
    private Consult resertRequestConsultData(Consult consult) {


        String requestMpiId = consult.getRequestMpi();

        if (null == consult.getDisCountType()) {
            consult.setDisCountType(0);//默认为无优惠
        }
        consult.setActualPrice(consult.getConsultCost());
        if (consult.getConsultCost() == 0) {
            consult.setPayflag(1);
            consult.setConsultStatus(ConsultConstant.CONSULT_STATUS_SUBMIT);
            if(ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())||
                    ConsultConstant.CONSULT_TYPE_RECIPE .equals( consult.getRequestMode()) ||
                    ConsultConstant.CONSULT_TYPE_GRAPHIC .equals( consult.getRequestMode())){
                if(ValidateUtil.isTrue(consult.getTeams())){
                    consult.setStatus(1);
                }else{
                    consult.setStatus(3);
                }
            }else{
                consult.setStatus(2);
            }
        } else {
            consult.setPayflag(0);
            consult.setConsultStatus(ConsultConstant.CONSULT_STATUS_PENDING);
            consult.setStatus(0);
        }
        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(consult.getAnswerTel())) {
            consult.setAnswerTel(DAOFactory.getDAO(PatientDAO.class)
                    .getMobileByMpiId(consult.getMpiid()));
        }

        UserSevice userService = new UserSevice();

        // 获取申请者urtid
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient requestMpi = patientDAO.get(requestMpiId);
        String requestMobile = requestMpi.getLoginId();
        int requestUrtId = userService.getUrtIdByUserId(requestMobile,
                "patient");
        consult.setRequestMpiUrt(requestUrtId);

        // 获取目标医生urtid
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        int targetDoctorId = consult.getConsultDoctor();
        Doctor targetDoctor = doctorDAO.get(targetDoctorId);
        Boolean teams = targetDoctor.getTeams();

        if (null == teams) {
            teams = false;
        }
        consult.setTeams(teams);

        //不是团队医生
        if (!teams) {
            String targetMobile = targetDoctor.getMobile();
            int targetUrtId = userService.getUrtIdByUserId(targetMobile, "doctor");
            consult.setConsultDoctorUrt(targetUrtId);
        }
        //将请求的微信appid保存到咨询记录表里
        try {
            SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
            if (wxAccount != null) {
                consult.setAppId(wxAccount.getAppId());
                consult.setOpenId(wxAccount.getOpenId());
            }
            Integer deviceId = SessionItemManager.instance().checkClientAndGet();
            consult.setDeviceId(deviceId);
        } catch (Exception e) {
            log.info(LocalStringUtil.format("resertRequestConsultData exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        consult.setHasChat(false);
        consult.setRemindFlag(false);
        //版本3.8.8 ，聊天页面新增开方温馨提醒，增加字段，默认为0：显示，1表示不显示
        consult.setRecipeReminderFlag(false);

        return consult;
    }

    /**
     * 判断一个咨询单能不能申请
     *
     * @param consult
     * @return
     */
    private Boolean canRequestConsult(Consult consult) {
        Boolean returnFlag = true;//能申请

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        Integer requestMode = consult.getRequestMode();//咨询方式(1电话咨询2图文咨询3视频咨询)
        String requestMpiId = consult.getRequestMpi();
        String mpiId = consult.getMpiid();
        Integer doctorId = consult.getConsultDoctor();

        HashMap<String, Boolean> map = SameUserMatching.patientsAndDoctor(mpiId, requestMpiId, doctorId);
        Boolean patSameWithDoc = map.get("patSameWithDoc");//患者是否和医生为同一个人,true为同一个人

        if (null == requestMode) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestMode is needed");
        }

        //患者与目标医生不能为同一个人
        if (patSameWithDoc) {
            log.info("患者mpiId[" + mpiId + "],与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
            throw new DAOException(608, "患者与目标医生不能为同一个人");
        }

        //判断该患者和该医生是否有未结束的同种业务
        List<Consult> samelist = consultDao.findApplyingConsultByPatientsAndDoctorAndRequestMode(requestMpiId, doctorId, requestMode);
        if(!requestMode.equals(ConsultConstant.CONSULT_TYPE_POHONE) && ValidateUtil.notBlankList(samelist)){
            for(Consult c : samelist){
                if(c.getConsultStatus().equals(ConsultConstant.CONSULT_STATUS_PENDING)){
                    log.info("requestMpiId[" + mpiId + "]图文咨询还未支付，不能再发起咨询");
                    String title = "您有一条待支付的咨询单，请先处理哦！";
                    Map<String, Object> errorObj = Maps.newHashMap();
                    errorObj.put("title", title);
                    errorObj.put("consultId", c.getConsultId());
                    throw new DAOException(ErrorCode.CONSULT_PENDING, JSONObject.toJSONString(errorObj));
                } else {
                    log.info("canRequestConsult exists not ended consult, requestMode[{}]", requestMode);
                    throw new DAOException(ErrorCode.REQUEST_MODE_EXISTS, String.valueOf(c.getConsultId()));
                }
            }
        }
        if (!requestMode.equals(ConsultConstant.CONSULT_TYPE_POHONE) && ValidateUtil.notBlankList(samelist)) {
            for(Consult c : samelist){
                log.info("canRequestConsult exists not ended consult, requestMode[{}]", requestMode);
                throw new DAOException(ErrorCode.REQUEST_MODE_EXISTS, String.valueOf(c.getConsultId()));
            }
        }

        //电话咨询
        if (ConsultConstant.CONSULT_TYPE_POHONE.equals(requestMode)) {
            // 电话咨询预约时间过期
            if (consult.getAppointTime() != null) {
                Date appointTime = consult.getAppointTime();
                if (appointTime.before(DateConversion.getFormatDate(new Date(),"yyyy-MM-dd"))) {
                    log.info("电话咨询预约时间appointTime[{}]过期",appointTime);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "信息已过期，请重试");
                }
                Date appointEndTime = consult.getAppointEndTime();
                if (appointEndTime != null && !appointEndTime.after(new Date())) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "信息已过期，请重试");
                }
            }
        }

        return returnFlag;
    }

    /**
     * 支付成功后需要进行的操作
     *
     * @param clinicId
     */
    public void doAfterPaySuccess(Integer clinicId) {
        log.info("doAfterPaySuccess...clinickId[{}]", clinicId);
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        Consult consult2 = consultDao.get(clinicId);
        Boolean teams = consult2.getTeams();
        asynDoBussService.fireEvent(new BussCreateEvent(consult2, BussTypeConstant.CONSULT));
        Integer clientId = null;
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        try {
            String executor = "PushDocWithPaySucc";
            if(consult2.getRequestMode()==4){
                executor = "PushDocWithPaySuccForRecipe";
            }
            smsPushService.pushMsgData2Ons(clinicId, consult2.getConsultOrgan(), executor, executor, clientId);
            log.info("doAfterPaySuccess..............");
        }catch (Exception e){
            log.error(LocalStringUtil.format("error.......message[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        if(teams){
            //微信2.9，向团队发起咨询，团队管理员收到短信，由于条件限制，另起一文件
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<Integer> administratorDoctors = doctorGroupDAO.findAdministratorByDoctorId(consult2.getConsultDoctor());
            log.info("咨询向管理员发送短信[{}]",JSONObject.toJSONString(administratorDoctors));
            if(ValidateUtil.notBlankList(administratorDoctors)) {
                smsPushService.pushMsgData2Ons(clinicId, consult2.getConsultOrgan(), "PushDocWithPaySuccForTeam", "PushDocWithPaySuccForTeam", clientId);
            }
        }
        // 目标医生不是团队医生 且为图文咨询，创建群聊
        if (!teams && (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(consult2.getRequestMode()) || ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult2.getRequestMode()) || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult2.getRequestMode()))) {
            GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
            Group group = groupDAO.createConsultGroup(clinicId);
            if (!StringUtils.isEmpty(group.getGroupId())) {
                consultDao.updateSessionStartTimeByConsultId(consult2.getRequestTime(),
                        clinicId);
                consultDao.updateSessionIDByConsultId(group.getGroupId(), clinicId);
                // 插入患者病情描述/系统消息/默认消息到医患消息表
                consult2.setSessionID(group.getGroupId());
                ConsultMessageService msgService = new ConsultMessageService();
                msgService.defaultHandleWhenPatientStartConsult(consult2);
                asynDoBussService.fireEvent(new BussAcceptEvent(consult2.getConsultId(), BussTypeConstant.CONSULT, consult2.getConsultDoctor()));
            }
        }
        //电话咨询:电话咨询申请成功发给申请人发微信
        if (1 == consult2.getRequestMode()) {
            //pushWxMsgToPatWithRequestConsult(consult2);
            smsPushService.pushMsgData2Ons(consult2.getConsultId(),consult2.getConsultOrgan(),"PushWxPatReqConsult","PushWxPatReqConsult",consult2.getDeviceId());
        }
    }
}
