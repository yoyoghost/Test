package eh.push.function;

import ctd.util.JSONUtils;
import eh.bus.service.seeadoctor.CallNumberService;
import eh.entity.his.push.callNum.HisCallNoticeReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.util.Constant;
import eh.utils.ValidateUtil;
import io.netty.util.internal.StringUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * 文本信息推送服务（his传过来具体文案，平台不做处理按照his给的文案 进行 微信客服推送）
 *
 */
public class CallNoticeFunction implements Function{
    private static final Log logger = LogFactory.getLog(CallNoticeFunction.class);

    @Override
    public PushResponseModel perform(PushRequestModel req) {
        String paramStr = JSONUtils.toString(req);
        logger.info(paramStr);
    	PushResponseModel response = new PushResponseModel();
    	Boolean status = validatePara(req,response);
        if(!status){
            return response;
        }
    	HisCallNoticeReqMsg hisCallNoticeReqMsg = (HisCallNoticeReqMsg)req.getData();
        CallNumberService callNumberService = new CallNumberService();
        String param = null;
        if(null != hisCallNoticeReqMsg.getMsgType() && hisCallNoticeReqMsg.getMsgType().equals(1)){
        	param = Constant.WX_NOTICE_BUSTYPE;
        }else if(null == hisCallNoticeReqMsg.getMsgType() || hisCallNoticeReqMsg.getMsgType().equals(2)){
        	param = Constant.WX_MESSAGE_BUSTYPE;
        }
        callNumberService.sendWxTemplateMessageHis(hisCallNoticeReqMsg,param);
        response.setMsg("发送微信推送执行成功");
        return response;
    }
    
    private Boolean validatePara(PushRequestModel reqMsg,PushResponseModel response) {
    	boolean status = false;
    	HisCallNoticeReqMsg hisCallNoticeReqMsg = (HisCallNoticeReqMsg)reqMsg.getData();
        if(ValidateUtil.blankString(reqMsg.getServiceId())){
        	response.setMsg("serviceId 为空");
        }else if (reqMsg.getData() == null){
        	response.setMsg("发送对象体 HisCallNoticeReqMsg 为空");
        }else if (StringUtil.isNullOrEmpty(hisCallNoticeReqMsg.getMessage())) {
            response.setMsg("推送信息 msg");
        } else if (StringUtil.isNullOrEmpty(hisCallNoticeReqMsg.getIdCard())) {
            response.setMsg("患者idCard为空");
        } else {
        	status = true;
        }
        return status;
    }
    
}
