package eh.mindgift.service;

import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.account.constant.ServerPriceConstant;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorAccountDAO;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.evaluation.TmpsensitiveMsgBodyMQ;
import eh.entity.mindgift.MindGift;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.MindGiftDAO;
import eh.push.SmsPushService;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.Date;


public class MindGiftMsgService {
    private static final Log logger = LogFactory.getLog(MindGiftMsgService.class);

    /**
     * 支付成功，则发送ons消息处理敏感词
     * @param mindGiftId
     */
    public static void doAfterPaySuccess(Integer mindGiftId){
        MindGiftDAO dao= DAOFactory.getDAO(MindGiftDAO.class);
        MindGift mind=dao.get(mindGiftId);

        //发送ONS消息，处理敏感词
        SendSensitiveOnsMsg(mindGiftId);
    }

    /**
     * 发送Ons消息
     * @param mindGiftId
     * @return
     */
    private static  void SendSensitiveOnsMsg(Integer mindGiftId) {
        try {
            final Publisher publisher = MQHelper.getMqPublisher();
            final TmpsensitiveMsgBodyMQ tMsg = new TmpsensitiveMsgBodyMQ();
            tMsg.setBusId(mindGiftId);
            tMsg.setBusType(BussTypeConstant.MINDGIFT);
            tMsg.setCreateTime(new Date());
            logger.info("心意["+mindGiftId+"]支付成功，发送敏感词处理ons消息："+ JSONUtils.toString(tMsg));
            publisher.publish(OnsConfig.sensitiveTopic, tMsg);
        } catch (Exception e) {
            logger.error("mindGift:" + mindGiftId + " send to ons failed:" + e.getMessage());
        }

    }

    /**
     * 敏感词处理成功，则发送系统消息，信鸽消息，咨询单另发送咨询会话消息,给予积分
     * @param mindGiftId
     */
    public static void doAfterSensitiveWords(Integer mindGiftId,String filtText){
        logger.info("心意["+mindGiftId+"]敏感词["+filtText+"]处理成功，发送消息");
        MindGiftDAO dao= DAOFactory.getDAO(MindGiftDAO.class);
        DoctorAccountDAO accountDao=DAOFactory.getDAO(DoctorAccountDAO.class);

        MindGift mind=dao.get(mindGiftId);

        Double docPrice=mind.getPrice();
        Double rate=Double.parseDouble(ParamUtils.getParam(ParameterConstant.KEY_DOCACCOUNT_RATE,SystemConstant.rate.toString()));
        Double docAccount= new BigDecimal(docPrice).multiply(new BigDecimal(rate)).doubleValue();

        //更新敏感词以及医生积分
        Integer updatedNum=dao.updateSensitiveWordsAndAccountInfoById(MindGiftConstant.MINDGIFT_STATUS_AUDITED,filtText,docAccount,mindGiftId);

        //更新了有效数据，则进行接下来的操作
        if(updatedNum>0){

            //给予积分
            Integer servicePriceId= ServerPriceConstant.ID_MINDGIFT;
            accountDao.addDoctorRevenue(mind.getDoctorId(),servicePriceId,mindGiftId,docPrice);

            // 咨询业务，需要在咨询对话框中发送一条对话消息
            if(BussTypeConstant.CONSULT==mind.getBusType()){
                if(mind.getSubBusType().intValue()!=MindGiftConstant.SUBBUSTYPE_DHZX){
                    ConsultMessageService msgService = new ConsultMessageService();
                    msgService.sendMindGiftNotificationMessage(mindGiftId);
                }else{
                    logger.info("心意单["+mind.getMindGiftId()+"]不是图文咨询/专家解读/寻医问药，往聊天框中插聊天记录");
                }

            }

            //发送系统消息，信鸽消息
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2Ons(mindGiftId, mind.getOrgan(), "MindGiftSuccess", "MindGiftSuccess", mind.getClientId());
        }

    }



}
