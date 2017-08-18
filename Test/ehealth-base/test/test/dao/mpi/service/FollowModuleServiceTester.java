package test.dao.mpi.service;

import ctd.util.JSONUtils;
import eh.entity.mpi.FollowModule;
import eh.entity.mpi.FollowModulePlan;
import eh.mpi.service.follow.FollowModuleService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jianghc
 * @create 2016-10-12 10:13
 **/
public class FollowModuleServiceTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    private static FollowModuleService service = appContext.getBean("followModuleService", FollowModuleService.class);

    public void testGetAllModules() {
        //List<Map> map = service.getAllModuleInfo(0,10);
        /*for (String str : (Set<String>)map.keySet()) {
            Map mp = (Map) map.get(str);
            System.out.println(JSONUtils.toBytes(map));
        }*/

        System.out.println(JSONUtils.toString(service.getAllModuleInfo(0,10)));
    }

    public void testGetModuls() {
       /* List<Map> maps = service.getModuls();
        for (Map map : maps) {
            System.out.println(JSONUtils.toString(map));
        }*/
    }


    public void testCreateOneModule() {
        FollowModule module = new FollowModule();
        module.setTitle("测试模板");
        module.setRemark("仅供测试使用");


        List<FollowModulePlan> plans = new ArrayList<>();
        for (int i = 0; i <= 2; i++) {
            FollowModulePlan plan = new FollowModulePlan();
            plan.setPlanType(3);
            plan.setIntervalNum(1);
            plan.setIntervalUnit(1);
            plan.setIntervalDay(5);
            plan.setIntervalDayUnit(1);
            plan.setRemindSign(false);
            plan.setAheadNum(1);
            plan.setAheadUnit(1);
            plan.setContent("测试计划" + i);
            plans.add(plan);
        }

//        System.out.println(JSONUtils.toString(service.createOneModule(module, plans)));

    }

    public void testUpdateOneModule(){
        List<Map> maps = service.getModuls(0,10);
        Map map = maps.get(1);
      //  System.out.println(JSONUtils.toString(map));
        FollowModule module = (FollowModule)map.get("followModule");
        List<FollowModulePlan> plans = (List<FollowModulePlan>)map.get("followModulePlanList");

        module.setRemark("测试更新2");
        plans.remove(1);
        for (int i = 4; i <= 5; i++) {
            FollowModulePlan plan = new FollowModulePlan();
            plan.setPlanType(3);
            plan.setIntervalNum(1);
            plan.setIntervalUnit(1);
            plan.setIntervalDay(5);
            plan.setIntervalDayUnit(1);
            plan.setRemindSign(false);
            plan.setAheadNum(1);
            plan.setAheadUnit(1);
            plan.setContent("测试计划NEW" + i);
            plans.add(plan);
        }
        List<Integer> dels = new ArrayList<Integer>();
        dels.add(14);
//        Map reMap = service.updateOneModule(module,plans,dels);
//        System.out.println(JSONUtils.toString(reMap));

    }

    public void testDeleteOneModule(){
        service.deleteOneModule(2);
    }





}
