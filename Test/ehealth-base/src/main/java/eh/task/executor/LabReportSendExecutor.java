package eh.task.executor;

import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.cdr.dao.LabReportDAO;
import eh.entity.mpi.Patient;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 预约（转诊）注册
 * @author w
 *
 */
public class LabReportSendExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(LabReportDAO.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newSingleThreadExecutor());

    /** 业务参数 */
    private  Patient patient;
    private  Integer organ;
    public LabReportSendExecutor(Patient patient,Integer organ){
    	this.patient=patient;
    	this.organ=organ;
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
	public void sendToHis(){
		String HisServiceId = "h" + organ+ ".labReportService";
//		IHisServiceInterface labReportService = AppContextHolder.getBean(
//				HisServiceId, IHisServiceInterface.class);
		logger.info("send to his" + JSONUtils.toString(patient));
		try{
			if(tryCount<=5){
				tryCount++;
				// 该定时器没有被使用
				RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,HisServiceId,"getLableReports",patient,organ);
//				labReportService.queryLabReport(patient,organ);
				logger.info("调用前置机采集检验报告成功！");
			}
			else{
				logger.error("调用采集检验报告服务超过重试次数！");
			}
		}
		catch(TransportException e){
			logger.error("TransportException采集错误：发起重试"+e.getMessage());
			sendToHis();
		}
		catch (Exception e) {
			logger.error("Exception采集错误：发起重试"+e.getMessage());
			sendToHis();
			
		}
	}

}
