package eh.bus.dao;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.base.dao.OrganConfigDAO;
import eh.entity.base.OrganConfig;
import eh.entity.bus.OrganCloudPrice;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-04-25 14:33
 **/
public abstract class OrganCloudPriceDAO extends HibernateSupportDelegateDAO<OrganCloudPrice> {
    public OrganCloudPriceDAO() {
        super();
        setEntityName(OrganCloudPrice.class.getName());
        setKeyField("id");
    }

    @DAOMethod(sql = " from OrganCloudPrice where organ=:organ and bussType=:bussType and proTitle=:proTitle")
    public abstract OrganCloudPrice getPriceByOrganAndBussTypeAndProTitle(@DAOParam("organ")Integer organ,@DAOParam("bussType")String bussType,@DAOParam("proTitle") String proTitle);

    @DAOMethod(sql = " from OrganCloudPrice where organ=:organ and bussType=:bussType ",limit = 0)
    public abstract List<OrganCloudPrice> findByOrganAndBussType(@DAOParam("organ")Integer organ, @DAOParam("bussType")String bussType);


    @Override
    public OrganCloudPrice save(OrganCloudPrice o) throws DAOException {
        if(o==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"OrganCloudPrice is require");
        }
        if(o.getOrgan()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"OrganCloudPrice.Organ is require");
        }
        if (StringUtils.isEmpty(o.getBussType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.BussType is require");
        }
        if (StringUtils.isEmpty(o.getProTitle())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.ProTitle is require");
        }
        o.setExtPrice(o.getExtPrice()==null?Double.valueOf(0):o.getExtPrice());
        o.setPrice(o.getPrice()==null?Double.valueOf(0):o.getPrice());
        return super.save(o);
    }

    @Override
    public OrganCloudPrice update(OrganCloudPrice o) throws DAOException {
        if(o==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"OrganCloudPrice is require");
        }
        if(o.getOrgan()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"OrganCloudPrice.Organ is require");
        }
        if (StringUtils.isEmpty(o.getBussType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.BussType is require");
        }
        if (StringUtils.isEmpty(o.getProTitle())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganCloudPrice.ProTitle is require");
        }
        o.setExtPrice(o.getExtPrice()==null?Double.valueOf(0):o.getExtPrice());
        o.setPrice(o.getPrice()==null?Double.valueOf(0):o.getPrice());
        return super.update(o);
    }


    public OrganCloudPrice getPriceForAppoint(Integer organ,String proTitle){
        if(organ==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"organ is require");
        }
        if (StringUtils.isEmpty(proTitle)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "proTitle is require");
        }
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);

        OrganConfig config = organConfigDAO.getByOrganId(organ);

        if(config==null){
            throw new DAOException("organId is not exist");
        }
        if(ObjectUtils.nullSafeEquals(config.getAppointCloudPayOnLine(),true)){
            throw new DAOException("organ can't pay off line");
        }
       return this.getPriceByOrganAndBussTypeAndProTitle(organ,"appoint",proTitle);
    }

}
