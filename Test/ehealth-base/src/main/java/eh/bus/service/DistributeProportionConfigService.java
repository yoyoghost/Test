package eh.bus.service;


import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import eh.bus.dao.BusMoneyDistributeProportionConfigDAO;
import eh.entity.bus.BusMoneyDistributeProportionConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class DistributeProportionConfigService {
    private static final Logger logger = Logger.getLogger(DistributeProportionConfigService.class);

    /**
     * 按业务类型+机构Id查询分成配置
     * @param busType
     * @param organId
     * @return
     */
    public Map<String,BigDecimal> getOrganProportion(String distributeProportionBusType,
                                                     Integer organId){
        logger.info("getOrganProportion organ="+organId+"; subbusType="+distributeProportionBusType);
        BusMoneyDistributeProportionConfigDAO configDAO=
                DAOFactory.getDAO(BusMoneyDistributeProportionConfigDAO.class);

        List<BusMoneyDistributeProportionConfig> list=new ArrayList<>();

        if(!StringUtils.isEmpty(distributeProportionBusType) && organId!=null){
            list=configDAO.findByBusTypeAndOrganId(distributeProportionBusType,organId);
        }

        if(list.isEmpty()){
            //取值为0的，默认分成配置
            list=configDAO.findByBusTypeAndOrganId(distributeProportionBusType,organId);
        }

        //未查询到任何配置
        if(!list.isEmpty()){
            return getMapFromConfigs(list);
        }

        return null;
    }

    /**
     * 将数据库字段转成map
     * @param configList
     * @return
     */
    private Map<String,BigDecimal> getMapFromConfigs(List<BusMoneyDistributeProportionConfig> configList){
        Map <String,BigDecimal> rateMap= Maps.newHashMap();
        for (BusMoneyDistributeProportionConfig config:configList) {
            rateMap.put(config.getRole(),BigDecimal.valueOf(config.getProportion()));
        }
        return rateMap;
    }

}
