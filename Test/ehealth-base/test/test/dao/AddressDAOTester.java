package test.dao;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.entity.mpi.Address;
import eh.mpi.dao.AddressDAO;

import junit.framework.TestCase;

public class AddressDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static AddressDAO dao = null;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("addressDAO",AddressDAO.class);
	}
	
	public void testAddAddress(){
		Address a = new Address();
		a.setMpiId("000000");
		a.setReceiver("小明");
		a.setRecMobile("15257109953");
		a.setRecTel("1234567");
		a.setAddress1("浙江省");
		a.setAddress2("杭州市");
		a.setAddress3("滨江区");
		a.setAddress4("江南大道3778号潮人汇9楼");
		a.setZipCode("310000");
		
		//dao.save(a);
		dao.addAddress(a);
		System.out.println("add successfully");
	}
	
	public void testUpdateAddressByAddressId(){
		Address a = new Address();
		a.setAddressId(2);
		a.setMpiId("000000");
		a.setReceiver("小谢");
		a.setRecMobile("15257109953");
		a.setRecTel("1234567");
		a.setAddress1("浙江省");
		a.setAddress2("杭州市");
		a.setAddress3("滨江区");
		a.setAddress4("江南大道3778号潮人汇9楼");
		a.setZipCode("310000");
		dao.updateAddress(a);
		System.out.println("update successfully");
	}
	
	public void testDeleteByAddressId(){
		dao.deleteByAddressId(2);
		System.out.println("delete successfully");
	}
	
	public void testFindByMpiId() {
		System.out.println(JSONUtils.toString(dao.findByMpiId("2c9081814cc3ad35014cc54fca420003")));
	}
}
