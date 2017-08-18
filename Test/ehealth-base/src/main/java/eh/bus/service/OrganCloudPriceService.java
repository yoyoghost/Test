package eh.bus.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganConfigDAO;
import eh.bus.dao.OrganCloudPriceDAO;
import eh.entity.base.OrganConfig;
import eh.entity.bus.OrganCloudPrice;
import eh.op.auth.service.SecurityService;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author jianghc
 * @create 2017-04-25 14:46
 **/
public class OrganCloudPriceService {
    private OrganCloudPriceDAO organCloudPriceDAO;

    public OrganCloudPriceService() {
        this.organCloudPriceDAO = DAOFactory.getDAO(OrganCloudPriceDAO.class);
    }


    @RpcService
    public OrganCloudPrice saveOrUpdateOnePrice(OrganCloudPrice price) {
        if (price == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice is require");
        }
        if (price.getOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.Organ is require");
        }
        if (StringUtils.isEmpty(price.getBussType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.BussType is require");
        }
        if (StringUtils.isEmpty(price.getProTitle())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.ProTitle is require");
        }
        OrganCloudPrice old = organCloudPriceDAO.getPriceByOrganAndBussTypeAndProTitle(price.getOrgan(), price.getBussType(), price.getProTitle());
        if (old!=null){
            BeanUtils.map(price, old);
            return organCloudPriceDAO.update(old);
        }
        return organCloudPriceDAO.save(price);
    }


    @RpcService
    public List<OrganCloudPrice> findByOrganAndBussType(Integer organ, String bussType){
        if (organ == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ is require");
        }
        if (StringUtils.isEmpty(bussType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bussType is require");
        }
        return organCloudPriceDAO.findByOrganAndBussType(organ,bussType);
    }


    /**
     * 运营平台（权限改造）
     * @param organ
     * @param bussType
     * @return
     */
    @RpcService
    public List<OrganCloudPrice> findByOrganAndBussTypeForOp(Integer organ, String bussType){
        if (organ == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ is require");
        }
        if (StringUtils.isEmpty(bussType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bussType is require");
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(organ);
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        return organCloudPriceDAO.findByOrganAndBussType(organ,bussType);
    }


    @RpcService
    public OrganCloudPrice getPriceForAppoint(Integer organ,String proTitle){
        return organCloudPriceDAO.getPriceForAppoint(organ,proTitle);
    }

    /**
     * 获取云门诊价格
     *
     * @param organ    机构内码
     * @param proTitle 医生职称
     * @return double
     */
    @RpcService
    public double getPriceForCloudOnlineOnly(Integer organ, String proTitle) {
        if (organ == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ is require");
        }
        if (StringUtils.isEmpty(proTitle)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "proTitle is require");
        }
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config = organConfigDAO.getByOrganId(organ);
        if (config == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is not exist");
        }

        if (!ObjectUtils.nullSafeEquals(config.getAppointCloudPayOnLine(), true)) {
            return 0;
        }
        OrganCloudPrice price = organCloudPriceDAO.getPriceByOrganAndBussTypeAndProTitle(organ, "appoint", proTitle);
        if (price == null || price.getPrice() == null) {
            return 0;
        }
        return price.getPrice();
    }

}

