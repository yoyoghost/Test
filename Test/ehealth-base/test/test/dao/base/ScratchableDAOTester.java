package test.dao.base;

import ctd.util.JSONUtils;
import eh.base.constant.ScratchableConstant;
import eh.base.dao.ScratchableDAO;
import eh.entity.base.Scratchable;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

public class ScratchableDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static ScratchableDAO dao;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("scratchableDAO", ScratchableDAO.class);
	}

	public void testfindByTempIdAndLevel() {
		/*int tempId= ScratchableConstant.SCRATCHABLE_TEMP_NGARI;//根据访问获取从哪个公众号获取

		String appId="wxd2e81bf9ed28e14e";
		int limit=10;
		long start=0;
		int level= ScratchableConstant.SCRATCHABLE_LEVEL_NGARI;//个人设置
		List<Scratchable> list = dao.findByTempIdAndLevel(1,0);*/
		System.out.println(JSONUtils.toString(dao.findModelsByConfigId(null,0)));
	}

	public void testfindByTempIdAndLevelAndAppId() {
		/*int tempId= ScratchableConstant.SCRATCHABLE_TEMP_LATEX;//根据访问获取从哪个公众号获取

		String appId="wxd2e81bf9ed28e14e";
		int limit=10;
		long start=0;
		int level= ScratchableConstant.SCRATCHABLE_LEVEL_ORGAN;//个人设置
		List<Scratchable> list = dao.findByTempIdAndLevelAndAppId(appId,tempId,level);
		System.out.println(JSONUtils.toString(list));*/
	}
}
