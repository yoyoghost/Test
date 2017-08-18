package eh.util;

import com.ngari.his.appoint.mode.HisDoctorParamTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.HisDoctorParam;
import eh.redis.RedisClient;
import eh.remote.IHisServiceInterface;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步实时号源工具类  OVERTIME 内不更新
 * Created by hwg on 2016/12/19.
 */
public class RpcAsynchronousUtil implements ActionExecutor {
    //超时时间 5分钟
    private final long OVERTIME= 300;//秒
    private final String REDISKEY= "HIS.SOURCEREAL";//REDIS业务名
    private final String DIFF= ".";
    private static final Logger logger = Logger.getLogger(RpcAsynchronousUtil.class);
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(10));
    private HisDoctorParam doctorParam;
    private Integer organId;
    private RedisClient redisClient = RedisClient.instance();

    private IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);

    public RpcAsynchronousUtil(HisDoctorParam doctorParam, Integer organId){
        this.doctorParam = doctorParam;
        this.organId = organId;
    }

    private String getKey(HisDoctorParam doctorParam) {
        return StringUtils.join(REDISKEY,DIFF,doctorParam.getOrganID(),DIFF,doctorParam.getJobNum());
    }

    public void obtainNowSource(){
        logger.info("start collection Source"+getKey(doctorParam));
        boolean isNeed = isNeedCollect();
        try {
            if(isNeed)
                execute();
        } catch (Exception e) {
            logger.error("getDoctorScheduling error", e);
        }
    }

    private boolean isNeedCollect(){
        try {
            String key = getKey(doctorParam);
            boolean isExist = redisClient.exists(key);
            if(!isExist){
                logger.info(StringUtils.join(key+"没有缓存，需要进行收集"));
                redisClient.setEX(key,OVERTIME,(byte)1);
                return true;
            }
            logger.info(StringUtils.join(key+"有缓存，不需要进行收集"));
        } catch (Exception e) {
//            logger.error("getDoctorScheduling error", e);
            return true;
        }
        return false;
    }

    @Override
    public void execute() throws Exception {
        executors.execute(new Runnable() {
            @Override
            public void run() {


                try {
                    logger.info("开始获取实时号源doctor："+doctorParam.getDoctorId()+",organ:"+organId);
                    boolean s = DBParamLoaderUtil.getOrganSwich(organId);
                    HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    HisServiceConfig config = hisServiceConfigDao.getByOrganId(organId);
                    String hisServiceId = config.getAppDomainId() + ".appointGetService";
                    if(s){
                    	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                        HisDoctorParamTO hisDoctorParamTO = new HisDoctorParamTO();
                        BeanUtils.copy(doctorParam,hisDoctorParamTO);
                        appointService.getDoctorScheduling(hisDoctorParamTO);
                    }else
                        RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getDoctorScheduling", doctorParam);
                } catch (Exception e) {
                    logger.error("his thread exception: " +e);
                }
            }
        });
    }


    public Integer getOrganId() {
        return organId;
    }

    public void setOrganId(Integer organId) {
        this.organId = organId;
    }


    public HisDoctorParam getDoctorParam() {
        return doctorParam;
    }

    public void setDoctorParam(HisDoctorParam doctorParam) {
        this.doctorParam = doctorParam;
    }

}
