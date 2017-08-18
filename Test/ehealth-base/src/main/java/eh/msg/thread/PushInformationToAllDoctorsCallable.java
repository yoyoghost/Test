package eh.msg.thread;

import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import eh.base.dao.DoctorDAO;
import eh.msg.service.MessagePushService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 推送医生资讯
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/6/15.
 */
public class PushInformationToAllDoctorsCallable implements Callable<String> {

    private Log logger = LogFactory.getLog(PushInformationToAllDoctorsCallable.class);

    private int start;

    private int limit;

    private HashMap<String, Object> map;

    private String content;

    public PushInformationToAllDoctorsCallable(int start, int limit, HashMap<String, Object> map, String content) {
        this.start = start;
        this.limit = limit;
        this.map = map;
        this.content = content;
    }

    @Override
    public String call() throws Exception {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        MessagePushService pushService= AppDomainContext.getBean("messagePushService",MessagePushService.class);
        List<String> mobiles = doctorDAO.findDotorMobileForPage(this.start,this.limit);
        //logger.info("PushInformationToAllDoctorsCallable send num ["+mobiles.size()+"]");
        int sendCount = 0;
        if(null != mobiles && !mobiles.isEmpty()) {
            for(String mobile : mobiles) {
                if(StringUtils.isNotEmpty(mobile)) {
                    sendCount++;
                    pushService.pushMsg(mobile, content, map);
                }
            }
        }
        logger.info("PushInformationToAllDoctorsCallable finish send num ["+sendCount+"]" );
        return null;
    }
}
