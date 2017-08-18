package eh.follow;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.entity.base.FollowChatStatistics;
import eh.entity.mpi.FollowChat;
import eh.mpi.dao.FollowChatDAO;
import eh.mpi.service.follow.FollowChatService;
import eh.mpi.service.follow.FollowQueryService;
/**
 * @author hexy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class FollowQueryServiceTest {
    @Test
    public void queryFollowModuleLimit() {
    	FollowQueryService service = AppContextHolder.getBean("eh.followQueryService",FollowQueryService.class);
    	List<Map<String, Object>> result = service.findMyModuleList(301, 0, 10);
    	System.err.println(JSONObject.toJSONString(result));
    }
    
    @SuppressWarnings("all")
    @Test
    public void getCountThisMon() {
    	FollowChatDAO followChatDAO = DAOFactory.getDAO(FollowChatDAO.class);
    	FollowChatService service = AppContextHolder.getBean("eh.followChatService",FollowChatService.class);
    	Date startTime = getTime(0);
        Date endTime = getTime(1);
        List<FollowChat> countThisMon = followChatDAO.getCountThisMon(1425, startTime, endTime);
		List<FollowChat> countThisDocSum = followChatDAO.getCountThisDocSum(1425);
		Long countThisDocPatientSum = followChatDAO.getCountThisDocPatientSum(1425);
		System.err.println(JSONObject.toJSONString(countThisMon));
		System.err.println(JSONObject.toJSONString(countThisDocSum));
		System.err.println(JSONObject.toJSONString(countThisDocPatientSum));
    }     
    
    
    @Test
    public void query () {
    	FollowChatService service = AppContextHolder.getBean("eh.followChatService",FollowChatService.class);
    	FollowChatStatistics statisticsFollow = service.statisticsFollow(1425);
    	System.err.println(JSONObject.toJSONString(statisticsFollow));
    }
    public void test(String[] args) {
    	int num1 = 1;

		int num2 = 2;
		NumberFormat numberFormat = NumberFormat.getInstance();
		numberFormat.setMaximumFractionDigits(2);
		String result = numberFormat.format((float) num1 / (float) num2 * 100);
		System.out.println("num1和num2的百分比为:" + result + "%");
	}
    
	private Date getTime(int dayFlag) {
		try {
			  Calendar cale = Calendar.getInstance(); 
			  SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd"); 
			  cale.add(Calendar.MONTH, dayFlag);
			  cale.set(Calendar.DAY_OF_MONTH,1);
			  String sTime = format.format(cale.getTime());  
			  return format.parse(sTime);
		} catch (Exception e) {
		}
		return null;
	}
	
	public static void main(String[] args) {
		List<Long> list =new ArrayList<>();
		list.add(10L);
		list.add(11L);
		list.add(7L);
		list.add(67L);
		Collections.sort(list,Collections.reverseOrder());
		System.err.println(JSONObject.toJSONString(list));
	}
}
