package eh.task.executor;

import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import eh.cdr.dao.ClinicListDAO;
import eh.entity.his.CliniclistRequest;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导入医院的就诊记录请求
 * @author Qichengjian
 *
 */
public class LoadClinicListSendExecutor  implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(ClinicListDAO.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newSingleThreadExecutor());
    
    /** 业务参数 */
    private CliniclistRequest request;
    
	public LoadClinicListSendExecutor(CliniclistRequest request) {
		super();
		this.request=request;
	}
	private int tryCount=0;
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				  sendToHis();
			}
		});
	}
 
	public void sendToHis() {
		String HisServiceId = "h1.clinicListInHosService";
		IHisServiceInterface loadClinicListService = AppContextHolder.getBean(
				HisServiceId, IHisServiceInterface.class);
		logger.info("load his clinicList of" + request.getPatientName() + " to platform ");
		try {
			if(tryCount<=5){
				tryCount++;
			loadClinicListService.loadClinicRecordRequest(request);
			}
		} catch (TransportException e) {
			logger.error("TransportException导入就诊记录错误：发起重试" + e.getMessage());
			sendToHis();
		} catch (Exception e) {
			logger.error("Exception导入就诊记录错误：发起重试" + e.getMessage());
			sendToHis();

		}
	}
}
