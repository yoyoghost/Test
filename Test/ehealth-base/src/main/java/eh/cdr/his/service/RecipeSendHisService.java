package eh.cdr.his.service;

import com.google.common.collect.Lists;
import com.ngari.his.recipe.mode.RecipeSendRequestTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.HisServiceConfigDAO;
import eh.cdr.bean.RecipeCheckPassResult;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.service.HisCallBackService;
import eh.cdr.service.RecipeLogService;
import eh.cdr.thread.RecipeBusiThreadPool;
import eh.entity.base.HisServiceConfig;
import eh.entity.cdr.Recipedetail;
import eh.entity.his.OrderRep;
import eh.entity.his.RecipeSendRequest;
import eh.entity.his.RecipeSendResponse;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by zhongzx on 2016/6/14 0014.
 * 根据云平台的处方信息在HIS系统生成电子处方。
 */
public class RecipeSendHisService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeSendHisService.class);

    public void recipeSendHis(final RecipeSendRequest request){

        RecipeBusiThreadPool pool = new RecipeBusiThreadPool(new Runnable() {

            private int count = 0;

            private RecipeSendRequest _request;

            @Override
            public void run() {
                    _request = request;
                    HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    Integer organId = Integer.valueOf(_request.getOrganID());
                    HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
                    //调用服务id
                    String hisServiceId = cfg.getAppDomainId() + ".recipeSendService";
                    logger.info("RecipeSendHisService request={}", JSONUtils.toString(_request));
                    RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                    if (count <= 5) {
                        count++;
                        try {
                            if (count <= 1) {
                                recipeDAO.updateRecipeInfoByRecipeId(Integer.valueOf(_request.getRecipeID()), RecipeStatusConstant.CHECKING_HOS, null);
                            }
                        	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(_request.getOrganID()))){
                        		IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
                        		RecipeSendRequestTO reqTO= new RecipeSendRequestTO();
                        		BeanUtils.copy(_request,reqTO);
                        		iRecipeHisService.recipeSend(reqTO);
                        	}else{
                        		RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"recipeSend",_request);
                        	}                        	
                            logger.info("RecipeSendHisService 调用前置机处方写入服务成功! recipeId:"+Integer.valueOf(_request.getRecipeID()));
                        } catch (Exception e) {
                            logger.error("RecipeSendHisService 处方写入服务错误：发起重试" + e.getMessage());
                            run();
                        }
                    } else {
                        //失败发送系统消息
                        recipeDAO.updateRecipeInfoByRecipeId(Integer.valueOf(_request.getRecipeID()), RecipeStatusConstant.HIS_FAIL, null);
                        //日志记录
                        RecipeLogService.saveRecipeLog(Integer.valueOf(_request.getRecipeID()),RecipeStatusConstant.CHECK_PASS,
                                RecipeStatusConstant.HIS_FAIL,"his写入失败，调用前置机处方写入服务超过重试次数");
                        logger.error("RecipeSendHisService 调用前置机处方写入服务超过重试次数!");
                    }
                }
        });

        try {
            pool.execute();
        }catch (Exception e){
            logger.error("RecipeSendHisService 线程池异常errMsg={}", e.getMessage());
        }
    }

    /**
     * @param response
     * @return his返回结果消息
     */
    @RpcService
    public void sendSuccess(RecipeSendResponse response) {
        logger.info("RecipeSendHisService recive success. dao={}", JSONUtils.toString(response));
        List<OrderRep> repList = response.getData();
        if (null != repList && repList.size() > 0) {
            RecipeCheckPassResult rcr = new RecipeCheckPassResult();
            List<Recipedetail> list = Lists.newArrayList();

            //以下数据取第一个数据是因为这些数据是处方相关的
            String recipeNo = repList.get(0).getRecipeNo();
            String patientId = repList.get(0).getPatientID();
            String amount = repList.get(0).getAmount();

            for(OrderRep rep:repList) {
                Recipedetail rp = new Recipedetail();
                String orderNo = rep.getOrderNo();
                String setNo = rep.getSetNo();
                if(!StringUtils.isEmpty(rep.getPrice())) {
                    BigDecimal price = new BigDecimal(rep.getPrice());
                    rp.setDrugCost(price);
                }
                String pharmNo = rep.getPharmNo();
                String remark = rep.getRemark();

                rp.setRecipeDetailId(Integer.valueOf(rep.getOrderID()));
                rp.setOrderNo(orderNo);
                rp.setDrugGroup(setNo);
                rp.setPharmNo(pharmNo);   //取药窗口是否都是返回同一窗口
                rp.setMemo(remark);     //
                list.add(rp);
            }
            if(!StringUtils.isEmpty(amount)) {
                BigDecimal total = new BigDecimal(amount);
                rcr.setTotalMoney(total);
            }
            rcr.setRecipeId(Integer.valueOf(response.getRecipeId()));
            rcr.setRecipeCode(recipeNo);
            rcr.setPatientID(patientId);
            rcr.setDetailList(list);
            HisCallBackService.checkPassSuccess(rcr, true);
        }
    }


    /**
     * 该处方单发送his系统失败并给医生发送推送和系统消息通知。
     * @param response
     */
    @RpcService
    public void sendFail(final RecipeSendResponse response) {
        logger.error("RecipeSendHisService recive fail. dao={}", JSONUtils.toString(response));
        // 给申请医生，患者发送推送消息
        HisCallBackService.checkPassFail(Integer.valueOf(response.getRecipeId()), response.getMsgCode(),response.getMsg());
    }
}
