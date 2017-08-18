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
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.service.DoctorInfoService;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.service.ObtainImageInfoService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.bus.Consult;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.mpi.Recommend;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RecommendDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariPayService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 咨询申请服务(包括不用付费的，要付费的)
 * Created by zhangx on 2016/5/24.
 */
public class RequestConsultService extends CommonConsultService {
    private static final Log logger = LogFactory.getLog(RequestConsultService.class);
    private AsynDoBussService asynDoBussService=AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 申请人/患者与目标医生不能为同一个人
     *
     * @param mpiId      患者
     * @param requestMpi 申请人分
     * @param doctorId   医生
     * @return true同一个人;false不是同一个人
     */
    @RpcService
    public Boolean isSameForPatsAndDocs(String mpiId, String requestMpi, Integer doctorId) {
        Boolean isSameForPatsAndDocs = false;

        //检验数据完整性
        isValidIsSameForPatsAndDocsData(mpiId, requestMpi, doctorId);

        PatientDAO dao= DAOFactory.getDAO(PatientDAO.class);
        Patient pat=dao.get(requestMpi);
        if(pat!=null){
            String idCard=pat.getCardId();
            if(StringUtils.isEmpty(idCard)){
                isSameForPatsAndDocs=true;
            }else{
                HashMap<String, Boolean> map = SameUserMatching.patientsAndDoctor(mpiId, requestMpi, doctorId);
                Boolean patSameWithDoc = map.get("patSameWithDoc");//患者是否和医生为同一个人,true为同一个人
                Boolean reqPatSameWithDoc = map.get("reqPatSameWithDoc");//判断申请人是否和医生为同一个人,true为同一个人

                if (patSameWithDoc && reqPatSameWithDoc) {
                    isSameForPatsAndDocs = true;
                }
            }
        }

        return isSameForPatsAndDocs;
    }

    /**
     * 是否能够申请图文咨询(点击图文咨询时)
     *
     * @param requestMpi
     * @param doctorId
     * @return true能申请;false不能申请
     */
    @RpcService
    public Boolean canRequestOnlineConsult(String requestMpi, Integer doctorId) {

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        //判断是否存在有未完成的图文咨询单(电话咨询返回true能申请)
        List<Consult> list = consultDao.findApplyingConsultByRequestMpi(requestMpi, ConsultConstant.CONSULT_TYPE_GRAPHIC);
        if (ValidateUtil.notBlankList(list)) {
            for(Consult c : list){
                if(ValidateUtil.nullOrZeroInteger(c.getPayflag())){
                    logger.info("requestMpiId[" + requestMpi + "]图文咨询还未支付，不能再发起咨询");
                    String title = "您有一条待支付的咨询单，请先处理哦！";
                    Map<String, Object> errorObj = Maps.newHashMap();
                    errorObj.put("title", title);
                    errorObj.put("consultId", c.getConsultId());
                    throw new DAOException(ErrorCode.CONSULT_PENDING, JSONObject.toJSONString(errorObj));
                }
            }
            logger.info("requestMpiId[" + requestMpi + "]图文咨询还未结束，不能再发起咨询");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "您的图文咨询还未结束，不能再发起咨询哦！");
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
                if (!teams) {//个人图文咨询单
                    //申请人/患者与目标医生不能为同一个人
                    if (patSameWithDoc && reqPatSameWithDoc) {
                        // 2016-6-18 luf:区分1秒弹框和确定，将 ErrorCode.SERVICE_ERROR 改成 608
                        logger.info("患者mpiId[" + mpiId + "],申请人requestMpi[" + requestMpi + "]与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
                        throw new DAOException(608, "申请人与目标医生不能为同一个人");
                    }
                }
            }
        }

