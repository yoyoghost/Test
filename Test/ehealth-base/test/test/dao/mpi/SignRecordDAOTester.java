package test.dao.mpi;

import ctd.util.JSONUtils;
import eh.entity.his.sign.SignCommonBean;
import eh.entity.mpi.SignRecord;
import eh.entity.msg.Ad;
import eh.mpi.dao.SignRecordDAO;
import eh.msg.dao.AdDAO;
import eh.remote.IHisServiceInterface;
import eh.util.RpcServiceInfoUtil;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

public class SignRecordDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static SignRecordDAO dao = appContext.getBean("signRecordDAO",SignRecordDAO.class);
	
	public void testfindByDoctorAndRequestStatus(){
		List<SignRecord> list=dao.findByDoctorAndRecordStatus(1182,0);
		System.out.println(JSONUtils.toString(list));
	}

	public void testfindByDoctorAndRecordStatusPages(){
		List<SignRecord> list=dao.findByDoctorAndRecordStatusPages(40,0,2);
		System.out.println(JSONUtils.toString(list));
	}


	public void testfindByDoctorAndMpiIdAndRequestStatus(){
		List<SignRecord> list=dao.findByDoctorAndMpiIdAndRecordStatus(1182,"402885f2548efbce01548f4186770001",0);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 测试是否允许签约
	 */
	public void testIsCanSign(){
		SignCommonBean sign = new SignCommonBean();
		Object flag = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "h1.signService", "isCanSign", sign);
		System.err.println(flag);
		Object flag1 = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "h1.signService", "registSign", sign);
		System.err.println(flag1);
		Object list = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, "h1.signService", "getSignList", sign);
		System.err.println(JSONUtils.toString(list));
	}

}
