package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.bus.AppointDepart;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 挂号科室维护相关服务
 * Created by houxr on 2016/5/27.
 */
public class AppointDepartOpService {
    private static final Log logger = LogFactory.getLog(AppointDepartOpService.class);

    /**
     * 由appointDepartId查询挂号科室
     *
     * @param appointDepartId
     * @return
     */
    @RpcService
    public AppointDepart getAppointDepartById(final Integer appointDepartId) {
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        AppointDepart appointDepart = appointDepartDAO.getById(appointDepartId);
        return appointDepart;
    }

    /**
     * 挂号科室更新
     *
     * @param appointDepart
     * @return
     */
    @RpcService
    public AppointDepart updateAppointDepart(AppointDepart appointDepart) {
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        if (appointDepart.getAppointDepartId() == null) {
            throw new DAOException("appointDepartId is null");
        }
        return appointDepartDAO.updateAppointDepartForOp(appointDepart);
    }

    /**
     * 运营平台--查询未与新增科室对照的挂号科室
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<AppointDepart> queryUnContrastDeparts(int start, int limit) {
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        return appointDepartDAO.queryUnContrastDeparts(start, limit);
    }
}
