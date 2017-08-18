package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.constant.ServiceType;
import eh.base.dao.DoctorDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganConfigDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.OrganConfig;
import eh.entity.bus.AppointSource;
import eh.task.ExecutorRegister;
import eh.utils.DateConversion;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 住院转诊确认
 * @author w
 *
 */
public class SaveHisAppointSourceExecutor {
	private static final Log logger = LogFactory.getLog(SaveHisAppointSourceExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(20));

//    /** 业务参数 */
//    private  List<AppointSource> sourceList;
//    public SaveHisAppointSourceExecutor(List<AppointSource> sourceList){
//    	this.sourceList=sourceList;
//    }
//	@Override
	public void execute(final List<AppointSource> sourceList) throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				saveAppointSources( sourceList);
			}
		});
	}
	
	private void saveAppointSources(List<AppointSource> sourceList) throws DAOException{
		AppointSourceDAO dao=DAOFactory.getDAO(AppointSourceDAO.class);
		// 不要打印这种日志 ！  logger.info("开始号源导入--------"+JSONUtils.toString(sourceList));
		Integer organid = -1;
		String departcode = null;
		Integer doctorid = -1;
		HashMap<String, AppointSource> map = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		if(sourceList!=null&&sourceList.size()>0){

			organid = sourceList.get(0).getOrganId();

			departcode = sourceList.get(0).getAppointDepartCode();
			doctorid = sourceList.get(0).getDoctorId();
			HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);

			OrganConfig organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(organid);
			if(organConfig==null || organConfig.getSourceFlag().intValue()==0){
				logger.info("医院不支持his号源导入！"+organid);
				return ;
			}

			boolean f = configDAO.isServiceEnable(organid, ServiceType.SOURCEREAL);
			if(f){
				//查询所有号源日期
				HashSet<Date> workdateSet = new HashSet<Date>();
				for(AppointSource source:sourceList){
					Date w = DateConversion.getFormatDate(source.getWorkDate(), DateConversion.YYYY_MM_DD);
					source.setWorkDate(w);
					workdateSet.add(source.getWorkDate());
				}

				List<AppointSource> partlist = new ArrayList<AppointSource>();
				//根据以上获取的号源日期，查询该医生在这些日期内的已有的所有正常状态的号源
				for(Date workdate:workdateSet){
					//partlist = dao.findByOrganAndDoctorAndWorkdate(organid,departcode,doctorid,workdate);
					partlist = dao.findBySchedulingAndOrganAndDoctorAndWorkdate(organid,departcode,doctorid,workdate,sourceList.get(0).getOrganSchedulingId());
				}
				//将alllist元素放入map，且元素appointSourceId为其key
				map = new HashMap<String, AppointSource>();
				for(int i=0;i<partlist.size();i++){
					map.put(sdf.format(partlist.get(i).getWorkDate())+"|"+partlist.get(i).getOriginalSourceId(), partlist.get(i));
				}
			}
		}else{
			return ;
		}
		
		String workDateAndOriginalId = null;
		//保存同个排班的 号源数据
		logger.info("his传过来的数据大小======》》》》" + sourceList.size() + "organId======》》》" + sourceList.get(0).getOrganId());
		for(AppointSource source:sourceList){
			try{
				dao.saveAppointSource(source);
			}catch (Exception e){
				logger.error("save failed:" + JSONUtils.toString(source));
				continue;
			}
			if(map!=null){
				//排除需要新增及更新的号源
				workDateAndOriginalId = sdf.format(source.getWorkDate())+"|"+source.getOriginalSourceId();
				map.remove(workDateAndOriginalId);
			}
		}
		if(map != null && map.size()>0){
			//将map中剩余号源设置为停诊
			Iterator<AppointSource> iterator = map.values().iterator();
			while(iterator.hasNext()){
				//如果是当天的不设置
				AppointSource s = iterator.next();
				Date today = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
				if (s.getWorkDate().compareTo(today) != 0) {
					dao.updateStopFlagById(s.getAppointSourceId());
				}
			}
		}
		//将医生更新成有号源标志
		if(sourceList!=null&&sourceList.size()>0){
			DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
			doctorDAO.updateHaveAppointByDoctorId(sourceList.get(0).getDoctorId(), 1);
		}else{
			DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
			doctorDAO.updateHaveAppointByDoctorId(sourceList.get(0).getDoctorId(), 0);
		}
	}
}
