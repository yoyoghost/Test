package eh.push.function;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.DeviceDAO;
import eh.base.dao.OrganDAO;
import eh.entity.base.bean.PacsReportMsgBean;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.utils.DateConversion;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/5/18.
 */
public class PacsJNoticeFunction implements Function{

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(PacsJNoticeFunction.class);

    @Override
    public PushResponseModel perform(PushRequestModel task) {
        PushResponseModel response = new PushResponseModel();
        PacsReportMsgBean msgBean = (PacsReportMsgBean)task.getData();
        logger.info("PacsJNoticeFunction get. param={}", JSONUtils.toString(msgBean));
        if(null == msgBean || StringUtils.isEmpty(msgBean.getIdCard())
                || StringUtils.isEmpty(msgBean.getOrganId()) || StringUtils.isEmpty(msgBean.getAppId())){
            logger.error("PacsJNoticeFunction 参数不全.");
            return response.setError("1","参数不全");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByIdCard(msgBean.getIdCard());
        if(null == patient){
            logger.error("PacsJNoticeFunction 患者未注册. idCard=[{}]", msgBean.getIdCard());
            return response.setError("2","患者未注册");
        }

        if(StringUtils.isEmpty(patient.getLoginId())){
            logger.error("PacsJNoticeFunction 患者没有loginId. mpiId=[{}]", patient.getMpiId());
            return response.setError("3","患者没有loginId");
        }

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);

        Integer organId = Integer.parseInt(msgBean.getOrganId());
        String organName = organDAO.getNameById(organId);
        List<Integer> clientIdList = deviceDAO.findWXByUserIdAndRoleIdAndTokenExt(patient.getLoginId(),"patient","%@"+msgBean.getAppId());
        if(CollectionUtils.isNotEmpty(clientIdList)){
            SmsInfo info = new SmsInfo();
            info.setBusId(1);// 业务表主键
            info.setBusType("PacsMsg");// 业务类型
            info.setSmsType("PacsMsg");//
            info.setClientId(clientIdList.get(0));
            info.setStatus(0);
            info.setOrganId(organId);

            Map<String,String> extInfo = new HashMap<>();
            extInfo.put("first", organName+"取报告单提醒");
            extInfo.put("reportName", msgBean.getStudyItem());
            extInfo.put("reportTime", DateTime.now().toString(DateConversion.DEFAULT_DATE_TIME));
            extInfo.put("reportUrl", msgBean.getReportURL());
            info.setExtendValue(JSONUtils.toString(extInfo));

            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(info);

            response.setSuccess("pacs消息推送成功");
        }else{
            logger.error("PacsJNoticeFunction device没有记录. loginId=[{}], token=[{}]", patient.getLoginId(), msgBean.getAppId());
            response.setError("4","device没有记录");
        }

        return response;
    }
}
