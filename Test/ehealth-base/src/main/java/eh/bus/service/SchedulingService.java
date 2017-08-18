package eh.bus.service;

import com.google.common.collect.Lists;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointScheduleDAO;
import eh.entity.bus.AppointSchedule;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by xuqh on 2016/5/30.
 */
public class SchedulingService extends HibernateSupportDelegateDAO<AppointSchedule> {
    private static final Logger log = Logger.getLogger(SchedulingService.class);

    /**
     * 取排班信息
     * @param param
     * @return
     */
    @RpcService
    public  String getDoctorSchedule(String param) {

        AppointScheduleDAO asDAO = DAOFactory.getDAO( AppointScheduleDAO.class );
        List<AppointSchedule> listAppointSchedule = Lists.newArrayList();
        AppointSchedule as = new AppointSchedule();
        String organID = null;
        String startTime = null;
        String endTime = null;
        Map<String, Object> parse = JSONUtils.parse( param, Map.class );
        Set<Map.Entry<String, Object>> entryset = parse.entrySet();
        for (Iterator<Map.Entry<String, Object>> info = entryset.iterator(); info.hasNext(); ) {
            Map.Entry<String, Object> e = info.next();
            if ("OrganID".equals( e.getKey() )) {
                organID = e.getValue().toString();
            }
            if ("StartTime".equals( e.getKey() )) {
                startTime = e.getValue().toString();
            }
            if ("EndTime".equals( e.getKey() )) {
                endTime =  e.getValue().toString();
            }
        }
        listAppointSchedule = asDAO.getScheduling( organID, startTime, endTime );
        return JSONUtils.writeValueAsString(listAppointSchedule).toString();
    }
}
