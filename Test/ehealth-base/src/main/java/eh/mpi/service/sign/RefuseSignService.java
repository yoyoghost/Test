package eh.mpi.service.sign;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.ScratchableConstant;
import eh.base.dao.DoctorDAO;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.service.housekeeper.HouseKeeperService;
import eh.entity.base.Doctor;
import eh.entity.his.sign.SignCommonBean;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.push.SmsPushService;
import eh.task.executor.WxRefundExecutor;
import eh.utils.DateConversion;

import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;


public class RefuseSignService {

    /**
     * 拒绝签约申请
     * @param signRecordId
     * @param cause
     * @return
     */
    @RpcService
    public Boolean refuseSignRecord(Integer signRecordId,String cause) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);

        if (StringUtils.isEmpty(cause)) {
            cause="医生已拒绝了您的申请";
        }

        SignRecord sign = signDao.get(signRecordId);
        if(null == sign){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "signRecord is null");
        }
        Patient patient = patientDAO.getByMpiId(sign.getRequestMpiId());
        Doctor doctor = doctorDAO.getByDoctorId(sign.getDoctor());
        //2017-4-11 15:03:17 zhangx为 兼容健康APP老版本，保存前将是否预签约标记设置为不预签约
        int preSign=sign.getPreSign()==null?SignRecordConstant.SIGN_IS_NOT_PRESIGN:sign.getPreSign();

        if(preSign == 1) {
            int flag = HouseKeeperService.getOfHisIsSigned(patient, doctor);
            if (flag == 1) {
                AcceptSignService.accpetSignRecord(sign, sign.getDoctor().toString(), 1);
                throw new DAOException(ErrorCode.SERVICE_CONFIRM, "已完成签约，不能做拒绝操作");
            } else {
                if (!SignRecordConstant.RECORD_STATUS_APPLYING.equals(sign.getRecordStatus())) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "该申请记录不存在或者已处理");
                }

                sign.setRecordStatus(SignRecordConstant.RECORD_STATUS_REFUSE);
                sign.setCause(cause);
                signDao.updateRequestStatus(sign);

                if (sign.getSignCost() > 0 && sign.getPayFlag() == 1) {
                    WxRefundExecutor executor = new WxRefundExecutor(signRecordId, ScratchableConstant.SCRATCHABLE_MODULE_SIGN);
                    executor.execute();
                }

                AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCancelEvent(signRecordId, BussTypeConstant.SIGN, sign.getDoctor()));
                //拒绝成功发送微信推送
                AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(sign.getSignRecordId(), sign.getOrgan(), "RefuseSign", "", 0);

                return true;
            }
        }else{
            if (!SignRecordConstant.RECORD_STATUS_APPLYING.equals(sign.getRecordStatus())) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该申请记录不存在或者已处理");
            }

            sign.setRecordStatus(SignRecordConstant.RECORD_STATUS_REFUSE);
            sign.setCause(cause);
            signDao.updateRequestStatus(sign);

            if (sign.getSignCost() > 0 && sign.getPayFlag() == 1) {
                WxRefundExecutor executor = new WxRefundExecutor(signRecordId, ScratchableConstant.SCRATCHABLE_MODULE_SIGN);
                executor.execute();
            }

            AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCancelEvent(signRecordId, BussTypeConstant.SIGN, sign.getDoctor()));
            //拒绝成功发送微信推送
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(sign.getSignRecordId(), sign.getOrgan(), "RefuseSign", "", 0);

            return true;
        }
    }

    /**
     * 拒绝签约申请,不填原因
     * @param signRecordId
     * @return
     */
    @RpcService
    public Boolean refuseSignRecordWithNoCause(Integer signRecordId) {
        return refuseSignRecord(signRecordId,"");
    }

    /**
     * 超过48小时自动拒绝(已付款)
     */
    @RpcService
    public void cancelOverTimeAndPush(){
        final SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        Date cancelTime = new Date();
        Date dayBeforeYesterdayDate = DateConversion.getDaysAgo(2);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date dayBeforeYesterday = DateConversion.getDateByTimePoint(dayBeforeYesterdayDate,
                timePoint);
        List<SignRecord> signRecords = signDao.findOverByRequestTime(dayBeforeYesterday);
        for(SignRecord signRecord : signRecords){
            signRecord.setRecordStatus(2);
            signRecord.setFromSign(SignRecordConstant.SIGN_SYSTEM);
            signRecord.setLastModify(new Date());
            signRecord.setCause("对方医生超过48小时未处理，系统自动取消");
            signDao.update(signRecord);

            if(signRecord.getSignCost()>0&&signRecord.getPayFlag()==1) {
                WxRefundExecutor executor = new WxRefundExecutor(signRecord.getSignRecordId(), ScratchableConstant.SCRATCHABLE_MODULE_SIGN);
                executor.execute();
            }

            //拒绝成功发送微信推送
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecord.getSignRecordId(), signRecord.getOrgan(), "RefuseSign", "", 0);
        }
    }

    /**
     * 超过24小时自动取消(待付款)
     */
    @RpcService
    public void cancelOverTime(){
        final SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        Date cancelTime = new Date();
        Date dayBeforeYesterdayDate = DateConversion.getDaysAgo(1);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date dayBeforeYesterday = DateConversion.getDateByTimePoint(dayBeforeYesterdayDate,
                timePoint);
        List<SignRecord> signRecords = signDao.findToApplyByRequestTime(dayBeforeYesterday);
        for(SignRecord signRecord : signRecords){
            signRecord.setRecordStatus(9);
            signRecord.setFromSign(SignRecordConstant.SIGN_SYSTEM);
            signRecord.setLastModify(new Date());
            signRecord.setCause("患者超过24小时未处理未付款的签约申请单，系统自动取消。");
            signDao.update(signRecord);
        }
    }

    /**
     * 超过7*24小时自动取消(预签约)
     */
    @RpcService
    public void cancelOverTimeOfPreSign(){
        final SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        Date cancelTime = new Date();
        Date dayBeforeYesterdayDate = DateConversion.getDaysAgo(7);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date dayBeforeYesterday = DateConversion.getDateByTimePoint(dayBeforeYesterdayDate,
                timePoint);
        List<SignRecord> signRecords = signDao.findToApplyByRequestTimeAndPre(dayBeforeYesterday);
        
        Patient patient = null;
        Doctor doctor = null;
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        RelationDoctorDAO relationDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        for(SignRecord signRecord : signRecords){
        	//update hexy
             patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
             doctor = doctorDAO.getByDoctorId(signRecord.getDoctor());
             //0没接his，1已签，2未签
             int flag = HouseKeeperService.getOfHisIsSigned(patient,doctor);
             switch (flag) {
				case 0:
					signRecord.setCause("超过7天未处理，系统自动取消。");
					signRecord.setFromSign(SignRecordConstant.SIGN_SYSTEM);
					signRecord.setRecordStatus(SignRecordConstant.RECORD_STATUS_CANCEL);
					break;
				case 1:
					signRecord.setCause("his系统已确认签约,base系统自动确认。");
					signRecord.setRecordStatus(SignRecordConstant.RECORD_STATUS_AGREE);
					SignCommonBean signCommonBean = HouseKeeperService.getHisIsSigned(patient, doctor);
					signRecord.setStartDate(signCommonBean.getStartDate());
					signRecord.setEndDate(signCommonBean.getEndDate());
					boolean addFamilyDoctor = relationDao.addFamilyDoctor(signRecord.getRequestMpiId(), signRecord.getDoctor(), new Date(), signRecord.getStartDate(), signRecord.getEndDate());
					if (!addFamilyDoctor) {
						throw new DAOException("定时任务 保存医生签约信息异常,relationDao.addFamilyDoctor");
					}
					break;
				case 2:
					signRecord.setCause("超过7天未处理，系统自动取消。");
					signRecord.setFromSign(SignRecordConstant.SIGN_SYSTEM);
					signRecord.setRecordStatus(SignRecordConstant.RECORD_STATUS_CANCEL);
					break;
				}
            signRecord.setLastModify(new Date());
            signDao.update(signRecord);
        }
    }

    /**
     * 签约到期前?个月向签约患者发送消息(提前几个月由运营平台设置)
     */
    @RpcService
    public void pushMessageOfLeftOneMonth(){
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(0, 0, "SignLeftOneMonth", "", 0);
    }

}
