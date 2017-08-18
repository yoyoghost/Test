package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.account.user.User;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryItem;
import ctd.util.JSONUtils;

import eh.base.user.AdminUserSevice;

public class AdminUserServiceTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/**
	 * 获取当前管理员所管机构的区域
	 * @author hyj
	 */
	public void testGetAddrAreaByAdmin(){
		String manageUnit="eh001";
		AdminUserSevice admin=appContext.getBean("adminUserSevice", AdminUserSevice.class);
		List<DictionaryItem> list=admin.getAddrAreaByAdmin(manageUnit);
		for(DictionaryItem d:list){
			System.out.println(JSONUtils.toString(d));
		}
	}
	
	/**
	 * 创建管理员账户
	 * @author ZX
	 * @date 2015-5-27  12:04:26
	 * @throws ControllerException
	 */
	public void testCreateAdminUser() throws ControllerException{
		String userId="18768177770";
		String name="张肖"; 
		String password="888888";
		int organId=1;
		AdminUserSevice adminService=appContext.getBean("adminUserSevice", AdminUserSevice.class);
		String retUserId=adminService.createAdminUser(userId,name,password ,organId);
		
		System.out.println(retUserId);
	}
	
	/**
	 * 修改管理员信息
	 * @author ZX
	 * @throws ControllerException
	 */
	public void testResetAdminUserInfo() throws ControllerException{
		String userId="18768177768";
		String name="";
		String email="18768177768@163.com";
		Integer avatarFileId= null;
		
		AdminUserSevice adminService=appContext.getBean("adminUserSevice", AdminUserSevice.class);
		adminService.resetAdminUserInfo(userId, name, email, avatarFileId);
	}
	
	/**
	 * 根据机构ID获取该机构下的所有管理员列表
	 * 
	 * @author yaozh
	 * @return
	 */
	public void testFindByManageUnit() throws ControllerException{
		AdminUserSevice adminService=appContext.getBean("adminUserSevice", AdminUserSevice.class);
		List<User> users = adminService.findByManageUnit("eh");
		 System.out.println(JSONUtils.toString(users.get(0)));
	}
	
	
	
}
