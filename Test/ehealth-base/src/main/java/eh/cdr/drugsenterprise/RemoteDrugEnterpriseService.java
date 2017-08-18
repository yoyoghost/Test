package eh.cdr.drugsenterprise;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.SaleDrugListDAO;
import eh.cdr.bean.DepDetailBean;
import eh.cdr.bean.DrugEnterpriseResult;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.DrugsEnterpriseDAO;
import eh.cdr.dao.RecipeOrderDAO;
import eh.entity.cdr.DrugsEnterprise;
import eh.entity.cdr.Recipe;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static ctd.util.AppContextHolder.getBean;

/**
 * 业务使用药企对接类，具体实现在CommonRemoteService
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/3/7.
 */
public class RemoteDrugEnterpriseService {

    private static final Logger logger = LoggerFactory.getLogger(RemoteDrugEnterpriseService.class);

    private static final String COMMON_SERVICE = "commonRemoteService";

    /**
     * 推送处方
     * @param recipeId 处方ID集合
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushSingleRecipeInfo(Integer recipeId){
        DrugEnterpriseResult result = getServiceByRecipeId(recipeId);
        DrugsEnterprise enterprise = result.getDrugsEnterprise();
        if(DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()){
            result = result.getAccessDrugEnterpriseService().pushRecipeInfo(Collections.singletonList(recipeId));
            if(DrugEnterpriseResult.SUCCESS.equals(result.getCode())){
                result.setDrugsEnterprise(enterprise);
            }
        }
        logger.info("pushSingleRecipeInfo recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    /**
     * 带药企ID进行推送
     * @param recipeId
     * @param depId
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushSingleRecipeInfoWithDepId(Integer recipeId, Integer depId){
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        if(null != depId) {
            DrugsEnterprise dep = drugsEnterpriseDAO.get(depId);
            if(null != dep){
                result.setAccessDrugEnterpriseService(this.getServiceByDep(dep));
            }else{
                result.setCode(DrugEnterpriseResult.FAIL);
                result.setMsg("药企"+depId+"未找到");
            }
        }else{
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg("处方单"+recipeId+"未分配药企");
        }

        if(DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()){
            result = result.getAccessDrugEnterpriseService().pushRecipeInfo(Collections.singletonList(recipeId));
        }
        logger.info("pushSingleRecipeInfoWithDepId recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    /**
     * 库存检验
     * @param recipeId 处方ID
     * @param drugsEnterprise 药企
     * @return
     */
    @RpcService
    public boolean scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise){
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        AccessDrugEnterpriseService drugEnterpriseService = null;
        if(null == drugsEnterprise){
            //药企对象为空，则通过处方id获取相应药企实现
            DrugEnterpriseResult _result = this.getServiceByRecipeId(recipeId);
            if(DrugEnterpriseResult.SUCCESS.equals(_result.getCode())){
                drugEnterpriseService = _result.getAccessDrugEnterpriseService();
                drugsEnterprise = _result.getDrugsEnterprise();
            }
        }else {
            drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
        }

        if(null != drugEnterpriseService){
            result = drugEnterpriseService.scanStock(recipeId,drugsEnterprise);
        }
        logger.info("scanStock recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result.getCode().equals(DrugEnterpriseResult.SUCCESS)?true:false;
    }


    /**
     * 药师审核通过通知消息
     * @param recipeId 处方ID
     * @param checkFlag 审核结果
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag){
        DrugEnterpriseResult result = getServiceByRecipeId(recipeId);
        if(DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()){
            result = result.getAccessDrugEnterpriseService().pushCheckResult(recipeId, checkFlag);
        }
        logger.info("pushCheckResult recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    /**
     * 查找供应商
     * @param recipeId
     * @return
     */
    @RpcService
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, DrugsEnterprise drugsEnterprise){
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        if(CollectionUtils.isNotEmpty(recipeIds) && null != drugsEnterprise){
            AccessDrugEnterpriseService drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
            result = drugEnterpriseService.findSupportDep(recipeIds);
            logger.info("findSupportDep recipeIds={}, DrugEnterpriseResult={}", JSONUtils.toString(recipeIds), JSONUtils.toString(result));
        }else{
            logger.error("findSupportDep param error. recipeIds={}, drugsEnterprise={}", JSONUtils.toString(recipeIds), JSONUtils.toString(drugsEnterprise));
        }

        return result;
    }

    /**
     * 药品库存同步
     * @return
     */
    @RpcService
    public DrugEnterpriseResult syncEnterpriseDrug(){
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);

        List<DrugsEnterprise> drugsEnterpriseList = drugsEnterpriseDAO.findAllDrugsEnterpriseByStatus(1);
        if (CollectionUtils.isNotEmpty(drugsEnterpriseList)) {
            AccessDrugEnterpriseService drugEnterpriseService;
            for (DrugsEnterprise drugsEnterprise : drugsEnterpriseList) {
                if (null != drugsEnterprise) {
                    List<Integer> drugIdList = saleDrugListDAO.findSynchroDrug(drugsEnterprise.getId());
                    if (CollectionUtils.isNotEmpty(drugIdList)) {
                        drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
                        if(null != drugEnterpriseService){
                            logger.info("syncDrugTask 开始同步药企[{}]药品，药品数量[{}]",drugsEnterprise.getName(),drugIdList.size());
                            drugEnterpriseService.syncEnterpriseDrug(drugsEnterprise, drugIdList);
                        }
                    }else{
                        logger.error("syncDrugTask 药企[{}]无可同步药品.",drugsEnterprise.getName());
                    }
                }
            }
        }

        return result;
    }


    @RpcService
    public void updateAccessTokenById(Integer code , Integer depId){
        AccessDrugEnterpriseService drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
        drugEnterpriseService.updateAccessTokenById(code,depId);
    }

    public String updateAccessToken(List<Integer> drugsEnterpriseIds){
        AccessDrugEnterpriseService drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
        return drugEnterpriseService.updateAccessToken(drugsEnterpriseIds);
    }

    /**
     * 根据单个处方ID获取具体药企实现
     * @param recipeId
     * @return
     */
    public DrugEnterpriseResult getServiceByRecipeId(Integer recipeId){
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        if(null == recipeId){
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg("处方ID为空");
        }

        if(DrugEnterpriseResult.SUCCESS.equals(result.getCode())){
            DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            //PS:药企ID取的是订单表的药企ID
            Integer depId = recipeOrderDAO.getEnterpriseIdByRecipeId(recipeId);
            if(null != depId) {
                DrugsEnterprise dep = drugsEnterpriseDAO.get(depId);
                if(null != dep){
                    result.setAccessDrugEnterpriseService(this.getServiceByDep(dep));
                    result.setDrugsEnterprise(dep);
                }else{
                    result.setCode(DrugEnterpriseResult.FAIL);
                    result.setMsg("药企"+depId+"未找到");
                }
            }else{
                result.setCode(DrugEnterpriseResult.FAIL);
                result.setMsg("处方单"+recipeId+"未分配药企");
            }
        }

        logger.info("getServiceByRecipeId recipeId:{}, result:{}", recipeId, result.toString());
        return result;
    }

    /**
     * 通过药企实例获取具体实现
     * @param drugsEnterprise
     * @return
     */
    public AccessDrugEnterpriseService getServiceByDep(DrugsEnterprise drugsEnterprise){
        AccessDrugEnterpriseService drugEnterpriseService = null;
        if(null != drugsEnterprise){
            //药企帐号，通过该字段获取实现类
            String account = drugsEnterprise.getAccount();
            StringBuilder beanName = new StringBuilder();
            if(StringUtils.isNotEmpty(account)){
                beanName.append(account+"RemoteService");
            }else{
                beanName.append(COMMON_SERVICE);
            }
            try {
                logger.info("getServiceByDep 获取[{}]协议实现.service=[{}]",drugsEnterprise.getName(),beanName);
                drugEnterpriseService = getBean(beanName.toString(), AccessDrugEnterpriseService.class);
            } catch (Exception e) {
                logger.error("未找到[{}]药企实现，使用通用协议处理. beanName={}",drugsEnterprise.getName(),beanName.toString());
                drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
            }
        }

        return drugEnterpriseService;
    }

    /**
     * 获取药企帐号
     * @param depId
     * @return
     */
    public String getDepAccount(Integer depId){
        if(null == depId){
            return null;
        }
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        return drugsEnterpriseDAO.getAccountById(depId);
    }

    /**
     * 获取钥世圈订单详情URL
     * @param recipe
     * @return
     */
    public String getYsqOrderInfoUrl(Recipe recipe){
        String backUrl = "";
        String ysqUrl = ParamUtils.getParam(ParameterConstant.KEY_YSQ_SKIP_URL);
        if(RecipeStatusConstant.FINISH != recipe.getStatus()){
            backUrl = ysqUrl + "Order/Index?id=0&inbillno=" + recipe.getClinicOrgan() + YsqRemoteService.YSQ_SPLIT + recipe.getRecipeCode();
        }
        return backUrl;
    }
}
