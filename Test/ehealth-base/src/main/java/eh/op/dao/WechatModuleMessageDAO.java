package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.WechatModuleMessage;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Created by andywang on 2016/12/5.
 */
public abstract class WechatModuleMessageDAO extends HibernateSupportDelegateDAO<WechatModuleMessage> {

    public static final Logger log = Logger.getLogger(WechatModuleMessageDAO.class);

    public WechatModuleMessageDAO() {
        super();
        this.setEntityName(WechatModuleMessage.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql="from WechatModuleMessage where 1=1 order by id")
    public abstract List<WechatModuleMessage> findAllModuleMessages();

}
