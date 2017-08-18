package test.dao;

import java.util.Date;
import java.util.List;


import ctd.util.JSONUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.impl.user.UserDAO;

import junit.framework.TestCase;

public class UserDAOTester extends TestCase {
	
	private static ClassPathXmlApplicationContext appContext;
	private static UserDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("userDAO",UserDAO.class);
	}
	
	public void testUserCreate(){
		User u = new User();
		
		u.setId("13575730220");
		u.setEmail("s@sina.com");
		u.setName("test");
		u.setPlainPassword("123");
		u.setCreateDt(new Date());
		u.setStatus("1");
		
		dao.save(u);
	}
	
	public void testUserAndUrtsCreate(){
		User u = new User();
		
		u.setId("13575730220");
		u.setEmail("s@sina.com");
		u.setName("test");
		u.setPlainPassword("123");
		u.setCreateDt(new Date());
		u.setStatus("1");
		
		UserRoleTokenEntity urt = new UserRoleTokenEntity();
		urt.setRoleId("patient");
		urt.setTenantId("eh");
		urt.setManageUnit("eh");
		
		dao.remove("13575730220");
		dao.createUser(u, urt);
		
		
	}
	
	public void convertPwdTest(){
		User u = dao.get("13858043673");
		u.setPlainPassword(u.getPassword());
		
		dao.update(u);
	}


    public void testFindByManageUnit(){
        List<User>  users = dao.findByManageUnit("eh", 0, 20);
        System.out.println(JSONUtils.toString(users.get(0)));
    }
	
	public void convertAllPwdTest(){
		HibernateSessionTemplate.instance().executeTrans(new HibernateStatelessAction() {
			
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql ="from User where LENGTH(password) < 64";
				Query q = ss.createQuery(hql);
				List<User> users = q.list();
				
				for(User u : users){
					u.setPlainPassword(u.getPassword());
					ss.update(u);
				}
				
			}
		});
	}

}
