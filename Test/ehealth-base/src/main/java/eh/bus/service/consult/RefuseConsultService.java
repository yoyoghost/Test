package eh.bus.service.consult;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.entity.bus.Consult;
import eh.entity.bus.SystemNotificationMsgBody;
import eh.push.SmsPushService;
import eh.remote.IWXServiceInterface;
import eh.task.executor.WxRefundExecutor;
import eh.utils.LocalStringUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 咨询拒绝(医生主动拒绝/定时任务拒绝),调用入口为consultDAO.refuseConsultAndbackMoney
 * Created by zhangx on 2016/6/7
 */
public class RefuseConsultService {
    private static final Log logger = LogFactory.getLog(RefuseConsultService.class);
    private AsynDoBussService asynDoBussService= AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 咨询拒绝(医生主动拒绝/定时任务拒绝),前端调用入口为consultDAO.refuseConsultAndbackMoney
     *
     * @param cancelCause 拒绝原因
     * @param consultId   咨询单主键
     * @param refuseFlag  拒绝标记(0系统自动拒绝;1医生主动拒绝)
     */
    @RpcService
    public void refuseConsultAndbackMoney(String cancelCause,
                                          Integer consultId, Integer refuseFlag) {
        logger.info("咨询拒绝,consultId:" + consultId + ",cancelCause:" + cancelCause);

        if (consultId == null) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        if (StringUtils.isEmpty(cancelCause)) {
//            logger.error("cancelCause is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "cancelCause is required");
        }
        if (refuseFlag == null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "refuseFlag is required");
        }

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDAO.getById(consultId);
        if(consult.getConsultStatus().equals(ConsultConstant.CONSULT_STATUS_FINISH)){
            logger.error(LocalStringUtil.format("can not reject consult with current status[{}]", consult.getConsultStatus()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者已结束咨询，不能拒绝");
        }

        //更新咨询单状态
        consultDAO.updateConsultForRefuse2(new Date(), cancelCause, consultId, 3,
                refuseFlag);
        consultDAO.updateStatusByConsultId(10,consultId);
        // 退还患者咨询费
        WxRefundExecutor executor = new WxRefundExecutor(consultId, "consult");
        executor.execute();

        asynDoBussService.fireEvent(new BussCancelEvent(consultId, BussTypeConstant.CONSULT));
        //拒绝咨询，退款成功，给申请人发送微信推送消息、推送消息、短信(图文咨询)
        Integer clientId = consult.getDeviceId();
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService",SmsPushService.class);
        smsPushService.pushMsgData2Ons(consultId,consult.getConsultOrgan(),"PushPatRefundSucc","PushPatRefundSucc",clientId);


        // 发送系统通知消息到医患消息记录表
        if(consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC)
                || consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE)
                || consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)) {
            String systemNotification = "咨询已被医生拒绝！";
            if (refuseFlag == 0) {
                systemNotification = "已过" + SystemConstant.ONLINE_CONSULT_TIME + "小时，咨询单自动拒绝";
            }
            ConsultMessageService msgService = new ConsultMessageService();
            SystemNotificationMsgBody msgObj = new SystemNotificationMsgBody();
            msgObj.setType(ConsultConstant.SYSTEM_MSG_TYPE_WITHOUT_LINK);
            msgObj.setText(systemNotification);
            msgObj.setUrl(null);
            msgService.handleSystemNotificationMessage(consultId, msgObj);
        }
        // desc_2016.4.1 zhangjr 系统自动拒绝给目标医生发系统消息
        // desc_2016.6.8 zhangx 系统自动拒绝，电话咨询给目标医生发送系统消息;个人图文咨询给目标医生发送系统消息;
        //desc_2017年4月18日10:19:45 yuanb 变为系统自动取消  走取消路线
        // 团队图文咨询,当有医生接收，给执行医生发送系统消息；没有医生接收，给团队中所有的医生发送系统消息
//        if (refuseFlag != null && refuseFlag == 0) {
//            //pushSystemMsgForSystemRefuseToDoc(consult);
//            smsPushService.pushMsgData2Ons(consultId,consult.getConsultOrgan(),"PushSysMsgForSysFToD","PushSysMsgForSysFToD",clientId);
//
//        }
        //yuanb 2017年6月30日10:13:09  为了同步咨询状态 发送一条CMD消息  到session组
        consult.setConsultStatus(3);
        consultDAO.sendCMDMsgToRefreshConsult(consult);


        try {
            if (ConsultConstant.CONSULT_TYPE_GRAPHIC .equals(consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())) {
                IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                wxService.reloadConsult(consult.getAppId(), consultId); // 刷新微信咨询单缓存
            }
        } catch (Exception e) {
           logger.error(e);
        }
    }

}
