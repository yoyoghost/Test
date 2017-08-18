package test.opservice;

import ctd.util.JSONUtils;
import eh.entity.msg.Banner;
import eh.op.service.AppointOpService;
import eh.op.service.BannerService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/6/22.
 */
public class BannerServiceTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static BannerService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("bannerService", BannerService.class);
    }

    public void testCreateBannerAndBannerTarget() {
        Banner banner = new Banner();
        banner.setBannerName("我的测试banner3");
        banner.setBannerType("0");
        banner.setLink("https://www.baidu.com/");
        banner.setContent("我的测试banner first!");
        banner.setPhoto("2124");
        banner.setStartDate(new Date());
        banner.setEndDate(new Date());
        banner.setCreateTime(new Date());
        banner.setUserRolesId(6579);
        banner.setReadNum(0);
        banner.setOrderNum(1);
        banner.setRoleId(1);
        banner.setMemo("4我都恩爱的激发了看见了");

        List<String> organs = new ArrayList<String>();
        organs.add("4");
        organs.add("5");
        service.createBannerAndBannerTarget(banner, organs);
    }

    public void testQueryBannerByOrganIdAndStatusAndType() {
        List<Integer> organs = new ArrayList<Integer>();
        /*organs.add(1);
        organs.add(2);
        organs.add(3);*/
        //System.out.println(JSONUtils.toString(service.queryBannerByOrganIdAndStatusAndType(organs, null, "1", 0, 10)));
//        System.out.println(JSONUtils.toString(service.queryBannerByOrganIdAndStatusAndType(organs, null, "1", 0, 10)));

    }

    public void testUpdateBannerAndBannerTarget() {
        Banner banner = new Banner();
        banner.setBannerId(1);
        banner.setBannerName("2我的测试banner");
        banner.setBannerType("1");
        banner.setLink("2https://www.baidu.com/");
        banner.setContent("2我的测试banner first!");
        banner.setPhoto("2123");
        banner.setStartDate(new Date());
        banner.setEndDate(new Date());
        banner.setCreateTime(new Date());
        banner.setUserRolesId(6579);
        banner.setReadNum(0);
        banner.setOrderNum(1);
        banner.setRoleId(1);
        banner.setMemo("我都恩爱的激发了看见了");

        List<String> organs = new ArrayList<String>();
        organs.add("4");
        organs.add("5");
        service.updateBannerAndOrgans(banner,organs);
    }


    public void testFindBannerByDoctorId(){
        System.out.println(JSONUtils.toString(service.findBannerByDoctorId(1167,"0")));
    }

    public void testFindBannerTargetByOrganId(){
        System.out.println(JSONUtils.toString(service.findBannerTargetByOrganId(1167)));
    }
}
