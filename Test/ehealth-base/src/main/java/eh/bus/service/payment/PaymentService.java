package eh.bus.service.payment;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganDAO;
import eh.bus.dao.InHospitalPrepaidDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.his.service.AppointTodayBillService;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.service.RecipeService;
import eh.entity.base.Organ;
import eh.entity.bus.PayBusiness;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.Recipe;
import eh.entity.his.InpPreRequest;
import eh.entity.his.InpPreResponse;
import eh.entity.his.fee.InHospitalPrepaidResponse;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.task.executor.WxRefundExecutor;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariPayService;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缴费模块相关支付服务
 * Created by haoak on 2016/8/25.
 */
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    /**
     * 门诊缴费和住院预交微信支付，这里因为参数一样，只有金额和支付方式，支付流程类似，所以建议共用一个接口
     * @param totalFee      //支付总金额
     * @param payWay        //支付方式    40微信网页支付 41微信app支付
     * @param organID       //机构id      选择缴费的机构id
     * @return
     */
    @RpcService
    public Map<String,Object> wxPayment(String totalFee, String payWay,Integer organID, String orderId, Map<String, String> payListParams){
        logger.info("wxPayment totalFee[{}], payWay[{}], organID[{}], orderId[{}], payListParams[{}]", totalFee, payWay, organID, orderId, payListParams);
        Map<String, Object> resultMap = new HashMap<>();
        String code = SystemConstant.SUCCESS;
        String msg = "";
        // 1.校验参数
        if(ValidateUtil.blankString(totalFee)){
            logger.error("PaymentService.wxPayment*****totalFee["+totalFee+"]为零，该笔费用不存在*****");
            code = SystemConstant.FAIL;
            msg = "该笔费用不存在！";
        }
        if(StringUtil.isNullOrEmpty(payWay) || organID == null || ValidateUtil.blankString(orderId)){
            logger.error("PaymentService.wxPayment*****参数不能为空*****");
            code = SystemConstant.FAIL;
            msg = "参数不能为空！";
        }
        BusTypeEnum busTypeEnum = BusTypeEnum.PREPAY;
        try{
            Double totalFeeDouble = Double.valueOf(totalFee);
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            if(SystemConstant.SUCCESS.equals(code)) {
                // 3.所有支付都作为一种业务，所以这里先记录入数据库，方便之后统一和扩展
                PayBusiness payBusiness = new PayBusiness();
                String busId = BusTypeEnum.getSuffix(); // 获取无意义id
                payBusiness.setBusId(busId);
                payBusiness.setTotalFee(totalFeeDouble);
                payBusiness.setBusType(busTypeEnum.getCode());
                payBusiness.setMPIID(payListParams.get("MpiID"));
                payBusiness.setPayMPIID(patient.getMpiId());
                payBusiness.setOrganId(organID);
                payBusiness.setDetails(orderId);
                payBusiness.setPayListParams(JSONObject.toJSONString(payListParams));
                PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                payBusinessDAO.savePayBusiness(payBusiness);

                NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
                Map<String, String> callbackParamsMap = Maps.newHashMap();
                callbackParamsMap.put("price", String.valueOf(payBusiness.getTotalFee()));
                resultMap = payService.immediatlyPayForBus(payWay, BusTypeEnum.PREPAY.getCode(), payBusiness.getId(), payBusiness.getOrganId(), callbackParamsMap);
                logger.info("PaymentService.wxPayment 返回：" + JSONUtils.toString(resultMap));
                return resultMap;
            }else{
                resultMap.put("code",code);
                resultMap.put("msg",msg);
            }
            return resultMap;
        }catch (Exception e){
            logger.error(LocalStringUtil.format("住院预交发起支付失败, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw new DAOException(ErrorCode.SERVICE_ERROR, PayConstant.ORGAN_NOT_OPEN_ONLINE_PAY_MSG);
        }
    }

    /**
     * 申请住院预交
     * @param totalFee
     * @param organID
     * @param orderId
     * @param payListParams
     * @return
     */
    @RpcService
    public PayBusiness requestPrepay(String totalFee, Integer organID, String orderId, Map<String, String> payListParams){
        logger.info("requestPrePay totalFee[{}], organID[{}], orderId[{}], payListParams[{}]", totalFee, organID, orderId, payListParams);
        // 1.校验参数
        if(ValidateUtil.blankString(totalFee)){
            logger.info("requestPrePay*****totalFee["+totalFee+"]为零，该笔费用不存在*****");
        }
        if(ValidateUtil.nullOrZeroInteger(organID)|| ValidateUtil.blankString(orderId)){
            logger.error("requestPrePay necessary param null, totalFee[{}], organID[{}], orderId[{}], payListParams[{}]", totalFee, organID, orderId, payListParams);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必传参数为空");
        }
        BusTypeEnum busTypeEnum = BusTypeEnum.PREPAY;
        try{
            Double totalFeeDouble = Double.valueOf(totalFee);
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            // 3.所有支付都作为一种业务，所以这里先记录入数据库，方便之后统一和扩展
            PayBusiness payBusiness = new PayBusiness();
            String busId = BusTypeEnum.getSuffix(); // 获取无意义id
            payBusiness.setBusId(busId);
            payBusiness.setTotalFee(totalFeeDouble);
            payBusiness.setBusType(busTypeEnum.getCode());
            payBusiness.setMPIID(payListParams.get("MpiID"));
            payBusiness.setPayMPIID(patient.getMpiId());
            payBusiness.setOrganId(organID);
            payBusiness.setDetails(orderId);
            payBusiness.setPayListParams(JSONObject.toJSONString(payListParams));
            if(totalFeeDouble==0){
                payBusiness.setPayflag(1);
            }
            PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
            payBusinessDAO.savePayBusiness(payBusiness);
            return payBusiness;
        }catch (Exception e){
            logger.info("requestPrePay exception, totalFee[{}], organID[{}], orderId[{}], payListParams[{}], errorMessage[{}], stackTrace[{}]",  totalFee, organID, orderId, payListParams, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @RpcService
    public Map<String,Object> wxPaySucc(String mpiID, Integer organID, String busId, List<Map<String,String>> reqlist){

        Map<String, Object> resultMap = new HashMap<>();
        String code = SystemConstant.SUCCESS;
        String msg = "";
        // 1.校验参数
        if(StringUtil.isNullOrEmpty(mpiID) || StringUtil.isNullOrEmpty(busId) || reqlist == null || reqlist.size()==0){
            logger.error("PaymentService.wxPaySucc*****参数不能为空*****");
            code = SystemConstant.FAIL;
            msg = "参数不能为空！";
        }

        try{
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patientLogin = (Patient) urt.getProperty("patient");
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient patient = patientDAO.getPatientByMpiId(mpiID);
            PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
            PayBusiness payBusiness = payBusinessDAO.getById(Integer.valueOf(busId));

            if(SystemConstant.SUCCESS.equals(code) && payBusiness!=null) {
                // 2.如果是待缴费支付，则需要把相关电子处方的配送方式写入数据库和his
                if(BusTypeEnum.OUTPATIENT.getCode().equals(payBusiness.getBusType())){
                    RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                    RecipeService recipeService = AppContextHolder.getBean("eh.recipeService", RecipeService.class);

                    String temp_orderid = null;
                    String temp_sendway = null;
                    String temp_addressId = null;
                    Recipe recipe = null;
                    Map<String, String> tempmap2 = null;
                    for(Map<String, String> tempmap : reqlist){
                        temp_orderid = tempmap.get("OrderID");
                        temp_sendway = tempmap.get("sendWay");
                        temp_addressId = tempmap.get("addressId");
                        recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(temp_orderid,organID);
                        if(recipe == null){
                            throw new DAOException("获取不到处方订单");
                        }

                        if("1".equals(temp_sendway)){
                            recipe.setGiveMode(1);
                            recipe.setAddressId(Integer.parseInt(temp_addressId));
                        }else{
                            recipe.setGiveMode(2);
                        }

//                        tempmap2 = recipeService.updateRecipePayResult(recipe,true,patientLogin.getMpiId());
                        logger.info("PaymentService.wxPaySucc...处方是【"+recipe+"】的完成支付配送选择返回结果是："+tempmap2);
                    }

                }


            }else{
                resultMap.put("code",code);
                resultMap.put("msg",msg);
            }
        }catch (Exception e){
            logger.error("门诊缴费or住院预交发起支付失败："+e.getMessage());
            WxRefundExecutor executor = new WxRefundExecutor(
                    Integer.parseInt(busId), "outpatient");
            executor.execute();
            resultMap.put("code","FAIL");
            resultMap.put("msg",e.getMessage());
            return resultMap;
        }


        return resultMap;
    }

    /**
     * not use
     * 某一个电子处方选定配送方式
     * @param orderID 处方单号
     * @param sendWay 配送方式：0到院取药，1送货上门
     * @param addressId 选定地址
     */
    @Deprecated
    @RpcService
    public Map<String,Object> setRecipeAddress(String mpiId, String orderID, String organID, String sendWay, String addressId){
        Map<String,Object> resultmap = new HashMap<String,Object>();
        if(StringUtil.isNullOrEmpty(mpiId) || StringUtil.isNullOrEmpty(orderID) || StringUtil.isNullOrEmpty(organID) || StringUtil.isNullOrEmpty(sendWay)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "setRecipeAddress参数不能为空，请检查传入参数!");
        }

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(orderID,Integer.parseInt(organID));

        if("1".equals(sendWay)){
            recipe.setGiveMode(1);
            if(ValidateUtil.blankString(addressId)){
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "配送地址不能为空，请检查传入参数!");
            }
            recipe.setAddressId(Integer.parseInt(addressId));
        }else{
            recipe.setGiveMode(2);
        }

        RecipeService recipeService = AppContextHolder.getBean("eh.recipeService", RecipeService.class);
//        resultmap.putAll(recipeService.updateRecipePayResult(recipe,false,mpiId));
        resultmap.put("recipe",recipe);
        return resultmap;

    }

    /**
     * his结算
     * @param payBus
     * @return
     */
    public void hisSettlement(PayBusiness payBus){

        String outTradeNo = payBus.getOutTradeNo(); //商户订单号
        Integer busId = payBus.getId();  //业务主键
        String interid = payBus.getDetails();  //订单详情，缴费列表的唯一索引
        int organId = payBus.getOrganId();  //机构编号
        String payMPIID = payBus.getMPIID(); //支付的用户id
        String payWay = payBus.getPayWay(); //支付方式

        AppointTodayBillService service = new AppointTodayBillService();
        PatientDAO patDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient pat = patDAO.getByMpiId(payMPIID);

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(organId);

        InpPreRequest req = new InpPreRequest();
        req.setOrganId(organId);
        req.setCertID(pat.getRawIdcard());
        req.setPatientID(payMPIID);
        req.setInterid(interid);
        req.setPatientName(pat.getPatientName());
        req.setPayWay(payWay);
        double pTotalFee = payBus.getTotalFee(); //支付总金额
        req.setPreAmt(pTotalFee);
        req.setPayerName("");
        req.setPayChannel("");
        req.setPayTradeno(outTradeNo);
        req.setTradeno(payBus.getTradeNo());
        req.setOrganizeCode(o.getOrganizeCode());
//        InHospitalPrepaidDAO inHospDAO = DAOFactory.getDAO(InHospitalPrepaidDAO.class);
//        InHospitalPrepaidResponse inHosp = inHospDAO.getByInterIdAndOrganId(interid,organId);
//        String sBalance = inHosp.getBalance();
//        req.setBalance(Double.valueOf(sBalance));
        //his返回数据
        HisResponse<InpPreResponse> res = service.settleInhosPrePayment(req);
        // res = null 通知his结算不确定错误，暂不退款
        if(res == null ){
            logger.info("住院预缴金结算超时，患者姓名："+pat.getPatientName()+",支付总金额："+payBus.getTotalFee()+",outTradeNo:"+outTradeNo);
        	//logger.info("住院预缴金结算异常！name="+req.getPatientName()+";fee="+req.getPreAmt());
        }else{
        	InpPreResponse inpPreResponse = res.getData();        	
        	if("200".equals(res.getMsgCode()) && inpPreResponse != null){
        		logger.info("住院缴费成功。name="+req.getPatientName()+";fee="+req.getPreAmt());
                String preReceipt = inpPreResponse.getPreReceipt();   //his单据号
                double prePayment = inpPreResponse.getPrePayment(); //预交款总额
                double balance = inpPreResponse.getBalance();  //余额
                double total = prePayment - balance;  //已产生总费用
                PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                payDAO.updateHisbackCode(preReceipt,outTradeNo);
                logger.info("更新his单据号成功。preReceipt="+preReceipt+";outTradeNo="+outTradeNo);
                InHospitalPrepaidDAO inHosDAO = DAOFactory.getDAO(InHospitalPrepaidDAO.class);
                inHosDAO.updatePerPayAndBalance(String.valueOf(prePayment),String.valueOf(balance),String.valueOf(total),interid,organId);
                logger.info("更新预交款总额和余额成功，name="+req.getPatientName()+";fee="+req.getPreAmt());
                String XN_ORGAN = ParamUtils.getParam("XN_ORGAN");
                String[] organs = XN_ORGAN.split(",");
                for (int i=0;i<organs.length;i++){
                    if (organs[i].equals(String.valueOf(req.getOrganId()))) {
                        SmsInfo smsInfo = new SmsInfo();
                        smsInfo.setBusId(busId);
                        smsInfo.setBusType("InhosPrePayment");
                        smsInfo.setSmsType("InhosPrePayment");
                        smsInfo.setOrganId(req.getOrganId());
                        smsInfo.setExtendValue(String.valueOf(pTotalFee));
                        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
                    }
                }
            }else if("-1".equals(res.getMsgCode())){
        		logger.info("住院预缴金结算失败。busId="+busId+";name="+req.getPatientName());
                WxRefundExecutor executor = new WxRefundExecutor(busId, "prepay");
                executor.execute();
        	}else{
        		logger.info("住院预缴金结算不明错误！name="+req.getPatientName()+";fee="+req.getPreAmt());
        	}
        }
    }
}
