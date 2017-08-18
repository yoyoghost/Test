package eh.cdr.service;

import com.google.common.collect.Lists;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.QuestionnaireDAO;
import eh.bus.service.consult.CommonConsultService;
import eh.entity.bus.Consult;
import eh.entity.bus.Questionnaire;
import eh.entity.cdr.Otherdoc;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import java.util.*;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/2/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class RecipeOrderServiceTest extends AbstractJUnit4SpringContextTests {
    @Test
    public void createOrder() throws Exception {
        RecipeOrderService recipeOrderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        Map<String,String> ext = new HashMap<>();
        ext.put("operMpiId","2c90818256d9c7ce0156da3aa6bb0000");
        ext.put("addressId","1");
        ext.put("payway","40");
        ext.put("payMode","0");
        ext.put("depId","3");
        show(recipeOrderService.createOrder(Arrays.asList(1),ext,1));
    }

    @Test
    public void createBlankOrder() throws Exception {
        RecipeOrderService recipeOrderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        Map<String,String> ext = new HashMap<>();
        ext.put("operMpiId","2c90818256d9c7ce0156da3aa6bb0000");
        ext.put("addressId","1");
        show(recipeOrderService.createBlankOrder(Arrays.asList(1),ext));
    }

    @Test
    public void cancelOrderById(){
        RecipeOrderService recipeOrderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        show(recipeOrderService.cancelOrderById(1,7));
    }

    @Test
    public void getOrderDetailById(){
        RecipeOrderService recipeOrderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        show(recipeOrderService.getOrderDetailById(1));
    }

    @Test
    public void payRecipeOrder() throws Exception {
        RecipeOrderService recipeOrderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);

        Map<String, Object> attrMap = new HashMap<>();

        attrMap.put("appId", "1");
        attrMap.put("openId", "2");
        attrMap.put("payway", "41");
        attrMap.put("busType", "recipe");
        attrMap.put("busId", "656220089MmM5763");
        attrMap.put("payMode", 1);
        attrMap.put("addressId", 3);
        attrMap.put("couponId", null);
//        show(recipeOrderService.payRecipeOrder(attrMap));
    }

    public void show(Object object) {
        Assert.notNull(object,"object can't be null...");
        System.out.println(JSONUtils.toString(object));
    }

    @Test
    public void testGetConsultAndPatientAndDoctorById() {
        Integer consultId = 202;
        ConsultDAO dao = DAOFactory.getDAO( ConsultDAO.class);
//        Map<String, Object> res = dao.getConsultAndPatientAndDoctorById(
//                consultId, 1492);
//        System.out.println(JSONUtils.toString(res));
    }

    @Test
    public void testRequestConsultAndCdrOtherdoc(){
        CommonConsultService service = AppContextHolder.getBean("eh.commonConsultService",CommonConsultService.class);
        Consult consult = new Consult();
        consult.setMpiid("2c9081824cc3552a014cc3a9a0120002");
        consult.setConsultType(1);
        consult.setEmergency(0);
        consult.setRequestMode(4);
        consult.setRequestMpi("2c9081824cc3552a014cc3a9a0120002");
        consult.setRequestTime(new Date());
        consult.setConsultOrgan(1);
        consult.setConsultDepart(47);
        consult.setConsultCost(0.0);
        consult.setExeDepart(47);
        consult.setConsultDoctor(110039);
//        consult.setLeaveMess("fdsasadsf");
        Questionnaire q = new Questionnaire();
        q.setMpiid("2c9081824cc3552a014cc3a9a0120002");
        q.setDiseaseStatus(2);
        consult.setQuestionnaire(q);
        List<Otherdoc> list = Lists.newArrayList();
        Otherdoc otherdoc = new Otherdoc();
        otherdoc.setDocName("saf");
        otherdoc.setDocFormat("d12");
        list.add(otherdoc);
//        Map<String, Object> res = service.requestConsultAndCdrOtherdoc(consult,list);
//        System.out.println(JSONUtils.toString(res));
    }

    @Test
    public void testFindPendingConsultByMpiIdAndDoctor(){
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
//        List<String> requestMpiIds = consultDAO.findPendingConsultByMpiIdAndDoctor("2c9081895828cb41015828dc4caa0000", 110364);
//        System.out.println(JSONUtils.toString(requestMpiIds));
    }

    @Test
    public void testQuestionnaire(){
        QuestionnaireDAO qDao = DAOFactory.getDAO(QuestionnaireDAO.class);
        Questionnaire q = qDao.get(1);
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult c = consultDAO.get(3253);
        consultDAO.setConsultQuestionnaire(c);
        System.out.println(JSONUtils.toString(c.getQuestionnaire()));

    }
}