package test.coupon.dao;

import ctd.util.BeanUtils;
import eh.bus.dao.AppointRecordDAO;
import eh.coupon.dao.CouponInfoDAO;
import eh.coupon.service.CouponPushService;
import eh.entity.bus.AppointRecord;
import eh.entity.coupon.CouponInfo;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CouponInfoDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static CouponInfoDAO dao = appContext.getBean("couponInfoDAO",CouponInfoDAO.class);
	
	public  void testSave(){
		CouponInfo info=new CouponInfo();
		dao.saveCoupon(info);
	}

	public void testparse(){
		CouponInfo info=new CouponInfo();
		info.setDate(new Date());
		Map<String,Object> map=new HashMap<String,Object>();
		BeanUtils.copy(info,map);
		System.out.println(map);
	}

	public void testappoint(){
		AppointRecordDAO dao = appContext.getBean("appointRecordDAO",AppointRecordDAO.class);
		AppointRecord ar=dao.get(109496);
		CouponPushService service=new CouponPushService();
		service.sendAppointSuccessCouponMsg(ar);
	}


}
