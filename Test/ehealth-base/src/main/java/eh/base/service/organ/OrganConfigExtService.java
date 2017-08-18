package eh.base.service.organ;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganConfigExtDAO;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfigExt;

import java.util.List;


/**
 * @author jianghc
 * @create 2017-03-23 15:43
 **/
public class OrganConfigExtService {
    private OrganConfigExtDAO extOrganConfigDAO;

    public OrganConfigExtService() {
        this.extOrganConfigDAO = DAOFactory.getDAO(OrganConfigExtDAO.class);
    }

    @RpcService
    public OrganConfigExt getByExtTypeAndOrganId(String extType, Integer organId) {
        return extOrganConfigDAO.getByExtTypeAndOrganId(extType, organId);
    }


    @RpcService
    public List<OrganConfigExt> findByOrganId(Integer organId) {
        return extOrganConfigDAO.findByOrganId(organId);
    }

    @RpcService
    public List<OrganConfigExt> findByExtType(String extType) {
        return extOrganConfigDAO.findByExtType(extType);
    }

    @RpcService
    public OrganConfigExt saveOneExtOrganConfig(OrganConfigExt extOrganConfig) {
        return extOrganConfigDAO.saveOneExtOrganConfig(extOrganConfig);
    }

    @RpcService
    public void saveListExtOrganConfigs(String extType, List<Integer> list) {
        try {
            if (extType == null || DictionaryController.instance().get("eh.base.dictionary.ExtType").getText(extType) == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "extType is not exist");
            }
        } catch (ControllerException e) {
            throw new DAOException("extType is not exist");
        }
        if (list == null || list.size() <= 0) {
            extOrganConfigDAO.removeByExtType(extType);
            return;
        }
        extOrganConfigDAO.removeByExtTypeAndNotInOrganIds(extType, list);
        List<OrganConfigExt> old = extOrganConfigDAO.findByExtType(extType);
        //采取guava工具方式list->map
        ImmutableMap<Integer, OrganConfigExt> map = Maps.uniqueIndex(old, new Function<OrganConfigExt, Integer>() {
            @Override
            public Integer apply(OrganConfigExt ext) {
                return ext.getOrganId();
            }
        });

        for (Integer organId : list) {
            if (map.get(organId) == null) {
                extOrganConfigDAO.save(new OrganConfigExt(extType, organId));
            }
        }
    }

    @RpcService
    public QueryResult<Organ> queryOrganForMindGift(Boolean canMindGift, int start, int limit) {
        canMindGift = canMindGift == null ? false : canMindGift;
        return extOrganConfigDAO.queryOrganForMindGift(canMindGift, start, limit);
    }

}
