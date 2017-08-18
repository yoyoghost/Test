package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.ProfessionDAO;
import eh.entity.base.Profession;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-05-04 17:53
 **/
public class ProfessionService {
    private ProfessionDAO professionDAO;

    public ProfessionService() {
        professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
    }

    @RpcService
    public Profession saveOrUpdateProfession(Profession profession) {
        if (profession == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "profession is require");
        }
        String key = profession.getKey();
        if (StringUtils.isEmpty(key)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "profession.key is require");
        }
        if (StringUtils.isEmpty(profession.getText())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "profession.text is require");
        }

        Profession old = professionDAO.getByKey(profession.getKey());
        if (old == null) {//add
            String strParent = profession.getParent();
            if (!StringUtils.isEmpty(strParent)) {
                Profession parent = professionDAO.getByKey(strParent);
                if (parent == null) {
                    throw new DAOException("不存在父节点");
                }
                if (parent.getLeaf()) {
                    parent.setLeaf(Boolean.FALSE);
                    professionDAO.update(parent);
                }
            }
            profession.setOrderNum(profession.getOrderNum()==null?0:profession.getOrderNum());
            profession.setLeaf(Boolean.TRUE);
            profession = professionDAO.save(profession);
            BusActionLogService.recordBusinessLog("专科字典", profession.getKey(), "Profession",
                    "新增专科：" + profession.toString());
        } else {//update
            String strOld = old.toString();
            old.setOrderNum(profession.getOrderNum()==null?0:profession.getOrderNum());
            old.setText(profession.getText());
            profession = professionDAO.update(old);
            BusActionLogService.recordBusinessLog("专科字典", profession.getKey(), "Profession",
                    "更新专科：" + strOld + "更新为" + profession.toString());
        }
        professionDAO.reloadDictionary();
        return profession;
    }

    @RpcService
    public void removeByKey(String key) {
        if (StringUtils.isEmpty(key)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "key is require");
        }
        Profession old = professionDAO.getByKey(key);
        if (old == null) {
            throw new DAOException("key is not exist");
        }
        if(!old.getLeaf()){
            throw new DAOException("key is not leaf");
        }
        BusActionLogService.recordBusinessLog("专科字典", key, "Profession",
                "删除专科：" + old.toString());
        professionDAO.remove(key);
        if(!StringUtils.isEmpty(old.getParent())){
            List<Profession> list = professionDAO.findLikeKey(old.getParent()+"%");
            if(list==null||list.isEmpty()||list.size()==1){
              professionDAO.updateLeafByKey(old.getParent(),Boolean.TRUE);
            }
        }
        professionDAO.reloadDictionary();
    }

    @RpcService
    public Profession getByKey(String key) {
        if (StringUtils.isEmpty(key)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "key is require");
        }
        return professionDAO.getByKey(key);
    }

    @RpcService
    public List<Profession> findAllProfession(int limit,int start){
        return professionDAO.findAllProfession(limit,start);
    }


}
