package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.AddrAreaDAO;
import eh.entity.base.AddrArea;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-13 10:10
 **/
public class AddrAreaService {
    private static final Log logger = LogFactory.getLog(AddrAreaService.class);
    private AddrAreaDAO addrAreaDAO;

    public AddrAreaService() {
        addrAreaDAO = DAOFactory.getDAO(AddrAreaDAO.class);
    }

    private final static String dicName = "eh.base.dictionary.AddrArea";

    @RpcService
    public QueryResult<AddrArea> queryByStartAndLimit(final int start, final int limit) {
        return addrAreaDAO.queryByStartAndLimit(start, limit);
    }

    @RpcService
    public AddrArea getByKey(String id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea.id is require");
        }
        return addrAreaDAO.get(id);
    }

    /**
     * 根据地址名称获取对应地址数据
     * @param text
     * @param codePrefix 前提限制
     * @return
     */
    public List<AddrArea> getByName(String text, String codePrefix) {
        if(StringUtils.isEmpty(text)){
            return null;
        }

        List<AddrArea> areas;
        if(StringUtils.isEmpty(codePrefix)){
            areas = addrAreaDAO.findAreaByText(text);
        }else{
            areas = addrAreaDAO.findAreaByTextWithLike(text, codePrefix+"%");
        }

        return areas;
    }

    @RpcService
    public AddrArea addOrUpdateOne(AddrArea addrArea) {
        if (addrArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea is require");
        }
        String id = addrArea.getId() == null ? "" : addrArea.getId().trim();
        if (StringUtils.isEmpty(id)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea.id is require");
        }
        String text = addrArea.getText() == null ? "" : addrArea.getText().trim();
        if (StringUtils.isEmpty(text)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea.text is require");
        }
        StringBuffer logMsg = new StringBuffer();
        AddrArea old = addrAreaDAO.get(id);
        if (old == null) {//add
            if (id.length() > 2) {
                AddrArea father = addrAreaDAO.get(id.substring(0, id.length() - 2));
                if (father == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "farther is not exist");
                }
                addrArea.setLeaf(true);
                //将父节点的叶子节点属性去除
                if(father.getLeaf()) {
                    father.setLeaf(false);
                    addrAreaDAO.update(father);
                }
            }
            addrArea = addrAreaDAO.save(addrArea);
            logMsg.append("新增：").append(addrArea.toString());
        } else {//update
            addrArea = addrAreaDAO.update(addrArea);
            logMsg.append("更新：原").append(old.toString()).append("更新为").append(addrArea.toString());
        }
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            logger.error("addarea 字典缓存刷新失败：" + e.getMessage());
        }
        BusActionLogService.recordBusinessLog("地域字典管理", addrArea.getId(), "AddrArea", logMsg.toString());
        return addrArea;

    }

    @RpcService
    public void deleteById(String id) {
        if (id == null || StringUtils.isEmpty(id)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea.id is require");
        }
        AddrArea old = addrAreaDAO.get(id);
        if (old == null) {//
            throw new DAOException(" addrArea.id is not exist");
        }
        //List<AddrArea> child = addrAreaDAO.findChildById(id + "%");
        if (!old.getLeaf()) {
            throw new DAOException(" have child ");
        }
        addrAreaDAO.remove(id);
        int length = id.length();
        if(length>2) {
            List<AddrArea> child = addrAreaDAO.findChildById(id.substring(0,length-2) + "%");
            if(child.size()==1){//若父节点无子子节点，父节点添加叶子节点属性
                AddrArea father = child.get(0);
                father.setLeaf(true);
                addrAreaDAO.update(father);
            }
        }
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            logger.error("addarea 字典缓存刷新失败：" + e.getMessage());
        }
        BusActionLogService.recordBusinessLog("地域字典管理", id, "AddrArea", "删除：" + old.toString());

    }

    @RpcService
    public List<AddrArea> findChildById(String id) {
        if (id == null || StringUtils.isEmpty(id)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " addrArea.id is require");
        }
        return addrAreaDAO.findChildById(id + "%");
    }

}
