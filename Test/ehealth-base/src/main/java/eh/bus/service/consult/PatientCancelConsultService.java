package eh.bus.service.consult;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.CallRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.coupon.service.CouponService;
import eh.entity.base.Doctor;
import eh.entity.bus.CallRecord;
import eh.entity.bus.Consult;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.task.executor.WxRefundExecutor;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

import static eh.bus.constant.ConsultConstant.CONSULT_CANCEL_ROLE_PATIENT;
import static eh.bus.constant.ConsultConstant.CONSULT_STATUS_PENDING;

/**
 * 患者取消图文咨询接口
 * Created by zhaotm on 2016/5/24.
 */
public class PatientCancelConsultService {
    private static final Log log = LogFactory.getLog(PatientCancelConsultService.class);
    private AsynDoBussService asynDoBussService= AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 患者取消图文咨询接口
     *
     * @param consultId 咨询ID，整型，必选参数
     * @return
     * @requirement 医生未回复的图文咨询单，且咨询人当天取消咨询次数未达到上限次数（3次），患者可以取消此咨询单；
     * 系统（目前为 全额）退款给患者微信账户，并向医生发送系统消息，向患者发送微信模板消息；
     */
    @RpcService
    public Boolean patientCancelGraphicTextConsult(Integer consultId) {
        String cancelCause = "医生未回复，患者取消";
        return CancelConsult(consultId,cancelCause, CONSULT_CANCEL_ROLE_PATIENT);
    }

    /**
     *  取消咨询入口
     *
     *  微信3.0 咨询单超时变成取消，增加cancelRole字段判断：0：患者取消  1：系统超时自动取消
     *  2017年4月17日17:22:42
     * @param consultId
     * @param cancelCause
     * @param cancelRole
     * @return
     */
    @RpcService
    public boolean CancelConsult(Integer consultId,String cancelCause,Integer cancelRole){
        if (consultId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        CallRecordDAO callDao = DAOFactory.getDAO(CallRecordDAO.class);
        // 判断该咨询是否满足取消条件：图文咨询；医生是否有回复；咨询人当天取消次数是否已达取消上限（目前3次）
        Consult consult = consultDAO.getById(consultId);
        if(null ==consult ){
            throw new DAOException(609,"consult not find");
        }
        if(cancelRole .equals(CONSULT_CANCEL_ROLE_PATIENT)&&consult.getPayflag().equals(CONSULT_STATUS_PENDING)){
            cancelCause = "未支付，患者取消";
        }
        //2016-6-16 luf：2.1.1需求添加团队文案提示
        if ( null != consult.getConsultStatus() && consult.getConsultStatus() == 1) {
            Boolean teams = consult.getTeams();
            if (null == teams) {
                Doctor consultD = DAOFactory.getDAO(DoctorDAO.class).get(consult.getConsultDoctor());
                if (null != consultD && null != consultD.getTeams() && consultD.getTeams()) {
                    teams = true;
                } else {
                    teams = false;
                }
            }
            if (teams&&cancelRole==0) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "医生已接收您的咨询，无法取消该咨询单.");
            }
        }
        if (null !=consult.getConsultStatus()&&consult.getConsultStatus() > 1
                && CONSULT_STATUS_PENDING!=consult.getConsultStatus()) {
            throw new DAOException(609, "很抱歉，当前咨询状态不能执行取消操作！");
        }
        if (null!=consult.getHasChat()&&(consult.getHasChat())) {
            throw new DAOException(608, "医生已回复您的咨询，无法取消该咨询单.");
        }
        //电话咨询取消判断
        if( ConsultConstant.CONSULT_TYPE_POHONE .equals( consult.getRequestMode())) {
            // 根据bussType 和bussId 获取通话列表最新的一条记录
            List<CallRecord> list = callDao.findByBussIdAndBussType(consultId, 3);
            if (null != list && list.size() > 0) {
                throw new DAOException(608, "医生已回复您的咨询，无法取消该咨询单.");
            }
        }
        if (consult.getConsultStatus() != CONSULT_STATUS_PENDING
                && cancelRole.equals(CONSULT_CANCEL_ROLE_PATIENT)) {
            // 获取当前患者信息
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            String requestMpiId = patient.getMpiId(); //"2c9081814cd4ca2d014cd4ddd6c90000";
            List<Consult> todayCancelConsultList = consultDAO.findTodayCancelConsultTimesByRequestMpiId(requestMpiId, consult.getRequestMode());
            if (!CollectionUtils.isEmpty(todayCancelConsultList) &&
                    todayCancelConsultList.size() >= ConsultConstant.PATIENT_GRAPHIC_CONSULT_CANCEL_TIMES_MAX) {
                throw new DAOException(609, "您今天的取消次数已达到上限.");
            }
        }
        // 执行取消咨询操作： 更新咨询状态；若是付费咨询需退款给患者；给患者发微信模板消息；给医生发系统消息；
        Integer status = cancelRole==0?8:9;
        consultDAO.updateConsult(new Date(), cancelCause, consultId, 9,cancelRole,status);
        if (ValidateUtil.notBlankString(consult.getTradeNo()) && ValidateUtil.notNullAndZeroInteger(consult.getPayflag()) && consult.getPayflag() == PayConstant.PAY_FLAG_PAY_SUCCESS) {
            // 执行微信退款
            WxRefundExecutor executor = new WxRefundExecutor(
                    consult.getConsultId(), "consult");
            executor.execute();
        }else if(ValidateUtil.notNullAndZeroInteger(consult.getCouponId()) && consult.getCouponId()>0){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.unlockCouponById(consult.getCouponId());
        }
        Patient dbPatient = DAOFactory.getDAO(PatientDAO.class).get(consult.getRequestMpi());

        Integer clientId = consult.getDeviceId();
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        String executor = "PatCancelConsult";
        if(consult.getRequestMode()==4){
            executor = "PatCancelConsultForRecipe";
        }
        smsPushService.pushMsgData2Ons(consultId, consult.getConsultOrgan(), executor, executor, clientId);

        // desc_2017年4月18日10:21:21 yuanb咨询单超时取消发送系统消息
        if (cancelRole == 1) {
            //pushSystemMsgForSystemRefuseToDoc(consult);
            executor = "PushSysMsgForSysFToD";
            if(consult.getRequestMode().equals(4)){
                executor = "PushSysMsgForSysFToDForRecipe";
            }
            smsPushService.pushMsgData2Ons(consultId,consult.getConsultOrgan(),executor,executor,clientId);

        }

        log.info("consult cancel success......");
        asynDoBussService.fireEvent(new BussCancelEvent(consultId, BussTypeConstant.CONSULT));
        return true;
    }

    /**
     * 支付失败更新咨询单状态为取消
     * 因电话咨询时段查询，新增此服务
     *
     * @param consultId
     * @return
     * @date 2016-7-16 luf
     */
    @RpcService
    public Boolean cancelAppointForPayFail(int consultId) {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        consultDAO.updateConsultStatusByConsultId(9, consultId);
        return true;
    }
}
