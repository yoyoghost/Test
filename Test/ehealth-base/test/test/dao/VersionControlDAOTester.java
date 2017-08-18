package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.VersionControlDAO;
import eh.entity.base.VersionControl;

public class VersionControlDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/**
	 * 查询程序最新版本的版本号
	 * @author hyj
	 */
	public void testGetByPrgType(){
		int prgType=2;
		VersionControlDAO dao = appContext.getBean("versionControlDAO", VersionControlDAO.class);
		VersionControl v=dao.getByPrgType(prgType);
		System.out.println(JSONUtils.toString(v));
		
	}
	
	/**
	 * 检测版本更新服务
	 * @author hyj
	 */
	public void testCheckVersion(){
		int prgType=1;
		String version="v01.02.3001";
		VersionControlDAO dao = appContext.getBean("versionControlDAO", VersionControlDAO.class);
		boolean flag=dao.checkVersion(prgType, version);
		System.out.println(flag);
	}
	
}
