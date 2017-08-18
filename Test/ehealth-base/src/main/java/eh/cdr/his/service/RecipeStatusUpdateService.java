package eh.cdr.his.service;

import com.ngari.his.recipe.mode.RecipeStatusUpdateReqTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.RecipeStatusUpdateReq;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by zhongzx on 2016/6/17 0017.
 * 云平台处方状态改变同步更新HIS系统处方状态。
 */
public class RecipeStatusUpdateService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeStatusUpdateService.class);

    public Boolean recipeStatusUpdate(RecipeStatusUpdateReq request) {
        try {
            Integer organID = Integer.valueOf(request.getOrganID());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".recipeUpdateService";
            logger.info("recipeStatusUpdate request={}", JSONUtils.toString(request));
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	RecipeStatusUpdateReqTO reqTO = new RecipeStatusUpdateReqTO();
        		BeanUtils.copy(request,reqTO);
        		return iRecipeHisService.recipeUpdate(reqTO);
        	}else{
        		return (boolean) RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"recipeUpdate",request);
        	}            
        } catch (Exception e) {
            logger.error("recipeStatusUpdate HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return false;
    }
}
