package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.entity.mpi.SignPatient;
import eh.mpi.dao.RelationDoctorDAO;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 保存签约病人
 * @author hyj
 *
 */
public class SaveHisSignPatientExecutor implements ActionExecutor{
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(2));

    /** 业务参数 */
    private  List<SignPatient> signPatients;
    public SaveHisSignPatientExecutor(List<SignPatient> signPatients){
    	this.signPatients=signPatients;
    }
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				saveSignPatients();
			}
		});
	}
	
	private void saveSignPatients() throws DAOException{
		RelationDoctorDAO dao=DAOFactory.getDAO(RelationDoctorDAO.class);
		for(SignPatient signPatient:signPatients){
			dao.saveSignPatient(signPatient);
		}
	}

}
