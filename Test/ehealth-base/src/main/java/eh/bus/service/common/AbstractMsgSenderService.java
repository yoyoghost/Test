package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import ctd.util.annotation.RpcService;
import eh.entity.bus.msg.SendSucessCallbackMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Administrator on 2016/8/31 0031.
 */
public abstract class AbstractMsgSenderService<T extends SendSucessCallbackMsg> {
    private static final Logger log = LoggerFactory.getLogger(AbstractMsgSenderService.class);

    protected abstract boolean checkParam(T t);

    public abstract void doSend(T t) throws Exception;

    public abstract void afterSend(T t);

    @RpcService
    public void send(T t) throws Exception{
        if(checkParam(t)){
            try {
                doSend(t);
                log.info("[{}] doSend success with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(t));
            } catch (Exception e){
                log.info("[{}] doSend exception with param[{}], errorMessage[{}], errorStackTrace[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(t), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                throw e;
            }
        }
    }

    @RpcService
    public void callback(T t) {
        if(checkParam(t)){
            afterSend(t);
            log.info("[{}] callback success with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(t));
        }
    }

}
