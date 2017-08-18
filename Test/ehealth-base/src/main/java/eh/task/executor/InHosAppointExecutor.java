package eh.task.executor;

import com.ngari.his.appoint.mode.AppointInHosRequestTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.AppointInHosRequest;
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
 * 住院转诊确认
 * @author w
 *
 */
public class InHosAppointExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(AppointRecordDAO.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));

    /** 业务参数 */
    private  AppointInHosRequest request;
    public InHosAppointExecutor(AppointInHosRequest request){
    	this.request=request;
    }
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
		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(Integer.parseInt(request.getOrganID()));
		String HisServiceId =  cfg.getAppDomainId()+ ".appointInHosService";
		boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(request.getOrganID()));
		if(s){
			IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
			AppointInHosRequestTO appointInHosRequestTO = new AppointInHosRequestTO();
			BeanUtils.copy(request,appointInHosRequestTO);
			appointService.registInHosAppoint(appointInHosRequestTO);
		}else
			RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,HisServiceId,"registInHosAppoint",request);
		logger.info("send to his appoint" + JSONUtils.toString(request));
		
	}

}
