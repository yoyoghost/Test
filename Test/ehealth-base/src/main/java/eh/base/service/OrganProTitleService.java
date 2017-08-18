package eh.base.service;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganProTitleDAO;
import eh.entity.base.OrganProTitle;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-13 11:32
 **/
public class OrganProTitleService {
    @RpcService
    public List<OrganProTitle> findByOrganId(Integer organId) {
        return DAOFactory.getDAO(OrganProTitleDAO.class).findByOrganId(organId == null ? 0 : organId);
    }

    @RpcService
    public OrganProTitle saveOrUpdateOneOrganProTitle(OrganProTitle organProTitle) {
        if (organProTitle == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "proTitle is require");
        }
        Integer proTitleId = organProTitle.getProTitleId();
        Integer organId = organProTitle.getOrganId();
        if (proTitleId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "proTitle.proTitleId is require");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "proTitle.organId is require");
        }
        Integer id = organProTitle.getId();
        OrganProTitleDAO organProTitleDAO = DAOFactory.getDAO(OrganProTitleDAO.class);
        OrganProTitle old = organProTitleDAO.getByOrganIdAndProTitleId(organId, proTitleId);
        Integer orderNum = organProTitle.getOrderNum();
        organProTitle.setOrderNum(orderNum == null ? 0 : orderNum);
        if (old == null) {//create
            organProTitle.setId(null);
            organProTitle = organProTitleDAO.save(organProTitle);
            try {
                BusActionLogService.recordBusinessLog("机构职称",id+"","OrganProTitle",
                        organId==0?"平台":(DictionaryController.instance().get("eh.base.dictionary.Organ").getText(organId)+"机构")+"新增职称："+DictionaryController.instance().get("eh.base.dictionary.ProTitle").getText(organProTitle.getProTitleId())+"("+organProTitle.getProTitleId()+"),排序为"+organProTitle.getOrderNum());
            } catch (ControllerException e) {
                e.printStackTrace();
            }
        } else {//update
            if (id != null && !old.getId().equals(id)) {
                throw new DAOException("机构已存在该职称");
            }
            organProTitle = organProTitleDAO.update(organProTitle);
            try {
                BusActionLogService.recordBusinessLog("机构职称",id+"","OrganProTitle",
                        organId==0?"平台":(DictionaryController.instance().get("eh.base.dictionary.Organ").getText(organId)+"机构")+"更新职称："+DictionaryController.instance().get("eh.base.dictionary.ProTitle").getText(organProTitle.getProTitleId())+"("+organProTitle.getProTitleId()+"),排序为"+organProTitle.getOrderNum());
            } catch (ControllerException e) {
                e.printStackTrace();
            }
        }
        return organProTitle;
    }

    @RpcService
    public void deleteOneOrganProTitle(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        OrganProTitleDAO organProTitleDAO = DAOFactory.getDAO(OrganProTitleDAO.class);
        OrganProTitle organProTitle= organProTitleDAO.get(id);
        if (organProTitle==null){
            throw new DAOException(" id is not exist");
        }
        organProTitleDAO.remove(id);
        Integer organId = organProTitle.getOrganId();
        try {
            BusActionLogService.recordBusinessLog("机构职称",id+"","OrganProTitle",
                    organId==0?"平台":(DictionaryController.instance().get("eh.base.dictionary.Organ").getText(organId)+"机构")+"删除职称："+DictionaryController.instance().get("eh.base.dictionary.ProTitle").getText(organProTitle.getProTitleId())+"("+organProTitle.getProTitleId()+")");
        } catch (ControllerException e) {
            BusActionLogService.recordBusinessLog("机构职称",id+"","OrganProTitle","删除机构职称："+id);
        }
    }

}
