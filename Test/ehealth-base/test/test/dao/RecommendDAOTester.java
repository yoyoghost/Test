package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.entity.mpi.Recommend;
import eh.mpi.dao.RecommendDAO;

public class RecommendDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static RecommendDAO dao = appContext.getBean("recommendDAO",RecommendDAO.class);

	/**
	 * 推荐开通推
	 * @author zhangx
	 * @date 2015-12-30 下午3:36:54
	 */
	public void testRecommendOpenSet(){
		dao.recommendOpenSet(2, "2c9081824cc3552a014cc3a9a0120002", 1182);
	}
	
	/**
	 * 获取推荐某项业务开通的次数
	 * @author zhangx
	 * @date 2015-12-30 下午3:46:24
	 */
	public void testGetRecommendNum(){
		System.out.println(dao.getRecommendNum( "2c9081824cc3552a014cc3a9a0120002", 1182,2));
	}
	
	/**
	 * 获取推荐某项业务开通记录
	 * @author zhangx
	 * @date 2015-12-30 下午3:47:53
	 */
	public void testgetByMpiIdAndDoctorId(){
		List<Recommend> list=dao.findByMpiIdAndDoctorId( "2c9081824cc3552a014cc3a9a0120002", 1182);
		System.out.println(JSONUtils.toString(list));
	}
}
