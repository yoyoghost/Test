package eh.mpi.service.agreement;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.bus.Agreement;
import eh.mpi.dao.AgreeMentDAO;
import eh.utils.LocalStringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author renzh
 * @date 2016/9/18 0018 下午 14:24
 */
public class AgreeMentQueryService {
    private static final Logger logger = LoggerFactory.getLogger(AgreeMentQueryService.class);

    private AgreeMentDAO agreeMentDAO;

    public AgreeMentQueryService(){
        agreeMentDAO = DAOFactory.getDAO(AgreeMentDAO.class);
    }

    /**
     * 根据机构和协议类型取协议
     * @param organId
     * @param agreementType
     * @return
     */
    @RpcService
    public Integer getUrlByorganIdAndagreementType(Integer organId,Integer agreementType){
        Agreement agreement = null;
        try {
            agreement = agreeMentDAO.getByOrganIdAndAgreementType(organId,agreementType);
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        if (agreement != null) {
            return agreement.getContent();
        } else {
            return 0;
        }
    }

    /**
     * 根据机构和协议类型取协议和导出地址
     * @param organId
     * @param agreementType
     * @return
     */
    @RpcService
    public Map getContentAndDownloadUrl(Integer organId, Integer agreementType){
        Map map = new HashMap();
        Integer content = 0;
        Integer downloadPDF = 0;
        try {
            Agreement agreement = agreeMentDAO.getByOrganIdAndAgreementType(organId,agreementType);
            content = agreement.getContent();
            downloadPDF = agreement.getDownloadPDF();
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        map.put("content",content);
        map.put("downloadPDF",downloadPDF);
        return map;
    }

}
