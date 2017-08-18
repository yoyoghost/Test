package test.dao;

import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.bus.dao.CallRecordDAO;
import eh.entity.bus.CallRecord;
import eh.util.CallResult;
import eh.util.Callback;

import junit.framework.TestCase;

public class CallRecordTest extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testSaveCallRecord() {
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		CallRecord callRecord = new CallRecord("222", "222", "222", "222",
				"222");
		dao.save(callRecord);
	}

	public void testCallBack() {
		Callback call = new Callback();
		// call.SDKCallback("15990092533", "18768177768");
		String calling = "15990092533";
		String called = "18868744478";
		int bussType = 3;

		call.SDKCallbackTwo(calling, called, bussType, 526);
	}

	public void testGetCallByFour() {
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		List<CallRecord> list = dao.findCallByFour("15990092533",
				"18868744478", 3, 526);
		System.out.println(JSONUtils.toString(list));
	}

	public void testGetByCallSid() {
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		CallRecord ca = dao.getByCallSid("16011515250562980001005700368af9");
		System.out.println(JSONUtils.toString(ca));
	}

	public void testSDKCallResult() {
		CallResult cr = new CallResult();
		cr.SDKCallResult("16011515250562980001005700368af9");
	}

	public void testGetSumBussCallTime() {
		int bussType = 3;
		int bussId = 916;
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		System.out.println(dao.getMaxBussCallTime(bussType, bussId));
	}

	public void testFindByBussIdAndBussType() {
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		System.out.println(JSONUtils.toString(dao.findByBussIdAndBussType(712,3)));
	}

	public void testGetCallRecordNumBuss() {
		CallRecordDAO dao = appContext.getBean("callRecordDAO",
				CallRecordDAO.class);
		System.out.println(dao.getCallRecordNumBuss(790,3));
	}
}
