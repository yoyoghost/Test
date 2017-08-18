package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.TempTableDAO;
import eh.entity.bus.AppointSource;
import eh.entity.his.TempTable;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 住院转诊确认
 * @author w
 *
 */
public class SaveHisAppointRecordExecutor implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(SaveHisAppointRecordExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(5));

    /** 业务参数 */
    private List<TempTable> tmpList;
    public SaveHisAppointRecordExecutor(List<TempTable> tmpList){
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
		AppointSourceDAO sdao=DAOFactory.getDAO(AppointSourceDAO.class);
//		TempTableDAO dao=DAOFactory.getDAO(TempTableDAO.class);
		for(TempTable t:tmpList){
			t.setCreateTime(new Date());
//			不保存
//			dao.save(t);
			logger.info("his Record"+t.getOrganSourceId());
			AppointSource old=sdao.getAppointSourceNew(t.getOrganId(), t.getOrganSchedulingId(), t.getOrganSourceId());
			if(old!=null){
				//更新成已用
				sdao.updateUsedNum(1, old.getOrderNum(), old.getAppointSourceId());
				//更新医生是否有号源标志
//				sdao.totalByDoctorDate(old.getDoctorId(), old.getSourceType());
				
			}
			
		}
		logger.info("保存预约记录成功："+tmpList.size());
		
	}

}
