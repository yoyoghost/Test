package eh.cdr.his.service;

import com.ngari.his.recipe.mode.RecipeListQueryReqTO;
import com.ngari.his.recipe.mode.RecipeListQueryResTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.service.HisCallBackService;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.QueryRep;
import eh.entity.his.RecipeListQueryReq;
import eh.entity.his.RecipeListQueryRes;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhongzx on 2016/6/16 0016.
 * 纳里平台向HIS系统发起处方列表查询，HIS系统返回有效期内的处方列表。
 */
public class RecipeListQueryService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeListQueryService.class);

    public void recipeListQuery(RecipeListQueryReq request) {
        try {
            Integer organID = Integer.valueOf(request.getOrganID());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".recipeListService";
            logger.info("recipeListQuery request={}", JSONUtils.toString(request));
            RecipeListQueryRes response = null;
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){ 
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	RecipeListQueryResTO resTO = new RecipeListQueryResTO();
            	RecipeListQueryReqTO reqTO = new RecipeListQueryReqTO();
        		BeanUtils.copy(request,reqTO);
        		resTO = iRecipeHisService.listQuery(reqTO);
                response=new RecipeListQueryRes();
        		BeanUtils.copy(resTO, response);
        	}else{
        		response = (RecipeListQueryRes)RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"listQuery",request);
        	}
            logger.info("recipeListQuery dao={}", JSONUtils.toString(response));
            resolveResponse(response);
        } catch (Exception e) {
            logger.error("recipeListQuery HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
    }

    /**
     * 查询单个处方在HIS中的状态，返回处方在业务中的状态
     * @param request
     * @return
     */
    public Integer recipeSingleQuery(RecipeListQueryReq request){
        if(null == request){
            return null;
        }

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);

        if(StringUtils.isNotEmpty(request.getOrganID())) {
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.valueOf(request.getOrganID()));
            if(null != cfg && StringUtils.isNotEmpty(cfg.getAppDomainId())) {
                //调用服务id
                String hisServiceId = cfg.getAppDomainId() + ".recipeListService";
                logger.info("recipeListQuery request={}", JSONUtils.toString(request));
                try {
                	RecipeListQueryRes response = null;
                    if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){ 
                    	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
                    	RecipeListQueryResTO resTO = new RecipeListQueryResTO();
                    	RecipeListQueryReqTO reqTO = new RecipeListQueryReqTO();
                		BeanUtils.copy(request,reqTO);
                		resTO = iRecipeHisService.listQuery(reqTO);
                        response=new RecipeListQueryRes();
                		BeanUtils.copy(resTO, response);
                	}else{
                		response = (RecipeListQueryRes) RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class, hisServiceId, "listQuery", request);
                	}
                    logger.info("recipeListQuery dao={}", JSONUtils.toString(response));
                    return resolveSingleResponse(response);
                } catch (Exception e) {
                    logger.error("recipeSingleQuery 调用 处方列表查询his服务出错==========="+e.getMessage());
                }
            }
        }

        return null;
    }

    //解析 处理 his 返回结果
    public void resolveResponse(RecipeListQueryRes response){
        if(null == response || null == response.getMsgCode()){
            return;
        }
        List<QueryRep> list = response.getData();
        List<String> payList = new ArrayList<>();
        List<String> finishList = new ArrayList<>();
        Integer organId = Integer.valueOf(response.getOrganID());

        for(QueryRep rep:list){
            Integer isPay = Integer.valueOf(rep.getIsPay());
            Integer recipeStatus = Integer.valueOf(rep.getRecipeStatus());
            Integer phStatus = Integer.valueOf(rep.getPhStatus());
            if(recipeStatus == 1) {
                //有效的处方单已支付 未发药 为已支付状态
                if(isPay == 1 && phStatus == 0) {
                    payList.add(rep.getRecipeNo());
                }
                //有效的处方单已支付 已发药 为已完成状态
                if(isPay == 1 && phStatus == 1){
                    finishList.add(rep.getRecipeNo());
                }
            }
        }

        if(CollectionUtils.isNotEmpty(payList)) {
            HisCallBackService.havePayRecipesFromHis(payList, organId);
        }

        if(CollectionUtils.isNotEmpty(finishList)) {
            HisCallBackService.finishRecipesFromHis(finishList, organId);
        }
    }

    private Integer resolveSingleResponse(RecipeListQueryRes response){
        Integer busStatus = null;
        if(null == response || null == response.getMsgCode()){
            return busStatus;
        }

        List<QueryRep> list = response.getData();

        if(StringUtils.isNotEmpty(response.getOrganID()) && CollectionUtils.isNotEmpty(list)){
            List<String> payList = new ArrayList<>();
            List<String> finishList = new ArrayList<>();

            QueryRep rep = list.get(0);
            if(null != rep){
                Integer organId = Integer.valueOf(response.getOrganID());
                Integer isPay = (null == rep.getIsPay())?0:Integer.valueOf(rep.getIsPay());
                Integer recipeStatus = (null == rep.getRecipeStatus())?0:Integer.valueOf(rep.getRecipeStatus());
                Integer phStatus = (null == rep.getPhStatus())?0:Integer.valueOf(rep.getPhStatus());
                if(recipeStatus == 1) {
                    busStatus = RecipeStatusConstant.CHECK_PASS;
                    //有效的处方单已支付 未发药 为已支付状态
                    if(isPay == 1 && phStatus == 0) {
                        busStatus = RecipeStatusConstant.HAVE_PAY;
                        payList.add(rep.getRecipeNo());
                        HisCallBackService.havePayRecipesFromHis(payList, organId);
                    }
                    //有效的处方单已支付 已发药 为已完成状态
                    if(isPay == 1 && phStatus == 1){
                        busStatus = RecipeStatusConstant.FINISH;
                        finishList.add(rep.getRecipeNo());
                        HisCallBackService.finishRecipesFromHis(finishList, organId);
                    }
                }
            }
        }

        return busStatus;
    }
}
