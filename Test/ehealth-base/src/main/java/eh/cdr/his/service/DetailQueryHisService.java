package eh.cdr.his.service;

import com.ngari.his.recipe.mode.DetailQueryReqTO;
import com.ngari.his.recipe.mode.DetailQueryResTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.his.DetailQueryReq;
import eh.entity.his.DetailQueryRes;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by zhongzx on 2016/6/24 0024.
 * 向his 查询处方详情服务
 */
public class DetailQueryHisService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(DetailQueryHisService.class);
    
    public DetailQueryRes detailQueryHis(DetailQueryReq request) {
        try {
            Integer organID = Integer.valueOf(request.getOrganID());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID);
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".detailQueryService";
            logger.info("detailQueryHis request={}", JSONUtils.toString(request));
            DetailQueryRes response = null;
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){ 
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	DetailQueryResTO resTO = new DetailQueryResTO();
            	DetailQueryReqTO reqTO = new DetailQueryReqTO();
        		BeanUtils.copy(request,reqTO);
        		resTO = iRecipeHisService.detailQuery(reqTO);
                response=new DetailQueryRes();
        		BeanUtils.copy(resTO, response);
        	}else{
        		response = (DetailQueryRes)RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"detailQuery",request);
        	}
            logger.info("detailQueryHis dao={}", JSONUtils.toString(response));
            return response;
        } catch (Exception e) {
            logger.error("detailQueryHis HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return null;
    }
}
