package eh.bus.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.BusMoneyDistributeProportionConfigDAO;
import eh.entity.bus.BusMoneyDistributeProportionConfig;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-11 10:16
 **/
public class BusMoneyDistributeProportionConfigService {

    @RpcService
    public List<BusMoneyDistributeProportionConfig> findByOrganId(Integer organId){
        if (organId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"organId is require");
        }
        return DAOFactory.getDAO(BusMoneyDistributeProportionConfigDAO.class).findByOrganId(organId);
    }

    @RpcService
    public List<BusMoneyDistributeProportionConfig> findByBusTypeAndOrganId(Integer organId,String busType){
        if (organId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"organId is require");
        }
        if (StringUtils.isEmpty(busType)){
            throw new DAOException(DAOException.VALUE_NEEDED,"busType is require");
        }
        return DAOFactory.getDAO(BusMoneyDistributeProportionConfigDAO.class).findByBusTypeAndOrganId(busType,organId);
    }

    @RpcService
    public void deleteByOrganIdAndBusType(Integer organId,String busType){
        if (organId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"organId is require");
        }
        if (StringUtils.isEmpty(busType)){
            throw new DAOException(DAOException.VALUE_NEEDED,"busType is require");
        }
        String strOrgan ="纳里云平台";
        if(!organId.equals(0)){
            strOrgan = DAOFactory.getDAO(OrganDAO.class).getNameById(organId);
        }
        if(StringUtils.isEmpty(strOrgan)){
            throw new DAOException("机构不存在");
        }
        String strBusType ="";
        try {
            strBusType = DictionaryController.instance().get("eh.base.dictionary.SubBusType").getText(busType);
        } catch (ControllerException e) {
            throw new DAOException("业务类型不存在");
        }
        BusActionLogService.recordBusinessLog("机构业务分成",organId+"-"+busType,"BusMoneyDistributeProportionConfig","删除机构【"+strOrgan+"】的【"+strBusType+"】业务分成配置");
        DAOFactory.getDAO(BusMoneyDistributeProportionConfigDAO.class).deleteByOrganIdAndBusType(organId,busType);
    }

    @RpcService
    public void saveOrUpdateByOrganIdAndBusType(Integer organId,String busType,List<BusMoneyDistributeProportionConfig> list){
        if (organId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"organId is require");
        }
        if (StringUtils.isEmpty(busType)){
            throw new DAOException(DAOException.VALUE_NEEDED,"busType is require");
        }
        if(list==null||list.isEmpty()){
            throw new DAOException(DAOException.VALUE_NEEDED,"BusMoneyDistributeProportionConfigs is require");
        }
        String strOrgan ="纳里云平台";
        if(!organId.equals(0)){
            strOrgan = DAOFactory.getDAO(OrganDAO.class).getNameById(organId);
        }
        if(StringUtils.isEmpty(strOrgan)){
            throw new DAOException("机构不存在");
        }
        String strBusType ="";
        try {
            strBusType = DictionaryController.instance().get("eh.base.dictionary.SubBusType").getText(busType);
        } catch (ControllerException e) {
            throw new DAOException("业务类型不存在");
        }
        BusMoneyDistributeProportionConfigDAO busMoneyDistributeProportionConfigDAO = DAOFactory.getDAO(BusMoneyDistributeProportionConfigDAO.class);

        List<BusMoneyDistributeProportionConfig>  olds =busMoneyDistributeProportionConfigDAO.findByBusTypeAndOrganId(busType,organId);
        StringBuffer sbLog =  new StringBuffer();
        Dictionary roleDic = null;
        try {
            roleDic = DictionaryController.instance().get("eh.bus.dictionary.RoleType");
        } catch (ControllerException e) {
            throw new DAOException("RoleType 字典加载失败");
        }
        boolean isUpdate = false;
        if (olds!=null&&!olds.isEmpty()){
          for(BusMoneyDistributeProportionConfig old:olds){
              sbLog.append(roleDic.getText(old.getRole())).append("(").append(old.getProportion()).append("),");
          }
            sbLog = new StringBuffer(sbLog.substring(0,sbLog.length()-1));
            sbLog.append("更新为：");
            isUpdate = true;
        }
       //删除原有配置
        busMoneyDistributeProportionConfigDAO.deleteByOrganIdAndBusType(organId,busType);
        for (BusMoneyDistributeProportionConfig item:list){
            item.setId(null);
            item.setBusType(busType);
            item.setOrganId(organId);
            busMoneyDistributeProportionConfigDAO.save(item);
            sbLog.append(roleDic.getText(item.getRole())).append("(").append(item.getProportion()).append("),");
        }
        BusActionLogService.recordBusinessLog("机构业务分成",organId+"-"+busType,"BusMoneyDistributeProportionConfig",isUpdate?"更新":"添加"+"机构【"+strOrgan+"】的【"+strBusType+"】业务分成配置,"+sbLog.substring(0,sbLog.length()-1));
    }



}
