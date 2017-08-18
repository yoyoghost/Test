package eh.bus.service;

import com.ngari.his.appoint.mode.TaskQueueHisTO;
import com.ngari.his.check.service.ICheckHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.thread.EmrImageThreadPool;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfig;
import eh.entity.his.TaskQueue;
import eh.entity.mpi.Patient;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * 获取影像信息
 * Created by hwg on 2016/12/14.
 */
public class ObtainImageInfoService {
    private Patient p;
    private Integer organId;

    public ObtainImageInfoService(){}

   public ObtainImageInfoService(Patient p,Integer organId){
       this.p = p;
       this.organId = organId;
   }

    private static final Log logger = LogFactory.getLog(ObtainImageInfoService.class);

    @RpcService
    public void getImageInfo() {
        new EmrImageThreadPool(new Runnable(){
            @Override
            public void run() {
                try{
                    logger.info("获取病人影像信息: " + JSONUtils.toString(p));
                    OrganConfigDAO configDAO = DAOFactory.getDAO(OrganConfigDAO.class);
                    OrganConfig organConfig = configDAO.getByOrganId(organId);
                    OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                    Organ organ = organDAO.getByOrganId(organId);
                    Integer canImage = organConfig.getCanImage();
                    if (canImage != null && canImage == 1) {
                        TaskQueue q = new TaskQueue();
                        q.setCardorgan(organ.getOrganizeCode());
                        q.setCardtype("1");
                        q.setCertno(p.getRawIdcard());
                        q.setCreatetime(new Date());
                        q.setMpi(p.getMpiId());// 平台主索引
                        q.setPatientid("");// 病历号
                        q.setPatientName(p.getPatientName());
                        q.setPatientType("1");
                        q.setPriority(1);// 默认1
                        q.setStatus(0);// 0初始
                        q.setTrycount(0);//
                        q.setOrganid(organId);
                        q.setTopic("QueryReport");// 主题，获取报告
                        String hisServiceId = "emr.taskQueue";
                        //RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "saveTaskQueue", q);
                        if(DBParamLoaderUtil.getOrganSwich(q.getOrganid())){ 
                        	ICheckHisService iCheckHisService = AppDomainContext.getBean("his.iCheckHisService", ICheckHisService.class);
                    		TaskQueueHisTO reqTO= new TaskQueueHisTO();
                    		BeanUtils.copy(q,reqTO);
                    		iCheckHisService.saveTaskQueue(reqTO);
                    	}else{
                    		RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "emr.taskQueue","saveTaskQueue",q);
                    	}
                    }
                }catch (Exception e){
                    logger.info("his thread exception: " +e.getMessage());
                }
            }
        }).execute();

    }




}
