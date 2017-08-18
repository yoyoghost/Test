package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.cdr.dao.ClinicListDAO;
import eh.entity.cdr.ClinicList;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 保存医院就诊记录到平台
 * @author Qichengjian
 *
 */

public class SaveClinicRecord implements ActionExecutor{
	private static final Log logger = LogFactory.getLog(ClinicListDAO.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(2));
    
    /**业务参数*/
    private List<ClinicList> clinicLists;
	public SaveClinicRecord(List<ClinicList> clinicLists) {
		this.clinicLists = clinicLists;
	}
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
			save();
			}

			private void save() {
				ClinicListDAO dao = DAOFactory.getDAO(ClinicListDAO.class);
				for(ClinicList clinicList :clinicLists){
					dao.save(clinicList);
				}
				logger.info("病人就诊记录导入成功："+clinicLists.size());
			}
		});
	}

}
