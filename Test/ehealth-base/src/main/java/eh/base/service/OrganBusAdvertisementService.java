package eh.base.service;

import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganBusAdvertisementDAO;
import eh.entity.base.OrganBusAdvertisement;
import org.apache.log4j.Logger;

import java.util.Date;

/**
 * @author jianghc
 * @create 2016-11-10 11:09
 **/
public  class OrganBusAdvertisementService {
    private static Logger logger = Logger.getLogger(OrganBusAdvertisementService.class);

    public OrganBusAdvertisementDAO organBusAdvertisementDAO = DAOFactory.getDAO(OrganBusAdvertisementDAO.class);

    /**
     * 创建一个机构业务文案
     *
     * @param organBusAdvertisement
     * @return
     */
    @RpcService
    public OrganBusAdvertisement createOneOrganBusAdvertisement(OrganBusAdvertisement organBusAdvertisement) {
        if (organBusAdvertisement == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organBusAdvertisement is required");
        }
        if (organBusAdvertisement.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required");
        }
        if (organBusAdvertisement.getBusType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busType is required");
        }
        OrganBusAdvertisement advertisement = organBusAdvertisementDAO.getByOrganIdAndBusType(organBusAdvertisement.getOrganId(), organBusAdvertisement.getBusType());
        if (advertisement != null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " this advertisement is exist");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        organBusAdvertisement.setId(null);
        organBusAdvertisement.setCreator(urt.getId());
        organBusAdvertisement.setCreateName(urt.getUserName());
        organBusAdvertisement.setCreateDate(new Date());
        organBusAdvertisement.setUpdater(urt.getId());
        organBusAdvertisement.setUpdaterName(urt.getUserName());
        organBusAdvertisement.setLastModify(new Date());
        String strBusType = organBusAdvertisement.getBusType().toString();
        try {
            strBusType = DictionaryController.instance()
                    .get("eh.base.dictionary.OrganBussType")
                    .getText(strBusType);
        } catch (ControllerException e) {
            logger.error("createOneOrganBusAdvertisement() error: "+e);
        }
        StringBuilder sbMsg = new StringBuilder("给");
        sbMsg.append(organBusAdvertisement.getOrganName()).append("机构的").append(strBusType).append("业务添加提示文案").append(organBusAdvertisement.getAdvertisement()).append("。");
        organBusAdvertisement = organBusAdvertisementDAO.save(organBusAdvertisement);
        BusActionLogService.recordBusinessLog("机构业务文案管理", organBusAdvertisement.getId().toString(), "OrganBusAdvertisement", sbMsg.toString());

        return organBusAdvertisement;
    }

    /**
     * 更新一个机构业务
     *
     * @param organBusAdvertisement
     * @return
     */
    @RpcService
    public OrganBusAdvertisement updateOneOrganBusAdvertisement(OrganBusAdvertisement organBusAdvertisement) {
        if (organBusAdvertisement == null || organBusAdvertisement.getId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organBusAdvertisement is required");
        }
        if (organBusAdvertisement.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required");
        }
        if (organBusAdvertisement.getBusType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busType is required");
        }
        OrganBusAdvertisement advertisement = organBusAdvertisementDAO.getByOrganIdAndBusType(organBusAdvertisement.getOrganId(), organBusAdvertisement.getBusType());
        if (advertisement == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " this advertisement is not exist");
        }
        if (advertisement.getId().intValue() != organBusAdvertisement.getId().intValue()) {
            throw new DAOException(DAOException.VALUE_NEEDED, " this advertisement is exist");
        }
        StringBuilder sbMsg = new StringBuilder(" ");
        if(organBusAdvertisement.getOrganId().intValue()!=advertisement.getOrganId().intValue()){
            sbMsg.append("原机构").append(advertisement.getOrganName()).append("更新为").append(organBusAdvertisement.getOrganName()).append(",");
        }
        if(organBusAdvertisement.getBusType().intValue()!=advertisement.getBusType().intValue()){
            try {
                String oldstrBusType = DictionaryController.instance()
                        .get("eh.base.dictionary.OrganBussType")
                        .getText(advertisement.getBusType().toString());
                String newstrBusType = DictionaryController.instance()
                        .get("eh.base.dictionary.OrganBussType")
                        .getText(organBusAdvertisement.getBusType().toString());
                sbMsg.append("原业务").append(oldstrBusType).append("更新为").append(newstrBusType).append(",");

            } catch (ControllerException e) {
                logger.error("updateOneOrganBusAdvertisement() error : "+e);
            }
        }
        if(!organBusAdvertisement.getAdvertisement().equals(advertisement.getAdvertisement())){
            sbMsg.append("原文案").append(advertisement.getAdvertisement()).append("更新为").append(organBusAdvertisement.getAdvertisement()).append(",");
        }
        BeanUtils.map(organBusAdvertisement, advertisement);
        UserRoleToken urt = UserRoleToken.getCurrent();
        advertisement.setUpdater(urt.getId());
        advertisement.setUpdaterName(urt.getUserName());
        advertisement.setLastModify(new Date());
        if(sbMsg.length()>3) {
            BusActionLogService.recordBusinessLog("机构业务文案管理", organBusAdvertisement.getId().toString(), "OrganBusAdvertisement", sbMsg.substring(0, sbMsg.length() - 1));
        }
        return organBusAdvertisementDAO.update(advertisement);
    }

    @RpcService
    public void deleteOneOrganBusAdvertisement(Integer id){
        OrganBusAdvertisement advertisement = organBusAdvertisementDAO.getbyId(id);
        if(advertisement==null){
            throw new DAOException(DAOException.VALUE_NEEDED, " this advertisement is not exist");
        }
        String strBusType = advertisement.getBusType().toString();
        try {
            strBusType = DictionaryController.instance()
                    .get("eh.base.dictionary.OrganBusType")
                    .getText(strBusType);
        } catch (ControllerException e) {
            logger.error("deleteOneOrganBusAdvertisement() error: "+e);
        }
        StringBuilder sbMsg = new StringBuilder("删除文案：");
        sbMsg.append(advertisement.getOrganName()).append("机构的").append(strBusType).append("业务添加提示文案").append(advertisement.getAdvertisement()).append("。");
        BusActionLogService.recordBusinessLog("机构业务文案管理", advertisement.getId().toString(), "OrganBusAdvertisement", sbMsg.toString());
        organBusAdvertisementDAO.deleteById(id);
    }

}
