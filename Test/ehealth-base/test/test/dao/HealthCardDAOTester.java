package test.dao;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.entity.mpi.HealthCard;
import eh.mpi.dao.HealthCardDAO;
import junit.framework.TestCase;

public class HealthCardDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static HealthCardDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=appContext.getBean("healthCardDAO", HealthCardDAO.class);
	}
	
	/**
	 * 保存
	 * @throws DAOException
	 */
	public void testCreate() throws DAOException{
		
		int n4 = ThreadLocalRandom.current().nextInt(10000000,99999999);
		int n3 = ThreadLocalRandom.current().nextInt(100,999);
		HealthCard c = new HealthCard();
		c.setCardId(n4+"");
		c.setCardType("1");
		c.setMpiId("402881834b6d0cfc014b6d0d04f10000");
		c.setCardOrgan(n3);
		dao.save(c);
	}
	
	
	public void testGetByMpiId()throws DAOException{
		String mpiId="402881834b6d0cfc014b6d0d04f10000";
		List<HealthCard> card=dao.findByMpiId(mpiId);
		System.out.println(JSONUtils.toString(card));
	}
	

	/**
	 * 根据卡号,发卡机构 查询 卡信息
	 * @throws DAOException
	 */
	public void testGetByCardOrganAndCardId(){
		HealthCard card=dao.getByCardOrganAndCardId(1, "73725840", "123");
		System.out.println(JSONUtils.toString(card));
	}
	
	public void testGetMpiidByCard(){
		String mpiid=dao.getMpiIdByCard(1, "73725840", "321");
		System.out.println(JSONUtils.toString(mpiid));
	}
	
	public void testFindByMpiid(){
		List<HealthCard> rs= dao.findByMpiId("402881834b71a24f014b71a254020000");
		System.out.println(JSONUtils.toString(rs));
	}
	
	public void testFindByCardOrganAndMpiId(){
		List<HealthCard> rs= dao.findByCardOrganAndMpiId(1,"2c9081824cc3ae4a014cc4ee8e2c0000");
		System.out.println(JSONUtils.toString(rs));
	}

}
