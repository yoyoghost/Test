package test.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.service.payment.PaymentService;
import eh.bus.service.payment.QueryOutpatient;
import eh.entity.bus.PayBusiness;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static PaymentService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("paymentService", PaymentService.class);
	}

	public void testSetRecipeAddress(){
		System.out.println(ctd.util.JSONUtils.toString(service.setRecipeAddress("2c9081855208340401523393983c026e","541000","1","1","4")));
	}

	public void testwxPayment(){
		/*List<String> list = new ArrayList<String>();
		list.add("2222");
		System.out.println(JSONUtils.toString(service.wxPayment("2c9081855208340401523393983c026e",400.33,"40","prepay",1,list)));*/

		//判断支付状态
		PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
		PayBusiness payBusiness = payBusinessDAO.get("3454545");
		System.err.println(JSONUtils.toString(payBusiness));
}
	public void testWxPayment(){
		String mpiID = "2c90818256791e5501567cd1ee2f0004";
		double totalFee = 0.1;
		String payWay = "40";
		String busType = "prepay";
		int organId = 1;
		List<String> orderIdList = new ArrayList<String>();
		orderIdList.add("188882");
//		service.wxPayment(mpiID,totalFee,payWay,busType,organId,orderIdList);
	}

}
