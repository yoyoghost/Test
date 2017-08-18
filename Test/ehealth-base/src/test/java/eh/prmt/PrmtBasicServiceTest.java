package eh.prmt;

import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONObject;

import ctd.util.AppContextHolder;
import eh.entity.prmt.PrmtBasicEntity;
import eh.entity.prmt.vo.PrmtQueryVO;
import eh.prmt.service.PrmtBasicService;
/**
 * @author hexy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class PrmtBasicServiceTest {
    @Test
    public void findByQueryPages() {
    	PrmtBasicService service = AppContextHolder.getBean("eh.prmtBasicService",PrmtBasicService.class);
    	PrmtQueryVO queryVO = new PrmtQueryVO();
    	queryVO.setPrmtCode("10101");
    	queryVO.setPrmtStatus(1);
    	List<HashMap<String, Object>> result = service.findByQueryPages(queryVO, 0);
    	System.err.println(JSONObject.toJSONString(result));
    }
    
    @Test
    public void findByQuery() {
    	PrmtBasicService service = AppContextHolder.getBean("eh.prmtBasicService",PrmtBasicService.class);
    	PrmtQueryVO queryVO = new PrmtQueryVO();
    	queryVO.setPrmtCode("10101");
    	queryVO.setPrmtStatus(1);
    	PrmtBasicEntity entity = service.findByQuery(queryVO);
    	System.err.println(JSONObject.toJSONString(entity));
    }
    

}
