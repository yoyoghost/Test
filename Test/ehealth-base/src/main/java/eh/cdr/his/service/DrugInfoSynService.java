package eh.cdr.his.service;

import com.ngari.his.recipe.mode.DrugInfoRequestTO;
import com.ngari.his.recipe.mode.DrugInfoResponseTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.DrugInfo;
import eh.entity.his.DrugInfoRequest;
import eh.entity.his.DrugInfoResponse;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhongzx on 2017/3/31 0031.
 */
public class DrugInfoSynService {

    private static final Logger logger = LoggerFactory.getLogger(DrugInfoSynService.class);

    /**
     * 查询药品在医院里的信息
     * @param
     * @return
     */
    public List<DrugInfo> queryDrugInfo(List<DrugInfo> drugInfoList, int organId) {
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDAO.getByOrganId(organId);

        DrugInfoRequest request = new DrugInfoRequest();
        request.setOrganId(organId);
        if (null != cfg) {
            String hisServiceId = cfg.getAppDomainId() + ".drugInfoSynService";
            if(CollectionUtils.isEmpty(drugInfoList)){
                //查询全部药品信息，返回的是医院所有有效的药品信息

            }else{
                //查询限定范围内容的药品数据，返回的是该医院 无效的药品信息
                request.setData(drugInfoList);
            }

            DrugInfoResponse response = null;
            logger.info("queryDrugInfo request={}", JSONUtils.toString(request));
            try {
            	if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganId()))){ 
                	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
                	DrugInfoResponseTO resTO = new DrugInfoResponseTO();
                	DrugInfoRequestTO reqTO = new DrugInfoRequestTO();
            		BeanUtils.copy(request,reqTO);
            		resTO = iRecipeHisService.queryDrugInfo(reqTO);
                    response =new DrugInfoResponse();
            		BeanUtils.copy(resTO, response);
            	}else{
            		response = (DrugInfoResponse) RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class, hisServiceId, "queryDrugInfo", request);
            	}
            } catch (Exception e) {
                logger.error("queryDrugInfo HIS接口调用失败. organId=[{}], param={}",organId,JSONUtils.toString(request));
            }
            logger.info("queryDrugInfo dao={}", JSONUtils.toString(response));
            if(null != response && Integer.valueOf(0).equals(response.getMsgCode())){
                return (null != response.getData())?response.getData():new ArrayList<DrugInfo>();
            }
        }else{
            logger.error("queryDrugInfo organId=[{}]没有配置hisServiceConfig", organId);
        }

        return null;
    }
}
