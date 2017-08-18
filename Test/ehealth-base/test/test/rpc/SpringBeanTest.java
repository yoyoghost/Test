package test.rpc;

import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.HisServiceConfig;
import eh.remote.IHisServiceInterface;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2016-5-6.
 */
public class SpringBeanTest extends TestCase {

    private static final Log logger = LogFactory.getLog(SpringBeanTest.class);
    private static ClassPathXmlApplicationContext appContext;
    private static AppointSourceDAO bdd;
    static DefaultListableBeanFactory acf = null;
    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
        acf = (DefaultListableBeanFactory) appContext.getAutowireCapableBeanFactory();
    }

    /**
     * 调用rpc时  修改springbean的url rpc重新指向数据库配置的url
     **/
    public void testModify() {
//		ApplicationContext ctx = new ClassPathXmlApplicationContext("spring.xml");
        //从数据库配置读取beanName（serviceID）
        String id = "h1000007.appointmentService";
        String dbUrl = "tcp://121.43.186.171:9005?codec=hessian";
        BeanDefinition b1 = appContext.getBeanFactory().getBeanDefinition(id);
        MutablePropertyValues prov = b1.getPropertyValues();
        ArrayList remoteUrls = (ArrayList) prov.get("remoteUrls");
        String url = (String) remoteUrls.get(0);
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(dbUrl) || dbUrl.equals(url)) {
            return;
        } else {
            url = dbUrl;
            List<String> ls = new ArrayList<>();
            ls.add(dbUrl);
            prov.add("remoteUrls", ls);
            prov.add("id", id);
            prov.add("interface", "eh.remote.IHisServiceInterface");
            acf.registerBeanDefinition(id, b1);
        }
        BeanDefinition b2 = appContext.getBeanFactory().getBeanDefinition(id);

        IHisServiceInterface appointService = AppContextHolder.getBean(id, IHisServiceInterface.class);
        String res = appointService.testRpc();
//		AppointmentRequest req = new AppointmentRequest();
//		req.setId("111");
//		appointService.registAppoint( req);
        System.out.println(res);


    }

    public void testCreat() {
//		ApplicationContext ctx = new ClassPathXmlApplicationContext("spring.xml");
        String id = "h1000007.appointmentService";
        RootBeanDefinition refDef = new RootBeanDefinition();
        refDef.setBeanClass(ctd.net.rpc.beans.DirectReferenceBean.class);
        refDef.setInitMethodName("init");
        MutablePropertyValues pv = refDef.getPropertyValues();
        List<String> ls = new ArrayList<>();
        ls.add("tcp://121.43.186.171:9005?codec=hessian");
        pv.add("remoteUrls", ls);
        pv.add("id", id);
        pv.add("interface", "eh.remote.IHisServiceInterface");
        acf.registerBeanDefinition(id, refDef);
        acf.getBean(id);
        IHisServiceInterface appointService = AppContextHolder.getBean(id, IHisServiceInterface.class);
        String res = appointService.testRpc();
//		BeanDefinition ddd = appContext.getBeanFactory().getBeanDefinition(id);
//		IHisServiceInterface appointService = AppContextHolder.getBean(id, IHisServiceInterface.class);
        System.out.println(res);
//
//		BeanDefinition b1 = appContext.getBeanFactory().getBeanDefinition(domainBeanName);
//		Object b2 = appContext.getBeanFactory().getBean(domainBeanName);
//		Assert.assertNotNull(b1);


    }


