package eh.bus.his.service;

import com.ngari.his.appoint.mode.QueryOrderNumRequestTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import eh.base.constant.ErrorCode;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.QueryOrderNumRequest;
import eh.entity.bus.QueryOrderNumResponse;
import eh.remote.IHisServiceInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Created by zhongzx on 2016/8/1 0001.
 * 排队叫号 查询当前就诊顺序数
 */
public class QueryOrderNumService {

    private static final Log log = LogFactory.getLog(QueryOrderNumService.class);
    
    /**
     * zhongzx
     * 排队叫号 查询当前就诊顺序数
     * @param organID
     * @param appointDepartCode
     * @param jobNumber
     */
    public Integer queryOrderNum(Integer organID, String appointDepartCode, String jobNumber){
        log.info("orgaId:="+organID + "appointDepartCode:="+appointDepartCode + "jobNumber:="+jobNumber);
        //预约科室代码必传 医生工号可以为空 如果为空 查询的是整个科室的
        if(StringUtils.isEmpty(appointDepartCode)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "appointDepartCode is needed");
        }
        try{
            HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisDao.getByOrganId(organID);
            String hisServiceId = cfg.getAppDomainId() + ".queryOrderNumService";
            //调用 his 查询当前就诊顺序数
            boolean s = DBParamLoaderUtil.getOrganSwich(organID);
            Integer o = 0;
            if(s){
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                o = appointService.queryOrderNum(appointDepartCode,jobNumber,organID);
            }else{
                o = (Integer) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryOrderNum", appointDepartCode, jobNumber);
            }
            return o;
        }catch (Exception e){
            log.error("调用前置机服务失败" + e.getMessage());
        }
        return null;
    }


    public QueryOrderNumResponse queryOrderNumNew(QueryOrderNumRequest request){

        try{
            HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisDao.getByOrganId(request.getOrganID());
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".queryOrderNumService";
            //调用 his 查询当前就诊顺序数
            boolean s = DBParamLoaderUtil.getOrganSwich(request.getOrganID());
            Object res = null;
            if(s){
            	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
                QueryOrderNumRequestTO to = new QueryOrderNumRequestTO();
                BeanUtils.copy(request,to);
                appointService.queryOrderNumNew(to);
            }else{
                res = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryOrderNumNew", request);

            }
            if(res==null){
              return null;
            }else
                return (QueryOrderNumResponse)res;
        }catch (Exception e){
            log.error("查询叫号失败" + e.getMessage());
        }
        return null;
    }

}
