package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.ProTitleDAO;
import eh.entity.base.ProTitle;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-13 11:03
 **/
public class ProTitleService {

    private final static String dicName = "eh.base.dictionary.ProTitle";

    @RpcService
    public ProTitle saveOrUpdateOneProTitle(ProTitle proTitle){
        if (proTitle==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"proTitle is require");
        }
        Integer key= proTitle.getId();
        String text = proTitle.getText();
        if(key==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"proTitle.id is require");
        }
        if(StringUtils.isEmpty(text)){
            throw new DAOException(DAOException.VALUE_NEEDED,"proTitle.text is require");
        }
        Integer orderNum= proTitle.getOrderNum();
        proTitle.setOrderNum(orderNum==null?0:orderNum);
        ProTitleDAO proTitleDAO = DAOFactory.getDAO(ProTitleDAO.class);
        ProTitle old = proTitleDAO.get(key);
        if(old==null){//create
            proTitleDAO.save(proTitle);
            BusActionLogService.recordBusinessLog("字典维护",key+"","ProTitle","【职称字典】新增字典:值为【"+key+"】,名称为"+text+",排序为"+orderNum);
        }else {//update
            proTitleDAO.update(proTitle);
            BusActionLogService.recordBusinessLog("字典维护",key+"","ProTitle","【职称字典】更新字典:值为【"+key+"】,原名称为"+old.getText()+"更新为"+text+",原排序为"+old.getOrderNum()+"更新为"+proTitle.getOrderNum());
        }
        this.reloadDictionary();
        return proTitle;
    }

    @RpcService
    public List<ProTitle> findAllProTitle(){
        return DAOFactory.getDAO(ProTitleDAO.class).findAllProTitle();
    }


    public void reloadDictionary() {
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            throw new DAOException("职称字典刷新失败：" + e.getMessage());
        }
    }
}
