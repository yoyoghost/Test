package eh.task.executor;

import com.ngari.his.appoint.mode.MedRequestHisTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.MedRequest;
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
 * 转诊申请备案
 * @author ZX
 */
public class TransferSendExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(TransferSendExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));

    /** 业务参数 */
    private  MedRequest request;
    public TransferSendExecutor(MedRequest request){
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
	public void sendToHis(){
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory
				.getDAO(HisServiceConfigDAO.class);
		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(request.getRequestOrganId());
		String hisServiceId =cfg.getAppDomainId()+".transferService";//调用服务id
//		IHisServiceInterface transferService = AppContextHolder.getBean(
//				hisServiceId, IHisServiceInterface.class);
		logger.info("send to his transfer" + JSONUtils.toString(request));
		boolean s = DBParamLoaderUtil.getOrganSwich(request.getRequestOrganId());

		try{
			if(tryCount<=5){
				tryCount++;
				if(s){
					IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
					MedRequestHisTO medRequestHisTO = new MedRequestHisTO();
					BeanUtils.copy(request,medRequestHisTO);
					appointService.registTransfer(medRequestHisTO);
				}else
					RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,hisServiceId,"registTransfer", request);
				logger.info("调用医保备案服务成功！");
			}
			else{
				logger.error("调用医保备案超过重试次数！");
			}
		}
		catch(TransportException e){
			logger.error("TransportException转诊错误：发起重试"+e.getMessage());
			sendToHis();
		}
		catch (Exception e) {
			logger.error("Exception转诊错误：发起重试"+e.getMessage());
			sendToHis();
			
		}
	}

}
