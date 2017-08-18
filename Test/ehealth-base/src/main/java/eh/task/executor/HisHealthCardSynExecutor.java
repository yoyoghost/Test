package eh.task.executor;

import ctd.util.AppContextHolder;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.his.hisCommonModule.Patient_HIS;
import eh.entity.mpi.HealthCard;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.RpcServiceInfoUtil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Administrator on 2017-7-28.
 */
public class HisHealthCardSynExecutor implements ActionExecutor {

    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(2));
    private HisServiceConfigDAO dao= AppContextHolder.getBean("eh.serviceConfig",HisServiceConfigDAO.class);
    private List<HealthCard> healthCardList;
    private Integer organID;

    public HisHealthCardSynExecutor(List<HealthCard> healthCardList ,Integer organID){
        this.healthCardList = healthCardList;
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
        RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, serviceName, "sendHealthCards",healthCardList);
    }
}
