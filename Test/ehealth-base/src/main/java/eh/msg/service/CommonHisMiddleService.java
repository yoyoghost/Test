package eh.msg.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.patient.mode.CustomMessageTO;
import com.ngari.his.patient.mode.PushRequestModelTO;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.msg.ShaoYiFuCustomerMsg;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.message.CustomMessage;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by Administrator on 2017/3/22 0022.
 */
public class CommonHisMiddleService {
    private static final Logger log = LoggerFactory.getLogger(CommonHisMiddleService.class);

    @RpcService
    public Map<String, Object> shaoYiFuMessagePush(ShaoYiFuCustomerMsg customerMsg){
        try {
            HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisDao.getByOrganId(customerMsg.getOrganId());
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".customMessageService";
            log.info("shaoYiFuMessagePush hisServiceId[{}]", hisServiceId);
            PushRequestModel<CustomMessage> requestModel = new PushRequestModel<>();
            CustomMessage requestCustomerMessage = new CustomMessage();
            requestCustomerMessage.setTitle(customerMsg.getTitle());
            requestCustomerMessage.setMessage(customerMsg.getMessage());
            requestCustomerMessage.setPatientId(customerMsg.getPatientId());
            requestCustomerMessage.setHealthCardNo(customerMsg.getHealthCardNo());
            requestModel.setOrganId(String.valueOf(customerMsg.getOrganId()));
            requestModel.setData(requestCustomerMessage);
            //HisResponse hisResponse = (HisResponse)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,
            //        hisServiceId, "sendCustomMessage", requestModel);
            HisResponse hisResponse = new HisResponse();
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(requestModel.getOrganId()))){
            	IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
            	HisResponseTO responseTO = new HisResponseTO();
        		PushRequestModelTO<CustomMessageTO> reqTO= new PushRequestModelTO<CustomMessageTO>();
        		BeanUtils.copy(requestModel,reqTO);
        		responseTO = iPatientHisService.sendCustomMessage(reqTO);
        		BeanUtils.copy(responseTO, hisResponse);
        	}else{
        		hisResponse = (HisResponse)RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId, "sendCustomMessage", requestModel);
        	}
            log.info("shaoYiFuMessagePush hisResponse[{}]", JSONObject.toJSONString(hisResponse));
            Map<String, Object> resultMap = Maps.newHashMap();
            if("200".equals(hisResponse.getMsgCode())) {
                resultMap.put("code", "0");
            }else {
                resultMap.put("code", hisResponse.getMsgCode());
            }
            resultMap.put("msg", hisResponse.getMsg());
            return resultMap;
        }catch (Exception e){
            log.error("shaoYiFuMessagePush error, customerMsg[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(customerMsg), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }
}
