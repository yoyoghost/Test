package test.dao;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.base.user.WXUserService;
import eh.bus.dao.HisRemindDAO;
import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.entity.msg.Ad;
import eh.msg.dao.AdDAO;

public class HisRemindDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static HisRemindDAO dao = appContext.getBean("hisRemindDAO",HisRemindDAO.class);

	public void testfindUnRemindUsersByMaintainOrgan(){
		System.out.println(JSONUtils.toString(dao.findUnRemindUsersByMaintainOrgan(1)));
	}

	public void testfindUnRemindRecordsByUsersAndMaintainOrgan(){
		System.out.println(JSONUtils.toString(dao.findUnRemindRecordsByUsersAndMaintainOrgan("1182",1)));
	}

	public void testbeReminded(){
		System.out.println(JSONUtils.toString(dao.beReminded("1182",1)));
	}
}
