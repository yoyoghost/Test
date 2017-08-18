package eh.bus.his.service;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.appoint.mode.*;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.OutpatientDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfig;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.his.*;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.remote.IHisServiceInterface;
import eh.task.executor.WxRefundExecutor;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoStore;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zhongzx on 2016/10/28 0028.
 */
public class AppointTodayBillService {

    private static final Log logger = LogFactory.getLog(AppointTodayBillService.class);

    /**
     * 释放号源
     */
    public static void releaseAppointSource(AppointRecord appointRecord) {
        Integer appointSourceID = appointRecord.getAppointSourceId();
        if (appointSourceID == null || appointSourceID.intValue() == 0) {
            return;
        }
        AppointSourceDAO sourceDao = DAOFactory.getDAO(AppointSourceDAO.class);
        AppointSource source = sourceDao.get(appointSourceID);
        if (source.getUsedNum() > 0) {
            sourceDao.updateUsedNumByAppointSourceId(source.getUsedNum() - 1, appointSourceID);
        }
    }

    /**
     *his返回的业务单号查询预约、挂号、门诊、住院预缴费查询
     * @param paymentResultRequest
     * @return
     */
    @RpcService
    public HisResponse queryPaymentResult(PaymentResultRequest paymentResultRequest){
        logger.info("******his返回的业务单号查询预约、挂号、门诊、住院预缴费接收参数patientID="+paymentResultRequest.getPatientID()
                +",certID="+paymentResultRequest.getCertID()
                +",businessID="+paymentResultRequest.getBusinessID()
                +",BusinessType="+paymentResultRequest.getBusinessType() );
        HisResponse hisResponse = new HisResponse();
        boolean s = DBParamLoaderUtil.getOrganSwich(paymentResultRequest.getOrganId());
        if(s){
            IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
//            PaymentResultRequestTO to = new PaymentResultRequestTO();
//            BeanUtils.copy(paymentResultRequest,to);
//            HisResponseTO r = appointService.queryPaymentResult(to);
//            BeanUtils.copy(r,hisResponse);
        }else {
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(paymentResultRequest.getOrganId());
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".paymentResultService";
            Object obj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryPaymentResult", paymentResultRequest);
            if(obj==null){
                return null;
            }
            hisResponse = (HisResponse)obj;
        }
        return hisResponse;
    }

