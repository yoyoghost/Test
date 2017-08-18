package test.dao.mpi;

import ctd.util.JSONUtils;
import ctd.util.context.Context;
import eh.entity.his.sign.SignCommonBean;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.mpi.dao.UserSourceDAO;
import eh.remote.IHisServiceInterface;
import eh.util.RpcServiceInfoUtil;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.List;

public class UserSourceDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	private static UserSourceDAO dao;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=appContext.getBean("userSourceDAO",UserSourceDAO.class);
	}

	public void testfindUnRemindPatient(){
		Date d= Context.instance().get("date.datetimeOfLastDay",Date.class);
		System.out.println(JSONUtils.toString(dao.findUnRemindPatient(d,0,100)));
	}
}
