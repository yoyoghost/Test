package eh.prmt;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONObject;

import ctd.util.AppContextHolder;
import eh.entity.prmt.PrmtSignupInfoEntity;
import eh.entity.prmt.vo.PrmtQueryVO;
import eh.prmt.service.PrmtSignupInfoService;
/**
 * @author hexy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class PrmtSignupInfoServiceTest {
    @Test
    public void query() {
    	PrmtSignupInfoService service = AppContextHolder.getBean("eh.prmtSignupInfoService",PrmtSignupInfoService.class);
        PrmtQueryVO vo = new PrmtQueryVO();
        vo.setPrmtCode("10101");
        vo.setPrmtStatus(1);
        vo.setDoctorId("95596");
    	List<HashMap<String, Object>> result = service.getPrmtSignupInfoByQueryVO(vo, 0);
    	System.err.println(JSONObject.toJSONString(result));
    }
    
    @Test
    public void findSignupInfoByQueryVO() {
    	PrmtSignupInfoService service = AppContextHolder.getBean("eh.prmtSignupInfoService",PrmtSignupInfoService.class);
        PrmtQueryVO vo = new PrmtQueryVO();
        vo.setPrmtCode("10101");
        vo.setPrmtStatus(1);
        vo.setDoctorId("95596");
        PrmtSignupInfoEntity entity = service.findSignupInfoByQueryVO(vo);
        System.err.println(JSONObject.toJSONString(entity));
    }
    
    @Test
    public void save() {
    	PrmtSignupInfoEntity entity = new PrmtSignupInfoEntity();
    	entity.setActiveFlag(true);
    	entity.setMpiId("2c90818256cf275c0156eebc92510001");
    	entity.setPrmtCode("10101");
    	entity.setPrmtName("签约满10次奖励一个IPHONE 7 PLUS");
    	entity.setRewardStatus(0);
    	entity.setPrmtStatus(1);
    	entity.setSignupTime(new Date());
    	entity.setSignupUserId("2c90818256cf275c0156eebc92510001");
    	entity.setSignupUserName("王五");
    	PrmtSignupInfoService service = AppContextHolder.getBean("eh.prmtSignupInfoService",PrmtSignupInfoService.class);
    	System.err.println(JSONObject.toJSONString(service.savePrmtSignupInfo(entity)));
    }
    
    @Test
    public void update() {
    	PrmtSignupInfoService service = AppContextHolder.getBean("eh.prmtSignupInfoService",PrmtSignupInfoService.class);
        PrmtQueryVO vo = new PrmtQueryVO();
        vo.setPrmtCode("10101");
        vo.setPrmtStatus(1);
        vo.setDoctorId("95596");
        PrmtSignupInfoEntity entity = service.findSignupInfoByQueryVO(vo);
        System.err.println(JSONObject.toJSONString(entity));
        entity.setPrmtStatus(2);
    	PrmtSignupInfoEntity updatePrmtSignupInfo = service.updatePrmtSignupInfo(entity);
    	System.err.println(JSONObject.toJSONString(updatePrmtSignupInfo));
    }
}
