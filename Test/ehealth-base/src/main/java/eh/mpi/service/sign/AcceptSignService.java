package eh.mpi.service.sign;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.housekeeper.HouseKeeperService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.dao.SignPatientLabelDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.ngari.his.sign.mode.SignCommonBeanTO;
import com.ngari.his.sign.service.ISignHisService;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;


public class AcceptSignService {
    private static final Log logger = LogFactory.getLog(AcceptSignService.class);

    /**
     * 接收签约申请
     * @param signRecordId
     * @return
     */
    @RpcService
    public Boolean acceptSignRecord(Integer signRecordId, Integer doctorId) {
        if(clickCompSign(signRecordId,doctorId.toString(),1)!=null){
            return true;
        }else{
            return false;
        }
    }

    /**
     * 预签约点击“已完成签约按钮”，调his查询判断完成签约
     * @param recordId 签约申请id
     * @param roleId 角色id
     * @param role 角色 1医生2患者
     * @return
     */
    @RpcService
    public String clickCompSign(Integer recordId,String roleId,Integer role){
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        SignRecord signRecord = signRecordDAO.get(recordId);
        Patient patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
        Doctor doctor = doctorDAO.getByDoctorId(signRecord.getDoctor());
        int flag = HouseKeeperService.getOfHisIsSigned(patient,doctor);

        //2017-4-11 15:03:17 zhangx为 兼容健康APP老版本，保存前将是否预签约标记设置为不预签约
        int preSign=signRecord.getPreSign()==null?SignRecordConstant.SIGN_IS_NOT_PRESIGN:signRecord.getPreSign();

        if((flag == 1 && preSign == 1)||preSign == 0){
            boolean sFlag = accpetSignRecord(signRecord,roleId,role);
            if(sFlag) {
                return patient.getMpiId();
            }else{
                return null;
            }
        }

        if(flag == 2  && preSign == 1){
            if(role == 1){
                throw new DAOException(ErrorCode.SERVICE_CONFIRM, "请先完成线下签约");
            }
            if(role == 2){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "暂未签约！");
            }
        }
        if(flag == 0  && preSign == 1){
            if(role == 1){
                throw new DAOException(ErrorCode.SERVICE_DOUBLE_CONFIRM, "请选确认与【"+patient.getPatientName()+"】已完成签约手续？");
            }
        }
        return null;
    }

    /**
     * 已签约
     * @param signRecordId
     * @return
     */
    @RpcService
    public Boolean hasSign(Integer signRecordId, Integer doctorId) {
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        return accpetSignRecord(signRecordDAO.get(signRecordId),doctorId.toString(),2);
    }

    /**
     * 内部方法，同意签约
     * @param signRecord
     * @param roleId
     * @param role
     * @return
     */
    public static boolean accpetSignRecord(final SignRecord signRecord, String roleId, int role){
        final RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        final SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        if (signRecord == null || !SignRecordConstant.RECORD_STATUS_APPLYING.equals(signRecord.getRecordStatus())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该申请记录不存在或者已处理");
        }
        if(role==1&&signRecord.getDoctor()!=Integer.parseInt(roleId)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不能处理不是自己的业务");
        }
        ConsultSet consultSet = consultSetDAO.getById(signRecord.getDoctor());
        signRecord.setSignTime(consultSet.getSignTime());
        signRecord.setStartDate(new Date());
        signRecord.setEndDate(DateConversion.getYearslater(Integer.parseInt(StringUtils.isEmpty(consultSet.getSignTime())?"0":consultSet.getSignTime())));
        
        final SignRecord acceptSign=signRecord;
        acceptSign.setRecordStatus(SignRecordConstant.RECORD_STATUS_AGREE); 
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            public void execute(StatelessSession ss) throws Exception {
                Boolean result;
                Boolean flagHIS = false;
                //hexy update
                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(signRecord.getOrgan());
                if (null == cfg || null == cfg.getSigntohis()) {
               	 	throw new DAOException(ErrorCode.SERVICE_ERROR, "是否对接His系统字段为null.");
                }
                if (cfg.getSigntohis().equals(SignRecordConstant.DOCK_SYSTEM_TRUE)) {
                	PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                	DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                	Doctor doctor = doctorDAO.get(signRecord.getDoctor());
                	Patient patient = patientDAO.getPatientByMpiId(signRecord.getRequestMpiId());

                	acceptSign.setRecordStatus(SignRecordConstant.RECORD_STATUS_CONFIRMATION);
            		eh.entity.his.sign.SignCommonBean signCommonBean = new eh.entity.his.sign.SignCommonBean();
                    signCommonBean.setPatientID(patient.getMpiId());
                    signCommonBean.setPatientName(patient.getPatientName());
                    signCommonBean.setCertID(patient.getIdcard());
                    signCommonBean.setCardType("");
                    signCommonBean.setCardID("");
                    signCommonBean.setMobile(patient.getMobile());
                    signCommonBean.setDoctor(doctor.getDoctorId());
                    signCommonBean.setDoctorName(doctor.getName());
                    signCommonBean.setRecordStatus(SignRecordConstant.RECORD_STATUS_CONFIRMATION.toString());
                    signCommonBean.setSignRecordId(signRecord.getOrganSignId());
                    signCommonBean.setStartDate(signRecord.getStartDate());
                    signCommonBean.setEndDate(signRecord.getEndDate());
                    Employment employment = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctor.getDoctorId());
                    signCommonBean.setJobNum(employment.getJobNumber());
                    signCommonBean.setRelationDate(signRecord.getRequestDate());
                    String hisServiceId = cfg.getAppDomainId() + ".signService";
                    if (DBParamLoaderUtil.getOrganSwich(signCommonBean.getOrgan())) {
                        ISignHisService iSignHisService = AppDomainContext.getBean("his.iSignHisService", ISignHisService.class);
                        SignCommonBeanTO reqTO = new SignCommonBeanTO();
                        BeanUtils.copy(signCommonBean, reqTO);
                        flagHIS = iSignHisService.registSign(reqTO);
                    } else {
                        flagHIS = (Boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "registSign", signCommonBean);
                    }
                    logger.info("签约his写入" + flagHIS + "=========mpiId=" + signRecord.getRequestMpiId() + "========doctorId" + signRecord.getDoctor()+"flagHIS"+flagHIS);
                    if (null == flagHIS || !flagHIS) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "医院his写入失败");
                    }
