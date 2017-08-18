package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.his.hisCommonModule.Patient_HIS;
import eh.his.service.HisPatientService;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.RpcServiceInfoUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by zxq on 2017-7-28.
 */
public class HisPatientSynExecutor implements ActionExecutor {
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));
    private Patient_HIS patient;
    private Integer organID;
    private HisServiceConfigDAO dao= AppContextHolder.getBean("eh.serviceConfig",HisServiceConfigDAO.class);


    public HisPatientSynExecutor(Patient_HIS patient,Integer organID ){
        this.patient = patient;
        this.organID = organID;
    }

    @Override
    public void execute() throws Exception {
        executors.execute(new Runnable() {
            @Override
            public void run() {
                send();
            }
        });
    }
    private void send(){
        String serviceName = dao.getByOrganId(organID).getAppDomainId()+".synService";
        RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, serviceName, "sendPatient",patient);
    }
}
