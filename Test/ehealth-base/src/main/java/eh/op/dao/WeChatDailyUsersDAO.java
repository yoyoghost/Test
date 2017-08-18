package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.wx.WeChatDailyUsers;

import java.util.Date;

/**
 * @author jianghc
 * @create 2017-04-06 16:11
 **/
public abstract class WeChatDailyUsersDAO extends HibernateSupportDelegateDAO<WeChatDailyUsers> {
    public WeChatDailyUsersDAO() {
        super();
        setEntityName(WeChatDailyUsers.class.getName());
        setKeyField("Id");
    }

    @DAOMethod(sql = "from WeChatDailyUsers where wxConfigId=:wxConfigId and refDate=:refDate and userSource=:userSource")
    public abstract WeChatDailyUsers getByWxConfigIdAndRefDateAndUserSource(@DAOParam("wxConfigId") Integer wxConfigId
            ,@DAOParam("refDate") Date refDate,@DAOParam("userSource") String userSource);



}
