package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.CheckRequestImg;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-10-26 15:06
 **/
public abstract class CheckRequestImgDAO extends HibernateSupportDelegateDAO<CheckRequestImg> {
    private static final Log logger = LogFactory.getLog(CheckRequestImgDAO.class);

    public CheckRequestImgDAO() {
        super();
        this.setEntityName(CheckRequestImg.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod
    public abstract List<CheckRequestImg> findByRequestId(Integer requestId);




}
