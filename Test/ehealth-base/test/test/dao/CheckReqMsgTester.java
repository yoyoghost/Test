package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.bus.dao.CheckReqMsg;

import java.util.concurrent.TimeUnit;

public class CheckReqMsgTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testCheckSysMsgAndPush() {
		int checkRequestId = 1;
		int flag = 0;
		CheckReqMsg.checkSysMsgAndPush(checkRequestId, flag);
	}

	/**
	 * 医技检查报告发布，发送短信给患者
	 */
	public void testsendMsg(){
		try {
			int checkRequestId=18;
//			CheckReqMsg.sendMsg(checkRequestId, "checkRequestReportIssue");

			//将线程睡眠2秒，否则短信发送不成功
			TimeUnit.SECONDS.sleep(200);

		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

}
