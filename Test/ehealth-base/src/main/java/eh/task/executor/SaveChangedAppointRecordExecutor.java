package eh.task.executor;

import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.JSONUtils;
import eh.bus.service.AppointService;
import eh.entity.bus.HisAppointRecord;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 住院转诊确认
 * @author w
 *
 */
public class SaveChangedAppointRecordExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(SaveChangedAppointRecordExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newSingleThreadExecutor());

    /** 业务参数 */
    private List<HisAppointRecord> tmpList;
    public SaveChangedAppointRecordExecutor(List<HisAppointRecord> tmpList){
    	this.tmpList=tmpList;
    }
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				save();
			}
		});
	}
	public void save(){
		
		AppointService service=AppDomainContext.getBean("eh.appointService",AppointService.class);
		for(HisAppointRecord source:tmpList){
			try{
				service.updateSource(source);
			}catch(Exception e)
			{
				logger.error("号源更新异常："+e.getMessage()+"/r/n号源信息："+JSONUtils.toString(source));
			}
		}
		logger.info("保存预约记录成功："+tmpList.size());
		
	}

}