//
//    public void testService() {
//        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
//        HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(1000007);
//        String hisServiceId =cfg.getAppDomainId()+".appointmentService";//调用服务id
//        String dbUrl = cfg.getRemoteurl();
//        if(StringUtils.isEmpty(dbUrl)){
//            logger.error(hisServiceId+"数据库的his地址为空！请配置！");
//        }
//        if (acf.containsBean(hisServiceId)){
//            BeanDefinition b1 = appContext.getBeanFactory().getBeanDefinition(hisServiceId);
//            MutablePropertyValues prov = b1.getPropertyValues();
//            ArrayList remoteUrls = (ArrayList) prov.get("remoteUrls");
//            String beanUrl = (String) remoteUrls.get(0);
//            //如果springBean中的实例url和数据库不一样，则更新bean的url
//            if(!dbUrl.equals(beanUrl)){
//                beanUrl = dbUrl;
//                List<String> ls = new ArrayList<>();
//                ls.add(dbUrl);
//                prov.add("remoteUrls", ls);
//                prov.add("id", hisServiceId);
//                prov.add("interface", "eh.remote.IHisServiceInterface");
//                acf.registerBeanDefinition(hisServiceId, b1);
//            }
//            //一样的话不处理
//
//        }else {
//            logger.info("创建bean:"+hisServiceId+"|url:"+dbUrl);
//            RootBeanDefinition refDef = new RootBeanDefinition();
//            refDef.setBeanClass(ctd.net.rpc.beans.DirectReferenceBean.class);
//            refDef.setInitMethodName("init");
//            MutablePropertyValues pv = refDef.getPropertyValues();
//            List<String> ls = new ArrayList<>();
//            ls.add(dbUrl);
//            pv.add("remoteUrls", ls);
//            pv.add("id", hisServiceId);
//            pv.add("interface", "eh.remote.IHisServiceInterface");
//            acf.registerBeanDefinition(hisServiceId, refDef);
//        }
//        IHisServiceInterface appointService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);
//        String res = appointService.testRpc();
//
//    }
//
//
//
    public void testService2() {

        ApplicationContext a = AppDomainContext.get();
        acf = (DefaultListableBeanFactory) a.getAutowireCapableBeanFactory();
        //机构
        int organId = 1000007;
        //配置表
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(organId);
        String hisServiceId =cfg.getAppDomainId()+".appointmentService";//调用服务id
//        String dbUrl = cfg.getRemoteurl();//添加url字段 从数据库配置
        String dbUrl = "";//添加url字段 从数据库配置
        if(StringUtils.isEmpty(dbUrl)){
            logger.error(hisServiceId+"数据库的his地址为空！请配置！");
        }
        if (AppContextHolder.containBean(hisServiceId)){
            BeanDefinition b1 = appContext.getBeanFactory().getBeanDefinition(hisServiceId);
            MutablePropertyValues prov = b1.getPropertyValues();
            ArrayList remoteUrls = (ArrayList) prov.get("remoteUrls");
            String beanUrl = (String) remoteUrls.get(0);
            //如果springBean中的实例url和数据库不一样，则更新bean的url
            if(!dbUrl.equals(beanUrl)){
                beanUrl = dbUrl;
                List<String> ls = new ArrayList<>();
                ls.add(dbUrl);
                prov.add("remoteUrls", ls);
                prov.add("id", hisServiceId);
                prov.add("interface", "eh.remote.IHisServiceInterface");
                acf.registerBeanDefinition(hisServiceId, b1);
                logger.info("修改bean: "+hisServiceId+" | url:"+dbUrl);
            }
            //一样的话不处理

        }else {
            logger.info("创建bean: "+hisServiceId+" | url:"+dbUrl);
            RootBeanDefinition refDef = new RootBeanDefinition();
            refDef.setBeanClass(ctd.net.rpc.beans.DirectReferenceBean.class);
            refDef.setInitMethodName("init");
            MutablePropertyValues pv = refDef.getPropertyValues();
            List<String> ls = new ArrayList<>();
            ls.add(dbUrl);
            pv.add("remoteUrls", ls);
            pv.add("id", hisServiceId);
            pv.add("interface", "eh.remote.IHisServiceInterface");
            acf.registerBeanDefinition(hisServiceId, refDef);
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("remoteUrls", ls);
            properties.put("id", hisServiceId);
            properties.put("interface", "eh.remote.IHisServiceInterface");
            AppContextHolder.addBean(hisServiceId,ctd.net.rpc.beans.DirectReferenceBean.class,properties);
            acf.getBean(hisServiceId);
        }
        IHisServiceInterface appointService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);//null
        String res = appointService.testRpc();

    }
}
