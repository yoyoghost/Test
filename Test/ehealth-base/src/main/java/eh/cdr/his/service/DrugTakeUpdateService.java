package eh.cdr.his.service;

import com.ngari.his.recipe.mode.DrugTakeChangeReqTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.DrugTakeChangeReq;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by zhongzx on 2016/6/17 0017.
 *  云平台更改处方取药方式后同步更改HIS系统处方取药方式。
 */
public class DrugTakeUpdateService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(DrugTakeUpdateService.class);

    public Boolean drugTakeUpdate(DrugTakeChangeReq request) {
        try {
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            Integer organID = Integer.valueOf(request.getOrganID());
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID.intValue());
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".drugTakeService";
            logger.info("drugTakeUpdate request={}", JSONUtils.toString(request));
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	DrugTakeChangeReqTO reqTO = new DrugTakeChangeReqTO();
        		BeanUtils.copy(request,reqTO);
        		return iRecipeHisService.drugTakeChange(reqTO);
        	}else{
        		return (Boolean)RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"drugTakeChange",request);
        	}
        } catch (Exception e) {
            logger.error("drugTakeUpdate HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return false;
    }
}
