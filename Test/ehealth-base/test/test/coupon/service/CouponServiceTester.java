package test.coupon.service;

import ctd.util.JSONUtils;
import eh.base.service.DoctorGroupService;
import eh.coupon.constant.CouponBusTypeEnum;
import eh.coupon.service.CouponService;
import eh.entity.base.Doctor;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

public class CouponServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static CouponService service;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service = appContext.getBean("couponService", CouponService.class);
	}

	public void testfindCoupons(){

	}



}
