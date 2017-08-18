package eh.cdr.service;

import java.util.ArrayList;
import java.util.List;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.service.AdviceService;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.bus.Advice;
import eh.entity.bus.AppointSource;
import eh.entity.his.push.callNum.HisCallNoticeReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.push.NgariService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

/**
 * company: ngarihealth
 * author: 6037/liuya
 * date:2017/2/120
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class AdviceServiceTest extends AbstractJUnit4SpringContextTests {

    @Test
    public void saveDrugAdvice() {
        AdviceService adviceService = AppContextHolder.getBean("eh.adviceService",AdviceService.class);
        Advice ad = new  Advice();
        ad.setAdviceContent("测试意见22222");
        ad.setAdviceType("doctor");
        ad.setUserId("18768177768");

        adviceService.saveAdvice(ad);
    }

    @Test
    public void saveDrugAdvice1() {
    	NgariService adviceService = AppContextHolder.getBean("eh.ngariService",NgariService.class);
    	PushRequestModel pushRequestModel = new PushRequestModel();
		pushRequestModel.setBranchId("1");
		pushRequestModel.setOrganId("1");
		pushRequestModel.setServiceId("PushNoticeInfo");
		HisCallNoticeReqMsg msg = new HisCallNoticeReqMsg();
		msg.setIdCard("330481198909072210");
		msg.setMessage("你有一条新的报告单！");
		msg.setMsgType(2);
		pushRequestModel.setData(msg);
		System.out.println(JSONUtils.toString(pushRequestModel));

        adviceService.reciveHisMsg(pushRequestModel);
    }
    
    public void show(Object object){
        Assert.notNull(object , "object can't be null");
        System.out.println(JSONUtils.toString(object));
    }

    // 通州号源导入数据测试
	@Test
	public void saveAppointSource() {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		List<AppointSource> sourceList = new ArrayList();
		String str = "[{'organId':1000668,'organSourceId':'20170616|124916|2|20','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 15:00:00','endTime':'2017-06-16 16:00:00','sourceNum':1,'orderNum':20,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'20'},{'organId':1000668,'organSourceId':'20170616|124916|2|10','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 12:50:00','endTime':'2017-06-16 14:00:00','sourceNum':1,'orderNum':10,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'10'},{'organId':1000668,'organSourceId':'20170616|124916|2|7','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 12:50:00','endTime':'2017-06-16 14:00:00','sourceNum':1,'orderNum':7,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'7'},{'organId':1000668,'organSourceId':'20170616|124916|2|6','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 12:50:00','endTime':'2017-06-16 14:00:00','sourceNum':1,'orderNum':6,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'6'},{'organId':1000668,'organSourceId':'20170616|124916|2|19','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 15:00:00','endTime':'2017-06-16 16:00:00','sourceNum':1,'orderNum':19,'stopFlag':1,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'19'},{'organId':1000668,'organSourceId':'20170616|124916|2|15','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 14:00:00','endTime':'2017-06-16 15:00:00','sourceNum':1,'orderNum':15,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'15'},{'organId':1000668,'organSourceId':'20170616|124916|2|9','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 12:50:00','endTime':'2017-06-16 14:00:00','sourceNum':1,'orderNum':9,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'9'},{'organId':1000668,'organSourceId':'20170616|124916|2|8','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 12:50:00','endTime':'2017-06-16 14:00:00','sourceNum':1,'orderNum':8,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'8'},{'organId':1000668,'organSourceId':'20170616|124916|2|14','organSchedulingId':'124916','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':2,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 14:00:00','endTime':'2017-06-16 15:00:00','sourceNum':1,'orderNum':14,'stopFlag':0,'createDate':'2017-06-13 19:27:04','cloudClinic':0,'modifyDate':'2017-06-13 19:27:04','originalSourceId':'14'}]"; // 一个未转化的字符串
		JSONArray json = JSONArray.fromObject(str); // 首先把字符串转成 JSONArray 对象
		if (json.size() > 0) {
			for (int i = 0; i < json.size(); i++) {
				JSONObject job = json.getJSONObject(i); // 遍历 jsonarray														
				AppointSource source = JSONUtils.parse(job.toString(),AppointSource.class);
				sourceList.add(source);
			}
		}
		dao.saveAppointSources(sourceList);

		List<AppointSource> sourceList1 = new ArrayList();
		String str1 = "[{'organId':1000668,'organSourceId':'20170616|124915|1|21','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 10:00:00','endTime':'2017-06-16 11:00:00','sourceNum':1,'orderNum':21,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'21'},{'organId':1000668,'organSourceId':'20170616|124915|1|20','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 10:00:00','endTime':'2017-06-16 11:00:00','sourceNum':1,'orderNum':20,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'20'},{'organId':1000668,'organSourceId':'20170616|124915|1|6','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 08:00:00','endTime':'2017-06-16 09:00:00','sourceNum':1,'orderNum':6,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'6'},{'organId':1000668,'organSourceId':'20170616|124915|1|19','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 10:00:00','endTime':'2017-06-16 11:00:00','sourceNum':1,'orderNum':19,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'19'},{'organId':1000668,'organSourceId':'20170616|124915|1|5','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 08:00:00','endTime':'2017-06-16 09:00:00','sourceNum':1,'orderNum':5,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'5'},{'organId':1000668,'organSourceId':'20170616|124915|1|4','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 08:00:00','endTime':'2017-06-16 09:00:00','sourceNum':1,'orderNum':4,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'4'},{'organId':1000668,'organSourceId':'20170616|124915|1|24','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 11:00:00','endTime':'2017-06-16 12:40:00','sourceNum':1,'orderNum':24,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'24'},{'organId':1000668,'organSourceId':'20170616|124915|1|25','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 11:00:00','endTime':'2017-06-16 12:40:00','sourceNum':1,'orderNum':25,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'25'},{'organId':1000668,'organSourceId':'20170616|124915|1|13','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 09:00:00','endTime':'2017-06-16 10:00:00','sourceNum':1,'orderNum':13,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'13'},{'organId':1000668,'organSourceId':'20170616|124915|1|14','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 09:00:00','endTime':'2017-06-16 10:00:00','sourceNum':1,'orderNum':14,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'14'},{'organId':1000668,'organSourceId':'20170616|124915|1|11','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 09:00:00','endTime':'2017-06-16 10:00:00','sourceNum':1,'orderNum':11,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'11'},{'organId':1000668,'organSourceId':'20170616|124915|1|12','organSchedulingId':'124915','appointDepartCode':'3449','appointDepartName':'小儿心胸内科门诊','doctorId':16762,'workDate':'2017-06-16 00:00:00','workType':1,'sourceType':1,'sourceLevel':1,'price':50.0,'startTime':'2017-06-16 09:00:00','endTime':'2017-06-16 10:00:00','sourceNum':1,'orderNum':12,'stopFlag':0,'createDate':'2017-06-13 19:02:51','cloudClinic':0,'modifyDate':'2017-06-13 19:02:51','originalSourceId':'12'}]"; // 一个未转化的字符串
		JSONArray json1 = JSONArray.fromObject(str1); // 首先把字符串转成 JSONArray 对象
		if (json1.size() > 0) {
			for (int i = 0; i < json1.size(); i++) {
				JSONObject job1 = json1.getJSONObject(i); // 遍历 jsonarray														
				AppointSource source1 = JSONUtils.parse(job1.toString(),AppointSource.class);
				sourceList1.add(source1);
			}
		}
		dao.saveAppointSources(sourceList1);
	}


}