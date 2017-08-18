package eh.bus.service.payment;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.ngari.his.appoint.mode.*;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.organ.OrganConfigService;
import eh.bus.dao.InHospitalPrepaidDAO;
import eh.bus.dao.OutpatientDAO;
import eh.bus.dao.OutpatientResponseDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.his.service.AppointTodayBillService;
import eh.bus.push.MessagePushExecutorConstant;
import eh.cdr.dao.RecipeDAO;
import eh.controller.PayController;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfig;
import eh.entity.bus.Outpatient;
import eh.entity.bus.PayBusiness;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.ConfirmOutpatientInput;
import eh.entity.bus.pay.ConfirmOutpatientOut;
import eh.entity.cdr.Recipe;
import eh.entity.his.PaymentResultRequest;
import eh.entity.his.fee.*;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.Address;
import eh.entity.mpi.Patient;
import eh.entity.mpi.PatientLastBehavior;
import eh.entity.mpi.PatientType;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.AddressDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.PatientLastBehaviorDAO;
import eh.mpi.dao.PatientTypeDAO;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.task.executor.WxRefundExecutor;
import eh.unifiedpay.constant.PayWayEnum;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariPayService;
import eh.wxpay.util.PayUtil;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by haoak on 2016/8/24.x
 * 门诊缴费和住院预交查询服务
 */
public class  QueryOutpatient {
    private static final Logger logger = LoggerFactory.getLogger(QueryOutpatient.class);

    private static final char HIS_RECIPEID_APPEND_CHAR = '|';
    public static final String HIS_RECIPEID_SPLIT_CHAR = "\\" + HIS_RECIPEID_APPEND_CHAR;

    private static String getAddressDic(String area) {
        if (StringUtils.isNotEmpty(area)) {
            try {
                return DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(area);
            } catch (ControllerException e) {
                logger.error("pushRecipInfo 获取地址数据类型失败*****area:" + area);
            }
        }
        return null;
    }

