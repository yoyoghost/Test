package eh.push;

import ctd.account.session.ClientSession;
import ctd.net.broadcast.MQHelper;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.msg.SmsInfo;
import eh.msg.dao.SmsInfoDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.Date;

/**
 * 消息推送改造
 * Created by Administrator on 2016/12/16.
 */
public class SmsPushService {
    private static final Logger logger = LoggerFactory.getLogger(SmsPushService.class);

    private void setInternalClientIdIfHave(SmsInfo smsInfo){
        ClientSession cs = ClientSession.getCurrent();
        if (cs != null){
            smsInfo.setInternalClientId(cs.getId());
        }
    }

    /**
     * 扩展smsInfo字段不够的情况
     * 消息推送改造使用aliyunONS消息队列
     * 将需要发送的业务smsInfo丢进ONS队列
     */
    @RpcService
    public void pushMsgData2OnsExtendValue(final SmsInfo smsInfo) {
        SmsInfoDAO smsInfoDAO = DAOFactory.getDAO(SmsInfoDAO.class);
        if (!OnsConfig.onsSwitch) {
            logger.info("OnsSwitch is set off, ons is out of service.");
            return;
        }
        validatePushParam(smsInfo.getBusId(), smsInfo.getOrganId(), smsInfo.getBusType());

        smsInfo.setCreateTime(new Date());
        smsInfo.setStatus(0);
        setInternalClientIdIfHave(smsInfo);
        smsInfoDAO.save(smsInfo);
        logger.info("smsInfo.save成功:SmsInfo:[{}]," + JSONUtils.toString(smsInfo));

        //smsInfo放入ONS队列
        MQHelper.getMqPublisher().publish(OnsConfig.pushTopic, smsInfo);
    }

    /**
     * 消息推送改造使用aliyunONS消息队列
     * 将需要发送的业务id和业务类型丢进ONS队列
     *
     * @param bizId   业务Id
     * @param organId 机构内码id 非个性化机构消息使用 默认0纳里健康
     * @param BusType 消息业务类型
     * @param SmsType 消息类型
     */
    @RpcService
    public void pushMsgData2Ons(final Integer bizId, final Integer organId,
                                final String BusType, final String SmsType,
                                final Integer clientId) {
        SmsInfoDAO smsInfoDAO = DAOFactory.getDAO(SmsInfoDAO.class);
        if (!OnsConfig.onsSwitch) {
            logger.info("OnsSwitch is set off, ons is out of service.");
            return;
        }
        validatePushParam(bizId, organId, BusType);

        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(bizId);
        smsInfo.setBusType(BusType);
        smsInfo.setSmsType(SmsType);
        smsInfo.setOrganId(organId);
        smsInfo.setClientId(clientId);
        smsInfo.setExtendValue(null);
        smsInfo.setCreateTime(new Date());
        smsInfo.setStatus(0);
        setInternalClientIdIfHave(smsInfo);
        smsInfoDAO.save(smsInfo);
        logger.info("smsInfo.save成功:SmsInfo:[{}]," + JSONUtils.toString(smsInfo));
        //smsInfo放入ONS队列
        MQHelper.getMqPublisher().publish(OnsConfig.pushTopic, smsInfo);
    }

    public static void validatePushParam(final Integer bizId, final Integer organId,
                                         final String BusType) {
        if (ObjectUtils.isEmpty(bizId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "smsInfo bizId is null");
        }
        if (ObjectUtils.isEmpty(organId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "smsInfo organId is null");
        }
        if (ObjectUtils.isEmpty(BusType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "smsInfo BusType is null");
        }
    }


}
