package eh.task.executor;

import ctd.persistence.exception.DAOException;
import eh.entity.msg.SmsContent;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.SendTemplateSMS;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 短信发送
 * @author w
 *
 */
public class SmsSendExecutor implements ActionExecutor{
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(5));//Executors.newSingleThreadExecutor();//;//
    private static final Log logger = LogFactory.getLog(SmsSendExecutor.class);
    /** 业务参数 */
    private  SmsContent smsContent;
    public SmsSendExecutor(SmsContent smsContent){
    	this.smsContent=smsContent;
    }
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				sendMsg();
			}
		});
	}
	private void sendMsg(){
		if(smsContent.getType()==SmsContent.DOCTOR){
			SendTemplateSMS.sendMesToDoctor(smsContent);
		}
		else if(smsContent.getType()==SmsContent.PATIENT){
			SendTemplateSMS.sendMesToPatient(smsContent);
		}else{
			logger.error("no message for sending!!");
		}
	}

}
