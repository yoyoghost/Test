//package test.search;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//
//import junit.framework.TestCase;
//
//import org.apache.http.client.ClientProtocolException;
//import org.springframework.context.support.ClassPathXmlApplicationContext;
//
//import com.aliyun.opensearch.CloudsearchClient;
//import com.aliyun.opensearch.CloudsearchDoc;
//import com.aliyun.opensearch.CloudsearchSearch;
//import com.aliyun.opensearch.object.KeyTypeEnum;
//import ctd.persistence.DAOFactory;
//import ctd.persistence.bean.QueryResult;
//import ctd.util.JSONUtils;
//import eh.base.constant.AliConfig;
//import eh.base.dao.DoctorDAO;
//import eh.base.dao.EmploymentDAO;
//import eh.entity.base.Doctor;
//import eh.entity.base.Employment;
//import eh.entity.search.DoctorInfo;
//
//public class DoctorSearchTester extends TestCase {
//	private static ClassPathXmlApplicationContext appContext;
//
//	/*static {
//		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
//	}
//	private static DoctorDAO dao =appContext.getBean("doctorDAO",DoctorDAO.class);
//
//
//
//	public void testUploadDoctorInfo() throws ClientProtocolException, IOException{
//		HashMap<String, Object> opts=new HashMap<String, Object>();
//		List<Doctor> qr= dao.findByOrgan(1);
//		List<Map<String,Object>> list=new ArrayList<Map<String,Object>>();
//		EmploymentDAO epdao=DAOFactory.getDAO(EmploymentDAO.class);
//		for ( Doctor dr : qr) {
//			DoctorInfo info=new DoctorInfo();
//			Employment emp=epdao.getPrimaryEmpByDoctorId(dr.getDoctorId());
//			if(emp!=null){
//				info.setDepartmentid(emp.getDepartment());
//			}
//			info.setDoctorid(dr.getDoctorId());
//			info.setAppointstatus(dr.getHaveAppoint());
//			info.setDomain(dr.getDomain());
//			info.setEducation(dr.getEducation());
//			info.setHonour(dr.getHonour());
//			info.setIntroduce(dr.getIntroduce());
//			info.setName(dr.getName());
//			info.setOrdernum(dr.getOrderNum());
//			info.setProtitle(dr.getProTitle());
//			info.setPhoto(dr.getPhoto());
//			info.setScheduleinfo("周三,周四");
//			Map<String,Object> data=new HashMap<>();
//			data.put("cmd", "add");
//			//data.put("cmd", "delete");
//			data.put("fields", info);
//			list.add(data);
//		}
//		String upStr=JSONUtils.toString(list);
//		CloudsearchClient client=new CloudsearchClient(AliConfig.accessKeyId, AliConfig.accessKeySecret, AliConfig.OPENSEARCH_PUBLIC, opts,KeyTypeEnum.ALIYUN);
//		String table_name = "main";
//		CloudsearchDoc doc = new CloudsearchDoc("doctor_list", client);
//		System.out.println(doc.push(upStr, "main"));
//		System.out.println("ok");
//	}*/
//	public void testSearch() throws ClientProtocolException, IOException{
//		HashMap<String, Object> opts=new HashMap<String, Object>();
//		CloudsearchClient client=new CloudsearchClient(AliConfig.accessKeyId, AliConfig.accessKeySecret, AliConfig.OPENSEARCH_PUBLIC, opts,KeyTypeEnum.ALIYUN);
//		CloudsearchSearch search = new CloudsearchSearch(client);
//		// 添加指定搜索的应用：
//		search.addIndex("doctor_list");
//		// 指定搜索的关键词，这里要指定在哪个索引上搜索，如果不指定的话默认在使用“default”索引（索引字段名称是您在您的数据结构中的“索引到”字段。）
//		//search.setQueryString("'6'");
//		search.setQueryString("departmentid:'4'");
//		//search.setQueryString("index_name:'词典'");
//		// 指定搜索返回的格式。
//		search.setFormat("json");
//		List<String> fileds=new ArrayList<String>();
//		fileds.add("name");
//		//opts.put("fetch_field", fileds);//返回字段
//		//opts.put("hits", 100);//返回条数
//		// 设定过滤条件
//		//search.addFilter("diseasid>1");
//		// 设定排序方式 + 表示正序 - 表示降序
//		search.addSort("ordernum", "+");
//		// 返回搜索结果
//		String res=search.search(opts);
//		HashMap<String, Object> map=JSONUtils.parse(res, HashMap.class);
//		System.out.println(map);
//	}
//}