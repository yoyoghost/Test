package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.dao.LogonLogDAO;
import eh.entity.base.LogonLog;

import java.util.Date;

/**
 * @author jianghc
 * @create 2017-03-16 14:26
 **/
public class LogonLogService {
    private LogonLogDAO logonLogDAO;
    public LogonLogService() {
        logonLogDAO = DAOFactory.getDAO(LogonLogDAO.class);
    }

    @RpcService
    public QueryResult<LogonLog>  queryDocLogonLog(Integer organId,Integer urt,Date bDate,Date eDate,int start,int limit){
        return logonLogDAO.queryLogonLog(SystemConstant.ROLES_DOCTOR,organId,urt,bDate,eDate,start,limit);
    }

    @RpcService
    public QueryResult<LogonLog>  queryPatLogonLog(Integer urt,Date bDate,Date eDate,int start,int limit){
        return logonLogDAO.queryLogonLog(SystemConstant.ROLES_PATIENT,null,urt,bDate,eDate,start,limit);
    }

}
