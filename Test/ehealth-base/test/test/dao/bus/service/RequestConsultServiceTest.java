package test.dao.bus.service;

import com.alibaba.druid.support.json.JSONUtils;
import ctd.persistence.DAOFactory;
import eh.bus.dao.WxTemplateDAO;
import eh.bus.service.consult.RequestConsultService;
import eh.entity.bus.Consult;
import eh.util.Easemob;
import eh.util.SameUserMatching;
import junit.framework.TestCase;
import org.jdom.output.SAXOutputter;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhangx on 2016/5/24.
 */
public class RequestConsultServiceTest  extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static RequestConsultService service;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service=new RequestConsultService();
    }


    /**
     * 以管理员身份发送消息
     */
    public void testSendSimpleMsgByAdmin(){
        //以管理员身份发送消息[您有一条来自xxx（申请人）的图文咨询申请，请及时回复；]
//        String msg="您有一条来自张肖测试的图文咨询申请，请及时回复；";
        Object key = DAOFactory.getDAO(WxTemplateDAO.class).getTemplateByTemplateKeyAndAppId("callNumberRemind","");
        System.out.println(key);

//        Easemob.sendSimpleMsg("admin", "199854036587905468", msg, "chatgroups");
//        Integer as=null;
//        System.out.println(as.toString());
    }

    public void testpatientsAndDoctor(){
        String mpiId="2c9081814cd4ca2d014cd4ddd6c90000";
        String requestMpi="2c9081814d689a20014d6b6c4ad80001";
        Integer doctorId=3988;
        HashMap<String,Boolean> map= SameUserMatching.patientsAndDoctor(mpiId, requestMpi, doctorId);
        System.out.println(JSONUtils.toJSONString(map));
    }

    public void testcanSubmitConsult(){
        Consult consult=new Consult();
        consult.setMpiid("40288114542d342501542d3af9f20000");
        consult.setRequestMpi("2c9081814cc5cb8a014cd483068d0001");
        consult.setConsultOrgan(1);
        consult.setConsultDepart(3024);
        consult.setConsultDoctor(1182);
        consult.setRequestMode(2);
        Boolean flag=service.canSubmitConsult(consult);
        System.out.println(flag);
    }

}
