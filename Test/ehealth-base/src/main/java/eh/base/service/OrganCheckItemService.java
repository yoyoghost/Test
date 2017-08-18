package eh.base.service;

import ctd.persistence.DAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganCheckItemDAO;
import eh.op.auth.service.SecurityService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author jianghc
 * @create 2017-07-04 10:26
 **/
public class OrganCheckItemService {

    /**
     * 运营平台（权限改造）
     *
     * @param organId
     * @param checkClass
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map<String, Object>> queryByOrganIdAndClass( Integer organId,
                                                                    String checkClass,
                                                                    int start,
                                                                    int limit) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        OrganCheckItemDAO checkItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        return checkItemDAO.queryByOrganIdAndClass(organId, checkClass, start, limit);
    }

}
