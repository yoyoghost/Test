package eh.cdr.his.service;

import com.ngari.his.recipe.mode.PayNotifyReqTO;
import com.ngari.his.recipe.mode.PayNotifyResTO;
import com.ngari.his.recipe.service.IRecipeHisService;
import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.cdr.Recipedetail;
import eh.entity.his.PayNotifyReq;
import eh.entity.his.PayNotifyRes;
import eh.remote.IHisRecipeInterface;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by zhongzx on 2016/6/17 0017.
 * 纳里平台完成处方支付后，同步通知HIS系统已完成处方支付。
 */
public class PayNotifyHisService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(PayNotifyHisService.class);

    public Recipedetail payNotifyHis(PayNotifyReq request) {
        try {
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.valueOf(request.getOrganID()));
            //调用服务id
            String hisServiceId = cfg.getAppDomainId() + ".payNotifyService";
            logger.info("payNotifyHis request={}", JSONUtils.toString(request));
            PayNotifyRes response = null;
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(request.getOrganID()))){ 
            	IRecipeHisService iRecipeHisService = AppDomainContext.getBean("his.iRecipeHisService", IRecipeHisService.class);
            	PayNotifyResTO resTO = new PayNotifyResTO();
            	PayNotifyReqTO reqTO = new PayNotifyReqTO();
        		BeanUtils.copy(request,reqTO);
        		resTO = iRecipeHisService.payNotify(reqTO);
                response=new PayNotifyRes();
        		BeanUtils.copy(resTO, response);
        	}else{
        		response = (PayNotifyRes)RpcServiceInfoUtil.getClientService(IHisRecipeInterface.class,hisServiceId,"payNotify",request);
        	}
            logger.info("payNotifyHis dao={}", JSONUtils.toString(response));
            return resolveResponse(response);
        } catch (Exception e) {
            logger.error("payNotifyHis HIS接口调用失败. organId=[{}], param={}",request.getOrganID(),JSONUtils.toString(request));
        }
        return null;
    }

    public Recipedetail resolveResponse(PayNotifyRes response){
        if(null == response || null == response.getMsgCode()){
            return null;
        }
        Recipedetail detail = new Recipedetail();
        detail.setPatientInvoiceNo(response.getData().getInvoiceNo());
        detail.setPharmNo(response.getData().getWindows());
        return detail;
    }
}
