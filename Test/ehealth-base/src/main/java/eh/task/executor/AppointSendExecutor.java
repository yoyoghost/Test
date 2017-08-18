package eh.task.executor;

import com.ngari.his.appoint.mode.AppointmentRequestHisTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
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
 * 预约（转诊）注册
 * @author w
 *
 */
public class AppointSendExecutor implements ActionExecutor{

	private static final Log logger = LogFactory.getLog(AppointSendExecutor.class);
//	/** 线程池 */
	private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(10));
	/** 业务参数 */
	private  AppointmentRequest request;
	public AppointSendExecutor(AppointmentRequest request){
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
		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(request.getTargetOrganId());
		String hisServiceId =cfg.getAppDomainId()+".appointmentService";//调用服务id
		logger.info("开始调用前置机服务：" + JSONUtils.toString(request));
		boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(request.getOrganID()));
		try{
			if(tryCount<=5){
				tryCount++;
				if(s){
					IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
					AppointmentRequestHisTO to = new AppointmentRequestHisTO();
					BeanUtils.copy(request,to);
					appointService.registAppoint(to);
				}else{
					RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId,"registAppoint",request);

				}
				logger.info("调用前置机预约服务成功！");
			}
			else{
				// 将号源释放
//				updateSource(request);
				logger.error("调用预约服务超过重试次数！");
			}
		}
		catch(TransportException e){
			logger.error("TransportException预约错误：发起重试"+tryCount+e.getMessage());
			sendToHis();
		}
		catch (Exception e) {
			logger.error("Exception预约错误：发起重试"+tryCount+e.getMessage());
			sendToHis();

		}
	}


}