        return true;
    }

    /**
     * 判断能否提交咨询申请
     *
     * @param consult 待申请的咨询单数据
     * @return
     */
    @RpcService
    public Boolean canSubmitConsult(Consult consult) {

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


    /**
     * 判断一个咨询单能不能申请
     *
     * @param consult
     * @return
     */
    public Boolean canRequestConsult(Consult consult) {
        Boolean returnFlag = true;//能申请

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        Integer requestMode = consult.getRequestMode();//咨询方式(1电话咨询2图文咨询3视频咨询)
        String requestMpiId = consult.getRequestMpi();
        String mpiId = consult.getMpiid();
        Integer doctorId = consult.getConsultDoctor();

        HashMap<String, Boolean> map = SameUserMatching.patientsAndDoctor(mpiId, requestMpiId, doctorId);
        Boolean patSameWithDoc = map.get("patSameWithDoc");//患者是否和医生为同一个人,true为同一个人
        Boolean beTeams = map.get("beTeams");//判断申请人和患者是否组成了医生团队，该团队只有申请人和患者,true为是

        if (null == requestMode) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestMode is needed");
        }

        //判断同一个医生同一个患者同一个申请人是否有未完成的图文/电话咨询单
        List<Consult> samelist = consultDao.findApplyingConsultByPatientsAndDoctor(mpiId, requestMpiId, doctorId, requestMode);
        if (samelist.size() > 0) {
            logger.info("requestMpiId[" + requestMpiId + "]重复提交");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
        }


        //图文咨询
        if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(requestMode) ) {

            //判断是否存在有未完成的图文咨询单(电话咨询返回true能申请)
            List<Consult> list = consultDao.findApplyingConsultByRequestMpi(requestMpiId, requestMode);
            if (list.size() > 0) {
                logger.info("requestMpiId[" + requestMpiId + "]图文咨询还未结束，不能再发起咨询");
                throw new DAOException(610, "您的图文咨询还未结束，不能再发起咨询哦！");
            }

            Boolean teams = consult.getTeams();
            if (teams) {//团队图文咨询单

                //若团队只有两个医生，且是一个是患者，一个是申请人，则在图文咨询申请页，点击确认的时候，弹框1s提示：申请条件不符合规则；
                if (beTeams) {
                    logger.info("患者mpiId[" + mpiId + "],申请人requestMpi[" + requestMpiId + "]与目标医生ConsultDoctor[" + doctorId + "]为同一个团队");
                    throw new DAOException(608, "申请条件不符合规则");
                }

            } else {//个人图文咨询单

                //患者与目标医生不能为同一个人
                if (patSameWithDoc) {
                    // 2016-6-18 luf:区分1秒弹框和确定，将 ErrorCode.SERVICE_ERROR 改成 608
                    logger.info("患者mpiId[" + mpiId + "],申请人requestMpi[" + requestMpiId + "]与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
                    throw new DAOException(608, "患者与目标医生不能为同一个人");
                }
            }
        }

        //电话咨询
        if (ConsultConstant.CONSULT_TYPE_POHONE.equals(requestMode) ) {
            //患者与目标医生不能为同一个人
            if (patSameWithDoc) {
                // 2016-6-18 luf:区分1秒弹框和确定，将 ErrorCode.SERVICE_ERROR 改成 608
                logger.info("患者mpiId[" + mpiId + "],与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
                throw new DAOException(608, "患者与目标医生不能为同一个人");
            }

            // 电话咨询预约时间过期
            if (consult.getAppointTime() != null) {
                Date appointTime = consult.getAppointTime();
                if (appointTime.before(DateConversion.getFormatDate(new Date(),"yyyy-MM-dd"))) {
                    returnFlag = false;
                    logger.info("电话咨询预约时间appointTime[" + appointTime + "]过期");
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "信息已过期，请重试");
                }
                Date appointEndTime = consult.getAppointEndTime();
                if (appointEndTime != null && !appointEndTime.after(new Date())) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "信息已过期，请重试");
                }

                // 2016-12-6 luf:微信2.7版本去除已约限制
                //判断时间是否已被预约
//                List<Consult> consults = consultDao.findConsultByDoctorAndTimePaied(
//                        doctorId, appointTime);
//                if (consults.size() > 0) {
//                    throw new DAOException(ErrorCode.SERVICE_ERROR, "啊哦！这个时段刚刚被约走了...");
//                }

            }
        }

        return returnFlag;
    }

    /**
     * @param consult      咨询单信息
     * @param cdrOtherdocs 文档信息
     * @return
     */
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
     * 咨询申请服务(新增其他病历文档保存)
     *
     * @param consult      咨询单信息
     * @param cdrOtherdocs 文档信息
     * @return String 支付订单号
     */
    @RpcService
    public Map<String, Object> requestConsultAndCdrOtherdoc(Consult consult,
                                                            final List<Otherdoc> cdrOtherdocs) {
        logger.info("咨询申请服务(新增其他病历文档保存)(requestConsultAndCdrOtherdoc):"
                + "consult" + JSONUtils.toString(consult) + ";cdrOtherdocs"
                + JSONUtils.toString(cdrOtherdocs));

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);

        Consult con = requestConsult(consult, cdrOtherdocs);

        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        Integer uid = 0;
        if (ure != null) {
            uid = ure.getId();
        }

        Map<String, Object> consultInfo  = consultDAO.getConsultAndPatientAndDoctorById(con.getConsultId(), uid);

        Map<String, Object> returnMap = new HashMap<String, Object>();
        returnMap.put("consultInfo", consultInfo);

        return returnMap;
    }


    /**
     * @param consult
     * @param payWay
     * @return Map<String,Object>
     * @function 患者咨询申请提交并发起支付
     * @author zhangx
     * @date 2016-05-24
     */
    @RpcService
    public Map<String, Object> submitConsultAndPay(Consult consult,
                                                   List<Otherdoc> cdrOtherdocs, String payWay) {
        logger.info("咨询申请服务(新增其他病历文档保存)(submitConsultAndPay):"
                + "consult" + JSONUtils.toString(consult) + ";cdrOtherdocs"
                + JSONUtils.toString(cdrOtherdocs));
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        try {
            Consult con = requestConsult(consult, cdrOtherdocs);
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            callbackParamsMap.put("price", con.getConsultPrice()==null?"0":String.valueOf(con.getConsultPrice()));
            callbackParamsMap.put("requestMode", con.getRequestMode()==null?"0":String.valueOf(con.getRequestMode()));
            Map<String, Object> map = payService.immediatlyPayForBus(payWay, BusTypeEnum.CONSULT.getCode(), con.getConsultId(), con.getConsultOrgan(), callbackParamsMap);

            UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
            Integer uid = 0;
            if (ure != null) {
                uid = ure.getId();
            }
            Map<String, Object> consultInfo = consultDAO.getConsultAndPatientAndDoctorById(con.getConsultId(), uid);
            map.put("consultInfo", consultInfo);
            return map;
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("[{}] submitConsultAndPay error, with params:consult[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(consult), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(e.getMessage());
        }
    }

    /**
     * 生成待支付的咨询单
     * @param consult
     * @param cdrOtherdocs
     * @return
     */
    @RpcService
    public Map<String, Object> requestUnPayConsult(Consult consult,
                                                   List<Otherdoc> cdrOtherdocs) {
        logger.info("咨询申请服务(新增其他病历文档保存)(requestPayConsult):"
                + "consult" + JSONUtils.toString(consult) + ";cdrOtherdocs"
                + JSONUtils.toString(cdrOtherdocs));
        consult.setPayflag(PayConstant.PAY_FLAG_NOT_PAY);
        consult.setConsultCost(consult.getConsultPrice());
        Consult con = requestConsult(consult, cdrOtherdocs);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("busId",con.getConsultId());
        return map;
    }


    /**
     * RequestConsult()数据校验
     *
     * @param consult
     */
    private void isValidRequestConsultData(Consult consult) {
        if (StringUtils.isEmpty(consult.getMpiid())) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(consult.getRequestMpi())) {
//            logger.error("requestMpi is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestMpi is required");
        }
        if (consult.getConsultDoctor() == null) {
//            logger.error("consultDoctor is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDoctor is required");
        }
        if (consult.getConsultOrgan() == null) {
//            logger.error("consultOrgan is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultOrgan is required");
        }
        if (consult.getConsultDepart() == null) {
//            logger.error("consultDepart is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDepart is required");
        }
        if (null == consult.getRequestMode()) {
//            logger.error("requestMode is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "requestMode is needed");
        }if(null == consult.getEmergency()){
            //防止老版本出错，给Emergency加一个默认值普通程度
            consult.setEmergency(0);
        }

        // 电话咨询预约时间不能为空
        if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE) && consult.getAppointTime() == null) {
//            logger.error("requestMode=1----->AppointTime is needed");
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
        // 获取签约标记，关注标记，关注ID
        //wx3.2  发现此处代码的查询结果没有被使用，故先注释
//        RelationDoctorDAO relationDao = DAOFactory
//                .getDAO(RelationDoctorDAO.class);
//        String mpi = consult.getRequestMpi();
//        Integer docId = consult.getConsultDoctor();
//        RelationDoctor relation = relationDao
//                .getByMpiIdAndDoctorIdAndRelationType(mpi, docId);
//        Boolean isSign = false;
//
//        if (relation != null) {
//            Integer type = relation.getRelationType();
//            if (type != null && type == 0) {
//                isSign = true;
//            }
//        }

        //wx2.7 2016-12-28 15:22:20 zhangx 使用优惠劵，咨询价格要用优惠劵重新计算
//        ConsultSet set = new DoctorInfoService().getDoctorDisCountSet(docId, mpi, isSign);
//
//        if (consult.getRequestMode() == ConsultConstant.CONSULT_TYPE_POHONE) {
//            //电话咨询
//            consult.setDisCountType(set.getAppointDisCountType());
//            consult.setConsultCost(set.getAppointConsultActualPrice());
//            consult.setConsultPrice(set.getAppointConsultPrice());
//        } else if (consult.getRequestMode() == ConsultConstant.CONSULT_TYPE_GRAPHIC) {
//            //图文咨询
//            consult.setDisCountType(set.getOnlineDisCountType());
//            consult.setConsultCost(set.getOnLineConsultActualPrice());
//            consult.setConsultPrice(set.getOnLineConsultPrice());
//        } else {
//            throw new DAOException(ErrorCode.SERVICE_ERROR,
//                    "目前不支持该业务哦！");
//        }

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
            consult.setOutTradeNo(BusTypeEnum.CONSULT.getApplyNo());// 支付商户订单号
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
            logger.info(LocalStringUtil.format("resertRequestConsultData exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        consult.setHasChat(false);
        consult.setRemindFlag(false);

        return consult;
    }

    /**
     * IsSameForPatsAndDocs()数据校验
     *
     * @param mpiId
     * @param requestMpi
     * @param doctorId
     */
    private void isValidIsSameForPatsAndDocsData(String mpiId, String requestMpi, Integer doctorId) {
        if (StringUtils.isEmpty(mpiId)) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(requestMpi)) {
//            logger.error("requestMpi is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestMpi is required");
        }

        if (null == doctorId) {
//            logger.error("doctorId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
    }


    /**
     * 患者再次发起图文咨询是需要的信息
     *
     * @param consultId
     * @return
     */
    @RpcService
    public Map<String, Object> getConsultSet(Integer consultId) {
        if (ValidateUtil.nullOrZeroInteger(consultId)) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        Map<String, Object> map = new HashMap<>();
        Consult consult = DAOFactory.getDAO(ConsultDAO.class).getById(consultId);
        Integer doctorId = consult.getExeDoctor() == null ? consult.getConsultDoctor() : consult.getExeDoctor();
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doc = doctorDao.getByDoctorId(doctorId);

        if (doc == null) {
            throw new DAOException(600, "医生" + doctorId + "不存在");
        }
        if (null == doc.getTeams()) {
            doc.setTeams(false);
        }
        Boolean teamsFlag = doc.getTeams();
        Employment emp = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctorId);
        doc.setDepartment(emp.getDepartment());
        // 获取签约标记，关注标记，关注ID
        RelationDoctor relation = DAOFactory.getDAO(RelationDoctorDAO.class)
                .getByMpiIdAndDoctorIdAndRelationType(consult.getRequestMpi(), doctorId);

        if (relation == null) {
            doc.setIsRelation(false);
            doc.setIsSign(false);
            doc.setRelationId(null);
        } else {
            Integer type = relation.getRelationType();
            Integer relationId = relation.getRelationDoctorId();
            doc.setIsSign(false);
            doc.setIsRelation(true);
            doc.setRelationId(relationId);
            if (type != null && type == 0) {
                doc.setIsSign(true);
            }
        }

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(doctorDao.doctorRelationNumber(doctorId));
        ConsultSet docSet = new DoctorInfoService().getDoctorDisCountSet(doctorId, consult.getRequestMpi(), doc.getIsSign());

        if (teamsFlag) {
            //是团队
            doc.setHaveAppoint(0);
//
//            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
//            List<Doctor> members = new DoctorGroupService().getTeamMembersForHealth(doctorId, 0, 32);
//            Long memberNum = groupDAO.getMemberNum(doctorId);
        } else {
            //不是团队

            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
            List<Object[]> oss = asDao.findTotalByDcotorId(doctorId, 1);// 患者端固定传1
            if (oss != null && oss.size() > 0) {
                doc.setHaveAppoint(1);
            } else {
                doc.setHaveAppoint(0);
            }
        }

        List<Recommend> list = DAOFactory.getDAO(RecommendDAO.class).findByMpiIdAndDoctorId(consult.getRequestMpi(), doctorId);
        for (Recommend recommend : list) {
            // 0特需预约1图文咨询2电话咨询
            Integer recommendType = recommend.getRecommendType();
            switch (recommendType) {
                case 0:
                    docSet.setPatientTransferRecomFlag(true);
                    break;
                case 1:
                    docSet.setOnLineRecomFlag(true);
                    break;
                case 2:
                    docSet.setAppointRecomFlag(true);
                    break;
                default:
                    break;
            }
        }
        map.put("consult", consult);
        map.put("doctor", doc);
        map.put("consultSet", docSet);
        return map;
    }


}
