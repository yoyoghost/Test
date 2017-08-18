package eh.task.executor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointSource;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by hwg on 2017/3/2.
 */
public class SaveHisAppointDepartExecutor implements ActionExecutor {

    private static final Log logger = LogFactory.getLog(SaveHisAppointDepartExecutor.class);
    /** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(2));



    /** 业务参数 */
    private AppointSource appointSource;
    public SaveHisAppointDepartExecutor(AppointSource appointSource){
        this.appointSource=appointSource;
    }
    @Override
    public void execute() throws Exception {
        executors.execute(new Runnable() {
            @Override
            public void run() {
                saveAppointDepart();
            }
        });
    }

    @RpcService
    public void saveAppointDepart(){
        String key = appointSource.getOrganId()+"_"+appointSource.getAppointDepartCode();
        try {
            AppointDepart departKey = appointDeparts.get(key);
        } catch (ExecutionException e) {
//            e.printStackTrace();

        }
    }

    private LoadingCache<String, AppointDepart> appointDeparts = CacheBuilder.newBuilder().build(new CacheLoader<String, AppointDepart>(){

        @Override
        public AppointDepart load(String appointDepartCode) throws Exception {
            AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
            String organ = appointDepartCode.split("_")[0];
            String code = appointDepartCode.split("_")[1];
            AppointDepart ad = appointDepartDAO.getAppointDepartByOrganIDAndAppointDepartCode(Integer.parseInt(organ),code);
            if (null == ad){
                AppointDepart appointdepart = new AppointDepart();
                appointdepart.setOrganId(appointSource.getOrganId());
                appointdepart.setAppointDepartCode(appointSource.getAppointDepartCode());
                appointdepart.setAppointDepartName(appointSource.getAppointDepartName());
                appointdepart.setProfessionCode("98");// 默认其他科室
                appointdepart.setCancleFlag(0);
                appointdepart.setCreateTime(new Date());
                try {
                    appointDepartDAO.saveAppointDepart(appointdepart);
                    appointDeparts.put(appointDepartCode,appointdepart);
                    appointDepartDAO.createCommonDoctorByAppointDepart(appointdepart);
                } catch (Exception e) {
                    logger.error("保存saveAppointDepart异常====》》》" + JSONUtils.toString(appointdepart));
                    logger.error(e);
                }
            }else {
                appointDeparts.put(appointDepartCode,ad);
                }
            return  ad;
        }
    });
}
