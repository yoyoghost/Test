package eh.cdr.service;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.service.housekeeper.HouseKeeperService;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.sign.SignCommonBean;
import eh.mpi.service.sign.AcceptSignService;
import eh.mpi.service.sign.RefuseSignService;
import eh.redis.RedisClient;
import eh.remote.IHisServiceInterface;
import eh.tpn.SecretKeyService;
import eh.tpn.SecretKeyTokenService;
import eh.tpn.TPNService;
import eh.util.RpcServiceInfoUtil;
import eh.vb.tpn.VbModelParam;
import eh.vb.tpn.VbModelResult;
/**
 * @author hexy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class TPNServiceTest {

	@Test
	public void get(){
		TPNService service = AppContextHolder.getBean("tPNService", TPNService.class);
		VbModelParam param = new VbModelParam();
		param.setCalorie("40.0");
		param.setFat("6.0");
		param.setWeight("70.0");
		param.setSugar("4.0");
		param.setDepart(1);
		param.setDoctor(9559);
		param.setOrgan(859);
		VbModelResult result = service.calculationTpnScheme(param);
		System.err.println(JSONObject.toJSONString(result));
	}
	@Test
	public void test() {
		RefuseSignService bean = AppContextHolder.getBean("refuseSignService", RefuseSignService.class);
		bean.cancelOverTimeOfPreSign();
	}
	
	@Test
	public void test1() {
		HouseKeeperService bean = AppContextHolder.getBean("houseKeeperService",HouseKeeperService.class);
		List<Map<String, Object>> patientSignDoctors = bean.getPatientSignDoctors("2c9081825b0e0b92015b0e54a9640000");
		System.err.println(JSONObject.toJSONString(patientSignDoctors));
	}
	
	@Test
	public void test2() {
		AcceptSignService bean = AppContextHolder.getBean("acceptSignService", AcceptSignService.class);
		String clickCompSign = bean.clickCompSign(3052, "2c9081825b0e0b92015b0e54a9640000", 2);
		System.err.println(clickCompSign);
	}
	

	
	@Test
	public void test3(){
		 HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
		 HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(1);
		 System.err.println("the cfg obj is "+JSONObject.toJSONString(cfg));
		 String hisServiceId = "h1000999" + ".signService";
		 SignCommonBean signCommonBean = new SignCommonBean();
		 signCommonBean.setPatientID("2c9081895cc969e0015cc99fb2950000");
         signCommonBean.setPatientName("张三");
         signCommonBean.setCertID("330182199105213222");
         signCommonBean.setCardType("2");
         signCommonBean.setCardID("65485");
         signCommonBean.setMobile("13052028517");
         signCommonBean.setDoctor(1180);
         signCommonBean.setDoctorName("邵逸夫");
         signCommonBean.setRecordStatus("4");
         signCommonBean.setOneToMany(true);
         System.err.println("IHisServiceInterface.registSign star param"+JSONObject.toJSONString(signCommonBean));
		 Object resultFlag = null;
		try {
			resultFlag = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "registSign", signCommonBean);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		} finally{
			System.err.println("IHisServiceInterface.registSign end  result:"+JSONObject.toJSONString(resultFlag));
		}
	}
	
	@Test
	public void createSecretKey() {
		SecretKeyService bean = AppContextHolder.getBean("secretKeyService", SecretKeyService.class);
		bean.createSecretKey(100L);
	}
	
	@Test
	public void bindKey() {
		SecretKeyTokenService bean = AppContextHolder.getBean("secretKeyTokenService", SecretKeyTokenService.class);
		boolean bindToken = bean.bindToken("gcklVAKV30", "vEH1cOx51vEH1cOx51");
		System.err.println(JSONUtils.toString(bindToken));
	}
	
	@Test
	public void findByToken() {
		SecretKeyTokenService bean = AppContextHolder.getBean("secretKeyTokenService", SecretKeyTokenService.class);
		boolean result = bean.findByToken("o4522wyGVTtxyJBmoY8i3Krwg7as");
		System.err.println(JSONUtils.toString(result));
	}
	
	@Test
	public void redisTest() {
		RedisClient bean = AppContextHolder.getBean("redisClient", RedisClient.class);
		Long del = bean.del("vEH1cOx51vEH1cOx51");
		System.err.println(JSONUtils.toString(del));
	}

}
