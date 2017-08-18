package eh.cdr.his.service;

import com.ngari.his.recipe.mode.RecipeQueryReqTO;
import com.ngari.his.recipe.mode.RecipeQueryResTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.RecipeQueryReq;
import eh.entity.his.RecipeQueryRes;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by zhongzx on 2016/6/18 0018.
 *  纳里平台向HIS系统发起单张处方信息查询，HIS系统返回该处方单详细信息。
 */
public class RecipeQueryHisSerivce {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeQueryHisSerivce.class);

    public RecipeQueryRes recipeQueryHis(RecipeQueryReq request){
        try{
            Integer organID = Integer.valueOf(request.getOrganID());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".recipeQueryService";
            logger.info("recipeQueryHis request={}", JSONUtils.toString(request));
            RecipeQueryRes response = null;
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){ 
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	RecipeQueryResTO resTO = new RecipeQueryResTO();
            	RecipeQueryReqTO reqTO = new RecipeQueryReqTO();
        		BeanUtils.copy(request,reqTO);
        		resTO = iRecipeHisService.recipeQuery(reqTO);
                response=new RecipeQueryRes();
        		BeanUtils.copy(resTO, response);
        	}else{
        		response = (RecipeQueryRes)RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"recipeQuery",request);
        	}
            logger.info("recipeQueryHis dao={}", JSONUtils.toString(response));
            return response;
        }catch (Exception e){
            logger.error("recipeQueryHis HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return null;
    }
}