    private static void getAddr(Map<String, Object> recMsg) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patientLogin = (Patient) urt.getProperty("patient");
        PatientLastBehaviorDAO patientLastBehaviorDAO = DAOFactory.getDAO(PatientLastBehaviorDAO.class);
        AddressDAO addressDAO = DAOFactory.getDAO(AddressDAO.class);
        PatientLastBehavior patientLastBehavior = null;
        Address address = null;
        recMsg.put("recMode", 0);//没有默认配送选择行为
        recMsg.put("exflag", 0);//没有地址
        Map<String, Object> addressmap = new HashMap<String, Object>();
        if (patientLogin != null) {
            patientLastBehavior = patientLastBehaviorDAO.getByMpiId(patientLogin.getMpiId());
            if (patientLastBehavior != null) {
                address = addressDAO.getByAddressId(patientLastBehavior.getAddressID());
                if (patientLastBehavior.getReachHomeMode() == null) {
                    recMsg.put("recMode", 1);//上次默认医院取药
                } else {
                    recMsg.put("recMode", 2);//上次默认配送到家
                }
            } else {
                address = addressDAO.getLastAddressByMpiId(patientLogin.getMpiId());
            }
        }
        if (address != null) {
            addressmap = new HashMap<String, Object>();
            DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
            addressmap.put("addressId", address.getAddressId());
            addressmap.put("address1Text", getAddressDic(address.getAddress1()));
            addressmap.put("address2Text", getAddressDic(address.getAddress2()));
            addressmap.put("address3Text", getAddressDic(address.getAddress3()));
            addressmap.put("receiver", address.getReceiver());
            addressmap.put("recMobile", address.getRecMobile());
            addressmap.put("address1", address.getAddress1());
            addressmap.put("address2", address.getAddress2());
            addressmap.put("address3", address.getAddress3());
            addressmap.put("address4", address.getAddress4());
            addressmap.put("zipCode", address.getZipCode());
            recMsg.put("exflag", 1);//有地址（默认配送地址，或用户地址）
        }
        recMsg.put("address", addressmap);
    }

    // 判断一个字符是否是中文
    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;// 根据字节码判断
    }

    // 判断一个字符串是否含有中文
    private static boolean isChinese(String str) {
        if (str == null) return false;
        for (char c : str.toCharArray()) {
            if (isChinese(c)) return true;// 有一个中文字符就返回
        }
        return false;
    }

    /**getget
     * 申请门诊缴费
     * @param opList
     * @param totalFee
     * @param organId
     * @param payListParams
     * @return
     */
    @RpcService
    public Outpatient requestOutPatient(List<ConfirmOutpatientInput> opList, String totalFee, Integer organId, Map<String, String> payListParams){
        logger.info("门诊缴费参数"+payListParams);
        logger.info("requestOutPatient step in, params opList[{}], totalFee[{}], organId[{}], payListParams[{}]", JSONObject.toJSONString(opList), totalFee, organId, JSONObject.toJSONString(payListParams));
        if(ValidateUtil.blankList(opList) || ValidateUtil.nullOrZeroInteger(organId)){
            logger.info("requestOutPatient param invalid, opList[{}], organId", JSONObject.toJSONString(opList), organId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请选择项目");
        }
        try{
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient currentPatient = urt.getProperty("patient", Patient.class);
            String requestMpi = currentPatient.getMpiId();
            String mpiId = payListParams.get("MpiID");
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiId);
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ o = organDAO.getByOrganId(organId);

            ConfirmOutpatientOut nextStepParam = new ConfirmOutpatientOut();
            // 是否支持医保
            boolean isSupportMedicalInsurance = judgeIsSupportMedicalInsurance(mpiId, organId);
            nextStepParam.setSupportMedicalInsurance(isSupportMedicalInsurance);
            nextStepParam.setTotalFee(totalFee);
            // 组装并放入预结算his参数信息
            packHisPreSettleRequestParam(mpiId, opList, nextStepParam);
            nextStepParam.setOrganId(organId);
            nextStepParam.setPayListParams(payListParams);
            nextStepParam.setOrganizeCode(o.getOrganizeCode());

            OutpatientPreSettlementRequest preRequest = new OutpatientPreSettlementRequest();
            preRequest.setName(patient.getPatientName());
            preRequest.setCertID(patient.getRawIdcard());
            preRequest.setMobile(patient.getMobile());
            preRequest.setPatientID(nextStepParam.getPatientID());
            preRequest.setReceiptList(nextStepParam.getReceiptList());
            preRequest.setChargeTypeList(nextStepParam.getChargeTypeList());
            preRequest.setOrganizeCode(nextStepParam.getOrganizeCode());
            preRequest.setMobile(patient.getMobile());
            preRequest.setGuardianName(patient.getGuardianName());
            preRequest.setGuardianFlag(patient.getGuardianFlag());
            preRequest.setTotalFee(totalFee);
            OutpatientPreSettlementResponse preSettlementResponse = this.fetchPreSettlementForOutPatient(preRequest, nextStepParam.getOrganId());

            Outpatient op = new Outpatient();
            if(preSettlementResponse==null){
                return op;
            }
            op.setPatientID(preRequest.getPatientID());
            op.setOrderIds(preRequest.getReceiptList());
            op.setOrderTypes(preRequest.getChargeTypeList());
            op.setStatementNo(preSettlementResponse.getStatementNo());
            op.setChargeTamt(preSettlementResponse.getChargeTamt());
            op.setDiscountsAmt(preSettlementResponse.getDiscountsAmt());
            op.setPersonAmt(preSettlementResponse.getPersonAmt());
            op.setTotalFee(preSettlementResponse.getPersonAmt());
            op.setClinicNum("".equals(preSettlementResponse.getClinicNum())?"":preSettlementResponse.getClinicNum());
            op.setMedicalRecordNo("".equals(preSettlementResponse.getMedicalRecordNo())?"":preSettlementResponse.getMedicalRecordNo());
            op.setOrganId(nextStepParam.getOrganId());
            op.setPayListParams(JSONObject.toJSONString(nextStepParam.getPayListParams()));
            op.setMpiId(payListParams.get("MpiID"));
            op.setRequestMpi(requestMpi);
            op.setPayType(PayConstant.PAY_TYPE_SELF_FINANCED);
            op.setCreateTime(new Date());
            op.setPayflag(0);
            op.setRecipeIds(nextStepParam.getRecipeIds());
            logger.info("op[{}]",JSONObject.toJSONString(op));
            OutpatientDAO outpatientDao = DAOFactory.getDAO(OutpatientDAO.class);
            outpatientDao.save(op);
            op.setBusTypeName(BusTypeEnum.OUTPATIENT.getDesc());
            op.setOrderAmount(LocalStringUtil.removeRedundantZeroForFloatNumber(op.getTotalFee()==null?null:String.valueOf(op.getTotalFee())));
            return op;
        }catch (Exception e){
            logger.error("requestOutPatient error, params opList[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(opList), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @RpcService
    public ConfirmOutpatientOut confirmOutPatient(List<ConfirmOutpatientInput> opList, String totalFee, Integer organId, Map<String, String> payListParams ){
        logger.info("confirmOutPatient step in, params opList[{}], totalFee[{}], organId[{}], payListParams[{}]", JSONObject.toJSONString(opList), totalFee, organId, JSONObject.toJSONString(payListParams));
        if(ValidateUtil.blankList(opList) || ValidateUtil.nullOrZeroInteger(organId)){
            logger.info("confirmOutPatient param invalid, opList[{}], organId", JSONObject.toJSONString(opList), organId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请选择项目");
        }
        try{
            UserRoleToken urt = UserRoleToken.getCurrent();
//            Patient patient = (Patient) urt.getProperty("patient");
            String mpiId = payListParams.get("MpiID");
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiId);
            ConfirmOutpatientOut nextStepParam = new ConfirmOutpatientOut();
            // 是否支持医保
            boolean isSupportMedicalInsurance = judgeIsSupportMedicalInsurance(mpiId, organId);
            nextStepParam.setSupportMedicalInsurance(isSupportMedicalInsurance);
            nextStepParam.setTotalFee(totalFee);
            // 组装并放入预结算his参数信息
            packHisPreSettleRequestParam(mpiId, opList, nextStepParam);
            nextStepParam.setOrganId(organId);
            nextStepParam.setPayListParams(payListParams);
            return nextStepParam;
        }catch (Exception e){
            logger.error("confirmOutPatient error, params opList[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(opList), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取预结算页面数据
     * @param lastStepParam
     * @return
     */
    @RpcService
    public Outpatient getPreSettlementPageDataForOp(ConfirmOutpatientOut lastStepParam){
        logger.info("getPreSettlementPageDataForOp start in with param: lastStepParam[{}]", JSONObject.toJSONString(lastStepParam));
        if(ValidateUtil.blankString(lastStepParam.getPatientID()) || ValidateUtil.nullOrZeroInteger(lastStepParam.getOrganId()) || ValidateUtil.blankString(lastStepParam.getReceiptList())){
            logger.info("getPreSettlementPageDataForOp param invalid, lastStepParam[{}]", JSONObject.toJSONString(lastStepParam));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请求参数缺失，请检查！");
        }
        try{
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient currentPatient = urt.getProperty("patient", Patient.class);
            String requestMpi = currentPatient.getMpiId();
            OutpatientPreSettlementRequest preRequest = new OutpatientPreSettlementRequest();
            String mpiID = lastStepParam.getPayListParams().get("MpiID");
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient p = patientDAO.getByMpiId(mpiID);
            preRequest.setName(p.getPatientName());
            preRequest.setCertID(p.getRawIdcard());
            preRequest.setMobile(p.getMobile());
            preRequest.setPatientID(lastStepParam.getPatientID());
            preRequest.setReceiptList(lastStepParam.getReceiptList());
            preRequest.setChargeTypeList(lastStepParam.getChargeTypeList());
            preRequest.setOrganizeCode(lastStepParam.getOrganizeCode());
            preRequest.setMobile(p.getMobile());
            preRequest.setGuardianFlag(p.getGuardianFlag());
            preRequest.setGuardianName(p.getGuardianName());
            preRequest.setTotalFee(lastStepParam.getTotalFee());
            OutpatientPreSettlementResponse preSettlementResponse = this.fetchPreSettlementForOutPatient(preRequest, lastStepParam.getOrganId());

            Outpatient op = new Outpatient();
            if(preSettlementResponse==null){
                return op;
            }
            op.setPatientID(preRequest.getPatientID());
            op.setOrderIds(preRequest.getReceiptList());
            op.setOrderTypes(preRequest.getChargeTypeList());
            op.setStatementNo(preSettlementResponse.getStatementNo());
            op.setChargeTamt(preSettlementResponse.getChargeTamt());
            op.setDiscountsAmt(preSettlementResponse.getDiscountsAmt());
            op.setPersonAmt(preSettlementResponse.getPersonAmt());
            op.setTotalFee(preSettlementResponse.getPersonAmt());
            op.setOrganId(lastStepParam.getOrganId());
            op.setPayListParams(JSONObject.toJSONString(lastStepParam.getPayListParams()));
            op.setMpiId(mpiID);
            op.setRequestMpi(requestMpi);
            op.setPayType(40);
            op.setCreateTime(new Date());
            op.setPayflag(0);
            op.setRecipeIds(lastStepParam.getRecipeIds());
            logger.info("op[{}]",JSONObject.toJSONString(op));
            OutpatientDAO outpatientDao = DAOFactory.getDAO(OutpatientDAO.class);
            outpatientDao.save(op);
            op.setBusTypeName(BusTypeEnum.OUTPATIENT.getDesc());
            op.setOrderAmount(LocalStringUtil.removeRedundantZeroForFloatNumber(op.getTotalFee()==null?null:String.valueOf(op.getTotalFee())));
            return op;
        }catch (Exception e){
            logger.error("getPreSettlementPageDataForOp exception param: lastStepParam[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(lastStepParam), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @RpcService
    public boolean applyMedicalInsurancePay(ConfirmOutpatientOut lastStepParam){
        logger.info("applyMedicalInsurancePay start, params: lastStepParam[{}]", JSONObject.toJSONString(lastStepParam));
        try{
            String outTradeNo = BusTypeEnum.OUTPATIENT.getApplyNo();
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            HisServiceConfig hisServiceConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(lastStepParam.getOrganId());
            Map<String, Object> httpRequestParam = Maps.newHashMap();
            httpRequestParam.put("mrn", lastStepParam.getPatientID());
            httpRequestParam.put("id_card_no", patient.getIdcard());
            httpRequestParam.put("cfhs", lastStepParam.getReceiptList().split(HIS_RECIPEID_SPLIT_CHAR));
            httpRequestParam.put("hospital_code", hisServiceConfig.getYkfPlatHospitalCode());
            httpRequestParam.put("partner_trade_no", outTradeNo);
            httpRequestParam.put("callback_url", PayUtil.getNotify_domain() + PayController.DA_BAI_ASYNC_NOTIFY_URL);
            httpRequestParam.put("need_app_notify", "1");
            httpRequestParam.put("is_show_result", "1");
            Outpatient op = new Outpatient();
            DaBaiMedicalInsuranceService medicalInsuranceService = AppContextHolder.getBean("medicalInsuranceService", DaBaiMedicalInsuranceService.class);
            DaBaiMedicalInsuranceService.PayResult payResult = medicalInsuranceService.applyDaBaiPay(httpRequestParam);
            op.setPatientID(lastStepParam.getPatientID());
            op.setOrderIds(lastStepParam.getReceiptList());
            op.setOrderTypes(lastStepParam.getChargeTypeList());
            op.setStatementNo(null);
            op.setTotalFee(ValidateUtil.blankString(lastStepParam.getTotalFee())?0:Double.valueOf(lastStepParam.getTotalFee()));
            op.setOrganId(lastStepParam.getOrganId());
            op.setPayListParams(JSONObject.toJSONString(lastStepParam.getPayListParams()));
            op.setMpiId(patient.getMpiId());
            op.setRequestMpi(patient.getMpiId());
            op.setPayType(PayConstant.PAY_TYPE_MEDICAL_INSURANCE);
            op.setCreateTime(new Date());
            op.setPayflag(0);
            op.setRecipeIds(lastStepParam.getRecipeIds());
            op.setOutTradeNo(outTradeNo);
            op.setPayOrganId(hisServiceConfig.getYkfPlatHospitalCode());
            OutpatientDAO outpatientDao = DAOFactory.getDAO(OutpatientDAO.class);
            if("0".equals(payResult.getCode())) {
                String tradeNo = payResult.getData().getTrade_no();
                op.setTradeNo(tradeNo);
                outpatientDao.save(op);
                for(int i=0; i<3; i++) {
                    TimeUnit.SECONDS.sleep((long)Math.pow(2, i));
                    Outpatient result = outpatientDao.getById(op.getId());
                    if(result.getTradeStatus()!=null && result.getTradeStatus()==3){
                        return true;
                    }
                }
                return false;
            }else {
                op.setErrorInfo(JSONObject.toJSONString(payResult));
                outpatientDao.save(op);
                return false;
            }
        }catch (Exception e){
            logger.error("applyMedicalInsurancePay error, lastStepParam[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(lastStepParam), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
//            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            return false;
        }

    }

    private String packDaBaiNotifyUrl(){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String daBaiNotifyUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" + PayController.DA_BAI_ASYNC_NOTIFY_URL;
        return daBaiNotifyUrl;
    }

    /**
     * 门诊缴费提交订单——自费
     * @param payWay
     * @param id
     * @return
     */
    @RpcService
    public Map<String, Object> submitOutpatientAndPay(String payWay, Integer id){
        logger.info("submitOutpatientAndPay start in with params: payway[{}], id[{}]", payWay, id);
        if(ValidateUtil.blankString(payWay) || ValidateUtil.nullOrZeroInteger(id)){
            logger.info("submitOutpatientAndPay has invalid param, payway[{}], id[{}]", payWay, id);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空，请检查");
        }
        try {
            Outpatient op = DAOFactory.getDAO(OutpatientDAO.class).get(id);
//            if(ValidateUtil.nullOrZeroDouble(op.getTotalFee())){
//                logger.info("submitOutpatientAndPay totalFee is null or zero, id[{}]",  id);
//                throw new DAOException("金额为0不能发起支付");
//            }
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            Map<String, String> callbackParamsMap = Maps.newHashMap();
            callbackParamsMap.put("price", op.getTotalFee()==null?"0":String.valueOf(op.getTotalFee()));
            Map<String, Object> map = payService.immediatlyPayForBus(payWay, BusTypeEnum.OUTPATIENT.getCode(), id, op.getOrganId(), callbackParamsMap);

            logger.info("WxPayService.payApply 返回数据[{}]", JSONUtils.toString(map));
            return map;
        } catch (Exception e){
            logger.error("submitOutpatientAndPay error, with params:payWay[{}], id[{}], errorMessage[{}], stackTrace[{}]", payWay, id, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

@RpcService
    public void doAfterMedicalInsurancePaySuccessForOutpatient(Integer id, boolean success){
        OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
        Integer organId = outpatientDAO.getById(id).getOrganId();
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(id);
        smsInfo.setBusType(MessagePushExecutorConstant.PAYRESULT_OUTPATIENT);
        smsInfo.setSmsType(MessagePushExecutorConstant.PAYRESULT_OUTPATIENT);
        smsInfo.setOrganId(organId);
        smsInfo.setExtendValue(String.valueOf(success));
        SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        logger.info("doAfterMedicalInsurancePaySuccessForOutpatient success, id[{}], success[{}]", id, success);

    }

    private boolean judgeIsSupportMedicalInsurance(String mpiId, Integer organId) {
        HisServiceConfig hisServiceConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(organId);
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiId);
        if(ValidateUtil.blankString(patient.getPatientType()) || String.valueOf(PayConstant.PAY_TYPE_SELF_FINANCED).equals(patient.getPatientType())){
            logger.info("judgeIsSupportMedicalInsurance patient not support, mpiId[{}]", patient.getMpiId());
            return false;
        }
        if(ValidateUtil.isNotTrue(hisServiceConfig.getSupportMedicalInsurance())) {
            logger.info("judgeIsSupportMedicalInsurance hisServiceConfig not support, id[{}]", hisServiceConfig.getId());
            return false;
        }
        return true;
    }

    private void packHisPreSettleRequestParam(String mpiId, List<ConfirmOutpatientInput> opList, ConfirmOutpatientOut nextStepParam){
        StringBuffer recipeSb = new StringBuffer();
        StringBuffer chargeTypeSb = new StringBuffer();
        StringBuffer recipeIdSb = new StringBuffer();
        for(ConfirmOutpatientInput opi : opList){
            if(ValidateUtil.notBlankString(opi.getOrderID())) {
                recipeSb.append(opi.getOrderID());
                recipeSb.append(HIS_RECIPEID_APPEND_CHAR);
            }
            if(ValidateUtil.notBlankString(opi.getOrderType())) {
                chargeTypeSb.append(opi.getOrderType());
                chargeTypeSb.append(HIS_RECIPEID_APPEND_CHAR);
            }
            nextStepParam.setPatientID(opi.getPatientId());
            if(PayConstant.OUTPATIENT_OPTYPE_RECIPE==opi.getOpType()) {
                if(ValidateUtil.notBlankString(opi.getRecipeId())) {
                    recipeIdSb.append(opi.getRecipeId());
                    recipeIdSb.append(HIS_RECIPEID_APPEND_CHAR);
                }
                PaymentService paymentService = AppContextHolder.getBean("paymentService", PaymentService.class);
                paymentService.setRecipeAddress(mpiId, opi.getOrderID(), (nextStepParam.getOrganId() == null ? null : String.valueOf(nextStepParam.getOrganId())), String.valueOf(opi.getSendWay()), opi.getAddressId());
            }
        }
        String recipeStrs = recipeSb.toString();
        String chargeStrs = chargeTypeSb.toString();
        String recipeIds = recipeIdSb.toString();
        if(recipeStrs.endsWith(HIS_RECIPEID_APPEND_CHAR+"")){
            recipeStrs = recipeStrs.substring(0, recipeStrs.length()-1);
        }
        if(chargeStrs.endsWith(HIS_RECIPEID_APPEND_CHAR+"")){
            chargeStrs = chargeStrs.substring(0, chargeStrs.length()-1);
        }
        if(recipeIds.endsWith(HIS_RECIPEID_APPEND_CHAR+"")){
            recipeIds = recipeIds.substring(0, recipeIds.length()-1);
        }
        nextStepParam.setReceiptList(recipeStrs);
        nextStepParam.setChargeTypeList(chargeStrs);
        nextStepParam.setRecipeIds(recipeIds);
    }

    /**
     * 调用his门诊预结算接口获取结算信息
     * @param preSettlementRequest
     * @return
     */
    public OutpatientPreSettlementResponse fetchPreSettlementForOutPatient(OutpatientPreSettlementRequest preSettlementRequest, Integer organId){
        logger.info("fetchPreSettlementForOutPatient step in, params preSettlementRequest[{}]", JSONObject.toJSONString(preSettlementRequest));
        HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisDao.getByOrganId(organId);
        //调用服务id
        String hisServiceId = cfg.getAppDomainId() + ".hisSettlementService";
        logger.info("门诊缴费预结算参数："+JSONUtils.toString(preSettlementRequest));

        //调用 his 查询当前就诊顺序数
        String organizeCode = DAOFactory.getDAO(OrganDAO.class).getOrganizeCodeByOrganId(organId);
        preSettlementRequest.setOrganizeCode(organizeCode);
        boolean s = DBParamLoaderUtil.getOrganSwich(organId);
        OutpatientPreSettlementResponse response = new OutpatientPreSettlementResponse();
        if(s){
        	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            OutpatientPreSettlementRequestTO to = new OutpatientPreSettlementRequestTO();
            BeanUtils.copy(preSettlementRequest,to);
            OutpatientPreSettlementResponseTO r = appointService.queryPreSettlement(to);
            if(r==null){
                throw new DAOException(LocalStringUtil.format("获取门诊预结算数据失败，【失败原因】[{}]", "返回为空"));
            }
            BeanUtils.copy(r,response);

        }else{
            Object res = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryPreSettlement", preSettlementRequest);
            if(res==null){
                throw new DAOException(LocalStringUtil.format("获取门诊预结算数据失败，【失败原因】[{}]", "返回为空"));
            }
            response = (OutpatientPreSettlementResponse)res;
        }

        Integer code = response.getMsgCode();
        if(code!=null){
            if(code.intValue()==200){
                return response;
            }else if(code.intValue()==-2){
                logger.error("门诊缴费预结算失败："+response.getMsg());
                throw new DAOException("抱歉，该医院只支持单笔订单支付。");
            }else{
                logger.error("门诊缴费预结算失败："+response.getMsg());
                throw new DAOException("医院处理失败");
            }

        }
        return null;
    }

    /**
     * 支付成功，调用his结算
     * @param outpatient
     * @return
     */
    public boolean settlementOutPatientToHis(Outpatient outpatient){
        logger.info("settlementOutPatientToHis start, params[{}]", JSONObject.toJSONString(outpatient));
        try{
            OutpatientSettlementRequest settlementRequest = new OutpatientSettlementRequest();
            PatientDAO  paDao = DAOFactory.getDAO(PatientDAO.class);
            String paylistparam = outpatient.getPayListParams();
            Map param = JSONUtils.parse(paylistparam, Map.class);

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ o = organDAO.getByOrganId(outpatient.getOrganId());
            Patient pa = paDao.getByMpiId(param.get("MpiID").toString());
            settlementRequest.setRequestCertID(paDao.getByMpiId(outpatient.getRequestMpi()).getIdcard());
            settlementRequest.setName(pa.getPatientName());
            settlementRequest.setCertID(pa.getRawIdcard());
            settlementRequest.setPatientID(outpatient.getPatientID());
            settlementRequest.setStatementNo(outpatient.getStatementNo());
            settlementRequest.setReceiptList(outpatient.getOrderIds());
            settlementRequest.setChargeTypeList(outpatient.getOrderTypes());
            settlementRequest.setChargeTamt(outpatient.getChargeTamt());
            settlementRequest.setPersonAmt(outpatient.getPersonAmt());
            settlementRequest.setDiscountsAmt(outpatient.getDiscountsAmt());
            settlementRequest.setPayWay(outpatient.getPayWay());
            settlementRequest.setPayChannel(outpatient.getPayWay());
            settlementRequest.setPayTradeno(outpatient.getOutTradeNo());
            settlementRequest.setTradeno(outpatient.getTradeNo());
            settlementRequest.setOrganizeCode(o.getOrganizeCode());
            settlementRequest.setMobile(pa.getMobile());
            settlementRequest.setGuardianFlag(pa.getGuardianFlag());
            settlementRequest.setGuardianName(pa.getGuardianName());

            OutpatientSettlementResponse settlementResponse = doSettlementToHis(settlementRequest, outpatient.getOrganId());

            if(settlementResponse==null){
                if(outpatient.getOrganId()==1000423){
                    //his结算返回null，organId=1000423是高州医院
                    logger.info("his门诊结算返回null，organId=1000423是高州医院");
                    AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
                    PaymentResultRequest paymentResultRequest=new PaymentResultRequest();
                    paymentResultRequest.setOrganId(outpatient.getOrganId());
                    paymentResultRequest.setBusinessID(outpatient.getStatementNo());
                    paymentResultRequest.setBusinessType(3);
                    paymentResultRequest.setTradeNo(outpatient.getTradeNo());
                    paymentResultRequest.setOutTradeNo(outpatient.getOutTradeNo());
                    HisResponse hisResponse=appointTodayBillService.queryPaymentResult(paymentResultRequest);
                    if(null==hisResponse || !hisResponse.getMsgCode().equals("200")){
                        logger.info("organId=1000423高州医院门诊结算查询outpatient.getStatementNo()未找到" + outpatient.getStatementNo());
                        paymentResultRequest.setBusinessID(outpatient.getOutTradeNo());
                        hisResponse=appointTodayBillService.queryPaymentResult(paymentResultRequest);
                    }
                    if(null!=hisResponse && hisResponse.getMsgCode().equals("200")){
                        settlementResponse=new OutpatientSettlementResponse();
                        settlementResponse.setMsgCode(Integer.valueOf(hisResponse.getMsgCode().toString()));
                        settlementResponse.setStatementNo(outpatient.getStatementNo());
                        settlementResponse.setAccountDate(outpatient.getHisAccountDate());
                        settlementResponse.setClinicNum(outpatient.getClinicNum());
                        settlementResponse.setMedicalRecordNo(outpatient.getMedicalRecordNo());
                        settlementResponse.setInvoiceNo(outpatient.getInvoiceNo());
                        logger.info("organId=1000423高州医院预约门诊结算查询结果"+JSONUtils.toString(settlementResponse));
                    }else{
                        logger.info("his门诊结算查询结果为空");
                        return false;
                    }
                }else{
                    logger.info("his门诊结算结果为空");
                    //空 直接返回 不发起退款
                    return false;
                }
            }
            logger.info("his门诊结算结果："+JSONUtils.toString(settlementResponse));
            if (null != settlementResponse && settlementResponse.getMsgCode()!=null&&settlementResponse.getMsgCode().intValue()==200){
                outpatient.setHisAccountDate(settlementResponse.getAccountDate());
                outpatient.setInvoiceNo(settlementResponse.getInvoiceNo()==null?"":settlementResponse.getInvoiceNo());
                outpatient.setClinicNum(settlementResponse.getClinicNum()==null?"":settlementResponse.getClinicNum());
                outpatient.setMedicalRecordNo(settlementResponse.getMedicalRecordNo()==null?"":settlementResponse.getMedicalRecordNo());
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                outpatientDAO.update(outpatient);

                OutpatientResponseDAO outpatientResponseDAO = DAOFactory.getDAO(OutpatientResponseDAO.class);
                String OrderIds = outpatient.getOrderIds();
                String payFlag = "1";
                if (OrderIds.contains("|")) {
                    String[] orders = OrderIds.split(HIS_RECIPEID_SPLIT_CHAR);
                    for (int i=0;i<orders.length;i++) {
                        logger.info("更新支付状态"+outpatient.getOrganId()+"|"+param.get("MpiID")+"|"+orders[i]);
                        outpatientResponseDAO.updatePayFlagByMpiAndOrganIdAndOrderID(payFlag,param.get("MpiID").toString(),String.valueOf(outpatient.getOrganId()),orders[i]);
                    }
                }else {
                    outpatientResponseDAO.updatePayFlagByMpiAndOrganIdAndOrderID(payFlag,param.get("MpiID").toString(),String.valueOf(outpatient.getOrganId()),OrderIds);
                }
                doAfterMedicalInsurancePaySuccessForOutpatient(outpatient.getId(),true);
                return true;
            }else{
                if(settlementResponse != null){
                    logger.error("门诊缴费支付失败原因: " + settlementResponse.getMsgCode() == null ? "" : settlementResponse.getMsgCode() + "===" + settlementResponse.getMsg() != null ? settlementResponse.getMsg() : "");
                }
                doAfterMedicalInsurancePaySuccessForOutpatient(outpatient.getId(),false);
                Integer busId = outpatient.getId();
                if (busId != null){
                    WxRefundExecutor executor = new WxRefundExecutor(
                            busId, "outpatient");
                    executor.execute();
                    return false;
                }

            }
        }catch (Exception e){
            logger.info("settlementOutPatientToHis error, params[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(outpatient), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return false;
    }

    /**
     * 调用his门诊结算数据接口
     * @param settlementRequest
     * @param organId
     * @return
     */
    private OutpatientSettlementResponse doSettlementToHis(OutpatientSettlementRequest settlementRequest, Integer organId){
        logger.info("fetchSettlementForOutPatient step in, params settlementRequest[{}]", JSONObject.toJSONString(settlementRequest));
        try{
            HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisDao.getByOrganId(organId);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".hisSettlementService";
            logger.info("门诊缴费结算his参数:======="+JSONUtils.toString(settlementRequest));
            //调用 his 查询当前就诊顺序数
            boolean s = DBParamLoaderUtil.getOrganSwich(organId);
            OutpatientSettlementResponse res = new OutpatientSettlementResponse();
            if(s){
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                OutpatientSettlementRequestTO to = new OutpatientSettlementRequestTO();
                BeanUtils.copy(settlementRequest,to);
                OutpatientSettlementResponseTO r = appointService.querySettlement(to);
                if(r==null){
                    logger.info("门诊缴费结算his结果为空");
                    return null;
                }
                BeanUtils.copy(r,res);
            }else{
                Object obj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "querySettlement", settlementRequest,settlementRequest.getCertID());
                if(obj==null){
                    logger.info("门诊缴费结算his结果为空");
                    return null;
                }
                res = (OutpatientSettlementResponse) obj;
            }
            logger.info("门诊缴费结算his结果:======="+JSONUtils.toString(res));
            return  res;
        }catch (Exception e){
            logger.info("fetchSettlementForOutPatient fail, params settlementRequest[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(settlementRequest), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(LocalStringUtil.format("获取门诊结算数据失败，【失败原因】[{}]", e.getMessage()));
        }
    }

    /**
     * 查询门诊缴费列表(Flag//查询标志 0-未缴费记录;1-历史缴费记录)
     */
    @RpcService
    public Map<String,Object> getPayList(Map<String, String> map){
        List<OutpatientListResponse> listOutpatient = new ArrayList<>();
        Map<String,Object> resultMap =new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> resultLableReport2 = new ArrayList<HashMap<String, Object>>();//电子处方
        List<Map.Entry<String, List>> mapList = new ArrayList<>(); //缴费
        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String flag = map.get("Flag");                 //查询标志 0-未缴费记录;1-历史缴费记录
        String startNo = map.get("StartNo");          //起始数 从1开始
        String records = map.get("Records");          //记录数
        String patientName =null;                      //病人姓名

        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断

        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }
        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            patientName =patient.getPatientName();

            OutpatientListRequest outpatientListRequest = new OutpatientListRequest();
            outpatientListRequest.setCredentialsType("");
            outpatientListRequest.setCertID(patient.getRawIdcard());
            outpatientListRequest.setPatientID("");
            outpatientListRequest.setPatientName(patientName);
            outpatientListRequest.setStay("O");
            outpatientListRequest.setJSFlag(flag);
            outpatientListRequest.setCardType(cardType);
            outpatientListRequest.setCardOrgan(cardOrgan);
            outpatientListRequest.setCardID(cardID);
            outpatientListRequest.setGuardianName(patient.getGuardianName());
            outpatientListRequest.setGuardianFlag(patient.getGuardianFlag());
            outpatientListRequest.setOrganizeCode(organ.getOrganizeCode());
            outpatientListRequest.setOrganID(organ.getOrganId());
            outpatientListRequest.setMobile(patient.getMobile());
            //调用his接口获得待缴费记录，已排序；
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));
            if(s){
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                OutpatientListRequestTO to = new OutpatientListRequestTO();
                BeanUtils.copy(outpatientListRequest,to);
                List<OutpatientListResponseTO> listOutpatients = appointService.getPayList(to);
                if(listOutpatients!=null&&!listOutpatients.isEmpty()){
                    for(OutpatientListResponseTO t : listOutpatients){
                        OutpatientListResponse r = new OutpatientListResponse();
                        BeanUtils.copy(t,r);
                        listOutpatient.add(r);
                    }
                }
            }else{
                listOutpatient = (List<OutpatientListResponse>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getPayList",outpatientListRequest);

            }

            OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
            OrganConfig organConfig = organConfigDAO.get(Integer.parseInt(organID));
            Integer groupMode;
            if (null == organConfig.getJfGroupMode()) {     //不关联挂号序号
                groupMode = 0;
            }else {
                groupMode = organConfig.getJfGroupMode();
            }
            resultMap.put("groupMode", groupMode);
            resultMap.put("payMode", (null==organConfig.getJfPayMode()?0:organConfig.getJfPayMode()));

            logger.info("flag={}, listOutpatient={}",flag,JSONUtils.toString(listOutpatient));
            if("1".equals(flag)) {  //历史缴费 需要分页
                if(listOutpatient == null){
                    //返回空值
                    logger.info("listOutpatient is null!");
                    resultMap.put("data",mapList);
                    resultMap.put("flag",false);
                }else{
                    mapList = groupMap(listOutpatient, groupMode);
                    List listResult = new ArrayList();
                    logger.info("历史缴费记录："+JSONUtils.toString(mapList));
                    if(mapList.size() > 10){//大于10条数据需要分页
                        int nStartNo = new Integer(startNo);
                        int nRecords = new Integer(records);
                        int count = (nStartNo +nRecords) -1;
                        if (count > mapList.size()){//防止前台传来的值超过总记录数
                            count = mapList.size();
                        }
                        for (int i =nStartNo -1; i<count; i++){
                            Map.Entry<String, List> m = mapList.get(i);
                            listResult.add(m);
                        }
                        resultMap.put("data",listResult);
                        resultMap.put("flag",true);
                    }else{
                        resultMap.put("data",mapList);
                        resultMap.put("flag",true);
                    }
                }
            }else{      //未缴费记录，不需要分页
                if(listOutpatient ==null){
                    //返回空值
                    resultMap.put("JFData", mapList);
                    resultMap.put("type2", resultLableReport2);
                }else {
                    logger.info("listOutpatient size is:"+listOutpatient.size());
                    //重新组装数据返回给前台
                    RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                    Recipe recipe;
                    mapList = groupMap(listOutpatient, groupMode);
                    for (OutpatientListResponse outpatientListResponse : listOutpatient) {
                        recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(outpatientListResponse.getOrderID(),Integer.parseInt(organID));
                        //判断是否是电子处方
                        if(recipe == null){

                        }else{
                            resultLableReport2.add(pacRes(outpatientListResponse,recipe));
                        }
                    }
                    resultMap.put("JFData", mapList);
                    resultMap.put("type2", resultLableReport2);

                    //如果是挂号序号方式展现，则对数据进行挂号序号排序

                    if(Integer.valueOf(1).equals(organConfig.getJfGroupMode())
                            || Integer.valueOf(2).equals(organConfig.getJfGroupMode())) {
                        Collections.sort(resultLableReport2, new Comparator<HashMap<String, Object>>() {
                            @Override
                            public int compare(HashMap<String, Object> o1, HashMap<String, Object> o2) {
                                String Series1 = MapValueUtil.getString(o1, "Series");
                                String Series2 = MapValueUtil.getString(o2, "Series");
                                return Series1.compareTo(Series2);
                            }
                        });
                    }

                }
                //再次发送历史缴费查询的请求，如果有数据返回true，没有返回false

                outpatientListRequest.setJSFlag("1");
                //List<OutpatientListResponse> listOutpatient1 = (List<OutpatientListResponse>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getPayList",outpatientListRequest);
                List<OutpatientListResponse> listOutpatient1 = new ArrayList<>();
                if(DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID))){
                	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                    OutpatientListRequestTO to = new OutpatientListRequestTO();
                    BeanUtils.copy(outpatientListRequest,to);
                    List<OutpatientListResponseTO> listOutpatients = appointService.getPayList(to);
                    if(listOutpatients!=null&&!listOutpatients.isEmpty()){
                        for(OutpatientListResponseTO t : listOutpatients){
                            OutpatientListResponse r = new OutpatientListResponse();
                            BeanUtils.copy(t,r);
                            listOutpatient1.add(r);
                        }
                    }
                }else{
                	listOutpatient1 = (List<OutpatientListResponse>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getPayList",outpatientListRequest);
                }
                logger.info("再次查询缴费信息 listOutpatient={}",flag,JSONUtils.toString(listOutpatient));

                if (listOutpatient1!=null &&listOutpatient1.size() >0){
                    resultMap.put("flag",true);
                }else{
                    resultMap.put("flag",false);
                }

                //获取患者上次使用的收货地址
                //当前登录用户
                Map<String,Object> recMsg = new HashMap<String,Object>();
                getAddr(recMsg);
                resultMap.put("recMsg",recMsg);
            }
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("JFData", mapList);
            resultMap.put("type2", resultLableReport2);
            resultMap.put("flag",false);
        }
        return resultMap;
    }


    @RpcService
    public List groupMap(List<OutpatientListResponse> listOutpatient, Integer groupMode){
        //根据挂号序号进行缴费集合分组
        Map<String, List> mapList = new LinkedHashMap<>();
        Collections.sort(listOutpatient, new Comparator<OutpatientListResponse>() {
            @Override
            public int compare(OutpatientListResponse o1, OutpatientListResponse o2) {
                return o1.getOrderDate().compareTo(o2.getOrderDate());
            }
        });
        Collections.reverse(listOutpatient);
        for (int i=0;i<listOutpatient.size();i++) {
            OutpatientListResponse outpatientListResponse = listOutpatient.get(i);
            String  series = outpatientListResponse.getSeries();
            if(mapList.containsKey(series)) {
                mapList.get(series).add(outpatientListResponse);
            }else {
                List<OutpatientListResponse> list = new ArrayList();
                list.add(outpatientListResponse);
                mapList.put(series, list);
            }
        }
        List lists = new ArrayList();
        if(groupMode!=0){
            Iterator<Map.Entry<String, List>> it = mapList.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<String, List> entry = it.next();
                lists.add(entry);
            }
        }else{
            Map<String, List> map = new LinkedHashMap<>();
            map.put("0",listOutpatient);
            lists.add(map);
        }
        return lists;
    }
    /**
     * 查询门诊待缴费明细
     */
    @RpcService
    public Map<String,Object> getPayDetail(Map<String, String> map){
        logger.info("getPayDetail service begin..");
        Map<String,Object> resultMap =new HashMap<String,Object>();
        ArrayList<HashMap<String, String>> resultPayDetail = new ArrayList<HashMap<String, String>>();
        OutpatientDetailRequest outpatientDetailRequest = new OutpatientDetailRequest();
        List<OutpatientDetailResponse> outpatientDetailResponseList = new ArrayList<OutpatientDetailResponse>();
        ArrayList<HashMap<String, String>> resultPayDetails = new ArrayList<HashMap<String, String>>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String orderID = map.get("OrderID");            //唯一索引值，即处方单号号码
        String orderType = map.get("OrderType");        //医嘱类型
        String hosCode = map.get("HosCode");            //院区
        String recipeFlag = map.get("RecipeFlag");      //电子处方标识，1是电子处方

        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID) || StringUtil.isNullOrEmpty(orderID)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID or orderID is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try{
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }

            outpatientDetailRequest.setCredentialsType("");
            outpatientDetailRequest.setCertID(patient.getIdcard());
            outpatientDetailRequest.setPatientID("");
            outpatientDetailRequest.setPatientName(patient.getPatientName());
            outpatientDetailRequest.setStay("O");
            outpatientDetailRequest.setCardType(cardType);
            outpatientDetailRequest.setCardOrgan(cardOrgan);
            outpatientDetailRequest.setCardID(cardID);
            outpatientDetailRequest.setOrderID(orderID);
            outpatientDetailRequest.setHosCode(hosCode);
            outpatientDetailRequest.setOrderType(orderType);
            outpatientDetailRequest.setOrganID(organ.getOrganId());
            outpatientDetailRequest.setOrganizeCode(organ.getOrganizeCode());
            outpatientDetailRequest.setMobile(patient.getMobile());
            outpatientDetailRequest.setGuardianFlag(patient.getGuardianFlag());
            outpatientDetailRequest.setGuardianName(patient.getGuardianName());
            logger.info("查询门诊待缴费明细调用his请求参数："+JSONUtils.toString(outpatientDetailRequest));
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));
            if(s) {
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                OutpatientDetailRequestTO to = new OutpatientDetailRequestTO();
                BeanUtils.copy(outpatientDetailRequest,to);
                List<OutpatientDetailResponseTO> res = appointService.getPayDetail(to);
                if(res!=null&&!res.isEmpty()){
                    for(OutpatientDetailResponseTO t : res){
                        OutpatientDetailResponse r = new OutpatientDetailResponse();
                        BeanUtils.copy(t,r);
                        outpatientDetailResponseList.add(r);
                    }
                }
            }else
                outpatientDetailResponseList = (List<OutpatientDetailResponse>)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getPayDetail",outpatientDetailRequest);
            logger.info("查询门诊待缴费明细调用his返回结果："+ JSONUtils.toString(outpatientDetailResponseList));
            for(OutpatientDetailResponse outpatientDetailResponse : outpatientDetailResponseList){
                HashMap<String, String> m =new HashMap<String, String>();
                m.put("Num",outpatientDetailResponse.getNum());
                m.put("OrderID",outpatientDetailResponse.getOrderID());
                m.put("OrderName",outpatientDetailResponse.getOrderName());
                m.put("TotalFee",priceCast(outpatientDetailResponse.getTotalFee()));
                m.put("OrderType",outpatientDetailResponse.getOrderType());
                m.put("OrderTypeName",outpatientDetailResponse.getOrderTypeName());
                m.put("HosCode",outpatientDetailResponse.getHosCode());
                m.put("OrderDate",outpatientDetailResponse.getOrderDate());
                m.put("Stay",outpatientDetailResponse.getStay());
                m.put("PatientId",outpatientDetailResponse.getPatientId());
                m.put("Series",outpatientDetailResponse.getSeries());
                resultPayDetails.add(m);
            }
            resultMap.put("data",resultPayDetails);
            //如果是电子处方，则获取并返回配送方式（配送地址）
            if("1".equals(recipeFlag)){
                RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(orderID,Integer.parseInt(organID));
                resultMap.put("recipe",recipe);
                //获取患者上次使用的收货地址
                //当前登录用户
                Map<String,Object> recMsg = new HashMap<String,Object>();
                getAddr(recMsg);
                resultMap.put("recMsg",recMsg);
            }

        }catch(Exception e){
            logger.error("his invoke error:" + e.getMessage());
            resultMap.put("head","");
        }
        return resultMap;
    }

    /**
     * 查询住院预交服务接口
     * @return
     * @throws ParseException
     */
    @RpcService
    public Map<String, Object> getPrePayForLiveHospital(Map<String, String> map){

        InHospitalPrepaidResponse inHospatialPrepaidResponse = new InHospitalPrepaidResponse();
        Map<String,Object> resultMap =new HashMap<String, Object>();
        Map<String,String> dataMap = new HashMap<String, String>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String patientName =null;                      //病人姓名

        HisServiceConfig config = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(Integer.parseInt(organID));
        Integer paymentInHosp = config.getPaymentInHosp();
        if(paymentInHosp==null||paymentInHosp==0){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医院暂未开通住院缴费功能！");
        }
        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            PatientTypeDAO patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
            PatientType patientType = patientTypeDAO.get(patient.getPatientType());
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }

            int organId = Integer.parseInt(organID);
            OrganConfigService organConfigService = AppContextHolder.getBean("eh.organConfigService", OrganConfigService.class);
            Map organConfig = organConfigService.getOrganConfig(organId);

            boolean displayDetailAndSuggestMoney = true;
            //打开隐藏住院预交详情及建议金额开关
            if (organConfig != null && organConfig.get("hiddenPrePayForLiveHospital") != null
                    && ((boolean) organConfig.get("hiddenPrePayForLiveHospital"))) {
                displayDetailAndSuggestMoney = false;
            }

            resultMap.put("displayDetailAndSuggestMoney", displayDetailAndSuggestMoney);


            patientName =patient.getPatientName();

            InHospitalPrepaidRequest inHospitalPrepaidRequest = new InHospitalPrepaidRequest();
            inHospitalPrepaidRequest.setCredentialsType("");
            inHospitalPrepaidRequest.setCertID(patient.getIdcard());
            inHospitalPrepaidRequest.setPatientID("");
            inHospitalPrepaidRequest.setPatientName(patientName);
            inHospitalPrepaidRequest.setCardType(cardType);
            inHospitalPrepaidRequest.setCardOrgan(cardOrgan);
            inHospitalPrepaidRequest.setCardID(cardID);

            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));
            Object object =null;
            if(s){
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                InHospitalPrepaidRequestTO to = new InHospitalPrepaidRequestTO();
                BeanUtils.copy(inHospitalPrepaidRequest,to);
                InHospitalPrepaidResponseHisTO res = appointService.getInHospitalPrepaidInfo(to);
                BeanUtils.copy(res,inHospatialPrepaidResponse);
            }else {
                object = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getInHospitalPrepaidInfo", inHospitalPrepaidRequest);
                if(object!=null){
                    inHospatialPrepaidResponse = (InHospitalPrepaidResponse) object;
                }
            }
            logger.info("inHospitalPrepaidRequest[{}], inHospatialPrepaidResponse[{}]", JSONObject.toJSONString(inHospitalPrepaidRequest), JSONObject.toJSONString(object));
            // 需要一个标志位判断结果
            if(inHospatialPrepaidResponse != null){
                inHospatialPrepaidResponse.setOrganId(Integer.parseInt(organID));
                dataMap.put("mrn", inHospatialPrepaidResponse.getMrn());
                dataMap.put("series", inHospatialPrepaidResponse.getSeries());
                dataMap.put("interid", inHospatialPrepaidResponse.getInterid());
                dataMap.put("pname", inHospatialPrepaidResponse.getPname());
                dataMap.put("deptname", inHospatialPrepaidResponse.getDeptname());
                dataMap.put("bedid", inHospatialPrepaidResponse.getBedid());
                dataMap.put("hospital", inHospatialPrepaidResponse.getHospital());
                dataMap.put("floor", inHospatialPrepaidResponse.getFloor());
                dataMap.put("position", inHospatialPrepaidResponse.getPosition());
                dataMap.put("indate", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(inHospatialPrepaidResponse.getIndate())));
                dataMap.put("service", inHospatialPrepaidResponse.getService());
                dataMap.put("prepayment", priceCast(inHospatialPrepaidResponse.getPrepayment()));
                dataMap.put("totalFee", priceCast(inHospatialPrepaidResponse.getTotalFee()));
                dataMap.put("balance", priceCast(inHospatialPrepaidResponse.getBalance()));
                dataMap.put("hosName",organ.getShortName());
                dataMap.put("serviceName",patientType.getText());
                if("2".equals(patient.getPatientSex())){
                    dataMap.put("sex", "女");
                }else{
                    dataMap.put("sex", "男");
                }
                dataMap.put("age", (patient.getBirthday() == null ? 0 : DateConversion.getAge(patient.getBirthday()))+"");
                resultMap.put("code",200);
                resultMap.put("data",dataMap);
                /*查询his基本信息和缴费，保存到InHospitalPrepaidResponse对象*/
                InHospitalPrepaidDAO dao = DAOFactory.getDAO(InHospitalPrepaidDAO.class);
                dao.saveInHospitalPrepaid(inHospatialPrepaidResponse);
                logger.info("inHospatialPrepaidResponse is :"+ JSONUtils.toString(inHospatialPrepaidResponse));
            }else{
                logger.info("住院预交调用his接口返回null");
                return null;
            }
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }

    /**
     * 查询每日清单
     *
     * @return
     */
    @RpcService
    public Map<String, Object> getEveryDayFeeTicket(Map<String, String> map) {

        Map<String, Object> resultMap = new HashMap<String, Object>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID = map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String interid = map.get("interid"); //用单号
        String feeDate = map.get("feeDate");             //费用日期
        String mrn = map.get("mrn");             //病历号
        String patientName = null;                      //病人姓名

        resultMap.put("interid", interid);
        resultMap.put("MpiID", MpiID);
        resultMap.put("OrganID", organID);
        resultMap.put("CardType", cardType);
        resultMap.put("CardOrgan", cardOrgan);
        resultMap.put("CardID", cardID);
        resultMap.put("feeDate", feeDate);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID) || StringUtil.isNullOrEmpty(interid) || StringUtil.isNullOrEmpty(feeDate)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID or feedate or interid is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType != null && (cardOrgan == null || cardID == null)) ||
                (cardOrgan != null && (cardType == null || cardID == null)) ||
                (cardID != null && (cardType == null || cardOrgan == null))) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            patientName = patient.getPatientName();

            AlreadyGenerateCostRequest alreadyGenerateCostRequest = new AlreadyGenerateCostRequest();
            alreadyGenerateCostRequest.setCredentialsType("");
            alreadyGenerateCostRequest.setCertID(patient.getIdcard());
            alreadyGenerateCostRequest.setPatientID(mrn);
            alreadyGenerateCostRequest.setPatientName(patientName);
            alreadyGenerateCostRequest.setCardType(cardType);
            alreadyGenerateCostRequest.setCardOrgan(cardOrgan);
            alreadyGenerateCostRequest.setCardID(cardID);
            alreadyGenerateCostRequest.setInterid(interid);
            alreadyGenerateCostRequest.setItemType("");


            String date = DateConversion.getDateFormatter(DateConversion.getCurrentDate(feeDate, DateConversion.YYYY_MM_DD), DateConversion.YYYY_MM_DD);

            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));


            List<Map<String, Object>> returnlist;
            Map<String, Object> returnlist2 = new HashMap<>();
            alreadyGenerateCostRequest.setFdate(date);
            if (s) {
                IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                AlreadyGenerateCostRequestTO to = new AlreadyGenerateCostRequestTO();
                BeanUtils.copy(alreadyGenerateCostRequest, to);
                returnlist2 = appointService.getAlreadyGenerateDayListNew(to);
            } else {
                returnlist2 = (Map<String, Object>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getAlreadyGenerateDayListNew", alreadyGenerateCostRequest);
            }
            logger.info("住院预交——已产生费用日清单集合dataMap is :" + JSONUtils.toString(returnlist2));
            resultMap.put("data", returnlist2);
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }



    /**
     * 住院预交——已产生费用按类型查看
     * @return
     * @throws ParseException
     */
    @RpcService
    public Map<String, Object> getAlreadyGenerateTypeList(Map<String, String> map){

        Map<String,Object> resultMap =new HashMap<String, Object>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String interid = map.get("interid");
        String mrn = map.get("mrn");
        String patientName =null;                      //病人姓名

        resultMap.put("interid",interid);
        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID) || StringUtil.isNullOrEmpty(interid)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID or interid is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            patientName =patient.getPatientName();

            AlreadyGenerateCostRequest alreadyGenerateCostRequest = new AlreadyGenerateCostRequest();
            alreadyGenerateCostRequest.setCredentialsType("");
            alreadyGenerateCostRequest.setCertID(patient.getIdcard());
            alreadyGenerateCostRequest.setPatientID(mrn);
            alreadyGenerateCostRequest.setPatientName(patientName);
            alreadyGenerateCostRequest.setCardType(cardType);
            alreadyGenerateCostRequest.setCardOrgan(cardOrgan);
            alreadyGenerateCostRequest.setCardID(cardID);
            alreadyGenerateCostRequest.setInterid(interid);
            alreadyGenerateCostRequest.setFdate("");
            alreadyGenerateCostRequest.setItemType("");

            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));
            List<AlreadyGenerateCostResponseType> list = new ArrayList<>();
            if(s){
                IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                AlreadyGenerateCostRequestTO to = new AlreadyGenerateCostRequestTO();
                BeanUtils.copy(alreadyGenerateCostRequest,to);
                List<AlreadyGenerateCostResponseTypeTO> res = appointService.getAlreadyGenerateTypeList(to);
                for(AlreadyGenerateCostResponseTypeTO t: res){
                    AlreadyGenerateCostResponseType r = new AlreadyGenerateCostResponseType();
                    BeanUtils.copy(t,r);
                    list.add(r);
                }
            }else {
                list = (List<AlreadyGenerateCostResponseType>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getAlreadyGenerateTypeList", alreadyGenerateCostRequest);
            }
            logger.info("住院预交——已产生费用按类型查看list is :"+JSONUtils.toString(list));
            // 需要一个标志位判断结果
            if(list != null){
                List<Map<String,String>> datalist = new ArrayList<Map<String,String>>();
                double typeTotalFee =  0.0;
                for(AlreadyGenerateCostResponseType alreadyGenerateCostResponseType : list){
                    Map<String,String> dataMap = new HashMap<String, String>();
                    String price = priceCast(alreadyGenerateCostResponseType.getItemfee());
                    dataMap.put("itemfee", price);
                    dataMap.put("itemtypename", alreadyGenerateCostResponseType.getItemtypename());
                    dataMap.put("itemtypecode", alreadyGenerateCostResponseType.getItemtypecode());
                    datalist.add(dataMap);
                    typeTotalFee += Double.parseDouble(price);
                }

                resultMap.put("code",200);
                resultMap.put("data",datalist);
                resultMap.put("typeTotalFee",priceCast(typeTotalFee+""));

            }else{
                throw new Exception("住院预交——已产生费用按类型查看调用his接口返回null");
            }
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }

    /**
     * 住院预交——已产生费用明细
     * @return
     * @throws ParseException
     */
    @RpcService
    public Map<String, Object> getAlreadyGenerateTypeDetail(Map<String, String> map){

        Map<String,Object> resultMap =new HashMap<String, Object>();
        Map<String,String> dataMap = new HashMap<String, String>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String interid = map.get("interid");
        String itemtypecode = map.get("itemtypecode");
        String patientName =null;                      //病人姓名

        resultMap.put("interid",interid);
        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID) || StringUtil.isNullOrEmpty(interid) || StringUtil.isNullOrEmpty(itemtypecode)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID or itemtypecode or interid is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            patientName =patient.getPatientName();

            AlreadyGenerateCostRequest alreadyGenerateCostRequest = new AlreadyGenerateCostRequest();
            alreadyGenerateCostRequest.setCredentialsType("");
            alreadyGenerateCostRequest.setCertID(patient.getIdcard());
            alreadyGenerateCostRequest.setPatientID("");
            alreadyGenerateCostRequest.setPatientName(patientName);
            alreadyGenerateCostRequest.setCardType(cardType);
            alreadyGenerateCostRequest.setCardOrgan(cardOrgan);
            alreadyGenerateCostRequest.setCardID(cardID);
            alreadyGenerateCostRequest.setInterid(interid);
            alreadyGenerateCostRequest.setFdate("");
            alreadyGenerateCostRequest.setItemType(itemtypecode);

            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));
            Map<String, Object> returnmap;
            if(s){
                IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                AlreadyGenerateCostRequestTO to = new AlreadyGenerateCostRequestTO();
                BeanUtils.copy(alreadyGenerateCostRequest,to);
                returnmap = appointService.getAlreadyGenerateTypeDetail(to);
            }else
                returnmap = (Map<String, Object>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getAlreadyGenerateTypeDetail",alreadyGenerateCostRequest);
            Map<String,Object> resmap = new HashMap<String,Object>();
            List<Object> reslist = new ArrayList<Object>();
            logger.info("住院预交——已产生费用详情returnmap is :"+JSONUtils.toString(returnmap));
            // 需要一个标志位判断结果
            if(returnmap != null){

                List<Object> list1 = (List<Object>)returnmap.get("data");
                if(list1!=null){
                    Map<String, Object> map2 = null;
                    List<Map<String,String>> llist2 = new ArrayList<Map<String,String>>();

                    List<AlreadyGenerateCostResponseDayList> llist = new ArrayList<AlreadyGenerateCostResponseDayList>();
                    for(Object o : list1){
                        map2 = (Map<String, Object>)o;
                        String data1 = (String)map2.get("feeDate");
                        String data2 = DateConversion.getWeekOfDate(new SimpleDateFormat("yyyy-MM-dd").parse(data1));

                        llist = (List<AlreadyGenerateCostResponseDayList>)map2.get("feeDetailList");
                        if(llist!=null){
                            for(AlreadyGenerateCostResponseDayList alreadyGenerateCostResponseDayList:llist){
                                String itemName = alreadyGenerateCostResponseDayList.getItemName();
                                String numb = alreadyGenerateCostResponseDayList.getNumb();
                                String fee = alreadyGenerateCostResponseDayList.getFee();
                                Map<String,String> mmap2 = new HashMap<String,String>();
                                mmap2.put("itemName",itemName);
                                mmap2.put("numb",numb);
                                mmap2.put("fee",fee);
                                llist2.add(mmap2);
                            }
                            resmap.put("dateId",data1+" "+data2);
                            resmap.put("data",llist2);
                            reslist.add(resmap);
                        }

                    }
                }
                resultMap.put("code",200);
                resultMap.put("data",reslist);
            }else{
                throw new Exception("住院预交——已产生费用按类型查看调用his接口返回null");
            }
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }

    /**
     * 住院预交——已产生费用日清单
     * @return
     * @throws ParseException
     */
    @RpcService
    public Map<String, Object> getAlreadyGenerateDayList(Map<String, String> map){

        Map<String,Object> resultMap =new HashMap<String, Object>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String interid = map.get("interid");
        String indate = map.get("indate");             //开始住院日期
        String patientName =null;                      //病人姓名

        resultMap.put("interid",interid);
        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID) || StringUtil.isNullOrEmpty(interid) || StringUtil.isNullOrEmpty(indate)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID or indate or interid is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(new Integer(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);
            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            patientName =patient.getPatientName();

            AlreadyGenerateCostRequest alreadyGenerateCostRequest = new AlreadyGenerateCostRequest();
            alreadyGenerateCostRequest.setCredentialsType("");
            alreadyGenerateCostRequest.setCertID(patient.getIdcard());
            alreadyGenerateCostRequest.setPatientID("");
            alreadyGenerateCostRequest.setPatientName(patientName);
            alreadyGenerateCostRequest.setCardType(cardType);
            alreadyGenerateCostRequest.setCardOrgan(cardOrgan);
            alreadyGenerateCostRequest.setCardID(cardID);
            alreadyGenerateCostRequest.setInterid(interid);
            alreadyGenerateCostRequest.setItemType("");
            //因为不确定一共多少天，考虑到住院实际情况，基本不会出现很多天没交钱住院的情况，所以这里循环遍历从开始住院到当前日期
            List<String> dateList = dateList(indate);
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organID));
            String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
            List<Object> reslist = new ArrayList<Object>();
            boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organID));

            for(String date : dateList){
                List<Map<String,Object>> returnlist;
                Map<String,Object> dataMap = new HashMap<String, Object>();
                alreadyGenerateCostRequest.setFdate(date);
                if(s){
                    IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                    AlreadyGenerateCostRequestTO to = new AlreadyGenerateCostRequestTO();
                    BeanUtils.copy(alreadyGenerateCostRequest,to);
                    returnlist = appointService.getAlreadyGenerateDayList(to);
                }else{
                    returnlist = (List<Map<String,Object>>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"getAlreadyGenerateDayList",alreadyGenerateCostRequest);
                }
                //logger.info("住院预交——已产生费用日清单返回参数"+JSONUtils.toString(returnlist));
                if(returnlist!=null){
                    String data_temp = DateConversion.getWeekOfDate(new SimpleDateFormat("yyyy-MM-dd").parse(date));
                    dataMap.put("dateId",date+" "+data_temp);
                    dataMap.put("data",returnlist);
                    reslist.add(dataMap);
                }
            }
            resultMap.put("code",200);
            resultMap.put("data",reslist);

            logger.info("住院预交——已产生费用日清单集合dataMap is :"+JSONUtils.toString(reslist));
        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }

    /**
     * 住院预交——预交记录查询
     * @return
     * @throws ParseException
     * 由于his未提供相关接口，因此这里先从支付业务表中查询记录
     */
    @RpcService
    public Map<String, Object> getAlreadyPrepayList(Map<String, String> map){

        Map<String,Object> resultMap =new HashMap<String, Object>();

        List<Map<String,Object>> dataList = new ArrayList<Map<String,Object>>();

        String MpiID = map.get("MpiID");                //患者编号
        String organID =map.get("OrganID");             //医院编号
        String cardType = map.get("CardType");         //卡类型（1医院就诊卡  2医保卡3 医院病历号）
        String cardOrgan = map.get("CardOrgan");       //发卡机构 就诊卡：就诊医院组织代码; 医保卡：各地各类医保、农保机构的组织代码
        String cardID = map.get("CardID");             //卡号（门诊号码、就诊卡号、医保号等）
        String patientName =null;                      //病人姓名
        ;
        resultMap.put("MpiID",MpiID);
        resultMap.put("OrganID",organID);
        resultMap.put("CardType",cardType);
        resultMap.put("CardOrgan",cardOrgan);
        resultMap.put("CardID",cardID);
        //非空判断
        if (StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(MpiID)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID or MpiID is required!");
        }
        //卡类型,发卡机构,卡号三者是联动关系,一个有值其他必须有值
        if ((cardType !=null &&(cardOrgan ==null || cardID ==null)) ||
                (cardOrgan !=null &&(cardType ==null || cardID ==null)) ||
                (cardID !=null &&(cardType ==null || cardOrgan ==null)) ){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CardType or CardOrgan or CardID is required!");
        }

        try {
            Calendar ca = Calendar.getInstance();
            ca.setTime(new Date()); //得到当前日期

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Organ organ = organDAO.getByOrganId(Integer.parseInt(organID));
            Patient patient = patientDAO.getPatientByMpiId(MpiID);

            if (organ == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "OrganID 对应找不到机构数据!");
            }
            if (patient == null) {
                throw new DAOException(DAOException.ENTITIY_NOT_FOUND,
                        "MpiID 对应找不到患者数据!");
            }
            PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
            List<PayBusiness> payBusinessesList = payBusinessDAO.findListByMpiIdAndOrganID(MpiID,Integer.parseInt(organID),1,"prepay");
            logger.info("住院预交——预交记录payBusinessesList is :"+JSONUtils.toString(payBusinessesList));
            if(payBusinessesList!=null){
                int i = 0;
                for(PayBusiness payBusiness:payBusinessesList){
                    i++;
                    Map<String,Object> dataMap = new HashMap<String, Object>();
                    dataMap.put("prepayFee",payBusiness.getTotalFee()+"");
                    dataMap.put("prepaydate",new SimpleDateFormat("yyyy-MM-dd").format(payBusiness.getCreateTime()));
                    PayWayEnum.fromCode(payBusiness.getPayWay());
                    dataMap.put("prepaywayName","纳里健康");
                    dataMap.put("prepayway", 1);
                    //这里只是为了演示测试效果，等his提供服务之后联系需求，定义数据字典
                    /**
                     * 0 支付宝
                     * 1 纳里健康
                     * 2 医院窗口
                     * 99 其他充值
                     */

                    dataList.add(dataMap);
                }
            }

            resultMap.put("code",200);
            resultMap.put("data",dataList);


        } catch (Exception e) {
            logger.error("his invoke error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            //返回空值
            resultMap.put("code", 501);
        }
        return resultMap;
    }

    private HashMap<String,Object> pacRes(OutpatientListResponse outpatientListResponse, Recipe recipe) throws ParseException {
        SimpleDateFormat myFmt1=new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat myFmt2=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        HashMap<String, Object> m =new HashMap<String, Object>();
        Date orderDate =myFmt2.parse(outpatientListResponse.getOrderDate());
        String OrderDate =myFmt1.format(orderDate);
        if(!isChinese(outpatientListResponse.getOrderTypeName())){
            outpatientListResponse.setOrderTypeName(outpatientListResponse.getOrderName());
        }
        m.put("OrderDate", OrderDate);
        m.put("OrderTypeName", outpatientListResponse.getOrderTypeName());
        m.put("TotalFee", priceCast(outpatientListResponse.getTotalFee()));
        m.put("OrderID", outpatientListResponse.getOrderID());
        m.put("OrderName", outpatientListResponse.getOrderName());
        m.put("OrderType", outpatientListResponse.getOrderType());
        m.put("HosCode", outpatientListResponse.getHosCode());
        m.put("Stay", outpatientListResponse.getStay());
        m.put("PatientId", outpatientListResponse.getPatientId());
        m.put("Series", outpatientListResponse.getSeries());
        m.put("RecipeSource", outpatientListResponse.getRecipeSource());

        if(recipe != null){
            m.put("recipe",recipe);
        }
        return m;
    }

    private HashMap<String,Object> pacResponse(OutpatientListResponse outpatientListResponse, Recipe recipe) throws ParseException {
        HashMap<String, Object> m =new HashMap<String, Object>();
        if(!isChinese(outpatientListResponse.getOrderTypeName())){
            outpatientListResponse.setOrderTypeName(outpatientListResponse.getOrderName());
        }
        m.put("OrderDate", outpatientListResponse.getOrderDate());
        m.put("OrderTypeName", outpatientListResponse.getOrderTypeName());
        m.put("TotalFee", priceCast(outpatientListResponse.getTotalFee()));
        m.put("OrderID", outpatientListResponse.getOrderID());
        m.put("OrderName", outpatientListResponse.getOrderName());
        m.put("OrderType", outpatientListResponse.getOrderType());
        m.put("HosCode", outpatientListResponse.getHosCode());
        m.put("Stay", outpatientListResponse.getStay());
        m.put("PatientId", outpatientListResponse.getPatientId());
        m.put("Series", outpatientListResponse.getSeries());
        m.put("RecipeSource", outpatientListResponse.getRecipeSource());

        if(recipe != null){
            m.put("recipe",recipe);
        }
        return m;
    }

    //金额转换，4位小数转换成2位
    private String priceCast(String price){
        if (price.contains(".")) {
            if(price.length()-price.indexOf(".")==2){
                return price.substring(0,price.indexOf(".")+2);
            }
            return price.substring(0,price.indexOf(".")+3);
        } else {
            return price;
        }
    }

    private List<String> dateList(String begindate) throws ParseException{
        List<String> list = new ArrayList<String>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");

        Date startTime = sdf1.parse(begindate);
        Date endTime = new Date();
        String startDay = sdf2.format(startTime);
        String endDay = sdf2.format(endTime);
        String middleDay = null;
        list.add(startDay);

        long start = startTime.getTime();
        long end = endTime.getTime();
        long interval = 24 * 60 * 60 * 1000;
        long temp = start;
        while (temp+interval <= end) {
            Date d = new Date();
            temp = temp + interval;
            d = new Date(temp);
            middleDay = sdf2.format(d);
            if(!middleDay.equals(startDay) && !middleDay.equals(endDay)){
                list.add(sdf2.format(d));
            }
        }
        if(!startDay.equals(endDay)){
            list.add(endDay);
        }

        return list;
    }

    //按报告日期 由近及远返回 排序
    private void sort(List<OutpatientListResponse> listOutpatient){

        Collections.sort(listOutpatient, new Comparator<OutpatientListResponse>() {
            @Override
            public int compare(OutpatientListResponse o1, OutpatientListResponse o2) {
                return o2.getOrderDate().compareTo(o1.getOrderDate());
            }
        });
    }
}
