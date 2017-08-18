package test.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import eh.cdr.service.DocIndexService;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.cdr.dao.DocIndexDAO;
import eh.entity.cdr.DocIndex;
import eh.entity.cdr.DocNumType;

public class DocIndexDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreate() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		
		
		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000,9999);
		
		DocIndex r = new DocIndex();
		r.setClinicId(nmr);
		r.setDocId(12+n4);
//		r.setDocIndexId(32+n4);
		r.setDocType(String.valueOf(n4%9+1));
		r.setMpiid("21"+nmr);
		dao.save(r);
	}
	
	/**
	 * 文档索引查询服务之情况一测试（根据主索引查一个病人的全部文档索引记录）
	 * @throws DAOException
	 */
	public void testFindByMpiid() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		String mpiid="402881834b7172a4014b7172acb90000";
		List<DocIndex> list=dao.findByMpiid(mpiid);
		for(int i=0;i<list.size();i++){
			System.out.println(list.size()+JSONUtils.toString(list.get(i)));
		}
		
	}
	
	/**
	 * 文档索引查询服务之情况二测试（根据主索引和就诊序号查一个病人一次看病的文档索引记录）
	 * @throws DAOException
	 */
	public void testFindByMpiidAndClinicId() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		String mpiid="402881834b7172a4014b7172acb90000";
		int clinicId=1;
		List<DocIndex> list=dao.findByMpiidAndClinicId(mpiid, clinicId);
		for(int i=0;i<list.size();i++){
			System.out.println(list.size()+JSONUtils.toString(list.get(i)));
		}
		
	}
	
	/**
	 * 文档索引查询服务之情况三测试（根据主索引和文档类别查一个病人的某类索引文档）
	 * @throws DAOException
	 */
	public void testFindByMpiidAndDocType() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		String mpiid="402881834b7172a4014b7172acb90000";
		String docType="1";
		List<DocIndex> list=dao.findByMpiidAndDocType(mpiid, docType);
		for(int i=0;i<list.size();i++){
			System.out.println(list.size()+JSONUtils.toString(list.get(i)));
		}
		
	}
	
	public void testFindByMpiidAndDocTypeAndClinicId() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		String mpiid="402881834b7172a4014b7172acb90000";
		String docType="1";
		int clinicId=1;
		List<DocIndex> list=dao.findByMpiidAndDocTypeAndClinicId(mpiid, docType, clinicId);
		for(int i=0;i<list.size();i++){
			System.out.println(list.size()+JSONUtils.toString(list.get(i)));
		}
		
	}
	
	/**
	 * 按病历类别统计文档数服务
	 * @throws DAOException
	 */
	public void testFindDocNumByType() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		String mpiid="402881834b7172a4014b7172acb90000";
		List<DocNumType> list=dao.findDocNumByType(mpiid);
		for(int i=0;i<list.size();i++){
			System.out.println(list.size()+JSONUtils.toString(list.get(i)));
		}
		
	}
	
	/**
	 * 文档展示查询服务
	 * @throws DAOException
	 */
	public void testGetDispDocUrl() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		Integer docIndex = 1;
		String url=dao.getDispDocUrl(docIndex);
		
		System.out.println(JSONUtils.toString(url));
	}
	
	public void testGetDocType() throws DAOException{
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
		List<DictionaryItem> list=dao.getDocType();
		System.out.println(JSONUtils.toString(list));
	}
	
	/**
	 * @function 根据患者主索引、文档类型查询分页分组列表功能测试
	 * @author zhangjr
	 * @date 2015-11-4
	 * @return void
	 */
	public void testFindByMpiIdAndDocTypeWithPage(){
		DocIndexDAO dao =appContext.getBean("docIndexDAO", DocIndexDAO.class);
//		List<HashMap> list= dao.findByMpiIdAndDocTypeWithPage("2c9081814cd4ca2d014cd4ddd6c90000", "1" , 0, 10);

		List<Map<String, Object>> list2= new DocIndexService().findByMpiIdAndDocTypeWithPage("2c9081814cd4ca2d014cd4ddd6c90000", null , 0, 10);

//		HashMap<String, Object>  list3= dao.findByMpiIdAndDocTypeWithPageForPc("2c9081814cd4ca2d014cd4ddd6c90000", null , 0, 10);
		HashMap<String, Object>  list4= new DocIndexService().findByMpiIdAndDocTypeWithPageForPc("2c9081814cd4ca2d014cd4ddd6c90000", null , 0, 10);
		System.out.println(JSONUtils.toString(list4));
	}
	
	/**
	 * 整理数据
	 * 
	 * @author zhangx
	 * @date 2015- 11- 2 下午10:03:44
	 */
	public void testCleanData() {
		DocIndexDAO dao = appContext.getBean("docIndexDAO", DocIndexDAO.class);
		dao.cleanData();
	}

}
