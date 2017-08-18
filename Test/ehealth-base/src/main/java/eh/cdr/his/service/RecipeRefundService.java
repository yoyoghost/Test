package eh.cdr.his.service;

import com.ngari.his.recipe.mode.RecipeRefundReqTO;
import com.ngari.his.recipe.mode.RecipeRefundResTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.RecipeRefundReq;
import eh.entity.his.RecipeRefundRes;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * HIS退款接口
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/11/2.
 */
public class RecipeRefundService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeRefundService.class);

    public RecipeRefundRes refund(RecipeRefundReq request) {
        try {
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.valueOf(request.getOrganID()));
            RecipeRefundRes response = null;
            if(null != cfg) {
                //调用服务id
                String hisServiceId = cfg.getAppDomainId() + ".recipeRefundService";
                logger.info("refund request={}", JSONUtils.toString(request));
                if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){
                	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
                	RecipeRefundResTO resTO = new RecipeRefundResTO();
                	RecipeRefundReqTO reqTO = new RecipeRefundReqTO();
            		BeanUtils.copy(request,reqTO);
            		resTO = iRecipeHisService.recipeRefund(reqTO);
                    response=new RecipeRefundRes();
            		BeanUtils.copy(resTO, response);
            	}else{
            		response = (RecipeRefundRes) RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class, hisServiceId, "recipeRefund", request);
            	}
                logger.info("refund dao={}", JSONUtils.toString(response));
            }else{
                logger.error("refund organId=[{}]没有配置hisServiceConfig", request.getOrganID());
            }
            return response;
        } catch (Exception e) {
            logger.error("refund HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return null;
    }
}
