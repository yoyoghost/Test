package eh.mpi.service.sign;

import com.ngari.his.sign.mode.SignCommonBeanTO;
import com.ngari.his.sign.service.ISignHisService;

import ctd.account.Client;
import ctd.account.session.SessionItemManager;
import ctd.account.session.SessionKey;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.housekeeper.HouseKeeperService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.*;
import eh.mpi.constant.SignInitiatorConstant;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.*;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;

import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;


public class RequestSignRecordService {
    private static final Logger logger = LoggerFactory.getLogger(RequestSignRecordService.class);

    /**
     * 判断能否申请签约
     *
     * @param record
     * @return
     */
    @RpcService
    public Boolean canRequestSign(SignRecord record) {
        Boolean returnFlag = true;

        SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);

        String requestMpi = record.getRequestMpiId();
        Integer doctor = record.getDoctor();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig = organConfigDAO.get(record.getOrgan());
        //update hexy
        if(null == organConfig || !organConfig.getCanSign()) {
        	 throw new DAOException(ErrorCode.SERVICE_ERROR, "该机构不支持签约功能");
        }
        
        String doctorIdCard = doctorDAO.get(doctor).getIdNumber();
        String patientIdCard = patientDAO.get(requestMpi).getIdcard();
        if(doctorIdCard.equals(patientIdCard)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "签约患者与目标医生不能为同一人");
        }
        //不是续签的情况下判断
        if(record.getRenew()==null || (record.getRenew()!=null && record.getRenew()==0)){
            RelationDoctor relationPAndD = relationDao.getSignByMpiAndDoc(requestMpi, doctor);
            if (relationPAndD != null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "你们已经是签约关系");
            }
            List<SignRecord> signRecords = signDao.findByMpiId(requestMpi);
            if (!organConfig.getOneTomany()) {
            	 if (!CollectionUtils.isEmpty(signRecords)) {
                 	for (SignRecord signRecord : signRecords) {
           			   Integer organ = signRecord.getOrgan();
           			   //判断是否当前机构
           			   if (organ.equals(record.getOrgan())) {
           				  //查询当前机构是否可以签约多个医生
           			      OrganConfig config = organConfigDAO.get(record.getOrgan());
           				  if (!config.getOneTomany()) {
           					 throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者已和医生建立签约关系，不可再签约。");
           				  }else {
           					 if(record.getDoctor().equals(signRecord.getDoctor())) {
                                   throw new DAOException(ErrorCode.SERVICE_ERROR, "您已发起过对该医生的签约申请，请勿重复申请");
                               }
           				  }
           			   }
          		   }
                 }
            }
            List<SignRecord> signRecordList = signDao.findByDoctorAndMpiId(doctor, requestMpi);
        	if (!CollectionUtils.isEmpty(signRecordList)) {
        		throw new DAOException(ErrorCode.SERVICE_ERROR, "您已发起过对该医生的签约申请，请勿重复申请");
        	} 
        }
        //续签的情况下判断以下情况
        if (record.getRenew()!=null && record.getRenew()==1){
            List<SignRecord> signList=signDao.findRenewSignRecords(requestMpi);
            if (ValidateUtil.notBlankList(signList)){
                throw new DAOException(ErrorCode.SERVICE_ERROR,"您已发起过续签申请，请耐心等待医生答复");
            }

           List<RelationDoctor>relationDoctors=relationDao.findByMpiIdAndDoctorId(requestMpi,doctor);
            for (RelationDoctor r:relationDoctors){
                if (r.getDoctorId()!=null && !r.getDoctorId().equals(doctor)){
                    throw new DAOException(ErrorCode.SERVICE_ERROR,"续签医生和当前医生不一致");
                }
            }
        }

        ConsultSet consultSet = consultSetDAO.getDefaultConsultSet(record.getDoctor());
        if(!consultSet.getSignStatus()||!consultSet.getCanSign()){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "您所申请的医生已经关闭了签约功能");
        }
        

        return returnFlag;
    }

    @RpcService
    public Boolean canPay(Integer signRecordId){
        SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        SignRecord signRecord = signDao.get(signRecordId);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig = organConfigDAO.get(signRecord.getOrgan());
        //续签的情况下点击支付按钮不判断是否已签约
        if(signRecord.getRenew()==null || (signRecord.getRenew()!=null && signRecord.getRenew()==0)){
        	 List<SignRecord> signRecords = signDao.findByMpiId(signRecord.getRequestMpiId());
        	 if (!organConfig.getOneTomany()) {
        		 for (SignRecord entity : signRecords) {
        			 Integer organ = entity.getOrgan();
        			 if (organ.equals(signRecord.getOrgan())) {
        				 OrganConfig config = organConfigDAO.get(entity.getOrgan());
//        				 Doctor doctor = doctorDAO.getByDoctorId(entity.getDoctor());
        				 if (!config.getOneTomany()) {
        					 if (!signRecord.getRecordStatus().equals(SignRecordConstant.RECORD_STATUS_TOPAY)) {
        						 String doctorName = doctorDAO.getNameById(entity.getDoctor());
               					 throw new DAOException(ErrorCode.SERVICE_ERROR, "您已签约"+doctorName+"医生！");
        					 }
           				 }
//        				 if (null != doctor && null != doctor.getIsSign() && !doctor.getIsSign()){
//           					 throw new DAOException(ErrorCode.SERVICE_ERROR, "您签约的医生已关闭签约功能!");
//           			 }
        			 }
				}
        	 }        	         	         	         	 	        
        }
        return true;
    }

    
    /**
     * 生成待支付签约单, 目前健康app使用，可与签约申请统一接口合并
     * @param record
     * @param cardType
     * @param cardId
     * @param patientType
     * @return
     */
    @RpcService
    public Map<String, Object> requestUnPaySign(SignRecord record,String cardType,String cardId,String patientType){

        //2017-4-11 15:03:17 zhangx为 兼容健康APP老版本，保存前将是否预签约标记设置为不预签约
        if(record.getPreSign()==null){
            record.setPreSign(SignRecordConstant.SIGN_IS_NOT_PRESIGN);
        }

        Map<String, Object> map = new HashMap<>();
        SignRecord sign2;
        Integer signId;
        if(record.getPreSign()!=null && record.getPreSign()==1){
            record.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);
            record.setRecordStatus(SignRecordConstant.RECORD_STATUS_APPLYING);
            sign2 = requestSign(record, cardType, cardId, patientType);
            signId = sign2.getSignRecordId();
            map.put("mpiId", sign2.getFromMpiId());
            if (SignRecordConstant.RECORD_STATUS_APPLYING.equals(sign2.getRecordStatus())) {
                AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCreateEvent(sign2, BussTypeConstant.SIGN));
                AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signId, record.getOrgan(), "SignMessage", "", 0);
                AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signId, record.getOrgan(), "SignOfPreMsg", "", 0);
            }
        }else {
            if (ValidateUtil.nullOrZeroDouble(record.getSignCost())) {
                record.setPayFlag(PayConstant.PAY_FLAG_PAY_SUCCESS);
                record.setRecordStatus(SignRecordConstant.RECORD_STATUS_APPLYING);
            } else {
                record.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);
                record.setRecordStatus(SignRecordConstant.RECORD_STATUS_TOPAY);
            }

            sign2 = requestSign(record, cardType, cardId, patientType);
            signId = sign2.getSignRecordId();

            if (SignRecordConstant.RECORD_STATUS_APPLYING.equals(sign2.getRecordStatus())) {
                AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCreateEvent(sign2, BussTypeConstant.SIGN));
                AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signId, sign2.getOrgan(), "SignMessage", "", 0);
            }
            map.put("busId", signId);
        }
        return map;
    }

    private SignRecord requestSign(final SignRecord record,final String cardType,final String cardId,final String patientType) {
        //校验数据完整性
        isValidRequestSignData(record);

        //检查是否能申请签约
        if (!canRequestSign(record)) {
            return new SignRecord();
        }

        try {
            record.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        }catch (Exception e){
            logger.error(LocalStringUtil.format("class[{}] method[{}] set deviceId error! errorMessage[{}]", this.getClass().getSimpleName(), "requestSign", e.getMessage()));
        }
        HibernateStatelessResultAction<SignRecord> action = new AbstractHibernateStatelessResultAction<SignRecord>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                HealthCard healthCard = addHealthCard(record.getRequestMpiId(),cardType,cardId,patientType);
                if(healthCard!=null) {
                    checkOfHis(record, healthCard.getCardType(), healthCard.getCardId());
                }else{
                    checkOfHis(record,cardType,cardId);
                }
                SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
                record.setFromSign(SignRecordConstant.SIGN_PATIENT);
                SignRecord sign2 = signDao.saveSignRecord(record);
                Integer signId = sign2.getSignRecordId();
                //判断写入签约记录表是否成功
                if (signId != null && signId > 0){

                    SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);
                    //将居民类型插入居民类型关系表
                    Boolean plflag = signPatientLabelDAO.saveSignResidentType(signId, sign2.getDoctor(),
                            sign2.getFromMpiId(), record.getPatientLabel(), SignInitiatorConstant.PATIENT_SIGN);

                    if (plflag){ //如果保存居民类型成功
                        setResult(sign2);
                    } else {
                        setResult(new SignRecord());
                        logger.info("患者申请签约付费保存居民类型时失败！");
                        throw new DAOException("申请签约失败！");
                    }
                } else {
                    setResult(new SignRecord());
                    logger.info("患者申请签约付费保存签约记录失败！");
                    throw new DAOException("申请签约失败！");
                }
            }
        };

        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 患者绑定医保卡
     */
    public HealthCard addHealthCard(String mpiId,String cardType,String cardId,String patientType){
        HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        HealthCard healthCard = null;
        if(!"1".equals(patientType)) {
            HealthCard healthCardOld = healthCardDAO.getByTwo(mpiId, Integer.parseInt(patientType));
            HealthCard healthCardExist = healthCardDAO.getByCardOrganAndCardId(Integer.parseInt(patientType),cardId.toUpperCase(),cardId);
            if (healthCardExist == null) {
                if (healthCardOld != null) {
                    healthCardOld.setCardId(cardId.toUpperCase());
                    healthCardOld.setInitialCardID(cardId);
                    healthCardDAO.update(healthCardOld);
                    healthCard = healthCardOld;
                } else {
                    healthCard = new HealthCard(mpiId, cardId.toUpperCase(), cardType, Integer.parseInt(patientType), cardId);
                    healthCardDAO.save(healthCard);
                }
            } else if (!healthCardExist.getMpiId().equals(mpiId)){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该医保卡已被使用");
            }
        }
        patientDAO.updatePatientTypeByMpiId(patientType, mpiId);
        return  healthCard;
    }

    /**
     * 上门签约-添加患者(new)
     * 返回数据格式和之前版本不同导致还未更新的app界面出现null
     * @param p
     * @param doctorId
     * @return
     */
    @RpcService
    public Map getOrUpdatePatientToSignNew(Patient p,Integer doctorId) {
//          logger.info("上门签约-添加患者getOrUpdatePatientToSignNew:" + JSONObject.toJSONString(p));
            getIfCanSign(doctorId, p.getPatientType());
            Map map = new HashMap();
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            Patient patient = patientDAO.getOrUpdate(p);
            HealthCard healthCard = new HealthCard();
            if (p.getHealthCards().size() > 0) {
                healthCard = healthCardDAO.getByCardOrganAndCardId(p.getHealthCards().get(0).getCardOrgan(),
                        p.getHealthCards().get(0).getCardId().toUpperCase(), p.getHealthCards().get(0).getCardId());
                if (null == healthCard){
                	healthCard = healthCardDAO.getByCardOrganAndCardId(p.getHealthCards().get(0).getCardOrgan(),
                            p.getHealthCards().get(0).getCardId().toUpperCase(), p.getHealthCards().get(0).getCardId());
                }
            }
            
            RelationDoctor relation = relationDao.getSignByMpiAndDoc(patient.getMpiId(), doctorId);
            if (relation != null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "你们已经是签约关系");
            }
            List<HealthCard> healthCards = new ArrayList<>();
            healthCards.add(healthCard);
            patient.setHealthCards(healthCards);
            map.put("patient", patient);
            return map;       
    }

    /**
     * 上门签约-添加患者(old)
     * @param p
     * @param doctorId
     * @return
     */
    @RpcService
    public Patient getOrUpdatePatientToSign(Patient p, Integer doctorId) {
        logger.info("上门签约-添加患者getOrUpdatePatientToSign:" + JSONUtils.toString(p));
        Patient patient = null;;
		try {
			getIfCanSign(doctorId,p.getPatientType());
			PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
			RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
			patient = patientDAO.getOrUpdate(p);
			RelationDoctor relation = relationDao.getSignByMpiAndDoc(patient.getMpiId(), doctorId);
			if (relation != null) {
			    throw new DAOException(ErrorCode.SERVICE_ERROR, "你们已经是签约关系");
			}
			//默认关注
			RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
			RelationDoctor relationPatient = new RelationDoctor();
			relationPatient.setDoctorId(doctorId);
			relationPatient.setMpiId(patient.getMpiId());
			relationPatientDAO.addRelationPatient(relationPatient);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DAOException(ErrorCode.SERVICE_ERROR, "上门签约-添加患者异常.");
		}
        return patient;
    }

    /**
     * 医生第一执业机构区域和患者医保所在区域对比
     * @param doctorId
     * @param key
     * @return
     */
    @RpcService
    public Boolean getDiffOfDoctorAndPType(Integer doctorId,String key){
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        PatientTypeDAO patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
        String doctorArea = signRecordDAO.getDoctorArea(doctorId);
        PatientType patientType = patientTypeDAO.get(key);
        if(patientType!=null){
            if(patientType.getAddrArea()!=null&&!"".equals(patientType.getAddrArea())){
                if(doctorArea.length()>=4 && patientType.getAddrArea().length()>=4) {
                    if (!doctorArea.substring(0, 4).equals(patientType.getAddrArea().substring(0, 4))) {
                        return false;
                    }
                }else{
                    if (!doctorArea.substring(0, 2).equals(patientType.getAddrArea().substring(0, 2))) {
                        return false;
                    }
                }
            }
        }else{
            return false;
        }
        return true;
    }

    /**
     * 判断能否上门签约
     * @param doctorId
     * @param key
     * @return
     */
    @RpcService
    public Boolean getIfCanSign(Integer doctorId,String key){
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        PatientTypeDAO patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
        String doctorArea = signRecordDAO.getDoctorArea(doctorId);
        PatientType patientType = patientTypeDAO.get(key);
        if(patientType!=null){
            if(patientType.getAddrArea()!=null&&!"".equals(patientType.getAddrArea())){
                if(doctorArea.length()>=4 && patientType.getAddrArea().length()>=4) {
                    if (!doctorArea.substring(0, 4).equals(patientType.getAddrArea().substring(0, 4))) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "医保地区和医生所在区域不在同一地区");
                    }
                }else{
                    if (!doctorArea.substring(0, 2).equals(patientType.getAddrArea().substring(0, 2))) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "医保地区和医生所在区域不在同一地区");
                    }
                }
            }
        }else{
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医保地区和医生所在区域不在同一地区");
        }
        return true;
    }

    /**
     * 判断是否允许签约（通过his）
     * @return
     */
    public void checkOfHis(SignRecord record,String cardType,String cardId){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getPatientByMpiId(record.getRequestMpiId());
        if(!"1".equals(patient.getPatientType())) {
            String idCard = patient.getIdcard();
            if(cardId != null && cardId.trim().length() > 0 && idCard.substring(idCard.length()-12, idCard.length()).equals(cardId)){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "患者信息和医院录入信息不匹配");
            }else{
                return;
            }
        }
    }

    /**
     * PC诊间签约（需返回签约开始结束时间）
     * @param record
     * @param cardType
     * @param cardId
     * @return
     */
    @RpcService
    public Map saveSignPC(SignRecord record,String cardType,String cardId){
        Map map = new HashMap();
        Date sTime;
        Date eTime;
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet consultSet = consultSetDAO.getById(record.getDoctor());
        if(saveSign(record,cardType,cardId)){
            Date d=new Date();
            sTime = d;
            eTime = DateConversion.getYearslater(Integer.parseInt(StringUtils.isEmpty(consultSet.getSignTime())?"0":consultSet.getSignTime()));
            map.put("sTime",sTime);
            map.put("eTime",eTime);
        }else{
            throw new DAOException(ErrorCode.SERVICE_ERROR, "签约失败");
        }
        return map;
    }

    /**
     * 保存签约(医生端)
     *
     * @param record
     * @return
     */
    @RpcService
    public Boolean saveSign(SignRecord record,String cardType,String cardId) {
//      logger.info("SignRecord:" + JSONUtils.toString(record));
        Client client = SessionItemManager.instance().get(SessionKey.of(Context.CLIENT).<Client>deviceSupport(true));
        if(client!=null){
            if(ClientPlatformEnum.ANDROID.getKey().equals(client.getOs())||ClientPlatformEnum.IOS.getKey().equals(client.getOs())){
                checkOfHis(record,cardType,cardId);
            }
        }
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Patient patient = patientDAO.getPatientByMpiId(record.getRequestMpiId());
        Doctor doctor = doctorDAO.get(record.getDoctor());
        RelationDoctor relation = relationDao.getSignByMpiAndDoc(record.getRequestMpiId(),record.getDoctor());
        ConsultSet consultSet = consultSetDAO.getById(record.getDoctor());
        String doctorIdCard = doctor.getIdNumber();
        String patientIdCard = patient.getIdcard();
        String mpiId = record.getRequestMpiId();
        //update hexy
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
    	OrganConfig organConfig = organConfigDAO.get(record.getOrgan());

        if(null == organConfig || !organConfig.getCanSign()) {
        	 throw new DAOException(ErrorCode.SERVICE_ERROR, "该机构不支持签约功能");
        }
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (StringUtils.isEmpty(patientIdCard)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能签约");
        }

        if(doctorIdCard.equals(patientIdCard)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "签约患者与目标医生不能为同一人");
        }
        if (!consultSet.getCanSign()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "你没签约权限，请联系机构管理员");
        }
        if (!consultSet.getSignStatus()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请打开签约开关");
        }
        if (relation != null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "你们已经是签约关系");
        }
        List<SignRecord> signRecords = signRecordDAO.findByMpiId(record.getRequestMpiId());
        if (!organConfig.getOneTomany()) {
       	 if (!CollectionUtils.isEmpty(signRecords)) {
            	for (SignRecord signRecord : signRecords) {
      			   Integer organ = signRecord.getOrgan();
      			   //判断是否当前机构
      			   if (organ.equals(record.getOrgan())) {
      				  //查询当前机构是否可以签约多个医生
      			      OrganConfig config = organConfigDAO.get(record.getOrgan());
      				  if (!config.getOneTomany()) {
      					 throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者已和医生建立签约关系，不可再签约。");
      				  }else {
      					 if(record.getDoctor().equals(signRecord.getDoctor())) {
                              throw new DAOException(ErrorCode.SERVICE_ERROR, "您已发起过对该医生的签约申请，请勿重复申请");
                          }
      				  }
      			   }
     		   }
            }
       }
    	List<SignRecord> signRecordList = signRecordDAO.findByDoctorAndMpiId(record.getDoctor(), mpiId);
       	if (!CollectionUtils.isEmpty(signRecordList)) {
       		throw new DAOException(ErrorCode.SERVICE_ERROR, "您已发起过对该医生的签约申请，请勿重复申请");
       	}
        if (!"1".equals(patient.getPatientType())) {
            if(signRecordDAO.getDoctorArea(record.getDoctor()).length()>=4 && signRecordDAO.getPatientArea(record.getRequestMpiId()).length()>=4) {
                if (!signRecordDAO.getDoctorArea(record.getDoctor()).substring(0, 4).equals(signRecordDAO.getPatientArea(record.getRequestMpiId()).substring(0, 4))) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "医保地区和医生所在区域不在同一地区");
                }
            }else{
                if (!signRecordDAO.getDoctorArea(record.getDoctor()).substring(0, 2).equals(signRecordDAO.getPatientArea(record.getRequestMpiId()).substring(0, 2))) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "医保地区和医生所在区域不在同一地区");
                }
            }
        }

        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(doctor.getOrgan());
        //写入his标志
        Boolean flagHIS = false;
        Integer recordStatus = SignRecordConstant.RECORD_STATUS_AGREE;
        if(cfg!=null) {
        	//update hexy
        	//是否对接his系统 0否,1是
        	Integer signtohis = cfg.getSigntohis();
        	if (null == signtohis){
        		 throw new DAOException(ErrorCode.SERVICE_ERROR, "是否对接His系统字段为null.");
        	}
        	if (signtohis.equals(SignRecordConstant.DOCK_SYSTEM_TRUE)) {
        		recordStatus = SignRecordConstant.RECORD_STATUS_CONFIRMATION;
        		eh.entity.his.sign.SignCommonBean signCommonBean = new eh.entity.his.sign.SignCommonBean();
                signCommonBean.setPatientID(patient.getMpiId());
                signCommonBean.setPatientName(patient.getPatientName());
                signCommonBean.setCertID(patient.getIdcard());
                signCommonBean.setRequestMpiId(patient.getMpiId());
                signCommonBean.setCardType(cardType);
                signCommonBean.setCardID(cardId);
                signCommonBean.setMobile(patient.getMobile());
                signCommonBean.setDoctor(doctor.getDoctorId());
                signCommonBean.setDoctorName(doctor.getName());
                signCommonBean.setRecordStatus(recordStatus.toString());
                signCommonBean.setOneToMany(organConfig.getOneTomany());
                signCommonBean.setValidateMobile(cfg.isValidateMobile());
                signCommonBean.setValidateHealthSystem(organConfig.isValidateHealthSystem());
                signCommonBean.setSignRecordId(record.getOrganSignId());
                signCommonBean.setStartDate(record.getStartDate());
                signCommonBean.setEndDate(record.getEndDate());
                Employment employment = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctor.getDoctorId());
                signCommonBean.setJobNum(employment.getJobNumber());
                signCommonBean.setRelationDate(record.getRequestDate());
                String hisServiceId = cfg.getAppDomainId() + ".signService";
               
                if (DBParamLoaderUtil.getOrganSwich(signCommonBean.getOrgan())) {
                    ISignHisService iSignHisService = AppDomainContext.getBean("his.iSignHisService", ISignHisService.class);
                    SignCommonBeanTO reqTO = new SignCommonBeanTO();
                    BeanUtils.copy(signCommonBean, reqTO);
                    flagHIS = iSignHisService.registSign(reqTO);
                } else {
                    flagHIS = (Boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "registSign", signCommonBean);
                }
                logger.info("签约his写入" + flagHIS + "=========mpiId=" + record.getRequestMpiId() + "========doctorId" + record.getDoctor()+"flagHIS:"+flagHIS);
                if (null == flagHIS || !flagHIS) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "医院his写入失败");
                }
            }
        }
        SignRecord signRecord = signRecordDAO.getByDoctorAndMpiIdAndRecordStatusNearly(record.getDoctor(), record.getRequestMpiId(), 0 , 1);
        //平台写入标志
        Boolean flag;
        if(signRecord!=null){
        	signRecord.setRecordStatus(recordStatus);
            flag = visitSignUpdate(signRecord,consultSet, record.getPatientLabel());
        }else{
            flag = visitSigAdd(record,consultSet, record.getPatientLabel(),recordStatus);
        }
        return flag;
    }

    /**
     * 上门签约（无未处理的请求）
     * @param s
     * @param c
     * @return
     */
    public Boolean visitSigAdd(SignRecord s, ConsultSet c, final List<String> patientLabel,Integer recordStatus){
        logger.info("visitSigAdd: SignRecord:" + JSONUtils.toString(s) + " patientLabel:" + JSONUtils.toString(patientLabel));
        final RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        final SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        try {
            s.setDeviceId(SessionItemManager.instance().checkClientAndGet());
        }catch (Exception e){
            logger.error(LocalStringUtil.format("class[{}] method[{}] set deviceId error! errorMessage[{}]", this.getClass().getSimpleName(), "requestSign", e.getMessage()));
        }
        Date d=new Date();
        s.setPayFlag(1);
        s.setFromMpiId(s.getRequestMpiId());
        s.setLastModify(d);
        s.setRequestDate(d);
        s.setSignTime(c.getSignTime());
        s.setStartDate(d);
        s.setEndDate(DateConversion.getYearslater(Integer.parseInt(StringUtils.isEmpty(c.getSignTime())?"0":c.getSignTime())));
        s.setRecordStatus(null == recordStatus ? SignRecordConstant.RECORD_STATUS_AGREE:recordStatus);
        s.setFromSign(SignRecordConstant.SIGN_DOCTOR);
        final SignRecord acceptSign = signRecordDAO.save(s);
        final Boolean[] plflag = {false}; //增加居民类型成功标志
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @SuppressWarnings("all")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Boolean result=true;
                boolean bl = relationDao.addFamilyDoctor(acceptSign.getRequestMpiId(), acceptSign.getDoctor(),
                        new Date(), acceptSign.getStartDate(), acceptSign.getEndDate());

                //增加居民类型
                if (bl && patientLabel != null && patientLabel.size() > 0){
                    SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);
                    plflag[0] = signPatientLabelDAO.saveSignResidentType(acceptSign.getSignRecordId(),
                            acceptSign.getDoctor(), acceptSign.getRequestMpiId(), patientLabel,
                            SignInitiatorConstant.DROP_IN_SIGN);
                }

                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if(action.getResult()){
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(acceptSign.getSignRecordId(), acceptSign.getOrgan(), "SignMessage", "", 0);
        }
        return action.getResult() && plflag[0];
    }

    /**
     * 上门签约（有未处理的请求）
     * @param s
     * @param c
     * @return
     */
    public Boolean visitSignUpdate(final SignRecord s, ConsultSet c, final List<String> patientLabel){
        logger.info("visitSignUpdate: SignRecord:" + JSONUtils.toString(s) + " patientLabel:" + JSONUtils.toString(patientLabel));
        final RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        final SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        Date d=new Date();
        s.setPayFlag(1);
        s.setFromMpiId(s.getRequestMpiId());
        s.setLastModify(d);
        s.setSignTime(c.getSignTime());
        s.setStartDate(d);
        s.setEndDate(DateConversion.getYearslater(Integer.parseInt(StringUtils.isEmpty(c.getSignTime())?"0":c.getSignTime())));
        s.setRecordStatus(null == s.getRecordStatus()?SignRecordConstant.RECORD_STATUS_AGREE:s.getRecordStatus());
        s.setFromSign(SignRecordConstant.SIGN_DOCTOR);
        final SignRecord acceptSign=s;

        final Boolean[] plflag = {false}; //增加居民类型成功标志

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Boolean result=true;
                signRecordDAO.updateRequestStatus(acceptSign);
                boolean bl = relationDao.addFamilyDoctor(acceptSign.getRequestMpiId(), acceptSign.getDoctor(),
                        new Date(), acceptSign.getStartDate(), acceptSign.getEndDate());

                //增加居民类型
                if (bl && patientLabel != null && patientLabel.size() > 0) {
                    SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);
                    plflag[0] = signPatientLabelDAO.saveSignResidentType(acceptSign.getSignRecordId(),
                            acceptSign.getDoctor(), acceptSign.getRequestMpiId(), patientLabel,
                            SignInitiatorConstant.DROP_IN_SIGN);
                }

                setResult(result);
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if(action.getResult()){
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(acceptSign.getSignRecordId(), acceptSign.getOrgan(), "SignMessage", "", 0);
        }
        return action.getResult() && plflag[0];
    }

    /**
     * 上门签约
     * @param doctorId
     * @return
     */
    @RpcService
    public Map visitSign(Integer doctorId){
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet consultSet = consultSetDAO.getDefaultConsultSet(doctorId);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        if(null == consultSet){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "consultSet not exist");
        }
        if (!consultSet.getSignStatus() || !consultSet.getCanSign()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请您开启签约功能");
        }
        Map map = new HashMap();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Employment employment = DAOFactory.getDAO(EmploymentDAO.class)
                .getPrimaryEmpByDoctorId(doctorId);

        OrganConfig organConfig = organConfigDAO.get(doctor.getOrgan());
        Double oSignCost = (organConfig.getSignPrice() == null ? 0 : organConfig.getSignPrice()).doubleValue();
        if (consultSet!=null && oSignCost > consultSet.getSignPrice()) {
            consultSet.setSignPrice(oSignCost);
        }
        map.put("doctor",doctor);
        map.put("employment",employment);
        map.put("consultSet",consultSet);
        return map;
    }

//    @RpcService
//    public Boolean requestOneYearSign(SignRecord record) {
//        record.setSignTime("1");
//        return requestSign(record);
//    }

    /**
     * 校验requestSign()数据完整性
     *
     * @param record
     * @return
     */
    private void isValidRequestSignData(SignRecord record) {
        if (StringUtils.isEmpty(record.getRequestMpiId())) {
//            logger.error("requestMpiId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestMpiId is required");
        }
        if (record.getDoctor() == null) {
//            logger.error("doctor is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctor is required");
        }
        if (record.getOrgan() == null) {
//            logger.error("organ is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organ is required");
        }
        if (record.getDepart() == null) {
//            logger.error("depart is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "depart is required");
        }

        if (record.getDepart() == null) {
//            logger.error("depart is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "depart is required");
        }
    }

    /**
     * 根据id查签约记录
     */
    @RpcService
    public SignRecord getBySignRecordId(Integer id){
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        return signRecordDAO.get(id);
    }

    /**
     * 获取签约到期前?个月的患者列表
     * @return
     */
    @RpcService
    public List<RelationDoctor> getMpiList(){
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        List<RelationDoctor> relationDoctorList = relationDoctorDAO.queryMpiList();
        return relationDoctorList;
    }

}
