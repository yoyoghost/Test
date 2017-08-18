package eh.task.executor;

import ctd.net.rpc.async.AsyncTask;
import ctd.net.rpc.async.AsyncTaskRegistry;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.JSONUtils;
import eh.entity.msg.SmsInfo;
import eh.msg.dao.SmsInfoDAO;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.AlidayuSms;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 短信发送
 * @author w
 *
 */
public class AliSmsSendExecutor implements ActionExecutor{
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(10));//Executors.newSingleThreadExecutor();//;//
    private static final Log logger = LogFactory.getLog(AliSmsSendExecutor.class);
    /** 业务参数 */
    private SmsInfo smsInfo;

	public AliSmsSendExecutor(){}

    public AliSmsSendExecutor(SmsInfo smsInfo){
    	this.smsInfo=smsInfo;
    }
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				send();
			}
		});
	}
	/**
	 * 发送短信任务到短信发送执行器
	 */
	private void send(){
		if(!AlidayuSms.isCanSend()){
			return;
		}
		AsyncTaskRegistry reg = AppDomainContext.getBean("sms.asyncTaskRegistry",AsyncTaskRegistry.class);
		SmsInfoDAO dao=DAOFactory.getDAO(SmsInfoDAO.class);
		smsInfo.setCreateTime(new Date());
		dao.save(smsInfo);
		String id=smsInfo.getId()+"_"+smsInfo.getBusType()+"_"+smsInfo.getBusId();
		AsyncTask task = new AsyncTask(id,"smsAsyncTaskExecutor");
		task.setParameters(smsInfo);
		reg.add(task);
		logger.info("send AsyncTask:"+ JSONUtils.toString(task));
	}

	public void destroy(){
		executors.shutdown();
	}

	public SmsInfo getSmsInfo() {
		return smsInfo;
	}

	public void setSmsInfo(SmsInfo smsInfo) {
		this.smsInfo = smsInfo;
	}
}
