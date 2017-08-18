package eh.msg.service;

import ctd.net.rpc.async.AsyncTask;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.msg.SmsInfo;
import eh.msg.dao.SmsInfoDAO;
import org.apache.log4j.Logger;


public class SmsCallbackService {

	public static final Logger log = Logger.getLogger(SmsCallbackService.class);
	
	/**
	 * 短信完成更新状态
	 * @param task
	 * @throws InterruptedException
	 */
	@RpcService
	public void onSmsSended(AsyncTask task) throws InterruptedException{
		Object[] info= task.getParameters();
		SmsInfo smsInfo=(SmsInfo) info[0];
		log.info("sms task callback"+task.getId());
		SmsInfoDAO dao=DAOFactory.getDAO(SmsInfoDAO.class);
		dao.updateStatusById(2, smsInfo.getId());//update the status to success
		
	}
}
