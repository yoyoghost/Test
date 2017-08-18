package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.service.RecipeLogService;
import eh.cdr.service.RecipeMsgService;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.AppointSchedule;
import eh.remote.ISchedulingInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by xuqh on 2016/5/10.
 */
public class DoctorScheduSendExceutor implements ActionExecutor {
    private static final Log logger = LogFactory.getLog( DoctorScheduSendExceutor.class );

    /**
     * 线程池
     */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newCachedThreadPool());
    List<AppointSchedule> listAppointSchedule;

    private int tryCount = 0;

    public DoctorScheduSendExceutor(List<AppointSchedule> listAppointSchedule) {
        this.listAppointSchedule = listAppointSchedule;
    }
    @Override
    public void execute() throws DAOException {
        try {
            executors.execute( new Runnable() {
                @Override
                public void run() {
                    sendToHis();
                }
            } );
        } catch (Exception e) {
            logger.error(e);
        }
    }

    public void sendToHis() {
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO( HisServiceConfigDAO.class );
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId( listAppointSchedule.get( 0 ).getOrganId());
        //调用服务id
        String hisServiceId = cfg.getAppDomainId() + ".doctorScheduInfoService";
        logger.info( hisServiceId );
        ISchedulingInterface schedulingInterface = AppContextHolder.getBean( hisServiceId, ISchedulingInterface.class );
        logger.info( "send to his appoint" + JSONUtils.toString( listAppointSchedule ) );
        RecipeDAO recipeDAO = DAOFactory.getDAO( RecipeDAO.class );
        //组装到院支付参数
        if (tryCount <= 5) {
            tryCount++;
            try {
                schedulingInterface.setDoctorSchedu(listAppointSchedule );
                logger.info( JSONUtils.toString( "调用前置机到院取药服务成功!" ) );
            } catch (Exception e) {
                logger.error( "Exception处方到院取药服务错误：发起重试" + e.getMessage() );
                sendToHis();
            }
        } else {
            //失败发送系统消息
            recipeDAO.updateRecipeInfoByRecipeId(listAppointSchedule.get( 0 ).getScheduleId(), RecipeStatusConstant.HIS_FAIL, null );
            //日志记录
            RecipeLogService.saveRecipeLog(listAppointSchedule.get( 0 ).getScheduleId(),RecipeStatusConstant.CHECK_PASS,
                    RecipeStatusConstant.HIS_FAIL,"his写入失败调用前置机到院取药服务超过重试次数");
            RecipeMsgService.batchSendMsg( listAppointSchedule.get( 0 ).getScheduleId(), RecipeStatusConstant.HIS_FAIL );
            logger.error( "调用前置机到院取药通过服务超过重试次数!" );
        }
    }
}
