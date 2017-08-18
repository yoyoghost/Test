package eh.remote;

import ctd.util.annotation.RpcService;

import java.util.HashMap;

public interface IAlidayuSmsInterface {
	/*
	 * 发送短信
	 */
	@RpcService
	public void sendSmsUpGrade(String mobile,String templateCode,HashMap<String,String> smsParam,Integer smsInfoId);

	@RpcService
	public void sendSmsUpGrade(String signName,String mobile,String templateCode,HashMap<String,String> smsParam,Integer smsInfoId);
	
	@RpcService
	public void reSendSmsByIdOrExtendId(Integer id,String extendId);
}
