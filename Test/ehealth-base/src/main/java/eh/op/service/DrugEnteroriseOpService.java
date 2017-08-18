package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DrugListDAO;
import eh.base.service.BusActionLogService;
import eh.cdr.dao.DrugsEnterpriseDAO;
import eh.entity.base.DrugList;
import eh.entity.cdr.DrugsEnterprise;
import eh.util.HttpHelper;
import eh.utils.MapValueUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2016-11-15 13:15
 **/
public class DrugEnteroriseOpService {
    private static final Log logger = LogFactory.getLog(DrugEnteroriseOpService.class);


    /**
     * 推送药品给药企
     *
     * @param depId
     */
    @RpcService
    public Boolean pushToEnterpriseDrugs(Integer depId) {
        logger.info("开始向推送药品，药企ID：" + depId);
        if(depId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"deptId is required");
        }

        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        List<DrugList> drugList = drugListDAO.findDrugsByOrganId(depId);
        if(drugList==null||drugList.size()<=0){
            throw new DAOException(DAOException.VALUE_NEEDED," drugList is not exist");
        }
        logger.info("需要推送的药品数量"+drugList.size());

        List<Map<String, Object>> drugsList = new ArrayList<>();
        Map<String, Object> drugMap;

        //处理药品数据
        for (DrugList drug : drugList) {
            drugMap = new HashMap<String, Object>();
            drugMap.put("producer", drug.getProducer());
            drugMap.put("gname", drug.getDrugName());
            drugMap.put("goodsid", drug.getDrugId());
            drugMap.put("msunitno", drug.getUnit());
            drugMap.put("spec", drug.getDrugSpec());
            drugMap.put("drugname", drug.getSaleName());
            drugsList.add(drugMap);
        }
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise de = drugsEnterpriseDAO.getById(depId);

        Map<String, Object> sendMap = new HashMap<>();
        sendMap.put("access_token", de.getToken());
        sendMap.put("action", "setGoods");


        //分批推送
        int yz = 50;
        int count = (int) Math.ceil(drugsList.size() / 50.0);
        List<Map<String, Object>> everyCountList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            everyCountList.clear();
            int start = i * yz;
            int end = (i + 1) * yz;
            if (end > drugsList.size()) {
                end = drugsList.size();
            }
            logger.info("开始条目数"+start+"结束"+end);
            for (int k = start; k < end; k++) {
                everyCountList.add(drugsList.get(k));
            }
            if (!everyCountList.isEmpty()) {
                sendMap.put("data", everyCountList);
                String backMsg = "";
                try {
                    backMsg = HttpHelper.doPost(de.getBusinessUrl(), JSONUtils.toString(sendMap));
                } catch (Exception e) {
                    logger.error("IOException " + e.getMessage() + "，详细数据：" + JSONUtils.toString(sendMap));
                    backMsg = "";
                }

                if (StringUtils.isNotEmpty(backMsg)) {
                    // code 1成功
                    Map backMap = JSONUtils.parse(backMsg, Map.class);
                    if (1 == MapValueUtil.getInteger(backMap, "code")) {
                        //System.out.println("药品推送成功:");
                        BusActionLogService.recordBusinessLog("推送药品",depId.toString(),"DrugsEnterprise","向"+de.getName()+"推送"+drugList.size()+"条药品。");
                        return true;
                    } else {
                        logger.error("失败信息：" + MapValueUtil.getString(backMap, "message") + "***推送信息:" + JSONUtils.toString(sendMap));
                        BusActionLogService.recordBusinessLog("推送药品",depId.toString(),"DrugsEnterprise","向"+de.getName()+"推送药品失败："+MapValueUtil.getString(backMap, "message"));
                    }
                }
            }
        }
        return false;
    }


}