//                    通知his成功， 由his返回业务结果 调用 SignRecordService.updateSignRecordWithHIS
                    acceptSign.setRecordStatus(SignRecordConstant.RECORD_STATUS_CONFIRMATION);
            	}else{
                    flagHIS = true;//不对接his 默认true
                    acceptSign.setRecordStatus(SignRecordConstant.RECORD_STATUS_AGREE);
                }
                signRecordDAO.updateRequestStatus(acceptSign);
                //续约  //TODO 从业务抽离出来
                if(acceptSign.getRenew()!=null && acceptSign.getRenew()==1){
                    List<RelationDoctor> relationDoctorList = relationDao.findSignPatientByDoctorIdAndMpi(signRecord.getRequestMpiId(),signRecord.getDoctor());
                    if(!CollectionUtils.isEmpty(relationDoctorList)){
                        RelationDoctor relationDoctor = relationDoctorList.get(0);
                        GregorianCalendar gc=new GregorianCalendar();
                        gc.setTime(relationDoctor.getEndDate());
                        gc.add(1,1);
                        relationDoctor.setEndDate(gc.getTime());
                        //标记为还没有提醒可续签状态
                        relationDoctor.setRemindPreSign(false);
                        relationDao.update(relationDoctor);
                    }
                }else {
                    relationDao.addFamilyDoctor(acceptSign.getRequestMpiId(), acceptSign.getDoctor(), new Date(), acceptSign.getStartDate(), acceptSign.getEndDate());
                }
                result = savePatientLabel(acceptSign);
                setResult(flagHIS);
            }
        };

        HibernateSessionTemplate.instance().executeTrans(action);
        Boolean sFlag= action.getResult();

        if(sFlag){
            AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussFinishEvent(acceptSign.getSignRecordId(),BussTypeConstant.SIGN));
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(acceptSign.getSignRecordId(), signRecord.getOrgan(), "SignMessage", "", 0);
        }
        return sFlag;
    }

    /**
     * 将居民类型作为医生给患者打的标签
     * @param acceptSign
     * @return
     */
    public static Boolean savePatientLabel(SignRecord acceptSign) throws ControllerException {
        if (acceptSign != null){

            RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);

            //获取签约记录优先查询relationType为2的，如果没有再查为0的
            RelationDoctor relationDoctor = relationDoctorDAO
                    .getSignByMpiAndDocAndType(acceptSign.getRequestMpiId(), acceptSign.getDoctor(), 2);

            if (relationDoctor == null){
                relationDoctor = relationDoctorDAO
                        .getSignByMpiAndDocAndType(acceptSign.getRequestMpiId(), acceptSign.getDoctor(), 0);
            }
            //将居民类型保存到患者标签表
            if (relationDoctor != null){
                Integer relationDoctorId = relationDoctor.getRelationDoctorId(); //医生关注内码
                SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);
                //患者标签列表
                List<Integer> patientLabelList = signPatientLabelDAO.findSplLabelBySignRecordId(acceptSign.getSignRecordId());
                if (patientLabelList != null && patientLabelList.size() > 0){
                    for (int i=0; i < patientLabelList.size(); i++){
                        Integer patientLabel = patientLabelList.get(i); //居民类型

                        RelationLabelDAO relationLabelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
                        //将居民类型插入患者标签表
                        Boolean spl = relationLabelDAO.savePatientLabel(relationDoctorId, patientLabel);
                        if (!spl){
                            logger.info("发起签约申请插入患者标签表失败！");
                            return false;
                        }
                    }
                } else {
                    logger.info("医生同意签约时，患者标签列表为空！");
                    return false;
                }
            } else {
                logger.info("发起签约申请插入居民类型的时候RelationDoctor为空！");
                return false;
            }
        } else {
            logger.info("医生同意签约将居民类型作为医生给患者打的标签时acceptSign为空");
            return false;
        }
        logger.info("医生同意签约将居民类型作为医生给患者打的标签操作成功！");
        return true;
    }

}