    /**
     * 挂号 预结算
     *request
     */
    @RpcService
    public  HisResponse<PreBillResponse>  settlePreBill(PreBillRequest request){
        HisResponse<PreBillResponse> hisResponse = new HisResponse();
        try {
            logger.info("******his预结算参数" + JSONUtils.toString(request));

            Integer organId = request.getOrganId();
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".regPreAccountService";
            logger.info("hisServiceId:" + hisServiceId);

            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            Integer appointRecordId = request.getAppointRecordId();
            AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);
            //金额
            Double clinicPrice = appointRecord.getClinicPrice();

//            ClientSetDAO dao = DAOFactory.getDAO(ClientSetDAO.class);
//            RpcServiceInfo info = dao.getByOrganIdAndServiceName(hisServiceId);
//            RpcServiceInfoStore rpcServiceInfoStore = AppDomainContext.getBean("eh.rpcServiceInfoStore", RpcServiceInfoStore.class);
//            RpcServiceInfo info = rpcServiceInfoStore.getInfo(hisServiceId);

            /**现在增加一种情况 就是免费的挂号
             * 如果 没有预结算接口直接调结算接口
             * 如果预结算接口返回也是0也直接调结算接口
             * 如果我们这里显示免费 但是预结算接口显示有金额  就要支付成功以后才走结算接口
             */
            //如果没有预结算返回
//            if(null == info){
//                //免费的挂号 如果 没有预结算接口直接调结算接口
//                if(null != clinicPrice && 0 == clinicPrice){
//                    //支付状态置为1
//                    appointRecord.setPayFlag(1);
//                    settleRegBill(appointRecord);
//                }
//                hisResponse.setMsgCode("200");
//                hisResponse.setMsg("");
//                PreBillResponse r = new PreBillResponse();
//                r.setPersonAmt(appointRecord.getClinicPrice());//没有预结算以排班号源价格为准
//                hisResponse.setData(r);
//            }else {
                boolean s = DBParamLoaderUtil.getOrganSwich(organId);
                PreBillResponse preBillResponse = new PreBillResponse();
                if(s){
                	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                    PreBillRequestTO to = new PreBillRequestTO();
                    BeanUtils.copy(request,to);
                    HisResponseTO<PreBillResponseTO> r = appointService.regPreAccount(to);
                    BeanUtils.copy(r,hisResponse);
                    if(r.isSuccess()){
                        PreBillResponseTO preBillResponseTO = r.getData();
                        BeanUtils.copy(preBillResponseTO,preBillResponse);
                        hisResponse.setData(preBillResponse);
                    }

                }else{
                    hisResponse = (HisResponse) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "regPreAccount", request);
                    preBillResponse = hisResponse.getData();
                }
                //如果有预结算接口，根据医院返回的费用 更新挂号费用
                if (null != hisResponse && "200".equals(hisResponse.getMsgCode())) {
                    //PreBillResponse preBillResponse = hisResponse.getData();
                    logger.info("******his预结算返回体 hisResponse:" + JSONUtils.toString(hisResponse));
                    Double personAmt = preBillResponse.getPersonAmt();
                    //更新个人金额 和医院优惠金额（总金额 可以由两者相加获得）
                    if (null != personAmt) {
                        appointRecord.setClinicPrice(personAmt);
                    }
                    Double discountsAmt = preBillResponse.getDiscountsAmt();
                    if (null != discountsAmt) {
                        appointRecord.setDiscountsAmt(discountsAmt);
                    }
                    String regReceipt = preBillResponse.getRegReceipt();
                    logger.info("******his预结算成功 regReceipt:" + regReceipt);
                    if(null!=regReceipt){
                        appointRecord.setRegReceipt(regReceipt);
                    }
                    String regId = preBillResponse.getRegId();
                    if(regId!=null&&!"".equals(regId)){
                        appointRecord.setRegId(regId);
                    }
                    if(!StringUtils.isEmpty(preBillResponse.getPatientID())){
                        appointRecord.setClinicId(preBillResponse.getPatientID());
                    }
                    if(!StringUtils.isEmpty(preBillResponse.getCardId())){
                        appointRecord.setCardId(preBillResponse.getCardId());
                    }
                    if(!StringUtils.isEmpty(preBillResponse.getCardType())){
                        appointRecord.setCardType(preBillResponse.getCardType());
                    }
                    AppointRecord appointRecordNew = appointRecordDAO.update(appointRecord);
                    logger.info("******his预结算成功 返回个人支付金额:" + personAmt);
                    //如果有预结算接口 返回值是0 或者为空并且号源价格本身为0 就认为是免费的挂号 直接调用结算
                    if(null != clinicPrice && 0 == clinicPrice && (null == personAmt || 0 == personAmt)){
                        //认为已支付
                        appointRecordNew.setPayFlag(1);
                        //settleRegBill(appointRecordNew);
                    }
                } else if (null != hisResponse) {
                    logger.error("******his预结算故障 msgCode:" + hisResponse.getMsgCode() + " errMsg:" + hisResponse.getMsg());
                } else {
                    logger.error("******his预结算故障 errMsg:HisResponse 返回为空");
                }
//            }
            return hisResponse;
        }catch (Exception e){
            logger.error("调用挂号预结算接口失败 errorMsg:" + e.getMessage());
        }
        return null;
    }

    /**
     *判断医院是否能够支付
     * */
    @RpcService
    public boolean canShowPayBtn(Integer appointRecordId){
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = dao.getByAppointRecordId(appointRecordId);
        OrganConfigDAO organConfigDao = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig = organConfigDao.getByOrganId(appointRecord.getOrganId());
        Integer payDay = organConfig.getPayAhead();//-2不支持 ， -1 不限制 ， 0 当天 ，N 提前N天
        boolean f =  true;
        if(payDay==null || payDay.intValue()==-2){
            f = false;
        }

        int status = appointRecord.getAppointStatus().intValue();
        boolean sFlag = false;
        if(status==0||status==4){//预约成功、待支付的 可显示支付按钮
            sFlag = true;
        }
        HisServiceConfig hisServiceConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(appointRecord.getOrganId());
        boolean isPassOut = true;
        Date endTime_db = appointRecord.getEndTime();
        //咸阳支付按钮显示。
        if(hisServiceConfig!=null && "yypt".equals(hisServiceConfig.getAppDomainId())){
            SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm");
            String myDate = sdf2.format(endTime_db);
            int hour = Integer.parseInt(myDate.substring(0, 2));
            Date endTime = null;
            if(hour<=12){
                endTime= DateConversion.getDateByTimePoint(endTime_db, "11:30");
            }else{
                endTime= DateConversion.getDateByTimePoint(endTime_db, "16:30");
            }
            if(new Date().compareTo(endTime)<=0){
                isPassOut = false;
            }
        }else{
            isPassOut = endTime_db.compareTo(new Date())<0;
        }
        return f&&sFlag&&!isPassOut;

//        if(payDay==null){
//            return false;
//        }
//        int days = DateConversion.getDaysBetween(new Date(), appointRecord.getWorkDate());
//        return payDay>=days;
    }

    /**
     * 业务中调用his挂号预结算接口
     * @param appointRecordId
     */
    @RpcService
    public  Map<String,Object>  settlePreBillForBus(Integer appointRecordId, String payWay){
        logger.info("开始调用预结算接口 id:"+appointRecordId.intValue()+"  payWay:"+payWay);
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = dao.getByAppointRecordId(appointRecordId);
        Map<String,Object> resMap = new HashMap<>();//结果
        if(null != appointRecord){
            if(appointRecord.getRecordType()!=null&&appointRecord.getRecordType().intValue()!=1){
                OrganConfigDAO organConfigDao = DAOFactory.getDAO(OrganConfigDAO.class);
                OrganConfig organConfig = organConfigDao.getByOrganId(appointRecord.getOrganId());
                Integer payDay = organConfig.getPayAhead();
                int days = DateConversion.getDaysBetween(new Date(), appointRecord.getWorkDate());
                if(payDay==null){
                    resMap.put("errorCode","-1");
                    resMap.put("errorMsg","该医院不支持预约支付！");
                    return resMap;
                }
                int status = appointRecord.getAppointStatus().intValue();
                String organAppointID = appointRecord.getOrganAppointId();
                if(status == 9 ){//预约成功以后进行预结算
                    resMap.put("errorCode","-1");
                    resMap.put("errorMsg","医院确认中，暂时不能支付");
                    return resMap;
                }
                if(appointRecord.getPayFlag()!=null&&appointRecord.getPayFlag().intValue()==1){
                    resMap.put("errorCode","-1");
                    resMap.put("errorMsg","该订单已经支付");
                    return resMap;
                }
//                if(ValidateUtil.blankString(organAppointID)){
//                    throw new DAOException(609, "医院确认中，暂时不能支付");
//                }
                if(days>payDay.intValue()&&payDay.intValue()!=-1){
                    if(payDay.intValue()==0){
                        resMap.put("errorCode","-1");
                        resMap.put("errorMsg","您好，因医院实际情况，请在就诊当天再支付");
                        return resMap;
                    }
                    resMap.put("errorCode","-1");
                    resMap.put("errorMsg","您好，因医院实际情况，请在就诊前"+payDay.intValue()+"天再支付");
                    return resMap;
                }
            }

            //病人建档
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            Map<String, Object> patientMap = hisServiceConfigDao.canAppointAndCanFile(appointRecord.getOrganId(),appointRecord.getMpiid());
            resMap.put("patient",patientMap);
            Integer canFile = (Integer)patientMap.get("canFile");
            boolean hasOrNot = (boolean)patientMap.get("hasOrNot");
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            List<HealthCard> card = healthCardDAO.findByMpiIdAndCardOrgan(appointRecord.getMpiid(), appointRecord.getOrganId());//病历号也是医院卡证的一种
            if(card!=null && !card.isEmpty()){
                hasOrNot = true;
            }
            Object canAppoint = patientMap.get("canAppoint");
            if(!hasOrNot){
            	if(canFile==null||canFile.intValue()==0 || (canFile!=null&&canFile.intValue()==1)){
            		throw new DAOException(ErrorCode.SERVICE_ERROR,"尚未查到您在医院的病历档案信息，请前往医院新建档案");
            	}
                if( canFile==null||canFile.intValue()==0){
                    resMap.put("errorCode","1");
                    resMap.put("errorMsg","尚未查到您在医院的病历档案信息，请前往医院新建档案！");
                    return resMap;
                }
                if(canFile!=null&&canFile.intValue()==1){
                    resMap.put("errorCode","1");
                    resMap.put("errorMsg","尚未查到您在医院的病历档案信息，您可直接新建档案！");
                    return resMap;
                }
            }
            logger.info("调用预结算接口patient返回:"+patientMap.toString());
            //组装参数
            resMap = setRequest(appointRecord);
            /*PreBillRequest request = new PreBillRequest();
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ o = organDAO.getByOrganId(appointRecord.getOrganId());
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient patient = patientDAO.getPatientByMpiId(appointRecord.getMpiid());
            if(null != patient){
                String c = LocalStringUtil.getSubstringByDiff(patient.getIdcard(), "-");
                request.setIdCard(c);
                request.setPatientName(patient.getPatientName());
                request.setMobile(patient.getMobile());
            }
            request.setAppointRecordId(appointRecordId);
            request.setOrganAppointID(appointRecord.getOrganAppointId());
            request.setDeptId(appointRecord.getAppointDepartId());
            request.setOrderNum(appointRecord.getOrderNum());
            request.setSchedulingID(appointRecord.getOrganSchedulingId());
            request.setWorkDate(appointRecord.getWorkDate());
            request.setWorkType(String.valueOf(appointRecord.getWorkType()));
            request.setOrganId(appointRecord.getOrganId());
            request.setOrganizeCode(o.getOrganizeCode());
            if (appointRecord.getRecordType()!=null&&0 == appointRecord.getRecordType().intValue()) {
                request.setPreregFlag("1");
            } else {
                request.setPreregFlag("2");
            }
            request.setRegType(String.valueOf(appointRecord.getSourceLevel()));
            request.setPatientID(appointRecord.getClinicId());
            request.setOrganAppointID(appointRecord.getOrganAppointId());
            if (patient != null && patient.getGuardianFlag() != null) {
                request.setGuardianFlag(patient.getGuardianFlag());
            }
            if (patient != null && patient.getGuardianName() != null) {
                request.setGuardianName(patient.getGuardianName());
            }
            EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            List<String> jobNums = employmentDAO.findJobNumberByDoctorIdAndOrganId(appointRecord.getDoctorId(), appointRecord.getOrganId());
            if(null != jobNums){
                request.setDoctorID(jobNums.get(0));
            }else{
                request.setDoctorID("");
            }
            //调用his预结算接口
            logger.info("调用his预结算接口settlePreBill");
            HisResponse<PreBillResponse>  res = settlePreBill(request);
            if(res==null){
                throw new DAOException(609, "获取支付金额失败！");
            }
            if(res.isSuccess()){
                resMap.put("preBill",res.getData().getPersonAmt());
                if(appointRecord.getRecordType()==null||appointRecord.getRecordType().intValue()!=1){
                    dao.updateStatusById(4,appointRecord.getAppointRecordId());
                }
                appointRecord.setRegId(res.getData().getRegId());
                appointRecord.setClinicId(res.getData().getPatientID());//病历号
                appointRecord.setClinicMzId(res.getData().getPatientMzID());//门诊号
                appointRecord.setClinicPrice(res.getData().getPersonAmt());
                dao.updatePreParam(res.getData().getPatientID(),res.getData().getPatientMzID(),res.getData().getRegId(),
                        res.getData().getPersonAmt(),
                        appointRecord.getAppointRecordId());
                logger.info("调用his预结算接口settlePreBill成功");
                resMap.put("errorCode","canPay");
                resMap.put("errorMsg","结算成功");
                resMap.put("preBill",res.getData().getPersonAmt());
            }else{
                logger.info("调用his预结算接口settlePreBill失败" + res.getMsg());
                if("5".equals(res.getMsgCode())){
                    resMap.put("errorCode","-1");
                    resMap.put("errorMsg","此挂号费已支付，不可重复支付。");
                    appointRecord.setPayFlag(1);
                    appointRecord.setAppointStatus(5);
                    dao.update(appointRecord);
                }else {
                    resMap.put("errorCode", "-1");
                    resMap.put("errorMsg", res.getMsg());
                    if (appointRecord.getRecordType()!=null&&0 == appointRecord.getRecordType().intValue()) {
                       throw  new DAOException(res.getMsg());
                    }
                }
            }*/
            logger.info("预结算返回前端："+resMap.toString());
            return resMap;
        }else{
            throw new DAOException(609, "记录不存在");
        }
    }

    /*
     * 预结算组装参数已
     */
    public Map<String,Object> setRequest(AppointRecord appointRecord){
    	Map<String,Object> resMap = new HashMap<>();//结果
    	AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
    	//组装参数
        PreBillRequest request = new PreBillRequest();
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(appointRecord.getOrganId());
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getPatientByMpiId(appointRecord.getMpiid());
        if(null != patient){
            String c = LocalStringUtil.getSubstringByDiff(patient.getIdcard(), "-");
            request.setIdCard(c);
            request.setPatientName(patient.getPatientName());
            request.setMobile(patient.getMobile());
        }
        request.setTelClinicFlag(appointRecord.getTelClinicFlag());
        request.setAppointRecordId(appointRecord.getAppointRecordId());
        request.setOrganAppointID(appointRecord.getOrganAppointId());
        request.setDeptId(appointRecord.getAppointDepartId());
        request.setOrderNum(appointRecord.getOrderNum());
        request.setSchedulingID(appointRecord.getOrganSchedulingId());
        request.setWorkDate(appointRecord.getWorkDate());
        request.setWorkType(String.valueOf(appointRecord.getWorkType()));
        request.setOrganId(appointRecord.getOrganId());
        request.setOrganizeCode(o.getOrganizeCode());
        request.setCardType(appointRecord.getCardType());
        request.setCardId(appointRecord.getCardId());
        request.setPatientID(appointRecord.getClinicId());
        if (appointRecord.getRecordType()!=null&&0 == appointRecord.getRecordType().intValue()) {
            request.setPreregFlag("1");
        } else {
            request.setPreregFlag("2");
        }
        request.setRegType(String.valueOf(appointRecord.getSourceLevel()));
        request.setPatientID(appointRecord.getClinicId());
        request.setOrganAppointID(appointRecord.getOrganAppointId());
        if (patient != null && patient.getGuardianFlag() != null) {
            request.setGuardianFlag(patient.getGuardianFlag());
        }
        if (patient != null && patient.getGuardianName() != null) {
            request.setGuardianName(patient.getGuardianName());
        }
        request.setPrice(appointRecord.getClinicPrice());
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<String> jobNums = employmentDAO.findJobNumberByDoctorIdAndOrganId(appointRecord.getDoctorId(), appointRecord.getOrganId());
        if(null != jobNums){
            request.setDoctorID(jobNums.get(0));
        }else{
            request.setDoctorID("");
        }
        //调用his预结算接口
        logger.info("调用his预结算接口settlePreBill");
        HisResponse<PreBillResponse>  res = settlePreBill(request);
        if(res==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "获取支付金额失败！");
        }
        if(res.isSuccess()){
            resMap.put("preBill",res.getData().getPersonAmt());
            if(appointRecord.getRecordType()==null||appointRecord.getRecordType().intValue()!=1){
                dao.updateStatusById(4,appointRecord.getAppointRecordId());
            }
            appointRecord.setRegId(res.getData().getRegId());
            appointRecord.setClinicId(res.getData().getPatientID());//病历号
            appointRecord.setClinicMzId(res.getData().getPatientMzID());//门诊号
            appointRecord.setClinicPrice(res.getData().getPersonAmt());
            appointRecord.setRegReceipt(res.getData().getRegReceipt());
            dao.updatePreParam(res.getData().getPatientID(),res.getData().getPatientMzID(),res.getData().getRegId(),
                    res.getData().getPersonAmt(),res.getData().getRegReceipt(),
                    appointRecord.getAppointRecordId());
            logger.info("调用his预结算接口settlePreBill成功");
            resMap.put("errorCode","canPay");
            resMap.put("errorMsg","结算成功");
            resMap.put("preBill",res.getData().getPersonAmt());
        }else{
            logger.info("调用his预结算接口settlePreBill失败" + res.getMsg());
            if("5".equals(res.getMsgCode())){
                resMap.put("errorCode","-1");
                resMap.put("errorMsg","此挂号费已支付，不可重复支付。");
                appointRecord.setPayFlag(1);
                appointRecord.setAppointStatus(5);
                dao.update(appointRecord);
            }else {
                resMap.put("errorCode", "-1");
                resMap.put("errorMsg", res.getMsg());
                //预结算失败 抛出异常返回给前端提示
//                if (appointRecord.getRecordType()!=null&&0 == appointRecord.getRecordType().intValue()) {
                throw  new DAOException(ErrorCode.SERVICE_ERROR,"获取支付金额失败！"+res.getMsg());
//                }
            }
        }
        return resMap;
    }

    /**
     * 挂号结算
     * @param request
     */
    public  HisResponse<MakeBillResponse> settleBill(MakeBillRequest request){
        try {
            logger.info("预约结算参数:" + JSONUtils.toString(request));
            Integer organId = request.getOrganId();
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".regAccountService";
            logger.info("hisServiceId:" + hisServiceId);
            boolean s = DBParamLoaderUtil.getOrganSwich(organId);
            HisResponse<MakeBillResponse> hisResponse = new HisResponse<MakeBillResponse>();
            if(s){
                logger.info("s=====================true,s="+s);
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                MakeBillRequestTO to = new MakeBillRequestTO();
                BeanUtils.copy(request,to);
                HisResponseTO<MakeBillResponseTO> r = appointService.regAccount(to);
                if(r==null){
                    if(organId==1000423){
                        //his结算返回null，organId=1000423是高州医院
                        logger.info("his预约挂号结算返回null，organId=1000423是高州医院");
                        PaymentResultRequest paymentResultRequest=new PaymentResultRequest();
                        paymentResultRequest.setOrganId(organId);
                        paymentResultRequest.setBusinessID(request.getRegId());
                        paymentResultRequest.setBusinessType(2);
                        paymentResultRequest.setTradeNo(request.getTradeno());
                        paymentResultRequest.setOutTradeNo(request.getPayTradeno());
                        HisResponse hisResponseResult=queryPaymentResult(paymentResultRequest);
                        if(null==hisResponseResult || !hisResponseResult.getMsgCode().equals("200")){
                            logger.info("organId=1000423高州医院预约挂号结算查询request.getRegId()未找到" + request.getRegId());
                            paymentResultRequest.setBusinessID(request.getPayTradeno());
                            hisResponseResult=queryPaymentResult(paymentResultRequest);
                        }
                        if(null!=hisResponseResult && hisResponseResult.getMsgCode().equals("200")){
                            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                            AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(request.getAppointRecordId());
                            r=new HisResponseTO<MakeBillResponseTO>();
                            r.setMsgCode(hisResponseResult.getMsgCode());
                            MakeBillResponseTO makeBillResponseTO=new MakeBillResponseTO();
                            makeBillResponseTO.setRegId(appointRecord.getRegId());
                            makeBillResponseTO.setRegReceipt(appointRecord.getRegReceipt());
                            makeBillResponseTO.setOrderNum(appointRecord.getOrderNum());
                            makeBillResponseTO.setAccountDate(appointRecord.getAccountDate());
                            r.setData(makeBillResponseTO);
                            logger.info("organId=1000423高州医院预约挂号结算查询结果"+JSONUtils.toString(r));
                        }else{
                            logger.info("organId=1000423高州医院预约挂号结算查询失败");
                            return null;
                        }
                    }else{
                        logger.error("******his结算故障 errMsg:HisResponse 返回为空"+request.getAppointRecordId());
                        return null;
                    }
                }
                logger.info("settleBill,r="+JSONUtils.toString(r));
                BeanUtils.copy(r,hisResponse);
                MakeBillResponse makeBillResponse = new MakeBillResponse();
                if(r.isSuccess()){
                    BeanUtils.copy((MakeBillResponseTO)r.getData(),makeBillResponse);
                    logger.info("settleBill,makeBillResponse="+makeBillResponse);
                    hisResponse.setData(makeBillResponse);
                    logger.info("settleBill,hisResponse="+hisResponse);
                }
            }else{
                logger.info("s=====================false,s="+s);
                Object o = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "regAccount", request);
                if(o==null){
                    if(organId==1000423) {
                        //his结算返回null，organId=1000423是高州医院
                        logger.info("his预约挂号结算返回null，organId=1000423是高州医院");
                        PaymentResultRequest paymentResultRequest=new PaymentResultRequest();
                        paymentResultRequest.setOrganId(organId);
                        paymentResultRequest.setBusinessID(request.getRegId());
                        paymentResultRequest.setBusinessType(2);
                        paymentResultRequest.setTradeNo(request.getTradeno());
                        paymentResultRequest.setOutTradeNo(request.getPayTradeno());
                        HisResponse hisResponseResult=queryPaymentResult(paymentResultRequest);
                        if(null==hisResponseResult || !hisResponseResult.getMsgCode().equals("200")){
                            logger.info("organId=1000423高州医院预约挂号结算查询request.getRegId()未找到" + request.getRegId());
                            paymentResultRequest.setBusinessID(request.getPayTradeno());
                            hisResponseResult=queryPaymentResult(paymentResultRequest);
                        }
                        if(null!=hisResponseResult && hisResponseResult.getMsgCode().equals("200")){
                            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                            AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(request.getAppointRecordId());
                            o = new HisResponse<MakeBillResponse>();
                            ((HisResponse) o).setMsgCode(hisResponseResult.getMsgCode());
                            MakeBillResponse makeBillResponse = new MakeBillResponse();
                            makeBillResponse.setRegId(appointRecord.getRegId());
                            makeBillResponse.setRegReceipt(appointRecord.getRegReceipt());
                            makeBillResponse.setOrderNum(appointRecord.getOrderNum());
                            makeBillResponse.setAccountDate(appointRecord.getAccountDate());
                            ((HisResponse) o).setData(makeBillResponse);
                            logger.info("organId=1000423高州医院预约挂号结算查询结果" + JSONUtils.toString(o));
                        }else{
                            logger.info("organId=1000423高州医院预约挂号结算查询失败");
                            return null;
                        }
                    }else{
                        logger.error("******his结算故障 errMsg:HisResponse 返回为空"+request.getAppointRecordId());
                        return null;
                    }

                }
                hisResponse = (HisResponse) o;
            }
            if( "200".equals(hisResponse.getMsgCode())){
                logger.info("******his结算返回体:" + JSONUtils.toString("appointRecordId:"+ request.getAppointRecordId() + "**返回体:" + JSONUtils.toString(hisResponse)));
            }else {
                logger.error("******his结算故障 msgCode:" + hisResponse.getMsgCode() + " errMsg:" + hisResponse.getMsg());
            }
            return hisResponse;
        }catch (Exception e){
            logger.error("调用挂号结算接口失败 errorMsg:" + e.getMessage());
            return null;
        }
    }

    /**
     * 业务中挂号结算接口
     * @param appointRecord
     * @return
     */
    public  Map<String, Object> settleRegBill(AppointRecord appointRecord){
        if(null == appointRecord){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "appointRecord is null");
        }
        logger.info("开始调用结算接口："+JSONUtils.toString(appointRecord));
        Map<String, Object> resMap = new HashMap<>();
        String code = "success";
        String msg = "医院结算成功";
        try{
        if(null != appointRecord) {
            logger.info("---------appointRecord!=null");
            Integer payFlag = appointRecord.getPayFlag();
            //支付成功才调用结算接口
            if(null != payFlag && 1 == payFlag) {
                logger.info("---------payFlag=1");
                //组装参数
                MakeBillRequest request = new MakeBillRequest();
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient patient = patientDAO.getPatientByMpiId(appointRecord.getMpiid());
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                Organ o = organDAO.getByOrganId(appointRecord.getOrganId());
                if(null != patient){
                    logger.info("---------patient!=null");
                    request.setPatientName(patient.getPatientName());
                    String certID = appointRecord.getCertId();
                    String c = LocalStringUtil.getSubstringByDiff(certID, "-");
                    request.setIdCard(c);
                    request.setMobile(patient.getMobile());
                }
                request.setCardId(appointRecord.getCardId());
                request.setCardType(appointRecord.getCardType());
                request.setPatientID(appointRecord.getClinicId());
                //0-预约 1-挂号
                Integer recordType = appointRecord.getRecordType();
                if (0 == recordType) {
                    logger.info("---------recordType==0");
                    request.setPreregFlag("1");
                    request.setDelayPay("1");
                } else {
                    logger.info("---------recordType!=0");
                    request.setPreregFlag("2");
                    request.setDelayPay("0");
                }
                Integer appointRecordId = appointRecord.getAppointRecordId();
                request.setTelClinicFlag(appointRecord.getTelClinicFlag());
                request.setAppointRecordId(appointRecordId);
                request.setRegType(String.valueOf(appointRecord.getSourceLevel()));
                request.setDeptId(appointRecord.getAppointDepartId());
                request.setOrganizeCode(o.getOrganizeCode());
                //个人支付金额
                Double clinicPrice = appointRecord.getClinicPrice();
                //医院优惠金额
                Double discountsAmt = appointRecord.getDiscountsAmt();
                request.setDiscountsAmt(discountsAmt);
                //设置总金额
                if(null != discountsAmt){
                    logger.info("---------discountsAmt!=null");
                    BigDecimal personAmt = new BigDecimal(clinicPrice);
                    BigDecimal discAmt = new BigDecimal(discountsAmt);
                    BigDecimal regAmt = personAmt.add(discAmt);
                    request.setRegAmt(regAmt.doubleValue());
                }else{
                    logger.info("---------discountsAmt==null");
                    request.setRegAmt(clinicPrice);
                }
                request.setPersonAmt(clinicPrice);
                request.setOrderNum(appointRecord.getOrderNum());
                String payWay = appointRecord.getPayWay();
                //支付方法、渠道
                request.setPayWay(payWay);
                request.setPayChannel(payWay);
                request.setOrganId(appointRecord.getOrganId());
                request.setSchedulingID(appointRecord.getOrganSchedulingId());
                request.setWorkDate(appointRecord.getWorkDate());
                request.setWorkType(String.valueOf(appointRecord.getWorkType()));

                //支付平台单号
                request.setTradeno(appointRecord.getTradeNo());
                //传平台生成的流水号
                request.setPayTradeno(appointRecord.getOutTradeNo());
                request.setPayDate(appointRecord.getPaymentDate());
                request.setOrganAppointID(appointRecord.getOrganAppointId());
                request.setRegId(appointRecord.getRegId());
                //获取医生工号
                EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
                List<String> jobNums = employmentDAO.findJobNumberByDoctorIdAndOrganId(appointRecord.getDoctorId(), appointRecord.getOrganId());
                if (null != jobNums) {
                    logger.info("---------jobNums!=null");
                    request.setDoctorID(jobNums.get(0));
                } else {
                    logger.info("---------jobNums==null");
                    request.setDoctorID("");
                }
                request.setRegReceipt(appointRecord.getRegReceipt());

                if (patient != null) {
                    logger.info("---------patient!=null");
                    if (patient.getGuardianFlag() != null) {
                        logger.info("---------patient.getGuardianFlag()!=null");
                        request.setGuardianFlag(patient.getGuardianFlag());
                    }
                    if (patient.getGuardianName() != null) {
                        logger.info("---------patient.getGuardianFlag()==null");
                        request.setGuardianName(patient.getGuardianName());
                    }
                }
                //调用医院结算接口
                logger.info("---------调用医院结算接口");
                HisResponse<MakeBillResponse> response = settleBill(request);

                AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointmentResponse appointmentResponse = new AppointmentResponse();
                appointmentResponse.setId(String.valueOf(appointRecordId));

                if ( null != response && null != response.getData() && "200".equals(response.getMsgCode())) {
                    logger.info("******his结算成功"+appointRecordId);
                    MakeBillResponse makeBillResponse = response.getData();
                    if(null != makeBillResponse) {
                        String regId = makeBillResponse.getRegId();
                        if(StringUtils.isNotEmpty(regId)) {
                            appointmentResponse.setAppointID(regId);
                        }
                        Integer orderNum = makeBillResponse.getOrderNum();
                        if(null != orderNum) {
                            appointmentResponse.setOrderNum(orderNum);
                        }
                        if(StringUtils.isNotEmpty(makeBillResponse.getCardId())){
                            appointmentResponse.setCardId(makeBillResponse.getCardId());
                        }
                        if(StringUtils.isNotEmpty(makeBillResponse.getCardType())){
                            appointmentResponse.setCardType(makeBillResponse.getCardType());
                        }
                        appointmentResponse.setRegReceipt(makeBillResponse.getRegReceipt());
                        appointmentResponse.setAccountDate(makeBillResponse.getAccountDate());

                        AppointSourceDAO sourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
                        AppointSource source = sourceDAO.get(appointRecord.getAppointSourceId());
                        if(null != source && !StringUtils.isEmpty(source.getClinicAddr())){
                            appointmentResponse.setClinicArea(source.getClinicAddr());
                        }
                        if(!StringUtils.isEmpty(makeBillResponse.getClinicAddr())){
                            appointmentResponse.setClinicArea(makeBillResponse.getClinicAddr());
                        }

                    }
                    //如果医院结算成功，调用his成功后的更新接口(包括微信推送)
                    logger.info("******his结算成功222");
                    recordDAO.updateAppointForTodayBill(appointmentResponse,appointRecord);
                }
                else {
                    if(null != response){
                        appointmentResponse.setErrCode(response.getMsgCode());
                        appointmentResponse.setErrMsg(response.getMsg());
                        logger.info("******his结算失败"+appointRecordId+" msgCode:" + response.getMsgCode() + "**msg" + response.getMsg());
                    }else{
                        appointmentResponse.setErrCode("-1");
                        appointmentResponse.setErrMsg("结算返回为空");
                        logger.info("******his结算失败 hisResponse 返回为空"+appointRecordId);
                        //结算返回为null 直接结束，不发起退款
                        code = "fail";
                        msg = "医院结算系统故障，结算返回为空";
                        resMap.put("code", code);
                        resMap.put("msg", msg);
                        return resMap;
                    }

                    //医院结算失败，调用his失败后的更新接口（包括微信推送）
                    if (appointRecord.getRecordType()!=null&&1 == appointRecord.getRecordType().intValue()) {
                        //当天挂号自动取消
                        recordDAO.cancelForHisFail(appointmentResponse);
                    }
                    //TODO 释放号源
                    releaseAppointSource(appointRecord);

                    //支付金额不为0的时候进行退款
                    if(null != clinicPrice && 0 != clinicPrice &&
                            payFlag!=null&&payFlag.intValue()==1) {
                        try {
                            //微信退款
                        	String busType = "appoint";
                        	if(appointRecord.getTelClinicFlag() == 2){
                        		busType = "appointcloud";
                        	}
                            WxRefundExecutor executor = new WxRefundExecutor(appointRecordId, busType);
                            executor.execute();
                            //预约的结算失败仍旧是待支付状态， 用户还可以再次发起支付
                            //if (appointRecord.getRecordType()!=null&&0 == appointRecord.getRecordType().intValue()) {
                            //    recordDAO.updateAppointStatusAndPayFlagByAppointRecordId(4,0,appointRecordId);
                            //}
                        } catch (Exception e) {
                            logger.error("*****微信退款异常！appointRecordId[" + appointRecordId + "],err[" + e.getMessage() + "]");
                            recordDAO.updateAppointStatusAndPayFlagByAppointRecordId(7, 4, appointRecordId);

                        }
                    }
                    code = "fail";
                    msg = "医院结算系统故障，结算失败，挂号失败，进行退款。";
                }
            }else{
                code = "fail";
                msg = "该挂号记录还未支付";
            }
        }else{
            code = "fail";
            msg = "挂号平台序号找不到挂号记录";
        }
        }catch (Exception e){
            logger.error(e.getMessage());
        }
        resMap.put("code", code);
        resMap.put("msg", msg);
        return resMap;
    }

    /**
     * 住院预交
     * @param request
     */
    public HisResponse<InpPreResponse> settleInhosPrePayment(InpPreRequest request){
    	Integer organId = request.getOrganId();
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
        //调用服务id
        logger.info("住院预交hisService参数inpPrePayment:" + JSONUtils.toString(request));
        boolean s = DBParamLoaderUtil.getOrganSwich(organId);
        HisResponse<InpPreResponse> hisResponse = null;
        if(s){
        	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            InpPreRequestTO to = new InpPreRequestTO();
            BeanUtils.copy(request,to);
            HisResponseTO<InpPreResponseTO> r = appointService.inpPrePayment(to);
            hisResponse =new HisResponse<InpPreResponse>();
            BeanUtils.copy(r,hisResponse);
            if(r.isSuccess()){
                InpPreResponseTO dataTO = r.getData();
                InpPreResponse data = new InpPreResponse();
                BeanUtils.copy(dataTO,data);
                hisResponse.setData(data);
            }
        }else{
        	String hisServiceId = cfg.getAppDomainId() + ".queryNoPayService";
        	logger.info("hisServiceId"+ hisServiceId);
        	hisResponse = (HisResponse) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "inpPrePayment", request);
        }
        logger.info("his返回数据====》》》"+ JSONUtils.toString(hisResponse));
        return hisResponse;
    }
}
