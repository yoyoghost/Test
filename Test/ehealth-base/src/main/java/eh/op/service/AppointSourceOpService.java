package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.Organ;
import eh.utils.MapValueUtil;

/**
 * Created by andywang on 2017/6/19.
 */
public class AppointSourceOpService {

    @RpcService
    public Integer deleteUnUsedSourceByOrgan(final Integer organId){
        AppointSourceDAO ad = DAOFactory.getDAO(AppointSourceDAO.class);
        Integer count = ad.deleteUnUsedSourceByOrgan(organId);
        if (organId != null) {
            OrganDAO od = DAOFactory.getDAO(OrganDAO.class);
            Organ o = od.get(organId);
            if (o !=null) {
                String message = "批量删除"+ o.getShortName() + "[" + organId+  "]" + "未使用医生号源. 总计： " + count;
                BusActionLogService.recordBusinessLog("医生号源", organId.toString(), "Organ", message);
            }
        }
        return count;
    }

    @RpcService
    public Integer recoverSourceByOrganAndSchedule(final Integer organId, final String organSchedulingId){
        AppointSourceDAO ad = DAOFactory.getDAO(AppointSourceDAO.class);
        Integer count = ad.recoverSourceByOrganAndSchedule(organId,organSchedulingId);
        if (organId != null) {
            OrganDAO od = DAOFactory.getDAO(OrganDAO.class);
            Organ o = od.get(organId);
            if (o !=null) {
                String message = "批量恢复"+ o.getShortName() + "[" + organId+  "]" + " SchedulingID: " + organSchedulingId + " 医生号源, 总计： " + count;
                BusActionLogService.recordBusinessLog("医生号源", organId.toString(), "Organ", message);
            }
        }
        return count;
    }
}
