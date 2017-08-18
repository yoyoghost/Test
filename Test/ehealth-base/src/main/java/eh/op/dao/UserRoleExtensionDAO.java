package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.opauth.UserRoleExtension;

public abstract class UserRoleExtensionDAO extends HibernateSupportDelegateDAO<UserRoleExtension> {

    public UserRoleExtensionDAO() {
        super();
        this.setEntityName(UserRoleExtension.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod
    public abstract UserRoleExtension getByUserRoleId(Integer userRoleId);

}
