package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.UnitOpauthorizeDAO;
import eh.entity.base.UnitOpauthorizeAndOrgan;
import eh.op.auth.service.SecurityService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author jianghc
 * @create 2017-07-04 09:00
 **/
public class UnitOpauthorizeService {

    /**
     * 运营平台（权限改造）
     * @param organId
     * @param start
     * @return
     */
    @RpcService
    public List<UnitOpauthorizeAndOrgan> findUnitOpauthorizeAndOrgans(
            Integer organId, long start) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        return unitOpauthorizeDAO.findUnitOpauthorizeAndOrgans(organId,start);
    }

    /**
     *
     * 运营平台（权限改造）
     *
     * @param accreditOrganId
     * @param start
     * @return
     */
    @RpcService
    public List<UnitOpauthorizeAndOrgan> findUnitOpauthorizeAndAccreditOrgans(
            Integer accreditOrganId, long start) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(accreditOrganId);
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        return unitOpauthorizeDAO.findUnitOpauthorizeAndAccreditOrgans(accreditOrganId,start);


    }

}
