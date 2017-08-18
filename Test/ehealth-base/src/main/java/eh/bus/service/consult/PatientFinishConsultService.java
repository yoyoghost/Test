package eh.bus.service.consult;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.account.constant.ServerPriceConstant;
import eh.base.constant.BussTypeConstant;
import eh.base.dao.DoctorAccountDAO;
import eh.base.service.DoctorAccountConsultService;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.entity.bus.Consult;
import eh.entity.bus.EvaluationNotificationMsgBody;
import eh.push.SmsPushService;
import eh.utils.ValidateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 患者完成图文咨询接口
 * Created by zhaotm on 2016/5/24.
 */
public class PatientFinishConsultService {
    private static final Log log = LogFactory.getLog(PatientFinishConsultService.class);
    private AsynDoBussService asynDoBussService= AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 患者完成图文咨询接口
     *
     * @param consultId 咨询ID，整型，必选参数
     * @return
     * @requirement 医生已回复的图文咨询单，患者可以完成此咨询单；
     * 并向医生发送系统消息，向患者发送微信模板消息；
     * <p>
     */
    @RpcService
    public Boolean patientFinishGraphicTextConsult(Integer consultId) {
        if (consultId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDAO.getById(consultId);

        if (!consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC)
                && !consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE)
                && !consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)) {
            throw new DAOException(609, "很抱歉，您当前咨询类型不能结束！");
        }
        if (consult.getConsultStatus() > 1 || !consult.getHasChat()) {
            throw new DAOException(609, "很抱歉，该咨询单不能结束！");
        }
        Integer status = 5;
        if(ValidateUtil.isTrue(consult.getTeams())&&ValidateUtil.notNullAndZeroInteger(consult.getGroupMode())){
            status = 11;
        }
        // 更新咨询状态
        consultDAO.updateConsultStatusEndByEndRole(ConsultConstant.CONSULT_END_ROLE_PATIENT, consult.getConsultId(),status);

        // 医生账户增加咨询费
        Integer docId = consult.getExeDoctor();
        if (ValidateUtil.nullOrZeroInteger(docId)) {
            docId = consult.getConsultDoctor();
        }
        Integer serverId = null;
        if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE)) {// 电话咨询
            serverId = ServerPriceConstant.ID_CONSULT_PHONE;
        } else if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC)) {// 图文咨询
            serverId = ServerPriceConstant.ID_CONSULT_GRAPHIC;
        }else if(consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE)){//寻医问药
            serverId = ServerPriceConstant.ID_CONSULT_RECIPE;
        }else if(consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)){//专家解读
            serverId = ServerPriceConstant.ID_CONSULT_PROFESSOR;
        }
        if(serverId==null){
            throw new DAOException(609, "很抱歉，您的咨询类型有问题");
        }
        DAOFactory.getDAO(DoctorAccountDAO.class).addDoctorRevenue(docId, serverId, consult.getConsultId(), consult.getConsultPrice());
        //新增医生咨询补贴
        new DoctorAccountConsultService().rewardConsult(consult.getConsultId());

        asynDoBussService.fireEvent(new BussFinishEvent(consultId, BussTypeConstant.CONSULT));

        // 记录系统通知消息到医患消息表
        ConsultMessageService msgService = new ConsultMessageService();
        String notificationText = "咨询已结束，为医生的耐心解答<a href=\"" + consult.getConsultId() + "\">评价</a>";
        EvaluationNotificationMsgBody msgObj = new EvaluationNotificationMsgBody();
        msgObj.setEvaSwitch(ConsultConstant.EVALUATION_MSG_SWITCH_OFF);//显示评价按钮
        msgObj.setEvaValue(null);
        msgObj.setEvaText(null);
        msgService.handleEvaluationNotificationMessage(consult.getConsultId(), msgObj);

        Integer clientId = consult.getDeviceId();
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService",SmsPushService.class);
        String executor = "PatFinishConsult";
        if(consult.getRequestMode()==4){
            executor = "PatFinishConsultForRecipe";
        }
        smsPushService.pushMsgData2Ons(consultId,consult.getConsultOrgan(),executor,executor,clientId);

        //yuanb 2017年6月30日10:13:09  为了同步咨询状态 发送一条CMD消息  到session组
        consult.setConsultStatus(2);
        consultDAO.sendCMDMsgToRefreshConsult(consult);

        return true;
    }

}
