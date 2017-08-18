package eh.op.service;

import ctd.controller.exception.ControllerException;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganDAO;
import eh.base.dao.UserRolesDAO;
import eh.base.service.BusActionLogService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.Organ;
import eh.entity.wx.WXConfig;
import eh.msg.dao.AdDAO;
import eh.op.auth.service.SecurityService;
import eh.op.dao.WXConfigsDAO;
import eh.util.ControllerUtil;
import org.apache.axis.utils.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;

import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-28 10:26
 **/
public class OrganOPService {
    private static final Logger logger = Logger.getLogger(OrganOPService.class);

    /**
     * 更新机构的管理层级
     *
     * @param organId
     * @param manageUnit
     */
    @RpcService
    public void updateManageUnitByOrganId(Integer organId, final String manageUnit) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " organId is require");
        }
        if (manageUnit == null || StringUtils.isEmpty(manageUnit.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " manageUnit is require");
        }
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final Organ organ = organDAO.getByOrganId(organId);
        if (organ == null) {
            throw new DAOException(" organId is not exist");
        }
        final String oldManageUnit = organ.getManageUnit();//原管理层级
        if (oldManageUnit.equals(manageUnit)) {
            return;
        }
        if (organDAO.getByManageUnit(manageUnit) != null) {
            throw new DAOException(" manageUnit is  exist");
        }
        final UserRolesDAO userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
        final WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        final AdDAO adDAO = DAOFactory.getDAO(AdDAO.class);
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                organ.setManageUnit(manageUnit);
                organDAO.update(organ);//更新机构信息
                userRolesDAO.updateManageUnit(oldManageUnit, manageUnit);//更新UserRoles
                wxConfigsDAO.updateByManageUnit(oldManageUnit, manageUnit);
                adDAO.updateManageUnit(oldManageUnit, manageUnit);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        List<String> users = userRolesDAO.findAllByManageUnit(manageUnit);
        if (users != null) {
            for (String uId : users) {
                try {
                    ControllerUtil.reloadUserByUid(uId);//刷新用户缓存
                } catch (ControllerException e) {
                    logger.error(e);
                }
            }
        }
        List<WXConfig> wxConfigs = wxConfigsDAO.findByManageUnit(manageUnit);
        if (wxConfigs != null) {

            for (WXConfig wxConfig : wxConfigs) {
                try {
                    ControllerUtil.reloadWXAppByAppId(wxConfig.getAppID());
                } catch (ControllerException e) {
                    logger.error(e);
                }
            }
        }
        BusActionLogService.recordBusinessLog("机构管理层级管理", organId + "", "Organ",
                "机构:" + organ.getName() + ",将管理层级" + oldManageUnit + "更新为" + manageUnit);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByGrade(
            final Organ organ, final Date startDate, final Date endDate) {
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        return organDAO.getStatisticsByGrade(organ, startDate, endDate);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(
            final Organ organ, final Date startDate, final Date endDate) {
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        return organDAO.getStatisticsByStatus(organ, startDate, endDate);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByType(
            final Organ organ, final Date startDate, final Date endDate) {
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        return organDAO.getStatisticsByType(organ, startDate, endDate);
    }

    @RpcService
    public Organ getByOrganId(Integer organId) {
        Set<Integer> organs = new HashSet<Integer>();
        organs.add(organId);
        if (SecurityService.isAuthoritiedOrgan(organs)) {
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            return organDAO.getByOrganId(organId);
        }
        return null;
    }

    @RpcService
    public void updateOrganStatusByOrganId(Integer organId){
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        organDAO.updateStatusToCancellation(organId);
        this.changeOrganStatusForOp(organId,9);
    }

    public void changeOrganStatusForOp(Integer organId,Integer status){
        try {
            Publisher publisher = MQHelper.getMqPublisher();
            Map<String, Object> data = new HashMap<String,Object>();
            data.put("type",BusActionLogService.CHANGE_STATUS);
            data.put("event","changeorganstatus");
            data.put("organId",organId);
            data.put("status",status);
            publisher.publish(OnsConfig.logTopic, data);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}

