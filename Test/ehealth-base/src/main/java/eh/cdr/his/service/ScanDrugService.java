package eh.cdr.his.service;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.ngari.his.recipe.mode.DrugInfoRequestTO;
import com.ngari.his.recipe.mode.DrugInfoResponseTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDrugListDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.OrganDrugList;
import eh.entity.cdr.Recipedetail;
import eh.entity.his.DrugInfo;
import eh.entity.his.DrugInfoRequest;
import eh.entity.his.DrugInfoResponse;
import eh.entity.his.OrderItem;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 医院药品库存查询
 */
public class ScanDrugService {

    private static final Logger logger = LoggerFactory.getLogger(ScanDrugService.class);

    public DrugInfoResponse scanDrugStock(List<Recipedetail> detailList, int organId) {
        if(CollectionUtils.isEmpty(detailList)){
            return null;
        }
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        OrganDrugListDAO drugDao = DAOFactory.getDAO(OrganDrugListDAO.class);
        HisServiceConfig cfg = hisServiceConfigDAO.getByOrganId(organId);

        DrugInfoRequest request = new DrugInfoRequest();
        request.setOrganId(organId);
        if (null != cfg) {
            String hisServiceId = cfg.getAppDomainId() + ".scanDrugStockService";

            List<Integer> drugIdList = FluentIterable.from(detailList).transform(new Function<Recipedetail, Integer>() {
                @Override
                public Integer apply(Recipedetail input) {
                    return input.getDrugId();
                }
            }).toList();

            List<OrganDrugList> organDrugList = drugDao.findByOrganIdAndDrugIds(organId,drugIdList);
            Map<Integer, OrganDrugList> drugIdAndProduce = Maps.uniqueIndex(organDrugList, new Function<OrganDrugList, Integer>() {
                @Override
                public Integer apply(OrganDrugList input) {
                    return input.getDrugId();
                }
            });

            List<DrugInfo> data = new ArrayList<>(detailList.size());
            DrugInfo drugInfo;
            OrganDrugList organDrug;
            for(Recipedetail detail : detailList){
                drugInfo = new DrugInfo(detail.getOrganDrugCode());
                drugInfo.setPack(detail.getPack().toString());
                drugInfo.setPackUnit(detail.getDrugUnit());
                organDrug = drugIdAndProduce.get(detail.getDrugId());
                if(null != organDrug) {
                    drugInfo.setManfcode(organDrug.getProducerCode());
                }
                data.add(drugInfo);
            }
            request.setData(data);

            DrugInfoResponse response = null;
            logger.info("scanDrugStock request={}", JSONUtils.toString(request));
            try {
            	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganId()))){ 
                	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
                	DrugInfoResponseTO resTO = new DrugInfoResponseTO();
                	DrugInfoRequestTO reqTO = new DrugInfoRequestTO();
            		BeanUtils.copy(request,reqTO);
            		resTO = iRecipeHisService.scanDrugStock(reqTO);
                    response=new DrugInfoResponse();
            		BeanUtils.copy(resTO, response);
            	}else{
            		response = (DrugInfoResponse) RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class, hisServiceId, "scanDrugStock", request);
            	} 
            } catch (Exception e) {
                logger.error("scanDrugStock HIS接口调用失败. organId=[{}], param={}",organId,JSONUtils.toString(request));
            }

            logger.info("scanDrugStock dao={}", JSONUtils.toString(response));
            if(null != response && null != response.getMsgCode()){
                return response;
            }
        }else{
            logger.error("scanDrugStock organId=[{}]没有配置hisServiceConfig", organId);
        }

        return null;
    }
}
