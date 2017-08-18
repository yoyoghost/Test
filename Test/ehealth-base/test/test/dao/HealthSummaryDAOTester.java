package test.dao;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.cdr.dao.HealthSummaryDAO;
import eh.entity.cdr.HealthSummary;

import junit.framework.TestCase;

public class HealthSummaryDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static HealthSummaryDAO healthSummaryDAO;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		healthSummaryDAO = appContext.getBean("healthSummaryDAO", HealthSummaryDAO.class);
	}

	/**
	 * 保存数据
	 * @throws DAOException
	 */
	public void testCreate() throws DAOException {
		int nmr = ThreadLocalRandom.current().nextInt(9);
		HealthSummary summary = new HealthSummary();
		summary.setMpiId("402881834b6d0cfc014b6d0d04f10000");
		summary.setSummaryType(nmr);
		summary.setStartDate(new Date());
		healthSummaryDAO.save(summary);
	}
	
	/**
	 * 根据健康摘要序号查询
	 * @throws DAOException
	 */
	public void testGetBySummaryId() throws DAOException {
		System.out.println(JSONUtils.toString(healthSummaryDAO.getBySummaryId(1)));
	}
	
	/**
	 * 根据病人主索引查询
	 * @throws DAOException
	 */
	public void testFindByMpiId() throws DAOException {
		String mpiId ="402881834b6d0cfc014b6d0d04f10000";
		List<HealthSummary> list = healthSummaryDAO.findByMpiId(mpiId);
		System.out.println(JSONUtils.toString(list));
	}
	
	/**
	 * 健康摘要查询服务
	 * @throws DAOException
	 */
	public void testFindHealthSummaryByMpiId() throws DAOException {
		String mpiId ="402881834b6d0cfc014b6d0d04f10000";
		Object list = healthSummaryDAO.findHealthSummaryByMpiId(mpiId);
		System.out.println(JSONUtils.toString(list));
	}
}
