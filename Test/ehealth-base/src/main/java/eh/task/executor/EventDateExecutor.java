package eh.task.executor;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.appoint.mode.EventDataRequestTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.EventDataDAO;
import eh.entity.base.HisServiceConfig;
import eh.remote.IHisServiceInterface;
import eh.task.ExecutorRegister;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class EventDateExecutor {
	private static final Log logger = LogFactory.getLog(EventDateExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newFixedThreadPool(8));

//    /** 业务参数 */
//    private  List<HashMap<String, Object>> tmpList;
//    private String messageId;
//    private String queryDate;
//    public EventDateExecutor(List<HashMap<String, Object>> tmpList,String messageId,String queryDate){
//    	this.messageId=messageId;
//    	this.tmpList=tmpList;
//    	this.queryDate=queryDate;
//    }
//	@Override
	public void execute(final List<HashMap<String, Object>> tmpList,final String messageId,final String queryDate) throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				saveEventDatas( tmpList, messageId, queryDate);
			}
		});
	}
	
	@SuppressWarnings("unchecked")
	public void saveEventDatas(final List<HashMap<String, Object>> tmpList,final String messageId,final String queryDate) throws DAOException{
		
		logger.info("事件获取结果："+JSONUtils.toString(tmpList));
		final List<HashMap<String, Object>> schedulingList=new ArrayList<HashMap<String, Object>>();
		final List<HashMap<String, Object>> sourceList=new ArrayList<HashMap<String, Object>>();

		EventDataDAO eventDataDAO=DAOFactory.getDAO(EventDataDAO.class);
		HashMap<String, Object> Map=null;
		Map=(HashMap<String, Object>) tmpList.get(0).get("content");
		for (final HashMap<String, Object> map : tmpList) {

			//根据事件类型处理
			HashMap<String, Object> contentMap=null;
			contentMap=(HashMap<String, Object>) map.get("content");

			//排班删除事件
			if(map.get("eventType").equals("SCHEDULINGDELETE")){
				eventDataDAO.schedulingDelete(contentMap);
			}
			//号源删除事件
			if(map.get("eventType").equals("SOURCEDELETE")){
				eventDataDAO.sourceDelete(contentMap);
			}
			//排班开诊事件
			if(map.get("eventType").equals("SCHEDULINGOPEN")){
				eventDataDAO.schedulingOpenOrStop(contentMap,0);
			}
			//排班停诊事件
			if(map.get("eventType").equals("SCHEDULINGSTOP")){
				eventDataDAO.schedulingOpenOrStop(contentMap,1);
			}
			//排班新增事件
			if(map.get("eventType").equals("SCHEDULINGADD")){
				schedulingList.add(map);
			}
			//号源新增事件
			if(map.get("eventType").equals("SOURCEADD")){
				sourceList.add(map);
			}
			//排班修改事件
			if(map.get("eventType").equals("SCHEDULINGMODIFY")){
				eventDataDAO.schedulingModify(contentMap);
			}

//					//TODO 预约记录
//					<Content>{"EventType":"APPOINTMENT",
//						"AppointRecordInfo":{"CertID":" ","OperateDate":"2015-09-08 13:17:42.723","DepartCode":"20802","DepartName":"颈椎疾病","PatientType":"1","WorkDate":"2015-09-11","PatientName":"林佩英","OrganSourceID":"0015004131000007","OrganSchdulingID":"0015004131_20150911","OrderNum":"7","OrganId":"1000017","Mobile":"13735891715","DoctorID":"*   ","EndTime":"09:00","StartTime":"08:30","WorkType":"1","OrganAppointID":"26538724","CredentialsType":"01"}}</Content>
			if(map.get("eventType").equals("APPOINTMENT")){
				eventDataDAO.appointment(contentMap,"2");
			}

			if(map.get("eventType").equals("CANCELAPPOINTMENT")){
				eventDataDAO.appointment(contentMap,"1");
			}

			EventDataDAO dao=DAOFactory.getDAO(EventDataDAO.class);
			dao.saveEventData(map, messageId, queryDate);

		}
		//将新增的排班和号源更新到号源表
		logger.info("------->schedulingList:"+schedulingList);
		logger.info("------->sourceList:"+sourceList);
		eventDataDAO.schedulingAddOrSourceAdd(schedulingList, sourceList);
//				//检查医生是否有号源
//				AppointSourceDAO appointSourceDAO=DAOFactory.getDAO(AppointSourceDAO.class);
//				appointSourceDAO.checkAndUpateHaveAppoint();

		
		//事件处理完成通知
		HashMap<String, Object> organSchedulingMap=(HashMap<String, Object>) ((HashMap<String, Object>) tmpList.get(0).get("content")).get("OrganScheduling");
		int organId=Integer.parseInt(organSchedulingMap.get("OrganId").toString());
		HisServiceConfig config = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(organId);
		String HisServiceId = config.getAppDomainId()+ ".baseNotifyFinishedService";
		try{
			boolean s = DBParamLoaderUtil.getOrganSwich(organId);
			if(s){
				IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
				EventDataRequestTO eventDataRequestTO = new EventDataRequestTO();
				eventDataRequestTO.setMessageID(messageId);
				eventDataRequestTO.setOrganId(organId);
				HisResponseTO res = appointService.notifyEventHisSuccess(eventDataRequestTO);
				if(!res.isSuccess()){
					logger.error(messageId+"事件处理完成通知失败："+res.getMsg());
				}
			}else
				RpcServiceInfoUtil.getClientService(IHisServiceInterface.class,HisServiceId,"notifyHisSucc",messageId);
		}catch (Exception e) {
			logger.error("事件处理完成通知失败："+e.getMessage());
		}
	}
	
}
