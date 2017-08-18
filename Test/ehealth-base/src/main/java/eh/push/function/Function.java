package eh.push.function;


import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;

/**
 * 服务处理接口
 * @author zxq
 *
 */
public interface Function {

	public PushResponseModel perform(PushRequestModel task);

}
