package eh.task.executor;

import com.ngari.his.appoint.mode.CancelAllTransferRequestTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.Transfer;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
 
/**
 * 转诊申请备案取消
 * @author wnw
 */
public class TransferCancelExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(TransferCancelExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));

	/** 业务参数 */
    private  Transfer transfer;
    public TransferCancelExecutor(Transfer transfer){
    	this.transfer=transfer;
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
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory
				.getDAO(HisServiceConfigDAO.class);
		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(transfer.getRequestOrgan());
		String hisServiceId =cfg.getAppDomainId()+".transferService";//调用服务id
		logger.info("send to his transfer" + JSONUtils.toString(transfer));
		boolean s = DBParamLoaderUtil.getOrganSwich(transfer.getRequestOrgan());

		try{
			if(tryCount<=5){
				tryCount++;
				boolean isSucc = false;
				if(s){
					IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
					CancelAllTransferRequestTO cancelAllTransferRequestTO = new CancelAllTransferRequestTO();
					cancelAllTransferRequestTO.setOrganId(transfer.getRequestOrgan());
					cancelAllTransferRequestTO.setTransferId(transfer.getTransferId());
					appointService.cancelAllTransferResult(cancelAllTransferRequestTO);
				}else
					isSucc = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId,"cancelMedResult",transfer.getTransferId());
				if(isSucc){
					 logger.info(" cancel medRecord success:" + transfer.getTransferId());
					 TransferDAO dao=DAOFactory.getDAO(TransferDAO.class);
					 dao.updateInsuRecordById(0,transfer.getTransferId());
			        }
				logger.info("调用前置机转诊备案取消服务成功！");
			}
			else{
				logger.error("调用转诊取消服务超过重试次数！");
			}
		}
		catch(TransportException e){
			logger.error("TransportException转诊错误：发起重试"+e.getMessage());
			sendToHis();
		}
		catch (Exception e) {
			logger.error("Exception转诊取消错误：发起重试"+e.getMessage());
			sendToHis();
			
		}
	}

}
