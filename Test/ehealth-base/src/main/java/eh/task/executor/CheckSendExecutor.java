package eh.task.executor;

import com.ngari.his.appoint.mode.AppointmentRequestHisTO;
import com.ngari.his.check.service.ICheckHisService;
import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.AppointmentRequest;
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
 * 预约检查 
 * @author zxq
 *
 */
public class CheckSendExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(CheckSendExecutor.class);
	/** 线程池 */
	private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));

	/** 业务参数 */
	private  AppointmentRequest request;
	public CheckSendExecutor(AppointmentRequest request){
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
		HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(Integer.parseInt(request.getOrganID()));
		String hisServiceId =cfg.getAppDomainId()+".checkService";//调用服务id
//		IHisServiceInterface appointService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);
		logger.info("send to his check参数：" + JSONUtils.toString(request));
		try{
			if(tryCount<=5){
				tryCount++;
				//RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "registCheckrequest", request);
				if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){
					ICheckHisService iCheckHisService = AppDomainContext.getBean("his.iCheckHisService", ICheckHisService.class);
					AppointmentRequestHisTO reqTO= new AppointmentRequestHisTO();
	        		BeanUtils.copy(request,reqTO);
	        		iCheckHisService.registCheckrequest(reqTO);
	        	}else{
	        		RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "registCheckrequest", request);
	        	}
				logger.info("调用前置机预约服务成功！");
			}
			else{
				logger.error("调用预约服务超过重试次数！");
			}
		}
		catch(TransportException e){
			logger.error("TransportException预约检查错误：发起重试"+e.getMessage());
			sendToHis();
		}
		catch (Exception e) {
			logger.error("Exception预约检查错误：发起重试"+e.getMessage());
			sendToHis();

		}
	}

}
